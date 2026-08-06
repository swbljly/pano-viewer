package com.example.panoviewer.link;

import androidx.annotation.NonNull;

import java.net.URL;

/**
 * 链接解析相关的纯函数工具（无 Context 依赖，便于单测）。
 */
public final class LinkUtils {

    /** 支持的图片扩展名。 */
    private static final String[] IMAGE_EXTENSIONS = {
            "jpg", "jpeg", "png", "webp", "bmp", "gif"
    };

    private LinkUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 去掉 URL 中的查询串与片段，便于扩展名判断。
     */
    @NonNull
    public static String stripQuery(@NonNull String url) {
        int q = url.indexOf('?');
        if (q >= 0) {
            url = url.substring(0, q);
        }
        int h = url.indexOf('#');
        if (h >= 0) {
            url = url.substring(0, h);
        }
        return url;
    }

    /**
     * 判断 URL 是否为直链图片：必须是 http(s) 协议，且路径以图片扩展名结尾
     * （忽略大小写、忽略查询串）。非 http(s) 一律返回 false，避免 ftp 等协议被误判。
     */
    public static boolean isImageUrl(@NonNull String url) {
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        String path = stripQuery(lower);
        for (String ext : IMAGE_EXTENSIONS) {
            if (path.endsWith("." + ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 URL 中提取文件名（取最后一段路径；无则回退 "image"）。
     */
    @NonNull
    public static String fileNameFromUrl(@NonNull String url) {
        String path = stripQuery(url);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isEmpty()) {
            name = "image";
        }
        return name;
    }

    /**
     * 把可能为相对路径的 src 基于 base 解析为绝对 URL。解析失败则原样返回。
     */
    @NonNull
    public static String toAbsoluteUrl(@NonNull String base, @NonNull String src) {
        try {
            return new URL(new URL(base), src).toString();
        } catch (Exception e) {
            return src;
        }
    }
}
