package com.example.whereismychildapp.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.whereismychildapp.MainActivity;
import com.example.whereismychildapp.Objects.HelpRequest;
import com.example.whereismychildapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ParentSosPollingForegroundService extends Service {

    public static final String CHANNEL_ID = "parent_sos_polling_channel";
    private static final int NOTIF_ID = 2001;

    // Poll interval (תשנה לפי מה שבא לך)
    private static final long POLL_MS = 10_000; // 10 שניות

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DatabaseReference db = FirebaseDatabase.getInstance().getReference();

    private boolean started = false;

    private SharedPreferences prefs;
    private static final String PREFS = "parent_sos_prefs";
    private static final String KEY_LAST_SEEN_TS = "last_seen_ts";

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollOnce();
            handler.postDelayed(this, POLL_MS);
        }
    };

    // ====== API חיצוני ======
    public static void start(Context ctx) {
        Intent i = new Intent(ctx, ParentSosPollingForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, ParentSosPollingForegroundService.class);
        ctx.stopService(i);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createChannelIfNeeded();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (!started) {
                started = true;
                android.util.Log.d("SOS_SERVICE", "startForeground...");
                 startForeground(NOTIF_ID, buildOngoingNotification("ממתין ל-SOS..."));
                android.util.Log.d("SOS_SERVICE", "post pollRunnable...");
                handler.post(pollRunnable);
            }
            return START_STICKY;
        } catch (Exception e) {
            android.util.Log.e("SOS_SERVICE", "Crash in onStartCommand", e);
            stopSelf();
            return START_NOT_STICKY;
        }
    }


    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        started = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ====== Poll ======
    private void pollOnce() {
        String parentUid = FirebaseAuth.getInstance().getUid();
        if (parentUid == null) {
            // לא מחובר -> לא מבזבזים סוללה
            stopSelf();
            return;
        }

        long lastSeen = prefs.getLong(KEY_LAST_SEEN_TS, 0L);

        db.child("helpRequests").child(parentUid).get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    // נמצא “ה-SOS החדש ביותר” שעדיין open וגם מעל lastSeen
                    HelpRequest best = null;


                    for (DataSnapshot childSnap : snapshot.getChildren()) {
                        String childUid = childSnap.getKey();
                        if (childUid == null) continue;

                        for (DataSnapshot reqSnap : childSnap.getChildren()) {
                            String requestId = reqSnap.getKey();
                            if (requestId == null) continue;

                            String status = reqSnap.child("status").getValue(String.class);
                            if (status == null || !status.equals("open")) continue;

                            Long ts = reqSnap.child("timestamp").getValue(Long.class);
                            if (ts == null) continue;
                            if (ts <= lastSeen) continue;

                            Double lat = reqSnap.child("latitude").getValue(Double.class);
                            Double lng = reqSnap.child("longitude").getValue(Double.class);
                            String note = reqSnap.child("note").getValue(String.class);

                            String childName = reqSnap.child("childName").getValue(String.class);
                            if (childName == null) childName = "הילד";

                            best = new HelpRequest(
                                    childUid,
                                    requestId,
                                    ts,
                                    lat == null ? 0 : lat,
                                    lng == null ? 0 : lng,
                                    note == null ? "" : note,
                                    childName,
                                    status
                            );

                        }
                    }

                    if (best != null) {
                        prefs.edit().putLong(KEY_LAST_SEEN_TS, best.timestamp).apply();
                        showSosNotification(best);
                    }

                })
                .addOnFailureListener(e -> {
                    // אפשר להתעלם/ללוג. לא עוצרים את השירות על כשל זמני.
                });
    }

    // ====== Notifications ======
    private Notification buildOngoingNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                pendingFlags()
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos_notification)// תחליף לאייקון שקיים אצלך
                .setContentTitle("התראת SOS")
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void showSosNotification(HelpRequest hit) {
        // Intent שפותח את האפליקציה עם פרטים כדי לנווט ישר למסך מתאים
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("sos_childUid", hit.childUid);
        i.putExtra("sos_requestId", hit.requestId);
        i.putExtra("sos_lat", hit.latitude);
        i.putExtra("sos_lng", hit.longitude);
        i.putExtra("sos_note", hit.note);
        i.putExtra("sos_childName", hit.childName);

        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                this,
                1,
                i,
                pendingFlags()
        );

        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_sos_notification)
                .setContentTitle("🚨 קיבלת הודעת חירום מ-" + hit.childName)

                .setContentText(hit.note.isEmpty() ? "נשלחה בקשת עזרה" : hit.note)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(new long[]{0, 800, 300, 800, 300, 800})
                .setContentIntent(pi)
                .build();


        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int)(System.currentTimeMillis() % Integer.MAX_VALUE), n);

        // אפשר גם לעדכן את ההתראה הקבועה (אופציונלי)
        NotificationManager nm2 = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm2 != null) nm2.notify(NOTIF_ID, buildOngoingNotification("נמצא SOS חדש"));
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "Parent SOS Polling",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Poll SOS requests for parent");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private int pendingFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.FLAG_UPDATE_CURRENT;
    }


}

