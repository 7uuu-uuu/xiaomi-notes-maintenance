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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;


/**
 * 便签数据库通用操作工具类。
 * 提供一组静态辅助方法，封装批量删除便签、单条/批量移动便签至文件夹、统计用户文件夹数、
 * 检查记录或文件夹名存在性、查询文件夹关联桌面小部件、查询通话便签号码以及格式化摘要等高频操作。
 */
public class DataUtils {
    public static final String TAG = "DataUtils";
    /**
     * 根据便签/文件夹 ID 集合批量从数据库中永久删除记录（自动跳过系统根文件夹）。
     *
     * @param resolver ContentResolver 实例
     * @param ids      待删除的记录主键 ID 集合
     * @return 批量删除成功（或集合为空）返回 true，失败返回 false
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 安全保护：禁止删除系统默认根文件夹
            if(id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }
        try {
            // 通过 ContentProviderOperation 批量提交所有删除操作，减少跨进程/事务开销
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将单条便签从源文件夹移动到目标文件夹，同时记录原父目录 ID 并置位本地已修改标志。
     *
     * @param resolver    ContentResolver 实例
     * @param id          待移动的便签 ID
     * @param srcFolderId 移动前的源文件夹 ID（写入 origin_parent_id）
     * @param desFolderId 移动后的目标文件夹 ID（写入 parent_id）
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PARENT_ID, desFolderId);
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 将多条选中的便签批量移动到指定目标文件夹，并统一标记为本地已修改。
     *
     * @param resolver ContentResolver 实例
     * @param ids      待移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return 批量更新成功返回 true，异常或失败返回 false
     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
            long folderId) {
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * Get the all folder count except system folders {@link Notes#TYPE_SYSTEM}}
     */
    /**
     * 统计当前数据库中未处于回收站的用户自定义文件夹（TYPE_FOLDER）总数量。
     *
     * @param resolver ContentResolver 实例
     * @return 可见用户文件夹数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        Cursor cursor =resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { "COUNT(*)" },
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                null);

        int count = 0;
        if(cursor != null) {
            if(cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查指定 ID 和类型的记录是否在 note 表中处于可见状态（存在且不在回收站中）。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   待检查的记录 ID
     * @param type     期望的节点类型（如 TYPE_NOTE 或 TYPE_FOLDER）
     * @return 可见存在返回 true，否则返回 false
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String [] {String.valueOf(type)},
                null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定主键 ID 的记录是否存在于 note 表中（包含回收站中的记录）。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签或文件夹 ID
     * @return 存在返回 true，否则返回 false
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定数据行主键 ID 是否存在于 data 内容表中。
     *
     * @param resolver ContentResolver 实例
     * @param dataId   data 表记录 ID
     * @return 存在返回 true，否则返回 false
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定名称的用户文件夹是否已存在（排除已移入回收站的文件夹），用于新建/重命名文件夹防重名校验。
     *
     * @param resolver ContentResolver 实例
     * @param name     待校验的文件夹名称
     * @return 已存在同名可见文件夹返回 true，否则返回 false
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        boolean exist = false;
        if(cursor != null) {
            if(cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 查询指定文件夹下所有便签绑定的桌面小部件属性集合，用于删除文件夹后同步刷新桌面小部件。
     *
     * @param resolver ContentResolver 实例
     * @param folderId 目标文件夹 ID
     * @return 该文件夹内便签关联的 AppWidgetAttribute 集合；若无记录则返回 null
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?",
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);
                        widget.widgetType = c.getInt(1);
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

    /**
     * 根据通话便签的 noteId 查询其关联的电话号码字符串。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   通话便签的主键 ID
     * @return 对应的电话号码；未查到或异常时返回空字符串
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.PHONE_NUMBER },
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String [] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话时间戳反查对应的通话便签主键 ID，避免重复创建同一通电话的便签。
     *
     * @param resolver    ContentResolver 实例
     * @param phoneNumber 通话电话号码
     * @param callDate    通话发生时间戳
     * @return 匹配到的便签 ID；若不存在则返回 0
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String [] { CallNote.NOTE_ID },
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                + CallNote.PHONE_NUMBER + ",?)",
                new String [] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签主键 ID 查询 note 表中的 snippet 内容摘要（用于闹钟弹窗等场景展示）。
     *
     * @param resolver ContentResolver 实例
     * @param noteId   便签主键 ID
     * @return 便签摘要字符串
     * @throws IllegalArgumentException 当查询游标为 null 时抛出
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String [] { NoteColumns.SNIPPET },
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化便签摘要文本：去除首尾空白字符，并仅截取第一行内容用于列表单行标题展示。
     *
     * @param snippet 原始便签摘要字符串
     * @return 单行精简后的摘要字符串
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim();
            int index = snippet.indexOf('\n');
            if (index != -1) {
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}
