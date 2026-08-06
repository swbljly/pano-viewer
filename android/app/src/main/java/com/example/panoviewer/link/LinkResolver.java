package com.example.panoviewer.link;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 链接解析器接口。
 *
 * <p>每个实现负责一种 {@link LinkType}：{@link #detect(String)} 声明自己能处理哪类
 * URL；{@link #resolve(String, String)} 产出可下载项列表。</p>
 */
public interface LinkResolver {

    /**
     * 声明此解析器处理的链接类型（用于调度器按类型匹配）。
     */
    @NonNull
    LinkType detect(@NonNull String url);

    /**
     * 解析链接，产出可下载项列表。
     *
     * @param url      原始链接
     * @param passcode 可选的提取码（网页/直链忽略）
     * @return 解析出的条目（可能为空列表，表示未找到图片）
     * @throws Exception 解析失败（如夸克接口变更）时抛出
     */
    @NonNull
    List<ResolvedItem> resolve(@NonNull String url, @Nullable String passcode) throws Exception;
}
