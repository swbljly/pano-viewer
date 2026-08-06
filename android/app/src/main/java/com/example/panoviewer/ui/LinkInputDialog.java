package com.example.panoviewer.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.panoviewer.R;
import com.example.panoviewer.favorites.FavoriteItem;
import com.example.panoviewer.favorites.FavoritesRepository;
import com.example.panoviewer.link.LinkResolverDispatcher;
import com.example.panoviewer.link.LinkType;
import com.example.panoviewer.link.ResolveException;
import com.example.panoviewer.link.ResolvedItem;
import com.example.panoviewer.net.ImageDownloader;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 「链接查看」输入面板（Dialog 形态）。
 *
 * <p>流程：输入 URL（+ 可选提取码）→ 后台 dispatch 解析 → 主线程填充结果列表；
 * 每行可「查看」（回调 {@link Host#onResolvedChosen} 触发下载注入）或「☆ 收藏」。</p>
 */
public class LinkInputDialog extends Dialog implements ResolvedItemAdapter.OnItemAction {

    /** 解析结果被「查看」后的回调（通常为 MainActivity）。 */
    public interface Host {
        void onResolvedChosen(@NonNull ResolvedItem item);
    }

    @NonNull
    private final LinkResolverDispatcher dispatcher;
    @NonNull
    private final ImageDownloader imageDownloader;
    @NonNull
    private final FavoritesRepository favoritesRepo;
    @NonNull
    private final Host host;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText urlInput;
    private EditText passcodeInput;
    private ListView resultsList;
    private ResolvedItemAdapter adapter;

    public LinkInputDialog(
            @NonNull Context context,
            @NonNull LinkResolverDispatcher dispatcher,
            @NonNull ImageDownloader imageDownloader,
            @NonNull FavoritesRepository favoritesRepo,
            @NonNull Host host) {
        super(context);
        this.dispatcher = dispatcher;
        this.imageDownloader = imageDownloader;
        this.favoritesRepo = favoritesRepo;
        this.host = host;
    }

    @Override
    protected void onCreate(@Nullable android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_link_input);
        setTitle(R.string.dialog_link_title);

        urlInput = findViewById(R.id.input_url);
        passcodeInput = findViewById(R.id.input_passcode);
        resultsList = findViewById(R.id.list_results);
        Button parseBtn = findViewById(R.id.btn_parse);

        adapter = new ResolvedItemAdapter(getContext(), this);
        resultsList.setAdapter(adapter);

        parseBtn.setOnClickListener(v -> doParse());
    }

    /** 校验并后台解析。 */
    private void doParse() {
        String url = urlInput.getText().toString().trim();
        String passcode = passcodeInput.getText().toString().trim();

        if (TextUtils.isEmpty(url)) {
            Toast.makeText(getContext(), R.string.e_empty_input, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidUrl(url)) {
            Toast.makeText(getContext(), R.string.e_invalid_url, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(getContext(), R.string.toast_loading, Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            try {
                List<ResolvedItem> items = dispatcher.dispatch(
                        url, passcode.isEmpty() ? null : passcode);
                mainHandler.post(() -> {
                    adapter.clear();
                    if (items == null || items.isEmpty()) {
                        Toast.makeText(getContext(), R.string.e_no_image, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    adapter.addAll(items);
                });
            } catch (ResolveException re) {
                mainHandler.post(() ->
                        Toast.makeText(getContext(), R.string.e_resolve_quark, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                mainHandler.post(() ->
                        Toast.makeText(getContext(), R.string.e_network, Toast.LENGTH_SHORT).show());
            }
        });
    }

    /** 仅接受 http(s) 链接。 */
    private static boolean isValidUrl(@NonNull String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    // ---- ResolvedItemAdapter.OnItemAction ----

    @Override
    public void onView(@NonNull ResolvedItem item) {
        host.onResolvedChosen(item);
        dismiss();
    }

    @Override
    public void onFavorite(@NonNull ResolvedItem item) {
        FavoriteItem fav = FavoriteItem.create(
                item.getUrl(), item.getSourceType(), item.getTitle(), null);
        favoritesRepo.add(fav);
        Toast.makeText(getContext(), R.string.fav_added, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStop() {
        super.onStop();
        executor.shutdownNow();
    }
}
