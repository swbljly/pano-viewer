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
import com.example.panoviewer.favorites.FavoriteItem;

import java.util.List;

/**
 * 收藏列表适配器：每行展示标题 + 地址 + 类型 + 「打开」/「删除」按钮。
 */
public class FavoriteItemAdapter extends ArrayAdapter<FavoriteItem> {

    /** 行内动作回调。 */
    public interface OnItemAction {
        void onOpen(@NonNull FavoriteItem item);

        void onDelete(@NonNull FavoriteItem item);
    }

    @NonNull
    private final OnItemAction action;

    public FavoriteItemAdapter(@NonNull Context context, @NonNull OnItemAction action) {
        super(context, R.layout.item_favorite, R.id.item_title);
        this.action = action;
    }

    public FavoriteItemAdapter(
            @NonNull Context context,
            @NonNull List<FavoriteItem> items,
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
                    .inflate(R.layout.item_favorite, parent, false);
        }
        final FavoriteItem item = getItem(position);
        if (item == null) {
            return v;
        }
        TextView title = v.findViewById(R.id.item_title);
        TextView url = v.findViewById(R.id.item_url);
        TextView type = v.findViewById(R.id.item_type);
        title.setText(item.getTitle());
        url.setText(item.getUrl());
        type.setText(item.getLinkType().name());

        v.findViewById(R.id.btn_open).setOnClickListener(view -> action.onOpen(item));
        v.findViewById(R.id.btn_delete).setOnClickListener(view -> action.onDelete(item));
        return v;
    }
}
