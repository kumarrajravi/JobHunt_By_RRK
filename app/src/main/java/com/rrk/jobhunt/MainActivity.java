package com.rrk.jobhunt;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.core.content.FileProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private static final int FILE_REQ = 1;
    private String pendingNid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if ("file".equals(u.getScheme())) return false;
                openLink(u.toString());
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(p.createIntent(), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
        pendingNid = getIntent().getStringExtra("nid");
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("ev_notif", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(NotifyWorker.class, 15, TimeUnit.MINUTES).build());
    }

    private void openLink(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (url.contains("wa.me") || url.contains("whatsapp")) i.setPackage("com.whatsapp");
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
        }
    }

    private File resumeFile() {
        File d = new File(getFilesDir(), "resume");
        File[] l = d.listFiles();
        return (l != null && l.length > 0) ? l[0] : null;
    }

    private String mime(String n) {
        n = n.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".doc")) return "application/msword";
        return "application/octet-stream";
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String n = i.getStringExtra("nid");
        if (n != null) {
            pendingNid = n;
            web.evaluateJavascript("window.__nid&&window.__nid(" + JSONObject.quote(n) + ")", null);
        }
    }

    @Override
    protected void onActivityResult(int rq, int rs, Intent d) {
        if (rq == FILE_REQ && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(rs, d));
            fileCb = null;
        } else {
            super.onActivityResult(rq, rs, d);
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(window.__back?window.__back():false)", v -> {
            if (!"true".equals(v)) MainActivity.super.onBackPressed();
        });
    }

    class Bridge {
        @JavascriptInterface
        public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; }
        }

        @JavascriptInterface
        public void setNotif(String hook, String email, String key) {
            getSharedPreferences("ev", 0).edit().putString("hook", hook).putString("email", email).putString("key", key).apply();
        }

        @JavascriptInterface
        public String pendingNotif() {
            String n = pendingNid;
            pendingNid = null;
            return n == null ? "" : n;
        }

        private File writeCache(String name, String b64) throws Exception {
            File d = new File(getCacheDir(), "tailored");
            d.mkdirs();
            File f = new File(d, name.replaceAll("[^A-Za-z0-9._-]", "_"));
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(b64, Base64.DEFAULT)); }
            return f;
        }

        // Gmail with the tailored resume attached.
        @JavascriptInterface
        public void mailFile(final String to, final String subject, final String body, final String name, final String b64) {
            runOnUiThread(() -> {
                try {
                    File f = writeCache(name, b64);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                    i.putExtra(Intent.EXTRA_SUBJECT, subject);
                    i.putExtra(Intent.EXTRA_TEXT, body);
                    i.setType(mime(name));
                    i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    i.setPackage("com.google.android.gm");
                    try { startActivity(i); } catch (Exception e) { i.setPackage(null); startActivity(Intent.createChooser(i, "Send email")); }
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public void shareFile(final String name, final String b64) {
            runOnUiThread(() -> {
                try {
                    File f = writeCache(name, b64);
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType(mime(name));
                    i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(i, "Share resume"));
                } catch (Exception ignored) { }
            });
        }

        @JavascriptInterface
        public String deviceInfo() {
            try {
                JSONObject o = new JSONObject();
                o.put("model", Build.MODEL);
                o.put("brand", Build.MANUFACTURER);
                o.put("android", Build.VERSION.RELEASE);
                o.put("lang", java.util.Locale.getDefault().toString());
                o.put("tz", java.util.TimeZone.getDefault().getID());
                o.put("screen", getResources().getDisplayMetrics().widthPixels + "x" + getResources().getDisplayMetrics().heightPixels);
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }

        @JavascriptInterface
        public void clearResume() {
            File[] l = new File(getFilesDir(), "resume").listFiles();
            if (l != null) for (File f : l) f.delete();
        }

        @JavascriptInterface
        public void openUrl(final String url) { runOnUiThread(() -> openLink(url)); }

        @JavascriptInterface
        public void saveResume(String name, String b64) {
            try {
                File d = new File(getFilesDir(), "resume");
                d.mkdirs();
                File[] old = d.listFiles();
                if (old != null) for (File f : old) f.delete();
                try (FileOutputStream o = new FileOutputStream(new File(d, name.replaceAll("[^A-Za-z0-9._-]", "_")))) {
                    o.write(Base64.decode(b64, Base64.DEFAULT));
                }
            } catch (Exception ignored) { }
        }

        // Opens the Gmail app with To, Subject, Body and the saved resume attached.
        @JavascriptInterface
        public void mail(final String to, final String subject, final String body) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.putExtra(Intent.EXTRA_EMAIL, new String[]{to});
                i.putExtra(Intent.EXTRA_SUBJECT, subject);
                i.putExtra(Intent.EXTRA_TEXT, body);
                File f = resumeFile();
                if (f != null) {
                    i.setType(mime(f.getName()));
                    i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f));
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    i.setType("message/rfc822");
                }
                i.setPackage("com.google.android.gm");
                try {
                    startActivity(i);
                } catch (Exception e) {
                    i.setPackage(null);
                    startActivity(Intent.createChooser(i, "Send email"));
                }
            });
        }

        @JavascriptInterface
        public void http(final String id, final String url, final String method, final String headers, final String body) {
            new Thread(() -> {
                int code = 0;
                String out;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(290000);
                    c.setRequestProperty("User-Agent", "JobHunt_byRRK");
                    if (headers != null && !headers.isEmpty()) {
                        JSONObject h = new JSONObject(headers);
                        java.util.Iterator<String> k = h.keys();
                        while (k.hasNext()) { String key = k.next(); c.setRequestProperty(key, h.getString(key)); }
                    }
                    if (body != null && !body.isEmpty()) {
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json");
                        try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
                    }
                    code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while (in != null && (n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    out = bo.toString("UTF-8");
                } catch (Exception e) {
                    out = "ERR " + e;
                }
                final String js = "window.__cb(" + JSONObject.quote(id) + "," + code + "," + JSONObject.quote(out) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        // Downloads the new APK from the GitHub release and starts the installer.
        @JavascriptInterface
        public void install(final String url) {
            new Thread(() -> {
                String err = "";
                try {
                    File d = new File(getCacheDir(), "upd");
                    d.mkdirs();
                    final File f = new File(d, "update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setInstanceFollowRedirects(true);
                    try (InputStream in = c.getInputStream(); FileOutputStream o = new FileOutputStream(f)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
                    }
                    runOnUiThread(() -> {
                        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                            web.evaluateJavascript("window.__upd('Allow installs from this app, then tap Update again.')", null);
                            return;
                        }
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setDataAndType(FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f), "application/vnd.android.package-archive");
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    });
                } catch (Exception e) {
                    err = String.valueOf(e);
                    final String m = JSONObject.quote("Update failed: " + err);
                    runOnUiThread(() -> web.evaluateJavascript("window.__upd(" + m + ")", null));
                }
            }).start();
        }
    }
}
