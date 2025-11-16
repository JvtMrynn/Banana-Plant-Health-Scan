package com.example.capstoneprojectapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.SharedPreferences;
import com.google.firebase.firestore.FirebaseFirestore;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * YOLOv8 Object Detection Service for Leaf Disease Detection
 * Handles model loading, inference, and post-processing (NMS)
 */
public class YOLOv8DetectionService {

    private static final String TAG = "YOLOv8Detection";
    private static final String MODEL_NAME = "yolov8_disease_640_100.ptl";
    private static final String CUSTOM_MODEL_DIR = "models";
    private static final String CUSTOM_MODEL_NAME = "current_model.ptl";

    private Module model;
    private Context context;
    private SharedPreferences modelPrefs;

    // YOLOv8 model parameters
    private static final int INPUT_SIZE = 640;  // YOLOv8 default input size
    private static final float CONFIDENCE_THRESHOLD = 0.45f;  // Minimum confidence (increased to 60% to reduce false positives)
    private static final float IOU_THRESHOLD = 0.45f;  // NMS IoU threshold
    private static final int MAX_DETECTIONS = 300;
    private static final float HIGH_CONFIDENCE_THRESHOLD = 0.75f;  // Threshold for reliable detections
    private static final float VERY_HIGH_CONFIDENCE_THRESHOLD = 0.85f;  // Threshold for very reliable detections

    // Normalization parameters (ImageNet standard)
    private static final float[] NORM_MEAN = {0.0f, 0.0f, 0.0f};  // YOLOv8 uses 0-1 normalization
    private static final float[] NORM_STD = {255.0f, 255.0f, 255.0f};

    // Disease class labels - UPDATE THESE based on your training data
    // Common leaf diseases for reference
    private static final String[] CLASS_LABELS = {
        "Black Sigatoka",
        "Fusarium Wilt",
        "Banana Bract Mosaic Disease",
        "Xanthomonas Wilt",
        "Bacterial Wilt",
        "Healthy"
    };

    public YOLOv8DetectionService() {
        Log.d(TAG, "YOLOv8DetectionService initialized");
    }

    public void initialize(Context context) {
        this.context = context;
        this.modelPrefs = context.getSharedPreferences("ModelPrefs", Context.MODE_PRIVATE);
        try {
            String[] assets = context.getAssets().list("");
            Log.d(TAG, "Assets found: " + Arrays.toString(assets));
            boolean modelExists = false;
            for (String asset : assets) {
                if (MODEL_NAME.equals(asset)) {
                    modelExists = true;
                    break;
                }
            }
            if (!modelExists) {
                Log.w(TAG, "Model file '" + MODEL_NAME + "' not found in assets.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot list assets", e);
        }
    }

    public interface ModelUpdateCallback { void onComplete(boolean updated, String message); }

    /**
     * Check Firestore for a newer model and download from Firebase Storage if available.
     * Firestore doc: ml_models/yolov8 with fields: version (long), storagePath (string)
     */
    public void checkAndUpdateModelAsync(ModelUpdateCallback cb) {
        if (context == null) { if (cb != null) cb.onComplete(false, "No context"); return; }
        FirebaseFirestore.getInstance().collection("ml_models").document("yolov8")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) { if (cb != null) cb.onComplete(false, "No remote metadata"); return; }
                    Long rv = null;
                    try { rv = doc.getLong("version"); } catch (Exception ignored) {}
                    final long remoteVersion = rv != null ? rv : 0L;
                    String url = doc.getString("url");
                    String path = doc.getString("storagePath");
                    if ((url == null || url.isEmpty()) && (path == null || path.isEmpty())) { if (cb != null) cb.onComplete(false, "No model URL"); return; }
                    long localVersion = modelPrefs.getLong("modelVersion", 0L);
                    if (remoteVersion <= localVersion) { if (cb != null) cb.onComplete(false, "Model up to date"); return; }
                    // Download
                    File targetDir = new File(context.getFilesDir(), CUSTOM_MODEL_DIR);
                    if (!targetDir.exists()) targetDir.mkdirs();
                    File outFile = new File(targetDir, CUSTOM_MODEL_NAME);
                    // Prefer plain HTTPS URL if available; else attempt to treat storagePath as an HTTPS URL
                    String downloadUrl = (url != null && !url.isEmpty()) ? url : path;
                    httpDownloadToFile(downloadUrl, outFile, new Runnable() {
                        @Override public void run() {
                            SharedPreferences.Editor ed = modelPrefs.edit();
                            ed.putLong("modelVersion", remoteVersion).apply();
                            close();
                            if (cb != null) cb.onComplete(true, "Model updated");
                        }
                    }, new java.util.function.Consumer<String>() {
                        @Override public void accept(String err) {
                            if (cb != null) cb.onComplete(false, "Download failed: " + err);
                        }
                    });
                })
                .addOnFailureListener(e -> { if (cb != null) cb.onComplete(false, "Metadata error: " + e.getMessage()); });
    }

    private void httpDownloadToFile(String link, File outFile, Runnable onSuccess, java.util.function.Consumer<String> onError) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(link);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    onError.accept("HTTP " + conn.getResponseCode());
                    return;
                }
                try (InputStream is = conn.getInputStream(); FileOutputStream os = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                    os.flush();
                }
                if (outFile.length() < 100000) { onError.accept("Downloaded file too small (" + outFile.length() + " bytes)"); return; }
                onSuccess.run();
            } catch (Exception e) {
                onError.accept(e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /**
     * Detect diseases in the provided image
     */
    public List<Detection> detectDiseases(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Null bitmap provided");
            return new ArrayList<>();
        }

        try {
            if (model == null) {
                loadModel();
            }

            if (model == null) {
                Log.e(TAG, "Model loading failed");
                return new ArrayList<>();
            }

            return runInference(bitmap);

        } catch (Exception e) {
            Log.e(TAG, "Error during detection", e);
            return new ArrayList<>();
        }
    }

    /**
     * Load the YOLOv8 model from assets
     */
    private void loadModel() {
        try {
            if (context == null) {
                Log.e(TAG, "Context is null, cannot load model");
                return;
            }

            // Try custom model from app storage first
            File customDir = new File(context.getFilesDir(), CUSTOM_MODEL_DIR);
            File customModel = new File(customDir, CUSTOM_MODEL_NAME);
            if (customModel.exists() && customModel.length() > 0) {
                Log.d(TAG, "Loading YOLOv8 model from custom path: " + customModel.getAbsolutePath() + " (" + customModel.length() + " bytes)");
                try {
                    model = LiteModuleLoader.load(customModel.getAbsolutePath());
                    Log.d(TAG, "YOLOv8 custom model loaded successfully");
                    return;
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to load custom model. Deleting and falling back to asset.", t);
                    try { customModel.delete(); } catch (Exception ignored) {}
                    if (modelPrefs != null) { try { modelPrefs.edit().putLong("modelVersion", 0L).apply(); } catch (Exception ignored) {} }
                }
            }

            Log.d(TAG, "Loading YOLOv8 model from assets: " + MODEL_NAME);

            try (InputStream is = context.getAssets().open(MODEL_NAME)) {
                int size = is.available();
                Log.d(TAG, "Model file size: " + size + " bytes");

                File tempFile = File.createTempFile("yolov8_model", ".ptl", context.getCacheDir());
                tempFile.deleteOnExit();

                try (FileOutputStream os = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalBytes = 0;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    os.flush();
                    Log.d(TAG, "Bytes written: " + totalBytes);
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    model = LiteModuleLoader.load(tempFile.getAbsolutePath());
                    Log.d(TAG, "YOLOv8 model loaded successfully");
                } else {
                    Log.e(TAG, "Temp file creation failed");
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException loading model", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error loading model", e);
            model = null;
        }
    }

    /**
     * Run YOLOv8 inference on the image
     */
    private List<Detection> runInference(Bitmap bitmap) {
        try {
            // Resize image to model input size
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            
            // Convert bitmap to tensor
            Tensor inputTensor = bitmapToTensor(resizedBitmap);
            
            Log.d(TAG, "Input tensor shape: " + Arrays.toString(inputTensor.shape()));
            
            // Run inference
            long startTime = System.currentTimeMillis();
            IValue output = model.forward(IValue.from(inputTensor));
            long inferenceTime = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Inference time: " + inferenceTime + "ms");

            // Parse YOLOv8 output
            Tensor outputTensor = output.toTensor();
            Log.d(TAG, "Output tensor shape: " + Arrays.toString(outputTensor.shape()));
            
            // Process detections
            List<Detection> detections = processOutput(outputTensor, bitmap.getWidth(), bitmap.getHeight());
            Log.d(TAG, "Detections found: " + detections.size());
            
            return detections;

        } catch (Exception e) {
            Log.e(TAG, "Inference error", e);
            return new ArrayList<>();
        }
    }

    /**
     * Convert bitmap to tensor with proper normalization
     */
    private Tensor bitmapToTensor(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        float[] inputData = new float[3 * width * height];
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        
        // Convert to CHW format (channels, height, width) and normalize to 0-1
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            inputData[i] = ((pixel >> 16) & 0xFF) / 255.0f;  // R
            inputData[pixels.length + i] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
            inputData[2 * pixels.length + i] = (pixel & 0xFF) / 255.0f;  // B
        }
        
        return Tensor.fromBlob(inputData, new long[]{1, 3, height, width});
    }

    /**
     * Process YOLOv8 output tensor
     * YOLOv8 output format: [batch, 4 + num_classes, num_predictions]
     * Where first 4 values are [x_center, y_center, width, height]
     */
    private List<Detection> processOutput(Tensor outputTensor, int originalWidth, int originalHeight) {
        float[] output = outputTensor.getDataAsFloatArray();
        long[] shape = outputTensor.shape();
        
        Log.d(TAG, "Processing output with shape: " + Arrays.toString(shape));
        
        // YOLOv8 output is typically [1, 84, 8400] for 80 classes
        // or [1, num_classes + 4, num_predictions]
        int numPredictions = (int) shape[2];
        int numValues = (int) shape[1];  // 4 (bbox) + num_classes
        int numClasses = numValues - 4;
        
        Log.d(TAG, "Num predictions: " + numPredictions + ", Num classes: " + numClasses);
        
        List<Detection> allDetections = new ArrayList<>();
        
        // Parse each prediction
        for (int i = 0; i < numPredictions; i++) {
            // Get bounding box coordinates (normalized 0-1)
            float xCenter = output[i];
            float yCenter = output[numPredictions + i];
            float width = output[2 * numPredictions + i];
            float height = output[3 * numPredictions + i];
            
            // Find class with highest confidence
            float maxConf = 0;
            int maxClassId = 0;
            
            for (int c = 0; c < numClasses; c++) {
                float conf = output[(4 + c) * numPredictions + i];
                if (conf > maxConf) {
                    maxConf = conf;
                    maxClassId = c;
                }
            }
            
            // Filter by confidence threshold
            if (maxConf > CONFIDENCE_THRESHOLD) {
                // Convert from center format to corner format
                float left = (xCenter - width / 2) / INPUT_SIZE;
                float top = (yCenter - height / 2) / INPUT_SIZE;
                float right = (xCenter + width / 2) / INPUT_SIZE;
                float bottom = (yCenter + height / 2) / INPUT_SIZE;
                
                // Clamp to 0-1
                left = Math.max(0, Math.min(1, left));
                top = Math.max(0, Math.min(1, top));
                right = Math.max(0, Math.min(1, right));
                bottom = Math.max(0, Math.min(1, bottom));
                
                RectF bbox = new RectF(left, top, right, bottom);
                String className = maxClassId < CLASS_LABELS.length ? 
                    CLASS_LABELS[maxClassId] : "Class " + maxClassId;
                
                allDetections.add(new Detection(bbox, maxConf, maxClassId, className));
            }
        }
        
        Log.d(TAG, "Detections before NMS: " + allDetections.size());
        
        // Apply Non-Maximum Suppression
        List<Detection> finalDetections = applyNMS(allDetections);
        
        Log.d(TAG, "Detections after NMS: " + finalDetections.size());
        
        return finalDetections;
    }

    /**
     * Apply Non-Maximum Suppression to remove overlapping detections
     */
    private List<Detection> applyNMS(List<Detection> detections) {
        if (detections.isEmpty()) {
            return detections;
        }
        
        // Sort by confidence (descending)
        detections.sort((d1, d2) -> Float.compare(d2.getConfidence(), d1.getConfidence()));
        
        List<Detection> selected = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            Detection det1 = detections.get(i);
            selected.add(det1);
            
            if (selected.size() >= MAX_DETECTIONS) break;
            
            // Suppress overlapping detections
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                Detection det2 = detections.get(j);
                
                // Only suppress if same class
                if (det1.getClassId() == det2.getClassId()) {
                    float iou = calculateIoU(det1.getBoundingBox(), det2.getBoundingBox());
                    if (iou > IOU_THRESHOLD) {
                        suppressed[j] = true;
                    }
                }
            }
        }
        
        return selected;
    }

    /**
     * Calculate Intersection over Union (IoU) between two bounding boxes
     */
    private float calculateIoU(RectF box1, RectF box2) {
        float intersectionLeft = Math.max(box1.left, box2.left);
        float intersectionTop = Math.max(box1.top, box2.top);
        float intersectionRight = Math.min(box1.right, box2.right);
        float intersectionBottom = Math.min(box1.bottom, box2.bottom);
        
        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0.0f;
        }
        
        float intersectionArea = (intersectionRight - intersectionLeft) * 
                                (intersectionBottom - intersectionTop);
        
        float box1Area = (box1.right - box1.left) * (box1.bottom - box1.top);
        float box2Area = (box2.right - box2.left) * (box2.bottom - box2.top);
        
        float unionArea = box1Area + box2Area - intersectionArea;
        
        return intersectionArea / unionArea;
    }

    public void close() {
        if (model != null) {
            model.destroy();
            model = null;
            Log.d(TAG, "Model resources cleaned up");
        }
    }

    public String[] getClassLabels() {
        return CLASS_LABELS;
    }

    public boolean installCustomModel(InputStream inputStream) {
        if (context == null || inputStream == null) return false;
        try {
            File customDir = new File(context.getFilesDir(), CUSTOM_MODEL_DIR);
            if (!customDir.exists()) customDir.mkdirs();
            File outFile = new File(customDir, CUSTOM_MODEL_NAME);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                long total = 0;
                while ((len = inputStream.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                    total += len;
                }
                fos.flush();
                Log.d(TAG, "Custom model bytes written: " + total);
            }
            // Reset model so it reloads on next use
            close();
            return outFile.exists() && outFile.length() > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to install custom model", e);
            return false;
        }
    }
}
