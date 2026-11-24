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

package net.micode.notes.model;

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.HistoryColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.HistoryPolicy;
import net.micode.notes.tool.ResourceParser.NoteBgResources;


/**
 * 当前正在编辑的活动便签业务模型类。
 * 在底层 Note 类之上封装了便签的完整运行时状态（正文、清单模式、提醒时间、背景色、
 * 桌面小部件绑定、所属目录及删除标记），提供静态工厂方法用于新建空便签或从数据库加载已有便签，
 * 并通过观察者接口 NoteSettingChangedListener 将设置变化实时通知 UI 层（NoteEditActivity）。
 */
public class WorkingNote {
    // Note for the working note
    private Note mNote;
    // Note Id
    private long mNoteId;
    // Note content
    private String mContent;
    // Note mode
    private int mMode;

    // 闹钟提醒时间戳（毫秒，0 表示无提醒）
    private long mAlertDate;

    // 最后一次修改时间戳（毫秒）
    private long mModifiedDate;

    // 当前便签背景颜色 ID
    private int mBgColorId;

    // 关联的桌面小部件实例 ID
    private int mWidgetId;

    // 关联的桌面小部件尺寸类型（2x2 / 4x4 / 无效）
    private int mWidgetType;

    // 便签所属父文件夹 ID
    private long mFolderId;

    private Context mContext;

    private static final String TAG = "WorkingNote";

    // 标记当前便签是否已被用户删除（若已删除则退出编辑时不再保存）
    private boolean mIsDeleted;

    private int mPinned;

    private String mPassword;

    // 便签属性设置变更回调监听器（观察者模式）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    // 查询 data 表时使用的字段投影数组
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,
            DataColumns.CONTENT,
            DataColumns.MIME_TYPE,
            DataColumns.DATA1,
            DataColumns.DATA2,
            DataColumns.DATA3,
            DataColumns.DATA4,
    };

    // 查询 note 表时使用的字段投影数组
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,
            NoteColumns.ALERTED_DATE,
            NoteColumns.BG_COLOR_ID,
            NoteColumns.WIDGET_ID,
            NoteColumns.WIDGET_TYPE,
            NoteColumns.MODIFIED_DATE,
            NoteColumns.PINNED,
            NoteColumns.PASSWORD
    };

    // DATA_PROJECTION 与 NOTE_PROJECTION 各字段对应的游标列索引常量
    private static final int DATA_ID_COLUMN = 0;

    private static final int DATA_CONTENT_COLUMN = 1;

    private static final int DATA_MIME_TYPE_COLUMN = 2;

    private static final int DATA_MODE_COLUMN = 3;

    private static final int NOTE_PARENT_ID_COLUMN = 0;

    private static final int NOTE_ALERTED_DATE_COLUMN = 1;

    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;

    private static final int NOTE_WIDGET_ID_COLUMN = 3;

    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;

    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;

    private static final int NOTE_PINNED_COLUMN = 6;

    private static final int NOTE_PASSWORD_COLUMN = 7;

    // New note construct
    /**
     * 私有构造方法：用于在指定文件夹下创建一条尚未落库的新便签对象。
     *
     * @param context  上下文对象
     * @param folderId 目标父文件夹 ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mPinned = 0;
        mPassword = "";
    }

    // Existing note construct
    /**
     * 私有构造方法：用于根据已有便签 ID 从数据库加载完整的便签信息。
     *
     * @param context  上下文对象
     * @param noteId   已有便签的主键 ID
     * @param folderId 初始父文件夹 ID（加载后会被数据库实际值覆盖）
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();
    }

    /**
     * 从 note 表查询并填充当前便签的元数据（父目录、背景色、小部件、提醒时间、修改时间），
     * 随后调用 loadNoteData() 继续加载便签正文及扩展数据。
     */
    private void loadNote() {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId), NOTE_PROJECTION, null,
                null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
                mPinned = cursor.getInt(NOTE_PINNED_COLUMN);
                mPassword = cursor.getString(NOTE_PASSWORD_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();
    }

    /**
     * 从 data 表查询当前便签关联的所有数据行，按 MIME 类型区分加载文本正文/清单模式或通话记录 ID。
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI, DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?", new String[] {
                        String.valueOf(mNoteId)
                }, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 静态工厂方法：创建一个初始化了背景色和小部件绑定信息的空白工作便签（延迟到保存时才写入数据库）。
     *
     * @param context          上下文对象
     * @param folderId         所属父文件夹 ID
     * @param widgetId         关联桌面小部件 ID（无关联传 INVALID_APPWIDGET_ID）
     * @param widgetType       关联桌面小部件类型
     * @param defaultBgColorId 默认背景颜色 ID
     * @return 新创建的 WorkingNote 实例
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 静态工厂方法：根据便签主键 ID 从数据库加载一条已存在的便签。
     *
     * @param context 上下文对象
     * @param id      便签主键 ID
     * @return 加载完成的 WorkingNote 实例
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 线程安全地保存当前工作便签到数据库。
     * 若便签有保存价值：新便签先申请数据库 ID，再同步增量数据，最后通知关联的桌面小部件刷新。
     *
     * @return 实际执行保存并成功返回 true；无需保存或创建 ID 失败返回 false
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            // 本次维护新增：在覆盖旧正文之前，先把数据库中的现存正文归档为一条历史版本
            recordHistory();
            if (!existInDatabase()) {
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            mNote.syncNote(mContext, mNoteId);

            /**
             * Update widget content if there exist any widget of this note
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 本次维护新增：将数据库中被覆盖前的正文归档为一条历史版本记录。
     * 归档前做两重去重：①旧正文与新正文完全相同（例如仅修改背景色）时无需归档；
     * ②旧正文与最近一条历史版本相同（连续多次保存未改动正文）时不再重复归档。
     * 归档条数上限与过期清理由 NotesProvider 在写入时统一执行。
     */
    private void recordHistory() {
        if (!existInDatabase() || TextUtils.isEmpty(mContent)) {
            return;
        }
        String previousContent = loadStoredContent();
        if (!HistoryPolicy.shouldRecord(previousContent, mContent)) {
            return;
        }
        String newestHistory = loadNewestHistoryContent();
        // 第二重去重：旧正文与最近一条历史版本完全相同时不再重复归档；
        // 注意此处比较的是“旧正文”与“已有历史”，不能复用 shouldRecord(旧正文, 新正文) 的判断口径
        if (!TextUtils.isEmpty(newestHistory) && TextUtils.equals(newestHistory, previousContent)) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(HistoryColumns.NOTE_ID, mNoteId);
        values.put(HistoryColumns.CONTENT, previousContent);
        values.put(HistoryColumns.SNIPPET, HistoryPolicy.makeSnippet(previousContent,
                HistoryPolicy.SNIPPET_MAX_LENGTH));
        values.put(HistoryColumns.SAVED_DATE, System.currentTimeMillis());
        try {
            mContext.getContentResolver().insert(Notes.CONTENT_HISTORY_URI, values);
        } catch (Exception e) {
            // 历史归档属于增强能力，写入失败不能阻断便签本身的保存流程
            Log.e(TAG, "Record note history failed:" + e.toString());
        }
    }

    /**
     * 读取 data 表中当前便签已持久化的正文内容。
     *
     * @return 数据库现存正文；不存在时返回空串
     */
    private String loadStoredContent() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_DATA_URI,
                new String[] { DataColumns.CONTENT },
                DataColumns.NOTE_ID + "=? AND " + DataColumns.MIME_TYPE + "=?",
                new String[] { String.valueOf(mNoteId), DataConstants.NOTE }, null);
        String content = "";
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    content = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return content == null ? "" : content;
    }

    /**
     * 读取该便签最近一条历史版本的正文，用于避免重复归档相同内容。
     *
     * @return 最近一条历史正文；无历史时返回空串
     */
    private String loadNewestHistoryContent() {
        Cursor cursor = mContext.getContentResolver().query(Notes.CONTENT_HISTORY_URI,
                new String[] { HistoryColumns.CONTENT }, HistoryColumns.NOTE_ID + "=?",
                new String[] { String.valueOf(mNoteId) },
                HistoryColumns.SAVED_DATE + " DESC," + HistoryColumns.ID + " DESC");
        String content = "";
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    content = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }
        return content == null ? "" : content;
    }

    /**
     * 判断当前便签是否已经在数据库中分配了有效主键 ID（mNoteId > 0）。
     *
     * @return 已存在于数据库返回 true，否则返回 false
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断当前便签是否值得写入数据库：已标记删除、未落库且内容为空、或已落库但无任何修改时均无需保存。
     *
     * @return 需要保存返回 true，否则返回 false
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 注册便签属性设置变更监听器。
     *
     * @param l 实现了 NoteSettingChangedListener 的监听对象
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置或取消便签的定时闹钟提醒时间，并回调通知监听器注册或取消系统闹钟。
     *
     * @param date 提醒时间戳（毫秒，取消时传 0）
     * @param set  true 表示设置提醒，false 表示取消提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记当前便签是否被删除；若绑定了有效桌面小部件则同步触发小部件视图更新。
     *
     * @param mark true 表示标记为已删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 修改当前便签的背景颜色 ID，记录差异并通知 UI 层即时切换背景皮肤。
     *
     * @param id 新的背景颜色 ID
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 切换便签编辑模式（普通文本模式 0 / 清单模式 MODE_CHECK_LIST），并通知监听器更新编辑视图。
     *
     * @param mode 目标编辑模式常量
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 更新便签关联的桌面小部件尺寸类型。
     *
     * @param type 小部件类型常量
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 更新便签关联的桌面小部件实例 ID。
     *
     * @param id AppWidget 实例 ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 更新当前便签的正文文本内容；仅当新旧文本不同时才写入待同步缓存。
     *
     * @param text 最新编辑的便签文本
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将当前便签转换为通话记录便签，写入通话时间与号码，并将其归入系统通话便签文件夹。
     *
     * @param phoneNumber 通话电话号码
     * @param callDate    通话发生时间戳（毫秒）
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    /**
     * 判断当前便签是否设置了闹钟提醒（提醒时间戳大于 0）。
     */
    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    /**
     * 获取当前便签的正文内容。
     */
    public String getContent() {
        return mContent;
    }

    /**
     * 获取当前便签的闹钟提醒时间戳。
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取当前便签的最近修改时间戳。
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取当前背景色对应的编辑区背景图片资源 ID。
     */
    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    /**
     * 获取当前便签的背景颜色索引 ID。
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取当前背景色对应的编辑页标题栏背景图片资源 ID。
     */
    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    /**
     * 获取当前便签的清单模式状态值。
     */
    public int getCheckListMode() {
        return mMode;
    }

    /**
     * 获取当前便签在数据库中的主键 ID。
     */
    public long getNoteId() {
        return mNoteId;
    }

    /**
     * 获取当前便签所属父文件夹 ID。
     */
    public long getFolderId() {
        return mFolderId;
    }

    /**
     * 获取当前便签关联的桌面小部件 ID。
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取当前便签关联的桌面小部件尺寸类型。
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * Get whether the note is pinned to top
     * @return 1 if pinned, 0 if not pinned
     */
    public int getPinned() {
        return mPinned;
    }

    /**
     * Set the pinned state of the note
     * @param pinned 1 to pin, 0 to unpin
     */
    public void setPinned(int pinned) {
        if (pinned != mPinned) {
            mPinned = pinned;
            mNote.setNoteValue(NoteColumns.PINNED, String.valueOf(mPinned));
        }
    }

    /**
     * Check if the note has a password lock
     * @return true if password is set
     */
    public boolean hasPassword() {
        return !TextUtils.isEmpty(mPassword);
    }

    /**
     * Get the password hash of the note
     * @return password hash string, empty if no password
     */
    public String getPassword() {
        return mPassword;
    }

    /**
     * Set or remove the password lock for this note
     * @param password password hash to set, empty string to remove lock
     */
    public void setPassword(String password) {
        mPassword = password != null ? password : "";
        mNote.setNoteValue(NoteColumns.PASSWORD, mPassword);
    }

    /**
     * 便签设置状态变化回调接口，供编辑界面监听背景色、闹钟提醒、小部件及清单模式的变化。
     */
    public interface NoteSettingChangedListener {
        /**
         * Called when the background color of current note has just changed
         */
        void onBackgroundColorChanged();

        /**
         * Called when user set clock
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * Call when user create note from widget
         */
        void onWidgetChanged();

        /**
         * Call when switch between check list mode and normal mode
         * @param oldMode is previous mode before change
         * @param newMode is new mode
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}
