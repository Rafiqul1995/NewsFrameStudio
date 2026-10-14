package com.newsframe.studio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Foreground service: keeps the app process alive and shows a progress
 * notification while a video is being exported, even if the screen turns off
 * or another app is opened.
 */
public class ExportService extends Service {

    static final String ACT_CANCEL = "com.newsframe.studio.CANCEL";
    private static final String CH_PROGRESS = "export_progress";
    private static final String CH_DONE = "export_done";
    private static final int ID_PROGRESS = 101;
    private static final int ID_DONE = 102;

    static ExportService instance;

    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CH_PROGRESS, "Export progress", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CH_DONE, "Export complete", NotificationManager.IMPORTANCE_HIGH));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACT_CANCEL.equals(intent.getAction())) {
            MainActivity.cancelExport();
            return START_NOT_STICKY;
        }
        Notification n = build(0, "Export shuru ho raha hai...", true);
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        } else {
            startForeground(ID_PROGRESS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "newsframe:export");
            wakeLock.acquire(2L * 60 * 60 * 1000);
        }
        return START_NOT_STICKY;
    }

    private Notification build(int pct, String text, boolean ongoing) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent po = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, ongoing ? CH_PROGRESS : CH_DONE)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("News Frame Studio")
                .setContentText(text)
                .setContentIntent(po)
                .setOnlyAlertOnce(true);

        if (ongoing) {
            Intent ci = new Intent(this, ExportService.class);
            ci.setAction(ACT_CANCEL);
            PendingIntent pc = PendingIntent.getService(this, 1, ci,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setOngoing(true);
            b.setProgress(100, pct, false);
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notif), "Cancel", pc).build());
            if (Build.VERSION.SDK_INT >= 31) {
                b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
            }
        } else {
            b.setAutoCancel(true);
        }
        return b.build();
    }

    private void doProgress(int pct, String text) {
        int p = Math.max(0, Math.min(100, pct));
        nm.notify(ID_PROGRESS, build(p, "Export " + p + "%  " + (text == null ? "" : text), true));
    }

    private void doFinish(boolean ok, String name) {
        stopForeground(true);
        String msg = ok ? ("Export ho gaya: " + (name == null ? "" : name)) : "Export roka gaya";
        nm.notify(ID_DONE, build(100, msg, false));
        stopSelf();
    }

    static void progress(int pct, String text) {
        ExportService s = instance;
        if (s != null) {
            s.doProgress(pct, text);
        }
    }

    static void finish(boolean ok, String name) {
        ExportService s = instance;
        if (s != null) {
            s.doFinish(ok, name);
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }
}
