package com.sprd.generalsecurity.network;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.format.Formatter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.Contract;
import com.sprd.generalsecurity.utils.DateCycleUtils;
import com.sprd.generalsecurity.utils.TeleUtils;

import java.util.Date;
import java.util.List;

import static android.net.NetworkTemplate.buildTemplateMobileAll;
import android.net.TrafficStats;
import android.widget.Toast;
import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;

public class DataFlowMainEntry extends Activity implements View.OnClickListener {

    private static final String TAG = "DataFlowMainEntry";
    private static final String SIM1 = "sim1";
    private static final String SIM2 = "sim2";
    private static final int SIM1_INDEX = 0;
    private static final int SIM2_INDEX = 1;
    private static final int COUNT_SINGLE = 1;
    private static final int COUNT_DUAL = 2;

    private INetworkStatsSession mStatsSession;
    private INetworkStatsService mStatsService;
    private NetworkTemplate mTemplate;

    private float mSim1DataTotal;
    private float mSim1DataUsed;
    private float mSim2DataTotal;
    private float mSim2DataUsed;
    private long mSim1BitsUsed;
    private long mSim1BitsRemained;
    private long mSim2BitsUsed;
    private long mSim2BitsRemained;
    private static int mPrimaryCard = 0;
    private static int mSimCount;

    private TextView mRemainedFlow;
    private PrefsFragment mPrefsFragment;
    private static final String STATE_SIM1_USED = "sim1_used";
    private static final String STATE_SIM1_REMAINED = "sim1_remained";
    private static final String STATE_SIM1_TOTAL = "sim1_total";

    private static final String STATE_SIM2_USED = "sim2_used";
    private static final String STATE_SIM2_REMAINED = "sim2_remained";
    private static final String STATE_SIM2_TOTAL = "sim2_total";

    private static final String DATA_FLOW_INTENT = "com.sprd.generalsecurity.network.dataflowsetting";

    /** UNISOC: add for #1001755 @{ */
    private TextView mAllFlow;
    /** @} */
    /** UNISOC: add for #1003391 @{ */
    private SimStateChangeBroadCast simStateChangeBroadCast;
    /** @} */
    private final int[] drawableIds = {R.drawable.data_flow_remain_0,
            R.drawable.data_flow_remain_10, R.drawable.data_flow_remain_20,
            R.drawable.data_flow_remain_30, R.drawable.data_flow_remain_40,
            R.drawable.data_flow_remain_50, R.drawable.data_flow_remain_60,
            R.drawable.data_flow_remain_70, R.drawable.data_flow_remain_80,
            R.drawable.data_flow_remain_90, R.drawable.data_flow_remain_100};

    private List<SubscriptionInfo> mSubInfoList;
    private SubscriptionManager mSubscriptionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
        mSimCount = TeleUtils.getSimCount(this);

        mStatsService = INetworkStatsService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
        try {
            mStatsSession = mStatsService.openSession();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        if (mSimCount <= COUNT_SINGLE) {
            setContentView(R.layout.data_entry);
            if (mSimCount < COUNT_SINGLE) { //no sim
                Button bt = (Button) findViewById(R.id.button);
                bt.setEnabled(false);
                bt.setTextColor(Color.GRAY);
            }
        } else {
            setContentView(R.layout.data_entry_dual_sim);
        }
        if (mPrefsFragment == null) {
            /** UNISOC: modify for bug 1164697 @{ */
            mPrefsFragment = new PrefsFragment();
            /** @} */
        }
        getFragmentManager().beginTransaction().replace(R.id.pref, mPrefsFragment).commit();
        Button bt = (Button) findViewById(R.id.button);
        bt.setOnClickListener(this);

        initStatChart();

        /** UNISOC: add for #1003391 @{ */
        if (simStateChangeBroadCast == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.SIM_STATE_CHANGED");
            simStateChangeBroadCast = new SimStateChangeBroadCast();
            this.registerReceiver(simStateChangeBroadCast, filter);
        }
        /** @} */
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (DateCycleUtils.isSystemTimeCorrect(System.currentTimeMillis())) {
            checkUserDataSetting();
        }

        mPrimaryCard = TeleUtils.getPrimarySlot(this) - 1;//sim index is 0 based
        startNetworkStatsLoader();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(STATE_SIM1_USED, mSim1BitsUsed);
        outState.putLong(STATE_SIM1_REMAINED, mSim1BitsRemained);
        outState.putFloat(STATE_SIM1_TOTAL, mSim1DataTotal);

        if (mSimCount == COUNT_DUAL) {
            outState.putLong(STATE_SIM2_USED, mSim2BitsUsed);
            outState.putLong(STATE_SIM2_REMAINED, mSim2BitsRemained);
            outState.putFloat(STATE_SIM2_TOTAL, mSim2DataTotal);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);

        //update sim1 view
        mSim1DataTotal = state.getFloat(STATE_SIM1_TOTAL);
        mSim1BitsUsed = state.getLong(STATE_SIM1_USED);
        mSim1BitsRemained = state.getLong(STATE_SIM1_REMAINED);
        if (mSim1DataTotal == 0) {
            updateDataUnsetView(true, SIM1_INDEX);
        } else {
            updateDataUnsetView(false, SIM1_INDEX);
        }
        updatePieView(SIM1_INDEX, mSim1BitsUsed, mSim1BitsRemained);

        //update sim2 view
        if (mSimCount == COUNT_DUAL) {
            mSim2DataTotal = state.getFloat(STATE_SIM2_TOTAL);
            mSim2BitsUsed = state.getLong(STATE_SIM2_USED);
            mSim2BitsRemained = state.getLong(STATE_SIM2_REMAINED);
            if (mSim2DataTotal == 0) {
                updateDataUnsetView(true, SIM2_INDEX);
            } else {
                updateDataUnsetView(false, SIM2_INDEX);
            }
            updatePieView(SIM2_INDEX, mSim2BitsUsed, mSim2BitsRemained);
        }
    }

    /**
     * UNISOC: Bug815425 modify the memory leak issue.
     * @{
     */
    @Override
    protected void onDestroy() {
        TrafficStats.closeQuietly(mStatsSession);
        super.onDestroy();
        /** UNISOC: add for #1003391 @{ */
        if (simStateChangeBroadCast != null) {
            unregisterReceiver(simStateChangeBroadCast);
        }
        /** @} */
    }
    /**
     * @}
     */
    /*
     * Check if the a new month starts. If a new month starts, resets the user data set.
     */
    private void checkUserDataSetting() {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int savedMonth = sharedPref.getInt(Contract.KEY_CURRENT_MONTH, -1);
        Date dt = new Date();
        if (savedMonth != dt.getMonth()) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(Contract.KEY_CURRENT_MONTH, dt.getMonth());
            editor.apply();

            resetUserSetting(getSharedPreferences(SIM1, Context.MODE_PRIVATE).edit());
            resetUserSetting(getSharedPreferences(SIM2, Context.MODE_PRIVATE).edit());
        }
    }

    private void resetUserSetting(SharedPreferences.Editor editor) {
        Log.d(TAG, "resetUserSetting");
        editor.putLong(Contract.KEY_DATA_USED_QUERY_DELTA, 0);
        editor.putString(Contract.KEY_MONTH_USED, "0");
        editor.putString(Contract.KEY_DATA_USED_ORIGINAL, "0");
        editor.apply();
    }

    private void unsetSimChart(int simId) {
        final int INIT_PERCENT = 0;
        LinearLayout l = (simId == SIM1_INDEX) ? (LinearLayout) findViewById(R.id.df_circle)
                : (LinearLayout) findViewById(R.id.df_circle2);
        l.setBackground(getResources().getDrawable(
                drawableIds[(int) Math.rint((INIT_PERCENT / 100.0) * 10)]));
        mRemainedFlow = (TextView) l.findViewById(R.id.df_remained);
        mRemainedFlow.setText(Formatter.formatFileSize(this, INIT_PERCENT));

        /** UNISOC: add for #1001755 @{ */
        mAllFlow = l.findViewById(R.id.df_all);
        mAllFlow.setText(Formatter.formatFileSize(this, INIT_PERCENT));
        /** @} */
    }

    private void initStatChart() {
        if (mSimCount <= COUNT_SINGLE) {
            updateDataUnsetView(true, SIM1_INDEX);
        } else {
            updateDataUnsetView(true, SIM1_INDEX);
            updateDataUnsetView(true, SIM2_INDEX);
        }
    }

    /**
     * update the text views visibility
     *
     * @param dataUnset if data is set by user in the preference
     * @param whichSim  sim1 or sim2
     */
    private void updateDataUnsetView(boolean dataUnset, int whichSim) {
        if (dataUnset) {
            unsetSimChart(whichSim);
            if (findViewById(R.id.df_remained_str) != null) {
                ((TextView) findViewById(R.id.df_remained_str)).setText(R.string.data_unset);
            }
        }
    }

    @Override
    public void onClick(View v) {
        Intent it = new Intent(DATA_FLOW_INTENT);
        if (v.getId() == R.id.button) {
            it.putExtra(Contract.EXTRA_SIM_ID, 1);
            startActivity(it);
        }
    }

    private void updatePieView(int simId, long bitsUsed, long bitsRemained) {
        float simDataTotal = simId == 0 ? mSim1DataTotal : mSim2DataTotal;
        LinearLayout l = (simId == SIM1_INDEX) ? (LinearLayout) findViewById(R.id.df_circle)
                : (LinearLayout) findViewById(R.id.df_circle2);
        int percentUsed = 0;
        if (bitsRemained == 0) {
            percentUsed = 100;
        } else {
            percentUsed = bitsUsed > 0 ? (int) (100 * bitsUsed / (simDataTotal * Contract.M2BITS)) : 0;
        }
        l.setBackground(getResources().getDrawable(drawableIds[(int) Math.rint((percentUsed / 100.0) * 10)]));
        mRemainedFlow = (TextView) l.findViewById(R.id.df_remained);
        mRemainedFlow.setText(Formatter.formatFileSize(this, bitsRemained));
        if (findViewById(R.id.df_remained_str) != null) {
            ((TextView) findViewById(R.id.df_remained_str)).setText(R.string.df_remained_title);
        }

        /** UNISOC: add for #1001755 1061194 @{ */
        mAllFlow = l.findViewById(R.id.df_all);
        mAllFlow.setText(Formatter.formatFileSize(this, (long) (simDataTotal*Contract.M2BITS)));
        /** @} */
    }


    void updateStatsChart(int id, NetworkStats data) {
        if (data == null) {
            return;
        }
        long totalBytes = 0;
        NetworkStats.Entry entry = null;
        for (int i = 0; i < data.size(); i++) {
            entry = data.getValues(i, entry);
            totalBytes += (entry.rxBytes + entry.txBytes);
        }
        Log.d(TAG, "--totalBytes +" + totalBytes);

        if (id == SIM1_INDEX) { //SIM1

            SharedPreferences pref;
            if (mSimCount == COUNT_SINGLE) {
                if (mPrimaryCard == SIM1_INDEX) {
                    pref = this.getSharedPreferences(SIM1, Context.MODE_PRIVATE);
                } else {
                    pref = this.getSharedPreferences(SIM2, Context.MODE_PRIVATE);
                }
            } else { // dual sim release
                pref = this.getSharedPreferences(SIM1, Context.MODE_PRIVATE);
            }

            mSim1DataTotal = Float.parseFloat(pref.getString(Contract.KEY_MONTH_TOTAL, "0"));
            mSim1DataUsed = Float.parseFloat(pref.getString(Contract.KEY_DATA_USED_ORIGINAL, "0"));

            if (mSim1DataTotal == 0) {
                //Data unset
                updateDataUnsetView(true, SIM1_INDEX);
                return;
            }

            long delta = pref.getLong(Contract.KEY_DATA_USED_QUERY_DELTA, 0);
            Log.d(TAG, "mSim1DataTotal:" + mSim1DataTotal + ":" + delta + ":" + mPrimaryCard);

            mSim1BitsUsed = totalBytes - delta;
            if (mSim1BitsUsed > (long) (mSim1DataUsed * Contract.M2BITS)) {
                mSim1DataUsed = (float) (mSim1BitsUsed / Contract.M2BITS);
            }

            mSim1BitsRemained = (long) (mSim1DataTotal * Contract.M2BITS) - mSim1BitsUsed;
            if (mSim1BitsRemained < 0) {
                mSim1BitsRemained = 0;
            }

            updatePieView(id, mSim1BitsUsed, mSim1BitsRemained);
        } else if (id == SIM2_INDEX) {
            SharedPreferences pref = this.getSharedPreferences(SIM2, Context.MODE_PRIVATE);

            mSim2DataTotal = Float.parseFloat(pref.getString(Contract.KEY_MONTH_TOTAL, "0"));
            mSim2DataUsed = Float.parseFloat(pref.getString(Contract.KEY_DATA_USED_ORIGINAL, "0"));

            if (mSim2DataTotal == 0) {
                //Data unset
                updateDataUnsetView(true, SIM2_INDEX);
                return;
            }

            long delta = pref.getLong(Contract.KEY_DATA_USED_QUERY_DELTA, 0);
            mSim2BitsUsed = (long) (totalBytes - delta);
            if (mSim2BitsUsed > (long) (mSim2DataUsed * Contract.M2BITS)) {
                mSim2DataUsed = (float) (mSim2BitsUsed / Contract.M2BITS);
            }

            Log.d(TAG, "total=" + mSim2DataTotal + ":" + mSim2DataUsed + ":" + totalBytes);
            mSim2BitsRemained = (long) (mSim2DataTotal * Contract.M2BITS) - mSim2BitsUsed;
            if (mSim2BitsRemained < 0) {
                mSim2BitsRemained = 0;
            }
            updatePieView(id, mSim2BitsUsed, mSim2BitsRemained);
        }
    }

    private boolean isMobileDataAvailable(int subId) {
        return mSubscriptionManager.getActiveSubscriptionInfo(subId) != null;
    }

    void startNetworkStatsLoader() {
        long start = DateCycleUtils.getMonthCycleStart();
        try {
            mStatsService.forceUpdate();
        } catch (RemoteException e) {
            Log.e(TAG, "exception caught:" + e);
        }

        if (mSimCount == COUNT_SINGLE) {
            //single card inserted, need to check the primary card num
            mTemplate = buildTemplateMobileAll(TeleUtils.getActiveSubscriberId(this));

            // kick off loader for sim1 detailed stats
            getLoaderManager().restartLoader(0,
                    SummaryForAllUidLoader.buildArgs(mTemplate, start,
                            System.currentTimeMillis()), mSummaryCallbacks);

            return;
        }

        //dual card case
        if (mSimCount == COUNT_DUAL) {
            for (int i = 1; i <= COUNT_DUAL; i++) {
                mTemplate = buildTemplateMobileAll(TeleUtils.getActiveSubscriberId(this, i));
                getLoaderManager().restartLoader(i - 1,
                        SummaryForAllUidLoader.buildArgs(mTemplate, start,
                                System.currentTimeMillis()), mSummaryCallbacks);
            }
        }
    }

    private final LoaderCallbacks<NetworkStats> mSummaryCallbacks = new LoaderCallbacks<NetworkStats>() {
        @Override
        public Loader<NetworkStats> onCreateLoader(int id, Bundle args) {
            return new SummaryForAllUidLoader(DataFlowMainEntry.this, mStatsSession, args);
        }

        @Override
        public void onLoadFinished(Loader<NetworkStats> loader, NetworkStats data) {
            updateStatsChart(loader.getId(), data);
        }

        @Override
        public void onLoaderReset(Loader<NetworkStats> loader) {

        }
    };


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    //Mainly for single sim
    public static class PrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

        private static final int INDEX_MONTH = 0;
        private static final int INDEX_WEEK = 1;
        private static final int INDEX_DAY = 2;

        private static boolean mDualRelease;//= true;

        //private int mPrimarySim = 0;
        private Context mContext;
        SharedPreferences mSharedPref;

        private FloatKeyView mFloatKeyView;
        private boolean mShowRealSpeed, mShowLockFlow;

        private static final String KEY_CYCLE = "pref_data_cycle";

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mDualRelease = DataFlowMainEntry.mSimCount == COUNT_DUAL ? true : false;

            if (mDualRelease) {
                addPreferencesFromResource(R.xml.data_entry_dual_sim);
            } else {
                addPreferencesFromResource(R.xml.data_entry);
            }
        /** UNISOC: modify for bug 1164697 @{ */
            mContext = getContext();
        }

        public PrefsFragment() {}
        /** @} */
        @Override
        public void onResume() {
            super.onResume();
            /* UNISOC: Bug1001781 summery of data cycle not display when dual sim @{ */
            String v;
            mSharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (mDualRelease) {
                if (mPrimaryCard == SIM1_INDEX) {
                    v = mSharedPref.getString(DateCycleUtils.KEY_DATA_CYCLE_SIM1, "0");
                } else {
                    v = mSharedPref.getString(DateCycleUtils.KEY_DATA_CYCLE_SIM2, "0");
                }
            } else {
                ListPreference lp = (ListPreference) findPreference(KEY_CYCLE);
                lp.setOnPreferenceChangeListener(this);
                if (mPrimaryCard == SIM2_INDEX) {
                    v = mSharedPref.getString(DateCycleUtils.KEY_DATA_CYCLE_SIM2, "0");
                } else {
                    v = mSharedPref.getString(DateCycleUtils.KEY_DATA_CYCLE_SIM1, "0");
                }
            }
            setSummary(v);
            /* @} */
            CheckBoxPreference cp = (CheckBoxPreference) findPreference(Contract.KEY_REAL_SPEED_SWITCH);
            cp.setOnPreferenceChangeListener(this);
            CheckBoxPreference cpKeyguard = (CheckBoxPreference) findPreference(Contract.KEY_LOCK_DATA_SWITCH);
            cpKeyguard.setOnPreferenceChangeListener(this);

            mShowLockFlow = mSharedPref.getBoolean(Contract.KEY_LOCK_DATA_SWITCH, false);
            mShowRealSpeed = mSharedPref.getBoolean(Contract.KEY_REAL_SPEED_SWITCH, false);
        }

        void setSummary(String v) {
            int index = Integer.parseInt(v);
            /* UNISOC: Bug1001781 summery of data cycle not display when dual sim @{ */
            Preference lp = findPreference(KEY_CYCLE);
            if (lp instanceof ListPreference) {
                ((ListPreference) lp).setValue(v);
            }
            /* @} */
            switch (index) {
                case INDEX_MONTH:
                    lp.setSummary(getResources().getString(R.string.pref_data_cycle_default));
                    break;
                case INDEX_WEEK:
                    lp.setSummary(getResources().getString(R.string.data_cycle_week));
                    break;
                case INDEX_DAY:
                    lp.setSummary(getResources().getString(R.string.data_cycle_day));
                    break;
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference.getKey().equalsIgnoreCase(KEY_CYCLE)) {
                setSummary((String) newValue);

                //back up, so when new sim inserted, the new valuse can be resumed.
                /* UNISOC: Bug1001781 summery of data cycle not display when dual sim @{ */
                SharedPreferences.Editor editor = mSharedPref.edit();
                if (mPrimaryCard == SIM2_INDEX) {
                    editor.putString(DateCycleUtils.KEY_DATA_CYCLE_SIM2, (String) newValue);
                } else {
                    editor.putString(DateCycleUtils.KEY_DATA_CYCLE_SIM1, (String) newValue);
                }
                editor.commit();
                /* @} */
                return true;
            }

            if (preference.getKey().equalsIgnoreCase(Contract.KEY_REAL_SPEED_SWITCH)) {
                if ((boolean) newValue) {
                    mContext.startService(new Intent(mContext, FloatViewService.class));

                    Intent it = new Intent(this.getActivity(), ScreenStateService.class);
                    getActivity().startService(it);
                    mShowRealSpeed = true;
                } else {
                    /* UNISOC: modify for #612719 @{ */
                    if (mFloatKeyView == null && mContext != null) {
                        mFloatKeyView = FloatKeyView.getInstance(mContext);
                    }
                    /* @} */
                    if (mFloatKeyView != null) {
                        mContext.stopService(new Intent(mContext, FloatViewService.class));
                    }
                    mShowRealSpeed = false;
                    checkToStopScreenStateService();
                }
                return true;
            }

            if (preference.getKey().equalsIgnoreCase(Contract.KEY_LOCK_DATA_SWITCH)) {
                if ((boolean) newValue) {
                    Intent it = new Intent(this.getActivity(), ScreenStateService.class);
                    getActivity().startService(it);
                    mShowLockFlow = true;
                } else {
                    mShowLockFlow = false;
                    checkToStopScreenStateService();
                }
            }
            return true;
        }

        void checkToStopScreenStateService() {
            if (!mShowLockFlow && !mShowRealSpeed) {
                Intent it = new Intent(this.getActivity(), ScreenStateService.class);
                getActivity().stopService(it);
                Log.e(TAG, "stop ScreenStateService");
            }
        }
    }

    /** UNISOC: add for #1003391 @{ */
    private class SimStateChangeBroadCast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE) == null) {
                return;
            }

            if (mSimCount != TeleUtils.getSimCount(DataFlowMainEntry.this)) {
                ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.RunningTaskInfo info = manager.getRunningTasks(1).get(0);
                String className = info.topActivity.getClassName();
                Log.d(TAG,"SimStateChangeBroadCast onReceive "+className);

                if (className.equalsIgnoreCase("com.sprd.generalsecurity.network.DataFlowMainEntry")
                        || className.equalsIgnoreCase("com.sprd.generalsecurity.network.DataFlowSetting")
                        || className.equalsIgnoreCase("com.sprd.generalsecurity.GeneralSecurityManagement"))
                Toast.makeText(DataFlowMainEntry.this,R.string.sim_count_change,Toast.LENGTH_SHORT).show();

                finish();
            }
        }
    }
    /** @} */

}
