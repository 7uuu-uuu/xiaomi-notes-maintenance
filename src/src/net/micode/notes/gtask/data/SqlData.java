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

package net.micode.notes.gtask.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 云同步专用的本地 data 表数据行封装类。
 * 负责在 Google Task 同步过程中加载单条 data 表记录、与同步元数据 JSON 进行双向序列化/反序列化，
 * 记录字段增量变化（mDiffDataValues），并支持带乐观版本号校验地插入或更新回本地 SQLite 数据库。
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    // 表示尚未在本地数据库中分配有效主键的占位初始值
    private static final int INVALID_ID = -99999;

    // 同步读取 data 表时的列投影数组（ID、MIME 类型、正文、data1、data3）及对应列索引
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    public static final int DATA_ID_COLUMN = 0;

    public static final int DATA_MIME_TYPE_COLUMN = 1;

    public static final int DATA_CONTENT_COLUMN = 2;

    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;

    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    // 用于访问 NotesProvider 的 ContentResolver 对象
    private ContentResolver mContentResolver;

    // 标识该数据行是待新建插入（true）还是从数据库加载的已有记录（false）
    private boolean mIsCreate;

    // data 表记录主键 _id
    private long mDataId;

    // 数据项 MIME 类型（文本便签或通话便签）
    private String mDataMimeType;

    // 便签正文内容（或通话便签备注）
    private String mDataContent;

    // 扩展整型字段 data1（清单模式标志或通话时间戳）
    private long mDataContentData1;

    // 扩展文本字段 data3（通话便签电话号码）
    private String mDataContentData3;

    // 暂存待提交至数据库的增量修改字段集合
    private ContentValues mDiffDataValues;

    /**
     * 新建模式构造方法：创建一个尚未存在于本地数据库中的空白 SqlData 对象。
     *
     * @param context 上下文对象
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
     * 加载模式构造方法：从已有 data 表查询游标当前行初始化 SqlData 对象。
     *
     * @param context 上下文对象
     * @param c       指向目标 data 记录行的 Cursor 游标
     */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从 Cursor 当前行提取 ID、MIME 类型、正文、data1 及 data3 字段填充至成员变量。
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从云端同步下来的 data JSON 对象更新各字段值，并将发生变化的字段记入 mDiffDataValues。
     *
     * @param js 包含 data 表列键值的 JSONObject
     * @throws JSONException JSON 字段解析失败时抛出
     */
    public void setContent(JSONObject js) throws JSONException {
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 将当前已落库的 data 记录序列化为 JSONObject，以便封装进云端元数据上传。
     *
     * @return 包含当前数据行各列值的 JSONObject；若尚未落库则返回 null
     * @throws JSONException JSON 组装失败时抛出
     */
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * 将当前对象的增量变更提交到本地数据库 data 表。
     * 新建模式执行 insert；更新模式执行 update，可选校验所属便签的 version 版本号防止覆盖同步期间的用户编辑。
     *
     * @param noteId          所属便签的 note 表主键 ID
     * @param validateVersion 更新时是否启用 note 表版本号一致性校验
     * @param version         期望匹配的 note 表版本号
     */
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取当前 data 记录的主键 ID。
     */
    public long getId() {
        return mDataId;
    }
}
