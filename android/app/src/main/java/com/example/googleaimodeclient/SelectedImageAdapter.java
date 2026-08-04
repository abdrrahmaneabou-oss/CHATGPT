package com.example.googleaimodeclient;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class SelectedImageAdapter extends RecyclerView.Adapter<SelectedImageAdapter.ImageHolder> {
    interface Listener {
        void onRemoveImage(int position);
    }

    private final List<Uri> images;
    private final Listener listener;
    private final ThumbnailLoader thumbnailLoader;

    SelectedImageAdapter(List<Uri> images, Listener listener, ThumbnailLoader thumbnailLoader) {
        this.images = images;
        this.listener = listener;
        this.thumbnailLoader = thumbnailLoader;
        setHasStableIds(true);
    }

    @NonNull
    @Override
    public ImageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_selected_image, parent, false);
        return new ImageHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageHolder holder, int position) {
        Uri uri = images.get(position);
        int displayPosition = position + 1;
        holder.index.setText(String.format(Locale.getDefault(), "%02d", displayPosition));
        holder.thumbnail.setContentDescription(
                holder.itemView.getContext().getString(
                        R.string.content_description_selected_image,
                        displayPosition
                )
        );
        holder.remove.setContentDescription(
                holder.itemView.getContext().getString(R.string.remove_image, displayPosition)
        );
        holder.remove.setOnClickListener(view -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition != RecyclerView.NO_POSITION) {
                listener.onRemoveImage(adapterPosition);
            }
        });
        thumbnailLoader.load(uri, holder.thumbnail);
    }

    @Override
    public long getItemId(int position) {
        return images.get(position).toString().hashCode();
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    boolean move(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= images.size() || to >= images.size()) {
            return false;
        }
        Collections.swap(images, from, to);
        notifyItemMoved(from, to);
        int start = Math.min(from, to);
        notifyItemRangeChanged(start, Math.abs(from - to) + 1);
        return true;
    }

    static final class ImageHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView thumbnail;
        final TextView index;
        final ImageButton remove;

        ImageHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnail);
            index = itemView.findViewById(R.id.index);
            remove = itemView.findViewById(R.id.remove);
        }
    }
}

