/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import net.micode.notes.R;
import net.micode.notes.ui.NotesListActivity;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * GTaskASyncTask 类 - Google Task同步异步任务
 *
 * 功能概述：
 * 1. 在后台线程执行Google Task同步操作
 * 2. 显示同步进度通知
 * 3. 处理同步完成后的结果展示
 * 4. 支持同步取消操作
 *
 * 设计模式：
 * - 异步任务模式：继承AsyncTask，在后台线程执行同步
 * - 观察者模式：通过回调接口通知同步完成
 * - 命令模式：封装同步操作和取消操作
 *
 * 生命周期：
 * 1. doInBackground()：在后台执行同步
 * 2. onProgressUpdate()：更新同步进度通知
 * 3. onPostExecute()：同步完成后显示结果通知
 * 4. cancelSync()：取消正在进行的同步
 *
 * 线程安全：
 * 使用AsyncTask确保UI线程和后台线程的正确分离
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
/**
  *
  * @Package:        net.micode.notes.gtask.remote
  * @ClassName:      GTaskASyncTask
  * @Description:    1. 在后台线程执行Google Task同步操作
 *                   2. 显示同步进度通知
 *                   3. 处理同步完成后的结果展示
 *                   4. 支持同步取消操作
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;  // 同步通知ID

    //同步完成监听器接口 用于通知同步服务的完成事件
    public interface OnCompleteListener {
        void onComplete();
    }

    private Context mContext; // Android上下文
    private NotificationManager mNotifiManager;  // 通知管理器
    private GTaskManager mTaskManager;        // 同步管理器
    private OnCompleteListener mOnCompleteListener;  // 完成监听器


    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }


    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    //发布同步进度
    public void publishProgess(String message) {
        publishProgress(new String[] {
                message
        });
    }

    /**
     * @method  showNotification
     * @description 显示同步通知
     * @param tickerId 通知标题字符串资源ID
     * @param content 通知内容
     */
    private void showNotification(int tickerId, String content) {
        PendingIntent pendingIntent;
        if (tickerId != R.string.ticker_success) {
            // 同步中或失败：跳转到设置页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), PendingIntent.FLAG_IMMUTABLE);
        } else {
            // 同步成功：跳转到便签列表页面
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), PendingIntent.FLAG_IMMUTABLE);
        }

        // 构建通知
        Notification.Builder builder = new Notification.Builder(mContext)
                .setAutoCancel(true)
                .setContentTitle(mContext.getString(R.string.app_name))
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true);
        Notification notification=builder.getNotification();
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    /**
     * 在后台执行同步
     *
     * 执行流程：
     * 1. 发布登录进度
     * 2. 调用GTaskManager执行同步
     *
     * @param unused 未使用的参数（Void类型）
     * @return 同步结果状态码
     */
    /**
     * @method  doInBackground
     * @description 在后台执行同步
     * @param unused 未使用的参数（Void类型）
     * @return 同步结果状态码
     */
    @Override
    protected Integer doInBackground(Void... unused) {
        // 发布登录进度
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));

        // 执行同步
        return mTaskManager.sync(mContext, this);
    }


    //更新同步进度
    @Override
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }


    //同步完成后处理。根据同步结果显示相应的通知，并调用完成监听器
    @Override
    protected void onPostExecute(Integer result) {
        // 根据同步结果显示相应的通知
        if (result == GTaskManager.STATE_SUCCESS) {
            showNotification(R.string.ticker_success, mContext.getString(
                    R.string.success_sync_account, mTaskManager.getSyncAccount()));
            NotesPreferenceActivity.setLastSyncTime(mContext, System.currentTimeMillis());
        } else if (result == GTaskManager.STATE_NETWORK_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_network));
        } else if (result == GTaskManager.STATE_INTERNAL_ERROR) {
            showNotification(R.string.ticker_fail, mContext.getString(R.string.error_sync_internal));
        } else if (result == GTaskManager.STATE_SYNC_CANCELLED) {
            showNotification(R.string.ticker_cancel, mContext
                    .getString(R.string.error_sync_cancelled));
        }

        // 调用完成监听器（在新线程中执行，避免阻塞UI）
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {
                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}