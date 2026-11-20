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

package net.micode.notes.ui;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;


/**
 * 目标文件夹选择列表游标适配器类，继承自 CursorAdapter。
 * 在用户执行“移动到文件夹”操作时，将数据库查询出的可选文件夹游标绑定渲染为弹窗列表项视图。
 */
public class FoldersListAdapter extends CursorAdapter {
    // 查询文件夹列表所需的列投影（文件夹主键 ID 与文件夹名称 snippet）及列索引常量
    public static final String [] PROJECTION = {
        NoteColumns.ID,
        NoteColumns.SNIPPET
    };

    public static final int ID_COLUMN   = 0;
    public static final int NAME_COLUMN = 1;

    /**
     * 构造文件夹列表适配器。
     *
     * @param context 上下文对象
     * @param c       包含可选目标文件夹记录的 Cursor 游标
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
        // TODO Auto-generated constructor stub
    }

    @Override
    /**
     * 创建单个文件夹列表项视图 FolderListItem。
     */
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    @Override
    /**
     * 将游标当前行的文件夹数据绑定到视图：若为系统根文件夹则显示“上一级文件夹”，否则显示文件夹名。
     */
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                    .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 获取列表中指定下标位置对应的文件夹显示名称（用于移动完成后的 Toast 提示）。
     */
    public String getFolderName(Context context, int position) {
        Cursor cursor = (Cursor) getItem(position);
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER) ? context
                .getString(R.string.menu_move_parent_folder) : cursor.getString(NAME_COLUMN);
    }

    /**
     * 文件夹选择弹窗单行列表项视图内部类，加载 folder_list_item 布局并展示文件夹名称文本。
     */
    private class FolderListItem extends LinearLayout {
        private TextView mName;

        public FolderListItem(Context context) {
            super(context);
            inflate(context, R.layout.folder_list_item, this);
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        // 设置当前列表项显示的文件夹名称
        public void bind(String name) {
            mName.setText(name);
        }
    }

}
