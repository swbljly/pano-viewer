package com.example.panoviewer.favorites;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 收藏持久化数据源接口（与具体存储解耦，便于单测用内存实现替换）。
 */
public interface FavoritesDataSource {

    /**
     * 读取全部收藏项（存储损坏时返回空列表，不应抛异常）。
     */
    @NonNull
    List<FavoriteItem> load();

    /**
     * 写入全部收藏项。
     */
    void save(@NonNull List<FavoriteItem> items);
}
