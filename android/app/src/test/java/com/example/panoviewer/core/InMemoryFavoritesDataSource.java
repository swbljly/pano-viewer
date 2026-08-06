package com.example.panoviewer.core;

import androidx.annotation.NonNull;

import com.example.panoviewer.favorites.FavoriteItem;
import com.example.panoviewer.favorites.FavoritesDataSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存版 {@link FavoritesDataSource}：单测用，不做 JSON 持久化（仅维护内存副本）。
 */
public class InMemoryFavoritesDataSource implements FavoritesDataSource {

    @NonNull
    private List<FavoriteItem> store = new ArrayList<>();

    @NonNull
    @Override
    public List<FavoriteItem> load() {
        return new ArrayList<>(store);
    }

    @Override
    public void save(@NonNull List<FavoriteItem> items) {
        store = new ArrayList<>(items);
    }
}
