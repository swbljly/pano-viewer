package com.example.panoviewer.link;

/**
 * 链接类型枚举。
 *
 * <ul>
 *   <li>{@link #QUARK_SHARE}：夸克网盘分享链接（pan.quark.cn）。</li>
 *   <li>{@link #WEBPAGE}：普通网页（需解析其中的 &lt;img&gt;）。</li>
 *   <li>{@link #DIRECT_IMAGE}：可直接下载的图片直链。</li>
 *   <li>{@link #UNKNOWN}：无法识别的链接，应报错。</li>
 * </ul>
 */
public enum LinkType {
    QUARK_SHARE,
    WEBPAGE,
    DIRECT_IMAGE,
    UNKNOWN
}
