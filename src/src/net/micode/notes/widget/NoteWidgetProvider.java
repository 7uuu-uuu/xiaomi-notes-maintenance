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
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 桌面便签小部件抽象基类（采用模板方法设计模式），继承自 Android AppWidgetProvider。
 * 统一实现了小部件移除时解绑数据库记录、查询关联便签摘要与背景色、构建 RemoteViews 远程视图、
 * 处理访客隐私模式以及绑定点击跳转 PendingIntent 的公共逻辑；将具体尺寸相关的布局、背景图和
 * 类型常量交由子类（NoteWidgetProvider_2x / NoteWidgetProvider_4x）实现。
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {
    // 查询小部件关联便签信息时的 note 表字段投影（主键 ID、背景颜色、摘要文本）及列索引
    public static final String [] PROJECTION = new String [] {
        NoteColumns.ID,
        NoteColumns.BG_COLOR_ID,
        NoteColumns.SNIPPET
    };

    public static final int COLUMN_ID           = 0;
    public static final int COLUMN_BG_COLOR_ID  = 1;
    public static final int COLUMN_SNIPPET      = 2;

    private static final String TAG = "NoteWidgetProvider";

    @Override
    /**
     * 当用户从桌面移除一个或多个便签小部件时回调，将对应便签的 widget_id 重置为无效值以解除绑定。
     *
     * @param context      上下文对象
     * @param appWidgetIds 被移除的小部件实例 ID 数组
     */
    public void onDeleted(Context context, int[] appWidgetIds) {
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 查询绑定了指定 widgetId 且未被移入回收站的便签记录。
     *
     * @param context  上下文对象
     * @param widgetId 桌面小部件实例 ID
     * @return 包含便签 ID、背景色和摘要的 Cursor 游标
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 在非隐私（正常）模式下批量刷新指定桌面小部件的显示内容与背景。
     *
     * @param context          上下文对象
     * @param appWidgetManager 系统小部件管理器
     * @param appWidgetIds     待刷新的小部件 ID 数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 核心小部件渲染更新方法：遍历每个有效 widgetId，查询关联便签设置背景图和摘要文本，
     * 已关联便签点击跳转查看编辑（ACTION_VIEW），未关联便签点击新建（ACTION_INSERT_OR_EDIT），
     * 访客隐私模式下隐藏正文并跳转至便签主列表。
     *
     * @param context          上下文对象
     * @param appWidgetManager 系统小部件管理器
     * @param appWidgetIds     待刷新的小部件 ID 数组
     * @param privacyMode      是否处于访客隐私保护模式
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
            boolean privacyMode) {
        for (int i = 0; i < appWidgetIds.length; i++) {
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                int bgId = ResourceParser.getDefaultBgId(context);
                String snippet = "";
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);
                // 存在已关联的便签：读取其摘要与背景色，点击动作设为查看该便签
                if (c != null && c.moveToFirst()) {
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    snippet = c.getString(COLUMN_SNIPPET);
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    intent.setAction(Intent.ACTION_VIEW);
                } else {
                    // 尚未关联便签（或原便签已删）：显示提示语，点击动作设为新建并绑定该小部件
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                if (c != null) {
                    c.close();
                }

                // 通过子类模板方法获取布局与背景资源，构建跨进程 RemoteViews 并设置点击 PendingIntent
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);
                /**
                 * Generate the pending intent to start host for the widget
                 */
                PendingIntent pendingIntent = null;
                if (privacyMode) {
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    /**
     * 模板抽象方法：由子类返回对应尺寸小部件的背景图片资源 ID。
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 模板抽象方法：由子类返回对应尺寸小部件的布局 XML 资源 ID。
     */
    protected abstract int getLayoutId();

    /**
     * 模板抽象方法：由子类返回小部件尺寸类型标识（TYPE_WIDGET_2X 或 TYPE_WIDGET_4X）。
     */
    protected abstract int getWidgetType();
}
