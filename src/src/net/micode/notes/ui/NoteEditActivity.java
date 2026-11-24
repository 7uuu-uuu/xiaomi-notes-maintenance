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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ImageSpan;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.HistoryPolicy;
import net.micode.notes.tool.NoteImageUtils;
import net.micode.notes.tool.NoteTextUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
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

    // 本次维护新增：标题栏实时字数统计文本控件
    private TextView mWordCountView;

    // 本次维护新增：标题栏标签小条（显示当前标签）
    private TextView mTagView;

    // 当前正在查看或编辑的工作便签数据模型对象
    private WorkingNote mWorkingNote;

    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    // SharedPreferences 中保存全局正文字号偏好的键名
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    // 发送到桌面快捷方式图标名称的最大截取字符数
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    // 本次维护新增：单个便签标签允许的最大字符数（与列表页批量设置标签保持一致）
    private static final int TAG_MAX_LEN = 12;

    // 本次维护新增：正文图片插入相关
    private static final int REQUEST_PICK_IMAGE = 2001;
    private static final int REQUEST_TAKE_PHOTO = 2002;
    private static final String FILE_PROVIDER_AUTHORITY = "net.micode.notes.fileprovider";
    // 正文图片标记，与 NoteImageUtils 中的存储格式保持一致
    private static final Pattern IMAGE_MARKER_PATTERN =
            Pattern.compile("\\[img:([A-Za-z0-9_\\-]+(\\.jpg|\\.png))\\]");
    // 正文图片解码缓存，key 为“文件名@宽x高”，避免滚动、重绘时反复解码大图
    private final Map<String, Bitmap> mImageCache = new HashMap<String, Bitmap>();
    // 拍照输出 Uri，等待 onActivityResult 回读
    private Uri mPendingCameraUri;
    // 编辑区尚未完成布局时，避免重复 post 渲染图片
    private boolean mImageAttachPosted;

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
            // 本次维护新增：由应用内搜索结果进入时同样带上关键词，用于正文高亮
            if (TextUtils.isEmpty(mUserQuery)) {
                String extraQuery = intent.getStringExtra(SearchManager.USER_QUERY);
                if (!TextUtils.isEmpty(extraQuery)) {
                    mUserQuery = extraQuery;
                }
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
            // 本次维护新增：正文里的图片标记渲染成真实图片
            attachImagesToEditor();
            // 本次维护新增：由搜索进入时把光标落在首个命中处，EditText 会自动滚动到该行
            String plainText = mNoteEditor.getText().toString();
            if (!TextUtils.isEmpty(mUserQuery)) {
                int hit = plainText.toLowerCase().indexOf(mUserQuery.toLowerCase());
                mNoteEditor.setSelection(hit >= 0 ? hit : plainText.length());
            } else {
                mNoteEditor.setSelection(plainText.length());
            }
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
        // 本次维护新增：进入或刷新编辑界面时同步刷新字数统计
        updateWordCount();
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
        // 本次美化新增：编辑页标题栏返回按钮，复用系统返回逻辑（先关浮层、再保存退出）
        findViewById(R.id.btn_edit_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
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
        // 本次维护新增：标题栏 A- / A+ 快捷调节正文字号
        findViewById(R.id.btn_font_dec).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustFontSize(-1);
            }
        });
        findViewById(R.id.btn_font_inc).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                adjustFontSize(1);
            }
        });
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
        // 本次维护新增：绑定字数统计控件，并为普通文本编辑框注册文本变化监听以实时刷新字数
        mWordCountView = (TextView) findViewById(R.id.tv_word_count);
        // 本次维护新增：编辑页内的标签展示与打标签入口，避免必须回列表长按才能设置标签
        mTagView = (TextView) findViewById(R.id.tv_edit_tag);
        findViewById(R.id.btn_edit_tag).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTagDialog();
            }
        });
        // 本次维护新增：正文插入图片入口，点击弹出“相册选择 / 拍照”菜单
        findViewById(R.id.btn_insert_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageSourceMenu(v);
            }
        });
        updateTagDisplay();
        mNoteEditor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateWordCount();
            }

            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * 本次维护新增：弹框给当前便签设置标签，留空即清除标签。
     * 新建便签尚未落库时先保存一次，确保能拿到便签主键再写标签。
     */
    private void showTagDialog() {
        if (mWorkingNote == null) {
            return;
        }
        saveNote();
        final long noteId = mWorkingNote.getNoteId();
        if (noteId <= 0) {
            showToast(R.string.error_note_not_exist);
            return;
        }
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.tag_hint);
        input.setText(DataUtils.getNoteTag(getContentResolver(), noteId));
        input.setSelection(input.getText().length());
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(TAG_MAX_LEN) });
        new AlertDialog.Builder(this)
                .setTitle(R.string.tag_dialog_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String tag = input.getText().toString().trim();
                        DataUtils.setNoteTag(getContentResolver(), noteId, tag);
                        updateTagDisplay();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 本次维护新增：把当前便签的标签同步显示到标题栏小条，无标签时隐藏。
     */
    private void updateTagDisplay() {
        if (mTagView == null || mWorkingNote == null) {
            return;
        }
        String tag = DataUtils.getNoteTag(getContentResolver(), mWorkingNote.getNoteId());
        if (TextUtils.isEmpty(tag)) {
            mTagView.setVisibility(View.GONE);
        } else {
            mTagView.setText(tag);
            mTagView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 本次维护新增：弹出正文插入图片的来源选择菜单（相册 / 拍照）。
     */
    private void showImageSourceMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        getMenuInflater().inflate(R.menu.note_insert_image, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.item_pick_gallery) {
                    pickImageFromGallery();
                    return true;
                } else if (id == R.id.item_take_photo) {
                    takePhoto();
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    /**
     * 本次维护新增：打开系统相册/文件选择器挑选图片。
     */
    private void pickImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    getString(R.string.image_pick_title)), REQUEST_PICK_IMAGE);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "No gallery app available", e);
            Toast.makeText(this, R.string.image_insert_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 本次维护新增：调用系统相机拍照，输出到应用缓存目录的临时文件，
     * 通过 FileProvider 生成 content:// Uri 交给相机写入（Android 7.0 起不允许传 file://）。
     * 应用未声明 CAMERA 权限时使用 ACTION_IMAGE_CAPTURE 是官方推荐做法，无需运行时授权。
     */
    private void takePhoto() {
        File temp = new File(NoteImageUtils.getCameraTempDir(this),
                "cap_" + System.currentTimeMillis() + ".jpg");
        try {
            mPendingCameraUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, temp);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Build camera output uri failed", e);
            Toast.makeText(this, R.string.image_insert_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mPendingCameraUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
        } catch (android.content.ActivityNotFoundException e) {
            Log.e(TAG, "No camera app available", e);
            mPendingCameraUri = null;
            Toast.makeText(this, R.string.image_no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    /**
     * 本次维护新增：接收相册选择或拍照结果，统一交给后台任务压缩入库。
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        Uri source = null;
        if (requestCode == REQUEST_PICK_IMAGE && data != null) {
            source = data.getData();
        } else if (requestCode == REQUEST_TAKE_PHOTO) {
            source = mPendingCameraUri;
        }
        if (source == null) {
            return;
        }
        importAndInsertImage(source);
    }

    /**
     * 本次维护新增：后台压缩保存图片，成功后把 [img:文件名] 标记插入正文光标处。
     */
    private void importAndInsertImage(final Uri source) {
        if (mWorkingNote == null) {
            return;
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            Toast.makeText(this, R.string.image_list_mode_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... params) {
                return NoteImageUtils.importFromUri(NoteEditActivity.this, source);
            }

            protected void onPostExecute(String fileName) {
                if (isFinishing() || mNoteEditor == null) {
                    return;
                }
                if (fileName == null) {
                    Toast.makeText(NoteEditActivity.this, R.string.image_insert_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                insertImageMarker(fileName);
            }
        }.execute();
    }

    /**
     * 本次维护新增：在光标处插入独占一行的图片标记，并立即渲染成图片、落库一次。
     */
    private void insertImageMarker(String fileName) {
        Editable editable = mNoteEditor.getText();
        if (editable == null) {
            return;
        }
        int cursor = mNoteEditor.getSelectionStart();
        if (cursor < 0 || cursor > editable.length()) {
            cursor = editable.length();
        }
        boolean needLeadingBreak = cursor > 0 && editable.charAt(cursor - 1) != '\n';
        boolean needTrailingBreak = cursor < editable.length() && editable.charAt(cursor) != '\n';
        String block = (needLeadingBreak ? "\n" : "")
                + NoteImageUtils.buildMarker(fileName)
                + (needTrailingBreak ? "\n\n" : "\n");
        editable.insert(cursor, block);
        attachImagesToEditor();
        mNoteEditor.setSelection(Math.min(cursor + block.length(), editable.length()));
        // 图片插入属于明确的内容变更，立即保存一次，保证列表摘要与数据库同步
        saveNote();
    }

    /**
     * 本次维护新增：把正文里的 [img:文件名] 标记渲染成行内图片。
     * 标记文本本身保留在数据层（保存、导出、搜索都是纯文本），只在显示层替换为 ImageSpan。
     * 图片宽度按编辑区可用宽度等比缩放，高度不超过屏幕高度的 42%，避免一图占满整屏。
     */
    private void attachImagesToEditor() {
        if (mNoteEditor == null || mWorkingNote == null) {
            return;
        }
        Editable text = mNoteEditor.getText();
        if (text == null) {
            return;
        }
        // 先清除旧的图片 Span，避免重复设置导致同一标记叠加多张图
        ImageSpan[] oldSpans = text.getSpans(0, text.length(), ImageSpan.class);
        for (ImageSpan span : oldSpans) {
            text.removeSpan(span);
        }
        String plain = text.toString();
        if (plain.indexOf("[img:") < 0) {
            return;
        }
        // 编辑区尚未完成布局时宽度为 0，等下一帧再渲染一次
        if (mNoteEditor.getWidth() <= 0) {
            if (!mImageAttachPosted) {
                mImageAttachPosted = true;
                mNoteEditor.post(new Runnable() {
                    public void run() {
                        mImageAttachPosted = false;
                        attachImagesToEditor();
                    }
                });
            }
            return;
        }
        int available = mNoteEditor.getWidth() - mNoteEditor.getPaddingLeft()
                - mNoteEditor.getPaddingRight();
        if (available <= 0) {
            available = getResources().getDisplayMetrics().widthPixels - dpToPx(64);
        }
        int maxWidth = Math.max(1, available);
        int maxHeight = Math.max(1,
                (int) (getResources().getDisplayMetrics().heightPixels * 0.55f));

        Matcher matcher = IMAGE_MARKER_PATTERN.matcher(plain);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            Bitmap bitmap = loadImageBitmap(fileName, maxWidth, maxHeight);
            if (bitmap == null) {
                // 图片文件缺失时保留原文本标记，提示内容已被移除而不是静默丢弃
                continue;
            }
            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            ImageSpan imageSpan = new ImageSpan(drawable, fileName);
            text.setSpan(imageSpan, matcher.start(), matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    /**
     * 本次维护新增：按“文件名 + 目标尺寸”缓存解码结果，避免每次重绘都解码大图。
     */
    private Bitmap loadImageBitmap(String fileName, int maxWidth, int maxHeight) {
        String key = fileName + "@" + maxWidth + "x" + maxHeight;
        Bitmap cached = mImageCache.get(key);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        File file = NoteImageUtils.getImageFile(this, fileName);
        Bitmap bitmap = NoteImageUtils.decodeSized(file, maxWidth, maxHeight);
        if (bitmap != null) {
            mImageCache.put(key, bitmap);
        }
        return bitmap;
    }

    /**
     * 本次维护新增：dp 转 px，供尺寸兜底计算使用。
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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
     * 本次维护新增：退出编辑页时释放正文图片解码缓存，避免多张图片长期占用内存。
     * 回收前先摘掉编辑区上的 ImageSpan，防止已回收的位图仍被绘制。
     */
    protected void onDestroy() {
        if (mNoteEditor != null) {
            Editable text = mNoteEditor.getText();
            if (text != null) {
                ImageSpan[] spans = text.getSpans(0, text.length(), ImageSpan.class);
                for (ImageSpan span : spans) {
                    text.removeSpan(span);
                }
            }
        }
        for (Bitmap bitmap : mImageCache.values()) {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
        mImageCache.clear();
        super.onDestroy();
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

    /**
     * 本次维护新增：在现有四档字号之间上/下调一档，边界给出提示；结果写入偏好并对所有便签生效。
     */
    private void adjustFontSize(int delta) {
        int target = mFontSizeId + delta;
        if (target < ResourceParser.TEXT_SMALL || target > ResourceParser.TEXT_SUPER) {
            Toast.makeText(this, delta > 0 ? R.string.font_size_max : R.string.font_size_min,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mFontSizeId = target;
        mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
        applyFontSizeToEditor();
    }

    /**
     * 本次维护新增：把当前字号档位应用到正文（清单模式下重建列表项）。
     */
    private void applyFontSizeToEditor() {
        if (mWorkingNote == null) {
            return;
        }
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            getWorkingText();
            switchToListMode(mWorkingNote.getContent());
        } else if (mNoteEditor != null) {
            mNoteEditor.setTextAppearance(this,
                    TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        }
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
        return true;
    }

    @Override
    /**
     * 选项菜单点击分发：处理新建便签、删除便签、调整字号、切换清单模式、分享、发送到桌面、设置及删除提醒。
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_note) {
            createNewNote();
        } else if (id == R.id.menu_delete) {
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
        } else if (id == R.id.menu_font_size) {
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (id == R.id.menu_list_mode) {
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                    TextNote.MODE_CHECK_LIST : 0);
        } else if (id == R.id.menu_history) {
            // 本次维护新增：打开历史版本列表，支持把任意旧版本回滚到编辑区
            showHistoryDialog();
        } else if (id == R.id.menu_share) {
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (id == R.id.menu_send_to_desktop) {
            sendToDesktop();
        } else if (id == R.id.menu_alert) {
            setReminder();
        } else if (id == R.id.menu_delete_remind) {
            mWorkingNote.setAlertDate(0, false);
        }
        return true;
    }

    /**
     * 本次维护新增：弹出当前便签的历史版本列表。
     * 按归档时间由新到旧展示“时间 + 摘要”，点击某一版本后可将其正文回滚到编辑区；
     * 历史数据通过 Notes.CONTENT_HISTORY_URI 查询，条数上限与过期版本清理由 NotesProvider 负责。
     */
    private void showHistoryDialog() {
        if (!mWorkingNote.existInDatabase()) {
            showToast(R.string.history_empty);
            return;
        }
        final String[] versionContents = new String[HistoryPolicy.MAX_VERSIONS_PER_NOTE];
        final String[] items = new String[HistoryPolicy.MAX_VERSIONS_PER_NOTE];
        int size = 0;

        Cursor cursor = getContentResolver().query(Notes.CONTENT_HISTORY_URI,
                new String[] { Notes.HistoryColumns.ID, Notes.HistoryColumns.CONTENT,
                        Notes.HistoryColumns.SNIPPET, Notes.HistoryColumns.SAVED_DATE },
                Notes.HistoryColumns.NOTE_ID + "=?",
                new String[] { String.valueOf(mWorkingNote.getNoteId()) },
                Notes.HistoryColumns.SAVED_DATE + " DESC, " + Notes.HistoryColumns.ID + " DESC");
        if (cursor != null) {
            try {
                while (cursor.moveToNext() && size < HistoryPolicy.MAX_VERSIONS_PER_NOTE) {
                    versionContents[size] = cursor.getString(1);
                    String snippet = cursor.getString(2);
                    String time = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                            DateFormat.SHORT).format(new Date(cursor.getLong(3)));
                    items[size] = getString(R.string.history_item_format, time, snippet);
                    size++;
                }
            } finally {
                cursor.close();
            }
        }

        if (size == 0) {
            showToast(R.string.history_empty, Toast.LENGTH_LONG);
            return;
        }

        final int itemCount = size;
        final String[] shownItems = new String[itemCount];
        System.arraycopy(items, 0, shownItems, 0, itemCount);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.history_dialog_title);
        builder.setIcon(android.R.drawable.ic_menu_recent_history);
        builder.setItems(shownItems, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which < 0 || which >= itemCount) {
                    return;
                }
                final String content = versionContents[which] == null ? "" : versionContents[which];
                new AlertDialog.Builder(NoteEditActivity.this)
                        .setTitle(R.string.history_dialog_title)
                        .setMessage(R.string.history_restore_confirm)
                        .setPositiveButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        restoreFromHistory(content);
                                    }
                                })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /**
     * 本次维护新增：把指定历史版本的正文回滚到编辑区，并同步更新工作便签模型。
     * 清单模式下重建清单行视图，普通模式下直接替换编辑框文本，便于用户在该版本基础上继续修改。
     *
     * @param content 历史版本正文
     */
    private void restoreFromHistory(String content) {
        String safeContent = content == null ? "" : content;
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            mWorkingNote.setWorkingText(safeContent);
            switchToListMode(safeContent);
        } else {
            mNoteEditor.setText(getHighlightQueryResult(safeContent, mUserQuery));
            // 本次维护新增：历史版本回滚后重新渲染正文图片
            attachImagesToEditor();
            mWorkingNote.setWorkingText(safeContent);
        }
        updateWordCount();
        showToast(R.string.history_restored);
    }

    /**
     * 弹出自定义日期时间选择对话框 DateTimePickerDialog，用户确认后为当前便签设置定时提醒。
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);
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
     * 删除当前正在编辑的便签。
     * 本次维护调整：不再区分是否开启云同步，统一执行软删除（移入系统回收站），
     * 用户可在列表的回收站目录中恢复该便签或彻底删除。
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
            if (!DataUtils.moveNotesToTrash(getContentResolver(), ids)) {
                Log.e(TAG, "Move Note to trash error");
            }
            showToast(R.string.info_note_moved_to_trash, Toast.LENGTH_SHORT);
        }
        mWorkingNote.markDeleted(true);
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
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
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
     * 本次维护修复两处缺陷：①关键词可能包含 '('、'*'、'?'、'[' 等正则元字符，使用
     * {@link Pattern#quote} 将其作为字面量转义，避免抛出 PatternSyntaxException；
     * ②正文为 null 时统一按空串处理，避免 Matcher 空指针异常。
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        String safeText = fullText == null ? "" : fullText;
        SpannableString spannable = new SpannableString(safeText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(Pattern.quote(userQuery));
            Matcher m = mPattern.matcher(safeText);
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
            // 本次维护新增：从清单模式切回普通模式后重新渲染正文图片
            attachImagesToEditor();
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
        // 本次维护新增：清单文本收集完成后刷新字数统计
        updateWordCount();
        return hasChecked;
    }

    /**
     * 本次维护新增：根据当前编辑内容刷新标题栏字数统计。
     * 清单模式统计已收集到模型中的文本，普通模式直接统计编辑框当前文本；空白字符不计入字数。
     */
    private void updateWordCount() {
        if (mWordCountView == null) {
            return;
        }
        String text;
        if (mWorkingNote != null
                && mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            text = mWorkingNote.getContent();
        } else {
            text = mNoteEditor != null ? mNoteEditor.getText().toString() : "";
        }
        // 本次维护新增：图片标记不算正文字数，统计前统一替换为占位
        mWordCountView.setText(getString(R.string.word_count_format,
                NoteTextUtils.countChars(NoteImageUtils.stripMarkers(text))));
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
}