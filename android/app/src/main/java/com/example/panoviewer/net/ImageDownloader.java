package com.example.panoviewer.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Base64;

/**
 * 图片下载器：把远程图片字节转换为 {@code data:image/<mime>;base64,<b64>} 字符串，
 * 供 WebView 通过 {@code window.PanoViewer.loadImageDataURL(dataURL)} 离线注入（规避
 * file:// 下的 CORS 限制）。
 *
 * <p>依赖注入 {@link HttpHelper}，便于单测替换。</p>
 */
public class ImageDownloader {

    @NonNull
    private final HttpHelper httpHelper;

    public ImageDownloader(@NonNull HttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    /**
     * 下载图片并编码为 dataURL。
     *
     * @param url 图片地址
     * @return {@code data:image/<mime>;base64,<b64>}
     * @throws Exception 下载或编码失败
     */
    @NonNull
    public String downloadToDataUrl(@NonNull String url) throws Exception {
        byte[] bytes = httpHelper.getBytes(url);
        String mime = mimeFromUrl(url);
        String b64 = Base64.getEncoder().encodeToString(bytes);
        return "data:" + mime + ";base64," + b64;
    }

    /**
     * 依据 URL 扩展名推断 MIME 类型。
     *
     * @param url 图片地址（可含查询参数）
     * @return 推断出的 MIME，默认 {@code image/jpeg}
     */
    @NonNull
    static String mimeFromUrl(@NonNull String url) {
        String lower = url.toLowerCase();
        int q = lower.indexOf('?');
        if (q >= 0) {
            lower = lower.substring(0, q);
        }
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : "";
        switch (ext) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }
}
