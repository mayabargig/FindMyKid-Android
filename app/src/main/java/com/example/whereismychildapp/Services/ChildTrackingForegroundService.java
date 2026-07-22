package com.example.whereismychildapp.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.whereismychildapp.MainActivity;
import com.example.whereismychildapp.R;
import com.google.firebase.auth.FirebaseAuth;

public class ChildTrackingForegroundService extends Service {

    // חשוב שיהיה public כדי שתוכל להשתמש בזה מכל מקום (Activity/Fragment)
    public static final String CHANNEL_ID = "child_tracking_channel";

    private static final int NOTIF_ID = 1001;
    private FirebaseAuth.AuthStateListener authListener; // מאזין עם המשתמש התנתק

    private boolean started = false;
    private ChildLocationService locationService;

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, ChildTrackingForegroundService.class);

        // חובה ל-foreground service באנדרואיד 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("TRACKING", "Service created");

        authListener = firebaseAuth -> {
            if(firebaseAuth.getCurrentUser() == null)
                stopSelf();
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);

        locationService = new ChildLocationService(getApplicationContext());

        createChannel();
        startForeground(NOTIF_ID, buildNotification());
        // שים לב: לא מתחילים tracking כאן כדי לא להתחיל פעמיים
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // אם אין משתמש - סוגרים שירות מיד
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIF_ID, buildNotification()); // בטיחות נוספת

        if (!started) {
            started = true;
            locationService.startSmartTracking();
        }
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        if (locationService != null) {
            locationService.stopSmartTracking();
        }

        if (authListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authListener);
            authListener = null;
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Child Location Tracking",
                NotificationManager.IMPORTANCE_LOW
        );

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // אפשר להחליף לאייקון מיקום משלך
                .setContentTitle("מעקב מיקום פעיל")
                .setContentText("המיקום מתעדכן ברקע")
                .setContentIntent(pi)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .build();
    }
}
