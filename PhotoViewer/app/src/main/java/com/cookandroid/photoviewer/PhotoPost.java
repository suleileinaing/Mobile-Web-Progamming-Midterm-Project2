package com.cookandroid.photoviewer;

import android.graphics.Bitmap;

public class PhotoPost {

    public final long id;
    public final long authorId;
    public final String title;
    public final String text;
    public final String publishedDate;
    public final String imageUrl;
    public final Bitmap image;

    public PhotoPost(
            long id,
            long authorId,
            String title,
            String text,
            String publishedDate,
            String imageUrl,
            Bitmap image
    ) {
        this.id = id;
        this.authorId = authorId;
        this.title = title;
        this.text = text;
        this.publishedDate = publishedDate;
        this.imageUrl = imageUrl;
        this.image = image;
    }
}