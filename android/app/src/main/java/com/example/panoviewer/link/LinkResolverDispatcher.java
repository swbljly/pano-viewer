package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 链接解析调度器：持有多种 {@link LinkResolver}，按 URL 特征识别类型并派发。
 *
 * <p>识别规则：</p>
 * <ul>
 *   <li>host 含 {@code pan.quark.cn} → {@link LinkType#QUARK_SHARE}；</li>
 *   <li>否则以图片扩展名结尾 → {@link LinkType#DIRECT_IMAGE}；</li>
 *   <li>否则为 http(s) → {@link LinkType#WEBPAGE}；</li>
 *   <li>否则 → {@link LinkType#UNKNOWN}（报错）。</li>
 * </ul>
 */
public class LinkResolverDispatcher {

    @NonNull
    private final List<LinkResolver> resolvers;

    public LinkResolverDispatcher(@NonNull LinkResolver... resolvers) {
        this.resolvers = new ArrayList<>(Arrays.asList(resolvers));
    }

    public LinkResolverDispatcher(@NonNull List<LinkResolver> resolvers) {
        this.resolvers = new ArrayList<>(resolvers);
    }

    /**
     * 识别链接类型（纯 URL 规则，不涉及网络）。
     */
    @NonNull
    public LinkType detect(@NonNull String url) {
        if (url == null) {
            return LinkType.UNKNOWN;
        }
        String u = url.trim().toLowerCase();
        if (u.contains("pan.quark.cn") || u.contains("quark.cn/s/")) {
            return LinkType.QUARK_SHARE;
        }
        // PRD 支持的三类链接（夸克/网页/直链）均为 http(s)；非 http(s) 一律判 UNKNOWN。
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            return LinkType.UNKNOWN;
        }
        if (LinkUtils.isImageUrl(u)) {
            return LinkType.DIRECT_IMAGE;
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return LinkType.WEBPAGE;
        }
        return LinkType.UNKNOWN;
    }

    /**
     * 派发解析：选中对应该类型的解析器执行 {@code resolve}。
     *
     * @param url      原始链接
     * @param passcode 可选提取码
     * @return 解析出的条目列表
     * @throws ResolveException 无法识别或没有对应解析器 / 解析失败时抛出
     */
    @NonNull
    public List<ResolvedItem> dispatch(@NonNull String url, @Nullable String passcode)
            throws ResolveException {
        LinkType type = detect(url);
        if (type == LinkType.UNKNOWN) {
            throw new ResolveException("无法识别的链接类型");
        }
        for (LinkResolver r : resolvers) {
            if (r.detect(url) == type) {
                try {
                    return r.resolve(url, passcode);
                } catch (ResolveException re) {
                    throw re;
                } catch (Exception e) {
                    throw new ResolveException("解析失败：" + e.getMessage(), e);
                }
            }
        }
        throw new ResolveException("没有对应的解析器：" + type);
    }
}
