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
 * 回收站保留期策略（纯 Java 实现，不依赖 Android 框架，便于单元测试）。
 * 定义便签进入回收站后的保留天数、过期判定、剩余天数换算与到期时间格式化，
 * 供 NotesProvider 自动清理与界面提示统一调用。
 */
public final class TrashPolicy {
    // 便签在回收站中的默认保留天数，逾期未恢复即被自动永久删除
    public static final int RETENTION_DAYS = 30;

    // 一天的毫秒数，用于保留期换算
    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    // 工具类禁止实例化
    private TrashPolicy() {
    }

    /**
     * 判断指定删除时间是否允许作为有效的时间戳（未标记删除时间的记录按未删除处理）。
     *
     * @param deletedDate 进入回收站的时间戳（毫秒）
     * @return 有效时间戳返回 true
     */
    public static boolean hasDeletedDate(long deletedDate) {
        return deletedDate > 0;
    }

    /**
     * 计算便签在回收站中的到期时间戳。
     *
     * @param deletedDate   进入回收站的时间戳（毫秒）
     * @param retentionDays 保留天数
     * @return 到期时间戳；删除时间非法时返回 0
     */
    public static long expireDate(long deletedDate, int retentionDays) {
        if (!hasDeletedDate(deletedDate) || retentionDays < 0) {
            return 0;
        }
        return deletedDate + retentionDays * MILLIS_PER_DAY;
    }

    /**
     * 判断回收站中的便签是否已超过保留期，可被自动清理。
     *
     * @param deletedDate   进入回收站的时间戳（毫秒）
     * @param now           当前时间戳（毫秒）
     * @param retentionDays 保留天数
     * @return 已过期返回 true
     */
    public static boolean isExpired(long deletedDate, long now, int retentionDays) {
        if (!hasDeletedDate(deletedDate)) {
            return false;
        }
        return now >= expireDate(deletedDate, retentionDays);
    }

    /**
     * 计算回收站便签的剩余保留天数（不足一天按一天计，已过期返回 0）。
     *
     * @param deletedDate   进入回收站的时间戳（毫秒）
     * @param now           当前时间戳（毫秒）
     * @param retentionDays 保留天数
     * @return 剩余天数，范围 0 ~ retentionDays
     */
    public static int remainingDays(long deletedDate, long now, int retentionDays) {
        if (!hasDeletedDate(deletedDate)) {
            return retentionDays;
        }
        long remainMillis = expireDate(deletedDate, retentionDays) - now;
        if (remainMillis <= 0) {
            return 0;
        }
        long days = (remainMillis + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY;
        return days > retentionDays ? retentionDays : (int) days;
    }
}
