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
 * 便签文本统计与处理工具类（本次维护新增）。
 * <p>
 * 该类不依赖 Android 框架，全部为纯 Java 静态方法，便于在本地 JVM 单元测试中直接验证。
 * 提供便签内容的字符数、词数、行数统计，清单勾选进度统计，清单符号剥离以及超长文本截断等
 * 通用能力，供编辑界面字数显示、列表摘要生成、清单完成度展示等场景复用。
 */
public class NoteTextUtils {

    // 清单模式下已勾选行首前缀符号：'√'（\u221A）
    public static final String TAG_CHECKED = String.valueOf('\u221A');

    // 清单模式下未勾选行首前缀符号：'□'（\u25A1）
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    // 文本超长截断时追加的省略号
    private static final String ELLIPSIS = "…";

    /**
     * 私有构造方法：工具类不允许实例化。
     */
    private NoteTextUtils() {
    }

    /**
     * 判断文本是否为空（null 或去除首尾空白后长度为 0）。
     *
     * @param text 待判断的文本
     * @return 为空返回 true，否则返回 false
     */
    public static boolean isEmpty(String text) {
        return text == null || text.trim().length() == 0;
    }

    /**
     * 统计文本中非空白字符的个数（忽略空格、制表符、换行符等空白字符）。
     *
     * @param text 待统计文本，null 视为空串
     * @return 非空白字符数量
     */
    public static int countChars(String text) {
        if (isEmpty(text)) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计文本中的词数：连续的英文/数字序列计为一个英文词，其余非空白字符（如中文、日文等）
     * 每个字符单独计为一个词。
     *
     * @param text 待统计文本，null 视为空串
     * @return 词数
     */
    public static int countWords(String text) {
        if (isEmpty(text)) {
            return 0;
        }
        int words = 0;
        boolean inAsciiWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                inAsciiWord = false;
                continue;
            }
            if (isAsciiLetterOrDigit(c)) {
                // 连续的英文字母或数字合并为同一个词
                if (!inAsciiWord) {
                    words++;
                    inAsciiWord = true;
                }
            } else {
                inAsciiWord = false;
                words++;
            }
        }
        return words;
    }

    /**
     * 统计文本中非空行的数量（以换行符 '\n' 分隔，去除首尾空白后为空的行不计入）。
     *
     * @param text 待统计文本，null 视为空串
     * @return 非空行数
     */
    public static int countLines(String text) {
        if (isEmpty(text)) {
            return 0;
        }
        String[] lines = text.split("\n");
        int count = 0;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 剥离清单行首的勾选符号（'√' 或 '□'）及其后紧跟的空格。
     * 若文本不以清单符号开头，则原样返回。
     *
     * @param text 待处理文本，null 视为空串
     * @return 去除清单前缀符号后的文本
     */
    public static String stripCheckTag(String text) {
        if (isEmpty(text)) {
            return "";
        }
        String result = text;
        if (result.startsWith(TAG_CHECKED)) {
            result = result.substring(TAG_CHECKED.length());
        } else if (result.startsWith(TAG_UNCHECKED)) {
            result = result.substring(TAG_UNCHECKED.length());
        }
        return result.trim();
    }

    /**
     * 超长文本截断：当文本长度超过 maxLen 时，截取前 maxLen 个字符并追加省略号；
     * 未超长则原样返回。
     *
     * @param text   待截断文本，null 视为空串
     * @param maxLen 保留的最大字符数（不含省略号），小于等于 0 时返回空串
     * @return 截断后的文本
     */
    public static String truncate(String text, int maxLen) {
        if (maxLen <= 0) {
            return "";
        }
        if (isEmpty(text)) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + ELLIPSIS;
    }

    /**
     * 统计清单模式文本的勾选进度。
     * 按换行符拆分为若干清单项，行首以 '√' 开头视为已勾选，其余非空行视为未勾选。
     *
     * @param text 清单模式文本，null 视为空串
     * @return 长度为 2 的数组：第 0 项为已勾选数量，第 1 项为清单项总数
     */
    public static int[] checkListProgress(String text) {
        int checked = 0;
        int total = 0;
        if (!isEmpty(text)) {
            String[] lines = text.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                total++;
                if (line.startsWith(TAG_CHECKED)) {
                    checked++;
                }
            }
        }
        return new int[] { checked, total };
    }

    /**
     * 判断字符是否为英文字母或数字（ASCII 范围）。
     *
     * @param c 待判断字符
     * @return 是英文字母或数字返回 true，否则返回 false
     */
    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
