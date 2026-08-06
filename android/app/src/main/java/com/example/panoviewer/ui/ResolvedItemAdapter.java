package com.example.panoviewer.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.panoviewer.R;
import com.example.panoviewer.link.ResolvedItem;

import java.util.List;

/**
 * 解析结果列表适配器：每行展示标题 + 地址 + 「查看」/「☆ 收藏」按钮。
 */
public class ResolvedItemAdapter extends ArrayAdapter<ResolvedItem> {

    /** 行内动作回调。 */
    public interface OnItemAction {
        void onView(@NonNull ResolvedItem item);

        void onFavorite(@NonNull ResolvedItem item);
    }

    @NonNull
    private final OnItemAction action;

    public ResolvedItemAdapter(@NonNull Context context, @NonNull OnItemAction action) {
        super(context, R.layout.item_resolved, R.id.item_title);
        this.action = action;
    }

    public ResolvedItemAdapter(
            @NonNull Context context,
            @NonNull List<ResolvedItem> items,
            @NonNull OnItemAction action) {
        this(context, action);
        addAll(items);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_resolved, parent, false);
        }
        final ResolvedItem item = getItem(position);
        if (item == null) {
            return v;
        }
        TextView title = v.findViewById(R.id.item_title);
        TextView url = v.findViewById(R.id.item_url);
        title.setText(item.getTitle());
        url.setText(item.getUrl());

        v.findViewById(R.id.btn_view).setOnClickListener(view -> action.onView(item));
        v.findViewById(R.id.btn_fav).setOnClickListener(view -> action.onFavorite(item));
        return v;
    }
}
