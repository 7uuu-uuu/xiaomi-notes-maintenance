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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 便签查看与编辑核心界面类，继承自 Activity，同时实现控件点击、便签设置观察者与清单行变更三个监听接口。
 * 支持新建/编辑普通文本便签与通话便签、5 色背景皮肤切换、4 档字体大小调整、普通文本与清单（CheckList）
 * 模式互转、设置/取消定时闹钟提醒、搜索关键词高亮显示、第三方文本分享、发送快捷方式到桌面以及桌面小部件联动更新。
 */
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    /**
     * 编辑页顶部标题栏控件持有者内部类（包含修改时间、闹钟图标、提醒倒计时及背景色切换按钮）。
     */
    private class HeadViewHolder {
        public TextView tvModified;

        public ImageView ivAlertIcon;

        public TextView tvAlertDate;

        public ImageView ibSetBgColor;
    }

    // 背景色选择面板中 5 个颜色按钮资源 ID 到 ResourceParser 颜色编号的静态映射表
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    // 背景色编号到选择面板中对应“当前选中打勾图标”资源 ID 的静态映射表
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    // 字体大小选择面板中 4 个字号按钮 ID 到 ResourceParser 字号档位常量的静态映射表
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    // 字号档位常量到字号选择面板中对应“当前选中打勾图标”资源 ID 的静态映射表
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    private HeadViewHolder mNoteHeaderHolder;

    private View mHeadViewPanel;

    private View mNoteBgColorSelector;

    private View mFontSizeSelector;

    private EditText mNoteEditor;

    private View mNoteEditorPanel;

    // 当前正在查看或编辑的工作便签数据模型对象
    private WorkingNote mWorkingNote;

    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    // SharedPreferences 中保存全局正文字号偏好的键名
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    // 发送到桌面快捷方式图标名称的最大截取字符数
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    // 清单模式持久化存储时每行前缀的 Unicode 符号：'√'（\u221A）表示已勾选，'□'（\u25A1）表示未勾选
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');

    // 清单模式下动态承载多行复选框与 NoteEditText 的垂直线性布局容器
    private LinearLayout mEditTextList;

    // 从搜索结果跳转进入时携带的用户搜索关键词（用于正文高亮显示）
    private String mUserQuery;
    private Pattern mPattern;

    @Override
    /**
     * 界面创建回调：加载 note_edit 布局，根据启动 Intent 初始化工作便签状态，并绑定所有 UI 控件资源。
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.note_edit);

        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    @Override
    /**
     * 低内存回收后重建恢复回调：从 savedInstanceState 取出保存的便签 UID 并以查看模式重新加载便签。
     */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            if (!initActivityState(intent)) {
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");
        }
    }

    /**
     * 根据传入 Intent 的 Action 初始化当前编辑会话：
     * - ACTION_VIEW：打开已有便签或搜索结果便签（默认隐藏软键盘）；
     * - ACTION_INSERT_OR_EDIT：新建普通便签、桌面小部件便签或通话记录便签（默认弹出软键盘）。
     *
     * @param intent 启动或更新当前 Activity 的 Intent
     * @return 初始化成功返回 true；便签不存在或 Action 非法返回 false
     */
    private boolean initActivityState(Intent intent) {
        /**
         * If the user specified the {@link Intent#ACTION_VIEW} but not provided with id,
         * then jump to the NotesListActivity
         */
        mWorkingNote = null;
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";

            /**
             * Starting from the searched result
             */
            // 若由系统搜索建议列表点击进入，从 SearchManager 附加字段提取便签 ID 和搜索关键词
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                showToast(R.string.error_note_not_exist);
                finish();
                return false;
            } else {
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // New note
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // Parse call-record note
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            // 若携带通话时间和电话号码，则查找已有通话便签或新建并转换为通话记录便签
            if (callDate != 0 && phoneNumber != null) {
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    @Override
    /**
     * 界面恢复前台回调：根据 mWorkingNote 最新数据渲染编辑屏幕。
     */
    protected void onResume() {
        super.onResume();
        initNoteScreen();
    }

    /**
     * 初始化并刷新便签编辑界面：应用字号样式、按普通/清单模式填充内容（含关键词高亮）、
     * 设置标题栏与编辑区背景色、格式化最后修改时间，并更新闹钟提醒头部栏。
     */
    private void initNoteScreen() {
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mWorkingNote.getContent());
        } else {
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }
        // Apply Markdown rendering if enabled
        if (mNoteEditor instanceof NoteEditText) {
            boolean markdownEnabled = mSharedPrefs.getBoolean("pref_markdown_mode", false);
            ((NoteEditText) mNoteEditor).setMarkdownEnabled(markdownEnabled);
        }
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: Add the menu for setting alert. Currently disable it because the DateTimePicker
         * is not ready
         */
        showAlertHeader();
    }

    /**
     * 更新编辑页顶部的闹钟提醒状态栏：已设置提醒时显示闹钟图标及相对剩余时间（已过时则显示“已过期”），否则隐藏。
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {
            long time = System.currentTimeMillis();
            if (time > mWorkingNote.getAlertDate()) {
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    @Override
    /**
     * singleTop 启动模式下收到新 Intent 时回调，重新初始化目标工作便签。
     */
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        initActivityState(intent);
    }

    @Override
    /**
     * 界面可能被系统回收前保存实例状态：新便签先落库生成主键 ID，再将 noteId 存入 Bundle。
     */
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * For new note without note id, we should firstly save it to
         * generate a id. If the editing note is not worth saving, there
         * is no id which is equivalent to create new note
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    @Override
    /**
     * 全局触摸事件分发拦截：当背景色面板或字号选择面板展开时，点击面板外部区域自动收起面板。
     */
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        }

        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断用户的屏幕触摸点坐标是否落在指定 View 的屏幕矩形区域之内。
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;
        }
        return true;
    }

    /**
     * 查找并绑定编辑页全部视图控件，为背景色和字号选择按钮注册点击监听，并从偏好设置读取当前字号档位。
     */
    private void initResources() {
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        };
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        /**
         * HACKME: Fix bug of store the resource id in shared preference.
         * The id may larger than the length of resources, in this case,
         * return the {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
    }

    @Override
    /**
     * 界面暂停离开前台回调：自动保存当前编辑的便签内容，并收起打开的设置浮层面板。
     */
    protected void onPause() {
        super.onPause();
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();
    }

    /**
     * 向对应尺寸（2x2 或 4x4）的 NoteWidgetProvider 发送 APPWIDGET_UPDATE 广播，同步刷新桌面小部件显示。
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                mWorkingNote.getWidgetId()
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 处理界面点击事件：展开背景色选择面板、切换便签背景颜色，或切换并持久化正文字体大小。
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.VISIBLE);
        } else if (sBgSelectorBtnsMap.containsKey(id)) {
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            mNoteBgColorSelector.setVisibility(View.GONE);
        } else if (sFontSizeBtnsMap.containsKey(id)) {
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            mFontSizeId = sFontSizeBtnsMap.get(id);
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    @Override
    /**
     * 返回键处理：优先关闭正在显示的背景色/字号浮层；若无浮层则自动保存便签并返回上一页。
     */
    public void onBackPressed() {
        if(clearSettingState()) {
            return;
        }

        saveNote();
        super.onBackPressed();
    }

    /**
     * 检查并隐藏当前可见的背景色选择器或字号选择器浮层；若有浮层被关闭则返回 true。
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * NoteSettingChangedListener 回调：当便签背景色改变时，更新选中标记并更换编辑区与标题栏背景。
     */
    public void onBackgroundColorChanged() {
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    @Override
    /**
     * 每次展开选项菜单前动态准备菜单项：区分通话便签与普通便签菜单，动态切换清单模式与提醒菜单项的文案/显隐。
     */
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);
        }
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);
        }
        // Update pin menu title based on current state
        if (mWorkingNote.getPinned() == 1) {
            menu.findItem(R.id.menu_pin).setTitle(R.string.menu_unpin);
        } else {
            menu.findItem(R.id.menu_pin).setTitle(R.string.menu_pin);
        }
        // Update password menu visibility
        if (mWorkingNote.hasPassword()) {
            menu.findItem(R.id.menu_set_password).setVisible(false);
            menu.findItem(R.id.menu_remove_password).setVisible(true);
        } else {
            menu.findItem(R.id.menu_set_password).setVisible(true);
            menu.findItem(R.id.menu_remove_password).setVisible(false);
        }
        return true;
    }

    @Override
    /**
     * 选项菜单点击分发：处理新建便签、删除便签、调整字号、切换清单模式、分享、发送到桌面、设置及删除提醒。
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_new_note) {
            createNewNote();
        } else if (itemId == R.id.menu_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_note));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteCurrentNote();
                            finish();
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.menu_font_size) {
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                    TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (itemId == R.id.menu_send_to_desktop) {
            sendToDesktop();
        } else if (itemId == R.id.menu_alert) {
            setReminder();
        } else if (itemId == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false);
        } else if (itemId == R.id.menu_pin) {
            togglePin();
        } else if (itemId == R.id.menu_set_password) {
            setPassword();
        } else if (itemId == R.id.menu_remove_password) {
            removePassword();
        } else if (itemId == R.id.menu_markdown) {
            toggleMarkdown();
        }
        return true;
    }

    /**
     * 弹出自定义日期时间选择对话框 DateTimePickerDialog，用户确认后为当前便签设置定时提醒。
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date	, true);
            }
        });
        d.show();
    }

    /**
     * Share note to apps that support {@link Intent#ACTION_SEND} action
     * and {@text/plain} type
     */
    /**
     * 通过隐式 Intent（ACTION_SEND，text/plain）将当前便签文本内容分享至短信、邮件或社交等第三方应用。
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);
        intent.setType("text/plain");
        context.startActivity(intent);
    }

    /**
     * 在编辑页直接新建下一条便签：先保存并关闭当前便签，再在相同父文件夹下重新启动编辑界面。
     */
    private void createNewNote() {
        // Firstly, save current editing notes
        saveNote();

        // For safety, start a new NoteEditActivity
        finish();
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前正在编辑的便签：未开启云同步时直接物理删除；开启云同步时移入系统回收站文件夹，并标记为已删除。
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            // Always move to trash folder instead of permanent delete
            // This allows users to restore notes from the trash
            if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                Log.e(TAG, "Move notes to trash folder error, should not happens");
            }
        }
        mWorkingNote.markDeleted(true);
    }

    /**
     * 判断应用当前是否已绑定 Google 同步账号（处于云同步模式）。
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * NoteSettingChangedListener 回调：先确保便签已落库获取 ID，再通过系统 AlarmManager 注册或取消定时闹钟广播。
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * User could set clock to an unsaved note, so before setting the
         * alert clock, we should save the note first
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        if (mWorkingNote.getNoteId() > 0) {
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();
            if(!set) {
                alarmManager.cancel(pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * NoteSettingChangedListener 回调：关联的桌面小部件状态变更时触发桌面视图刷新。
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * OnTextViewChangeListener 回调：在清单模式下删除第 index 行，更新后续行号并将剩余文本追加合并到上一行末尾。
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;
        }

        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        mEditTextList.removeViewAt(index);
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        edit.setSelection(length);
    }

    /**
     * OnTextViewChangeListener 回调：在清单模式下按回车键时，在 index 位置插入新清单行并顺延后续行索引。
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * Should not happen, check for debug
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0);
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 将便签文本按换行符拆分为多条清单项视图并切换至清单模式（隐藏普通 EditText，显示清单容器）。
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();
        String[] items = text.split("\n");
        int index = 0;
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 使用正则表达式在便签正文中查找所有匹配搜索关键词 userQuery 的片段，并添加高亮背景色 Span。
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)), m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建单行清单项视图：绑定复选框勾选删除线（STRIKE_THRU）效果，解析行首 '√'/'□' 前缀并设置文本与高亮。
     */
    private View getListItem(String item, int index) {
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();
        }

        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * OnTextViewChangeListener 回调：根据第 index 行是否有文本或焦点，动态显示或隐藏该行左侧的复选框。
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * NoteSettingChangedListener 回调：响应普通文本模式与清单模式之间的双向切换并转换文本格式。
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            if (!getWorkingText()) {
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 收集当前界面上的最新编辑内容并同步至 mWorkingNote：
     * 清单模式下遍历所有非空行拼接 '√ '/'□ ' 前缀与换行符；普通模式下直接读取 EditText 文本。
     *
     * @return 若清单模式下至少包含一个已勾选项则返回 true，否则返回 false
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 提取当前编辑区文本并调用 mWorkingNote.saveNote() 持久化到数据库；若发生保存则设置 RESULT_OK 返回码。
     */
    private boolean saveNote() {
        getWorkingText();
        boolean saved = mWorkingNote.saveNote();
        if (saved) {
            /**
             * There are two modes from List view to edit view, open one note,
             * create/edit a node. Opening node requires to the original
             * position in the list when back from edit view, while creating a
             * new node requires to the top of the list. This code
             * {@link #RESULT_OK} is used to identify the create/edit state
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 将当前非空便签作为快捷方式图标发送到系统桌面（发送 INSTALL_SHORTCUT 广播），点击可直接打开该便签。
     */
    private void sendToDesktop() {
        /**
         * Before send message to home, we should make sure that current
         * editing note is exists in databases. So, for new note, firstly
         * save it
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));
            sender.putExtra("duplicate", true);
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            /**
             * There is the condition that user has input nothing (the note is
             * not worthy saving), we have no note id, remind the user that he
             * should input something
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成桌面快捷方式标题：剔除清单勾选符号，并将长度截断在 10 个字符以内。
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0,
                SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    // 显示短时 Toast 提示信息
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    // 按指定显示时长弹出 Toast 提示信息
    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }

    /**
     * Toggle the pinned state of the current note.
     * Pinned notes appear at the top of the note list.
     */
    private void togglePin() {
        int currentPinned = mWorkingNote.getPinned();
        int newPinned = (currentPinned == 1) ? 0 : 1;
        mWorkingNote.setPinned(newPinned);
        saveNote();
        if (newPinned == 1) {
            showToast(R.string.toast_note_pinned);
        } else {
            showToast(R.string.toast_note_unpinned);
        }
    }

    /**
     * Set a password lock for the current note.
     * Shows a dialog for entering and confirming the password.
     */
    private void setPassword() {
        final EditText passwordInput = new EditText(this);
        passwordInput.setHint(R.string.hint_password);
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_set_password_title);
        builder.setView(passwordInput);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String password = passwordInput.getText().toString();
                if (!TextUtils.isEmpty(password)) {
                    // In a real app, you would hash the password. For demo, store as-is.
                    mWorkingNote.setPassword(password);
                    saveNote();
                    showToast(R.string.password_set_success);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /**
     * Remove the password lock from the current note.
     */
    private void removePassword() {
        mWorkingNote.setPassword("");
        saveNote();
        showToast(R.string.password_removed);
    }

    /**
     * Toggle Markdown rendering mode for the current note.
     * When enabled, basic Markdown syntax (**bold**, *italic*, ~~strikethrough~~)
     * will be rendered with appropriate text styles.
     */
    private void toggleMarkdown() {
        // Store markdown preference in SharedPreferences
        SharedPreferences.Editor editor = mSharedPrefs.edit();
        boolean currentMarkdown = mSharedPrefs.getBoolean("pref_markdown_mode", false);
        boolean newMarkdown = !currentMarkdown;
        editor.putBoolean("pref_markdown_mode", newMarkdown);
        editor.commit();

        if (newMarkdown) {
            showToast(R.string.toast_markdown_enabled);
        } else {
            showToast(R.string.toast_markdown_disabled);
        }
        // Refresh the note display
        initNoteScreen();
    }
}
