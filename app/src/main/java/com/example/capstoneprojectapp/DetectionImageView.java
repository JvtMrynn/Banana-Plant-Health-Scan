package com.example.capstoneprojectapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom ImageView that can draw bounding boxes and labels for object detection
 */
public class DetectionImageView extends AppCompatImageView {

    private List<Detection> detections = new ArrayList<>();
    private Paint boxPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint;

    public DetectionImageView(Context context) {
        super(context);
        init();
    }

    public DetectionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DetectionImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint for bounding boxes
        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5f);
        boxPaint.setAntiAlias(true);

        // Paint for text labels
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setAntiAlias(true);
        textPaint.setFakeBoldText(true);

        // Paint for text background
        textBackgroundPaint = new Paint();
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setAntiAlias(true);
    }

    /**
     * Set detections to draw on the image
     */
    public void setDetections(List<Detection> detections) {
        this.detections = detections != null ? detections : new ArrayList<>();
        invalidate();  // Trigger redraw
    }

    /**
     * Clear all detections
     */
    public void clearDetections() {
        this.detections.clear();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (detections.isEmpty() || getDrawable() == null) {
            return;
        }

        // Get the actual image dimensions within the view
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        // Draw each detection
        for (Detection detection : detections) {
            drawDetection(canvas, detection, viewWidth, viewHeight);
        }
    }

    /**
     * Draw a single detection with bounding box and label
     */
    private void drawDetection(Canvas canvas, Detection detection, int viewWidth, int viewHeight) {
        // Get scaled bounding box
        RectF bbox = detection.getScaledBoundingBox(viewWidth, viewHeight);

        // Choose color based on class
        int color = getColorForClass(detection.getClassId());
        boxPaint.setColor(color);
        textBackgroundPaint.setColor(color);

        // Draw bounding box
        canvas.drawRect(bbox, boxPaint);

        // Prepare label text
        String label = String.format("%s %.0f%%", 
            detection.getClassName(), 
            detection.getConfidence() * 100);

        // Measure text
        float textWidth = textPaint.measureText(label);
        float textHeight = textPaint.getTextSize();

        // Calculate label position (above the box)
        float labelX = bbox.left;
        float labelY = bbox.top - 10;

        // Ensure label stays within view bounds
        if (labelY < textHeight) {
            labelY = bbox.top + textHeight + 10;  // Draw inside box if no space above
        }

        // Draw text background
        RectF textBgRect = new RectF(
            labelX,
            labelY - textHeight,
            labelX + textWidth + 20,
            labelY + 10
        );
        canvas.drawRect(textBgRect, textBackgroundPaint);

        // Draw text
        canvas.drawText(label, labelX + 10, labelY, textPaint);
    }

    /**
     * Get color for different disease classes
     */
    private int getColorForClass(int classId) {
        // Define colors for different classes
        int[] colors = {
            Color.rgb(0, 255, 0),      // Green - Healthy
            Color.rgb(255, 0, 0),      // Red - Disease 1
            Color.rgb(255, 165, 0),    // Orange - Disease 2
            Color.rgb(255, 255, 0),    // Yellow - Disease 3
            Color.rgb(255, 0, 255),    // Magenta - Disease 4
            Color.rgb(0, 255, 255),    // Cyan - Disease 5
            Color.rgb(128, 0, 128),    // Purple - Disease 6
            Color.rgb(255, 192, 203)   // Pink - Disease 7
        };

        return colors[classId % colors.length];
    }
}
