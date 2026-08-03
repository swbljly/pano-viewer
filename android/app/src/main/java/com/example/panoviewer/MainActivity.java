package com.example.panoviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 360° 全景查看器 —— Android 入口 Activity。
 *
 * 方案：用 {@link WebView} 加载内置在 assets 中的纯前端查看器
 * （file:///android_asset/viewer/index.html），three.min.js 一并内置，完全离线。
 *
 * 关键能力：
 * 1. 启用 JavaScript，并把查看器作为 assets 加载（无需任何网络请求）。
 * 2. 通过 {@link WebChromeClient#onShowFileChooser} 让网页内
 *    {@code <input type="file">} 调起系统文件选择器，选中的图片回传后由网页
 *    读取为 dataURL 并贴图（全离线，不依赖任何桥接）。
 * 3. 支持「用其他应用打开图片 → 选择本 App」：把图片读取为 base64 的 dataURL，
 *    通过 {@code evaluateJavascript} 注入 {@code window.PanoViewer.loadImageDataURL}。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_FILE = 100;

    private WebView webView;
    /** 网页内 <input type=file> 的文件选择回调（双指/单指均由系统回传）。 */
    private ValueCallback<Uri> uploadMessage;
    /** 页面尚未加载完成时，暂存待注入的 JS（页面加载完再执行）。 */
    private String pendingImageJs;
    /** 页面是否已加载完成。 */
    private boolean pageLoaded = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        setupWebView();
        loadViewer();
        // 若是通过「打开图片」intent 启动，则载入该图
        handleIntent(getIntent());
    }

    /** 配置 WebView：开启 JS、允许 assets 访问、支持文件选择器。 */
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        // 全景查看无需用户手势即可渲染；assets 为 file:// 不涉及混合内容
        ws.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                // 启动期若已拿到图片（intent），页面加载完后注入
                if (pendingImageJs != null) {
                    final String js = pendingImageJs;
                    pendingImageJs = null;
                    view.post(() -> view.evaluateJavascript(js, null));
                }
            }
        });

        // 让网页的 <input type="file"> 能调起系统文件选择器
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView wv,
                    ValueCallback<Uri> filePathCallback,
                    FileChooserParams fileChooserParams) {
                uploadMessage = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });
    }

    /** 加载内置查看器。 */
    private void loadViewer() {
        webView.loadUrl("file:///android_asset/viewer/index.html");
    }

    /** App 已在运行时再次收到打开图片的 intent。 */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** 解析启动/重入 intent：若是打开图片，则读取为 base64 并注入网页。 */
    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String dataUrl = readUriAsBase64DataUrl(intent.getData());
            if (dataUrl == null) return;
            // 轮询等待 window.PanoViewer 就绪后再加载（页面可能还没初始化完）
            String js = "(function(){var d='" + dataUrl + "';"
                    + "function t(){if(window.PanoViewer){window.PanoViewer.loadImageDataURL(d);}"
                    + "else{setTimeout(t,100);}}t();})();";
            if (pageLoaded) {
                webView.evaluateJavascript(js, null);
            } else {
                pendingImageJs = js;
            }
        }
    }

    /** 把 content:// 或 file:// 图片读取为 base64 的 dataURL（供网页纹理使用）。 */
    @Nullable
    private String readUriAsBase64DataUrl(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                baos.write(buf, 0, n);
            }
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "image/jpeg";
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            return "data:" + mime + ";base64," + b64;
        } catch (Exception e) {
            return null;
        }
    }

    /** 系统文件选择器返回结果，回传给 WebView 的 <input type=file>。 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri result = (data != null && resultCode == RESULT_OK) ? data.getData() : null;
            uploadMessage.onReceiveValue(result);
            uploadMessage = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 返回键：先让 WebView 后退，否则退出。 */
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
