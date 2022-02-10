package com.sprd.generalsecurity.network;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.net.INetworkStatsService;
import android.net.INetworkStatsSession;
import android.net.NetworkStats;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.Contract;
import com.sprd.generalsecurity.utils.CustomEditTextPreference;
import com.sprd.generalsecurity.utils.DateCycleUtils;
import com.sprd.generalsecurity.utils.TeleUtils;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.GregorianCalendar;

import static android.net.NetworkTemplate.buildTemplateMobileAll;
import android.net.TrafficStats;

public class SimPrefsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "DataFlowSetting";
    private static final String KEY_MONTH_TOTAL = "key_edit_month_total";
    private static final String KEY_MONTH_USED = "key_edit_month_used";
    private static final String KEY_DAY_RESTRICT = "key_day_flow_restrict";
    private static final String KEY_USED_SETTING_TIME = "key_data_use_time";
    private static final String KEY_SEEKBAR = "seek_bar_restrict";
    private static final String KEY_MONTH_REMIND_SWITCH = "key_restrict_month_reminder";
    private static final String KEY_DAY_REMIND_SWITCH = "key_restrict_day_reminder";
    private static final String KEY_MONTH_LEFT_RESTRICT = "key_month_flow_left_restrict";
    private static final String SIZE_UNIT = " MB";

    private int mSimID;
    private long dataUserSet;

    /**
     * UNISOC: Bug730981 Unable to instantiate DataFlowSetting$PrefsFragment @{
     */
    public SimPrefsFragment() {
    }

    /**
     * UNISOC: Bug730981 @}
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager pm = getPreferenceManager();
        pm.setSharedPreferencesName("sim" + mSimID);
        addPreferencesFromResource(R.xml.data_settings_pref);
        SwitchPreference switchPreference = (SwitchPreference) findPreference(KEY_MONTH_REMIND_SWITCH);
        switchPreference.setOnPreferenceChangeListener(this);

        switchPreference = (SwitchPreference) findPreference(KEY_DAY_REMIND_SWITCH);
        switchPreference.setOnPreferenceChangeListener(this);
        CustomEditTextPreference editTextPreference = (CustomEditTextPreference) findPreference(KEY_MONTH_TOTAL);
        editTextPreference.setOnPreferenceChangeListener(this);
        setSummary(KEY_MONTH_TOTAL, null);

        editTextPreference = (CustomEditTextPreference) findPreference(KEY_MONTH_USED);
        editTextPreference.setOnPreferenceChangeListener(this);
        setSummary(KEY_MONTH_USED, null);
        editTextPreference = (CustomEditTextPreference) findPreference(KEY_DAY_RESTRICT);
        editTextPreference.setOnPreferenceChangeListener(this);
        setSummary(KEY_DAY_RESTRICT, null);

        editTextPreference = (CustomEditTextPreference) findPreference(KEY_MONTH_LEFT_RESTRICT);
        editTextPreference.setOnPreferenceChangeListener(this);
        setSummary(KEY_MONTH_LEFT_RESTRICT, null);
    }

    public void setSimID(int i) {
        mSimID = i;
    }

    @Override
    public void onResume() {
        super.onResume();
        queryLoader(QUERY_MONTH_TOTAL);
    }

    private float preValueMonthRestrict;
    private float preValueMonthDataUsed;

    void resetRemindTrigger1AndCheckReminder() {
        SharedPreferences sp = getPreferenceManager().getSharedPreferences();
        Log.e(TAG, "update:" + mSimID + ":" + sp.getString(KEY_MONTH_TOTAL, "0"));

        SharedPreferences.Editor editor = sp.edit();
        //remind trigger time update to 0, so will remind again for the new setting.
        editor.putLong(Contract.KEY_MONTH_REMIND_TIME_TRIGGER_WARN, 0);
        editor.putLong(Contract.KEY_MONTH_REMIND_TIME_TRIGGER_OVER, 0);
        editor.apply();
        Intent it = new Intent(this.getActivity(), DataFlowService.class);
        getActivity().startService(it);
    }

    void setSummary(String key, String v) {
        CustomEditTextPreference editTextPreference = (CustomEditTextPreference) findPreference(key);
        if (v == null) {
            PreferenceManager pm = getPreferenceManager();
            SharedPreferences sharedPref = pm.getSharedPreferences();
            v = sharedPref.getString(key, "0");
        }
        editTextPreference.setSummary(v + SIZE_UNIT);
    }

    private long mDataUsedSetTime;

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        /* UNISOC: Bug 670294 GeneralSecurity monkey crash @{ */
        if (this.getActivity() == null) {
            return true;
        }
        /* @} */
        if (preference.getKey().equalsIgnoreCase(KEY_MONTH_USED)) {
            resetRemindTrigger1AndCheckReminder();
            SharedPreferences.Editor editor = preference.getEditor();
            mDataUsedSetTime = System.currentTimeMillis();
            editor.putLong(KEY_USED_SETTING_TIME, mDataUsedSetTime);
            if (newValue != null && !((String) newValue).equalsIgnoreCase("")) {
                editor.putString(Contract.KEY_DATA_USED_ORIGINAL, (String) newValue);
                dataUserSet = (long) (Float.parseFloat((String) newValue) * Contract.M2BITS);
            } else {
                editor.putString(Contract.KEY_DATA_USED_ORIGINAL, "0");
                dataUserSet = 0;
            }
            editor.apply();

            queryLoader(QUERY_USER_NOW);
            return true;
        } else if (preference.getKey().equalsIgnoreCase(KEY_MONTH_TOTAL)) {
            resetRemindTrigger1AndCheckReminder();
            return true;
        } else if (preference.getKey().equalsIgnoreCase(KEY_MONTH_REMIND_SWITCH) ||
                preference.getKey().equalsIgnoreCase(KEY_DAY_REMIND_SWITCH)) {
            if ((boolean) newValue) {
                //swich enabled
                Intent it = new Intent(this.getActivity(), DataFlowService.class);
                getActivity().startService(it);
            }
        } else if (preference.getKey().equalsIgnoreCase(KEY_DAY_RESTRICT)) {
            SharedPreferences.Editor editor = preference.getEditor();
            editor.putLong(Contract.KEY_DAY_REMIND_TIME_TRIGGER, 0);
            editor.apply();
            Intent it = new Intent(this.getActivity(), DataFlowService.class);
            getActivity().startService(it);
        } else if (preference.getKey().equalsIgnoreCase(KEY_MONTH_LEFT_RESTRICT)) {
            SharedPreferences.Editor editor = preference.getEditor();
            editor.putLong(Contract.KEY_MONTH_REMIND_TIME_TRIGGER_WARN, 0);
            editor.putLong(Contract.KEY_MONTH_REMIND_TIME_TRIGGER_OVER, 0);
            editor.apply();
            Intent it = new Intent(this.getActivity(), DataFlowService.class);
            getActivity().startService(it);
        }
        return true;
    }

    /*
     * Steps to set 'Data used' field.
     * 1. in OnResume, query type QUERY_MONTH_TOTAL, this will query the network API for used
     *    data.
     *    when use not set 'data used' field, the delta is 0, so set the query result as
     *    'Data used'.
     * 2. Once the user change 'Data used' field, call queryLoader with parameter
     * QUERY_USER_NOW,
     *    this will set the delta. The delta is used to calculate the 'Data used' after user
     *    setting, as following,
     *       monthUsed = QueryedTotalBytes - delta.
     *    Delta is also used in DataFlowMainEntry.java
    **/
    private static final int QUERY_MONTH_TOTAL = 1;
    private static final int QUERY_USER_NOW = 2;

    private void queryLoader(int type) {
        mStatsService = INetworkStatsService.Stub.asInterface(
                ServiceManager.getService(Context.NETWORK_STATS_SERVICE));
        try {
            mStatsSession = mStatsService.openSession();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        /**UNISOC: Bug998075 After inserting a dual mobile card, SIM1 has used the package flow to display the negative value. @{*/
        String newSubID = TeleUtils.getActiveSubscriberId(getContext(), mSimID);
        PreferenceManager pm = getPreferenceManager();
        SharedPreferences sharedPref = pm.getSharedPreferences();
        String subID = sharedPref.getString(Contract.SIM_SUB_ID, "-1");

        if (!subID.equalsIgnoreCase(newSubID)) {
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(Contract.SIM_SUB_ID, newSubID);
            editor.putLong(Contract.KEY_DATA_USED_QUERY_DELTA, 0);
            editor.apply();
        }
        /**@}*/

        // UNISOC: Bug759555 context is null after recreate activity
        mTemplate = buildTemplateMobileAll(newSubID);

        long start = DateCycleUtils.getMonthCycleStart();
        /* UNISOC: Bug 670294 GeneralSecurity monkey crash @{ */
        if (getActivity() != null) {
            if (type == QUERY_USER_NOW) {
                getLoaderManager().restartLoader(QUERY_USER_NOW,
                        SummaryForAllUidLoader.buildArgs(mTemplate, start,
                                mDataUsedSetTime), mSummaryCallbacks);
            } else {
                // kick off loader for sim1 detailed stats
                getLoaderManager().restartLoader(QUERY_MONTH_TOTAL,
                        SummaryForAllUidLoader.buildArgs(mTemplate, start,
                                System.currentTimeMillis()), mSummaryCallbacks);
            }
        /* @} */
        }
    }

    private INetworkStatsSession mStatsSession;
    private INetworkStatsService mStatsService;
    private NetworkTemplate mTemplate;

    private final LoaderCallbacks<NetworkStats> mSummaryCallbacks = new LoaderCallbacks<NetworkStats>() {
        @Override
        public Loader<NetworkStats> onCreateLoader(int id, Bundle args) {
            // UNISOC: Bug759555 context is null after recreate activity
            return new SummaryForAllUidLoader(getContext(), mStatsSession, args);
        }

        @Override
        public void onLoadFinished(Loader<NetworkStats> loader, NetworkStats data) {
            long totalBytes = 0;
            NetworkStats.Entry entry = null;

            for (int i = 0; i < data.size(); i++) {
                entry = data.getValues(i, entry);
                totalBytes += (entry.rxBytes + entry.txBytes);
            }
            Log.d(TAG, "totalBytes:" + totalBytes+ "dataUserSet:" + dataUserSet);
            SharedPreferences sp = getPreferenceManager().getSharedPreferences();
            if (loader.getId() == QUERY_USER_NOW) {
                SharedPreferences.Editor editor = sp.edit();
                editor.putLong(Contract.KEY_DATA_USED_QUERY_DELTA, (totalBytes - dataUserSet));
                editor.apply();
            } else {
                long delta = sp.getLong(Contract.KEY_DATA_USED_QUERY_DELTA, 0);
                float monthUsed = ((float) (totalBytes - delta)) / Contract.M2BITS;
                /** UNISOC: add for 1006035 the monthUsed dataflow show negative @{ */
                if (monthUsed < 0) {
                   monthUsed = 0;
                }
                /** @} */
                String used = new DecimalFormat("#.##").format(monthUsed);
                setSummary(KEY_MONTH_USED, used);

                Log.d(TAG, "======= put:" + monthUsed + ":" + delta);
                totalBytes = 0;
                for (int i = 0; i < data.size(); i++) {
                    entry = data.getValues(i, entry);
                    totalBytes += (entry.rxBytes + entry.txBytes);
                }
                SharedPreferences.Editor editor = sp.edit();
                editor.putString(KEY_MONTH_USED, used);
                editor.apply();
            }
        }

        @Override
        public void onLoaderReset(Loader<NetworkStats> loader) {
        }
    };
}