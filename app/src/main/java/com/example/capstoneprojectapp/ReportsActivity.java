package com.example.capstoneprojectapp;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReportsActivity extends AppCompatActivity {

    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int LOW_CONFIDENCE_THRESHOLD = 75;

    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup rangeToggle;
    private MaterialButton btnRange7;
    private MaterialButton btnRange30;
    private MaterialButton btnRange90;
    private MaterialButton btnRangeAll;
    private MaterialTextView tvRangeLabel;
    private MaterialTextView tvTotalScansValue;
    private MaterialTextView tvTotalDetectionsValue;
    private MaterialTextView tvDiseaseHealthyValue;
    private MaterialTextView tvAvgConfidenceValue;
    private MaterialTextView tvTopDiseases;
    private MaterialTextView tvLocationBreakdown;
    private MaterialTextView tvLowConfidence;
    private BarChart chartHealthSplit;
    private LineChart chartTrend;
    private BarChart chartTopDiseases;
    private BarChart chartLocations;
    private MaterialTextView tvComparisonSummary;
    private MaterialTextView tvComparisonDetails;
    private MaterialTextView tvComparisonNote;
    private MaterialCardView comparisonCard;
    private LinearLayout emptyStateLayout;
    private LinearLayout loadingLayout;
    private LinearLayout contentLayout;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isAdminMode = false;
    private Range currentRange = Range.DAYS_30;
    private long currentRangeStart = 0L;
    private long currentRangeEnd = 0L;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    private enum Range {
        DAYS_7(7, "Last 7 days"),
        DAYS_30(30, "Last 30 days"),
        DAYS_90(90, "Last 90 days"),
        ALL(0, "All time");

        private final int days;
        private final String label;

        Range(int days, String label) {
            this.days = days;
            this.label = label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        isAdminMode = getIntent().getBooleanExtra("ADMIN_MODE", false);

        initializeViews();
        setupToolbar();
        setupRangeToggle();
        setupCharts();

        if (!ensureSignedIn()) {
            return;
        }

        btnRange30.setChecked(true);
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        rangeToggle = findViewById(R.id.rangeToggle);
        btnRange7 = findViewById(R.id.btnRange7);
        btnRange30 = findViewById(R.id.btnRange30);
        btnRange90 = findViewById(R.id.btnRange90);
        btnRangeAll = findViewById(R.id.btnRangeAll);
        tvRangeLabel = findViewById(R.id.tvRangeLabel);
        tvTotalScansValue = findViewById(R.id.tvTotalScansValue);
        tvTotalDetectionsValue = findViewById(R.id.tvTotalDetectionsValue);
        tvDiseaseHealthyValue = findViewById(R.id.tvDiseaseHealthyValue);
        tvAvgConfidenceValue = findViewById(R.id.tvAvgConfidenceValue);
        tvTopDiseases = findViewById(R.id.tvTopDiseases);
        tvLocationBreakdown = findViewById(R.id.tvLocationBreakdown);
        tvLowConfidence = findViewById(R.id.tvLowConfidence);
        chartHealthSplit = findViewById(R.id.chartHealthSplit);
        chartTrend = findViewById(R.id.chartTrend);
        chartTopDiseases = findViewById(R.id.chartTopDiseases);
        chartLocations = findViewById(R.id.chartLocations);
        tvComparisonSummary = findViewById(R.id.tvComparisonSummary);
        tvComparisonDetails = findViewById(R.id.tvComparisonDetails);
        tvComparisonNote = findViewById(R.id.tvComparisonNote);
        comparisonCard = findViewById(R.id.comparisonCard);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        loadingLayout = findViewById(R.id.loadingLayout);
        contentLayout = findViewById(R.id.contentLayout);
    }

    private void setupCharts() {
        setupBarChart(chartHealthSplit, true);
        setupBarChart(chartTopDiseases, false);
        setupBarChart(chartLocations, false);
        setupLineChart(chartTrend);
    }

    private void setupBarChart(BarChart chart, boolean showLegend) {
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("No chart data");
        chart.setDrawGridBackground(false);
        chart.setFitBars(true);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getLegend().setEnabled(showLegend);
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getColor(R.color.Muted_Gray));
    }

    private void setupLineChart(LineChart chart) {
        if (chart == null) return;
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("No chart data");
        chart.setDrawGridBackground(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getLegend().setEnabled(false);
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(getColor(R.color.Muted_Gray));
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Reports");
            if (isAdminMode) {
                getSupportActionBar().setSubtitle("All users");
            }
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRangeToggle() {
        rangeToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            Range range = getRangeForButton(checkedId);
            loadReports(range);
        });
    }

    private boolean ensureSignedIn() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || currentUser.isAnonymous()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sign in required")
                    .setMessage("Please register or sign in to view your reports.")
                    .setPositiveButton("Register", (dialog, which) -> {
                        startActivity(new android.content.Intent(this, RegistrationActivity.class));
                        finish();
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> finish())
                    .show();
            return false;
        }
        return true;
    }

    private Range getRangeForButton(int buttonId) {
        if (buttonId == R.id.btnRange7) {
            return Range.DAYS_7;
        }
        if (buttonId == R.id.btnRange90) {
            return Range.DAYS_90;
        }
        if (buttonId == R.id.btnRangeAll) {
            return Range.ALL;
        }
        return Range.DAYS_30;
    }

    private void loadReports(@NonNull Range range) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        showLoading();
        currentRange = range;
        if (isAdminMode) {
            tvRangeLabel.setText(getString(R.string.reports_range_label_all_users, range.label));
        } else {
            tvRangeLabel.setText(getString(R.string.reports_range_label, range.label));
        }

        long now = System.currentTimeMillis();
        long rangeDuration = range.days * DAY_MS;
        long rangeStart = range == Range.ALL ? 0L : now - rangeDuration;
        long previousStart = rangeStart - rangeDuration;
        boolean includePrevious = range != Range.ALL;
        currentRangeStart = rangeStart;
        currentRangeEnd = now;

        Query query = db.collection("analysis_history");
        if (!isAdminMode) {
            query = query.whereEqualTo("userId", currentUser.getUid());
        }
        query = query.orderBy("timestamp", Query.Direction.DESCENDING);

        if (includePrevious) {
            query = query.whereGreaterThanOrEqualTo("timestamp", previousStart);
        }

        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<AnalysisHistory> currentRange = new ArrayList<>();
                    List<AnalysisHistory> previousRange = new ArrayList<>();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        AnalysisHistory history = document.toObject(AnalysisHistory.class);
                        if (history == null) {
                            continue;
                        }
                        long ts = history.getTimestamp();
                        if (range == Range.ALL || ts >= rangeStart) {
                            currentRange.add(history);
                        } else if (includePrevious && ts >= previousStart) {
                            previousRange.add(history);
                        }
                    }

                    if (currentRange.isEmpty()) {
                        showEmptyState();
                    } else {
                        showContent();
                        renderReports(range, currentRange, previousRange, rangeDuration);
                    }
                })
                .addOnFailureListener(e -> {
                    showEmptyState();
                    String errorMsg = e.getMessage();
                    if (errorMsg != null && errorMsg.contains("FAILED_PRECONDITION")) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Index Required")
                                .setMessage("The reports feature requires a database index.\n\n" +
                                        "Please check the error log for a link to create the required index in Firebase Console.\n\n" +
                                        "Error: " + errorMsg)
                                .setPositiveButton("OK", null)
                                .show();
                    } else {
                        Toast.makeText(this, "Failed to load reports: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void renderReports(Range range,
                               List<AnalysisHistory> currentRange,
                               List<AnalysisHistory> previousRange,
                               long rangeDuration) {
        List<DetectionEntry> currentDetections = collectDetections(currentRange);
        List<DetectionEntry> previousDetections = collectDetections(previousRange);

        int totalScans = currentRange.size();
        int totalDetections = currentDetections.size();
        int healthyCount = 0;
        int diseasedCount = 0;
        float confidenceSum = 0f;

        Map<String, DiseaseStats> diseaseStats = new HashMap<>();
        for (DetectionEntry entry : currentDetections) {
            confidenceSum += entry.confidence;
            if (isHealthy(entry.name)) {
                healthyCount++;
            } else {
                diseasedCount++;
                DiseaseStats stats = diseaseStats.get(entry.name);
                if (stats == null) {
                    stats = new DiseaseStats();
                    diseaseStats.put(entry.name, stats);
                }
                stats.count++;
                stats.confidenceSum += entry.confidence;
            }
        }

        tvTotalScansValue.setText(String.valueOf(totalScans));
        tvTotalDetectionsValue.setText(String.valueOf(totalDetections));
        tvDiseaseHealthyValue.setText("Diseased: " + diseasedCount + "  Healthy: " + healthyCount);

        if (totalDetections > 0) {
            float avgConfidence = confidenceSum / totalDetections;
            tvAvgConfidenceValue.setText(formatPercent(avgConfidence));
        } else {
            tvAvgConfidenceValue.setText("N/A");
        }

        updateHealthSplitChart(diseasedCount, healthyCount);
        updateTrendChart(currentDetections, range);

        tvTopDiseases.setText(buildTopDiseasesText(diseaseStats));
        updateTopDiseasesChart(diseaseStats);

        Map<String, Integer> locationCounts = buildLocationCounts(currentRange);
        tvLocationBreakdown.setText(buildLocationBreakdownText(locationCounts));
        updateLocationChart(locationCounts);

        tvLowConfidence.setText(buildLowConfidenceText(currentDetections));

        if (range == Range.ALL) {
            comparisonCard.setVisibility(View.GONE);
        } else {
            comparisonCard.setVisibility(View.VISIBLE);
            renderComparison(currentDetections, previousDetections, rangeDuration);
        }
    }

    private List<DetectionEntry> collectDetections(List<AnalysisHistory> histories) {
        List<DetectionEntry> entries = new ArrayList<>();
        if (histories == null) {
            return entries;
        }
        for (AnalysisHistory history : histories) {
            if (history == null) {
                continue;
            }
            String summary = history.getConfidence();
            entries.addAll(parseDetections(summary, history.getTimestamp()));
        }
        return entries;
    }

    private List<DetectionEntry> parseDetections(String summary, long timestamp) {
        List<DetectionEntry> entries = new ArrayList<>();
        if (TextUtils.isEmpty(summary)) {
            return entries;
        }

        Pattern pattern = Pattern.compile("([^,]+?)\\((\\d+(?:\\.\\d+)?)%");
        Matcher matcher = pattern.matcher(summary);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1).trim() : "";
            String confidenceText = matcher.group(2);
            if (TextUtils.isEmpty(name) || TextUtils.isEmpty(confidenceText)) {
                continue;
            }
            try {
                float confidence = Float.parseFloat(confidenceText);
                entries.add(new DetectionEntry(name, confidence, timestamp));
            } catch (NumberFormatException ignored) {
            }
        }
        return entries;
    }

    private boolean isHealthy(String name) {
        return name != null && name.toLowerCase(Locale.getDefault()).contains("healthy");
    }

    private String buildTopDiseasesText(Map<String, DiseaseStats> diseaseStats) {
        if (diseaseStats.isEmpty()) {
            return "No disease detections in this range.";
        }

        List<Map.Entry<String, DiseaseStats>> entries = new ArrayList<>(diseaseStats.entrySet());
        entries.sort((a, b) -> {
            int countCompare = Integer.compare(b.getValue().count, a.getValue().count);
            if (countCompare != 0) {
                return countCompare;
            }
            return Float.compare(b.getValue().getAverageConfidence(), a.getValue().getAverageConfidence());
        });

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, DiseaseStats> entry = entries.get(i);
            DiseaseStats stats = entry.getValue();
            builder.append(i + 1).append(". ")
                    .append(entry.getKey())
                    .append(" - ")
                    .append(stats.count)
                    .append(" detections (avg ")
                    .append(formatPercent(stats.getAverageConfidence()))
                    .append(")");
            if (i < limit - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String buildLowConfidenceText(List<DetectionEntry> detections) {
        List<DetectionEntry> lowConfidence = new ArrayList<>();
        for (DetectionEntry entry : detections) {
            if (entry.confidence < LOW_CONFIDENCE_THRESHOLD) {
                lowConfidence.add(entry);
            }
        }

        if (lowConfidence.isEmpty()) {
            return "No low-confidence detections in this range.";
        }

        Collections.sort(lowConfidence, Comparator.comparingDouble(a -> a.confidence));

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(lowConfidence.size(), 5);
        for (int i = 0; i < limit; i++) {
            DetectionEntry entry = lowConfidence.get(i);
            builder.append(entry.name)
                    .append(" - ")
                    .append(formatPercent(entry.confidence))
                    .append(" (")
                    .append(dateFormat.format(new Date(entry.timestamp)))
                    .append(")");
            if (i < limit - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String buildLocationBreakdown(List<AnalysisHistory> histories) {
        Map<String, Integer> counts = buildLocationCounts(histories);
        return buildLocationBreakdownText(counts);
    }

    private Map<String, Integer> buildLocationCounts(List<AnalysisHistory> histories) {
        Map<String, Integer> counts = new HashMap<>();
        if (histories == null) {
            return counts;
        }
        for (AnalysisHistory history : histories) {
            if (history == null) {
                continue;
            }
            String location = buildLocationKey(history);
            if (location == null) {
                continue;
            }
            counts.put(location, counts.getOrDefault(location, 0) + 1);
        }
        return counts;
    }

    private String buildLocationBreakdownText(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return "No location data in this range.";
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            builder.append(i + 1).append(". ")
                    .append(entry.getKey())
                    .append(" - ")
                    .append(entry.getValue())
                    .append(" scans");
            if (i < limit - 1) {
                builder.append("\n");
            }
        }
        return builder.toString();
    }

    private String buildLocationKey(AnalysisHistory history) {
        String municipality = history.getLocationMunicipality();
        String barangay = history.getLocationBarangay();
        if (municipality != null && !municipality.trim().isEmpty()
                && barangay != null && !barangay.trim().isEmpty()) {
            return municipality.trim() + " - " + barangay.trim();
        }
        String combined = history.getLocationName();
        if (combined != null && !combined.trim().isEmpty()) {
            return combined.trim();
        }
        return null;
    }

    private void renderComparison(List<DetectionEntry> currentDetections,
                                  List<DetectionEntry> previousDetections,
                                  long rangeDuration) {
        int currentTotal = currentDetections.size();
        int previousTotal = previousDetections.size();

        String changeText = formatChange(currentTotal, previousTotal);
        tvComparisonSummary.setText("Total detections: " + currentTotal +
                " (prev " + previousTotal + ", " + changeText + ")");

        Map<String, Integer> currentCounts = countDiseases(currentDetections);
        Map<String, Integer> previousCounts = countDiseases(previousDetections);

        if (previousTotal == 0) {
            tvComparisonNote.setVisibility(View.VISIBLE);
            tvComparisonNote.setText("No previous range data available.");
        } else {
            tvComparisonNote.setVisibility(View.GONE);
        }

        if (currentCounts.isEmpty()) {
            tvComparisonDetails.setText("No disease detections in this range.");
            return;
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(currentCounts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        StringBuilder builder = new StringBuilder();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            int current = entry.getValue();
            int previous = previousCounts.getOrDefault(entry.getKey(), 0);
            builder.append(entry.getKey())
                    .append(": ")
                    .append(current)
                    .append(" (prev ")
                    .append(previous)
                    .append(", ")
                    .append(formatChange(current, previous))
                    .append(")");
            if (i < limit - 1) {
                builder.append("\n");
            }
        }
        tvComparisonDetails.setText(builder.toString());
    }

    private Map<String, Integer> countDiseases(List<DetectionEntry> detections) {
        Map<String, Integer> counts = new HashMap<>();
        for (DetectionEntry entry : detections) {
            if (isHealthy(entry.name)) {
                continue;
            }
            counts.put(entry.name, counts.getOrDefault(entry.name, 0) + 1);
        }
        return counts;
    }

    private String formatPercent(float value) {
        return String.format(Locale.getDefault(), "%.1f%%", value);
    }

    private String formatChange(int current, int previous) {
        if (previous == 0) {
            return current > 0 ? "new" : "0%";
        }
        float change = ((float) (current - previous) / previous) * 100f;
        String sign = change > 0 ? "+" : "";
        return sign + String.format(Locale.getDefault(), "%.0f%%", change);
    }

    private void showLoading() {
        loadingLayout.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        loadingLayout.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
        contentLayout.setVisibility(View.GONE);
    }

    private void showContent() {
        loadingLayout.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);
        contentLayout.setVisibility(View.VISIBLE);
    }

    private void updateHealthSplitChart(int diseasedCount, int healthyCount) {
        if (chartHealthSplit == null) return;
        if (diseasedCount == 0 && healthyCount == 0) {
            chartHealthSplit.setVisibility(View.GONE);
            return;
        }
        chartHealthSplit.setVisibility(View.VISIBLE);
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, new float[]{diseasedCount, healthyCount}));
        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(
                getColor(android.R.color.holo_red_dark),
                getColor(R.color.Deep_Natural_Green)
        );
        dataSet.setStackLabels(new String[]{"Diseased", "Healthy"});
        dataSet.setValueTextColor(getColor(R.color.Near_Black));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chartHealthSplit.setData(data);

        XAxis xAxis = chartHealthSplit.getXAxis();
        List<String> labels = new ArrayList<>();
        labels.add("Selected range");
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());

        chartHealthSplit.getAxisLeft().setAxisMinimum(0f);
        chartHealthSplit.invalidate();
    }

    private void updateTopDiseasesChart(Map<String, DiseaseStats> diseaseStats) {
        if (chartTopDiseases == null) return;
        if (diseaseStats == null || diseaseStats.isEmpty()) {
            chartTopDiseases.setVisibility(View.GONE);
            return;
        }
        chartTopDiseases.setVisibility(View.VISIBLE);
        List<Map.Entry<String, DiseaseStats>> entries = new ArrayList<>(diseaseStats.entrySet());
        entries.sort((a, b) -> {
            int countCompare = Integer.compare(b.getValue().count, a.getValue().count);
            if (countCompare != 0) {
                return countCompare;
            }
            return Float.compare(b.getValue().getAverageConfidence(), a.getValue().getAverageConfidence());
        });

        List<BarEntry> chartEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, DiseaseStats> entry = entries.get(i);
            chartEntries.add(new BarEntry(i, entry.getValue().count));
            labels.add(entry.getKey());
        }

        BarDataSet dataSet = new BarDataSet(chartEntries, "Detections");
        dataSet.setColor(getColor(R.color.Deep_Natural_Green));
        dataSet.setValueTextColor(getColor(R.color.Near_Black));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chartTopDiseases.setData(data);

        XAxis xAxis = chartTopDiseases.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        xAxis.setLabelRotationAngle(30f);

        chartTopDiseases.getAxisLeft().setAxisMinimum(0f);
        chartTopDiseases.invalidate();
    }

    private void updateLocationChart(Map<String, Integer> locationCounts) {
        if (chartLocations == null) return;
        if (locationCounts == null || locationCounts.isEmpty()) {
            chartLocations.setVisibility(View.GONE);
            return;
        }
        chartLocations.setVisibility(View.VISIBLE);
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(locationCounts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<BarEntry> chartEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            chartEntries.add(new BarEntry(i, entry.getValue()));
            labels.add(entry.getKey());
        }

        BarDataSet dataSet = new BarDataSet(chartEntries, "Scans");
        dataSet.setColor(getColor(R.color.Deep_Natural_Green));
        dataSet.setValueTextColor(getColor(R.color.Near_Black));
        dataSet.setValueTextSize(10f);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chartLocations.setData(data);

        XAxis xAxis = chartLocations.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        xAxis.setLabelRotationAngle(30f);

        chartLocations.getAxisLeft().setAxisMinimum(0f);
        chartLocations.invalidate();
    }

    private void updateTrendChart(List<DetectionEntry> detections, Range range) {
        if (chartTrend == null) return;
        if (detections == null || detections.isEmpty()) {
            chartTrend.setVisibility(View.GONE);
            return;
        }

        TrendBucket bucket = getTrendBucket(range);
        Map<Long, Integer> counts = new TreeMap<>();
        for (DetectionEntry entry : detections) {
            long bucketStart = getBucketStart(entry.timestamp, bucket);
            counts.put(bucketStart, counts.getOrDefault(bucketStart, 0) + 1);
        }

        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int index = 0;

        if (bucket == TrendBucket.DAILY && currentRangeStart > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(getBucketStart(currentRangeStart, bucket));
            long end = getBucketStart(currentRangeEnd, bucket);
            while (cal.getTimeInMillis() <= end) {
                long key = cal.getTimeInMillis();
                int count = counts.getOrDefault(key, 0);
                entries.add(new Entry(index, count));
                labels.add(formatTrendLabel(key, bucket));
                cal.add(Calendar.DAY_OF_MONTH, 1);
                index++;
            }
        } else if (bucket == TrendBucket.WEEKLY && currentRangeStart > 0) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(getBucketStart(currentRangeStart, bucket));
            long end = getBucketStart(currentRangeEnd, bucket);
            while (cal.getTimeInMillis() <= end) {
                long key = cal.getTimeInMillis();
                int count = counts.getOrDefault(key, 0);
                entries.add(new Entry(index, count));
                labels.add(formatTrendLabel(key, bucket));
                cal.add(Calendar.DAY_OF_MONTH, 7);
                index++;
            }
        } else {
            for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
                entries.add(new Entry(index, entry.getValue()));
                labels.add(formatTrendLabel(entry.getKey(), bucket));
                index++;
            }
        }

        if (entries.isEmpty()) {
            chartTrend.setVisibility(View.GONE);
            return;
        }

        chartTrend.setVisibility(View.VISIBLE);
        LineDataSet dataSet = new LineDataSet(entries, "Total detections");
        dataSet.setColor(getColor(R.color.Deep_Natural_Green));
        dataSet.setCircleColor(getColor(R.color.Deep_Natural_Green));
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(3f);
        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        LineData data = new LineData(dataSet);
        chartTrend.setData(data);

        XAxis xAxis = chartTrend.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(Math.min(labels.size(), 6));
        xAxis.setLabelRotationAngle(30f);

        chartTrend.getAxisLeft().setAxisMinimum(0f);
        chartTrend.invalidate();
    }

    private TrendBucket getTrendBucket(Range range) {
        if (range == Range.ALL) {
            return TrendBucket.MONTHLY;
        }
        if (range == Range.DAYS_90) {
            return TrendBucket.WEEKLY;
        }
        return TrendBucket.DAILY;
    }

    private long getBucketStart(long timestamp, TrendBucket bucket) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (bucket == TrendBucket.WEEKLY) {
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int delta = (dow - Calendar.MONDAY + 7) % 7;
            cal.add(Calendar.DAY_OF_MONTH, -delta);
        } else if (bucket == TrendBucket.MONTHLY) {
            cal.set(Calendar.DAY_OF_MONTH, 1);
        }
        return cal.getTimeInMillis();
    }

    private String formatTrendLabel(long timestamp, TrendBucket bucket) {
        SimpleDateFormat formatter;
        if (bucket == TrendBucket.MONTHLY) {
            formatter = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        } else {
            formatter = new SimpleDateFormat("MMM dd", Locale.getDefault());
        }
        return formatter.format(new Date(timestamp));
    }

    private enum TrendBucket {
        DAILY,
        WEEKLY,
        MONTHLY
    }

    private static class DetectionEntry {
        private final String name;
        private final float confidence;
        private final long timestamp;

        private DetectionEntry(String name, float confidence, long timestamp) {
            this.name = name;
            this.confidence = confidence;
            this.timestamp = timestamp;
        }
    }

    private static class DiseaseStats {
        private int count;
        private float confidenceSum;

        private float getAverageConfidence() {
            return count > 0 ? confidenceSum / count : 0f;
        }
    }
}
