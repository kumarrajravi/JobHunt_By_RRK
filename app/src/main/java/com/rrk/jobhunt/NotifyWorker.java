package com.rrk.jobhunt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

// Polls Mission Control every ~15 minutes and shows any due notifications.
public class NotifyWorker extends Worker {
    public NotifyWorker(Context c, WorkerParameters p) { super(c, p); }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        }
    }

    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences sp = c.getSharedPreferences("ev", 0);
        String hook = sp.getString("hook", ""), email = sp.getString("email", ""), key = sp.getString("key", "");
        if (hook.isEmpty() || email.isEmpty()) return Result.success();
        try {
            String sep = hook.contains("?") ? "&" : "?";
            JSONArray a = new JSONArray(get(hook + sep + "a=notifs&k=" + enc(key) + "&email=" + enc(email)));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String id = String.valueOf(o.get("id"));
                show(c, id, o.optString("title"), o.optString("msg"));
                get(hook + sep + "a=delivered&k=" + enc(key) + "&email=" + enc(email) + "&id=" + enc(id));
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void show(Context c, String id, String title, String msg) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("ev_notif", "Mission Control", NotificationManager.IMPORTANCE_DEFAULT));
        Intent i = new Intent(c, MainActivity.class);
        i.putExtra("nid", id);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(id.hashCode(), new NotificationCompat.Builder(c, "ev_notif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(msg)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build());
    }
}
