package com.sprd.generalsecurity.network;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import com.sprd.generalsecurity.utils.String2NumberUtil;
import com.sprd.generalsecurity.utils.TeleUtils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Locale;

import static android.net.NetworkTemplate.buildTemplateMobileAll;
import android.net.TrafficStats;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;

public class DataFlowSetting extends Activity implements ActionBar.TabListener,
        ViewPager.OnPageChangeListener {
    private static final String TAG = "DataFlowSetting";
    private INetworkStatsSession mStatsSession;
    private INetworkStatsService mStatsService;
    private NetworkTemplate mTemplate;
    NetworkStats mStats;
    private GregorianCalendar mCalendar;
    int mSimId;
    private ViewPager mTabPager;//viewpager
    private String[] mTabTitles = {"SIM1", "SIM2"};
    private TabPagerAdapter mTabPagerAdapter;
    private SimPrefsFragment simFragment1;
    private SimPrefsFragment simFragment2;
    private ArrayList<SimPrefsFragment> fragmentList = new ArrayList<SimPrefsFragment>();

    /** UNISOC: add for #1003391 @{ */
    int simCount;
    private SimStateChangeBroadCast simStateChangeBroadCast;
    /** @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
        setTitle(getResources().getString(R.string.data_restrict_sim));
        setContentView(R.layout.data_setting);
        final FragmentManager fragmentManager = getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        /** UNISOC: add for #1003391 @{ */
        simCount = TeleUtils.getSimCount(this);
        /** @} */
        if (simCount == 1 || simCount == 0) {
            mSimId = TeleUtils.getPrimarySlot(this);
            SimPrefsFragment p = new SimPrefsFragment();
            p.setSimID(mSimId);
            getFragmentManager().beginTransaction().replace(R.id.setting, p).commit();
        } else {
            mTabPager = getView(R.id.tab_pager);
            mTabPagerAdapter = new TabPagerAdapter();
            mTabPager.setAdapter(mTabPagerAdapter);
            //UNISOC: Bug704824 add tab for dual sim card mode
            mTabPager.setOnPageChangeListener(this);
            setUpActionBar();
            setUpTabs();
            simFragment1 = new SimPrefsFragment();
            simFragment1.setSimID(1);
            simFragment2 = new SimPrefsFragment();
            simFragment2.setSimID(2);
            fragmentList.clear();
            fragmentList.add(simFragment1);
            fragmentList.add(simFragment2);
            transaction.add(R.id.tab_pager, simFragment1, "SIM1");
            transaction.add(R.id.tab_pager, simFragment2, "SIM2");
            transaction.commitAllowingStateLoss();
            fragmentManager.executePendingTransactions();
        }

        /** UNISOC: add for #1003391 @{ */
        if (simStateChangeBroadCast == null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction("android.intent.action.SIM_STATE_CHANGED");
            simStateChangeBroadCast = new SimStateChangeBroadCast();
            this.registerReceiver(simStateChangeBroadCast, filter);
        }
        /** @} */
    }

    private void setUpActionBar() {
        final ActionBar actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
    }

    private void setUpTabs() {
        final ActionBar actionBar = getActionBar();
        for (int i = 0; i < mTabPagerAdapter.getCount(); ++i) {
            actionBar.addTab(actionBar.newTab()
                    .setText("SIM"+(i+1))
                    .setTabListener(this));
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        mTabPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

    /**
     * UNISOC: Bug704824 add tab for dual sim card mode  @{
     */
    @Override
    public void onPageSelected(int i) {
        final ActionBar actionBar = getActionBar();
        actionBar.getTabAt(i).select();
    }

    @Override
    public void onPageScrollStateChanged(int i) {
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {
    }

    /**
     * @}
     */

    public <T extends View> T getView(int id) {
        T result = (T) findViewById(id);
        if (result == null) {
            throw new IllegalArgumentException("view 0x" + Integer.toHexString(id) + " doesn't exist");
        }
        return result;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
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
    /** UNISOC: add for #1003391 @{ */
    private class SimStateChangeBroadCast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getStringExtra(IccCardConstants.INTENT_KEY_ICC_STATE) == null) {
                return;
            }

            if (simCount != TeleUtils.getSimCount(DataFlowSetting.this)) {
                finish();
            }
        }
    }
    /** @} */
    /**
     * @}
     */

    private class TabPagerAdapter extends PagerAdapter {
        private final FragmentManager mFragmentManager;
        private FragmentTransaction mCurTransaction = null;

        public TabPagerAdapter() {
            mFragmentManager = getFragmentManager();
        }

        @Override
        public int getCount() {
            return mTabTitles.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return ((Fragment) object).getView() == view;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            Fragment f = fragmentList.get(position);
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.show(f);
            return f;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mCurTransaction == null) {
                mCurTransaction = mFragmentManager.beginTransaction();
            }
            mCurTransaction.hide((Fragment) object);
        }
    }
}
