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

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;


/**
 * 小米便签内容提供者（ContentProvider），对外封装本地 SQLite 数据库的增删改查接口。
 * 支持对 note 表、data 表的集合与单条记录操作，同时提供关键字模糊搜索与系统搜索建议
 * （SearchManager）查询能力，并在数据变更时通过 ContentResolver 通知观察者刷新界面。
 */
public class NotesProvider extends ContentProvider {
    // URI 路由匹配器，用于解析外部传入的 Content URI 类型
    private static final UriMatcher mMatcher;

    // 底层 SQLite 数据库管理辅助类实例
    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // URI 匹配码常量：1-note 全表，2-单条 note，3-data 全表，4-单条 data
    private static final int URI_NOTE            = 1;
    private static final int URI_NOTE_ITEM       = 2;
    private static final int URI_DATA            = 3;
    private static final int URI_DATA_ITEM       = 4;

    // URI 匹配码常量：5-便签关键字搜索，6-系统搜索框实时联想建议
    private static final int URI_SEARCH          = 5;
    private static final int URI_SEARCH_SUGGEST  = 6;

    // 静态初始化代码块：注册 NotesProvider 支持的所有 URI 路径匹配规则
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST);
    }

    /**
     * x'0A' represents the '\n' character in sqlite. For title and content in the search result,
     * we will trim '\n' and white space in order to show more information.
     */
    // 搜索建议查询列投影：去除摘要中的换行符(x'0A')和首尾空白，映射为 SearchManager 标准列
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    // 便签内容模糊搜索 SQL：在非回收站的普通便签中按 LIKE 匹配 snippet 字段
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    @Override
    /**
     * 初始化 ContentProvider，获取 NotesDatabaseHelper 数据库单例。
     *
     * @return 初始化成功始终返回 true
     */
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    /**
     * 根据 URI 执行数据库查询操作，支持便签/文件夹查询、便签详情数据查询及全文摘要搜索。
     *
     * @param uri           查询目标 URI
     * @param projection    需要返回的列名数组
     * @param selection     WHERE 过滤条件
     * @param selectionArgs WHERE 占位符参数数组
     * @param sortOrder     排序规则字符串
     * @return 查询结果 Cursor 游标（已绑定 URI 变更通知）；无匹配搜索词时返回 null
     */
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;
        switch (mMatcher.match(uri)) {
            // 查询 note 表集合记录
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            // 按路径中的 ID 查询 note 表中的单条便签或文件夹记录
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            // 查询 data 表集合记录
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            // 按路径中的 ID 查询 data 表中的单条数据记录
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            // 处理便签内容搜索与搜索框下拉建议请求
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                // 搜索建议从 URI 路径段提取关键词；普通搜索从查询参数 pattern 提取关键词
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 构造前后通配符 %keyword% 执行 SQL LIKE 模糊查询
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 为结果游标注册内容观察通知 URI，底层数据变化时可自动通知 UI 刷新
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    @Override
    /**
     * 向 note 表或 data 表插入新记录，并触发对应的 ContentResolver 数据变更通知。
     *
     * @param uri    插入目标表 URI（仅支持 URI_NOTE 和 URI_DATA）
     * @param values 待插入的字段键值对集合
     * @return 包含新插入行主键 ID 的完整记录 URI
     */
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // Notify the note uri
        // 通知监听该 note 记录 URI 的观察者数据已发生变更
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // Notify the data uri
        // 通知监听该 data 记录 URI 的观察者数据已发生变更
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    @Override
    /**
     * 根据 URI 和条件删除 note 表或 data 表记录，内置保护机制防止系统文件夹（ID<=0）被误删。
     *
     * @param uri           删除目标 URI
     * @param selection     WHERE 过滤条件
     * @param selectionArgs WHERE 参数数组
     * @return 实际被删除的数据库记录行数
     */
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 强制附加 _id > 0 条件，防止批量删除时误删根目录或系统文件夹
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                /**
                 * ID that smaller than 0 is system folder which is not allowed to
                 * trash
                 */
                long noteId = Long.valueOf(id);
                // 单条删除时若 ID <= 0，说明是系统内置文件夹，直接跳过不允许删除
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 若有记录被删除，通知对应 URI（删除 data 时同步通知 note URI 刷新列表摘要）
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    @Override
    /**
     * 根据 URI 和条件更新 note 表或 data 表记录；更新 note 表前会自动递增其 version 版本号。
     *
     * @param uri           更新目标 URI
     * @param values        待更新的字段键值对
     * @param selection     WHERE 过滤条件
     * @param selectionArgs WHERE 参数数组
     * @return 实际受影响的数据库记录行数
     */
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 辅助拼接附加 WHERE 子句：若 selection 非空则包装为 " AND (selection)"，否则返回空串。
     *
     * @param selection 原始过滤条件字符串
     * @return 拼接后的 SQL 条件片段
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 将满足指定 ID 及过滤条件的 note 表记录的 version 版本号自增 1。
     *
     * @param id            目标便签 ID（传入 <=0 表示按 selection 批量递增）
     * @param selection     附加过滤条件
     * @param selectionArgs 过滤条件占位参数数组
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    @Override
    /**
     * 返回指定 URI 对应的 MIME 类型（当前未实现，默认返回 null）。
     *
     * @param uri 待查询的 URI
     * @return MIME 类型字符串
     */
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

}
