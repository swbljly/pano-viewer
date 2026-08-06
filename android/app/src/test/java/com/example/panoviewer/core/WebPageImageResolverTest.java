package com.example.panoviewer.core;

import com.example.panoviewer.link.ResolvedItem;
import com.example.panoviewer.link.WebPageImageResolver;

import java.util.List;

/**
 * {@link WebPageImageResolver} 验证：注入 fake HttpHelper 返回样例 HTML，
 * 确认能抽出 img 绝对 URL、过滤非图片、去重。
 */
public class WebPageImageResolverTest {

    public static void main(String[] args) throws Exception {
        String base = "https://example.com/gallery/page.html";
        String html = "<html><body>"
                + "<img src=\"https://cdn.com/a.jpg\">"                       // 双引号绝对
                + "<img data-src='/relative/b.png' >"                          // data-src 相对
                + "<img src=//other.com/c.webp >"                              // 无引号 protocol-relative
                + "<img src=\"style.css\">"                                    // 非图片，应过滤
                + "<img data-original=\"https://x.com/d.gif\">"                // data-original
                + "<img src=\"https://cdn.com/a.jpg\">"                        // 与第一条重复，应去重
                + "</body></html>";

        FakeHttpHelper fake = new FakeHttpHelper();
        fake.setStringResponse(html);

        WebPageImageResolver resolver = new WebPageImageResolver(fake);
        List<ResolvedItem> items = resolver.resolve(base, null);

        // 期望：a.jpg, b.png(绝对化), c.webp(绝对化), d.gif = 4 条；style.css 被过滤；重复去重
        Assert.assertEquals(4, items.size());

        boolean hasAbsoluteB = false;
        boolean hasAbsoluteC = false;
        for (ResolvedItem it : items) {
            if (it.getUrl().equals("https://example.com/relative/b.png")) {
                hasAbsoluteB = true;
            }
            if (it.getUrl().equals("https://other.com/c.webp")) {
                hasAbsoluteC = true;
            }
            if (it.getUrl().contains("style.css")) {
                Assert.fail("非图片不应被保留: " + it.getUrl());
            }
        }
        Assert.assertTrue(hasAbsoluteB);
        Assert.assertTrue(hasAbsoluteC);

        System.out.println("WebPageImageResolverTest PASS");
    }
}
