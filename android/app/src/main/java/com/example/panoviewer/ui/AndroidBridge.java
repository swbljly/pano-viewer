package com.example.panoviewer.ui;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import android.webkit.JavascriptInterface;

/**
 * WebView ←→ Android 桥接对象（以 {@code PanoAndroid} 注册到 JS）。
 *
 * <p>网页工具栏按钮调用：</p>
 * <ul>
 *   <li>{@code PanoAndroid.openLinkPanel()}：调起「链接查看」面板；</li>
 *   <li>{@code PanoAndroid.openFavorites()}：打开「收藏」页。</li>
 * </ul>
 *
 * <p>方法必须保留（@Keep + @JavascriptInterface），否则 release 混淆会移除它们。</p>
 */
@Keep
public class AndroidBridge {

    /** 宿主回调（通常为 MainActivity）。 */
    public interface Host {
        void openLinkPanel();

        void openFavorites();
    }

    @NonNull
    private final Host host;

    public AndroidBridge(@NonNull Host host) {
        this.host = host;
    }

    @JavascriptInterface
    public void openLinkPanel() {
        host.openLinkPanel();
    }

    @JavascriptInterface
    public void openFavorites() {
        host.openFavorites();
    }
}
