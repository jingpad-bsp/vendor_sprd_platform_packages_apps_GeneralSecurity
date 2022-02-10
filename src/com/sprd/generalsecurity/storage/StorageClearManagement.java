package com.sprd.generalsecurity.storage;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.EnvironmentEx;
import android.os.Handler;
import android.os.Message;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.os.storage.VolumeInfo;
import android.provider.DocumentsContract;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.StorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StorageClearManagement extends Activity implements View.OnClickListener,
        OnItemClickListener {

    private static final String TAG = "StorageClearManagement";
    public static final int CACHE_ITEM = 0;
    public static final int RUBBISH_ITEM = 1;
    public static final int APK_ITEM = 2;
    public static final int TMP_ITEM = 3;
    public static final int LARGE_FILE_ITEM = 4;

    public static final int RUBBISH_LOG_EXT = 0;
    public static final int RUBBISH_BAK_EXT = 1;
    public static final int TMP_FILE_PREFIX = 2;
    public static final int TMP_FILE_EXT = 3;
    public static final int APK_FILE_EXT = 4;
    public static final int APK_CATCHE1_EXT = 5;
    public static final int APK_CATCHE2_EXT = 6;
    public static final int FILE_LARGE_EXT = 7;
    public static final int FILE_LARGE_AUDIO_EXT = 8;
    public static final int FILE_LARGE_VIDEO_EXT = 9;

    private static final int UPDATE_SIZE = 10;
    private static final int DONE_CLEAN = 11;
    private static final int RESTART_SCAN = 12;
    // UNISOC: Bug606356 if is run by FileExplorer, then auto scan rubbish
    private static final int RUBBISH_IS_CLEARED = 1;

    private static final String SDCARD_PREFIX = "sdcard";

    private ListView mStorageListView;
    private Context mContext;
    private FileAdapter mStorageAdatper;
    private Button clearBtn;
    private HashMap<Integer, Boolean> mCheckedMap;
    private HashMap<String, Boolean> mLargeFileCheckedMap;
    private long mSizeTotal;
    private DataGroup mData = DataGroup.getInstance();
    private ProgressDialog mWorkingProgress;
    private static final int DIALOG_STAND_TIME = 200;
    private TextView totalRubbishSize;
    private TextView scoreLable;
    private PackageManager mPm;
    private StorageManager mStorageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.manage_storage_clear);
        mPm = mContext.getPackageManager();
        mStorageManager = getSystemService(StorageManager.class);
//        mStorageManager.registerListener(mStorageListener);
        mStorageListView = (ListView) findViewById(R.id.internal_list);
        totalRubbishSize = (TextView) findViewById(R.id.score_bt);
        scoreLable = (TextView) findViewById(R.id.score_label);
        clearBtn = (Button) findViewById(R.id.clear_bt);
        clearBtn.setOnClickListener(this);
        mStorageAdatper = new FileAdapter(mContext);
        mStorageListView.setAdapter(mStorageAdatper);
        mStorageListView.setOnItemClickListener(this);
        mCheckedMap = new HashMap<Integer, Boolean>();
        mLargeFileCheckedMap = new HashMap<String, Boolean>();
        mSizeTotal = 0;
        initSizeAndCheckBox();
        //UNISOC: Bug863887  Into the cleanup acceleration, after scanning the junk file is completed,
        // the "top junk file" is displayed on the top side, which are duplicated.
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
    }
    /**
     * UNISOC: Bug863887  Into the cleanup acceleration, after scanning the junk file is completed,
     * the "top junk file" is displayed on the top side, which are duplicated.
     * @{
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home)
        {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /**
     * @}
     */
    public void initSizeAndCheckBox() {
        mCheckedMap.clear();
        mLargeFileCheckedMap.clear();
        for (int i = 0; i < mFileDetailTypes.length; i++) {
            mSizeTotal += mData.getDetailTotalSize(mFileDetailTypes[i]);
            mCheckedMap.put(mFileDetailTypes[i], true);
        }
        /**
         * UNISOC: Bug771467 display large file detail list in the extension list
         * @{
         */
        ArrayList<FileDetailModel> rubbishList = DataGroup.getInstance().mRubbish_large_ext;
        Collections.sort(rubbishList);
        String largeFilePath;
        for (int i = 0; i < rubbishList.size(); i++) {
            largeFilePath = rubbishList.get(i).getFilePath();
            if (largeFilePath != null) {
                mLargeFileCheckedMap.put(largeFilePath, true);
            }
        }
        /**
         * @}
         */
        if (mSizeTotal <= 0) {
            updateUihandler.sendEmptyMessage(RESTART_SCAN);
        } else {
            updateUihandler.sendEmptyMessage(UPDATE_SIZE);
        }
    }

    /**
     * UNISOC: Bug776690 total size in storage clear fragment show error
     * @{
     */
    @Override
    protected void onResume() {
        super.onResume();
        int size = 0;
        for (int i = 0; i < mFileDetailTypes.length; i++) {
            size += mData.getDetailTotalSize(mFileDetailTypes[i]);
        }
        if (size == 0) {
            finish();
        } else {
            updateTotalSize();
            mStorageAdatper.notifyDataSetChanged();
        }
    }

    void updateTotalSize() {
        /* UNISOC: Bug1015150 not update total size when cleaning up garbage @{ */
        if (mWorkingProgress != null) {
            return;
        }
        /* @} */
        long checkedSize = 0;
        for (int i = 0; i < mFileDetailTypes.length; i++) {
            if (mCheckedMap.containsKey(mFileDetailTypes[i])
                    && mCheckedMap.get(mFileDetailTypes[i])) {
                if (mFileDetailTypes[i] == FILE_LARGE_EXT) {
                    checkedSize += getCheckedLargeFileSize();
                } else {
                    checkedSize += mData.getDetailTotalSize(mFileDetailTypes[i]);
                }
            }
        }
        mSizeTotal = checkedSize;
        updateUihandler.sendEmptyMessage(UPDATE_SIZE);
    }

    long getCheckedLargeFileSize() {
        long size = 0;
        String largeFilePath;
        for (int i = 0; i < mData.mRubbish_large_ext.size(); i++) {
            largeFilePath = mData.mRubbish_large_ext.get(i).getFilePath();
            // UNISOC: Bug858614 Boolean is null, occur NullPointerException
            if (largeFilePath != null && mLargeFileCheckedMap.get(largeFilePath) != null
                    && mLargeFileCheckedMap.get(largeFilePath)) {
                size += mData.mRubbish_large_ext.get(i).getFileSize();
            }
        }
        return size;
    }
    /**
     * @}
     */

    private final int mNameIds[] = {R.string.cache_file, R.string.rubbish_file, R.string.invalid_file,
            R.string.temp_file, R.string.large_file};

    private final int mFileTypes[] = {CACHE_ITEM, RUBBISH_ITEM, APK_ITEM, TMP_ITEM, LARGE_FILE_ITEM};

    private final int mFileDetailTypes[] = {RUBBISH_LOG_EXT, RUBBISH_BAK_EXT, TMP_FILE_EXT,
            APK_FILE_EXT, APK_CATCHE1_EXT, APK_CATCHE2_EXT, FILE_LARGE_EXT, FILE_LARGE_AUDIO_EXT, FILE_LARGE_VIDEO_EXT};

    private class FileAdapter extends BaseAdapter {

        class ViewHolder {
            TextView name;
            TextView size;
            ImageView img;
            LinearLayout l;
        }

        private LayoutInflater mInflater;
        private ImageView fileTypeIcon = null;
        private TextView fileTypeName = null;
        private TextView fileSize = null;
        private CheckBox fileChecked = null;
        private View v = null;

        public FileAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return mNameIds != null ? mNameIds.length : 0;
        }

        @Override
        public Object getItem(int position) {
            return mNameIds[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            /** UNISOC: Bug1040556 Garbage Scan Cleanup Interface StorageClearManagement Memory Optimization @{*/
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
            /**@}*/
                convertView = mInflater.inflate(R.layout.storage_type_item2,
                        null);
                holder.name = (TextView) convertView.findViewById(R.id.name);
                holder.size = (TextView) convertView.findViewById(R.id.size);
                holder.img = (ImageView) convertView.findViewById(R.id.img);
                holder.l = (LinearLayout) convertView.findViewById(R.id.detail_file_list);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            // UNISOC: Bug856347 size is zero, but still display expand icon
            if (mData.getCategorySizeByType(mFileTypes[position]) > 0) {
                holder.img.setVisibility(View.VISIBLE);
            } else {
                holder.img.setVisibility(View.GONE);
                // UNISOC: Bug760412 layout of storage clear display error
                holder.l.setVisibility(View.GONE);
            }
            holder.l.removeAllViews();
            HashMap<Integer, ArrayList<FileDetailModel>> detailMap = mData.getDetailAssortmentType(mFileTypes[position]);

            for (Iterator<Map.Entry<Integer, ArrayList<FileDetailModel>>> it = detailMap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, ArrayList<FileDetailModel>> entry = it.next();
                int key = entry.getKey();
                switch (key) {
                    case RUBBISH_BAK_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_bak_ext);
                        break;
                    case RUBBISH_LOG_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_log_ext);
                        break;
                    case TMP_FILE_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_tmp_ext);
                        break;
                    case APK_FILE_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_apk_ext);
                        break;
                    case APK_CATCHE1_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_cache1_ext);
                        break;
                    case APK_CATCHE2_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_cache2_ext);
                        break;
                    case FILE_LARGE_EXT:
                        /**
                         * UNISOC: Bug771467 display large file detail list in the extension list
                         * @{
                         */
                        ArrayList<FileDetailModel> list = detailMap.get(key);
                        for (int i = 0; i < list.size(); i++) {
                            v = View.inflate(StorageClearManagement.this, R.layout.storage_type_detail_item, null);
                            long size = list.get(i).getFileSize();
                            if (size == 0) {
                                v.setVisibility(View.GONE);
                            }
                            final String largeFilePath = list.get(i).getFilePath();
                            if (largeFilePath == null) {
                                continue;
                            }
                            fileTypeIcon = (ImageView)v.findViewById(R.id.file_type_icon);
                            fileTypeName = (TextView) v.findViewById(R.id.file_type_drawable_name);
                            fileSize = (TextView) v.findViewById(R.id.file_type_size);
                            fileTypeIcon.setImageResource(R.drawable.large_file_icon);
                            fileTypeName.setText(largeFilePath);
                            fileSize.setText(String.format(getResources().getString(R.string.file_current_size), Formatter.formatFileSize(StorageClearManagement.this, size)));
                            fileChecked = (CheckBox) v.findViewById(R.id.file_clean_isChecked);
                            fileChecked.setChecked(mLargeFileCheckedMap.get(largeFilePath));
                            fileChecked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                                    mLargeFileCheckedMap.put(largeFilePath, isChecked);
                                    if (!isChecked) {
                                        mSizeTotal -= size;
                                    } else {
                                        mSizeTotal += size;
                                    }
                                    if (mSizeTotal <= 0) {
                                        updateUihandler.sendEmptyMessage(DONE_CLEAN);
                                    } else {
                                        updateUihandler.sendEmptyMessage(UPDATE_SIZE);
                                    }
                                }
                            });
                            holder.l.addView(v);
                            v = null;
                        }
                        /**
                         * @}
                         */
                        break;
                    case FILE_LARGE_AUDIO_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_large_audio_ext);
                        break;
                    case FILE_LARGE_VIDEO_EXT:
                        setDetailItemView(key, detailMap.get(key), R.string.file_large_video_ext);
                        break;
                }
                if (v != null) {
                    fileChecked = (CheckBox) v.findViewById(R.id.file_clean_isChecked);
                    fileChecked.setChecked(mCheckedMap.get(key));
                    fileChecked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                            mCheckedMap.put(key, isChecked);
                            if (!isChecked) {
                                mSizeTotal -= mData.getDetailTotalSize(key);
                            } else {
                                mSizeTotal += mData.getDetailTotalSize(key);
                            }
                            if (mSizeTotal <= 0) {
                                updateUihandler.sendEmptyMessage(DONE_CLEAN);
                            } else {
                                updateUihandler.sendEmptyMessage(UPDATE_SIZE);
                            }
                        }
                    });
                    holder.l.addView(v);
                    v = null;
                }
            }
            long sizeCurrentType = mData.getCategorySizeByType(mFileTypes[position]);
            holder.size.setText(String.format(getResources().getString(R.string.file_current_size),
                    Formatter.formatFileSize(StorageClearManagement.this, sizeCurrentType)));
            holder.name.setText(mNameIds[position]);
            return convertView;
        }

        public void setDetailItemView(int key, ArrayList<FileDetailModel> list, int rsid) {
            v = View.inflate(StorageClearManagement.this, R.layout.storage_type_detail_item, null);
            if (list.size() == 0) {
                v.setVisibility(View.GONE);
            }
            fileTypeName = (TextView) v.findViewById(R.id.file_type_drawable_name);
            fileSize = (TextView) v.findViewById(R.id.file_type_size);
            long size = mData.getTotalSizeByType(list);
            fileTypeName.setText(rsid);
            fileSize.setText(String.format(getResources().getString(R.string.file_current_size),
                    Formatter.formatFileSize(StorageClearManagement.this, size)));
        }

        @Override
        public boolean isEnabled(int position) {
            if (fileSize != null) {
                return true;
            }
            return false;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        LinearLayout fileDetail = (LinearLayout) view.findViewById(R.id.detail_file_list);
        ImageView imageView = (ImageView) view.findViewById(R.id.img);
        // UNISOC: Bug856347 size is zero, but still display expand icon
        if (mData.getCategorySizeByType(mFileTypes[position]) <= 0) {
            return;
        }
        if (fileDetail.getVisibility() == View.GONE) {
            fileDetail.setVisibility(View.VISIBLE);
            imageView.setImageResource(R.drawable.list_up);
        } else if (fileDetail.getVisibility() == View.VISIBLE) {
            fileDetail.setVisibility(View.GONE);
            imageView.setImageResource(R.drawable.list_down);
        }
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
    /**UNISOC: Bug913453 Remove the modification of the default write permission of the sd card to the mobile assistant. @{*/
        if (EnvironmentEx.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
            return fileDel(file);
        }else {
            return file.delete();
        }
    }

    private boolean fileDel(File file) {
        String path = file.getAbsolutePath();
        String externalStoragePath = EnvironmentEx.getExternalStoragePath().toString();

        if (!path.substring(0,externalStoragePath.length()).equals(externalStoragePath)) {
            return file.delete();
        }
        /**UNISOC: Bug1113033 After granting access to a folder of the SD card,
         * the cleaning acceleration still cannot clean the junk files under the folder.. @{*/
        String extFilePath = file.getAbsolutePath().substring(externalStoragePath.length());
        if (sdcardUri == null) {
            return false;
        }

        String documentId = DocumentsContract.getTreeDocumentId(sdcardUri);
        Uri uri;
        String documentIds[] = documentId.split(":");

        if (documentIds.length <= 1) {//have been granted the access to the root path of SD Card
            uri = DocumentsContract.buildDocumentUriUsingTree(sdcardUri,
                    documentId + extFilePath);
        } else {
            String document = documentIds[1];
            String ext = extFilePath.substring(1);//remove the first "/"
            Log.d(TAG,"fileDel: document: "+document+" ext: "+ext);
            if (ext.startsWith(document)) {
                uri = DocumentsContract.buildDocumentUriUsingTree(sdcardUri,
                        documentIds[0] + ":" +ext);
            } else {
                Log.d(TAG,"fileDel: have not right");
                return false;
            }
        }
        /**@}*/
        try {
            return DocumentsContract.deleteDocument(getContentResolver(),uri);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**@}*/

    class FileDeleteTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            onStart();
        }

        private void onStart() {
            // UNISOC: Bug776745 ProgressDialog is always showing
            if (!((Activity) mContext).isFinishing() && mWorkingProgress != null
                    && mWorkingProgress.isShowing()) {
                mWorkingProgress.dismiss();
            }
            mWorkingProgress = new ProgressDialog(mContext);
            mWorkingProgress.setCancelable(false);
            mWorkingProgress.setTitle(R.string.clean_storage_menu);
            mWorkingProgress.setMessage(mContext.getResources().getString(
                    R.string.cleaning_wait));
            mWorkingProgress.show();
        }

        /**UNISOC: Bug1102394 Mobile assistant cleaning scan function adaptation Android Q @{*/
        @Override
        protected Void doInBackground(Void... arg0) {
            Set<Integer> s = mCheckedMap.keySet();
            // UNISOC: bug 1035060 ConcurrentModificationException leading to crash
            try {
              for (int fType : s) {
                if (mCheckedMap.get(fType)) {
                    switch (fType) {
                        case RUBBISH_BAK_EXT:
                            for (int i = mData.mRubbish_bak_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_bak_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_bak_ext.remove(f);
                                }
                            }
                            break;
                        case RUBBISH_LOG_EXT:
                            for (int i = mData.mRubbish_log_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_log_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_log_ext.remove(f);
                                }
                            }
                            break;
                        case TMP_FILE_EXT:
                            for (int i = mData.mRubbish_tmp_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_tmp_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_tmp_ext.remove(f);
                                }
                            }
                            break;
                        case APK_FILE_EXT:
                            for (int i = mData.mRubbish_apk_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_apk_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_apk_ext.remove(f);
                                }
                            }
                            break;
                        case APK_CATCHE1_EXT:
                            // clean innercache
                            for (FileDetailModel f : mData.mRubbish_cach1_ext) {
                                File file = new File(f.getFilePath());
                                String packageName = file.getName();
                                mPm.deleteApplicationCacheFiles(packageName, null);
                                mSizeTotal -= f.getFileSize();
                            }
                            mData.mRubbish_cach1_ext.clear();
                            break;
                        case APK_CATCHE2_EXT:
                            for (int i = mData.mRubbish_cach2_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_cach2_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_cach2_ext.remove(f);
                                }
                            }
                            break;
                        case FILE_LARGE_EXT:
                            /**
                             * UNISOC: Bug771467 display large file detail list in the extension list
                             * @{
                             */
                            String largeFilePath;
                            for (int i = mData.mRubbish_large_ext.size() - 1; i >= 0; i--) {
                                largeFilePath = mData.mRubbish_large_ext.get(i).getFilePath();
                                if (largeFilePath != null && mLargeFileCheckedMap.get(largeFilePath)) {
                                    if (deleteFiles(mData.mRubbish_large_ext.get(i))) {
                                        mData.mRubbish_large_ext.remove(i);
                                    }
                                }
                            }
                            /**
                             * @}
                             */
                            break;
                        case FILE_LARGE_AUDIO_EXT:
                            for (int i = mData.mRubbish_large_audio_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_large_audio_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_large_audio_ext.remove(f);
                                }
                            }
                            break;
                        case FILE_LARGE_VIDEO_EXT:
                            for (int i = mData.mRubbish_large_video_ext.size() - 1; i >= 0; i--) {
                                FileDetailModel f = mData.mRubbish_large_video_ext.get(i);
                                if (deleteFiles(f)) {
                                    mData.mRubbish_large_video_ext.remove(f);
                                }
                            }
                            break;
                    }
                }
              }
            } catch (ConcurrentModificationException e) {
                Log.i(TAG, "ConcurrentModificationException");
            }
            try {
                Thread.sleep(DIALOG_STAND_TIME);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }
        /**@}*/
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            // UNISOC: Bug776745 ProgressDialog is always showing
            if (!((Activity) mContext).isFinishing() && mWorkingProgress != null
                    && mWorkingProgress.isShowing()) {
                mWorkingProgress.dismiss();
            }
            mWorkingProgress = null;
            /**
             * UNISOC: Bug606356 if is run by FileExplorer, then auto scan rubbish
             * @{
             */
            final Message message = new Message();
            message.what = DONE_CLEAN;
            int size = 0;
            for (int i = 0; i < mFileDetailTypes.length; i++) {
                size += mData.getDetailTotalSize(mFileDetailTypes[i]);
            }
            Log.i(TAG, "onPostExecute size = " + size + " mSizeTotal = " + mSizeTotal);
            if (mSizeTotal <= 0 || size <= 0) {
                if (mSizeTotal != size && size <= 0) {
                    message.arg1 = RUBBISH_IS_CLEARED;
                }
                updateUihandler.sendMessage(message);
            /**UNISOC: Bug1102394 Mobile assistant cleaning scan function adaptation Android Q @{*/
            } else {
                updateUihandler.sendEmptyMessage(UPDATE_SIZE);
            }
            /**@}*/
            /**
             * @}
             */
            mStorageAdatper.notifyDataSetChanged();
            /**
             * UNISOC: Bug705779 the rubbish is cleared but still show in pc
             * @{
             */
            StorageUtils.notifyMediaScanDir(mContext, EnvironmentEx.getInternalStoragePath());
            if (hasSDcard()) {
                StorageUtils.notifyMediaScanDir(mContext, EnvironmentEx.getExternalStoragePath());
            }
            /**
             * @}
             */
        }
    }

    Handler updateUihandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                default:
                case UPDATE_SIZE:
                    // UNISOC: Bug813685 Formatter.formatFileSize has changed in 8.1
                    String[] strSize_lable = StorageUtils.convertTotalSize(mContext, mSizeTotal);
                    if (strSize_lable != null && strSize_lable.length > 1) {
                        totalRubbishSize.setText(strSize_lable[0]);
                        scoreLable.setText(strSize_lable[1]);
                    }
                    /**
                     * UNISOC: Bug850851 clear Button display error
                     * @{
                     */
                    if (mSizeTotal > 0) {
                        clearBtn.setText(mContext.getText(R.string.clean_memory_button) + " (" +
                                Formatter.formatFileSize(StorageClearManagement.this, mSizeTotal) + ")");
                    } else {
                        clearBtn.setText(mContext.getText(R.string.end_clean_memory_button));
                    }
                    /**
                     * @}
                     */
                    break;
                case DONE_CLEAN:
                    /**
                     * UNISOC: Bug705036 after clean rubbish done, the size display error
                     * @{
                     */
                    if (mSizeTotal < 0) {
                        mSizeTotal = 0;
                    }
                    /**
                     * @}
                     */
                    /**
                     * UNISOC: Bug606356 if is run by FileExplorer, then auto scan rubbish
                     * @{
                     */
                    if (msg.arg1 == RUBBISH_IS_CLEARED) {
                        totalRubbishSize.setText("0");
                        scoreLable.setText("B");
                        clearBtn.setText(mContext.getText(R.string.end_clean_memory_button));
                        break;
                    }
                    /**
                     * @}
                     */
                    // UNISOC: Bug813685 Formatter.formatFileSize has changed in 8.1
                    String[] strSize_lable_done = StorageUtils.convertTotalSize(mContext, mSizeTotal);
                    if (strSize_lable_done != null && strSize_lable_done.length > 1) {
                        totalRubbishSize.setText(strSize_lable_done[0]);
                        scoreLable.setText(strSize_lable_done[1]);
                    }
                    clearBtn.setText(mContext.getText(R.string.end_clean_memory_button));
                    break;
                case RESTART_SCAN:
                    startUpdateActivity();
                    break;
            }
        }
    };

    private void startUpdateActivity() {
        Intent intent = new Intent(StorageClearManagement.this, StorageManagement.class);
        startActivity(intent);
        finish();
    }

    /**UNISOC: Bug1102394 Mobile assistant cleaning scan function adaptation Android Q @{*/
    public boolean deleteFiles(FileDetailModel f) {
        File file = new File(f.getFilePath());
        // UNISOC: Bug759869 files still show after sd card is uninstalled
        if (dirDelete(file) || !file.exists()) {
            mSizeTotal -= f.getFileSize();
            return true;
        } else {
            return false;
        }
    }
    /**@}*/
    @Override
    protected void onDestroy() {
        super.onDestroy();
        /* UNISOC: add for Bug 1012437 set mWorkingProgress is null to avoid IllegalArgumentException @{ */
        mWorkingProgress = null;
        /* @} */
        /**
         * UNISOC: Bug705779 the rubbish is cleared but still show in pc
         * UNISOC: Bug855179 Enter Clean Up Acceleration ->Click to start scanning -> Remove SD card during
         * scanning and show the Clean Up Acceleration interface -> The "Start Scan" menu is still
         * displayed, but all types of files are always showing the status of the unscanned completed,
         * after clicking the "Start Scan" menu,it does not appear Scan menu.
         * @{
         */
//        if (mStorageManager != null && mStorageListener != null) {
//            try {
//                mStorageManager.unregisterListener(mStorageListener);
//            } catch (Exception e) {
//                Log.i(TAG, "unregisterListener... exception");
//            }
//        }
        /**
         * @}
         */
    }

    @Override
    public void onClick(View view) {
        final String text = ((Button) view).getText().toString().trim();
        if (view.getId() == R.id.clear_bt) {
            if (!text.equals(mContext.getText(R.string.end_clean_memory_button))
                    && text.contains(mContext.getText(R.string.clean_memory_button))) {
                /**UNISOC: Bug913453 Remove the modification of the default write permission of the sd card to the mobile assistant. @{*/
                if (EnvironmentEx.getExternalStoragePathState().equals(Environment.MEDIA_MOUNTED)) {
                    onScopedDirectoryTest();
                } else {
                    FileDeleteTask deleteTask = new FileDeleteTask();
                    deleteTask.execute();
                }
                /**@}*/
            } else if (text.contains(mContext.getText(R.string.end_clean_memory_button))
                    || text.contains(mContext.getText(R.string.clear_memory))) {
                finish();
            }
        }
    }
    /**UNISOC: Bug913453 Remove the modification of the default write permission of the sd card to the mobile assistant. @{*/
    private Uri sdcardUri;
    final static int REQUEST_CODE = 42;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE) {
            sdcardUri = intent.getData();
            Log.d(TAG,"onActivityResult "+sdcardUri);
            final int takeFlags = intent.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(sdcardUri,takeFlags);
            FileDeleteTask deleteTask = new FileDeleteTask();
            deleteTask.execute();
        }
    }
    private void onScopedDirectoryTest() {
        for (StorageVolume volume : getVolumes()) {
            File volumePath = volume.getPathFile();
            if (!volume.isPrimary() && volumePath != null &&
                    Environment.getExternalStorageState(volumePath).equals(Environment.MEDIA_MOUNTED)
                    && volumePath.equals(EnvironmentEx.getExternalStoragePath())) {
                requestScopedDirectoryAccess(volume, null);
            }
        }
    }
    private void requestScopedDirectoryAccess(StorageVolume volume, String directoryName) {
        final Intent intent = volume.createOpenDocumentTreeIntent();
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE);
        }
    }
    private List<StorageVolume> getVolumes() {
        final StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        final List<StorageVolume> volumes = sm.getStorageVolumes();
        return volumes;
    }
    /**@}*/

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
    /**
     * UNISOC: Bug855179 Enter Clean Up Acceleration ->Click to start scanning -> Remove SD card during
     * scanning and show the Clean Up Acceleration interface -> The "Start Scan" menu is still
     * displayed, but all types of files are always showing the status of the unscanned completed,
     * after clicking the "Start Scan" menu,it does not appear Scan menu.
     * @{
     */
//    StorageEventListener mStorageListener = new StorageEventListener() {
//        @Override
//        public void onVolumeStateChanged(VolumeInfo vol, int oldState,
//                                         int newState) {
//            Log.i(TAG, "vol state:" + vol + "volchange  oldState:" + oldState
//                    + "    newState:" + newState);
//
//            if (vol.linkName != null && vol.linkName.startsWith(SDCARD_PREFIX)) {
//                if ((oldState == VolumeInfo.STATE_MOUNTED && newState == VolumeInfo.STATE_EJECTING)) {
//                    Toast.makeText(mContext, mContext.getResources().getString(R.string.sdcard_state_removed),
//                            Toast.LENGTH_SHORT).show();
//                } else if (oldState == VolumeInfo.STATE_CHECKING
//                        && newState == VolumeInfo.STATE_MOUNTED) {
//                    Toast.makeText(mContext, mContext.getResources().getString(R.string.sdcard_state_inserted),
//                            Toast.LENGTH_SHORT).show();
//                }
//            }
//        }
//    };
    /**
     * @}
     */
}
