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
import android.text.TextUtils;
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
     * 本次维护新增：将多条便签软删除到回收站。
     * 不直接从数据库移除记录，而是把 parent_id 改写为回收站目录，并记录便签删除前的真实位置
     * （origin_parent_id），删除时间戳由数据库触发器自动写入，从而支持后续恢复与过期清理。
     *
     * @param resolver ContentResolver 实例
     * @param ids      待移入回收站的便签 ID 集合
     * @return 全部操作成功返回 true，集合为空直接返回 true
     */
    public static boolean moveNotesToTrash(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return true;
        }
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            // 系统文件夹（ID<=0）不允许移入回收站，避免破坏内置目录
            if (id <= Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't move system folder to trash, id:" + id);
                continue;
            }
            long[] state = queryParentState(resolver, id);
            long parentId = state[0];
            long originParentId = state[1];
            // 已在回收站中的便签无需重复移动，避免反复刷新删除时间
            if (parentId == Notes.ID_TRASH_FOLER) {
                continue;
            }
            // 记录删除前的真实位置：优先用当前父目录；若当前在临时目录中，则沿用此前记录的原始目录
            long origin = parentId;
            if (parentId == Notes.ID_TEMPARAY_FOLDER && originParentId != 0
                    && originParentId != Notes.ID_TRASH_FOLER) {
                origin = originParentId;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, Notes.ID_TRASH_FOLER);
            builder.withValue(NoteColumns.ORIGIN_PARENT_ID, origin);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }
        return applyNoteOperations(resolver, operationList, "move notes to trash");
    }

    /**
     * 本次维护新增：从回收站恢复多条便签，使其回到删除前所在的文件夹。
     * 恢复目标取 origin_parent_id；若原始位置不可用（为空、仍指向回收站或临时目录），
     * 统一回落到根目录，保证恢复操作不会把便签留在异常目录中。
     *
     * @param resolver ContentResolver 实例
     * @param ids      待恢复的便签 ID 集合
     * @return 全部操作成功返回 true，集合为空直接返回 true
     */
    public static boolean restoreNotes(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return true;
        }
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        for (long id : ids) {
            if (id <= Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't restore system folder, id:" + id);
                continue;
            }
            long[] state = queryParentState(resolver, id);
            long parentId = state[0];
            long originParentId = state[1];
            // 仅在回收站中的便签执行恢复
            if (parentId != Notes.ID_TRASH_FOLER) {
                continue;
            }
            long destination = originParentId;
            if (destination == Notes.ID_TRASH_FOLER
                    || destination == Notes.ID_TEMPARAY_FOLDER
                    || destination < Notes.ID_CALL_RECORD_FOLDER) {
                destination = Notes.ID_ROOT_FOLDER;
            }
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, destination);
            builder.withValue(NoteColumns.ORIGIN_PARENT_ID, 0);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }
        return applyNoteOperations(resolver, operationList, "restore notes from trash");
    }

    /**
     * 本次维护新增：清理回收站中超过保留期的便签，执行物理删除。
     * 删除动作会由数据库触发器级联清除便签正文与历史版本，因此无需另行清理从表。
     *
     * @param resolver ContentResolver 实例
     * @return 实际被永久删除的便签条数
     */
    public static int purgeExpiredTrash(ContentResolver resolver) {
        long now = System.currentTimeMillis();
        long boundary = now - TrashPolicy.RETENTION_DAYS * TrashPolicy.MILLIS_PER_DAY;
        String selection = NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER
                + " AND " + NoteColumns.DELETED_DATE + ">0"
                + " AND " + NoteColumns.DELETED_DATE + "<" + boundary;
        return resolver.delete(Notes.CONTENT_NOTE_URI, selection, null);
    }

    /**
     * 本次维护新增：统计当前回收站中的便签数量，用于界面提示与触发清理。
     *
     * @param resolver ContentResolver 实例
     * @return 回收站内便签数量
     */
    public static int getTrashNoteCount(ContentResolver resolver) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, new String[] { NoteColumns.ID },
                NoteColumns.PARENT_ID + "=? AND " + NoteColumns.TYPE + "=?",
                new String[] { String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(Notes.TYPE_NOTE) }, null);
        int count = 0;
        if (cursor != null) {
            try {
                count = cursor.getCount();
            } finally {
                cursor.close();
            }
        }
        return count;
    }

    /**
     * 读取便签当前的父目录与已记录的原始父目录。
     *
     * @param resolver ContentResolver 实例
     * @param id       便签 ID
     * @return 长度为 2 的数组，依次为当前 parent_id 与 origin_parent_id；查询失败返回 {0, 0}
     */
    private static long[] queryParentState(ContentResolver resolver, long id) {
        long[] state = new long[] { 0, 0 };
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                new String[] { NoteColumns.PARENT_ID, NoteColumns.ORIGIN_PARENT_ID }, null, null,
                null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    state[0] = cursor.getLong(0);
                    state[1] = cursor.getLong(1);
                }
            } finally {
                cursor.close();
            }
        }
        return state;
    }

    /**
     * 批量提交便签更新操作，统一处理空批次与异常日志。
     *
     * @param resolver      ContentResolver 实例
     * @param operationList 待提交的操作列表
     * @param action        日志中描述本次动作的标识
     * @return 提交成功返回 true，异常或结果为空返回 false
     */
    private static boolean applyNoteOperations(ContentResolver resolver,
            ArrayList<ContentProviderOperation> operationList, String action) {
        if (operationList.size() == 0) {
            return true;
        }
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, action + " failed");
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
        // 本次维护补充：空集合直接返回成功，避免对空操作列表执行无意义的 applyBatch
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
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
     * 本次维护新增：批量设置/取消便签置顶。
     * 仅修改 pinned 标记，不触发内容同步逻辑，因此无需标记 local_modified。
     *
     * @param resolver ContentResolver 实例
     * @param ids      待处理的便签 ID 集合
     * @param pinned   true 置顶，false 取消置顶
     * @return 批量更新成功返回 true，异常或失败返回 false
     */
    public static boolean setNotesPinned(ContentResolver resolver, HashSet<Long> ids,
            boolean pinned) {
        if (ids == null || ids.size() == 0) {
            return true;
        }
        ContentValues values = new ContentValues();
        values.put(NoteColumns.PINNED, pinned ? 1 : 0);
        int updated = 0;
        for (long id : ids) {
            // 只对普通便签生效：先确认类型再更新，避免误改系统文件夹
            if (visibleInNoteDatabase(resolver, id, Notes.TYPE_NOTE)) {
                updated += resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                        values, null, null);
            }
        }
        return updated > 0;
    }

    /**
     * 本次维护新增：判断给定的便签是否全部已置顶（用于在批量菜单里决定显示“置顶”还是“取消置顶”）。
     */
    public static boolean areAllNotesPinned(ContentResolver resolver, HashSet<Long> ids) {
        if (ids == null || ids.size() == 0) {
            return false;
        }
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, new String[] { "COUNT(*)" },
                NoteColumns.PINNED + "=1 AND " + NoteColumns.ID + " IN " + buildIdList(ids),
                null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0) == ids.size();
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    /**
     * 本次维护新增：把 ID 集合拼成 SQL 的 (1,2,3) 形式，供 IN 条件使用。
     */
    private static String buildIdList(HashSet<Long> ids) {
        StringBuilder sb = new StringBuilder("(");
        boolean first = true;
        for (Long id : ids) {
            if (!first) {
                sb.append(",");
            }
            sb.append(id.longValue());
            first = false;
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 本次维护新增：为单条便签设置标签，空串表示清除标签。
     *
     * @param resolver ContentResolver 实例
     * @param id       便签 ID
     * @param tag      标签文本（已由调用方去除首尾空白）
     * @return 是否更新成功
     */
    public static boolean setNoteTag(ContentResolver resolver, long id, String tag) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.TAG_LABEL, tag == null ? "" : tag);
        return resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values,
                null, null) > 0;
    }

    /**
     * 本次维护新增：读取当前便签的标签，未设置时返回空串。
     */
    public static String getNoteTag(ContentResolver resolver, long id) {
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                new String[] { NoteColumns.TAG_LABEL }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    String tag = cursor.getString(0);
                    return tag == null ? "" : tag;
                }
            } finally {
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 本次维护新增：读取全部已使用的标签（去重、按字母序），供标签筛选对话框使用。
     */
    public static ArrayList<String> getAllTags(ContentResolver resolver) {
        ArrayList<String> tags = new ArrayList<String>();
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.TAG_LABEL },
                NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND " + NoteColumns.TAG_LABEL
                        + "<>''",
                null, NoteColumns.TAG_LABEL + " ASC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String tag = cursor.getString(0);
                    if (!TextUtils.isEmpty(tag) && !tags.contains(tag)) {
                        tags.add(tag);
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return tags;
    }

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
     * 本次维护补充空值保护：入参为 null 时返回空字符串，避免下游调用方对 null 执行 trim、
     * length 等操作引发空指针异常。
     *
     * @param snippet 原始便签摘要字符串
     * @return 单行精简后的摘要字符串；入参为 null 时返回空串
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet == null) {
            return "";
        }
        snippet = snippet.trim();
        // 本次维护新增：正文里的图片标记不进入列表摘要，统一替换为“[图片]”占位
        snippet = NoteImageUtils.stripMarkers(snippet).trim();
        int index = snippet.indexOf('\n');
        if (index != -1) {
            snippet = snippet.substring(0, index);
        }
        return snippet;
    }
}
