package com.croconaut.cpt.gcm;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.croconaut.cpt.link.PreferenceHelper;
import com.croconaut.cpt.network.AppServerSyncDownloadAttachmentsService;
import com.croconaut.cpt.network.AppServerSyncDownloadMessagesService;
import com.croconaut.cpt.network.AppServerSyncUploadFriendsService;
import com.croconaut.cpt.network.AppServerSyncUploadLocalMessagesWithAttachmentsService;
import com.croconaut.cpt.network.AppServerSyncUploadNonLocalMessagesService;
import com.croconaut.cpt.network.AppServerSyncUploadTokenService;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;

public class SyncTaskService extends GcmTaskService {
    private static final String TAG = "gcm";

    @Override
    public void onInitializeTasks() {
        Log.v(TAG, getClass().getSimpleName() + ".onInitializeTasks");

        /*
         * When your package is removed or updated, all of its tasks are cleared by the GcmNetworkManager.
         * You can override this method to reschedule them in the case of an updated package.
         * This is not called when your application is first installed.
         */
    }

    @Override
    public int onRunTask(TaskParams taskParams) {
        Log.v(TAG, getClass().getSimpleName() + ".onRunTask: " + taskParams.getTag());

        /*
         * Once a task is running it will not be cancelled, however a newly scheduled task with the same tag
         * will not be executed until the active task has completed. This newly scheduled task will replace
         * the previous task, regardless of whether the previous task returned RESULT_RESCHEDULE.
         */

        PreferenceHelper helper = new PreferenceHelper(this);
        if (!helper.getInternetEnabled()) {
            Log.w(TAG, "Not doing anything");
            return GcmNetworkManager.RESULT_SUCCESS;
        }

        boolean fullSync = false;
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        // the task is run only when network is connected
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetworkInfo != null) {
            fullSync = activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI;
        }

        // TODO: if we do not wish to have a cancel option, we can call directly Runnable.run()
        if (taskParams.getTag().equals(AppServerSyncDownloadAttachmentsService.class.getName())) {
            AppServerSyncDownloadAttachmentsService.sync(this, true, fullSync);
        } else if (taskParams.getTag().equals(AppServerSyncDownloadMessagesService.class.getName())) {
            AppServerSyncDownloadMessagesService.sync(this, true, fullSync);
        } else if (taskParams.getTag().equals(AppServerSyncUploadLocalMessagesWithAttachmentsService.class.getName())) {
            AppServerSyncUploadLocalMessagesWithAttachmentsService.sync(this, true, fullSync);
        } else if (taskParams.getTag().equals(AppServerSyncUploadNonLocalMessagesService.class.getName())) {
            AppServerSyncUploadNonLocalMessagesService.sync(this, true, fullSync);
        } else if (taskParams.getTag().equals(AppServerSyncUploadTokenService.class.getName())) {
            AppServerSyncUploadTokenService.sync(this, true, fullSync);
        } else if (taskParams.getTag().equals(AppServerSyncUploadFriendsService.class.getName())) {
            AppServerSyncUploadFriendsService.sync(this, true, fullSync);
        } else {
            Log.e(TAG, "Unknown tag: " + taskParams.getTag());
        }

        return GcmNetworkManager.RESULT_SUCCESS;
    }
}
