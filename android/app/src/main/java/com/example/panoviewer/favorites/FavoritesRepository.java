package com.example.panoviewer.favorites;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 收藏仓储：内存缓存 + 持久化。构造注入 {@link FavoritesDataSource}。
 *
 * <p>行为：</p>
 * <ul>
 *   <li>{@link #add(FavoriteItem)} 用 {@code url + linkType} 生成的 id 去重；</li>
 *   <li>{@link #remove(String)} 按 id 删除；</li>
 *   <li>增删后自动调用数据源 {@code save}；</li>
 *   <li>序列化字段由数据源负责（见 {@code SharedPreferencesFavoritesDataSource}），
 *       不写 passcode。</li>
 * </ul>
 */
public class FavoritesRepository {

    @NonNull
    private final FavoritesDataSource dataSource;

    @NonNull
    private final List<FavoriteItem> cache = new ArrayList<>();

    public FavoritesRepository(@NonNull FavoritesDataSource dataSource) {
        this.dataSource = dataSource;
        this.cache.addAll(dataSource.load());
    }

    /** 从数据源重新加载（用于从其它页面返回后保持缓存新鲜）。 */
    public synchronized void reload() {
        cache.clear();
        cache.addAll(dataSource.load());
    }

    /**
     * 新增 / 替换收藏（按 id 去重）。
     */
    public synchronized void add(@NonNull FavoriteItem item) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).getId().equals(item.getId())) {
                cache.set(i, item);
                persist();
                return;
            }
        }
        cache.add(item);
        persist();
    }

    /**
     * 按 id 删除。
     */
    public synchronized void remove(@NonNull String id) {
        boolean removed = cache.removeIf(i -> i.getId().equals(id));
        if (removed) {
            persist();
        }
    }

    /**
     * 返回全部收藏（副本，避免外部修改内部缓存）。
     */
    @NonNull
    public synchronized List<FavoriteItem> getAll() {
        return new ArrayList<>(cache);
    }

    /**
     * 是否已收藏该 url。
     */
    public synchronized boolean contains(@NonNull String url) {
        for (FavoriteItem i : cache) {
            if (i.getUrl().equals(url)) {
                return true;
            }
        }
        return false;
    }

    private void persist() {
        dataSource.save(new ArrayList<>(cache));
    }
}
