package com.jobflow.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private static final int FILE_REQ = 1;

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
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // links open in the browser, Gmail, WhatsApp
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
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    // Native HTTP so Apify calls are not blocked by browser CORS rules.
    class Bridge {
        @JavascriptInterface
        public void http(final String id, final String url, final String method, final String body) {
            new Thread(() -> {
                int code = 0;
                String out;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod(method);
                    c.setConnectTimeout(30000);
                    c.setReadTimeout(290000);
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
    }
}
