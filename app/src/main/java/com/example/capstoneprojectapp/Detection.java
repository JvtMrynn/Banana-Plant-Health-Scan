package com.example.capstoneprojectapp;

import android.graphics.RectF;

/**
 * Represents a single object detection result from YOLOv8
 */
public class Detection {
    private final RectF boundingBox;  // Normalized coordinates (0-1)
    private final float confidence;
    private final int classId;
    private final String className;

    public Detection(RectF boundingBox, float confidence, int classId, String className) {
        this.boundingBox = boundingBox;
        this.confidence = confidence;
        this.classId = classId;
        this.className = className;
    }

    public RectF getBoundingBox() {
        return boundingBox;
    }

    public float getConfidence() {
        return confidence;
    }

    public int getClassId() {
        return classId;
    }

    public String getClassName() {
        return className;
    }

    /**
     * Get bounding box scaled to image dimensions
     */
    public RectF getScaledBoundingBox(int imageWidth, int imageHeight) {
        return new RectF(
            boundingBox.left * imageWidth,
            boundingBox.top * imageHeight,
            boundingBox.right * imageWidth,
            boundingBox.bottom * imageHeight
        );
    }

    @Override
    public String toString() {
        return String.format("%s (%.2f%%) [%.2f, %.2f, %.2f, %.2f]",
            className, confidence * 100,
            boundingBox.left, boundingBox.top,
            boundingBox.right, boundingBox.bottom);
    }
}
