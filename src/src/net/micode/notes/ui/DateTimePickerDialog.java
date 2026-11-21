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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 便签提醒日期时间选择对话框类，继承自 AlertDialog 并实现按钮点击接口。
 * 内部承载自定义 DateTimePicker 滚轮控件，随用户滚动实时更新对话框标题栏日期时间预览，
 * 并在用户点击“设置”确认按钮时通过 OnDateTimeSetListener 将选定时间戳回传给 NoteEditActivity。
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    // 保存对话框当前选中的日期时间（秒数归零）
    private Calendar mDate = Calendar.getInstance();
    // 是否使用 24 小时制格式化标题栏时间
    private boolean mIs24HourView;
    // 用户点击“设置”确认按钮后的回调监听器
    private OnDateTimeSetListener mOnDateTimeSetListener;
    // 嵌入对话框内容区的日期时间滚轮选择控件
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间设定完成回调接口，供调用方接收用户最终确认的提醒时间戳（毫秒）。
     */
    public interface OnDateTimeSetListener {
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造日期时间选择对话框：嵌入 DateTimePicker 控件、绑定滚动监听、初始化默认时间及“设置/取消”按钮。
     *
     * @param context 宿主 Activity 上下文
     * @param date    初始显示的默认日期时间戳（毫秒）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                updateTitle(mDate.getTimeInMillis());
            }
        });
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);
        set24HourView(DateFormat.is24HourFormat(this.getContext()));
        updateTitle(mDate.getTimeInMillis());
    }

    // 设置是否启用 24 小时制显示标志
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    // 注册点击确认按钮时的日期时间选定回调监听器
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 将指定时间戳格式化为包含年、月、日、时、分的本地化字符串并更新到对话框标题栏。
     */
    private void updateTitle(long date) {
        int flag =
            DateUtils.FORMAT_SHOW_YEAR |
            DateUtils.FORMAT_SHOW_DATE |
            DateUtils.FORMAT_SHOW_TIME;
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 对话框“设置”按钮点击回调：将用户选定的毫秒级时间戳通知给 mOnDateTimeSetListener。
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }

}