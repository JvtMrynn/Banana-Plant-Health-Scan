package com.example.capstoneprojectapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ResultActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private DetectionImageView analyzedImageView;
    private MaterialTextView diseaseNameText;
    private MaterialTextView scientificNameText;
    private MaterialTextView definitionText;
    private MaterialTextView managementText;
    private MaterialTextView preventionText;
    private MaterialTextView confidenceText;
    private MaterialTextView treatableStatusText;
    private MaterialTextView detectionsListText;
    private MaterialButton btnShowAllDetections;
    private MaterialTextView detectionCardHeader;
    private LinearLayout detectionCardContainer;
    private View severityIndicator;
    private MaterialButton btnBackToDashboard;
    private MaterialButton btnConsultExpert;
    private MaterialCardView resultCard;
    private FirebaseFirestore db;
    private com.example.capstoneprojectapp.data.repo.DataRepository dataRepository;
    private boolean selectedDetectionView;
    private String analysisTitleForConsultation;
    private String analysisSummaryForConsultation;
    private long analysisTimestampForConsultation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        initializeViews();
        setupToolbar();
        db = FirebaseFirestore.getInstance();
        dataRepository = new com.example.capstoneprojectapp.data.repo.DataRepository(this);
        displayResults();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        analyzedImageView = findViewById(R.id.analyzedImageView);
        diseaseNameText = findViewById(R.id.diseaseNameText);
        scientificNameText = findViewById(R.id.scientificNameText);
        definitionText = findViewById(R.id.definitionText);
        managementText = findViewById(R.id.managementText);
        preventionText = findViewById(R.id.preventionText);
        confidenceText = findViewById(R.id.confidenceText);
        treatableStatusText = findViewById(R.id.treatableStatusText);
        detectionsListText = findViewById(R.id.detectionsListText);
        btnShowAllDetections = findViewById(R.id.btnShowAllDetections);
        detectionCardHeader = findViewById(R.id.detectionCardHeader);
        detectionCardContainer = findViewById(R.id.detectionCardContainer);
        severityIndicator = findViewById(R.id.severityIndicator);
        btnBackToDashboard = findViewById(R.id.btnBackToDashboard);
        btnConsultExpert = findViewById(R.id.btnConsultExpert);
        resultCard = findViewById(R.id.resultCard);

        btnBackToDashboard.setOnClickListener(v -> finish());
        if (btnConsultExpert != null) {
            btnConsultExpert.setOnClickListener(v -> openConsultationRequest());
        }

        // Tap image to view fullscreen with bounding boxes
        analyzedImageView.setOnClickListener(v -> {
            try {
                startActivity(new android.content.Intent(ResultActivity.this, FullscreenImageActivity.class));
            } catch (Exception ignored) { }
        });
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Analysis Result");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void displayResults() {
        Intent intent = getIntent();
        selectedDetectionView = intent.getBooleanExtra("selected_detection_view", false);
        boolean isError = intent.getBooleanExtra("is_error", false);
        analysisTimestampForConsultation = System.currentTimeMillis();

        // Retrieve the analyzed image from ImageHolder
        Bitmap analyzedImage = ImageHolder.getInstance().getImage();
        if (analyzedImage != null) {
            analyzedImageView.setImageBitmap(analyzedImage);
        } else {
            analyzedImageView.setVisibility(View.GONE);
        }

        // Retrieve and display detections
        ArrayList<Detection> detections = DetectionHolder.getInstance().getDetections();
        if (selectedDetectionView) {
            Detection selected = SelectedDetectionHolder.getInstance().getDetection();
            if (selected != null) {
                detections = new ArrayList<>();
                detections.add(selected);
                SelectedDetectionHolder.getInstance().clear();
            } else {
                finish();
                return;
            }
        }
        if (detections == null) {
            detections = new ArrayList<>();
        }
        List<Detection> highConfidenceDetections = filterHighConfidenceDetections(detections);
        List<Detection> distinctDetections = getBestDetectionsByClass(highConfidenceDetections);
        String summaryText = buildDetectionSummaryText(distinctDetections, false);
        populateDetectionCards(distinctDetections);
        if (!highConfidenceDetections.isEmpty()) {
            analyzedImageView.setDetections(highConfidenceDetections);
            detectionsListText.setText(summaryText);
            analysisSummaryForConsultation = summaryText;
            if (distinctDetections.size() > 5) {
                btnShowAllDetections.setVisibility(View.VISIBLE);
                btnShowAllDetections.setOnClickListener(v -> {
                    String allText = buildDetectionSummaryText(distinctDetections, true);
                    detectionsListText.setText(allText);
                    analysisSummaryForConsultation = allText;
                    btnShowAllDetections.setVisibility(View.GONE);
                });
            } else {
                btnShowAllDetections.setVisibility(View.GONE);
            }
        } else {
            analyzedImageView.clearDetections();
            detectionsListText.setText(getString(R.string.no_high_confidence));
            btnShowAllDetections.setVisibility(View.GONE);
            showNoHighConfidenceState(intent);
            return;
        }

        if (isError) {
            displayError(intent.getStringExtra("error_message"));
            return;
        }

        String diseaseName = intent.getStringExtra("disease_name");
        String description = intent.getStringExtra("description");
        String management = intent.getStringExtra("management");
        String prevention = intent.getStringExtra("prevention");
        String confidence = intent.getStringExtra("confidence");
        int severityColor = intent.getIntExtra("severity_color", getColor(android.R.color.holo_green_dark));

        if (distinctDetections.size() > 1) {
            diseaseName = getString(R.string.multi_detection_title);
            description = getString(R.string.multiple_detections_desc);
            confidence = String.format(Locale.getDefault(), "%d detections", distinctDetections.size());
        } else if (distinctDetections.size() == 1) {
            Detection only = distinctDetections.get(0);
            diseaseName = only.getClassName();
            description = getString(R.string.single_detection_desc);
            confidence = String.format(Locale.getDefault(), "%.1f%%", only.getConfidence() * 100f);
        }
        analysisTitleForConsultation = diseaseName;
        
        // Get detection summary if available
        String detectionsSummary = (summaryText != null && !summaryText.isEmpty())
                ? summaryText
                : intent.getStringExtra("detections_summary");
        if (detectionsSummary != null && !detectionsSummary.isEmpty()) {
            description = detectionsSummary + "\n\n" + description;
        }

        // If we have detections, enrich details from Firestore or fallback defaults
        if (!highConfidenceDetections.isEmpty()) {
            Detection top = getTopDetection(highConfidenceDetections);
            String topName = top.getClassName();
            // Healthy / Other quick paths
            if (equalsIgnoreCase(topName, "Healthy")) {
                diseaseName = "Healthy Leaf";
                description = "No disease detected. The leaf appears healthy.";
                management = "No action needed.";
                prevention = "Maintain good crop care and regular monitoring.";
            } else if (equalsIgnoreCase(topName, "Other")) {
                diseaseName = "Unidentified";
                description = (description == null ? "" : description) + "\nThe image does not match known banana diseases with high confidence.";
                management = "Consider re-taking photo in better lighting and consult an expert if symptoms persist.";
                prevention = "Ensure proper sanitation, irrigation, and use disease-free planting materials.";
            } else {
                // Try to load from Firestore disease_info where diseaseName equals the detection name
                // Fallback to static defaults if not found
                final String detectedName = topName;
                // Capture current values for use inside lambdas
                final String fbName = diseaseName;
                final String fbDesc = description;
                final String fbMgmt = management;
                final String fbPrev = prevention;
                db.collection("disease_info")
                        .whereEqualTo("diseaseName", detectedName)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(snap -> {
                            if (!snap.isEmpty()) {
                                DiseaseInfo info = snap.getDocuments().get(0).toObject(DiseaseInfo.class);
                                if (info != null) {
                                    bindDetails(info.getDiseaseName(), info.getScientificName(), buildDescription(info), info.getTreatment(), info.getPrevention(), intent);
                                    return;
                                }
                            }
                            // Fallback defaults or cached data
                            loadLocalDiseaseInfo(detectedName, local -> {
                                if (local != null) {
                                    String desc = buildDescription(local);
                                    bindDetails(local.name, local.scientificName, desc, local.treatment, local.prevention, intent);
                                    showOfflineBanner();
                                } else {
                                    DiseaseDetails def = Defaults.forName(detectedName);
                                    if (def != null) {
                                        bindDetails(def.name, null, def.description, def.management, def.prevention, intent);
                                    } else {
                                        bindDetails(fbName, null, fbDesc, fbMgmt, fbPrev, intent);
                                    }
                                }
                            });
                        })
                        .addOnFailureListener(e -> loadLocalDiseaseInfo(detectedName, local -> {
                            if (local != null) {
                                String desc = buildDescription(local);
                                bindDetails(local.name, local.scientificName, desc, local.treatment, local.prevention, intent);
                                showOfflineBanner();
                            } else {
                                DiseaseDetails def = Defaults.forName(detectedName);
                                if (def != null) {
                                    bindDetails(def.name, null, def.description, def.management, def.prevention, intent);
                                } else {
                                    bindDetails(fbName, null, fbDesc, fbMgmt, fbPrev, intent);
                                }
                            }
                        }));
                // Early return to avoid double-binding; Firestore callback will handle UI
                return;
            }
        }

        // Bind immediately for paths where we don't query Firestore
        bindDetails(diseaseName, null, description, management, prevention, intent);
    }

    private void loadLocalDiseaseInfo(String diseaseName, LocalDiseaseCallback callback) {
        new Thread(() -> {
            com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity local =
                    dataRepository.getLocalDiseaseByName(diseaseName);
            runOnUiThread(() -> callback.onComplete(local));
        }).start();
    }

    private void showOfflineBanner() {
        View banner = findViewById(R.id.offlineBanner);
        if (banner != null) banner.setVisibility(View.VISIBLE);
    }

    private interface LocalDiseaseCallback {
        void onComplete(com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity entity);
    }

    // Build description text from local entity fields (offline cache)
    private String buildDescription(com.example.capstoneprojectapp.data.local.entity.DiseaseInfoEntity e) {
        StringBuilder sb = new StringBuilder();
        if (e.causedBy != null && !e.causedBy.isEmpty()) {
            sb.append("Caused by: ").append(e.causedBy).append('\n');
        }
        if (e.symptoms != null && !e.symptoms.isEmpty()) {
            sb.append("Symptoms: ").append(e.symptoms).append('\n');
        }
        return sb.toString().trim();
    }

private void bindDetails(String diseaseName, String scientificName, String description, String management, String prevention, Intent intent) {
        // Set disease name
        diseaseNameText.setText(diseaseName != null ? diseaseName : "Unknown Disease");
        analysisTitleForConsultation = diseaseNameText.getText().toString();
        if (analysisSummaryForConsultation == null || analysisSummaryForConsultation.trim().isEmpty()) {
            analysisSummaryForConsultation = detectionsListText.getText().toString();
        }

    // Set scientific name (prefer expert-provided)
    String sci = (scientificName != null && !scientificName.trim().isEmpty()) ? scientificName : getScientificName(diseaseName);
    scientificNameText.setText(sci);

        // Set definition/description
        definitionText.setText(description != null && !description.isEmpty() ? description : "No description available");

        // Set management advice (bullet formatted)
        if (management != null && !management.isEmpty()) {
            findViewById(R.id.managementHeaderText).setVisibility(View.VISIBLE);
            managementText.setVisibility(View.VISIBLE);
            managementText.setText("🛠️ Management & Remedy:\n" + management);
        } else {
            findViewById(R.id.managementHeaderText).setVisibility(View.GONE);
            managementText.setVisibility(View.GONE);
        }

        // Set prevention tips
        if (prevention != null && !prevention.isEmpty()) {
            findViewById(R.id.preventionHeaderText).setVisibility(View.VISIBLE);
            preventionText.setVisibility(View.VISIBLE);
            preventionText.setText("💡 Prevention:\n" + prevention);
        } else {
            findViewById(R.id.preventionHeaderText).setVisibility(View.GONE);
            preventionText.setVisibility(View.GONE);
        }

        // Confidence and severity from intent (if provided by caller)
        String conf = intent.getStringExtra("confidence");
        int sevColor = intent.getIntExtra("severity_color", getColor(android.R.color.holo_green_dark));
        confidenceText.setText("Detection Confidence: " + (conf != null ? conf : "Unknown"));

        // Set treatable status
        String treatableStatus = deriveTreatableStatus(management);
        treatableStatusText.setText(treatableStatus);
        
        // Set severity indicator color
        severityIndicator.setBackgroundColor(sevColor);

        // Animate card
        resultCard.setAlpha(0f);
        resultCard.setTranslationY(50f);
        resultCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start();
    }

    private Detection getTopDetection(List<Detection> detections) {
        Detection top = detections.get(0);
        for (Detection d : detections) {
            if (d.getConfidence() > top.getConfidence()) top = d;
        }
        return top;
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private String buildDescription(DiseaseInfo info) {
        StringBuilder sb = new StringBuilder();
        if (info.getCausedBy() != null && !info.getCausedBy().isEmpty()) {
            sb.append("Caused by: ").append(info.getCausedBy()).append("\n\n");
        }
        if (info.getSymptoms() != null && !info.getSymptoms().isEmpty()) {
            sb.append("Symptoms:\n").append(info.getSymptoms()).append("\n\n");
        }
        return sb.toString();
    }

//    private CharSequence formatBullets(String text) {
//        // Split on semicolons or newlines to create bullets
//        String[] parts = text.split("[\n;]+\\s*");
//        StringBuilder sb = new StringBuilder();
//        for (String p : parts) {
//            if (p.trim().isEmpty()) continue;
//            sb.append("• ").append(p.trim()).append('\n');
//        }
//        return sb.toString().trim();
//    }

    private List<Detection> filterHighConfidenceDetections(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();
        if (detections == null) return result;
        for (Detection detection : detections) {
            if (detection != null && detection.getConfidence() >= 0.75f) {
                result.add(detection);
            }
        }
        return result;
    }

    private String buildDetectionSummaryText(List<Detection> detections, boolean showAll) {
        if (detections == null || detections.isEmpty()) {
            return "";
        }
        int limit = showAll ? detections.size() : Math.min(5, detections.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            Detection d = detections.get(i);
            sb.append(i + 1)
              .append(". ")
              .append(d.getClassName())
              .append(" (")
              .append(String.format(Locale.getDefault(), "%.1f%%", d.getConfidence() * 100f))
              .append(")\n");
        }
        if (!showAll && detections.size() > limit) {
            sb.append("…");
        }
        return sb.toString().trim();
    }

    private List<Detection> getBestDetectionsByClass(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();
        if (detections == null) return result;
        LinkedHashMap<String, Detection> bestByClass = new LinkedHashMap<>();
        for (Detection detection : detections) {
            if (detection == null) continue;
            String key = detection.getClassName() != null
                    ? detection.getClassName().toLowerCase(Locale.US)
                    : "";
            Detection current = bestByClass.get(key);
            if (current == null || detection.getConfidence() > current.getConfidence()) {
                bestByClass.put(key, detection);
            }
        }
        result.addAll(bestByClass.values());
        return result;
    }

    private void populateDetectionCards(List<Detection> detections) {
        if (detectionCardContainer == null || detectionCardHeader == null) return;
        if (selectedDetectionView || detections == null || detections.size() <= 1) {
            detectionCardHeader.setVisibility(View.GONE);
            detectionCardContainer.setVisibility(View.GONE);
            detectionCardContainer.removeAllViews();
            return;
        }
        detectionCardHeader.setVisibility(View.VISIBLE);
        detectionCardContainer.setVisibility(View.VISIBLE);
        detectionCardContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Detection detection : detections) {
            View card = inflater.inflate(R.layout.item_multi_detection_entry, detectionCardContainer, false);
            TextView title = card.findViewById(R.id.detectionTitleText);
            TextView confidence = card.findViewById(R.id.detectionConfidenceText);
            title.setText(detection.getClassName());
            confidence.setText(String.format(Locale.getDefault(), "%.1f%% confidence", detection.getConfidence() * 100f));
            card.setOnClickListener(v -> openDetectionDetail(detection));
            detectionCardContainer.addView(card);
        }
    }

    private void openDetectionDetail(Detection detection) {
        SelectedDetectionHolder.getInstance().setDetection(detection);
        Intent detailIntent = new Intent(this, ResultActivity.class);
        detailIntent.putExtra("selected_detection_view", true);
        detailIntent.putExtra("disease_name", detection.getClassName());
        detailIntent.putExtra("description", getString(R.string.single_detection_desc));
        detailIntent.putExtra("management", "");
        detailIntent.putExtra("prevention", "");
        detailIntent.putExtra("confidence", String.format(Locale.getDefault(), "%.1f%%", detection.getConfidence() * 100f));
        detailIntent.putExtra("severity_color", Color.RED);
        detailIntent.putExtra("is_error", false);
        detailIntent.putExtra("error_message", "");
        startActivity(detailIntent);
    }

    private void showNoHighConfidenceState(Intent intent) {
        diseaseNameText.setText(getString(R.string.no_confidence_title));
        scientificNameText.setText(getString(R.string.scientific_unknown));
        definitionText.setText(getString(R.string.no_high_confidence_details));
        analysisTitleForConsultation = diseaseNameText.getText().toString();
        analysisSummaryForConsultation = detectionsListText.getText().toString();

        View managementHeader = findViewById(R.id.managementHeaderText);
        managementHeader.setVisibility(View.GONE);
        managementText.setVisibility(View.GONE);
        View preventionHeader = findViewById(R.id.preventionHeaderText);
        preventionHeader.setVisibility(View.GONE);
        preventionText.setVisibility(View.GONE);

        confidenceText.setText("Detection Confidence: N/A");
        treatableStatusText.setText(getString(R.string.status_consult_expert));
        severityIndicator.setBackgroundColor(Color.GRAY);

        resultCard.setAlpha(0f);
        resultCard.setTranslationY(32f);
        resultCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start();
    }

    private void openConsultationRequest() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sign in required")
                    .setMessage("Please register or sign in to contact an expert.")
                    .setPositiveButton("Register", (dialog, which) -> {
                        startActivity(new Intent(this, RegistrationActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        Intent intent = new Intent(this, ConsultationRequestActivity.class);
        intent.putExtra(ConsultationRequestActivity.EXTRA_FROM_RESULT, true);
        intent.putExtra(ConsultationRequestActivity.EXTRA_ANALYSIS_TITLE, analysisTitleForConsultation);
        intent.putExtra(ConsultationRequestActivity.EXTRA_ANALYSIS_SUMMARY, analysisSummaryForConsultation);
        intent.putExtra(ConsultationRequestActivity.EXTRA_ANALYSIS_TIMESTAMP, analysisTimestampForConsultation);
        startActivity(intent);
    }

    // Simple defaults for common classes used by the model, used if Firestore has no entry
    private static class DiseaseDetails {
        String name; String description; String management; String prevention;
        DiseaseDetails(String n, String d, String m, String p){name=n;description=d;management=m;prevention=p;}
    }

    private static class Defaults {
        static DiseaseDetails forName(String name) {
            if (name == null) return null;
            String key = name.trim().toLowerCase();
            switch (key) {
                case "aphids":
                    return new DiseaseDetails(
                            "Aphids",
                            "Aphids are small sap-sucking insects that cluster on leaves and stems, causing curling and yellowing. They excrete honeydew leading to sooty mold.",
                            "Spray insecticidal soap or neem oil; encourage natural predators like lady beetles; remove heavily infested leaves.",
                            "Use reflective mulches, avoid excessive nitrogen, monitor regularly, and maintain field sanitation.");
                case "bacterial wilt":
                    return new DiseaseDetails(
                            "Bacterial Wilt",
                            "Systemic bacterial infection causing rapid wilting, vascular discoloration, and plant collapse.",
                            "Rogue infected plants; sanitize tools; improve drainage; rotate with non-host crops.",
                            "Use clean planting material, control insect vectors, and remove volunteer hosts.");
                case "black sigatoka":
                    return new DiseaseDetails(
                            "Black Sigatoka",
                            "Fungal leaf spot disease (Mycosphaerella fijiensis) producing elongated dark lesions that reduce photosynthesis and yield.",
                            "Apply recommended fungicides in rotation; remove infected leaves; improve airflow by pruning.",
                            "Plant resistant cultivars, ensure proper spacing, and maintain good nutrition.");
                case "xanthomonas wilt":
                case "xanthomonas wilt (bxw)":
                    return new DiseaseDetails(
                            "Xanthomonas Wilt",
                            "Bacterial disease causing yellowing, wilting, internal fruit discoloration, and ooze exudation.",
                            "Cut and bury infected mats; sterilize tools (bleach flame); remove male buds with forked stick.",
                            "Use clean suckers, control insect transmission, and enforce field hygiene.");
                default:
                    return null;
            }
        }
    }

    private void displayError(String errorMessage) {
        diseaseNameText.setText("Error");
        scientificNameText.setVisibility(View.GONE);
        definitionText.setText(errorMessage != null ? errorMessage : "An error occurred during analysis");
        managementText.setVisibility(View.GONE);
        preventionText.setVisibility(View.GONE);
        treatableStatusText.setVisibility(View.GONE);
        severityIndicator.setBackgroundColor(getColor(android.R.color.holo_red_dark));
        analysisTitleForConsultation = "Analysis Result";
        analysisSummaryForConsultation = errorMessage != null ? errorMessage : "Analysis error";
    }

    private String getScientificName(String diseaseName) {
        if (diseaseName == null) return "N/A";
        
        // Map disease names to scientific names
        switch (diseaseName.toLowerCase()) {
            case "cordana":
            case "cordana leaf spot":
                return "Cordana musae";
            case "fusarium":
            case "fusarium wilt":
            case "panama disease":
                return "Fusarium oxysporum f. sp. cubense";
            case "pestalotiopsis":
                return "Pestalotiopsis spp.";
            case "sigatoka":
            case "black sigatoka":
                return "Mycosphaerella fijiensis";
            case "healthy":
            case "no diseases detected":
                return "No pathogen detected";
            case "multiple detections":
                return "Multiple pathogens detected";
            default:
                return "Scientific name not available";
        }
    }

    private String deriveTreatableStatus(String management) {
        if (management == null || management.trim().isEmpty()) {
            return "Status: Consult agricultural expert";
        }
        // If expert provided management steps, consider it treatable/actionable
        return "✅ Treatable: See management guidance below";
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SelectedDetectionHolder.getInstance().clear();
        if (!selectedDetectionView) {
            // Clear the image and detections from memory when leaving the result screen
            ImageHolder.getInstance().clearImage();
            DetectionHolder.getInstance().clearDetections();
        }
    }
}
