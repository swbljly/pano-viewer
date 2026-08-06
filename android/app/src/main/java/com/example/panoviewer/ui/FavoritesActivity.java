package com.example.panoviewer.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.panoviewer.MainActivity;
import com.example.panoviewer.R;
import com.example.panoviewer.favorites.FavoriteItem;
import com.example.panoviewer.favorites.FavoritesRepository;
import com.example.panoviewer.favorites.SharedPreferencesFavoritesDataSource;

/**
 * 链接收藏页：列表展示收藏项，可「打开」（回传 MainActivity 复看）或「删除」。
 *
 * <p>「打开」实现：{@code finish()} 并以 extra 启动 {@code MainActivity}
 * （singleTask），由 {@code MainActivity.onNewIntent} 处理并加载，避免跨 Activity
 * 直接操作 WebView。</p>
 */
public class FavoritesActivity extends AppCompatActivity
        implements FavoriteItemAdapter.OnItemAction {

    private FavoritesRepository repo;
    private FavoriteItemAdapter adapter;
    private TextView emptyHint;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // 独立的仓库实例，读写同一份 SharedPreferences
        repo = new FavoritesRepository(new SharedPreferencesFavoritesDataSource(this));

        ListView list = findViewById(R.id.list_favorites);
        emptyHint = findViewById(R.id.empty_hint);

        adapter = new FavoriteItemAdapter(this, repo.getAll(), this);
        list.setAdapter(adapter);
        refreshEmptyState();
    }

    private void refreshEmptyState() {
        if (adapter.getCount() == 0) {
            emptyHint.setVisibility(View.VISIBLE);
        } else {
            emptyHint.setVisibility(View.GONE);
        }
    }

    // ---- FavoriteItemAdapter.OnItemAction ----

    @Override
    public void onOpen(@NonNull FavoriteItem item) {
        // 回传 MainActivity 复看（singleTask -> onNewIntent）
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_FAVORITE_URL, item.getUrl());
        intent.putExtra(MainActivity.EXTRA_FAVORITE_TYPE, item.getLinkType().name());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();
    }

    @Override
    public void onDelete(@NonNull FavoriteItem item) {
        repo.remove(item.getId());
        adapter.clear();
        adapter.addAll(repo.getAll());
        refreshEmptyState();
    }
}
