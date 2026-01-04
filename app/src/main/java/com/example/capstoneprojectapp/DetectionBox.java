package com.example.capstoneprojectapp;

public class DetectionBox {
    private float left;
    private float top;
    private float right;
    private float bottom;
    private float confidence;
    private int classId;
    private String className;

    public DetectionBox() {
    }

    public DetectionBox(float left, float top, float right, float bottom, float confidence, int classId, String className) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = confidence;
        this.classId = classId;
        this.className = className;
    }

    public float getLeft() {
        return left;
    }

    public void setLeft(float left) {
        this.left = left;
    }

    public float getTop() {
        return top;
    }

    public void setTop(float top) {
        this.top = top;
    }

    public float getRight() {
        return right;
    }

    public void setRight(float right) {
        this.right = right;
    }

    public float getBottom() {
        return bottom;
    }

    public void setBottom(float bottom) {
        this.bottom = bottom;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public int getClassId() {
        return classId;
    }

    public void setClassId(int classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }
}
