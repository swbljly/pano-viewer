package com.example.panoviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.panoviewer.favorites.FavoriteItem;
import com.example.panoviewer.favorites.FavoritesRepository;
import com.example.panoviewer.favorites.SharedPreferencesFavoritesDataSource;
import com.example.panoviewer.link.LinkResolverDispatcher;
import com.example.panoviewer.link.LinkType;
import com.example.panoviewer.link.ResolveException;
import com.example.panoviewer.link.ResolvedItem;
import com.example.panoviewer.net.HttpHelper;
import com.example.panoviewer.net.ImageDownloader;
import com.example.panoviewer.ui.AndroidBridge;
import com.example.panoviewer.ui.FavoritesActivity;
import com.example.panoviewer.ui.LinkInputDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 360° 全景查看器 —— Android 入口 Activity。
 *
 * <p>方案：用 {@link WebView} 加载内置在 assets 中的纯前端查看器
 * （file:///android_asset/viewer/index.html）。</p>
 *
 * <p>本次新增能力（复用渲染核心，不破坏现有「打开图片」入口）：</p>
 * <ul>
 *   <li>链接查看（夸克分享 / 网页 / 图片直链）：经 JS 桥 {@code PanoAndroid} 调起面板，
 *       后台解析 → 下载字节 → dataURL → {@code loadImageDataURL} 注入；</li>
 *   <li>链接收藏：解析结果可收藏，持久化于 SharedPreferences；收藏页可复看。</li>
 * </ul>
 */
public class MainActivity extends AppCompatActivity
        implements AndroidBridge.Host, LinkInputDialog.Host {

    private static final int REQUEST_SELECT_FILE = 100;

    /** FavoritesActivity 回传 MainActivity 时携带的 extra key（收藏项 url）。 */
    public static final String EXTRA_FAVORITE_URL = "favorite_url";
    /** FavoritesActivity 回传 MainActivity 时携带的 extra key（收藏项类型）。 */
    public static final String EXTRA_FAVORITE_TYPE = "favorite_type";

    private WebView webView;
    /** 网页内 <input type=file> 的文件选择回调。 */
    private ValueCallback<Uri[]> uploadMessage;
    /** 页面尚未加载完成时，暂存待注入的 JS。 */
    private String pendingImageJs;
    /** 页面是否已加载完成。 */
    private boolean pageLoaded = false;

    // ---- 新增：链接 / 收藏 相关服务 ----
    private ExecutorService executor;
    private Handler mainHandler;
    private HttpHelper httpHelper;
    private ImageDownloader imageDownloader;
    private LinkResolverDispatcher dispatcher;
    private FavoritesRepository favoritesRepo;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initServices();

        webView = findViewById(R.id.webview);
        setupWebView();
        loadViewer();
        handleIntent(getIntent());
    }

    /** 初始化网络 / 解析 / 下载 / 收藏服务（纯 Java，无 Context 依赖的部分可注入）。 */
    private void initServices() {
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        httpHelper = new HttpHelper();
        imageDownloader = new ImageDownloader(httpHelper);

        // 调度器持有三种解析器，dispatch 时按 URL 特征自动选择
        dispatcher = new LinkResolverDispatcher(
                new com.example.panoviewer.link.QuarkShareResolver(httpHelper),
                new com.example.panoviewer.link.WebPageImageResolver(httpHelper),
                new com.example.panoviewer.link.DirectImageResolver());

        favoritesRepo = new FavoritesRepository(new SharedPreferencesFavoritesDataSource(this));
    }

    /** 配置 WebView：开启 JS、允许 assets 访问、支持文件选择器、注册 JS 桥。 */
    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 注册 JS 桥：网页内可通过 window.PanoAndroid 调起链接面板 / 收藏页
        webView.addJavascriptInterface(new AndroidBridge(this), "PanoAndroid");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageLoaded = true;
                if (pendingImageJs != null) {
                    final String js = pendingImageJs;
                    pendingImageJs = null;
                    view.post(() -> view.evaluateJavascript(js, null));
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView wv,
                    ValueCallback<Uri[]> filePathCallback,
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

    // ====================================================================
    // AndroidBridge.Host 实现：来自网页工具栏按钮
    // ====================================================================

    /** 网页「🔗 链接」按钮：调起链接输入面板。 */
    @Override
    public void openLinkPanel() {
        LinkInputDialog dialog = new LinkInputDialog(
                this, dispatcher, imageDownloader, favoritesRepo, this);
        dialog.show();
    }

    /** 网页「⭐ 收藏」按钮：打开收藏页。 */
    @Override
    public void openFavorites() {
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);
    }

    // ====================================================================
    // LinkInputDialog.Host 实现：解析结果的选择
    // ====================================================================

    /** 用户在链接面板点「查看」：下载并注入当前 WebView。 */
    @Override
    public void onResolvedChosen(ResolvedItem item) {
        downloadAndInject(item);
    }

    // ====================================================================
    // 收藏复看（FavoritesActivity 通过 extra 回传 MainActivity）
    // ====================================================================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** 解析启动/重入 intent：打开图片 或 收藏复看。 */
    private void handleIntent(@Nullable Intent intent) {
        if (intent == null) return;

        // 收藏复看：FavoritesActivity 回传 favorite_url / favorite_type
        if (intent.hasExtra(EXTRA_FAVORITE_URL)) {
            String url = intent.getStringExtra(EXTRA_FAVORITE_URL);
            String typeName = intent.getStringExtra(EXTRA_FAVORITE_TYPE);
            LinkType type = LinkType.UNKNOWN;
            if (typeName != null) {
                try {
                    type = LinkType.valueOf(typeName);
                } catch (IllegalArgumentException ignore) {
                    type = LinkType.UNKNOWN;
                }
            }
            if (url != null && !url.isEmpty()) {
                openFavoriteByInfo(url, type);
            }
            return;
        }

        // 现有能力：用其它应用打开图片 -> 选择本 App
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String dataUrl = readUriAsBase64DataUrl(intent.getData());
            if (dataUrl == null) return;
            injectDataUrl(dataUrl);
        }
    }

    /** 由收藏信息（url + 类型）复看。 */
    private void openFavoriteByInfo(String url, LinkType type) {
        if (type == LinkType.QUARK_SHARE) {
            // 夸克收藏仅存链接，复看可能需要提取码 -> 重新弹窗
            promptPasscodeThenOpen(url);
        } else {
            dispatchAndOpen(url, null);
        }
    }

    /** 夸克场景：弹窗输入提取码后再解析。 */
    private void promptPasscodeThenOpen(String url) {
        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.passcode_hint);
        builder.setTitle(R.string.passcode_title)
                .setView(input)
                .setPositiveButton(R.string.btn_ok, (d, w) -> {
                    String pc = input.getText().toString().trim();
                    dispatchAndOpen(url, pc.isEmpty() ? null : pc);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    // ====================================================================
    // 解析 -> 下载 -> 注入 流水线（全部后台线程，结果回主线程）
    // ====================================================================

    /** 解析链接并取首个图片条目下载注入。 */
    private void dispatchAndOpen(String url, String passcode) {
        executor.execute(() -> {
            try {
                List<ResolvedItem> items = dispatcher.dispatch(url, passcode);
                if (items == null || items.isEmpty()) {
                    mainHandler.post(() ->
                            Toast.makeText(this, R.string.e_no_image, Toast.LENGTH_SHORT).show());
                    return;
                }
                downloadAndInjectOnThread(items.get(0));
            } catch (ResolveException re) {
                mainHandler.post(() ->
                        Toast.makeText(this, R.string.e_resolve_quark, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(this, R.string.e_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 下载单条解析结果并注入（后台线程调用，内部切回主线程注入）。 */
    private void downloadAndInject(ResolvedItem item) {
        executor.execute(() -> downloadAndInjectOnThread(item));
    }

    private void downloadAndInjectOnThread(ResolvedItem item) {
        mainHandler.post(() ->
                Toast.makeText(this, R.string.toast_loading, Toast.LENGTH_SHORT).show());
        try {
            String dataUrl = imageDownloader.downloadToDataUrl(item.getUrl());
            mainHandler.post(() -> injectDataUrl(dataUrl));
        } catch (Exception e) {
            mainHandler.post(() ->
                    Toast.makeText(this, R.string.e_download, Toast.LENGTH_SHORT).show());
        }
    }

    /** 把 dataURL 注入 WebView 的 PanoViewer（页面未就绪则暂存）。 */
    private void injectDataUrl(String dataUrl) {
        String js = "(function(){var d='" + dataUrl + "';"
                + "function t(){if(window.PanoViewer){window.PanoViewer.loadImageDataURL(d);}"
                + "else{setTimeout(t,100);}}t();})();";
        if (pageLoaded) {
            webView.evaluateJavascript(js, null);
        } else {
            pendingImageJs = js;
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
            uploadMessage.onReceiveValue(result != null ? new Uri[]{result} : null);
            uploadMessage = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdownNow();
        }
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
