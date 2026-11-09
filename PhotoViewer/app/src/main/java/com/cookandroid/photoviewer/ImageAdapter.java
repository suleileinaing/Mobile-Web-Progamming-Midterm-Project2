package com.cookandroid.photoviewer;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;
import java.util.function.Consumer;

public class ImageAdapter
        extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private final long currentUserId;

    private final List<PhotoPost> imageList;

    private final Runnable onDetailClosed;

    private final Consumer<PhotoPost> onDeleteRequested;
    public ImageAdapter(
            List<PhotoPost> imageList,
            Runnable onDetailClosed,
            long currentUserId,
            Consumer<PhotoPost> onDeleteRequested
    ) {
        this.imageList = imageList;
        this.onDetailClosed = onDetailClosed;
        this.currentUserId = currentUserId;
        this.onDeleteRequested = onDeleteRequested;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);

        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ImageViewHolder holder,
            int position) {

        PhotoPost post = imageList.get(position);

        holder.imageView.setImageBitmap(post.image);

        holder.imageView.setOnClickListener(v -> {
            View detail = LayoutInflater.from(v.getContext())
                    .inflate(R.layout.dialog_post_detail, null);

            TextView title = detail.findViewById(R.id.detailTitle);
            TextView text = detail.findViewById(R.id.detailText);
            ImageView image = detail.findViewById(R.id.detailImage);

            title.setText(post.title);
            text.setText(post.text);
            image.setImageBitmap(post.image);

            Button deleteButton = detail.findViewById(
                    R.id.btnDeletePost
            );

            boolean isOwner = currentUserId > 0
                    && post.authorId == currentUserId;

            deleteButton.setVisibility(
                    isOwner ? View.VISIBLE : View.GONE
            );


            image.setOnClickListener(imageView ->
                    showZoom(imageView.getContext(), post.image)
            );

            SharedPreferences savedPosts =
                    v.getContext().getSharedPreferences(
                            "saved_posts",
                            Context.MODE_PRIVATE
                    );

            String savedKey = "post_" + post.id;

            Button favoriteButton = detail.findViewById(
                    R.id.btnFavorite
            );

            favoriteButton.setText(
                    savedPosts.getBoolean(savedKey, false)
                            ? "♥" : "♡"
            );

            favoriteButton.setOnClickListener(favoriteView -> {
                boolean newSavedState =
                        !savedPosts.getBoolean(savedKey, false);

                savedPosts.edit()
                        .putBoolean(savedKey, newSavedState)
                        .apply();

                favoriteButton.setText(
                        newSavedState ? "♥" : "♡"
                );
            });

            AlertDialog dialog =
                    new AlertDialog.Builder(v.getContext())
                            .setView(detail)
                            .create();

            detail.findViewById(R.id.btnCloseDetail)
                    .setOnClickListener(closeView ->
                            dialog.dismiss()
                    );

            dialog.setCanceledOnTouchOutside(true);
            dialog.setOnDismissListener(ignored -> {
                if (onDetailClosed != null) {
                    onDetailClosed.run();
                }
            });

            deleteButton.setOnClickListener(deleteView -> {
                new AlertDialog.Builder(v.getContext())
                        .setTitle("게시물 삭제")
                        .setMessage(
                                "이 게시물을 삭제하시겠습니까?\n"
                                        + "삭제 후에는 되돌릴 수 없습니다."
                        )
                        .setNegativeButton("취소", null)
                        .setPositiveButton("삭제", (confirmation, which) -> {
                            dialog.dismiss();
                            onDeleteRequested.accept(post);
                        })
                        .show();
            });

            dialog.show();

            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT)
                );
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public static class ImageViewHolder
            extends RecyclerView.ViewHolder {

        final ImageView imageView;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem);
        }
    }

    private void showZoom(Context context, Bitmap bitmap) {
        Dialog zoomDialog = new Dialog(context);
        zoomDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        zoomDialog.setContentView(R.layout.dialog_photo_zoom);

        ZoomImageView zoomImage = zoomDialog.findViewById(
                R.id.zoomImage
        );
        zoomImage.setImageBitmap(bitmap);

        zoomDialog.findViewById(R.id.btnZoomIn)
                .setOnClickListener(v -> zoomImage.zoomIn());

        zoomDialog.findViewById(R.id.btnZoomOut)
                .setOnClickListener(v -> zoomImage.zoomOut());

        zoomDialog.findViewById(R.id.btnCloseZoom)
                .setOnClickListener(v -> zoomDialog.dismiss());

        zoomDialog.show();

        Window window = zoomDialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.BLACK)
            );

            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT
            );
        }
        zoomImage.post(() -> zoomImage.resetZoom());
    }
}