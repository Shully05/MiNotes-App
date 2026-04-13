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

/// 同步服务，负责在后台执行同步任务
public class GTaskSyncService extends Service {
    // 同步服务的Intent中传递的参数名称
    public final static String ACTION_STRING_NAME = "sync_action_type";

    public final static int ACTION_START_SYNC = 0;// 启动同步

    public final static int ACTION_CANCEL_SYNC = 1;// 取消同步

    public final static int ACTION_INVALID = 2;// 无效的操作

    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";// 同步服务的广播名称

    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";// 同步服务的广播中是否正在同步的参数名称

    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";// 同步服务的广播中同步进度消息的参数名称

    private static GTaskASyncTask mSyncTask = null;// 同步任务

    private static String mSyncProgress = "";// 同步进度消息

    // 启动同步任务
    private void startSync() {
        if (mSyncTask == null) {
            mSyncTask = new GTaskASyncTask(this, new GTaskASyncTask.OnCompleteListener() {
                public void onComplete() {
                    mSyncTask = null;
                    sendBroadcast("");
                    stopSelf();
                }
            });
            sendBroadcast("");
            mSyncTask.execute();
        }
    }

    // 取消同步任务
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // 服务被创建时调用,初始化同步任务
    @Override
    public void onCreate() {
        mSyncTask = null;
    }

    // 服务被销毁时调用,取消正在执行的同步任务
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
            return START_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    // 当系统内存不足时调用,取消正在执行的同步任务
    @Override
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    // 绑定服务时调用,本服务不提供绑定功能,返回null
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 发送广播通知同步状态和进度
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    // 启动同步服务并执行同步任务
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    // 启动同步服务并取消正在执行的同步任务
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    // 获取当前是否正在执行同步任务
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    // 获取当前同步进度消息
    public static String getProgressString() {
        return mSyncProgress;
    }
}
