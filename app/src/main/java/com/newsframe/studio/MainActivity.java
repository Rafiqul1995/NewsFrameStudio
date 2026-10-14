package com.newsframe.studio;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import java.io.OutputStream;

public class MainActivity extends Activity {

    static MainActivity instance;

    private static final int REQ_FILE = 1;
    private static final int REQ_NOTIF = 2;

    private WebView web;
    private ValueCallback<Uri[]> chooserCb;

    private OutputStream saveOut;
    private Uri saveUri;
    private String saveMime = "";
    private Uri lastUri;
    private String lastMime = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Android 15+ draws edge-to-edge: keep the page clear of system bars and keyboard.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportZoom(false);

        // Keep the renderer important even when the app is in the background,
        // so an export keeps running while another app is open.
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);

        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooserCb != null) {
                    chooserCb.onReceiveValue(null);
                }
                chooserCb = cb;
                String type = "*/*";
                String[] accept = params.getAcceptTypes();
                if (accept != null && accept.length > 0 && accept[0] != null && !accept[0].isEmpty()) {
                    type = accept[0];
                }
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(type);
                try {
                    startActivityForResult(Intent.createChooser(i, "Chunein"), REQ_FILE);
                } catch (Exception e) {
                    chooserCb.onReceiveValue(null);
                    chooserCb = null;
                    return false;
                }
                return true;
            }
        });

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && chooserCb != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            chooserCb.onReceiveValue(result);
            chooserCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        // Do not close the app by mistake while an export may be running.
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

    /** Called from the notification "Cancel" button. */
    static void cancelExport() {
        final MainActivity a = instance;
        if (a != null) {
            a.runOnUiThread(() -> a.web.evaluateJavascript("window.nfsCancel&&nfsCancel()", null));
        }
    }

    private void toast(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    /** Methods the web page can call as window.AndroidBridge.* */
    public class Bridge {

        @JavascriptInterface
        public void exportStart() {
            try {
                Intent i = new Intent(MainActivity.this, ExportService.class);
                startForegroundService(i);
            } catch (Exception e) {
                // keep going even if the notification service could not start
            }
            runOnUiThread(() -> getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        }

        @JavascriptInterface
        public void exportProgress(int pct, String text) {
            ExportService.progress(pct, text);
        }

        @JavascriptInterface
        public void exportDone(boolean ok, String name) {
            ExportService.finish(ok, name);
            runOnUiThread(() -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        }

        @JavascriptInterface
        public boolean saveBegin(String name, String mime) {
            try {
                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                Uri collection;
                String rel;
                if (mime.startsWith("video/")) {
                    collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    rel = Environment.DIRECTORY_MOVIES + "/NewsFrameStudio";
                } else if (mime.startsWith("image/")) {
                    collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    rel = Environment.DIRECTORY_PICTURES + "/NewsFrameStudio";
                } else {
                    collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    rel = Environment.DIRECTORY_DOWNLOADS;
                }
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                saveUri = cr.insert(collection, v);
                if (saveUri == null) {
                    return false;
                }
                saveOut = cr.openOutputStream(saveUri);
                saveMime = mime;
                return saveOut != null;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean saveChunk(String b64) {
            try {
                byte[] b = Base64.decode(b64, Base64.DEFAULT);
                saveOut.write(b);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void saveEnd() {
            try {
                if (saveOut != null) {
                    saveOut.close();
                    saveOut = null;
                }
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(saveUri, v, null, null);
                lastUri = saveUri;
                lastMime = saveMime;
                if (saveMime.startsWith("video/")) {
                    toast("Video Movies/NewsFrameStudio mein save ho gaya (Gallery mein dikhega)");
                } else if (saveMime.startsWith("image/")) {
                    toast("Photo Pictures/NewsFrameStudio mein save ho gayi");
                } else {
                    toast("File Downloads mein save ho gayi");
                }
            } catch (Exception e) {
                toast("Save mein dikkat aayi");
            }
        }

        @JavascriptInterface
        public void saveAbort() {
            try {
                if (saveOut != null) {
                    saveOut.close();
                    saveOut = null;
                }
                if (saveUri != null) {
                    getContentResolver().delete(saveUri, null, null);
                }
            } catch (Exception e) {
                // ignore
            }
        }

        @JavascriptInterface
        public void shareLast() {
            if (lastUri == null) {
                toast("Pehle video export karein");
                return;
            }
            final Uri u = lastUri;
            final String m = lastMime;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(m);
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share"));
            });
        }
    }
}
