package com.example.capstoneprojectapp;

import java.util.ArrayList;

/**
 * Singleton class to hold detection results temporarily when passing between activities
 * This avoids Intent size limitations when passing large detection lists
 */
public class DetectionHolder {
    private static DetectionHolder instance;
    private ArrayList<Detection> detections;

    private DetectionHolder() {
        // Private constructor for singleton
    }

    public static synchronized DetectionHolder getInstance() {
        if (instance == null) {
            instance = new DetectionHolder();
        }
        return instance;
    }

    public void setDetections(ArrayList<Detection> detections) {
        this.detections = detections;
    }

    public ArrayList<Detection> getDetections() {
        return detections;
    }

    public void clearDetections() {
        this.detections = null;
    }
}
