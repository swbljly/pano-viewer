package com.example.panoviewer.favorites;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.panoviewer.link.LinkType;

/**
 * 收藏项。
 *
 * <p>注意：{@link #passcode}（提取码）仅供运行期使用；持久化时<b>不写入</b>
 * JSON（见 {@code SharedPreferencesFavoritesDataSource}）。夸克分享复看时若需要
 * 提取码，会重新弹窗向用户索取。</p>
 */
public final class FavoriteItem {

    @NonNull
    private final String id;

    @NonNull
    private final String title;

    @NonNull
    private final String url;

    @NonNull
    private final LinkType linkType;

    /** 运行期提取码（不持久化）。 */
    @Nullable
    private final String passcode;

    private final long createdAt;

    public FavoriteItem(
            @NonNull String id,
            @NonNull String title,
            @NonNull String url,
            @NonNull LinkType linkType,
            @Nullable String passcode,
            long createdAt) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.linkType = linkType;
        this.passcode = passcode;
        this.createdAt = createdAt;
    }

    /**
     * 由 url + linkType 生成稳定去重 id。
     *
     * @param url      链接
     * @param linkType 链接类型
     * @return 形如 {@code <url>|<LINK_TYPE>} 的 id
     */
    @NonNull
    public static String idFor(@NonNull String url, @NonNull LinkType linkType) {
        return (url == null ? "" : url) + "|" + (linkType == null ? "UNKNOWN" : linkType.name());
    }

    /**
     * 便捷工厂：自动计算 id 与创建时间。
     *
     * @param url       链接
     * @param linkType  链接类型
     * @param title     展示标题
     * @param passcode  运行期提取码（可为 null）
     * @return 新建的收藏项
     */
    @NonNull
    public static FavoriteItem create(
            @NonNull String url,
            @NonNull LinkType linkType,
            @NonNull String title,
            @Nullable String passcode) {
        return new FavoriteItem(
                idFor(url, linkType),
                title,
                url,
                linkType,
                passcode,
                System.currentTimeMillis());
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @NonNull
    public LinkType getLinkType() {
        return linkType;
    }

    @Nullable
    public String getPasscode() {
        return passcode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    @NonNull
    @Override
    public String toString() {
        return "FavoriteItem{id='" + id + "', title='" + title + "', url='" + url
                + "', linkType=" + linkType + '}';
    }
}
