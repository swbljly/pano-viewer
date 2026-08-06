package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.panoviewer.net.HttpHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页图片解析器：抓取 HTML，用正则提取 {@code <img>} 的 {@code src} /
 * {@code data-src} / {@code data-original}（覆盖单引号 / 双引号 / 无引号），
 * 转为绝对 URL，过滤图片扩展名并去重。
 *
 * <p>依赖注入 {@link HttpHelper}（便于单测用 fake 返回样例 HTML）。</p>
 */
public class WebPageImageResolver implements LinkResolver {

    /** 匹配 <img ... src|data-src|data-original = "..." | '...' | 无引号。 */
    private static final Pattern IMG_PATTERN = Pattern.compile(
            "<img\\b[^>]*?\\b(?:src|data-src|data-original)\\s*=\\s*"
                    + "(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            Pattern.CASE_INSENSITIVE);

    @NonNull
    private final HttpHelper httpHelper;

    public WebPageImageResolver(@NonNull HttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }

    @NonNull
    @Override
    public LinkType detect(@NonNull String url) {
        return LinkType.WEBPAGE;
    }

    @NonNull
    @Override
    public List<ResolvedItem> resolve(@NonNull String url, @Nullable String passcode)
            throws Exception {
        String html = httpHelper.getString(url);
        Set<String> seen = new LinkedHashSet<>();
        List<ResolvedItem> result = new ArrayList<>();

        Matcher m = IMG_PATTERN.matcher(html);
        while (m.find()) {
            // 命中三种引号写法之一：group(2)=双引号内容, group(3)=单引号内容, group(4)=无引号内容
            String src = m.group(2);
            if (src == null) {
                src = m.group(3);
            }
            if (src == null) {
                src = m.group(4);
            }
            if (src == null || src.isEmpty()) {
                continue;
            }
            String absolute = LinkUtils.toAbsoluteUrl(url, src.trim());
            // 只保留图片扩展名
            if (!LinkUtils.isImageUrl(absolute)) {
                continue;
            }
            if (seen.add(absolute)) {
                String title = LinkUtils.fileNameFromUrl(absolute);
                result.add(new ResolvedItem(absolute, title, LinkType.WEBPAGE, 0, null));
            }
        }
        return result;
    }
}
