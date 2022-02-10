package com.sprd.generalsecurity.storage;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Environment;
import android.os.UserHandle;
import android.util.Log;

import com.android.settingslib.applications.StorageStatsSource;
import com.sprd.generalsecurity.utils.MemoryUtils;
import com.sprd.generalsecurity.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CacheAndFileLoader extends AsyncTaskLoader<List<FileDetailModel>> {

    private static final String TAG = "StorageManagement "+CacheAndFileLoader.class.getSimpleName();

    private Callback callback;
    private StorageStatsSource mStatsManager;
    private PackageManager mPackageManager;


    public CacheAndFileLoader(Context context) {
        super(context);
        callback = (Callback) context;
        mPackageManager = context.getPackageManager();
        mStatsManager = new StorageStatsSource(context);

    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
        Log.d(TAG,"onStartLoading");
        callback.startLoad();
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        Log.d(TAG,"onStopLoading");
        callback.cancelAllLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        cancelLoad();
    }

    @Override
    public List<FileDetailModel> loadInBackground() {
        Log.d(TAG, "loadInBackground start");

        callback.scanAllFiles();
        getStorageResultForUser();

        Log.d(TAG, "Obtaining result completed");
        callback.onComplete();
        return null;
    }

    /**
     * This method scan all cache of apps except system apps and apps in launcher.
     * It can refresh the UI real time.
     */
    private void getStorageResultForUser() {
        Log.i(TAG, "scan apps cache start");
        List<ApplicationInfo> applicationInfos = mPackageManager.getInstalledApplicationsAsUser(0, UserHandle.myUserId());
        UserHandle myUser = UserHandle.of(UserHandle.myUserId());

        for (int i = 0, size = applicationInfos.size(); i < size; i++) {
            //UNISOC: Bug1121147 not stop getting apps cache when user stop scan.
            if (callback.isCancelAllLoad()) {
                break;
            }
            ApplicationInfo app = applicationInfos.get(i);

            /** UNISOC: Bug 674774 keep mc10086 from killing and cleaning @{ */
            if (MemoryUtils.isPersistentServiceProcess(app.packageName)) {
                continue;
            }
            /** @} */

            if ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    || (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || StorageUtils.isLauncherApp(mPackageManager, app.packageName)) {

                StorageStatsSource.AppStorageStats stats;
                try {
                    stats = mStatsManager.getStatsForPackage(app.volumeUuid, app.packageName, myUser);
                } catch (PackageManager.NameNotFoundException | IOException e) {
                    // This may happen if the package was removed during our calculation.
                    Log.w(TAG, "App unexpectedly not found", e);
                    continue;
                }

                FileDetailModel result = new FileDetailModel((Environment.getDataDirectory()
                        .getAbsoluteFile()
                        + File.separator
                        + "data"
                        + File.separator
                        + app.packageName), stats.getCacheBytes());

                callback.refreshCacheUi(result);
            }

        }
        Log.i(TAG, "scan apps cache end");
    }

    public interface Callback {
        void refreshCacheUi(FileDetailModel fileDetailModel);
        void cancelAllLoad();
        void startLoad();
        void scanAllFiles();
        void onComplete();
        //UNISOC: Bug1121147 not stop getting apps cache when user stop scan.
        boolean isCancelAllLoad();
    }
}
