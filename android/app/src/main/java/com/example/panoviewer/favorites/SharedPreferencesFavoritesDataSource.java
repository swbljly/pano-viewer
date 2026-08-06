package com.example.panoviewer.favorites;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.panoviewer.link.LinkType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 {@link SharedPreferences} 的收藏数据源。
 *
 * <p>存储 key：{@code pano_favorites_v1}；JSON 结构为
 * {@code [{id,title,url,linkType,createdAt}]}，<b>不写入 passcode</b>。</p>
 */
public class SharedPreferencesFavoritesDataSource implements FavoritesDataSource {

    private static final String PREF_NAME = "pano_prefs";
    private static final String KEY = "pano_favorites_v1";

    @NonNull
    private final SharedPreferences sp;

    public SharedPreferencesFavoritesDataSource(@NonNull Context context) {
        this.sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public List<FavoriteItem> load() {
        List<FavoriteItem> list = new ArrayList<>();
        String json = sp.getString(KEY, null);
        if (json == null) {
            return list;
        }
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                FavoriteItem item = new FavoriteItem(
                        o.getString("id"),
                        o.optString("title", ""),
                        o.getString("url"),
                        LinkType.valueOf(o.optString("linkType", "UNKNOWN")),
                        null, // passcode 不持久化
                        o.optLong("createdAt", 0L));
                list.add(item);
            }
        } catch (JSONException e) {
            // 数据损坏：返回空列表，丢弃旧数据
            list.clear();
        }
        return list;
    }

    @Override
    public void save(@NonNull List<FavoriteItem> items) {
        try {
            JSONArray arr = new JSONArray();
            for (FavoriteItem it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.getId());
                o.put("title", it.getTitle());
                o.put("url", it.getUrl());
                o.put("linkType", it.getLinkType().name());
                o.put("createdAt", it.getCreatedAt());
                // 注意：不写 passcode
                arr.put(o);
            }
            sp.edit().putString(KEY, arr.toString()).apply();
        } catch (JSONException e) {
            // 序列化失败：静默忽略，不影响主流程
        }
    }
}
