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
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;


/**
 * 便签列表单项视图数据模型类。
 * 负责从 note 表查询游标单行提取便签或文件夹的展示属性，自动清理清单模式标记符，
 * 对通话便签关联查询电话号码与通讯录联系人姓名，并计算该项在列表中的首/中/末/紧随文件夹位置状态。
 */
public class NoteItemData {
    // 便签主列表查询 note 表时使用的 13 列字段投影数组及下方对应的游标列索引常量
    static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.ALERTED_DATE,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.CREATED_DATE,
        NoteColumns.HAS_ATTACHMENT,
        NoteColumns.MODIFIED_DATE,
        NoteColumns.NOTES_COUNT,
        NoteColumns.PARENT_ID,
        NoteColumns.SNIPPET,
        NoteColumns.TYPE,
        NoteColumns.WIDGET_ID,
        NoteColumns.WIDGET_TYPE,
        NoteColumns.DELETED_DATE,
        // 本次维护新增：置顶标记与标签，用于列表排序与展示
        NoteColumns.PINNED,
        NoteColumns.TAG_LABEL,
            NoteColumns.ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE,
            NoteColumns.HAS_ATTACHMENT,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT,
            NoteColumns.PARENT_ID,
            NoteColumns.SNIPPET,
            NoteColumns.TYPE,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.PINNED,
            NoteColumns.PASSWORD,
    };

    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;
    private static final int PINNED_COLUMN                = 12;
    private static final int PASSWORD_COLUMN              = 13;
    private static final int DELETED_DATE_COLUMN          = 12;
    // 本次维护新增：置顶标记与标签在投影中的列索引
    private static final int PINNED_COLUMN                = 13;
    private static final int TAG_COLUMN                   = 14;

    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private boolean mHasAttachment;
    private long mModifiedDate;
    private int mNotesCount;
    private long mParentId;
    private String mSnippet;
    private int mType;
    private int mWidgetId;
    private int mWidgetType;
    private long mDeletedDate;
    private String mName;
    private String mPhoneNumber;
    // 本次维护新增：是否置顶、所属标签
    private boolean mPinned;
    private String mTag;

    // 列表项相对位置状态标志（末项、首项、唯一项、文件夹后唯一便签、文件夹后首条多便签），用于选择圆角拼接背景
    private boolean mIsLastItem;
    private boolean mIsFirstItem;
    private boolean mIsOnlyOneItem;
    private boolean mIsOneNoteFollowingFolder;
    private boolean mIsMultiNotesFollowingFolder;
    private int mPinned;
    private boolean mHasPassword;

    /**
     * 从当前游标行构造列表项数据对象：解析基本属性、去除清单勾选标签、解析通话联系人，并计算位置标志。
     *
     * @param context 上下文对象
     * @param cursor  指向当前列表项记录的 Cursor 游标
     */
    public NoteItemData(Context context, Cursor cursor) {
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);
        mDeletedDate = cursor.getLong(DELETED_DATE_COLUMN);
        // 本次维护新增：读取置顶标记与标签
        mPinned = cursor.getInt(PINNED_COLUMN) > 0;
        String tag = cursor.getString(TAG_COLUMN);
        mTag = tag == null ? "" : tag;
        mPinned = cursor.getInt(PINNED_COLUMN);
        String passwordStr = cursor.getString(PASSWORD_COLUMN);
        mHasPassword = !TextUtils.isEmpty(passwordStr);

        mPhoneNumber = "";
        // 若属于通话便签目录，进一步查出电话号码并尝试匹配系统通讯录姓名（未匹配则直接显示号码）
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }

    /**
     * 检查当前记录在 Cursor 中的前后相邻关系，判断其是否为首项、末项、独立单项或紧跟在文件夹后的第一条便签。
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // 是否为排在文件夹后面的唯一一条便签
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    // 是否为排在文件夹后面的多条便签中的第一条
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    // 是否为整个列表的最后一项
    public boolean isLast() {
        return mIsLastItem;
    }

    // 获取通话便签显示的联系人姓名（或电话号码）
    public String getCallName() {
        return mName;
    }

    // 是否为整个列表的第一项
    public boolean isFirst() {
        return mIsFirstItem;
    }

    // 是否为整个列表中的唯一一项
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    // 获取记录主键 ID
    public long getId() {
        return mId;
    }

    // 获取定时提醒时间戳
    public long getAlertDate() {
        return mAlertDate;
    }

    // 获取创建时间戳
    public long getCreatedDate() {
        return mCreatedDate;
    }

    // 是否包含附件
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    // 获取最后修改时间戳
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 本次维护新增：获取便签进入回收站的时间戳（毫秒），0 表示不在回收站中。
     */
    public long getDeletedDate() {
        return mDeletedDate;
    }

    /**
     * 本次维护新增：判断该便签当前是否位于回收站目录中。
     */
    public boolean isInTrash() {
        return mParentId == Notes.ID_TRASH_FOLER;
    }

    // 获取便签背景颜色编号
    public int getBgColorId() {
        return mBgColorId;
    }

    // 获取父文件夹 ID
    public long getParentId() {
        return mParentId;
    }

    // 获取文件夹内包含的便签数量
    public int getNotesCount() {
        return mNotesCount;
    }

    // 获取所属文件夹 ID（同 getParentId）
    public long getFolderId () {
        return mParentId;
    }

    // 获取节点类型（便签/文件夹/系统目录）
    public int getType() {
        return mType;
    }

    // 获取关联的桌面小部件尺寸类型
    public int getWidgetType() {
        return mWidgetType;
    }

    // 获取关联的桌面小部件实例 ID
    public int getWidgetId() {
        return mWidgetId;
    }

    // 获取去除清单标记后的便签摘要或文件夹名
    public String getSnippet() {
        return mSnippet;
    }

    // 判断该便签是否设置了有效闹钟提醒
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 本次维护新增：判断该便签是否已置顶。
     */
    public boolean isPinned() {
        return mPinned;
    }

    /**
     * 本次维护新增：获取便签标签，未打标签时返回空串。
     */
    public String getTag() {
        return mTag;
    }

    // 判断该记录是否为有效的通话便签记录
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    // 静态工具方法：直接从游标当前行读取节点类型列
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }

    /**
     * Check if this note is pinned to the top
     * @return true if pinned
     */
    public boolean isPinned() {
        return mPinned == 1;
    }

    /**
     * Get the pinned value
     * @return 1 if pinned, 0 if not
     */
    public int getPinned() {
        return mPinned;
    }

    /**
     * Check if this note has a password lock
     * @return true if password protected
     */
    public boolean hasPassword() {
        return mHasPassword;
    }
}
