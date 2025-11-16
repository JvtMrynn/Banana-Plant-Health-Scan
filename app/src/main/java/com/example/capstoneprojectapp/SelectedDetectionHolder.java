package com.example.capstoneprojectapp;

/**
 * Holds a single detection when navigating to a detail view without
 * disturbing the list used by the main result screen.
 */
public class SelectedDetectionHolder {
    private static SelectedDetectionHolder instance;
    private Detection detection;

    private SelectedDetectionHolder() {}

    public static synchronized SelectedDetectionHolder getInstance() {
        if (instance == null) {
            instance = new SelectedDetectionHolder();
        }
        return instance;
    }

    public void setDetection(Detection detection) {
        this.detection = detection;
    }

    public Detection getDetection() {
        return detection;
    }

    public void clear() {
        detection = null;
    }
}
