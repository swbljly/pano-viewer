package com.example.panoviewer.core;

import com.example.panoviewer.net.ImageDownloader;

import java.util.Base64;

/**
 * {@link ImageDownloader} 验证：注入 fake HttpHelper 返回固定字节，
 * 确认生成正确的 {@code data:image/<mime>;base64,} 前缀与 base64 内容。
 */
public class ImageDownloaderTest {

    public static void main(String[] args) throws Exception {
        byte[] data = {1, 2, 3, 4, 5};
        FakeHttpHelper fake = new FakeHttpHelper();
        fake.setBytesResponse(data);

        ImageDownloader downloader = new ImageDownloader(fake);

        String jpg = downloader.downloadToDataUrl("https://x.com/p/photo.jpg");
        Assert.assertTrue(jpg.startsWith("data:image/jpeg;base64,"));
        String jpgB64 = jpg.substring("data:image/jpeg;base64,".length());
        Assert.assertEquals(Base64.getEncoder().encodeToString(data), jpgB64);

        String png = downloader.downloadToDataUrl("https://x.com/p/photo.png?x=1");
        Assert.assertTrue(png.startsWith("data:image/png;base64,"));

        String webp = downloader.downloadToDataUrl("https://x.com/p/photo.webp");
        Assert.assertTrue(webp.startsWith("data:image/webp;base64,"));

        // 未知扩展名回退 jpeg
        String fallback = downloader.downloadToDataUrl("https://x.com/p/photo");
        Assert.assertTrue(fallback.startsWith("data:image/jpeg;base64,"));

        System.out.println("ImageDownloaderTest PASS");
    }
}
