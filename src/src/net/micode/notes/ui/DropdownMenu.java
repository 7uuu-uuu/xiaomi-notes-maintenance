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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉弹出菜单封装组件类。
 * 将一个标题触发按钮（Button）与 Android PopupMenu 绑定组合，用于便签列表批量多选模式（ActionMode）
 * 顶部操作栏中显示“选中了 N 项”及弹出“全选 / 取消全选”下拉菜单。
 */
public class DropdownMenu {
    // 触发弹出下拉菜单的锚点按钮控件
    private Button mButton;
    // 依附于按钮弹出的系统 PopupMenu 实例
    private PopupMenu mPopupMenu;
    // PopupMenu 内部承载的菜单对象
    private Menu mMenu;

    /**
     * 构造下拉菜单组件：设置按钮下拉箭头背景、加载菜单 XML 资源，并绑定点击按钮展开菜单事件。
     *
     * @param context 上下文对象
     * @param button  触发按钮控件
     * @param menuId  下拉菜单布局资源 ID
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置下拉菜单项被点击时的事件监听器。
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据菜单项资源 ID 查找并返回对应的 MenuItem 对象（用于动态修改“全选/取消全选”文案）。
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 更新下拉菜单触发按钮上显示的标题文本（如“选中了 3 项”）。
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
