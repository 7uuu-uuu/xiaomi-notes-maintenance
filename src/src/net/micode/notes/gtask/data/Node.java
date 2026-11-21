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

import org.json.JSONObject;

/**
 * Google Task 云同步节点的抽象基类。
 * 抽象了云端任务（Task）、元数据任务（MetaData）和任务列表（TaskList）的公共属性
 * （云端唯一标识 gid、节点名称、最后修改时间、删除标记），定义了 9 种双向同步决策状态常量，
 * 并声明了本地数据库与远端 JSON 之间双向转换及同步动作判定的 6 个抽象方法。
 */
public abstract class Node {
    // 同步动作常量：本地与云端均无变化，无需同步
    public static final int SYNC_ACTION_NONE = 0;

    // 同步动作常量：本地新增节点，需在云端创建对应节点
    public static final int SYNC_ACTION_ADD_REMOTE = 1;

    // 同步动作常量：云端新增节点，需在本地数据库插入记录
    public static final int SYNC_ACTION_ADD_LOCAL = 2;

    // 同步动作常量：本地已删除节点，需同步删除云端节点
    public static final int SYNC_ACTION_DEL_REMOTE = 3;

    // 同步动作常量：云端已删除节点，需同步删除本地记录
    public static final int SYNC_ACTION_DEL_LOCAL = 4;

    // 同步动作常量：仅本地有修改，需将本地内容推送更新至云端
    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;

    // 同步动作常量：仅云端有修改，需将云端内容拉取覆盖至本地
    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;

    // 同步动作常量：本地与云端同时发生修改，产生双向更新冲突
    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;

    // 同步动作常量：同步校验异常或发生错误
    public static final int SYNC_ACTION_ERROR = 8;

    // 节点在 Google Task 服务端的全局唯一 ID（gid）
    private String mGid;

    // 节点名称（对应任务标题或任务列表名）
    private String mName;

    // 节点在云端记录的最后一次修改时间戳
    private long mLastModified;

    // 节点是否已被标记为删除
    private boolean mDeleted;

    /**
     * 默认构造方法，初始化同步节点基础属性为默认空值状态。
     */
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    /**
     * 生成在云端新建该节点的 Google Task 协议 JSON 动作对象。
     *
     * @param actionId 本次操作的递增动作 ID
     * @return 封装好的 create 动作 JSONObject
     */
    public abstract JSONObject getCreateAction(int actionId);

    /**
     * 生成在云端更新该节点的 Google Task 协议 JSON 动作对象。
     *
     * @param actionId 本次操作的递增动作 ID
     * @return 封装好的 update 动作 JSONObject
     */
    public abstract JSONObject getUpdateAction(int actionId);

    /**
     * 解析 Google Task 服务端返回的 JSON 数据并填充到当前节点对象。
     */
    public abstract void setContentByRemoteJSON(JSONObject js);

    /**
     * 根据本地数据库导出的 JSON 对象填充当前云同步节点内容。
     */
    public abstract void setContentByLocalJSON(JSONObject js);

    /**
     * 将当前同步节点内容转换为可供本地 SqlNote 持久化的 JSON 格式对象。
     */
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 对比当前云端节点状态与本地数据库游标记录，计算应执行的双向同步动作码。
     */
    public abstract int getSyncAction(Cursor c);

    // 设置云端节点全局唯一 ID
    public void setGid(String gid) {
        this.mGid = gid;
    }

    // 设置节点名称
    public void setName(String name) {
        this.mName = name;
    }

    // 设置节点最后修改时间戳
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    // 设置节点是否已删除标志
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    // 获取云端节点全局唯一 ID
    public String getGid() {
        return this.mGid;
    }

    // 获取节点名称
    public String getName() {
        return this.mName;
    }

    // 获取节点最后修改时间戳
    public long getLastModified() {
        return this.mLastModified;
    }

    // 获取节点是否已删除标志
    public boolean getDeleted() {
        return this.mDeleted;
    }

}
