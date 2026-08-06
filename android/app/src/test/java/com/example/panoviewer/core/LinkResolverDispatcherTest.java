package com.example.panoviewer.core;

import com.example.panoviewer.link.DirectImageResolver;
import com.example.panoviewer.link.LinkResolverDispatcher;
import com.example.panoviewer.link.LinkType;
import com.example.panoviewer.link.QuarkShareResolver;
import com.example.panoviewer.link.ResolveException;
import com.example.panoviewer.link.ResolvedItem;
import com.example.panoviewer.link.WebPageImageResolver;

import java.util.List;

/**
 * {@link LinkResolverDispatcher#detect} 识别 + 直链派发 验证。
 */
public class LinkResolverDispatcherTest {

    public static void main(String[] args) {
        LinkResolverDispatcher dispatcher = new LinkResolverDispatcher(
                new QuarkShareResolver(new FakeHttpHelper()),
                new WebPageImageResolver(new FakeHttpHelper()),
                new DirectImageResolver());

        // ---- detect 识别 ----
        Assert.assertEquals(LinkType.QUARK_SHARE, dispatcher.detect("https://pan.quark.cn/s/abc123"));
        Assert.assertEquals(LinkType.QUARK_SHARE, dispatcher.detect("https://quark.cn/s/xyz"));
        Assert.assertEquals(LinkType.WEBPAGE, dispatcher.detect("https://example.com/gallery"));
        Assert.assertEquals(LinkType.DIRECT_IMAGE, dispatcher.detect("https://cdn.com/a.jpg"));
        Assert.assertEquals(LinkType.DIRECT_IMAGE, dispatcher.detect("http://x.com/p/photo.PNG?k=1"));
        Assert.assertEquals(LinkType.UNKNOWN, dispatcher.detect("ftp://x.com/a.jpg"));
        Assert.assertEquals(LinkType.UNKNOWN, dispatcher.detect("这不是链接"));

        // ---- UNKNOWN 派发抛异常 ----
        boolean threw = false;
        try {
            dispatcher.dispatch("ftp://x.com/a.jpg", null);
        } catch (ResolveException e) {
            threw = true;
        }
        Assert.assertTrue(threw);

        // ---- 直链派发返回单条 ----
        List<ResolvedItem> items = dispatcher.dispatch("https://cdn.com/a.jpg", null);
        Assert.assertEquals(1, items.size());
        Assert.assertEquals("a.jpg", items.get(0).getTitle());
        Assert.assertEquals(LinkType.DIRECT_IMAGE, items.get(0).getSourceType());

        System.out.println("LinkResolverDispatcherTest PASS");
    }
}
