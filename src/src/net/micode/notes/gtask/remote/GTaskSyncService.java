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
 * Google Task 后台云同步服务类，继承自 Android Service。
 * 接收来自便签主界面或设置界面的启动/取消同步 Intent 指令，管理 GTaskASyncTask 异步任务的生命周期，
 * 在系统内存不足时自动取消同步，并通过自定义广播实时向前端界面播报同步状态与进度文本。
 */
public class GTaskSyncService extends Service {
    // 启动服务 Intent 中携带的同步指令参数键名及 3 种动作取值（0-开始、1-取消、2-无效）
    public final static String ACTION_STRING_NAME = "sync_action_type";

    public final static int ACTION_START_SYNC = 0;

    public final static int ACTION_CANCEL_SYNC = 1;

    public final static int ACTION_INVALID = 2;

    // 同步状态变化广播的 Action 名称及其附加字段键名（是否正在同步、当前进度消息）
    public final static String GTASK_SERVICE_BROADCAST_NAME = "net.micode.notes.gtask.remote.gtask_sync_service";

    public final static String GTASK_SERVICE_BROADCAST_IS_SYNCING = "isSyncing";

    public final static String GTASK_SERVICE_BROADCAST_PROGRESS_MSG = "progressMsg";

    // 当前正在运行的静态异步同步任务实例（为 null 表示空闲未同步）
    private static GTaskASyncTask mSyncTask = null;

    // 当前同步阶段的最新进度描述字符串
    private static String mSyncProgress = "";

    /**
     * 内部启动同步：若当前无运行中的同步任务，则创建并启动 GTaskASyncTask，完成后自动发广播并停止服务。
     */
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

    /**
     * 内部取消同步：若存在正在执行的同步任务，则调用其 cancelSync() 请求中止。
     */
    private void cancelSync() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    @Override
    /**
     * 服务首次创建回调，将同步任务引用重置为 null。
     */
    public void onCreate() {
        mSyncTask = null;
    }

    @Override
    /**
     * 服务启动命令回调：解析 Intent Bundle 中的动作类型，分发执行启动同步或取消同步。
     */
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

    @Override
    /**
     * 系统低内存告警回调：主动取消正在进行的同步任务以释放内存资源。
     */
    public void onLowMemory() {
        if (mSyncTask != null) {
            mSyncTask.cancelSync();
        }
    }

    /**
     * 不支持绑定模式启动服务，直接返回 null。
     */
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 记录最新同步进度消息，并向应用内广播当前是否正在同步及进度详情文本。
     *
     * @param msg 同步进度提示文本
     */
    public void sendBroadcast(String msg) {
        mSyncProgress = msg;
        Intent intent = new Intent(GTASK_SERVICE_BROADCAST_NAME);
        intent.putExtra(GTASK_SERVICE_BROADCAST_IS_SYNCING, mSyncTask != null);
        intent.putExtra(GTASK_SERVICE_BROADCAST_PROGRESS_MSG, msg);
        sendBroadcast(intent);
    }

    /**
     * 静态便捷入口：绑定发起同步的 Activity 上下文，并发送 ACTION_START_SYNC 启动后台同步服务。
     *
     * @param activity 发起同步的 Activity 实例
     */
    public static void startSync(Activity activity) {
        GTaskManager.getInstance().setActivityContext(activity);
        Intent intent = new Intent(activity, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_START_SYNC);
        activity.startService(intent);
    }

    /**
     * 静态便捷入口：向 GTaskSyncService 发送 ACTION_CANCEL_SYNC 指令以取消当前同步。
     *
     * @param context 上下文对象
     */
    public static void cancelSync(Context context) {
        Intent intent = new Intent(context, GTaskSyncService.class);
        intent.putExtra(GTaskSyncService.ACTION_STRING_NAME, GTaskSyncService.ACTION_CANCEL_SYNC);
        context.startService(intent);
    }

    // 判断后台当前是否正在执行云同步任务
    public static boolean isSyncing() {
        return mSyncTask != null;
    }

    // 获取当前最新的云同步进度描述文本
    public static String getProgressString() {
        return mSyncProgress;
    }
}
