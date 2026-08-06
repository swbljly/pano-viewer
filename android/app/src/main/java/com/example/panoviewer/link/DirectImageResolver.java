package com.example.panoviewer.link;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片直链解析器：url 本身即为可下载图片地址，返回单条 {@link ResolvedItem}。
 */
public class DirectImageResolver implements LinkResolver {

    @NonNull
    @Override
    public LinkType detect(@NonNull String url) {
        return LinkType.DIRECT_IMAGE;
    }

    @NonNull
    @Override
    public List<ResolvedItem> resolve(@NonNull String url, @NonNull String passcode) {
        List<ResolvedItem> list = new ArrayList<>();
        String title = LinkUtils.fileNameFromUrl(url);
        list.add(new ResolvedItem(url, title, LinkType.DIRECT_IMAGE, 0, null));
        return list;
    }
}
