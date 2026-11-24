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

import android.net.Uri;
/**
 * 小米便签数据契约（Contract）常量类。
 * 统一定义了 ContentProvider 的授权标识（AUTHORITY）、访问 URI、节点类型、
 * 系统内置文件夹 ID、组件间通信 Intent 附加参数键名、桌面小部件尺寸类型，
 * 以及 note 表（NoteColumns）、data 表（DataColumns）、文本便签（TextNote）
 * 与通话便签（CallNote）的列名和 MIME 类型常量。
 */
public class Notes {
    // NotesProvider 在 AndroidManifest.xml 中注册的唯一授权标识
    public static final String AUTHORITY = "micode_notes";
    // 日志输出标签
    public static final String TAG = "Notes";
    // note 表记录类型常量：0 表示普通便签（含文本便签和通话便签）
    public static final int TYPE_NOTE     = 0;
    // note 表记录类型常量：1 表示用户自定义文件夹
    public static final int TYPE_FOLDER   = 1;
    // note 表记录类型常量：2 表示系统内置文件夹（根目录、临时目录、通话记录、回收站）
    public static final int TYPE_SYSTEM   = 2;

    /**
     * Following IDs are system folders' identifiers
     * {@link Notes#ID_ROOT_FOLDER } is default folder
     * {@link Notes#ID_TEMPARAY_FOLDER } is for notes belonging no folder
     * {@link Notes#ID_CALL_RECORD_FOLDER} is to store call records
     */
    // 系统默认根文件夹 ID（顶层便签的默认父目录）
    public static final int ID_ROOT_FOLDER = 0;
    // 系统临时文件夹 ID（用于存放未归属文件夹的便签）
    public static final int ID_TEMPARAY_FOLDER = -1;
    // 通话便签专属系统文件夹 ID
    public static final int ID_CALL_RECORD_FOLDER = -2;
    // 系统回收站文件夹 ID（开启同步模式下删除便签时先移入此目录）
    public static final int ID_TRASH_FOLER = -3;

    // Intent 附加参数键名：便签闹钟提醒时间戳（毫秒）
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";
    // Intent 附加参数键名：便签背景颜色资源索引 ID
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id";
    // Intent 附加参数键名：关联的桌面小部件实例 ID（AppWidgetId）
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";
    // Intent 附加参数键名：关联的桌面小部件尺寸类型（2x2 或 4x4）
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";
    // Intent 附加参数键名：新建或打开便签所属的父文件夹 ID
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";
    // Intent 附加参数键名：通话便签对应的通话发生时间戳
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";

    // 桌面小部件类型常量：无效/未关联任何桌面小部件
    public static final int TYPE_WIDGET_INVALIDE      = -1;
    // 桌面小部件类型常量：2x2 尺寸桌面便签小部件
    public static final int TYPE_WIDGET_2X            = 0;
    // 桌面小部件类型常量：4x4 尺寸桌面便签小部件
    public static final int TYPE_WIDGET_4X            = 1;

    /**
     * 便签数据项 MIME 类型快捷常量类，区分普通文本便签与通话记录便签。
     */
    public static class DataConstants {
        // 普通文本便签单条记录的 MIME 类型
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;
        // 通话记录便签单条记录的 MIME 类型
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE;
    }

    /**
     * Uri to query all notes and folders
     */
    // 查询和操作 note 表（便签与文件夹元数据）的内容 URI
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * Uri to query data
     */
    // 查询和操作 data 表（便签具体文本及通话数据）的内容 URI
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * note 数据库表的列名定义接口。
     * 存储便签、用户文件夹及系统文件夹的元数据属性与云同步状态字段。
     */
    public interface NoteColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        // 记录唯一主键 ID（长整型自增）
        public static final String ID = "_id";

        /**
         * The parent's id for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        // 所属父文件夹的 ID（如根目录为 0，通话便签目录为 -2）
        public static final String PARENT_ID = "parent_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        // 便签或文件夹的创建时间戳（单位：毫秒）
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        // 便签或文件夹最近一次修改的时间戳（单位：毫秒）
        public static final String MODIFIED_DATE = "modified_date";


        /**
         * Alert date
         * <P> Type: INTEGER (long) </P>
         */
        // 定时闹钟提醒时间戳（单位：毫秒，0 表示未设置提醒）
        public static final String ALERTED_DATE = "alert_date";

        /**
         * Folder's name or text content of note
         * <P> Type: TEXT </P>
         */
        // 文件夹名称或普通便签的内容摘要（由数据库触发器自动同步文本内容）
        public static final String SNIPPET = "snippet";

        /**
         * Note's widget id
         * <P> Type: INTEGER (long) </P>
         */
        // 绑定的桌面小部件实例 ID
        public static final String WIDGET_ID = "widget_id";

        /**
         * Note's widget type
         * <P> Type: INTEGER (long) </P>
         */
        // 绑定的桌面小部件尺寸类型（-1 无效、0 为 2x2、1 为 4x4）
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * Note's background color's id
         * <P> Type: INTEGER (long) </P>
         */
        // 便签背景颜色编号（对应 ResourceParser 中的黄/蓝/白/绿/红）
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * For text note, it doesn't has attachment, for multi-media
         * note, it has at least one attachment
         * <P> Type: INTEGER </P>
         */
        // 是否包含多媒体附件标志（0 无附件，非 0 有附件）
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * Folder's count of notes
         * <P> Type: INTEGER (long) </P>
         */
        // 文件夹下包含的便签数量（由 SQLite 触发器自动增减维护）
        public static final String NOTES_COUNT = "notes_count";

        /**
         * The file type: folder or note
         * <P> Type: INTEGER </P>
         */
        // 节点类型（TYPE_NOTE=0 便签、TYPE_FOLDER=1 文件夹、TYPE_SYSTEM=2 系统目录）
        public static final String TYPE = "type";

        /**
         * The last sync id
         * <P> Type: INTEGER (long) </P>
         */
        // 最近一次与 Google Task 同步时的远端修改时间戳
        public static final String SYNC_ID = "sync_id";

        /**
         * Sign to indicate local modified or not
         * <P> Type: INTEGER </P>
         */
        // 本地是否发生未同步修改的标志位（1 已修改，0 未修改）
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * Original parent id before moving into temporary folder
         * <P> Type : INTEGER </P>
         */
        // 移入临时文件夹或回收站之前记录的原始父文件夹 ID，用于同步还原
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * The gtask id
         * <P> Type : TEXT </P>
         */
        // 对应 Google Task 云端任务或任务列表的全局唯一标识（gid）
        public static final String GTASK_ID = "gtask_id";

        /**
         * The version code
         * <P> Type : INTEGER (long) </P>
         */
        // 便签记录乐观版本号，每次更新时自动递增 1
        public static final String VERSION = "version";

        /**
         * Whether the note is pinned to the top of the list.
         * 1 means pinned, 0 means not pinned.
         * <P> Type : INTEGER </P>
         */
        public static final String PINNED = "pinned";

        /**
         * The password hash for locking a note. Empty string means no lock.
         * <P> Type : TEXT </P>
         */
        public static final String PASSWORD = "password";
    }

    /**
     * data 数据库表的列名定义接口。
     * 存储每条便签关联的具体正文内容以及按 MIME 类型复用的扩展列 data1~data5。
     */
    public interface DataColumns {
        /**
         * The unique ID for a row
         * <P> Type: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * The MIME type of the item represented by this row.
         * <P> Type: Text </P>
         */
        // 数据行的 MIME 类型，标识该行属于文本便签还是通话便签
        public static final String MIME_TYPE = "mime_type";

        /**
         * The reference id to note that this data belongs to
         * <P> Type: INTEGER (long) </P>
         */
        // 外键引用：该条数据所属便签在 note 表中的主键 ID
        public static final String NOTE_ID = "note_id";

        /**
         * Created data for note or folder
         * <P> Type: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * Latest modified date
         * <P> Type: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * Data's content
         * <P> Type: TEXT </P>
         */
        // 便签正文文本内容
        public static final String CONTENT = "content";


        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        // 通用整型扩展字段 1（文本便签中存清单模式标志，通话便签中存通话时间戳）
        public static final String DATA1 = "data1";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * integer data type
         * <P> Type: INTEGER </P>
         */
        // 通用整型扩展字段 2（预留扩展）
        public static final String DATA2 = "data2";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        // 通用文本扩展字段 3（通话便签中用于存储来电/去电电话号码）
        public static final String DATA3 = "data3";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        // 通用文本扩展字段 4（预留扩展）
        public static final String DATA4 = "data4";

        /**
         * Generic data column, the meaning is {@link #MIMETYPE} specific, used for
         * TEXT data type
         * <P> Type: TEXT </P>
         */
        // 通用文本扩展字段 5（预留扩展）
        public static final String DATA5 = "data5";
    }

    /**
     * 普通文本便签数据契约类，实现 DataColumns 接口。
     * 将 DATA1 列映射为清单模式（CheckList）状态字段，并定义文本便签 MIME 类型与 URI。
     */
    public static final class TextNote implements DataColumns {
        /**
         * Mode to indicate the text in check list mode or not
         * <P> Type: Integer 1:check list mode 0: normal mode </P>
         */
        // 文本编辑模式列名（复用 data1 列：0 为普通文本模式，1 为清单勾选模式）
        public static final String MODE = DATA1;

        // 清单模式（CheckList）启用标志常量值
        public static final int MODE_CHECK_LIST = 1;

        // 文本便签集合（目录）的 MIME 类型
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";

        // 单条文本便签记录的 MIME 类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        // 文本便签的内容访问 URI
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录便签数据契约类，实现 DataColumns 接口。
     * 将 DATA1 列映射为通话时间、DATA3 列映射为电话号码，并定义通话便签 MIME 类型与 URI。
     */
    public static final class CallNote implements DataColumns {
        /**
         * Call date for this record
         * <P> Type: INTEGER (long) </P>
         */
        // 通话发生时间戳列名（复用整型列 data1）
        public static final String CALL_DATE = DATA1;

        /**
         * Phone number for this record
         * <P> Type: TEXT </P>
         */
        // 通话对方电话号码列名（复用文本列 data3）
        public static final String PHONE_NUMBER = DATA3;

        // 通话便签集合（目录）的 MIME 类型
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";

        // 单条通话便签记录的 MIME 类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        // 通话便签的内容访问 URI
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}
