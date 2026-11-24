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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
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
    // 本次维护新增：置顶图钉与标签小条
    private ImageView mPinned;
    private TextView mTag;
    // 本次维护新增：搜索态下需要高亮的关键词
    private String mHighlightKeyword;
    // 本次维护新增：行内“更多操作”按钮及其点击回调
    private View mMenuButton;
    private OnClickListener mMenuClickListener;
    // 本次维护新增：双列网格紧凑排版标记
    private boolean mCompactMode;

    /**
     * 构造列表项视图：加载 R.layout.note_item 布局并初始化各个子控件引用。
     */
    public NotesListItem(Context context) {
        super(context);
        inflate(context, R.layout.note_item, this);
        // 本次维护新增：AbsListView 默认给子项 MATCH_PARENT 高度，双列网格下会把行高压扁导致卡片重叠，
        // 这里显式改为 WRAP_CONTENT，让行高跟随内容
        setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mAlert = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle = (TextView) findViewById(R.id.tv_title);
        mTime = (TextView) findViewById(R.id.tv_time);
        mCallName = (TextView) findViewById(R.id.tv_name);
        mCheckBox = (CheckBox) findViewById(android.R.id.checkbox);
        mPinned = (ImageView) findViewById(R.id.iv_pinned);
        mTag = (TextView) findViewById(R.id.tv_tag);
        // 本次维护新增：行内菜单按钮，点击事件交给列表 Activity 统一处理
        mMenuButton = findViewById(R.id.btn_item_menu);
        mMenuButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (mMenuClickListener != null) {
                    mMenuClickListener.onClick(v);
                }
            }
        });
    }

    /**
     * 本次维护新增：设置行内“更多操作”按钮的点击监听。
     */
    public void setItemMenuClickListener(OnClickListener listener) {
        mMenuClickListener = listener;
    }

    /**
     * 本次维护新增：设置双列网格紧凑排版，紧凑时隐藏相对时间并固定行高，
     * 避免 GridView 按较矮的同排条目压缩行高、导致卡片互相遮挡。
     */
    public void setCompactMode(boolean compact) {
        mCompactMode = compact;
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp instanceof AbsListView.LayoutParams) {
            ((AbsListView.LayoutParams) lp).height = compact
                    ? (int) (92 * getResources().getDisplayMetrics().density)
                    : ViewGroup.LayoutParams.WRAP_CONTENT;
            setLayoutParams(lp);
        }
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
        // 形态 0（本次维护新增）：回收站系统文件夹，显示“回收站(数量)”与删除图标
        if (data.getId() == Notes.ID_TRASH_FOLER) {
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.trash_folder_name)
                    + context.getString(R.string.format_folder_files_count, data.getNotesCount()));
            mAlert.setImageResource(R.drawable.ic_trash);
        // 形态 1：通话便签系统文件夹，显示“通话便签(数量)”和通话记录专属图标
        } else if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
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
                mAlert.setImageResource(R.drawable.ic_clock);
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
                // 本次美化调整：文件夹条目显示统一的线性文件夹图标
                mAlert.setVisibility(View.VISIBLE);
                mAlert.setImageResource(R.drawable.ic_folder);
            } else {
                // 本次维护新增：搜索结果中对命中的关键词做底色+改色高亮
                mTitle.setText(highlight(context, DataUtils.getFormattedSnippet(data.getSnippet())));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.ic_clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));
        // 本次维护新增：双列网格下半宽卡片空间有限，隐藏相对时间
        mTime.setVisibility(mCompactMode ? View.GONE : View.VISIBLE);

        // 本次维护新增：普通便签才显示置顶图钉与标签，文件夹/系统目录一律隐藏
        if (data.getType() == Notes.TYPE_NOTE) {
            // 本次维护新增：多选模式下隐藏行内菜单按钮，避免与复选框挤在一起
            mMenuButton.setVisibility(choiceMode ? View.GONE : View.VISIBLE);
            mPinned.setVisibility(data.isPinned() ? View.VISIBLE : View.GONE);
            String tag = data.getTag();
            if (tag != null && tag.length() > 0) {
                mTag.setText(tag);
                mTag.setVisibility(View.VISIBLE);
            } else {
                mTag.setVisibility(View.GONE);
            }
        } else {
            mMenuButton.setVisibility(View.GONE);
            mPinned.setVisibility(View.GONE);
            mTag.setVisibility(View.GONE);
        }

        setBackground(data);
    }

    /**
     * 本次维护新增：设置需要高亮的关键词，null 或空串表示不高亮。
     */
    public void setHighlightKeyword(String keyword) {
        mHighlightKeyword = keyword;
    }

    /**
     * 本次维护新增：把 text 中首个命中的关键词包装成带底色与配色的高亮文本（不区分大小写）。
     */
    private CharSequence highlight(Context context, CharSequence text) {
        if (TextUtils.isEmpty(mHighlightKeyword) || text == null) {
            return text;
        }
        String plain = text.toString();
        int start = plain.toLowerCase().indexOf(mHighlightKeyword.toLowerCase());
        if (start < 0) {
            return text;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder(plain);
        int end = start + mHighlightKeyword.length();
        builder.setSpan(new BackgroundColorSpan(0xFFD3F4EE), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(0xFF0F8C82), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
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
