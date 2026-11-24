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

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 界面皮肤与字体样式资源解析工具类。
 * 统一维护便签的 5 种背景颜色（黄、蓝、白、绿、红）和 4 档正文字号（小、中、大、超大）编号，
 * 并通过 4 个静态内部类将逻辑编号映射为编辑页背景、列表项分位背景、桌面小部件背景及文字外观资源 ID。
 */
public class ResourceParser {

    // 便签背景颜色索引常量：0-黄色、1-蓝色、2-白色、3-绿色、4-红色
    public static final int YELLOW           = 0;
    public static final int BLUE             = 1;
    public static final int WHITE            = 2;
    public static final int GREEN            = 3;
    public static final int RED              = 4;

    // 新建便签未开启随机颜色时的默认背景色（黄色）
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // 便签正文字体大小档位常量：0-小号、1-正常（中）、2-大号、3-超大号
    public static final int TEXT_SMALL       = 0;
    public static final int TEXT_MEDIUM      = 1;
    public static final int TEXT_LARGE       = 2;
    public static final int TEXT_SUPER       = 3;

    // 便签编辑器的默认字体大小档位（正常中号）
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    /**
     * 将颜色编号约束在合法的 0~(length-1) 区间内（本次维护新增的越界保护）。
     * 当传入编号为负数或超出数组长度时，统一回退到默认黄色（编号 0），避免数组下标越界崩溃。
     *
     * @param id     原始颜色编号
     * @param length 颜色资源数组长度
     * @return 合法区间内的颜色编号
     */
    private static int clampColorIndex(int id, int length) {
        if (id < 0 || id >= length) {
            return YELLOW;
        }
        return id;
    }

    /**
     * 便签编辑页背景图片资源映射内部类，提供编辑正文区与标题栏的 5 色 Nine-Patch 背景图。
     */
    public static class NoteBgResources {
        // 本次美化调整：改用浅色圆角纸面 drawable，替代原 9-patch 便签纸贴图
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_card_yellow,
            R.drawable.edit_card_blue,
            R.drawable.edit_card_white,
            R.drawable.edit_card_green,
            R.drawable.edit_card_red
        };

        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_header_yellow,
            R.drawable.edit_header_blue,
            R.drawable.edit_header_white,
            R.drawable.edit_header_green,
            R.drawable.edit_header_red
        };

        /**
         * 根据颜色编号获取编辑页正文区域背景 drawable 资源 ID；编号越界时回退默认黄色。
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[clampColorIndex(id, BG_EDIT_RESOURCES.length)];
        }

        /**
         * 根据颜色编号获取编辑页顶部标题栏背景 drawable 资源 ID；编号越界时回退默认黄色。
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[clampColorIndex(id, BG_EDIT_TITLE_RESOURCES.length)];
        }
    }

    /**
     * 获取新建便签的初始背景颜色编号：若用户在设置中开启了“新建便签背景颜色随机”，
     * 则在 5 种颜色中等概率随机返回一个，否则返回默认黄色。
     *
     * @param context 上下文对象，用于读取 SharedPreferences 偏好设置
     * @return 背景颜色索引 ID（0~4）
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    /**
     * 便签列表项背景图片资源映射内部类。
     * 根据便签在列表中的相对位置（首项、中间项、末项、独立单项）及文件夹类型返回对应圆角/拼接背景。
     */
    public static class NoteItemBgResources {
        // 本次美化调整：条目第一项、中间项、末项、单项统一使用独立的浅色圆角卡片，
        // 不再依赖上下拼接的 9-patch，列表呈现为一张张独立卡片
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.note_card_yellow,
            R.drawable.note_card_blue,
            R.drawable.note_card_white,
            R.drawable.note_card_green,
            R.drawable.note_card_red
        };

        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.note_card_yellow,
            R.drawable.note_card_blue,
            R.drawable.note_card_white,
            R.drawable.note_card_green,
            R.drawable.note_card_red
        };

        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.note_card_yellow,
            R.drawable.note_card_blue,
            R.drawable.note_card_white,
            R.drawable.note_card_green,
            R.drawable.note_card_red,
        };

        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.note_card_yellow,
            R.drawable.note_card_blue,
            R.drawable.note_card_white,
            R.drawable.note_card_green,
            R.drawable.note_card_red
        };

        // 获取列表首条便签背景资源 ID，编号越界时回退默认黄色
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[clampColorIndex(id, BG_FIRST_RESOURCES.length)];
        }

        // 获取列表末条便签背景资源 ID，编号越界时回退默认黄色
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[clampColorIndex(id, BG_LAST_RESOURCES.length)];
        }

        // 获取列表唯一单条便签背景资源 ID，编号越界时回退默认黄色
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[clampColorIndex(id, BG_SINGLE_RESOURCES.length)];
        }

        // 获取列表中间普通便签背景资源 ID，编号越界时回退默认黄色
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[clampColorIndex(id, BG_NORMAL_RESOURCES.length)];
        }

        // 获取文件夹列表项固定背景资源 ID（本次美化改为统一的白色圆角卡片）
        public static int getFolderBgRes() {
            return R.drawable.note_card_folder;
        }
    }

    /**
     * 桌面小部件背景资源映射内部类，分别提供 2x2 和 4x4 两种尺寸的 5 色背景图。
     */
    public static class WidgetBgResources {
        // 本次美化调整：小部件改用浅色圆角卡片，替代旧版 9-patch 彩色贴图
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_card_yellow,
            R.drawable.widget_card_blue,
            R.drawable.widget_card_white,
            R.drawable.widget_card_green,
            R.drawable.widget_card_red,
        };

        // 根据颜色编号获取 2x2 小部件背景资源 ID，编号越界时回退默认黄色
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[clampColorIndex(id, BG_2X_RESOURCES.length)];
        }

        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_card_yellow,
            R.drawable.widget_card_blue,
            R.drawable.widget_card_white,
            R.drawable.widget_card_green,
            R.drawable.widget_card_red
        };

        // 根据颜色编号获取 4x4 小部件背景资源 ID，编号越界时回退默认黄色
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[clampColorIndex(id, BG_4X_RESOURCES.length)];
        }
    }

    /**
     * 便签编辑文字外观样式（style）资源映射内部类，支持 4 级字号切换。
     */
    public static class TextAppearanceResources {
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal,
            R.style.TextAppearanceMedium,
            R.style.TextAppearanceLarge,
            R.style.TextAppearanceSuper
        };

        /**
         * 根据字号档位索引获取对应的 TextAppearance 样式资源 ID。
         * 本次维护补充了负数索引的兜底：当 id 为负数或超出资源数组长度时，统一回退到默认中号样式，
         * 避免数组下标越界抛出 ArrayIndexOutOfBoundsException。
         */
        public static int getTexAppearanceResource(int id) {
            if (id < 0 || id >= TEXTAPPEARANCE_RESOURCES.length) {
                return TEXTAPPEARANCE_RESOURCES[BG_DEFAULT_FONT_SIZE];
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 返回支持的字号样式总档位数。
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}
