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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;


/**
 * 2x2 尺寸桌面便签小部件提供者类，继承自抽象基类 NoteWidgetProvider。
 * 负责响应 2x2 小部件的更新广播，并提供 2x2 规格专属的布局、背景图片和类型常量。
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {
    @Override
    /**
     * 响应系统桌面小部件更新请求，委托父类 update 方法刷新所有 2x2 小部件视图。
     */
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        super.update(context, appWidgetManager, appWidgetIds);
    }

    @Override
    /**
     * 返回 2x2 桌面小部件的布局资源 ID（R.layout.widget_2x）。
     */
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    @Override
    /**
     * 根据背景颜色编号返回 2x2 小部件对应的背景图片资源 ID。
     */
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    @Override
    /**
     * 返回 2x2 桌面小部件类型常量（Notes.TYPE_WIDGET_2X）。
     */
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}
