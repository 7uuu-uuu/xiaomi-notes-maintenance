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

/**
 * Google Task 云同步协议 JSON 字段名与特殊标识字符串常量工具类。
 * 集中管理与 Google Tasks Web 接口交互时请求/响应 JSON 报文中的所有键名、动作类型、
 * 实体类型，以及小米便签在云端标识文件夹与元数据（METADATA）的专用前缀和命名常量。
 */
public class GTaskStringUtils {

    // JSON 键名：单次同步操作的递增动作编号
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    // JSON 键名：批量提交的操作指令数组
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    // JSON 键名：操作动作类型字段
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    // 动作类型值：创建新任务或任务列表
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    // 动作类型值：拉取全部任务或列表
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    // 动作类型值：移动任务位置或所属列表
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    // 动作类型值：更新已有节点属性
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // JSON 键名：节点创建者 ID
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    // JSON 键名：子节点实体数据
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    // JSON 键名：Google Task 客户端协议版本号
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    // JSON 键名：任务是否已完成布尔标志
    public final static String GTASK_JSON_COMPLETED = "completed";

    // JSON 键名：当前任务列表 ID
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    // JSON 键名：默认任务列表 ID
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    // JSON 键名：节点是否已被删除标志
    public final static String GTASK_JSON_DELETED = "deleted";

    // JSON 键名：移动任务的目标任务列表 ID
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    // JSON 键名：移动或创建任务的目标父列表 ID
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    // JSON 键名：目标父节点类型
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    // JSON 键名：节点属性增量变更对象
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    // JSON 键名：操作实体类型（GROUP 或 TASK）
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    // JSON 键名：查询时是否包含已删除节点
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    // JSON 键名：云端节点唯一 ID（gid）
    public final static String GTASK_JSON_ID = "id";

    // JSON 键名：任务列表排序索引
    public final static String GTASK_JSON_INDEX = "index";

    // JSON 键名：云端最后修改时间戳
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    // JSON 键名：服务端返回的最新同步时间点
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    // JSON 键名：任务所属列表 ID
    public final static String GTASK_JSON_LIST_ID = "list_id";

    // JSON 键名：返回报文中的任务列表集合数组
    public final static String GTASK_JSON_LISTS = "lists";

    // JSON 键名：任务标题或任务列表名称
    public final static String GTASK_JSON_NAME = "name";

    // JSON 键名：创建操作成功后服务端分配的新 gid
    public final static String GTASK_JSON_NEW_ID = "new_id";

    // JSON 键名：任务详细备注正文
    public final static String GTASK_JSON_NOTES = "notes";

    // JSON 键名：父节点 ID
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    // JSON 键名：同级前驱兄弟任务 ID（用于维护顺序）
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    // JSON 键名：POST 请求响应结果数组
    public final static String GTASK_JSON_RESULTS = "results";

    // JSON 键名：移动任务的源任务列表 ID
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    // JSON 键名：任务列表下的所有任务数组
    public final static String GTASK_JSON_TASKS = "tasks";

    // JSON 键名：类型字段
    public final static String GTASK_JSON_TYPE = "type";

    // 实体类型值：任务组/任务列表（对应本地文件夹）
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    // 实体类型值：单条任务（对应本地便签或元数据）
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    // JSON 键名：用户信息对象
    public final static String GTASK_JSON_USER = "user";

    // 小米便签在 Google Task 中创建任务列表时的专属识别前缀
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    // 本地默认根文件夹映射到云端任务列表的名称后缀
    public final static String FOLDER_DEFAULT = "Default";

    // 本地通话便签文件夹映射到云端任务列表的名称后缀
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    // 云端专门存放便签扩展元数据（背景色、提醒、清单模式等）的隐藏列表后缀
    public final static String FOLDER_META = "METADATA";

    // 元数据 JSON 键名：关联的业务便签云端 gid
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    // 元数据 JSON 键名：对应本地 note 表元信息对象
    public final static String META_HEAD_NOTE = "meta_note";

    // 元数据 JSON 键名：对应本地 data 表内容数组
    public final static String META_HEAD_DATA = "meta_data";

    // 云端元数据任务的固定警示标题，提醒用户切勿在网页端手动修改或删除
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}
