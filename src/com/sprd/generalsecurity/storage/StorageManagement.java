package com.sprd.generalsecurity.storage;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.Message;
import android.view.View;

import java.util.List;

import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.StorageUtils;

import java.io.File;

public class StorageManagement extends Activity implements
        View.OnClickListener, LoaderManager.LoaderCallbacks<List<FileDetailModel>>, CacheAndFileLoader.Callback {

    private static final String TAG = "StorageManagement";
    private static final String INTERNAL_STORAGE = "internal_storage";
    private static final String EXTERNAL_STORAGE = "external_storage";

    private static final int SET_BUTTON_CLEAN = 1;
    private static final int UPDATE_CACHE_SIZE = 3;
    private static final int UPDATE_RUBBISH_SIZE = 4;
    private static final int UPDATE_TMP_SIZE = 5;
    private static final int UPDATE_APK_SIZE = 6;
    private static final int UPDATE_LARGE_FILE_SIZE = 7;
    private static final int UPDATE_SIZE = 0;
    private static final int UPDATE_PATH_UI = 9;

    public static final int CACHE_ITEM = 0;
    public static final int RUBBISH_ITEM = 1;
    public static final int APK_ITEM = 2;
    public static final int TMP_ITEM = 3;
    public static final int LARGE_FILE_ITEM = 4;

    public static final String RUBBISH_FILE1_EXT = ".log";
    public static final String RUBBISH_FILE2_EXT = ".bak";
    public static final String TMP_FILE_PREFIX = "~";
    public static final String TMP_FILE_EXT = ".tmp";
    public static final String APK_FILE_EXT = ".apk";


    private StorageManager mStorageManager;
    private Context mContext;
    private View mInternal;
    private FrameLayout mLayout;
    private LayoutInflater mInflater;
    private FileScanAdapter mInAdapter;

    private Button mInButton;
    private DataGroup mData = DataGroup.getInstance();

    private static final long LARGE_FILE_FILTER_SIZE = 1024 * 1024 * 100; //100M

    private static final String SDCARD_PREFIX = "sdcard";

    private static final int RUBBISH_ITEM_INDEX = 1;
    private static final int APK_ITEM_INDEX = 2;
    private static final int TMP_ITEM_INDEX = 3;
    private static final int LARGE_ITEM_INDEX = 4;

    private static final int LOADER_ID = 1;

    private boolean isScanning = false;
    private boolean cancelLoad = false;

    private enum Tab {
        INTERNAL(INTERNAL_STORAGE, R.string.internal_storage, R.id.internal,
                R.id.internal_tv, R.id.score_label, R.id.internal_background, R.id.internal_list,
                R.id.internal_bt, true), EXTERNAL(EXTERNAL_STORAGE,
                R.string.external_storage, R.id.external, R.id.external_tv,R.id.score_label,
                R.id.external_background, R.id.external_list, R.id.external_bt,
                false);

        private final String mTag;
        private final int mLabel;
        private final int mScoreLabel;
        private final int mView;
        private final int mText;
        private final int mBackground;
        private final int mList;
        private final int mButton;
        private final boolean mWithSwitch;


        Tab(String tag, int label, int view, int text,int score_label, int background,
            int list, int button, boolean withSwitch) {
            mTag = tag;
            mLabel = label;
            mScoreLabel = score_label;
            mView = view;
            mText = text;
            mBackground = background;
            mList = list;
            mButton = button;
            mWithSwitch = withSwitch;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");

        mContext = this;
        mStorageManager = this.getSystemService(StorageManager.class);

        mInflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mLayout = new FrameLayout(mContext);

        setContentView(mLayout);
        refreshUi();

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        startScan();
    }

    private void startScan() {
        resetData();
        mInButton.setText(mContext.getResources().getString(
                R.string.stop_scan_button));
        // UNISOC: Bug776770 when scan from FileExplorer, not show progress image
        isScanning = true;
        cancelLoad = false;
        mInAdapter.notifyDataSetChanged();
        getLoaderManager().restartLoader(LOADER_ID, Bundle.EMPTY, this);
    }

    private boolean hasSDcard() {
        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        for (VolumeInfo vol : volumes) {
            if (vol.getType() == VolumeInfo.TYPE_PUBLIC) {
                if (vol.linkName != null && vol.linkName.startsWith(SDCARD_PREFIX) &&
                        vol.state == VolumeInfo.STATE_MOUNTED) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG,"onPause");
        getLoaderManager().getLoader(LOADER_ID).stopLoading();
    }

    @Override
    public void onClick(View view) {
        final String text = ((Button) view).getText().toString().trim();
        Log.i(TAG, "onClick text: " + text);
        if (text.equals(mContext.getText(R.string.clear_memory))){
            this.finish();
        } else if(text.equals(mContext
                .getText(R.string.stop_scan_button))){
            getLoaderManager().getLoader(LOADER_ID).stopLoading();
            mHandler.sendEmptyMessageDelayed(SET_BUTTON_CLEAN,100);
        }
    }

    long mInCacheSize;
    long mInRubbishSize;
    long mInApkSize;
    long mInTmpSize;
    long mExCacheSize;
    long mExRubbishSize;
    long mExApkSize;
    long mExTmpSize;
    long mLargeFileSize;
    long mUniqueLargeFileSize;
    long mExLargeFileSize;
    long mExUniqueLargeFileSize; //large file size that not contained in APK, tmp category.

    @Override
    public void refreshCacheUi(FileDetailModel fileDetailModel) {
        //UNISOC: Bug1121147 not stop getting apps cache when user stop scan.
        if (cancelLoad) {
            return;
        }
        mData.mRubbish_cach1_ext.add(fileDetailModel);
        mInCacheSize += fileDetailModel.getFileSize();
        mHandler.sendEmptyMessage(UPDATE_CACHE_SIZE);
    }

    @Override
    public void scanAllFiles() {
        try {
            File file_inter = EnvironmentEx.getInternalStoragePath();
            Log.i(TAG, "Scan files Start--------------------------------");
            Log.i(TAG, "file:" + file_inter.getName() + " ;path:"
                    + file_inter.getAbsolutePath());
            scanFiles(file_inter.listFiles());
            if(hasSDcard()){
                File file_ext = EnvironmentEx.getExternalStoragePath();
                scanFiles(file_ext.listFiles());
            }

            Log.i(TAG, "Scan files End-------------------------------");
        } catch (Exception e) {
            Log.i(TAG, "scanAllFiles Exception: "+e);
        }
    }



    @Override
    public void startLoad() {
        cancelLoad = false;
        resetData();
        if (mHandler.hasMessages(SET_BUTTON_CLEAN)) {
            mHandler.removeMessages(SET_BUTTON_CLEAN);
        }
    }
    @Override
    public void cancelAllLoad() {
        cancelLoad = true;
        Log.d(TAG,"cancelAllLoad cancelLoad: "+cancelLoad);
        if (mHandler.hasMessages(SET_BUTTON_CLEAN)) {
            mHandler.removeMessages(SET_BUTTON_CLEAN);
        }
    }

    @Override
    public boolean isCancelAllLoad() {
        return cancelLoad;
    }

    @Override
    public Loader<List<FileDetailModel>> onCreateLoader(int i, Bundle bundle) {
        Log.d(TAG,"onCreateLoader");
        return new CacheAndFileLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<List<FileDetailModel>> loader, List<FileDetailModel> fileDetailModels) {
        Log.d(TAG,"onLoadFinished");
    }
    @Override
    public void onComplete() {
        Log.d(TAG,"onComplete");
        if (!cancelLoad) {
            mHandler.sendEmptyMessage(SET_BUTTON_CLEAN);
        }
    }
    @Override
    public void onLoaderReset(Loader<List<FileDetailModel>> loader) {

    }

    private void scanFiles(File[] files) {
        if (files != null) {
            for (File file : files) {
                if (cancelLoad) {
                    break;
                }
                Message updatePathUi  = mHandler.obtainMessage(UPDATE_PATH_UI, file);
                mHandler.sendMessage(updatePathUi);
                if (file.isDirectory()) {
                    if (file.getName().equals("cache") || file.getName().equals("code_cache")) {
                        long size = scanDirSize(file);
                        if (size > 0) {
                            mData.mInCacheMap.put((file.getAbsolutePath()), size);
                            mInCacheSize += size;
                            mData.mRubbish_cach2_ext.add(new FileDetailModel(file.getAbsolutePath(), size));
                            mHandler.sendEmptyMessage(UPDATE_CACHE_SIZE);
                        }
                        continue;
                    }
                    scanFiles(file.listFiles());
                } else {
                    String name = file.getName();
                    if (name.endsWith(RUBBISH_FILE1_EXT)) {
                        mInRubbishSize += file.length();
                        mData.mRubbishCategorySize = mInRubbishSize;
                        mData.mInRubbishMap.put(file.getAbsolutePath(), file.length());
                        mData.mRubbish_log_ext.add(new FileDetailModel(file.getAbsolutePath(), file.length()));
                        mHandler.sendEmptyMessage(UPDATE_RUBBISH_SIZE);
                    } else if(name.endsWith(RUBBISH_FILE2_EXT)) {
                        mInRubbishSize += file.length();
                        mData.mRubbishCategorySize = mInRubbishSize;
                        mData.mInRubbishMap.put(file.getAbsolutePath(), file.length());
                        mData.mRubbish_bak_ext.add(new FileDetailModel(file.getAbsolutePath(), file.length()));
                        mHandler.sendEmptyMessage(UPDATE_RUBBISH_SIZE);
                    } else if (name.endsWith(APK_FILE_EXT)) {
                        if (file.getAbsolutePath()
                                .substring(0, 5)
                                .equals(Environment.getDataDirectory().getAbsolutePath())) {
                            continue;
                        }
                        mInApkSize += file.length();
                        mData.mAPKCategorySize = mInApkSize;
                        mData.mInApkMap.put(file.getAbsolutePath(), file.length());
                        mData.mRubbish_apk_ext.add(new FileDetailModel(file.getAbsolutePath(), file.length()));
                        mHandler.sendEmptyMessage(UPDATE_APK_SIZE);
                    } else if (name.endsWith(TMP_FILE_EXT) || name.startsWith(TMP_FILE_PREFIX)) {
                        mInTmpSize += file.length();
                        mData.mTempCategorySize = mInTmpSize;
                        mData.mInTmpMap.put(file.getAbsolutePath(), file.length());
                        mData.mRubbish_tmp_ext.add(new FileDetailModel(file.getAbsolutePath(), file.length()));
                        mHandler.sendEmptyMessage(UPDATE_TMP_SIZE);
                    } else {
                        largeFileCheck(file);
                    }
                }
            }
        }
    }

    private void largeFileCheck(File f) {
        /**UNISOC: Bug726136 large file should not include temp files @{ */
        if (f.length() > LARGE_FILE_FILTER_SIZE) {
            /**UNISOC: Bug1012245,1030354 large file repeat display @{*/
            if (mData.mLargeMap.containsKey(f.getAbsolutePath())) {
                return;
            }
            /**@}*/
            mLargeFileSize += f.length();
            mData.mLargeMap.put(f.getAbsolutePath(), f.length());
            mData.mRubbish_large_ext.add(new FileDetailModel(f.getAbsolutePath(), f.length()));
            DataGroup.getInstance().mLargeFileCategorySize = mLargeFileSize;
            mHandler.sendEmptyMessage(UPDATE_LARGE_FILE_SIZE);
            mData.mUniqueLargeFileSize += f.length();
            mHandler.sendEmptyMessage(UPDATE_SIZE);
        }
        /**@}*/
    }

    private long scanDirSize(File dir) {
        File[] fileList = dir.listFiles();
        int size = 0;
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    size += scanDirSize(file);
                } else {
                    size += file.length();
                }
            }
        }
        return size;
    }

    // Views for internal tab.
    private Button mInternalButton;
    private TextView mInternalBackground;
    private TextView mInternalSizeView;
    private TextView mInternalScore_LableView;
    private ListView mInternalListView;
    private View mInternalRubbishItem;
    private View mInternalApkItem;
    private View mInternalTmpItem;
    private View mInternalLargeItem;

    void initInternalViews() {
        mInternalButton =  (Button) mInternal.findViewById(Tab.INTERNAL.mButton);
        mInternalBackground = (TextView) mInternal.findViewById(Tab.INTERNAL.mBackground);
        mInternalSizeView = (TextView) mInternal.findViewById(Tab.INTERNAL.mText);
        mInternalScore_LableView= (TextView) mInternal.findViewById(Tab.INTERNAL.mScoreLabel);
        mInternalListView = (ListView) mInternal.findViewById(Tab.INTERNAL.mList);

        mInternalRubbishItem = mInternalListView.getChildAt(RUBBISH_ITEM_INDEX);
        mInternalApkItem = mInternalListView.getChildAt(APK_ITEM_INDEX);
        mInternalTmpItem = mInternalListView.getChildAt(TMP_ITEM_INDEX);
        mInternalLargeItem = mInternalListView.getChildAt(LARGE_ITEM_INDEX);
    }

    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
    long orgSize = 0;
    /**@}*/
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {

        public void handleMessage(Message msg) {

            if (mInternalRubbishItem == null) {
                initInternalViews();
            }
            File file = (File) msg.obj;
            switch (msg.what) {
                case UPDATE_PATH_UI:
                    if(file != null){
                        mInternalBackground
                                .setText(mContext.getResources().getString(R.string.doing_scan_memory_button)+file.getAbsolutePath());
                    }
                    break;
                case UPDATE_APK_SIZE:
                    mInApkSize = DataGroup.getInstance().mAPKCategorySize;
                    if (mInternalApkItem != null) {
                        final ProgressBar mProGressBar = (ProgressBar) mInternalApkItem
                                .findViewById(R.id.progress);
                        mProGressBar.setVisibility(View.GONE);
                        final ImageView mImg = (ImageView) mInternalApkItem
                                .findViewById(R.id.img);
                        mImg.setVisibility(View.VISIBLE);
                        mImg.setImageDrawable(mContext.getResources().getDrawable(R.drawable.finish_optimize));
                    }
                    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
                    updateSize();
                    /**@}*/
                    break;
                case UPDATE_TMP_SIZE:
                    if (mInternalTmpItem != null) {
                        final ProgressBar mProGressBar = (ProgressBar) mInternalTmpItem
                                .findViewById(R.id.progress);
                        mProGressBar.setVisibility(View.GONE);
                        final ImageView mImg = (ImageView) mInternalTmpItem
                                .findViewById(R.id.img);
                        mImg.setVisibility(View.VISIBLE);
                        mImg.setImageDrawable(mContext.getResources().getDrawable(R.drawable.finish_optimize));
                    }
                    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
                    updateSize();
                    /**@}*/
                    break;
                case UPDATE_RUBBISH_SIZE:
                    if (mInternalRubbishItem != null) {
                        final ProgressBar mProGressBar = (ProgressBar) mInternalRubbishItem
                                .findViewById(R.id.progress);
                        mProGressBar.setVisibility(View.GONE);
                        final ImageView mImg = (ImageView) mInternalRubbishItem
                                .findViewById(R.id.img);
                        mImg.setVisibility(View.VISIBLE);
                        mImg.setImageDrawable(mContext.getResources().getDrawable(R.drawable.finish_optimize));
                    }
                    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
                    updateSize();
                    /**@}*/
                    break;
                case UPDATE_CACHE_SIZE:
                    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
                    updateSize();
                    /**@}*/
                    break;
                case UPDATE_LARGE_FILE_SIZE:
                    if (mInternalLargeItem != null) {
                        final ProgressBar mProGressBar = (ProgressBar) mInternalLargeItem
                                .findViewById(R.id.progress);
                        mProGressBar.setVisibility(View.GONE);
                        final ImageView mImg = (ImageView) mInternalLargeItem
                                .findViewById(R.id.img);
                        mImg.setVisibility(View.VISIBLE);
                        mImg.setImageDrawable(mContext.getResources().getDrawable(R.drawable.finish_optimize));
                    }
                    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
                    updateSize();
                    break;
                case UPDATE_SIZE:
                    updateSize();
                    break;
                    /**@}*/
                case SET_BUTTON_CLEAN:
                    orgSize = mInCacheSize + mInRubbishSize + mInTmpSize
                            + mInApkSize + mData.mUniqueLargeFileSize;
                    Log.e(TAG, "cln button:" + orgSize);
                    for (int i = 0; i < mInternalListView.getChildCount(); i++) {
                        View item = mInternalListView.getChildAt(i);
                        final ProgressBar mProGressBar = (ProgressBar) item
                                .findViewById(R.id.progress);
                        mProGressBar.setVisibility(View.GONE);
                        final ImageView mImg = (ImageView) item
                                .findViewById(R.id.img);
                        mImg.setVisibility(View.VISIBLE);
                        mImg.setImageDrawable(mContext.getResources().getDrawable(R.drawable.finish_optimize));
                    }
                    if (orgSize <= 0) {
                        mInternalButton.setText(mContext.getResources().
                                getString(R.string.clear_memory));
                        // UNISOC: Bug728805 hide path info when finish clearing rubbish
                        mInternalBackground.setVisibility(View.INVISIBLE);
                    } else {
                        startUpdateActivity();
                    }
                    isScanning = false;
                    break;
            }
        }
    };
    /**UNISOC: Bug1038610 Cleanup acceleration interface StorageManagement refactoring optimization.@{*/
    private void updateSize() {
        if (cancelLoad) {
            return;
        }
        orgSize = mInCacheSize + mInRubbishSize + mInTmpSize
                + mInApkSize + mData.mUniqueLargeFileSize;
        // UNISOC: Bug813685 Formatter.formatFileSize has changed in 8.1
        String[] strSize_lable = StorageUtils.convertTotalSize(mContext, orgSize);
        if(strSize_lable!= null && strSize_lable.length >1){
            mInternalSizeView.setText(strSize_lable[0]);
            mInternalScore_LableView.setText(strSize_lable[1]);

        }
    }
    /**@}*/

    public boolean getScanStatus() {
        return isScanning;
    }

    private void startUpdateActivity(){
        Log.d(TAG,"startUpdateActivity cancelLoad: "+cancelLoad);
        Intent intent = new Intent(StorageManagement.this,
                StorageClearManagement.class);
        startActivity(intent);
        finish();
    }

    private void refreshUi() {
        Log.i(TAG, "refreshUi().........");
        mInAdapter = new FileScanAdapter(mContext);
        resetData();
        mLayout.removeAllViews();
        mInternal = mInflater.inflate(R.layout.manage_storage_2, null);
        mInButton = ((Button) mInternal.findViewById(R.id.internal_bt));
        mInButton.setOnClickListener(this);

        ListView internalList = (ListView) mInternal
                .findViewById(R.id.internal_list);
        internalList.setAdapter(mInAdapter);
        mLayout.addView(mInternal);
    }

    private void resetData() {
        mData.mInCacheMap.clear();
        mData.mInRubbishMap.clear();
        mData.mInApkMap.clear();
        mData.mInTmpMap.clear();
        mData.mLargeMap.clear();

        mData.mExCacheMap.clear();
        mData.mExRubbishMap.clear();
        mData.mExApkMap.clear();
        mData.mExTmpMap.clear();
        mData.mExLargeMap.clear();

        mData.mRubbish_bak_ext.clear();
        mData.mRubbish_log_ext.clear();
        mData.mRubbish_tmp_prefix.clear();
        mData.mRubbish_tmp_ext.clear();
        mData.mRubbish_apk_ext.clear();
        mData.mRubbish_cach1_ext.clear();
        mData.mRubbish_cach2_ext.clear();
        mData.mRubbish_large_ext.clear();
        mData.mRubbish_large_video_ext.clear();
        mData.mRubbish_large_audio_ext.clear();

        // UNISOC: Bug730263 large file is cleared, but not reset the value
        mData.mUniqueLargeFileSize = 0;
        mInCacheSize = 0;
        mInRubbishSize = 0;
        mInApkSize = 0;
        mInTmpSize = 0;
        mLargeFileSize = 0;
        mUniqueLargeFileSize = 0;

        mExCacheSize = 0;
        mExRubbishSize = 0;
        mExApkSize = 0;
        mExTmpSize = 0;
        mExLargeFileSize = 0;
        mExUniqueLargeFileSize = 0;
    }
}
