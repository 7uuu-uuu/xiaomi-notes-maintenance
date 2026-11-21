
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
 * Google Task 异步云同步任务类，继承自 Android AsyncTask<Void, String, Integer>。
 * 负责在后台工作线程中驱动 GTaskManager 执行耗时的网络同步流程，在主线程实时更新系统状态栏
 * Notification 通知并转发进度广播，在同步结束后更新最后同步时间并回调 OnCompleteListener。
 */
public class GTaskASyncTask extends AsyncTask<Void, String, Integer> {

    // 系统状态栏同步通知的唯一标识 ID
    private static int GTASK_SYNC_NOTIFICATION_ID = 5234235;

    /**
     * 异步同步任务完成回调接口，供 GTaskSyncService 在同步结束后清理状态并停止服务。
     */
    public interface OnCompleteListener {
        void onComplete();
    }

    // 启动同步任务的上下文对象（通常为 GTaskSyncService）
    private Context mContext;

    // 系统通知管理器，用于发布同步进度与结果状态栏通知
    private NotificationManager mNotifiManager;

    // 核心同步逻辑管理器单例引用
    private GTaskManager mTaskManager;

    // 同步完成后的回调监听器
    private OnCompleteListener mOnCompleteListener;

    /**
     * 构造异步同步任务，获取系统通知服务及 GTaskManager 单例。
     *
     * @param context  上下文对象
     * @param listener 任务执行完毕时的回调监听器
     */
    public GTaskASyncTask(Context context, OnCompleteListener listener) {
        mContext = context;
        mOnCompleteListener = listener;
        mNotifiManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mTaskManager = GTaskManager.getInstance();
    }

    /**
     * 请求取消当前正在进行的云同步任务（设置 GTaskManager 取消标志位）。
     */
    public void cancelSync() {
        mTaskManager.cancelSync();
    }

    /**
     * 对外暴露的进度发布辅助方法，供 GTaskManager 在后台线程推送阶段性进度提示文本。
     *
     * @param message 当前同步阶段的文字描述
     */
    public void publishProgess(String message) {
        publishProgress(new String[] {
            message
        });
    }

    /**
     * 构建并发送系统状态栏通知：同步成功时点击跳转便签列表页，其他状态点击跳转同步设置页。
     *
     * @param tickerId 状态栏滚动提示语字符串资源 ID
     * @param content  通知展开后的详情内容文本
     */
    private void showNotification(int tickerId, String content) {
        Notification notification = new Notification(R.drawable.notification, mContext
                .getString(tickerId), System.currentTimeMillis());
        notification.defaults = Notification.DEFAULT_LIGHTS;
        notification.flags = Notification.FLAG_AUTO_CANCEL;
        PendingIntent pendingIntent;
        if (tickerId != R.string.ticker_success) {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesPreferenceActivity.class), 0);

        } else {
            pendingIntent = PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                    NotesListActivity.class), 0);
        }
        notification.setLatestEventInfo(mContext, mContext.getString(R.string.app_name), content,
                pendingIntent);
        mNotifiManager.notify(GTASK_SYNC_NOTIFICATION_ID, notification);
    }

    @Override
    /**
     * 后台线程执行入口：先推送“正在登录账号”进度提示，随后调用 GTaskManager.sync 执行完整同步。
     *
     * @return 同步最终结果状态码（GTaskManager.STATE_*）
     */
    protected Integer doInBackground(Void... unused) {
        publishProgess(mContext.getString(R.string.sync_progress_login, NotesPreferenceActivity
                .getSyncAccountName(mContext)));
        return mTaskManager.sync(mContext, this);
    }

    @Override
    /**
     * 主线程进度更新回调：刷新状态栏同步进度通知，并通过 GTaskSyncService 发送全局进度广播。
     */
    protected void onProgressUpdate(String... progress) {
        showNotification(R.string.ticker_syncing, progress[0]);
        if (mContext instanceof GTaskSyncService) {
            ((GTaskSyncService) mContext).sendBroadcast(progress[0]);
        }
    }

    @Override
    /**
     * 主线程结束回调：根据同步返回码展示成功/网络错误/内部错误/已取消通知，
     * 成功时持久化保存最后同步时间戳，并在独立线程中触发 onComplete 完成回调。
     */
    protected void onPostExecute(Integer result) {
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
        if (mOnCompleteListener != null) {
            new Thread(new Runnable() {

                public void run() {
                    mOnCompleteListener.onComplete();
                }
            }).start();
        }
    }
}
