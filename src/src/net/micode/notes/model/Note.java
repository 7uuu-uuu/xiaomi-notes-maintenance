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
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;


/**
 * 底层便签实体模型类。
 * 负责缓存单条便签在编辑过程中产生的 note 表增量变更（mNoteDiffValues）以及
 * data 表增量变更（内部类 NoteData），并在保存时通过 ContentResolver 同步写入数据库。
 */
public class Note {
    // 暂存 note 表待提交更新的差异字段键值对
    private ContentValues mNoteDiffValues;
    // 封装该便签关联的文本数据（TextNote）与通话数据（CallNote）的内部对象
    private NoteData mNoteData;
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     */
    /**
     * 线程安全地在数据库中创建一条空白便签记录，并返回新生成的便签主键 ID。
     *
     * @param context  上下文对象，用于访问 ContentResolver
     * @param folderId 新便签所属的父文件夹 ID
     * @return 新建便签的数据库主键 ID；若解析异常则返回 0
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        // 组装新便签初始元数据：创建时间、修改时间、普通便签类型、本地已修改标志及父目录 ID
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从插入返回的 content://micode_notes/note/{id} 中解析出新记录 ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 默认构造方法，初始化 note 表差异集合与 NoteData 内容数据容器。
     */
    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    /**
     * 设置 note 表的某个属性变更，同时自动标记本地已修改并刷新最后修改时间。
     *
     * @param key   note 表列名（NoteColumns 常量）
     * @param value 待设置的属性值字符串
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    /**
     * 设置便签关联的普通文本数据字段（如正文内容或清单模式）。
     *
     * @param key   data 表列名
     * @param value 字段值
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置该便签在 data 表中对应的文本数据行主键 ID。
     *
     * @param id 文本数据行 ID（必须大于 0）
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取该便签对应的文本数据行主键 ID。
     *
     * @return data 表中文本记录的主键 ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置该便签在 data 表中对应的通话记录数据行主键 ID。
     *
     * @param id 通话数据行 ID（必须大于 0）
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话便签的数据字段（如通话日期或电话号码）。
     *
     * @param key   CallNote 列名
     * @param value 字段值
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断当前便签对象在内存中是否存在尚未同步到数据库的本地修改。
     *
     * @return 若 note 表属性或 data 表内容有变更则返回 true，否则返回 false
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 将内存中缓存的 note 表属性变更和 data 表内容变更一并持久化写入本地数据库。
     *
     * @param context 上下文对象
     * @param noteId  目标便签主键 ID（必须大于 0）
     * @return 同步写入成功（或无需写入）返回 true；data 表写入失败返回 false
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
        }
        mNoteDiffValues.clear();

        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 便签内容数据内部封装类。
     * 分别管理文本数据（TextNote）与通话数据（CallNote）的记录 ID 及待提交字段，
     * 支持首次插入（insert）和基于 ContentProviderOperation 的批量更新（applyBatch）。
     */
    private class NoteData {
        // 文本数据记录在 data 表中的主键 ID（0 表示尚未插入数据库）
        private long mTextDataId;

        // 暂存文本数据待写入的字段键值对
        private ContentValues mTextDataValues;

        // 通话数据记录在 data 表中的主键 ID（0 表示尚未插入数据库）
        private long mCallDataId;

        // 暂存通话数据待写入的字段键值对
        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        /**
         * 构造 NoteData 实例，初始化文本与通话数据的缓存容器。
         */
        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 检查文本数据或通话数据是否存在未落库的本地改动。
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 绑定文本数据行 ID，校验 ID 合法性（须大于 0）。
         */
        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 绑定通话数据行 ID，校验 ID 合法性（须大于 0）。
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 记录通话数据变更，并联动标记外部 Note 对象的本地修改状态与修改时间。
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 记录文本数据变更，并联动标记外部 Note 对象的本地修改状态与修改时间。
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将暂存的文本数据和通话数据推送到 ContentProvider 进行持久化。
         * 新数据执行 insert 并回填数据行 ID；已有数据构造更新操作并通过 applyBatch 批量提交。
         *
         * @param context 上下文对象
         * @param noteId  所属便签 ID
         * @return 批量更新成功时返回便签 URI；仅插入或发生异常时返回 null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理普通文本便签数据的新增插入或增量更新
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // 处理通话记录便签数据的新增插入或增量更新
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 若存在待执行的更新操作，通过 ContentResolver.applyBatch 一次性批量提交
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}
