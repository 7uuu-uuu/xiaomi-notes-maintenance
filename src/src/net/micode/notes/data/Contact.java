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

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人查询与缓存工具类。
 * 负责根据电话号码从系统通讯录（ContactsContract）中查询对应的联系人显示姓名，
 * 并通过静态 HashMap 在内存中缓存查询结果，减少对 ContentResolver 的重复数据库查询，
 * 主要用于通话便签（CallNote）列表项和同步时显示来电/去电联系人名称。
 */
public class Contact {
    // 电话号码到联系人姓名的内存缓存映射表（延迟初始化）
    private static HashMap<String, String> sContactCache;
    // 日志输出标签
    private static final String TAG = "Contact";

    /**
     * 联系人号码匹配的 SQL WHERE 查询条件模板。
     * 使用 SQLite 内置函数 PHONE_NUMBERS_EQUAL 比较号码，限定 MIME 类型为电话号码，
     * 并借助 phone_lookup 表的 min_match 最小后缀匹配索引加速查询（'+' 为待替换占位符）。
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据给定的电话号码查询联系人姓名。
     * 优先从内存缓存 sContactCache 中读取；若未命中则查询系统联系人数据库并写入缓存。
     *
     * @param context     应用或组件上下文，用于获取 ContentResolver
     * @param phoneNumber 待查询的电话号码字符串
     * @return 匹配到的联系人显示姓名；若未找到或发生异常则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 首次调用时懒加载初始化联系人缓存表
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 若缓存中已存在该号码的记录，直接返回缓存的联系人姓名
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 将查询模板中的 '+' 替换为该电话号码的最小匹配后缀串
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        // 通过 ContentResolver 查询系统通讯录 Data 表中的联系人显示名称列
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 若查询游标非空且存在匹配记录，提取第一条记录的姓名并写入缓存
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            // 捕获游标列索引越界异常并记录错误日志
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            // 无论读取成功与否，确保关闭游标以释放数据库资源
            } finally {
                cursor.close();
            }
        } else {
            // 未匹配到对应联系人（注意：若 cursor 非空但无记录，返回 null）
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}
