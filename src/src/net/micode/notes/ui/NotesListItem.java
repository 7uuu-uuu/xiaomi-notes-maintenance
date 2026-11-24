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

import android.content.Context;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;


/**
 * 便签主列表单行条目自定义视图类，继承自 LinearLayout。
 * 负责加载 note_item 布局并根据 NoteItemData 数据区分渲染四种条目形态：通话便签系统目录、
 * 通话便签子项、用户自定义文件夹（含数量）及普通便签（含闹钟图标、相对时间、多选框及分位圆角背景）。
 */
public class NotesListItem extends LinearLayout {
    // 右侧闹钟提醒图标（或通话记录文件夹专属图标）
    private ImageView mAlert;
    // 便签单行摘要标题或“文件夹名 (数量)”文本控件
    private TextView mTitle;
    // 便签或文件夹最后修改的相对时间文本控件
    private TextView mTime;
    // 通话便签专属的联系人姓名/电话号码文本控件
    private TextView mCallName;
    // 当前列表项视图绑定的数据模型对象
    private NoteItemData mItemData;
    // 批量多选模式下显示的复选框控件
    private CheckBox mCheckBox;

    /**
     * 构造列表项视图：加载 R.layout.note_item 布局并初始化各个子控件引用。
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
    }

    /**
     * 将 NoteItemData 数据绑定渲染到当前列表项控件，处理复选框显隐、标题/图标/时间展示及背景皮肤切换。
     *
     * @param context    上下文对象
     * @param data       当前行对应的数据对象
     * @param choiceMode 是否处于批量多选模式
     * @param checked    当前项是否被勾选
     */
    public void bind(Context context, NoteItemData data, boolean choiceMode, boolean checked) {
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        mItemData = data;
        // 形态 1：通话便签系统文件夹，显示“通话便签(数量)”和通话记录专属图标
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.call_record);
        // 形态 2：通话便签记录项，主显联系人姓名/号码，次显备注摘要及可选闹钟图标
        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            mTitle.setTextAppearance(context,R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }
        } else {
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            // 形态 3：用户自定义文件夹，显示“文件夹名(便签数)”；形态 4：普通便签，显示单行摘要与可选闹钟图标
            if (data.getType() == Notes.TYPE_FOLDER) {
                mTitle.setText(data.getSnippet()
                        + context.getString(R.string.format_folder_files_count,
                        data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                String snippet = DataUtils.getFormattedSnippet(data.getSnippet());
                // Add pin indicator for pinned notes
                if (data.isPinned()) {
                    snippet = "\uD83D\uDCCC " + snippet;  // Pin emoji
                }
                // Add lock indicator for password-protected notes
                if (data.hasPassword()) {
                    snippet = "\uD83D\uDD12 " + snippet;  // Lock emoji
                }
                mTitle.setText(snippet);
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        setBackground(data);
    }

    /**
     * 根据条目类型、颜色 ID 及其在列表中的相对位置（单项/首项/中间/末项）设置对应的 Nine-Patch 背景图。
     */
    private void setBackground(NoteItemData data) {
        int id = data.getBgColorId();
        if (data.getType() == Notes.TYPE_NOTE) {
            if (data.isSingle() || data.isOneFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项绑定的 NoteItemData 数据对象。
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}
