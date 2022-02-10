package com.sprd.generalsecurity.optimize;

import android.app.ActivityManager;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.ArrayMap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.settingslib.applications.StorageStatsSource;
import com.sprd.generalsecurity.GeneralSecurityManagement;
import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.Contract;
import com.sprd.generalsecurity.utils.MemoryUtils;
import com.sprd.generalsecurity.utils.RunningState;
import com.sprd.generalsecurity.utils.StorageUtils;
import com.sprd.generalsecurity.utils.TeleUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class OptimizeResultFragment extends Fragment implements RunningState.OnRefreshUiListener  {
    private static final String TAG = "OptimizeResult";
    private static final String RUBBISH_FILE2_EXT = ".bak";
    private static final String TMP_FILE_PREFIX = "~";
    private static final String TMP_FILE_EXT = ".tmp";
    private static final String APK_FILE_EXT = ".apk";

    private static final int FINISH_RUBBISH_SCAN = 1;
    private static final int FINISH_RUNNING_APPS_SCAN = 2;
    private static final int FINISH_RUBBISH_OPTIMIZE = 3;
    private static final int FINISH_RUNNING_APPS_OPTIMIZE = 4;
    private static final int UPDATE_DATA_SET = 5;
    private static final int UPDATE_SCORE = 6;
    private static final int UPDATE_PATH = 7;
    private static final int FINISH_ALL_OPTIMIZE = 8;
    private static final int NOTIFY_MEDIA = 9;

    private static final int RUBBISH_OPTIMIZE_ITEM = 0;
    private static final int MEMORY_OPTIMIZE_ITEM = 1;
    private static final int DATA_SET_OPTIMIZE_ITEM = 2;

    private static final int CACHE_ITEM = 0;
    private static final int RUBBISH_ITEM = 1;
    private static final int APK_ITEM = 2;
    private static final int TMP_ITEM = 3;

    private static final int FLAG_DATA_SET = 0;
    private static final int DATA_SET_SCORE = 3;
    private static final long MB = 1024 * 1024;

    private View mRootView;
    private OptimizeResultAdapter mOptimizeAdapter;
    private ListView mOptimizeList;
    private long mInCacheSize;
    private long mInRubbishSize;
    private long mInApkSize;
    private long mInTmpSize;

    private ArrayMap<String, Long> mInCacheMap;
    private ArrayMap<String, Long> mInRubbishMap;
    private ArrayMap<String, Long> mInApkMap;
    private ArrayMap<String, Long> mInTmpMap;

    private float mBeforeRubbishScore = 0;
    private float mBeforeMemoryhScore = 0;
    /**
     * the rubbish size after scan.
     */
    private long mScanRubbishSize = 0;
    /**
     * the app cache size after scan.
     */
    private long mRunningAppsSize = 0;
    /**
     * to refresh main UI.
     */
    private Listener mListener;
    /**
     * the score display in main ui.
     */
    private int mScore;
    /**
     * to get running apps.
     */
    private RunningState mRunningState;
    /**
     * current running apps.
     */
    private ArrayList<RunningState.MergedItem> mBackgroundItems;
    /**
     * save running apps after scan.
     */
    private ArrayList<RunningState.MergedItem> mOldBackgroundItems;
    private int mPrimarySim = 0;
    private PackageManager mPackageManger;
    private ActivityManager mActivityManager;
    private StorageStatsSource mStatsManager;
    /**
     * the value of data set.
     */
    private float mSimDataTotal;
    /**
     * if data has set.
     */
    private Boolean mDataSetFlag = true;
    /**
     * true: scan and optimize, false: just scan.
     */
    private Boolean mNeedOptimize = false;
    /**
     * scan files rubbish and apps cache.
     */
    private RubbishScanTask mRubbishScanTask;
    /**
     * clear files rubbish, apps cache, kill running apps.
     */
    private OptimizeManagerTask mOptimizeManagerTask;

    /**
     * Callbacks for refresh main UI.
     *
     * @param score               current score to display.
     * @param mode                FINISH_SCAN or FINISH_OPTIMIZE.
     * @param isScanOrOptimizeEnd whether scan or optimize is finished.
     */
    public interface Listener {
        void onRefreshMainUi(int score, int mode, Boolean isScanOrOptimizeEnd);
    }

    /**
     * UNISOC: Bug815425 modify the memory leak issue.
     *
     * @{
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListener = (Listener) getActivity();
    }

    /**
     * @}
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mScore = Contract.MAX_SCORE;
        mRunningState = RunningState.getInstance(getContext());
        mPackageManger = getContext().getPackageManager();
        mStatsManager = new StorageStatsSource(getContext());
        mActivityManager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        initData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.default_list, null);
        mOptimizeList = (ListView) mRootView.findViewById(R.id.list);
        mOptimizeAdapter = new OptimizeResultAdapter(getContext());
        mOptimizeList.setAdapter(mOptimizeAdapter);
        if (getActivity() != null) {
            mNeedOptimize = ((GeneralSecurityManagement) getActivity()).mNeedOptimize;
        }
        Log.i(TAG, "mNeedOptimize = " + mNeedOptimize);
        checkDataFlowSet(!mNeedOptimize);
        scanRubbish();
        return mRootView;
    }

    private void initData() {
        mInCacheMap = new ArrayMap<String, Long>();
        mInRubbishMap = new ArrayMap<String, Long>();
        mInApkMap = new ArrayMap<String, Long>();
        mInTmpMap = new ArrayMap<String, Long>();
        mBackgroundItems = new ArrayList<RunningState.MergedItem>();
        mOldBackgroundItems = new ArrayList<RunningState.MergedItem>();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FLAG_DATA_SET) {
            refreshListView(DATA_SET_OPTIMIZE_ITEM, null, checkDataFlowSet(true));
        }
    }

    /**
     * Check if data plan has set to avoid overusing your data.
     *
     * @param update whether to refresh the score text .
     * @return {@code true} data plan set, {@code false} otherwise.
     */
    private Boolean checkDataFlowSet(Boolean update) {
        if (TeleUtils.getSimCount(getContext()) == 0) {
            return false;
        }
        mPrimarySim = TeleUtils.getPrimarySlot(getContext()) - 1;
        Log.i(TAG, "check mPrimarySim = " + mPrimarySim);
        SharedPreferences pref;
        if (mPrimarySim == Contract.SIM1_INDEX) {
            pref = getContext().getSharedPreferences(Contract.SIM1, Context.MODE_PRIVATE);
        } else if (mPrimarySim == Contract.SIM2_INDEX) {
            pref = getContext().getSharedPreferences(Contract.SIM2, Context.MODE_PRIVATE);
        } else {
            return false;
        }
        mSimDataTotal = Float.parseFloat(pref.getString(Contract.KEY_MONTH_TOTAL, "0"));
        if (mSimDataTotal == 0 && mDataSetFlag) {
            mScore -= DATA_SET_SCORE;
            mDataSetFlag = false;
        } else if (mSimDataTotal != 0 && !mDataSetFlag) {
            mDataSetFlag = true;
            mScore += DATA_SET_SCORE;
        }
        Log.i(TAG, "mSimDataTotal = " + mSimDataTotal + " mDataSetFlag = " + mDataSetFlag + " mScore = " + mScore);
        if (update && mListener != null) {
            mListener.onRefreshMainUi(mScore, UPDATE_DATA_SET, false);
        }
        return mSimDataTotal == 0 ? false : true;
    }

    private void scanRubbish() {
        Log.i(TAG, "scanRubbish mRubbishScanTask = " + mRubbishScanTask);
        if (mRubbishScanTask != null) {
            mRubbishScanTask.cancel(true);
        }
        mRubbishScanTask = new RubbishScanTask();
        mRubbishScanTask.execute();
    }

    private void scanRunningApps() {
        Log.i(TAG, "scan running apps start");
        mRunningState.resume(this);

        // We want to go away if the service being shown no longer exists,
        // so we need to ensure we have done the initial data retrieval before
        // showing our ui.
        mRunningState.waitForData();
    }

    public void onRefreshUi(int what) {
        mBackgroundItems.clear();
        mBackgroundItems = mRunningState.getCurrentBackgroundItems();
        mRunningState.pause();
        mHandler.sendEmptyMessage(FINISH_RUNNING_APPS_SCAN);
        // all scan is finished, if need optimize, start optimize
        if (mNeedOptimize) {
            startOptimize();
        }
    }

    public void startOptimize() {
        if (mOptimizeManagerTask != null) {
            mOptimizeManagerTask.cancel(true);
        }
        mOptimizeManagerTask = new OptimizeManagerTask();
        mOptimizeManagerTask.execute();
    }

    class RubbishScanTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // get rubbish files
            try {
                File internalFile = EnvironmentEx.getInternalStoragePath();
                Log.i(TAG, "scan files start");
                scanFiles(internalFile.listFiles());
                Log.i(TAG, "scan files end");
            } catch (Exception e) {
                Log.i(TAG, "start scan e = " + e);
            }
            // get apps cache
            getStorageResultForUser();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mHandler.sendEmptyMessage(FINISH_RUBBISH_SCAN);
            // start scan background items
            scanRunningApps();
        }
    }

    private void getStorageResultForUser() {
        Log.i(TAG, "scan apps cache start");
        int userId = UserHandle.myUserId();
        UserHandle myUser = UserHandle.of(userId);
        mInCacheMap.clear();
        mInCacheSize = 0;
        List<ApplicationInfo> applicationInfos = mPackageManger.getInstalledApplicationsAsUser(0, userId);
        for (int i = 0, size = applicationInfos.size(); i < size; i++) {
            if (mRubbishScanTask != null && mRubbishScanTask.isCancelled()) {
                return;
            }
            ApplicationInfo app = applicationInfos.get(i);
            if (TextUtils.isEmpty(app.packageName)) {
                continue;
            }
            /* UNISOC: Bug 674774 keep mc10086 from killing and cleaning @{ */
            if (MemoryUtils.isPersistentServiceProcess(app.packageName)) {
                continue;
            }
            /* @} */
            if ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                    (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || StorageUtils.isLauncherApp(mPackageManger, app.packageName)) {


                StorageStatsSource.AppStorageStats stats;
                try {
                    stats = mStatsManager.getStatsForPackage(app.volumeUuid, app.packageName, myUser);
                } catch (NameNotFoundException | IOException e) {
                    // This may happen if the package was removed during our calculation.
                    Log.w(TAG, "App unexpectedly not found", e);
                    continue;
                }
                mInCacheMap.put((Environment.getDataDirectory().getAbsoluteFile()
                        + File.separator + "data" + File.separator + app.packageName),
                        stats.getCacheBytes());
                mInCacheSize += stats.getCacheBytes();
                Log.i(TAG, "scan apps cache, " + "packageName = " + app.packageName +
                        " cacheSize = " + stats.getCacheBytes());
            }
        }
        Log.i(TAG, "scan apps cache end, " + "mInCacheSize = " + mInCacheSize);
    }

    private void scanFiles(File[] files) {
        if (files == null) return;
        for (File file : files) {
            if (mRubbishScanTask != null && mRubbishScanTask.isCancelled()) {
                return;
            }
            Message msg = mHandler.obtainMessage(UPDATE_PATH, file);
            mHandler.sendMessage(msg);
            if (file.isDirectory()) {
                scanFiles(file.listFiles());
            } else {
                String name = file.getName();
                if (name.endsWith(RUBBISH_FILE2_EXT)) {
                    mInRubbishSize += file.length();
                    mInRubbishMap.put(file.getAbsolutePath(), file.length());
                    mHandler.sendEmptyMessage(UPDATE_SCORE);
                } else if (name.endsWith(APK_FILE_EXT)) {
                    if (file.getAbsolutePath().substring(0, 5)
                            .equals(Environment.getDataDirectory()
                                    .getAbsolutePath())) {
                        continue;
                    }
                    mInApkSize += file.length();
                    mInApkMap.put(file.getAbsolutePath(), file.length());
                    mHandler.sendEmptyMessage(UPDATE_SCORE);
                } else if (name.endsWith(TMP_FILE_EXT) || name.startsWith(TMP_FILE_PREFIX)) {
                    mInTmpSize += file.length();
                    mInTmpMap.put(file.getAbsolutePath(), file.length());
                    mHandler.sendEmptyMessage(UPDATE_SCORE);
                }
            }
        }
    }

    class OptimizeManagerTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... params) {
            // clear rubbish files and apps cache
            deleteFiles(mInRubbishMap, RUBBISH_ITEM);
            deleteFiles(mInTmpMap, TMP_ITEM);
            deleteFiles(mInApkMap, APK_ITEM);
            mHandler.sendEmptyMessage(NOTIFY_MEDIA);
            for (int i = 0; i < mInCacheMap.size(); i++) {
                if (mOptimizeManagerTask != null && mOptimizeManagerTask.isCancelled()) {
                    return null;
                }
                File file = new File(mInCacheMap.keyAt(i));
                String packageName = file.getName();
                Log.i(TAG, "clear app cache " + i + ": " + packageName);
                mPackageManger.deleteApplicationCacheFiles(packageName, null);
            }
            mInCacheSize = 0;
            mInCacheMap.clear();
            // UNISOC: Bug1161814 Send the message with a delay of 100ms
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FINISH_RUBBISH_OPTIMIZE), 100);

            // kill background running apps
            for (int i = 0; i < mBackgroundItems.size(); i++) {
                if (mOptimizeManagerTask != null && mOptimizeManagerTask.isCancelled()) {
                    return null;
                }
                Object item = mBackgroundItems.get(i);
                if (item instanceof RunningState.MergedItem) {
                    ActivityManager.RunningAppProcessInfo appProcessInfo =
                            ((RunningState.MergedItem) item).mProcess.mRunningProcessInfo;
                    if (appProcessInfo.importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                        String[] pkgList = appProcessInfo.pkgList;
                        for (int j = 0; j < pkgList.length; ++j) {
                            Log.d(TAG, j + ": It will be killed, package name : " + pkgList[j]);
                            mActivityManager.killBackgroundProcesses(pkgList[j]);
                        }
                    }
                }
            }
            mBackgroundItems.clear();
            mHandler.sendEmptyMessage(FINISH_RUNNING_APPS_OPTIMIZE);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            mHandler.sendEmptyMessage(FINISH_ALL_OPTIMIZE);
        }
    }

    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (getActivity() == null || mListener == null) {
                return;
            }
            String summery;
            switch (msg.what) {
                case UPDATE_SCORE:
                    Log.i(TAG, "UPDATE_SCORE");
                    if (!mNeedOptimize) {
                        mListener.onRefreshMainUi(getScore(getCurrentRubbishSize(), RUBBISH_OPTIMIZE_ITEM, true), UPDATE_SCORE, false);
                    }
                    break;
                case UPDATE_PATH:
                    File file = (File) msg.obj;
                    if (file != null && isVisible()) {
                        summery = getContext().getResources().getString(R.string.doing_scan_memory_button) + file.getAbsolutePath();
                        refreshListView(RUBBISH_OPTIMIZE_ITEM, summery, false);
                    }
                    break;
                case NOTIFY_MEDIA:
                    Log.i(TAG, "NOTIFY_MEDIA");
                    StorageUtils.notifyMediaScanDir(getContext(), EnvironmentEx.getInternalStoragePath());
                    break;
                case FINISH_RUBBISH_SCAN:
                    Log.i(TAG, "FINISH_RUBBISH_SCAN");
                    mScanRubbishSize = getCurrentRubbishSize();
                    Log.i(TAG, "mScanRubbishSize = " + mScanRubbishSize);
                    if (!mNeedOptimize) {
                        mListener.onRefreshMainUi(getScore(mScanRubbishSize, RUBBISH_OPTIMIZE_ITEM, true), Contract.FINISH_SCAN, false);
                    }
                    break;
                case FINISH_RUNNING_APPS_SCAN:
                    Log.i(TAG, "FINISH_RUNNING_APPS_SCAN");
                    mOldBackgroundItems.clear();
                    mOldBackgroundItems.addAll(mBackgroundItems);
                    mRunningAppsSize = getRunningAppsSize();
                    Log.i(TAG, "mRunningAppsSize = " + mRunningAppsSize / MB + " MB");
                    if (!mNeedOptimize) {
                        mListener.onRefreshMainUi(getScore(mRunningAppsSize, MEMORY_OPTIMIZE_ITEM, true), Contract.FINISH_SCAN, true);
                    }
                    break;
                case FINISH_RUBBISH_OPTIMIZE:
                    Log.i(TAG, "FINISH_RUBBISH_OPTIMIZE");
                    if (mScanRubbishSize == 0) {
                        summery = getActivity().getString(R.string.no_rubbish);
                    } else {
                        summery = getActivity().getString(R.string.clean_rubbish, Formatter.formatFileSize(getContext(), mScanRubbishSize -= getCurrentRubbishSize()));
                    }
                    refreshListView(RUBBISH_OPTIMIZE_ITEM, summery, true);
                    mListener.onRefreshMainUi(getScore(getCurrentRubbishSize(), RUBBISH_OPTIMIZE_ITEM, false), Contract.FINISH_OPTIMIZE, false);
                    break;
                case FINISH_RUNNING_APPS_OPTIMIZE:
                    Log.i(TAG, "FINISH_RUNNING_APPS_OPTIMIZE");
                    if (mOldBackgroundItems.size() == 0) {
                        summery = getActivity().getString(R.string.nothing_can_removed);
                    } else {
                        Log.i(TAG, " mOldBackgroundItems.size() = " + mOldBackgroundItems.size() + " mBackgroundItems.size() = " + mBackgroundItems.size());
                        summery = getActivity().getString(R.string.clean_running_process,
                                mOldBackgroundItems.size() - mBackgroundItems.size(),
                                Formatter.formatFileSize(getContext(), mRunningAppsSize - getRunningAppsSize()));
                    }
                    refreshListView(MEMORY_OPTIMIZE_ITEM, summery, true);
                    mListener.onRefreshMainUi(getScore(getRunningAppsSize(), MEMORY_OPTIMIZE_ITEM, false), Contract.FINISH_OPTIMIZE, false);
                    break;
                case FINISH_ALL_OPTIMIZE:
                    refreshListView(DATA_SET_OPTIMIZE_ITEM, null, checkDataFlowSet(true));
                    mListener.onRefreshMainUi(mScore, Contract.FINISH_OPTIMIZE, true);
                    break;
            }
        }
    };

    private void refreshListView(int position, String summery, boolean isOptimizeDone) {
        if (mOptimizeList == null) {
            return;
        }
        View item = mOptimizeList.getChildAt(position);
        if (item == null) {
            return;
        }
        final ProgressBar bar = (ProgressBar) item.findViewById(R.id.progress);
        final ImageView done = (ImageView) item.findViewById(R.id.done);
        final ImageView set = (ImageView) item.findViewById(R.id.set);

        final TextView description = (TextView) item.findViewById(R.id.description);
        if (bar == null || done == null || description == null || set == null) {
            return;
        }
        if (position == RUBBISH_OPTIMIZE_ITEM || position == MEMORY_OPTIMIZE_ITEM) {
            done.setVisibility(isOptimizeDone ? View.VISIBLE : View.GONE);
            bar.setVisibility(isOptimizeDone ? View.GONE : View.VISIBLE);
            set.setVisibility(View.GONE);
            description.setText(summery);
        } else if (position == DATA_SET_OPTIMIZE_ITEM && TeleUtils.getSimCount(getContext()) != 0) {
            if (isOptimizeDone) {
                done.setVisibility(View.VISIBLE);
                bar.setVisibility(View.GONE);
                set.setVisibility(View.GONE);
                description.setText(R.string.data_set);
                // UNISOC: Bug863565 Optimize ->click on the data flow limit control, can not directly enter the sub-menu, but only click the arrow at the end> can enter.
                mOptimizeList.setOnItemClickListener(null);
            } else {
                done.setVisibility(View.GONE);
                bar.setVisibility(View.GONE);
                set.setVisibility(View.VISIBLE);
                set.setImageDrawable(getResources().getDrawable(R.drawable.arrow));
                description.setText(R.string.data_unset);
                // UNISOC: Bug863565 Optimize ->click on the data flow limit control, can not directly enter the sub-menu, but only click the arrow at the end> can enter.
                mOptimizeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        if (i == DATA_SET_OPTIMIZE_ITEM) {
                            Intent it = new Intent("com.sprd.generalsecurity.network.dataflowsetting");
                            it.putExtra(Contract.EXTRA_SIM_ID, TeleUtils.getPrimarySlot(getContext()));
                            startActivityForResult(it, FLAG_DATA_SET);
                        }
                    }
                });
            }
        }
    }

    private long getCurrentRubbishSize() {
        return (mInCacheSize + mInRubbishSize + mInApkSize + mInTmpSize);
    }

    private long getRunningAppsSize() {
        if (mBackgroundItems == null || mBackgroundItems.size() == 0) {
            return 0;
        }
        long size = 0;
        for (int i = 0; i < mBackgroundItems.size(); i++) {
            Object item = mBackgroundItems.get(i);
            if (item instanceof RunningState.MergedItem) {
                size += ((RunningState.MergedItem) item).mSize;
            }
        }
        return size;
    }

    private int getScore(long allSize, int position, Boolean isScan) {
        Log.i(TAG, "allSize = " + allSize + " position = " + position + " isScan = " + isScan);
        if (allSize < 0) {
            return mScore;
        }
        long tempScore;
        long size = allSize / MB;
        switch (position) {
            case RUBBISH_OPTIMIZE_ITEM:
                tempScore = (size % 200 == 0) ? (size / 200) : (size / 200 + 1);
                if (mBeforeRubbishScore != tempScore) {
                    if (isScan) {
                        mScore -= tempScore - mBeforeRubbishScore;
                    } else {
                        mScore += mBeforeRubbishScore - tempScore;
                    }
                    mBeforeRubbishScore = tempScore;
                }
                break;
            case MEMORY_OPTIMIZE_ITEM:
                tempScore = (size % 100 == 0) ? (size / 100) : (size / 100 + 1);
                if (mBeforeMemoryhScore != tempScore) {
                    if (isScan) {
                        mScore -= tempScore - mBeforeMemoryhScore;
                    } else {
                        mScore += mBeforeMemoryhScore - tempScore;
                    }
                    mBeforeMemoryhScore = tempScore;
                }
                break;
            default:
                break;
        }

        if (mScore > Contract.MAX_SCORE) {
            mScore = Contract.MAX_SCORE;
        } else if (mScore < Contract.MIN_SCORE) {
            mScore = Contract.MIN_SCORE;
        }
        return mScore;
    }

    private void deleteFiles(ArrayMap<String, Long> map, int type) {
        for (int i = 0; i < map.size(); i++) {
            if (mOptimizeManagerTask != null && mOptimizeManagerTask.isCancelled()) {
                return;
            }
            File file = new File(map.keyAt(i));
            Message msg = mHandler.obtainMessage(UPDATE_PATH, file);
            msg.obj = file;
            mHandler.sendMessage(msg);
            long size = map.valueAt(i);
            if (!file.delete()) {
                Log.i(TAG, "Failed to delete internal file " + file.getName());
                continue;
            }
            switch (type) {
                case RUBBISH_ITEM:
                    mInRubbishSize -= size;
                    Log.i(TAG, "mInRubbishSize:" + mInRubbishSize + " fileSize:" + size);
                    break;
                case APK_ITEM:
                    mInApkSize -= size;
                    Log.i(TAG, "mInApkSize:" + mInApkSize + " fileSize:" + size);
                    break;
                case TMP_ITEM:
                    mInTmpSize -= size;
                    Log.i(TAG, "mInTmpSize:" + mInTmpSize + " fileSize:" + size);
                    break;
                default:
                    break;
            }
        }
        map.clear();
    }

    private boolean dirDelete(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!dirDelete(f)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRunningState != null) {
            mRunningState.removeAllMessage();
        }
        Log.d(TAG, "mOptimizeManagerTask = " + mOptimizeManagerTask +
                " mRubbishScanTask = " + mRubbishScanTask);
        if (mRubbishScanTask != null) {
            mRubbishScanTask.cancel(true);
        }
        if (mOptimizeManagerTask != null) {
            mOptimizeManagerTask.cancel(true);
        }
        Log.d(TAG, "onDestroy");
    }
}


