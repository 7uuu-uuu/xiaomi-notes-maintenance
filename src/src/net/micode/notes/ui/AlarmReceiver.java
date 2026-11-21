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

package net.micode.notes.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 便签定时闹钟到点广播接收器，继承自 BroadcastReceiver（运行于 :remote 独立进程）。
 * 接收由 AlarmManager 在预设提醒时刻触发的广播，并将携带便签 URI 的 Intent 转发启动 AlarmAlertActivity。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    /**
     * 闹钟触发回调：重定向目标组件为 AlarmAlertActivity，附加 NEW_TASK 栈标志后启动响铃弹窗界面。
     *
     * @param context 广播上下文
     * @param intent  包含待提醒便签 Content URI 的广播 Intent
     */
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, AlarmAlertActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
