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

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 便签同步元数据类，继承自 Task。
 * 由于原生 Google Task 仅支持纯文本标题和备注，小米便签在云端专门维护一个 METADATA 列表，
 * 将每条便签的背景色、提醒时间、清单模式、通话信息及关联 gid 序列化为 JSON 存入 MetaData 任务备注中。
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();

    // 该条元数据所关联的业务便签（或文件夹）在 Google Task 中的 gid
    private String mRelatedGid = null;

    /**
     * 绑定目标节点的云端 gid 并将完整元数据 JSON 写入任务备注，同时设置固定警示标题。
     *
     * @param gid      关联的目标任务或任务列表 gid
     * @param metaInfo 包含 note 和 data 本地属性的元数据 JSON 对象
     */
    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        setNotes(metaInfo.toString());
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取该元数据所关联的业务任务云端 gid。
     *
     * @return 关联节点的 gid 字符串；未解析成功时为 null
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    @Override
    /**
     * 判断当前元数据任务是否值得保存到云端（备注字段非空即有效）。
     */
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    @Override
    /**
     * 从云端返回的 JSON 解析元数据任务，并从 notes 备注 JSON 中提取关联的 meta_gid。
     *
     * @param js 远端返回的 Task JSON 对象
     */
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;
            }
        }
    }

    @Override
    /**
     * 禁用方法：元数据不直接映射本地独立便签，调用时抛出 IllegalAccessError。
     */
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    @Override
    /**
     * 禁用方法：元数据不直接转换为本地便签记录，调用时抛出 IllegalAccessError。
     */
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    @Override
    /**
     * 禁用方法：元数据跟随主业务节点同步，不单独计算同步动作，调用时抛出 IllegalAccessError。
     */
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}
