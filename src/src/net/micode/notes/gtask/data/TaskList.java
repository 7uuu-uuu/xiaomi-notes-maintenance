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

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
 * Google Task 任务列表（任务组）实体类，继承自同步节点抽象基类 Node。
 * 对应本地数据库中的文件夹（用户文件夹、默认根文件夹、通话便签文件夹）或云端 METADATA 列表，
 * 内部通过 ArrayList<Task> 聚合管理其下属的所有子任务及兄弟链表顺序，并实现文件夹级同步转换。
 */
public class TaskList extends Node {
    private static final String TAG = TaskList.class.getSimpleName();

    // 当前任务列表在 Google Task 中的排列索引
    private int mIndex;

    // 当前任务列表所包含的子任务（Task）有序集合
    private ArrayList<Task> mChildren;

    /**
     * 默认构造方法，初始化空的子任务列表并将索引置为 1。
     */
    public TaskList() {
        super();
        mChildren = new ArrayList<Task>();
        mIndex = 1;
    }

    /**
     * 构建向 Google Task 服务端发送的“新建任务列表（create GROUP）”动作 JSON 对象。
     *
     * @param actionId 动作序列编号
     * @return 新建任务列表请求的 JSONObject
     */
    public JSONObject getCreateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_CREATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // index
            js.put(GTaskStringUtils.GTASK_JSON_INDEX, mIndex);

            // entity_delta
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_CREATOR_ID, "null");
            entity.put(GTaskStringUtils.GTASK_JSON_ENTITY_TYPE,
                    GTaskStringUtils.GTASK_JSON_TYPE_GROUP);
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-create jsonobject");
        }

        return js;
    }

    /**
     * 构建向 Google Task 服务端发送的“更新任务列表（update）”动作 JSON 对象（含重命名与删除）。
     *
     * @param actionId 动作序列编号
     * @return 更新任务列表请求的 JSONObject
     */
    public JSONObject getUpdateAction(int actionId) {
        JSONObject js = new JSONObject();

        try {
            // action_type
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_UPDATE);

            // action_id
            js.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, actionId);

            // id
            js.put(GTaskStringUtils.GTASK_JSON_ID, getGid());

            // entity_delta
            JSONObject entity = new JSONObject();
            entity.put(GTaskStringUtils.GTASK_JSON_NAME, getName());
            entity.put(GTaskStringUtils.GTASK_JSON_DELETED, getDeleted());
            js.put(GTaskStringUtils.GTASK_JSON_ENTITY_DELTA, entity);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("fail to generate tasklist-update jsonobject");
        }

        return js;
    }

    /**
     * 解析服务端返回的任务列表 JSON，提取并设置云端 gid、最后修改时间和列表名称。
     *
     * @param js 服务端返回的任务列表 JSONObject
     */
    public void setContentByRemoteJSON(JSONObject js) {
        if (js != null) {
            try {
                // id
                if (js.has(GTaskStringUtils.GTASK_JSON_ID)) {
                    setGid(js.getString(GTaskStringUtils.GTASK_JSON_ID));
                }

                // last_modified
                if (js.has(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED)) {
                    setLastModified(js.getLong(GTaskStringUtils.GTASK_JSON_LAST_MODIFIED));
                }

                // name
                if (js.has(GTaskStringUtils.GTASK_JSON_NAME)) {
                    setName(js.getString(GTaskStringUtils.GTASK_JSON_NAME));
                }

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("fail to get tasklist content from jsonobject");
            }
        }
    }

    /**
     * 根据本地文件夹 JSON 生成带 "[MIUI_Notes]" 前缀的云端任务列表名称：
     * 根目录映射为 [MIUI_Notes]Default，通话目录映射为 [MIUI_Notes]Call_Note，用户目录直接拼接名称。
     *
     * @param js 本地文件夹导出的 JSONObject
     */
    public void setContentByLocalJSON(JSONObject js) {
        if (js == null || !js.has(GTaskStringUtils.META_HEAD_NOTE)) {
            Log.w(TAG, "setContentByLocalJSON: nothing is avaiable");
        }

        try {
            JSONObject folder = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                String name = folder.getString(NoteColumns.SNIPPET);
                setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + name);
            } else if (folder.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                if (folder.getLong(NoteColumns.ID) == Notes.ID_ROOT_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT);
                else if (folder.getLong(NoteColumns.ID) == Notes.ID_CALL_RECORD_FOLDER)
                    setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                            + GTaskStringUtils.FOLDER_CALL_NOTE);
                else
                    Log.e(TAG, "invalid system folder");
            } else {
                Log.e(TAG, "error type");
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    /**
     * 将云端任务列表还原为本地文件夹 JSON：剥离 "[MIUI_Notes]" 前缀并区分系统目录与普通用户文件夹。
     *
     * @return 包含 meta_note 文件夹信息的 JSONObject
     */
    public JSONObject getLocalJSONFromContent() {
        try {
            JSONObject js = new JSONObject();
            JSONObject folder = new JSONObject();

            String folderName = getName();
            if (getName().startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX))
                folderName = folderName.substring(GTaskStringUtils.MIUI_FOLDER_PREFFIX.length(),
                        folderName.length());
            folder.put(NoteColumns.SNIPPET, folderName);
            if (folderName.equals(GTaskStringUtils.FOLDER_DEFAULT)
                    || folderName.equals(GTaskStringUtils.FOLDER_CALL_NOTE))
                folder.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
            else
                folder.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);

            js.put(GTaskStringUtils.META_HEAD_NOTE, folder);

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 对比本地文件夹游标与云端任务列表时间戳，决定同步动作（文件夹发生双向冲突时优先以本地重命名为准）。
     *
     * @param c 指向本地文件夹记录的 Cursor 游标
     * @return SYNC_ACTION_* 同步动作常量
     */
    public int getSyncAction(Cursor c) {
        try {
            if (c.getInt(SqlNote.LOCAL_MODIFIED_COLUMN) == 0) {
                // there is no local update
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // no update both side
                    return SYNC_ACTION_NONE;
                } else {
                    // apply remote to local
                    return SYNC_ACTION_UPDATE_LOCAL;
                }
            } else {
                // validate gtask id
                if (!c.getString(SqlNote.GTASK_ID_COLUMN).equals(getGid())) {
                    Log.e(TAG, "gtask id doesn't match");
                    return SYNC_ACTION_ERROR;
                }
                if (c.getLong(SqlNote.SYNC_ID_COLUMN) == getLastModified()) {
                    // local modification only
                    return SYNC_ACTION_UPDATE_REMOTE;
                } else {
                    // for folder conflicts, just apply local modification
                    return SYNC_ACTION_UPDATE_REMOTE;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }

        return SYNC_ACTION_ERROR;
    }

    // 获取当前任务列表包含的子任务总数量
    public int getChildTaskCount() {
        return mChildren.size();
    }

    /**
     * 在子任务列表末尾追加一条不重复的子任务，并维护其前驱兄弟与父列表引用。
     *
     * @param task 待添加的子任务对象
     * @return 添加成功返回 true，任务为空或已存在返回 false
     */
    public boolean addChildTask(Task task) {
        boolean ret = false;
        if (task != null && !mChildren.contains(task)) {
            ret = mChildren.add(task);
            if (ret) {
                // need to set prior sibling and parent
                task.setPriorSibling(mChildren.isEmpty() ? null : mChildren
                        .get(mChildren.size() - 1));
                task.setParent(this);
            }
        }
        return ret;
    }

    /**
     * 在指定索引位置插入一条子任务，并重新调整相邻前后任务的前驱兄弟指针。
     *
     * @param task  待插入的子任务对象
     * @param index 插入位置索引（0 ~ size）
     * @return 索引合法处理完成返回 true，索引越界返回 false
     */
    public boolean addChildTask(Task task, int index) {
        if (index < 0 || index > mChildren.size()) {
            Log.e(TAG, "add child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (task != null && pos == -1) {
            mChildren.add(index, task);

            // update the task list
            Task preTask = null;
            Task afterTask = null;
            if (index != 0)
                preTask = mChildren.get(index - 1);
            if (index != mChildren.size() - 1)
                afterTask = mChildren.get(index + 1);

            task.setPriorSibling(preTask);
            if (afterTask != null)
                afterTask.setPriorSibling(task);
        }

        return true;
    }

    /**
     * 从当前列表中移除指定子任务，清空其父/兄弟引用，并修复其后继任务的前驱指针。
     *
     * @param task 待移除的子任务对象
     * @return 移除成功返回 true，列表中不存在该任务返回 false
     */
    public boolean removeChildTask(Task task) {
        boolean ret = false;
        int index = mChildren.indexOf(task);
        if (index != -1) {
            ret = mChildren.remove(task);

            if (ret) {
                // reset prior sibling and parent
                task.setPriorSibling(null);
                task.setParent(null);

                // update the task list
                if (index != mChildren.size()) {
                    mChildren.get(index).setPriorSibling(
                            index == 0 ? null : mChildren.get(index - 1));
                }
            }
        }
        return ret;
    }

    /**
     * 将列表中已有的某个子任务移动到新的目标索引位置。
     *
     * @param task  待移动的子任务
     * @param index 目标索引位置
     * @return 移动成功（或位置未变）返回 true，参数非法返回 false
     */
    public boolean moveChildTask(Task task, int index) {

        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "move child task: invalid index");
            return false;
        }

        int pos = mChildren.indexOf(task);
        if (pos == -1) {
            Log.e(TAG, "move child task: the task should in the list");
            return false;
        }

        if (pos == index)
            return true;
        return (removeChildTask(task) && addChildTask(task, index));
    }

    /**
     * 按索引遍历查找具有指定云端 gid 的子任务对象。
     *
     * @param gid 目标任务的云端唯一标识
     * @return 匹配到的 Task 对象；未找到返回 null
     */
    public Task findChildTaskByGid(String gid) {
        for (int i = 0; i < mChildren.size(); i++) {
            Task t = mChildren.get(i);
            if (t.getGid().equals(gid)) {
                return t;
            }
        }
        return null;
    }

    // 返回指定子任务在列表中的下标位置（不存在返回 -1）
    public int getChildTaskIndex(Task task) {
        return mChildren.indexOf(task);
    }

    // 根据下标安全获取子任务对象，越界时记录日志并返回 null
    public Task getChildTaskByIndex(int index) {
        if (index < 0 || index >= mChildren.size()) {
            Log.e(TAG, "getTaskByIndex: invalid index");
            return null;
        }
        return mChildren.get(index);
    }

    // 使用增强 for 循环按云端 gid 查找子任务对象
    public Task getChilTaskByGid(String gid) {
        for (Task task : mChildren) {
            if (task.getGid().equals(gid))
                return task;
        }
        return null;
    }

    // 获取内部维护的子任务 ArrayList 集合引用
    public ArrayList<Task> getChildTaskList() {
        return this.mChildren;
    }

    // 设置任务列表的排序索引
    public void setIndex(int index) {
        this.mIndex = index;
    }

    // 获取任务列表的排序索引
    public int getIndex() {
        return this.mIndex;
    }
}
