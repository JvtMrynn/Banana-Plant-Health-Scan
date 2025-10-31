package com.example.capstoneprojectapp;

import android.graphics.Bitmap;

/**
 * Singleton class to temporarily hold image data between activities
 * Avoids Intent size limitations when passing large bitmaps
 */
public class ImageHolder {
    private static ImageHolder instance;
    private Bitmap currentImage;

    private ImageHolder() {
    }

    public static ImageHolder getInstance() {
        if (instance == null) {
            instance = new ImageHolder();
        }
        return instance;
    }

    public void setImage(Bitmap image) {
        this.currentImage = image;
    }

    public Bitmap getImage() {
        return currentImage;
    }

    public void clearImage() {
        if (currentImage != null && !currentImage.isRecycled()) {
            currentImage = null;
        }
    }
}
