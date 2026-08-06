package com.example.panoviewer.core;

import com.example.panoviewer.favorites.FavoriteItem;
import com.example.panoviewer.favorites.FavoritesRepository;
import com.example.panoviewer.link.LinkType;

/**
 * {@link FavoritesRepository} 验证：add/remove/contains + JSON 序列化往返
 * （经由内存 dataSource 模拟持久化）。
 */
public class FavoritesRepositoryTest {

    public static void main(String[] args) {
        InMemoryFavoritesDataSource ds = new InMemoryFavoritesDataSource();
        FavoritesRepository repo = new FavoritesRepository(ds);

        Assert.assertEquals(0, repo.getAll().size());

        FavoriteItem a = FavoriteItem.create(
                "https://x.com/a.jpg", LinkType.DIRECT_IMAGE, "a.jpg", null);
        repo.add(a);
        Assert.assertEquals(1, repo.getAll().size());
        Assert.assertTrue(repo.contains("https://x.com/a.jpg"));

        // id 去重（同 url + linkType）：title 被更新，数量仍为 1
        FavoriteItem a2 = FavoriteItem.create(
                "https://x.com/a.jpg", LinkType.DIRECT_IMAGE, "a2.jpg", null);
        repo.add(a2);
        Assert.assertEquals(1, repo.getAll().size());
        Assert.assertEquals("a2.jpg", repo.getAll().get(0).getTitle());

        // 序列化往返：用同一数据源新建仓库，数据应一致
        FavoritesRepository repo2 = new FavoritesRepository(ds);
        Assert.assertEquals(1, repo2.getAll().size());
        Assert.assertEquals("a2.jpg", repo2.getAll().get(0).getTitle());

        // 删除
        repo.remove(a.getId());
        Assert.assertEquals(0, repo.getAll().size());
        FavoritesRepository repo3 = new FavoritesRepository(ds);
        Assert.assertEquals(0, repo3.getAll().size());

        System.out.println("FavoritesRepositoryTest PASS");
    }
}
