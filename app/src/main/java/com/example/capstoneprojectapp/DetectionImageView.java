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

        // Compute the actual displayed image rectangle within this view
        RectF imageRect = getImageRect();
        if (imageRect == null) {
            // Fallback: use full view (legacy behaviour)
            imageRect = new RectF(0, 0, getWidth(), getHeight());
        }

        // Clip all drawings to the image bounds so boxes never exceed the image area
        int save = canvas.save();
        canvas.clipRect(imageRect);

        for (Detection detection : detections) {
            drawDetection(canvas, detection, imageRect);
        }

        canvas.restoreToCount(save);
    }

    /**
     * Draw a single detection with bounding box and label
     */
    private void drawDetection(Canvas canvas, Detection detection, RectF imageRect) {
        // Scale normalized bbox by the displayed image dimensions, then offset by imageRect origin
        RectF scaled = detection.getScaledBoundingBox((int) imageRect.width(), (int) imageRect.height());
        RectF bbox = new RectF(
                imageRect.left + scaled.left,
                imageRect.top + scaled.top,
                imageRect.left + scaled.right,
                imageRect.top + scaled.bottom
        );

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

        // Ensure label stays within image bounds
        if (labelY - textHeight < imageRect.top) {
            labelY = Math.min(bbox.bottom - 10, imageRect.bottom - 10);
        }
        if (labelX < imageRect.left) labelX = imageRect.left + 10;
        if (labelX + textWidth + 20 > imageRect.right) {
            labelX = Math.max(imageRect.left + 10, imageRect.right - (textWidth + 20));
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

    /**
     * Calculate the rectangle where the drawable (bitmap) is actually displayed inside the view,
     * accounting for ImageView scaleType and matrix transforms.
     */
    private RectF getImageRect() {
        if (getDrawable() == null) return null;

        int dwidth = getDrawable().getIntrinsicWidth();
        int dheight = getDrawable().getIntrinsicHeight();
        if (dwidth <= 0 || dheight <= 0) return null;

        android.graphics.Matrix m = getImageMatrix();
        float[] vals = new float[9];
        m.getValues(vals);
        float scaleX = vals[android.graphics.Matrix.MSCALE_X];
        float scaleY = vals[android.graphics.Matrix.MSCALE_Y];
        float transX = vals[android.graphics.Matrix.MTRANS_X];
        float transY = vals[android.graphics.Matrix.MTRANS_Y];

        float left = transX + getPaddingLeft();
        float top = transY + getPaddingTop();
        float right = left + dwidth * scaleX;
        float bottom = top + dheight * scaleY;

        return new RectF(left, top, right, bottom);
    }
}
