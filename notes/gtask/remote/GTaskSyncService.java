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

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;


/**
  *
  * @Package:        net.micode.notes.gtask.remote
  * @ClassName:      GTaskSyncService
  * @Description:
  *  1. 管理Google Task同步的生命周期
  *  2. 接收同步开始/取消的Intent命令
  *  3. 在后台执行同步操作，避免阻塞UI线程
  *  4. 通过广播发送同步状态和进度信息
  */
public class GTaskSyncService extends Service {
    public final static String ACTION_STRING_NAME = "sync_action_type";  // Intent动作类型键

    public final static int ACTION_START_SYNC = 0;      // 开始同步动作
    public final static int ACTION_CANCEL_SYNC = 1;     // 取消同步动作
    public final static int ACTION_INVALID = 2;         // 无效动作

    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";  // 广播名称

    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";  // 是否正在同步的键
    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";  // 进度消息的键

    private static GTaskASyncTask mSyncTask = null;     // 同步任务实例
    private static String mSyncProgress = "";           // 同步进度信息

    /**
     * 开始同步
     *
     * 执行流程：
     * 1. 检查是否已经有同步任务在运行
     * 2. 创建新的GTaskASyncTask异步任务
     * 3. 设置任务完成监听器，在完成时清理资源
     * 4. 执行异步任务，发送进度广播
     */
    private void startSync() {
        // 确保只有一个同步任务在运行
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    mSyncTask = null;        // 清理任务引用
                    sendBroadcast("");       // 发送完成广播
                    stopSelf();              // 停止服务自身
                }
            });
            sendBroadcast("");               // 发送开始同步广播
            mSyncTask.execute();             // 执行异步任务
        }
    }

   //取消同步
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    //初始化同步任务引用
    @Override
    public void onCreate() {
        mSyncTask = null;
    }


    /**
     * @method  onStartCommand
     * @description 根据Intent中的动作类型执行相应的同步操作
     * @param intent 启动服务的Intent
     * @param flags 启动标志
     * @param startId 启动ID
     * @return 服务启动模式（START_STICKY表示服务被杀死后自动重启）
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle bundle = intent.getExtras();
        if (bundle != null && bundle.containsKey(ACTION_STRING_NAME)) {
            switch (bundle.getInt(ACTION_STRING_NAME, ACTION_INVALID)) {
                case ACTION_START_SYNC:
                    startSync();
                    break;
                case ACTION_CANCEL_SYNC:
                    cancelSync();
                    break;
                default:
                    break;
            }
            return START_STICKY;  // 服务被杀死后自动重启
        }
        return super.onStartCommand(intent, flags, startId);
    }

    //当系统内存不足时，取消同步任务以释放资源
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    //此服务不提供绑定功能，返回null
    public IBinder onBind(Intent intent) {
        return null;
    }

    //发送同步状态广播
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }


    //设置GTaskManager的Activity上下文并启动服务
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }


    //从Context调用的便捷方法，发送取消同步命令到服务
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    //检查是否正在同步

    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    
    public static String getProgressString() {
        return mSyncProgress;
    }
}