package fr.halalscan.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 42;
    private static final int REQ_CAMERA_OCR = 43;
    private static final int REQ_TAKE_PHOTO = 7;
    private static final int REQ_PICK_PHOTO = 8;
    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";

    private WebView web;
    private PermissionRequest pendingRequest;
    private String ocrId;
    private Uri photoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        // Sert les fichiers de l'appli depuis une origine https (nécessaire pour la caméra)
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, u)); // liens externes dans le navigateur
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        pendingRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }
        });

        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl(HOME);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_CAMERA && pendingRequest != null) {
            if (granted) pendingRequest.grant(pendingRequest.getResources());
            else pendingRequest.deny();
            pendingRequest = null;
        } else if (requestCode == REQ_CAMERA_OCR) {
            if (granted) launchCamera();
            else sendOcr(false, "Autorisation caméra refusée.");
        }
    }

    // ---------- Lecture du texte de l'étiquette (OCR, hors ligne) ----------

    private void launchCamera() {
        try {
            File dir = new File(getCacheDir(), "photos");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "etiquette.jpg");
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_TAKE_PHOTO);
        } catch (Exception e) {
            sendOcr(false, "Impossible d'ouvrir l'appareil photo : " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_TAKE_PHOTO && requestCode != REQ_PICK_PHOTO) return;
        if (resultCode != RESULT_OK) { sendOcr(false, "Photo annulée."); return; }
        Uri u = requestCode == REQ_TAKE_PHOTO ? photoUri : (data != null ? data.getData() : null);
        if (u == null) { sendOcr(false, "Aucune image reçue."); return; }
        try {
            InputImage image = InputImage.fromFilePath(this, u);
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener(text -> sendOcr(true, text.getText()))
                    .addOnFailureListener(e -> sendOcr(false, "Lecture impossible : " + e.getMessage()));
        } catch (Exception e) {
            sendOcr(false, "Image illisible : " + e.getMessage());
        }
    }

    private void sendOcr(boolean ok, String text) {
        if (ocrId == null) return;
        final String js = "window.__bridgeCb(" + JSONObject.quote(ocrId) + "," + ok + "," + JSONObject.quote(text == null ? "" : text) + ")";
        ocrId = null;
        runOnUiThread(() -> web.evaluateJavascript(js, null));
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** Pont Java -> JS : réseau (Open Food Facts, règles) et lecture d'étiquette. */
    private class Bridge {
        @JavascriptInterface
        public void get(final String id, final String url) {
            new Thread(() -> {
                boolean ok = false;
                String body;
                try {
                    URL u = new URL(url);
                    String host = u.getHost();
                    if (!"https".equals(u.getProtocol()) ||
                            !(host.endsWith("openfoodfacts.org") || host.equals("raw.githubusercontent.com"))) {
                        throw new SecurityException("Adresse non autorisée");
                    }
                    HttpURLConnection c = (HttpURLConnection) u.openConnection();
                    c.setRequestProperty("User-Agent", "HalalScan/1.0 (Android)");
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    int code = c.getResponseCode();
                    InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
                    StringBuilder sb = new StringBuilder();
                    if (is != null) {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = r.readLine()) != null) sb.append(line).append('\n');
                        }
                    }
                    ok = code < 400;
                    body = sb.toString();
                } catch (Exception e) {
                    body = e.getMessage() == null ? e.toString() : e.getMessage();
                }
                final String js = "window.__bridgeCb(" + JSONObject.quote(id) + "," + ok + "," + JSONObject.quote(body) + ")";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            }).start();
        }

        @JavascriptInterface
        public void ocrCamera(final String id) {
            runOnUiThread(() -> {
                ocrId = id;
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_OCR);
                } else {
                    launchCamera();
                }
            });
        }

        @JavascriptInterface
        public void ocrGallery(final String id) {
            runOnUiThread(() -> {
                ocrId = id;
                try {
                    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    startActivityForResult(Intent.createChooser(i, "Choisir une photo de l'étiquette"), REQ_PICK_PHOTO);
                } catch (Exception e) {
                    sendOcr(false, "Impossible d'ouvrir la galerie : " + e.getMessage());
                }
            });
        }
    }
}
