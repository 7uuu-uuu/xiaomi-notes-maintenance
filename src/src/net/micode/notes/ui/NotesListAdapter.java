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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;


/**
 * 便签与文件夹主列表游标适配器类，继承自 CursorAdapter。
 * 负责将数据库查询 Cursor 绑定到 NotesListItem 列表项视图，同时维护批量编辑多选模式（mChoiceMode）
 * 下各便签项的勾选状态映射表，支持全选、取消全选、统计选中数以及导出选中便签 ID 和关联小部件集合。
 */
public class NotesListAdapter extends CursorAdapter {
    private static final String TAG = "NotesListAdapter";
    private Context mContext;
    // 记录多选模式下列表位置下标到是否勾选状态的映射表
    private HashMap<Integer, Boolean> mSelectedIndex;
    // 当前列表中普通便签（不含文件夹）的总条目数，用于判断是否已全选
    private int mNotesCount;
    // 当前列表是否处于长按触发的批量多选编辑模式
    private boolean mChoiceMode;

    /**
     * 桌面小部件属性结构体静态内部类，保存小部件实例 ID 与尺寸类型，供删除便签后刷新桌面使用。
     */
    public static class AppWidgetAttribute {
        public int widgetId;
        public int widgetType;
    };

    /**
     * 构造主列表适配器，初始游标置空并初始化多选状态映射容器。
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    @Override
    /**
     * 创建新的便签列表项自定义视图 NotesListItem。
     */
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new NotesListItem(context);
    }

    @Override
    /**
     * 将游标当前行封装为 NoteItemData，连同多选模式和勾选状态一并绑定渲染到 NotesListItem。
     */
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            NoteItemData itemData = new NoteItemData(context, cursor);
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 更新指定位置列表项的勾选状态，并调用 notifyDataSetChanged() 通知列表刷新界面。
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        notifyDataSetChanged();
    }

    // 判断当前列表是否处于批量多选模式
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    // 进入或退出批量多选模式，并清空之前的勾选记录
    public void setChoiceMode(boolean mode) {
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选或取消全选当前列表中的所有普通便签项（自动跳过文件夹项）。
     *
     * @param checked true 表示全选，false 表示取消全选
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 收集并返回当前所有已勾选便签项的数据库主键 ID 集合（用于批量删除或批量移动）。
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }

        return itemSet;
    }

    /**
     * 收集并返回当前所有已勾选便签所关联的桌面小部件属性集合（用于删除后同步更新桌面视图）。
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * Don't close cursor here, only the adapter could close it
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    // 统计并返回当前已被勾选的便签总数量
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    // 判断当前列表中的全部普通便签是否均已被勾选
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    // 查询指定下标位置的列表项当前是否处于勾选状态
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    @Override
    // 数据库内容发生变更时回调，重新统计列表中普通便签数量
    protected void onContentChanged() {
        super.onContentChanged();
        calcNotesCount();
    }

    @Override
    // 切换新查询游标时回调，更新数据源并重新统计普通便签数量
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        calcNotesCount();
    }

    /**
     * 遍历当前适配器游标中的所有记录，统计节点类型为 TYPE_NOTE 的便签总数。
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}
