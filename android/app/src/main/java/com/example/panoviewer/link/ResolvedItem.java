package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 一次链接解析产出的「可下载项」。
 *
 * <p>对网页/直链而言 {@link #url} 即为图片下载地址；对夸克分享而言
 * {@link #url} 可能是可下载直链，也可能回退为分享页链接（由解析器决定）。</p>
 */
public final class ResolvedItem {

    /** 图片下载地址（或回退用的分享页地址）。 */
    @NonNull
    private final String url;

    /** 展示标题（通常为文件名）。 */
    @NonNull
    private final String title;

    /** 该条目来源的类型。 */
    @NonNull
    private final LinkType sourceType;

    /** 字节大小（未知时为 0）。 */
    private final long size;

    /** 缩略图地址（可选）。 */
    @Nullable
    private final String thumbUrl;

    public ResolvedItem(
            @NonNull String url,
            @NonNull String title,
            @NonNull LinkType sourceType,
            long size,
            @Nullable String thumbUrl) {
        this.url = url;
        this.title = title;
        this.sourceType = sourceType;
        this.size = size;
        this.thumbUrl = thumbUrl;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public LinkType getSourceType() {
        return sourceType;
    }

    public long getSize() {
        return size;
    }

    @Nullable
    public String getThumbUrl() {
        return thumbUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "ResolvedItem{url='" + url + "', title='" + title + "', sourceType=" + sourceType + '}';
    }
}
