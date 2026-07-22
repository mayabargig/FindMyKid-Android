package com.example.whereismychildapp.Services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/**
 * DEBUG VERSION:
 * - מבקש HIGH_ACCURACY
 * - min distance = 0 כדי לקבל callbacks גם בלי תזוזה
 * - requestLocationUpdates עם looper=null (לא main thread)
 * - לוגים מפורטים כדי לוודא שה-callback עובד
 * - שליחה ל-Firebase אם עברו 50 מטר או אם עברו FORCE_UPLOAD_EVERY_MS (כאן דקה)
 */
public class ChildLocationService {

    // שולחים אם עברו >= 50 מטר מהמיקום האחרון שנשלח
    private static final float MIN_DISTANCE_TO_UPLOAD_METERS = 50f;

    // בקשות לעדכוני מיקום (קבלת callbacks)
    private static final long INTERVAL_MS = 60_000;          // דקה
    private static final long FASTEST_INTERVAL_MS = 30_000;  // 30 שניות

    // DEBUG: heartbeat כל דקה גם בלי תזוזה (דרך תנאי זמן בתוך callback)
    private static final long FORCE_UPLOAD_EVERY_MS = 60_000;

    private final Context context;
    private final FusedLocationProviderClient fusedClient;
    private final DatabaseReference db;

    private LocationCallback trackingCallback;
    private boolean isTracking = false;

    private Location lastUploadedLocation;
    private long lastUploadTimeMs = 0;

    public ChildLocationService(Context context) {
        this.context = context.getApplicationContext();
        this.fusedClient = LocationServices.getFusedLocationProviderClient(this.context);
        this.db = FirebaseDatabase.getInstance().getReference();
    }

    /** שליחה חד-פעמית (לא tracking) */
    @SuppressLint("MissingPermission")
    public void fetchAndUploadLocation() {
        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        Toast.makeText(context, "לא הצלחתי להביא מיקום (תדליק GPS ותנסה שוב)", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Log.d("TRACKING", "fetchAndUploadLocation: " +
                            location.getLatitude() + "," + location.getLongitude());
                    upload(location);
                    lastUploadedLocation = location;
                    lastUploadTimeMs = System.currentTimeMillis();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "שגיאה בקבלת מיקום: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * DEBUG tracking:
     * HIGH_ACCURACY + minDistance=0 כדי לוודא שה-callback מגיע גם בלי תזוזה.
     */
    @SuppressLint("MissingPermission")
    public void startSmartTracking() {
        if (isTracking) return;

        isTracking = true;

        // DEBUG: HIGH ACCURACY + 0m כדי לקבל callbacks גם כשעומדים
        LocationRequest req = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS
        )
                .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
                .setMinUpdateDistanceMeters(0f) // DEBUG: לא תלוי תזוזה
                .build();

        trackingCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc == null) {
                    Log.d("TRACKING", "onLocationResult: loc=null");
                    return;
                }

                long now = System.currentTimeMillis();

                Log.d("TRACKING", "onLocationResult: " + loc.getLatitude() + "," + loc.getLongitude()
                        + " acc=" + loc.getAccuracy()
                        + " provider=" + loc.getProvider());

                // שליחה ראשונה
                if (lastUploadedLocation == null) {
                    Log.d("TRACKING", "First upload");

                    if (!upload(loc)) {
                        Log.d("TRACKING", "Stopping tracking - user logged out");
                        stopSmartTracking();
                        return;
                    }


                    lastUploadedLocation = loc;
                    lastUploadTimeMs = now;
                    return;
                }

                float d = loc.distanceTo(lastUploadedLocation);
                boolean timePassed = (now - lastUploadTimeMs) >= FORCE_UPLOAD_EVERY_MS;

                Log.d("TRACKING", "Decision: d=" + d
                        + " timePassed=" + timePassed
                        + " sinceLastMs=" + (now - lastUploadTimeMs));

                // שולחים אם עברו 50 מטר או אם עבר זמן (DEBUG: דקה)
                if (d >= MIN_DISTANCE_TO_UPLOAD_METERS || timePassed) {
                    if (timePassed && d < MIN_DISTANCE_TO_UPLOAD_METERS) {
                        Log.d("TRACKING", "Upload by time (no movement)");
                    } else {
                        Log.d("TRACKING", "Upload by distance: " + d + "m");
                    }

                    if (!upload(loc)) {
                        Log.d("TRACKING", "Stopping tracking - user logged out");
                        stopSmartTracking();
                        return;
                    }
                    lastUploadedLocation = loc;
                    lastUploadTimeMs = now;
                }
            }
        };

        // IMPORTANT: לא main looper, נותן למערכת לנהל את ה-thread
        fusedClient.requestLocationUpdates(req, trackingCallback, null);

        // ניסיון לשליחה מיידית של last known (לא חובה אבל נחמד)
        fusedClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null && lastUploadedLocation == null) {
                Log.d("TRACKING", "Initial lastLocation: " + loc.getLatitude() + "," + loc.getLongitude());
                upload(loc);
                lastUploadedLocation = loc;
                lastUploadTimeMs = System.currentTimeMillis();
            }
        });

        Log.d("TRACKING", "startSmartTracking: started");
    }

    public void stopSmartTracking() {
        if (!isTracking) return;

        isTracking = false;
        if (trackingCallback != null) {
            fusedClient.removeLocationUpdates(trackingCallback);
            trackingCallback = null;
        }
        Log.d("TRACKING", "stopSmartTracking: stopped");
    }

    public boolean isTracking() {
        return isTracking;
    }

    private boolean upload(Location location) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Log.d("TRACKING", "upload: uid=null (not logged in?)");
            return false;
        }

        double lat = location.getLatitude();
        double lon = location.getLongitude();

        Map<String, Object> loc = new HashMap<>();
        loc.put("latitude", lat);
        loc.put("longitude", lon);
        loc.put("timestamp", ServerValue.TIMESTAMP);

        db.child("locations").child(uid).updateChildren(loc);
        // db.child("children").child(uid).updateChildren(loc);

        Log.d("TRACKING", "upload -> Firebase: " + uid + " " + lat + "," + lon);
        return true;
    }
}
