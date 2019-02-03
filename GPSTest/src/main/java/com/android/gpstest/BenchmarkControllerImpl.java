/*
 * Copyright (C) 2018 Sean J. Barbeau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.gpstest;

import android.animation.*;
import android.graphics.*;
import android.location.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import androidx.constraintlayout.motion.widget.*;

import com.android.gpstest.chart.*;
import com.android.gpstest.model.*;
import com.android.gpstest.util.*;
import com.github.mikephil.charting.charts.*;
import com.github.mikephil.charting.components.*;
import com.github.mikephil.charting.data.*;
import com.github.mikephil.charting.interfaces.datasets.*;
import com.github.mikephil.charting.utils.*;
import com.google.android.material.card.*;
import com.google.android.material.textfield.*;
import com.sothree.slidinguppanel.*;

import java.text.*;
import java.util.*;

import static android.text.TextUtils.*;
import static android.view.View.*;

/**
 * This class encapsulates logic used for the benchmarking feature that compares a user-entered
 * ground truth value against the GPS location.
 */
public class BenchmarkControllerImpl implements BenchmarkController {

    private static final String TAG = "BenchmarkCntlrImpl";

    private static final String BENCHMARK_CARD_COLLAPSED = "ground_truth_card_collapsed";

    private static final int ERROR_SET = 0;

    private static final int ESTIMATED_ACCURACY_SET = 1;

    private boolean mBenchmarkCardCollapsed = false;

    MaterialCardView mGroundTruthCardView, mVerticalErrorCardView;

    MotionLayout mMotionLayout;

    TextView mErrorView, mVertErrorView, mAvgErrorView, mAvgVertErrorView, mErrorLabel, mAvgErrorLabel, mLeftDivider, mRightDivider, mErrorUnit, mAvgErrorUnit;
    TextInputLayout mLatText, mLongText, mAltText;

    SlidingUpPanelLayout mSlidingPanel;

    SlidingUpPanelLayout.PanelState mLastPanelState;

    LineChart mErrorChart, mVertErrorChart;

    Location mGroundTruthLocation;

    AvgError mAvgError = new AvgError();

    private Listener mListener;

    public BenchmarkControllerImpl(View v, Bundle savedInstanceState) {
        mSlidingPanel = v.findViewById(R.id.bottom_sliding_layout);
        mErrorView = v.findViewById(R.id.error);
        mVertErrorView = v.findViewById(R.id.vert_error);
        mAvgErrorView = v.findViewById(R.id.avg_error);
        mAvgVertErrorView = v.findViewById(R.id.avg_vert_error);
        mErrorLabel = v.findViewById(R.id.error_label);
        mAvgErrorLabel = v.findViewById(R.id.avg_error_label);
        mAvgErrorLabel.setText(Application.get().getString(R.string.avg_error_label, 0));
        mLeftDivider = v.findViewById(R.id.divider_left);
        mRightDivider = v.findViewById(R.id.divider_right);
        mErrorUnit = v.findViewById(R.id.error_unit);
        mAvgErrorUnit = v.findViewById(R.id.avg_error_unit);
        mErrorChart = v.findViewById(R.id.error_chart);
        mVertErrorChart = v.findViewById(R.id.vert_error_chart);
        initChart(mErrorChart);
        initChart(mVertErrorChart);
        mVerticalErrorCardView = v.findViewById(R.id.vert_error_layout);
        mGroundTruthCardView = v.findViewById(R.id.benchmark_card);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mGroundTruthCardView.getLayoutParams();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mGroundTruthCardView.getLayoutTransition()
                    .enableTransitionType(LayoutTransition.CHANGING);
        }

        if (savedInstanceState != null) {
            // Activity is being restarted and has previous state (e.g., user rotated device)
            mBenchmarkCardCollapsed = savedInstanceState.getBoolean(BENCHMARK_CARD_COLLAPSED, false);
        }

        mMotionLayout = v.findViewById(R.id.motion_layout);
        Button saveGroundTruth = v.findViewById(R.id.save);
        mLatText = v.findViewById(R.id.ground_truth_lat);
        mLongText = v.findViewById(R.id.ground_truth_long);
        mAltText = v.findViewById(R.id.ground_truth_alt);
        mMotionLayout.setTransitionListener(new MotionLayout.TransitionListener() {

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {
                if (currentId == R.id.expanded) {
                    saveGroundTruth.setText(Application.get().getString(R.string.save));
                    mLatText.setEnabled(true);
                    mLongText.setEnabled(true);
                    mAltText.setEnabled(true);
                    mLatText.setFocusable(true);
                    mLongText.setFocusable(true);
                    mAltText.setFocusable(true);
                } else {
                    // Collapsed
                    saveGroundTruth.setText(Application.get().getString(R.string.edit));
                    mLatText.setEnabled(false);
                    mLongText.setEnabled(false);
                    mAltText.setEnabled(false);
                    mLatText.setFocusable(false);
                    mLongText.setFocusable(false);
                    mAltText.setFocusable(false);
                }
            }
            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
            }
            @Override
            public void onTransitionChange(MotionLayout motionLayout, int startId, int endId, float progress) {
            }
            @Override
            public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b, float v) {
            }
        });

        // TODO - set initial state of card and motion layout depending on savedInstanceState
        // TODO - set initial state of sliding panel depending on savedInstanceState

        saveGroundTruth.setOnClickListener(view -> {
            if (!mBenchmarkCardCollapsed) {
                // TODO - if lat and long aren't filled, show error

                //PP java.lang.NumberFormatException: For input string: "42,7138500"
                NumberFormat localeFormat = DecimalFormat.getInstance(Locale.getDefault());

                // Save Ground Truth
                mGroundTruthLocation = new Location("ground_truth");
try {
    if (!isEmpty(mLatText.getEditText().getText().toString()) && !isEmpty(mLongText.getEditText().getText().toString())) {
        mGroundTruthLocation.setLatitude(localeFormat.parse(/*Double.valueOf(*/mLatText.getEditText().getText().toString()).doubleValue());
        mGroundTruthLocation.setLongitude(localeFormat.parse(/*Double.valueOf(*/mLongText.getEditText().getText().toString()).doubleValue());
    }
    if (!isEmpty(mAltText.getEditText().getText().toString())) {
        mGroundTruthLocation.setAltitude(localeFormat.parse(/*Double.valueOf(*/mAltText.getEditText().getText().toString()).doubleValue());
    }
} catch (Exception e) {
    e.printStackTrace();
}
                // Collapse card - we have to set height on card manually because card doesn't auto-collapse right when views are within card container
                mMotionLayout.transitionToEnd();
                lp.height = (int) Application.get().getResources().getDimension(R.dimen.ground_truth_cardview_height_collapsed);
                mGroundTruthCardView.setLayoutParams(lp);
                mBenchmarkCardCollapsed = true;

                resetError();

                // Show sliding panel if it's not visible
                if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.HIDDEN) {
                    mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                    mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                }
                if (mListener != null) {
                    mListener.onAllowGroundTruthEditChanged(false);
                    mListener.onGroundTruthLocationSaved(mGroundTruthLocation);
                }
            } else {
                // Expand card to allow editing ground truth
                mMotionLayout.transitionToStart();
                // We have to set height on card manually because it doesn't auto-expand right when views are within card container
                lp.height = (int) Application.get().getResources().getDimension(R.dimen.ground_truth_cardview_height);
                mGroundTruthCardView.setLayoutParams(lp);
                mBenchmarkCardCollapsed = false;

                // Collapse sliding panel if it's anchored so there is room
                if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED) {
                    mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                }

                if (mListener != null) {
                    mListener.onAllowGroundTruthEditChanged(true);
                }
            }
        });
    }

    private void initChart(LineChart errorChart) {
        errorChart.getDescription().setEnabled(false);
        errorChart.setTouchEnabled(true);
        errorChart.setDragEnabled(true);
        errorChart.setScaleEnabled(true);
        errorChart.setDrawGridBackground(false);
        // If disabled, scaling can be done on x- and y-axis separately
        errorChart.setPinchZoom(true);

        // Set an alternate background color
        //mErrorChart.setBackgroundColor(Color.LTGRAY);

        LineData data = new LineData();
        //data.setValueTextColor(Color.WHITE);

        // Add empty data
        errorChart.setData(data);

        // Get the legend (only possible after setting data)
        Legend l = errorChart.getLegend();
        l.setEnabled(true);

//        // Modify the legend ...
//        l.setForm(Legend.LegendForm.LINE);
//        //l.setTypeface(tfLight);
//        l.setTextColor(Color.WHITE);

        XAxis xAxis = errorChart.getXAxis();
        //xAxis.setTypeface(tfLight);
        //xAxis.setTextColor(Color.WHITE);
        xAxis.setDrawGridLines(false);
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);

        DistanceValueFormatter formatter = new DistanceValueFormatter("m");

        YAxis leftAxis = errorChart.getAxisLeft();
        //leftAxis.setTypeface(tfLight);
        //leftAxis.setTextColor(Color.WHITE);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setDrawGridLines(true);
        leftAxis.setValueFormatter(formatter);

        YAxis rightAxis = errorChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    private void resetError() {
        mAvgError.reset();
        mErrorView.setVisibility(INVISIBLE);
        mVertErrorView.setVisibility(INVISIBLE);
        mAvgErrorView.setVisibility(INVISIBLE);
        mAvgVertErrorView.setVisibility(INVISIBLE);
        mLeftDivider.setVisibility(INVISIBLE);
        mRightDivider.setVisibility(INVISIBLE);
        mErrorUnit.setVisibility(INVISIBLE);
        mAvgErrorUnit.setVisibility(INVISIBLE);
        mAvgErrorLabel.setText(Application.get().getString(R.string.avg_error_label, 0));

        mErrorChart.clearValues();
        mVertErrorChart.clearValues();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        // Save current benchmark card state
        outState.putBoolean(BENCHMARK_CARD_COLLAPSED, mBenchmarkCardCollapsed);
    }

    /**
     * Called from the hosting Activity when onBackPressed() is called (i.e., when the user
     * presses the back button)
     * @return true if the controller handled in the click and super.onBackPressed() should not be
     * called by the hosting Activity, or false if the controller didn't handle the click and
     * super.onBackPressed() should be called
     */
    @Override
    public boolean onBackPressed() {
        // Collapse the panel when the user presses the back button
        if (mSlidingPanel != null) {
            // Collapse the sliding panel if its anchored or expanded
            if (mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED
                    || mSlidingPanel.getPanelState() == SlidingUpPanelLayout.PanelState.ANCHORED) {
                mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets a lister that will be updated when benchmark controller events are fired
     * @param listener a lister that will be updated when benchmark controller events are fired
     */
    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void show() {
        if (mGroundTruthCardView != null) {
            mGroundTruthCardView.setVisibility(VISIBLE);
        }
        if (mMotionLayout != null) {
            mMotionLayout.setVisibility(VISIBLE);
        }
        if (mSlidingPanel != null && mLastPanelState != null) {
            mSlidingPanel.setPanelState(mLastPanelState);
        }
    }

    public void hide() {
        if (mGroundTruthCardView != null) {
            mGroundTruthCardView.setVisibility(GONE);
        }
        if (mMotionLayout != null) {
            mMotionLayout.setVisibility(GONE);
        }
        if (mSlidingPanel != null) {
            mLastPanelState = mSlidingPanel.getPanelState();
            mSlidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.HIDDEN);
        }
    }

    @Override
    public void gpsStart() {

    }

    @Override
    public void gpsStop() {

    }

    @Override
    public void onGpsStatusChanged(int event, GpsStatus status) {

    }

    @Override
    public void onGnssFirstFix(int ttffMillis) {

    }

    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {

    }

    @Override
    public void onGnssStarted() {

    }

    @Override
    public void onGnssStopped() {

    }

    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {

    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {

    }

    @Override
    public void onNmeaMessage(String message, long timestamp) {

    }

    @Override
    public void onLocationChanged(Location location) {
        if (mGroundTruthLocation == null || !mBenchmarkCardCollapsed) {
            // If we don't have a ground truth location yet, or if the user is editing the location,
            // don't update the errors
            return;
        }
        MeasuredError error = BenchmarkUtils.Companion.measureError(location, mGroundTruthLocation);
        mAvgError.addMeasurement(error);
        if (mErrorView != null && mAvgErrorView != null) {
            mErrorUnit.setVisibility(VISIBLE);
            mErrorView.setVisibility(VISIBLE);
            mErrorView.setText(Application.get().getString(R.string.benchmark_error, error.getError()));
            mAvgErrorUnit.setVisibility(VISIBLE);
            mAvgErrorView.setVisibility(VISIBLE);
            mAvgErrorView.setText(Application.get().getString(R.string.benchmark_error, mAvgError.getAvgError()));
            mAvgErrorLabel.setText(Application.get().getString(R.string.avg_error_label, mAvgError.getCount()));
        }
        if (mVertErrorView != null && !Double.isNaN(error.getVertError())) {
            // Vertical errors
            mErrorLabel.setText(R.string.horizontal_vertical_error_label);
            mLeftDivider.setVisibility(VISIBLE);
            mRightDivider.setVisibility(VISIBLE);
            mVertErrorView.setVisibility(VISIBLE);
            mVertErrorView.setText(Application.get().getString(R.string.benchmark_error, error.getVertError()));
            mAvgVertErrorView.setVisibility(VISIBLE);
            mAvgVertErrorView.setText(Application.get().getString(R.string.benchmark_error, mAvgError.getAvgVertError()));
            mVerticalErrorCardView.setVisibility(VISIBLE);
        } else {
            // Hide any vertical error indication
            mErrorLabel.setText(R.string.horizontal_error_label);
            mLeftDivider.setVisibility(GONE);
            mRightDivider.setVisibility(GONE);
            mVertErrorView.setVisibility(GONE);
            mAvgVertErrorView.setVisibility(GONE);
            mVerticalErrorCardView.setVisibility(GONE);
        }
        addErrorToGraphs(error, location);
    }

    private void addErrorToGraphs(MeasuredError error, Location location) {
        addErrorToGraph(mErrorChart, error.getError(), location.getAccuracy());

        if (!Double.isNaN(error.getVertError())) {
            float vertAccuracy = Float.NaN;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vertAccuracy = location.getVerticalAccuracyMeters();
            }

            addErrorToGraph(mVertErrorChart, error.getVertError(), vertAccuracy);
        }
    }

    private void addErrorToGraph(LineChart chart, double error, float estimatedAccuracy) {
        LineData data = chart.getData();

        if (data != null) {
            ILineDataSet errorSet = data.getDataSetByIndex(ERROR_SET);
            ILineDataSet estimatedSet = data.getDataSetByIndex(ESTIMATED_ACCURACY_SET);
            // errorSet.addEntry(...); // can be called as well

            if (errorSet == null) {
                errorSet = createGraphDataSet(ERROR_SET);
                data.addDataSet(errorSet);
            }
            if (estimatedSet == null && !Float.isNaN(estimatedAccuracy)) {
                estimatedSet = createGraphDataSet(ESTIMATED_ACCURACY_SET);
                data.addDataSet(estimatedSet);
            }

            data.addEntry(new Entry(mAvgError.getCount(), (float) error), ERROR_SET);
            if (!Float.isNaN(estimatedAccuracy)) {
                data.addEntry(new Entry(mAvgError.getCount(), estimatedAccuracy), ESTIMATED_ACCURACY_SET);
            }
            data.notifyDataChanged();

            // let the chart know it's data has changed
            chart.notifyDataSetChanged();

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(40);
            // chart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            chart.moveViewToX(data.getEntryCount());

            // this automatically refreshes the chart (calls invalidate())
            // chart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    /**
     * Creates a graph dataset, for error if set is ERROR_SET, or for estimated accuracy if ESTIMATED_ACCURACY_SET
     * @param setType creates a data set for error if set is ERROR_SET, and for estimated accuracy if ESTIMATED_ACCURACY_SET
     * @return a graph dataset
     */
    private LineDataSet createGraphDataSet(int setType) {
        String label;
        if (setType == ERROR_SET) {
            label = Application.get().getResources().getString(R.string.measured_error_graph_label);
        } else {
            label = Application.get().getResources().getString(R.string.estimated_accuracy_graph_label);
        }

        LineDataSet set = new LineDataSet(null, label);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        if (setType == ERROR_SET) {
            set.setColor(Color.RED);
        } else {
            set.setColor(ColorTemplate.getHoloBlue());
        }
        set.setCircleColor(Color.BLACK);
        set.setLineWidth(2f);
        set.setCircleRadius(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    public void onMapClick(Location location) {
        if (!mBenchmarkCardCollapsed) {
            mLatText.getEditText().setText(Application.get().getString(R.string.benchmark_lat_long, location.getLatitude()));
            mLongText.getEditText().setText(Application.get().getString(R.string.benchmark_lat_long, location.getLongitude()));

            if (location.hasAltitude()) {
                mAltText.getEditText().setText(Application.get().getString(R.string.benchmark_alt, location.getAltitude()));
            }
        }
    }
}
