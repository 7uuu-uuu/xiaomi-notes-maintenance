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

import java.util.ArrayList;
import java.util.List;

/**
 * 便签历史版本保留策略（纯 Java 实现，不依赖 Android 框架，便于单元测试）。
 * 统一定义历史版本的归档判定、摘要生成、条数上限与过期清理边界，
 * 供 NotesProvider 写入历史记录时调用，避免把保留规则散落在界面层或 SQL 语句中。
 */
public final class HistoryPolicy {
    // 单条便签最多保留的历史版本数，超出部分按归档时间从旧到新淘汰
    public static final int MAX_VERSIONS_PER_NOTE = 20;

    // 历史版本的最长保留天数，超过该天数的版本在写入时顺带清理
    public static final int RETENTION_DAYS = 30;

    // 历史列表摘要的最大展示长度（字符数）
    public static final int SNIPPET_MAX_LENGTH = 40;

    // 一天的毫秒数，用于保留期换算
    public static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    // 工具类禁止实例化
    private HistoryPolicy() {
    }

    /**
     * 判断一次保存前是否需要为被覆盖的旧正文归档一条历史版本。
     * 归档规则：旧正文非空，且与最近一条历史版本内容不同（重复内容不产生冗余版本）。
     *
     * @param previousContent 即将被覆盖的数据库现存正文
     * @param newestHistory   该便签最近一条历史版本的正文（无历史时传 null）
     * @return 需要归档返回 true，否则返回 false
     */
    public static boolean shouldRecord(String previousContent, String newestHistory) {
        if (previousContent == null || previousContent.length() == 0) {
            return false;
        }
        return previousContent.equals(newestHistory) == false;
    }

    /**
     * 生成历史列表使用的单行摘要：折叠换行与连续空白，超长时截断并追加省略号。
     *
     * @param content   历史版本正文
     * @param maxLength 摘要最大长度（字符数）
     * @return 单行摘要，空内容返回空串
     */
    public static String makeSnippet(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        if (maxLength <= 0 || oneLine.length() <= maxLength) {
            return oneLine;
        }
        return oneLine.substring(0, maxLength) + "...";
    }

    /**
     * 计算插入一条新版本后需要淘汰的旧版本条数。
     *
     * @param existingCount 该便签已存在的历史版本数
     * @param maxVersions   允许保留的最大版本数
     * @return 需要删除的条数，未超限返回 0
     */
    public static int excessCount(int existingCount, int maxVersions) {
        int afterInsert = existingCount + 1;
        if (maxVersions <= 0 || afterInsert <= maxVersions) {
            return 0;
        }
        return afterInsert - maxVersions;
    }

    /**
     * 计算历史版本的过期时间边界，早于该时间戳的版本应被清理。
     *
     * @param now          当前时间戳（毫秒）
     * @param retentionDays 保留天数
     * @return 过期时间边界时间戳（毫秒）
     */
    public static long retentionBoundary(long now, int retentionDays) {
        if (retentionDays <= 0) {
            return now;
        }
        return now - retentionDays * MILLIS_PER_DAY;
    }

    /**
     * 判断指定历史版本是否已超出保留期。
     *
     * @param savedDate     该版本归档时间戳（毫秒）
     * @param now           当前时间戳（毫秒）
     * @param retentionDays 保留天数
     * @return 已过期返回 true
     */
    public static boolean isExpired(long savedDate, long now, int retentionDays) {
        if (savedDate <= 0) {
            return false;
        }
        return savedDate < retentionBoundary(now, retentionDays);
    }

    /**
     * 从给定版本列表中筛选出应保留的部分：按输入顺序（由新到旧）保留最新 maxVersions 条，
     * 并跳过与已保留版本内容完全相同的重复项。
     *
     * @param contents     历史正文列表，按归档时间由新到旧排列
     * @param maxVersions  允许保留的最大版本数
     * @return 实际应保留的正文列表
     */
    public static List<String> selectRetained(List<String> contents, int maxVersions) {
        List<String> retained = new ArrayList<String>();
        if (contents == null || maxVersions <= 0) {
            return retained;
        }
        for (String content : contents) {
            if (content == null || content.length() == 0) {
                continue;
            }
            if (retained.contains(content)) {
                continue;
            }
            retained.add(content);
            if (retained.size() >= maxVersions) {
                break;
            }
        }
        return retained;
    }
}
