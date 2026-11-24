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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.HistoryColumns;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 小米便签 SQLite 数据库管理辅助类（单例模式），继承自 SQLiteOpenHelper。
 * 负责创建和升级本地数据库 note.db（当前版本 V5），管理 note、data 与 note_history 三张核心表、
 * 索引、系统默认文件夹初始化，以及维护文件夹计数、摘要同步、级联删除、回收站时间标记与
 * 历史版本清理等 SQLite 触发器。
 */
public class NotesDatabaseHelper extends SQLiteOpenHelper {
    // SQLite 数据库文件名
    private static final String DB_NAME = "note.db";

    // 当前数据库结构版本号（V5：在 V4 基础上新增回收站时间字段与历史版本表）
    private static final int DB_VERSION = 6;

    /**
     * 数据库表名常量接口，包含 note（便签/文件夹主表）、data（便签内容从表）
     * 与 note_history（便签历史版本表）。
     */
    public interface TABLE {
        public static final String NOTE = "note";

        public static final String DATA = "data";
    }

    private static final String TAG = "NotesDatabaseHelper";

    // 数据库帮助类的全局单例实例
    private static NotesDatabaseHelper mInstance;

    // 创建 note 主表的 SQL 语句，定义主键、父目录、提醒、背景色、小部件、云同步等 18 个字段
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.DELETED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            // 本次维护新增：置顶标记（1 置顶 / 0 普通）与便签标签（空串表示未打标签）
            NoteColumns.PINNED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.TAG_LABEL + " TEXT NOT NULL DEFAULT ''" +
        ")";

    // 创建 data 内容表的 SQL 语句，存储 MIME 类型、所属便签 ID、正文内容及 data1~data5 扩展列
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";

    // 在 data 表的 note_id 外键列上建立索引，提升按便签 ID 检索正文的查询性能
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    // 创建 note_history 历史版本表的 SQL 语句：主键自增，按 note_id 归属便签，保存正文快照与归档时间
    private static final String CREATE_HISTORY_TABLE_SQL =
        "CREATE TABLE " + TABLE.HISTORY + "(" +
            HistoryColumns.ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            HistoryColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            HistoryColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            HistoryColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            HistoryColumns.SAVED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)" +
        ")";

    // 在历史版本表的 note_id 上建立索引，加速按便签查询版本链与按便签裁剪历史
    private static final String CREATE_HISTORY_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS history_note_id_index ON " +
        TABLE.HISTORY + "(" + HistoryColumns.NOTE_ID + ");";

    // 在历史版本表的归档时间上建立索引，加速按时间清理过期历史
    private static final String CREATE_HISTORY_SAVED_DATE_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS history_saved_date_index ON " +
        TABLE.HISTORY + "(" + HistoryColumns.SAVED_DATE + ");";

    /**
     * Mark the deleted date when a note (or the folder's whole subtree) moves into trash
     */
    // 触发器：当便签的 parent_id 由非回收站变为回收站(-3)时，自动写入进入回收站的删除时间戳；
    // 文件夹整体移入回收站时，其子便签的 parent_id 被联动修改，同样会触发本规则
    private static final String NOTE_MARK_DELETED_DATE_ON_TRASH_TRIGGER =
        "CREATE TRIGGER mark_deleted_date_on_trash " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " AND old." + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.DELETED_DATE + "=strftime('%s','now') * 1000" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.ID + ";" +
        " END";

    /**
     * Clear the deleted date when a note is restored from trash
     */
    // 触发器：当便签的 parent_id 由回收站恢复为普通文件夹时，自动将删除时间戳清零
    private static final String NOTE_CLEAR_DELETED_DATE_ON_RESTORE_TRIGGER =
        "CREATE TRIGGER clear_deleted_date_on_restore " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
        " AND old." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.DELETED_DATE + "=0" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.ID + ";" +
        " END";

    /**
     * Delete history versions when the note is permanently deleted
     */
    // 触发器：便签被永久删除时，级联清理其全部历史版本，避免历史表残留孤儿记录
    private static final String HISTORY_DELETE_ON_NOTE_DELETE_TRIGGER =
        "CREATE TRIGGER delete_history_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.HISTORY +
        "   WHERE " + HistoryColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Increase folder's note count when move note to the folder
     */
    // 触发器：当便签修改 parent_id 移入新文件夹时，目标文件夹的 notes_count 自动加 1
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when move note from folder
     */
    // 触发器：当便签修改 parent_id 移出原文件夹时，原文件夹的 notes_count 自动减 1（不低于 0）
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * Increase folder's note count when insert new note to the folder
     */
    // 触发器：当向某文件夹新插入一条便签时，对应父文件夹的 notes_count 自动加 1
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when delete note from the folder
     */
    // 触发器：当从某文件夹删除一条便签时，对应父文件夹的 notes_count 自动减 1（不低于 0）
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * Update note's content when insert data with type {@link DataConstants#NOTE}
     */
    // 触发器：向 data 表插入普通文本便签内容后，自动同步更新 note 表对应记录的 snippet 摘要
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has changed
     */
    // 触发器：更新 data 表中普通文本便签内容后，自动同步更新 note 表对应记录的 snippet 摘要
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has deleted
     */
    // 触发器：删除 data 表中普通文本便签内容后，自动将 note 表对应记录的 snippet 置为空串
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Delete datas belong to note which has been deleted
     */
    // 触发器：删除 note 表中的便签记录后，级联删除 data 表中隶属于该便签的所有数据行
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Delete notes belong to folder which has been deleted
     */
    // 触发器：永久删除文件夹记录后，级联删除 note 表中所有 parent_id 指向该文件夹的子便签
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Move notes belong to folder which has been moved to trash folder
     */
    // 触发器：将文件夹移入回收站时，自动将该文件夹下的所有便签一并移入回收站
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " AND new." + NoteColumns.TYPE + "<>" + Notes.TYPE_NOTE +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER + "," +
        "       " + NoteColumns.DELETED_DATE + "=strftime('%s','now') * 1000" +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Move notes belong to folder which has been restored from trash folder
     */
    // 触发器：文件夹从回收站恢复时，把其下仍在回收站中的子便签一并移回文件夹，
    // 并清零它们的删除时间，保证父子位置与保留期状态一致
    private static final String FOLDER_RESTORE_NOTES_TRIGGER =
        "CREATE TRIGGER folder_restore_notes_on_restore " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN old." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " AND new." + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
        " AND new." + NoteColumns.TYPE + "<>" + Notes.TYPE_NOTE +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=new." + NoteColumns.ID + "," +
        "       " + NoteColumns.DELETED_DATE + "=0" +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * 构造方法，初始化数据库名称为 note.db、版本号为 DB_VERSION。
     *
     * @param context 应用上下文对象
     */
    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 创建 note 主表，重建相关触发器，并初始化四个系统内置文件夹。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    public void createNoteTable(SQLiteDatabase db) {
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    /**
     * 先删除旧触发器再重新创建 note 表关联的全部 7 个触发器，确保触发逻辑为最新版本。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");
        db.execSQL("DROP TRIGGER IF EXISTS folder_restore_notes_on_restore");
        db.execSQL("DROP TRIGGER IF EXISTS mark_deleted_date_on_trash");
        db.execSQL("DROP TRIGGER IF EXISTS clear_deleted_date_on_restore");
        db.execSQL("DROP TRIGGER IF EXISTS delete_history_on_delete");

        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
        db.execSQL(FOLDER_RESTORE_NOTES_TRIGGER);
        db.execSQL(NOTE_MARK_DELETED_DATE_ON_TRASH_TRIGGER);
        db.execSQL(NOTE_CLEAR_DELETED_DATE_ON_RESTORE_TRIGGER);
        db.execSQL(HISTORY_DELETE_ON_NOTE_DELETE_TRIGGER);
    }

    /**
     * 在 note 表中初始化插入 4 个系统级文件夹：通话便签文件夹(-2)、根文件夹(0)、
     * 临时文件夹(-1) 以及回收站文件夹(-3)，类型均标记为 TYPE_SYSTEM。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    private void createSystemFolder(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        /**
         * call record foler for call notes
         */
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * root folder which is default folder
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * temporary folder which is used for moving note
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        /**
         * create trash folder
         */
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 创建 data 内容从表，重建数据同步触发器，并创建 note_id 索引。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    public void createDataTable(SQLiteDatabase db) {
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    /**
     * 先清理再重新创建 data 表关联的 3 个内容摘要联动触发器。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    /**
     * 创建 note_history 历史版本表，并为其 note_id 与 saved_date 建立查询索引。
     * 本方法由：首次建库(onCreate)、V1 全量重建(upgradeToV2) 与 V4→V5 升级 三处统一调用，
     * 保证任意升级路径下历史表结构一致。
     *
     * @param db 可写的 SQLiteDatabase 数据库实例
     */
    public void createHistoryTable(SQLiteDatabase db) {
        db.execSQL(CREATE_HISTORY_TABLE_SQL);
        db.execSQL(CREATE_HISTORY_NOTE_ID_INDEX_SQL);
        db.execSQL(CREATE_HISTORY_SAVED_DATE_INDEX_SQL);
        Log.d(TAG, "history table has been created");
    }

    /**
     * 线程安全地获取 NotesDatabaseHelper 单例对象，避免多线程并发打开数据库冲突。
     *
     * @param context 上下文对象
     * @return NotesDatabaseHelper 全局唯一实例
     */
    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    @Override
    /**
     * 数据库首次创建时的生命周期回调，依次建好 note 表、data 表和 note_history 表。
     *
     * @param db 新创建的 SQLiteDatabase 实例
     */
    public void onCreate(SQLiteDatabase db) {
        createNoteTable(db);
        createDataTable(db);
        createHistoryTable(db);
    }

    @Override
    /**
     * 数据库版本升级回调，支持从 V1、V2、V3、V4 逐步平滑迁移至 V5。
     *
     * @param db         待升级的 SQLiteDatabase 实例
     * @param oldVersion 本地旧数据库版本号
     * @param newVersion 目标新数据库版本号
     */
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        // V1 升级至 V2：直接重建全量最新表结构（已包含 V3~V6 的表结构变更，故标记 skipV2）
        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // this upgrade including the upgrade from v2 to v3
            oldVersion = 6;
        }

        // 原生 V2 升级至 V3：新增 gtask_id 列与回收站目录，并标记需重建触发器
        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // V3 升级至 V4：为 note 表新增 version 版本号列
        if (oldVersion == 3) {
            upgradeToV4(db);
            oldVersion++;
        }

        // V4 升级至 V5：新增回收站删除时间字段与历史版本表，并重建含新触发器的全部规则
        if (oldVersion == 4) {
            upgradeToV5(db);
            reCreateTriggers = true;
            oldVersion++;
        }

        // V5 升级至 V6：为 note 表新增置顶标记与标签字段
        if (oldVersion == 5) {
            upgradeToV6(db);
            oldVersion++;
        }

        if (reCreateTriggers) {
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        // 校验最终版本号是否与目标版本一致，不一致则抛出非法状态异常
        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    /**
     * 将数据库从 V1 升级到 V2：删除旧 note 和 data 表并按最新结构重新创建，
     * 同时补建 V5 引入的历史版本表（旧版数据库必然不存在该表）。
     *
     * @param db SQLiteDatabase 实例
     */
    private void upgradeToV2(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.HISTORY);
        createNoteTable(db);
        createDataTable(db);
        createHistoryTable(db);
    }

    /**
     * 将数据库从 V2 升级到 V3：移除废弃的修改时间触发器，新增 gtask_id 同步字段及回收站系统目录。
     *
     * @param db SQLiteDatabase 实例
     */
    private void upgradeToV3(SQLiteDatabase db) {
        // drop unused triggers
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // add a column for gtask id
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // add a trash system folder
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    /**
     * 将数据库从 V3 升级到 V4：在 note 表中新增 version 整型版本号字段（默认值为 0）。
     *
     * @param db SQLiteDatabase 实例
     */
    private void upgradeToV4(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }

    /**
     * 将数据库从 V4 升级到 V5：为 note 表新增回收站删除时间字段，并创建历史版本表。
     * 新建的 deleted_date 列默认 0，历史数据中已处于回收站的便签由调用方按需回填时间戳，
     * 触发器随后由 onUpgrade 统一重建，保证进出回收站时时间字段自动维护。
     *
     * @param db SQLiteDatabase 实例
     */
    private void upgradeToV5(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.DELETED_DATE
                + " INTEGER NOT NULL DEFAULT 0");
        // 历史遗留数据兜底：升级前已在回收站的便签没有删除时间，统一回填为升级时刻，
        // 使其从现在开始计算保留期，而不是被立即判定为过期
        db.execSQL("UPDATE " + TABLE.NOTE
                + " SET " + NoteColumns.DELETED_DATE + "=strftime('%s','now') * 1000"
                + " WHERE " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER
                + " AND " + NoteColumns.ID + ">" + Notes.ID_ROOT_FOLDER);
        createHistoryTable(db);
    }

    /**
     * 将数据库从 V5 升级到 V6：为 note 表新增置顶标记与标签两个字段。
     * 两列都带默认值，历史数据自动补齐（全部为"未置顶、无标签"），无需回填。
     *
     * @param db SQLiteDatabase 实例
     */
    private void upgradeToV6(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.PINNED
                + " INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.TAG_LABEL
                + " TEXT NOT NULL DEFAULT ''");
    }
}
