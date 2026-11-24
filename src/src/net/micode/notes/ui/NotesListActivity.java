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
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.TrashPolicy;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * 小米便签应用主入口与便签/文件夹列表管理界面类，继承自 Activity 并实现点击与长按监听接口。
 * 维护根目录列表（NOTE_LIST）、用户子文件夹（SUB_FOLDER）和通话便签目录（CALL_RECORD_FOLDER）三种视图状态，
 * 提供异步加载便签列表、首次启动生成使用指南便签、新建/打开便签、新建/查看/重命名/删除文件夹、
 * 长按进入 ActionMode 批量删除或移动便签、导出文本到 SD 卡、触发/取消 Google Task 同步、全局搜索及跳转设置等功能。
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    // 异步数据库查询令牌（0-主列表查询，1-目标文件夹查询）及文件夹上下文菜单项 ID 常量
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;

    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;

    private static final int MENU_FOLDER_DELETE = 0;

    private static final int MENU_FOLDER_VIEW = 1;

    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    // 本次维护新增：批量便签操作动作标识，供 BatchActionTask 区分移入回收站、恢复与彻底删除
    private static final int BATCH_ACTION_TRASH = 0;

    private static final int BATCH_ACTION_RESTORE = 1;

    private static final int BATCH_ACTION_DELETE_FOREVER = 2;

    // SharedPreferences 布尔键名：记录是否已在首次启动时插入过内置欢迎介绍便签
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    // 本次维护新增：排序方式偏好键，true 表示按创建时间排序，false 表示按修改时间排序
    private static final String PREFERENCE_SORT_BY_CREATED = "net.micode.notes.sort_by_created";

    /**
     * 主列表当前所处的目录层级状态枚举：根目录主列表、普通用户子文件夹、系统通话便签文件夹、
     * 回收站文件夹。回收站为本次维护新增状态，用于区分删除语义（软删除 / 恢复 / 彻底删除）。
     */
    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER, TRASH_FOLDER
    };

    // 当前界面所处的列表浏览状态
    private ListEditState mState;

    // 负责在后台线程异步查询便签和文件夹游标的处理器
    private BackgroundQueryHandler mBackgroundQueryHandler;

    // 便签主列表 ListView 绑定的游标适配器
    private NotesListAdapter mNotesListAdapter;

    // 展示文件夹与便签条目的核心列表控件
    private ListView mNotesListView;

    // 界面底部“新建便签”按钮控件
    private Button mAddNewNote;

    private boolean mDispatch;

    private int mOriginY;

    private int mDispatchY;

    private TextView mTitleBar;

    // 本次美化新增：标题栏容器（承载返回箭头与标题文本）
    private LinearLayout mTitleBarLayout;
    // 本次维护新增：空态提示与标题基线文本（标签筛选时追加展示）
    private TextView mEmptyView;
    private String mBaseTitle = "";
    // 本次维护新增：左右滑手势的按下坐标、命中位置与状态标记
    private float mSwipeDownX;
    private float mSwipeDownY;
    private int mSwipePosition = -1;
    private boolean mSwiping;
    private boolean mSwipeTriggered;
    // 本次维护新增：最近一次按下期间的横向位移，用于区分“长按多选”与“横向滑动”
    private float mSwipeLastDx;
    // 本次维护新增：底部“撤销删除”提示条及其待恢复便签 ID 集合
    private View mUndoBar;
    private HashSet<Long> mPendingUndoIds;
    // 本次维护新增：双列网格视图与视图模式偏好（false=单列列表，true=双列网格）
    private GridView mNotesGridView;
    private boolean mGridViewMode;
    private static final String PREFERENCE_VIEW_MODE = "pref_grid_view_mode";
    private final Runnable mHideUndoRunnable = new Runnable() {
        public void run() {
            if (mUndoBar != null) {
                mUndoBar.setVisibility(View.GONE);
            }
            mPendingUndoIds = null;
        }
    };

    // 本次美化新增：子目录标题栏左侧的返回箭头
    private ImageView mBtnBack;

    // 本次维护新增：搜索栏、关键词输入框与搜索按钮，以及当前搜索关键词/标签筛选条件
    private LinearLayout mSearchBar;

    private EditText mSearchInput;

    private ImageView mBtnSearch;

    private boolean mSearchActive;

    private String mKeyword = "";

    private String mTagFilter = "";

    // 当前正在浏览的文件夹 ID（默认为 ID_ROOT_FOLDER=0）
    private long mCurrentFolderId;

    private ContentResolver mContentResolver;

    // 长按便签进入顶部批量多选操作栏（ActionMode）的回调处理器
    private ModeCallback mModeCallBack;

    private static final String TAG = "NotesListActivity";

    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;

    // 用户最近一次长按选中的便签或文件夹列表项数据
    private NoteItemData mFocusNoteDataItem;

    // 子文件夹视图下的 SQL 过滤条件：按 parent_id 查询下属便签
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";

    // 根目录视图下的 SQL 过滤条件：查询根目录下所有非系统节点，以及非空的通话便签系统文件夹；
    // 本次维护新增：回收站作为常驻入口一并纳入根目录查询，否则系统文件夹会被整体排除而无法进入回收站
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_TRASH_FOLER + ")";

    // 跳转 NoteEditActivity 打开已有便签（102）和新建便签（103）的请求码常量
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;

    @Override
    /**
     * 主界面创建回调：加载 note_list 布局，初始化视图与适配器资源，并在首次安装启动时写入内置介绍便签。
     */
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();

        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }

    @Override
    /**
     * 从编辑界面返回结果回调：若便签已新建或修改（RESULT_OK），先置空旧游标以便 onStart 重新查询刷新列表。
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 检查首次启动标志；若首次运行则读取 res/raw/introduction 资源文本，在根目录下自动创建一条红色介绍便签。
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if(in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    @Override
    /**
     * 界面可见启动回调：发起异步数据库查询以加载当前目录下的最新便签与文件夹列表。
     */
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    /**
     * 初始化 ContentResolver、异步查询器、ListView（含底部视图、点击/长按监听与适配器）、
     * 新建便签按钮及批量多选回调对象，初始状态设为根目录 NOTE_LIST。
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mNotesListView = (ListView) findViewById(R.id.notes_list);
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        // 本次维护新增：列表项左右滑动——右滑切换置顶，左滑删除（进回收站）
        mNotesListView.setOnTouchListener(mSwipeTouchListener);
        mNotesListAdapter = new NotesListAdapter(this);
        // 本次维护新增：列表行内“更多操作”按钮统一由本页处理
        mNotesListAdapter.setItemMenuClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showItemMenu(v);
            }
        });
        mNotesListView.setAdapter(mNotesListAdapter);
        // 本次维护新增：双列网格视图，与列表共用同一适配器（切换时只挂载到当前显示的视图）
        mNotesGridView = (GridView) findViewById(R.id.notes_grid);
        mNotesGridView.setOnItemClickListener(new OnListItemClickListener());
        mNotesGridView.setOnItemLongClickListener(this);
        mNotesGridView.setOnTouchListener(mSwipeTouchListener);
        // 本次维护新增：恢复上次选择的视图模式
        mGridViewMode = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREFERENCE_VIEW_MODE, false);
        applyViewMode(mGridViewMode);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        // 本次维护新增：底部撤销删除提示条
        mUndoBar = findViewById(R.id.undo_bar);
        View undoButton = findViewById(R.id.btn_undo);
        if (undoButton != null) {
            undoButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    undoLastDelete();
                }
            });
        }
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        // 本次美化新增：标题栏容器与返回箭头，根目录显示“全部便签”，子目录显示返回箭头
        mTitleBarLayout = (LinearLayout) findViewById(R.id.title_bar_layout);
        mBtnBack = (ImageView) findViewById(R.id.btn_back);
        mBtnBack.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                goToRootFolder();
            }
        });
        mTitleBar.setText(R.string.all_notes_name);
        mTitleBarLayout.setVisibility(View.VISIBLE);
        // 本次维护新增：记录标题基线文本，标签筛选时在其后追加筛选标签
        mBaseTitle = mTitleBar.getText().toString();
        mEmptyView = (TextView) findViewById(R.id.tv_empty);
        mBtnBack.setVisibility(View.GONE);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
        initSearchBar();
        initTitleBarMenu();
    }

    /**
     * 本次维护新增：初始化搜索栏。点击放大镜展开搜索栏并自动聚焦输入；
     * 输入变化时按关键词重新查询（摘要与正文同时参与匹配），关闭按钮清空并退出搜索态。
     */
    private void initSearchBar() {
        mSearchBar = (LinearLayout) findViewById(R.id.search_bar);
        mSearchInput = (EditText) findViewById(R.id.et_search);
        mBtnSearch = (ImageView) findViewById(R.id.btn_search);

        mBtnSearch.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openSearch();
            }
        });
        findViewById(R.id.btn_search_close).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeSearch();
            }
        });

        mSearchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                mKeyword = s == null ? "" : s.toString().trim();
                if (mSearchActive) {
                    startAsyncNotesListQuery();
                }
            }
        });
        mSearchInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                // 回车即收起键盘，让列表占满屏幕便于查看结果
                InputMethodManager imm = (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
                }
                return false;
            }
        });
    }

    /**
     * 本次维护新增：初始化右上角常驻菜单按钮。点击后就地弹出与选项菜单一致的菜单项，
     * 免去必须按 MENU 键（或从屏幕底部溢出菜单）才能访问排序/筛选等功能的麻烦。
     */
    private void initTitleBarMenu() {
        View btnMenu = findViewById(R.id.btn_menu);
        if (btnMenu == null) {
            return;
        }
        btnMenu.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showTitleBarMenu(v);
            }
        });
    }

    /**
     * 本次维护新增：按当前列表状态（根目录/子文件夹/通话便签/回收站）弹出对应菜单。
     */
    private void showTitleBarMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        }
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                return onOptionsItemSelected(item);
            }
        });
        popup.show();
    }

    /**
     * 本次维护新增：进入搜索态——隐藏标题栏、展示搜索栏并弹出键盘。
     */
    private void openSearch() {
        if (mSearchActive) {
            return;
        }
        mSearchActive = true;
        mTitleBarLayout.setVisibility(View.GONE);
        mSearchBar.setVisibility(View.VISIBLE);
        mSearchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(mSearchInput, InputMethodManager.SHOW_IMPLICIT);
        }
        startAsyncNotesListQuery();
    }

    /**
     * 本次维护新增：退出搜索态——清空关键词、恢复标题栏并按当前目录重新加载列表。
     */
    private void closeSearch() {
        mSearchActive = false;
        mKeyword = "";
        mSearchInput.setText("");
        mSearchBar.setVisibility(View.GONE);
        mTitleBarLayout.setVisibility(View.VISIBLE);
        InputMethodManager imm = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(mSearchInput.getWindowToken(), 0);
        }
        startAsyncNotesListQuery();
    }

    /**
     * 列表批量多选编辑模式（Contextual ActionMode）回调内部类。
     * 管理多选状态下的顶部全选下拉菜单（DropdownMenu）以及底部批量“删除”与“移动到文件夹”操作。
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;
        private ActionMode mActionMode;
        private MenuItem mMoveMenu;
        // 本次维护新增：批量置顶菜单项，标题随选中项置顶状态切换
        private MenuItem mPinMenu;

        /**
         * 进入批量多选模式：加载删除/移动菜单（通话目录或无用户文件夹时隐藏移动），开启复选框并绑定全选下拉菜单。
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            MenuItem deleteItem = menu.findItem(R.id.delete);
            deleteItem.setOnMenuItemClickListener(this);
            // 本次美化调整：删除/移动改为纯文字按钮，去掉旧版贴图图标，操作栏更清爽
            deleteItem.setIcon(null);
            deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            mMoveMenu = menu.findItem(R.id.move);
            mMoveMenu.setIcon(null);
            boolean inTrash = (mCurrentFolderId == Notes.ID_TRASH_FOLER);
            // 本次维护新增：回收站内隐藏“移动到文件夹”，并把“删除”改写为“彻底删除”，
            // 同时启用“恢复”菜单项；非回收站目录则隐藏“恢复”
            menu.findItem(R.id.restore).setVisible(inTrash);
            // 本次维护新增：置顶与标签仅对普通便签有效，回收站内隐藏置顶
            mPinMenu = menu.findItem(R.id.pin);
            mPinMenu.setVisible(!inTrash);
            mPinMenu.setOnMenuItemClickListener(this);
            menu.findItem(R.id.tag).setOnMenuItemClickListener(this);
            if (inTrash) {
                deleteItem.setTitle(R.string.menu_delete_forever);
                mMoveMenu.setVisible(false);
                menu.findItem(R.id.restore).setOnMenuItemClickListener(this);
            } else if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);
            // 本次维护新增：进入多选时收起搜索栏，保证批量操作栏与列表完整可见
            if (mSearchActive) {
                mSearchActive = false;
                mKeyword = "";
                mSearchInput.setText("");
                mSearchBar.setVisibility(View.GONE);
                mTitleBarLayout.setVisibility(View.VISIBLE);
            }

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                /**
                 * 批量操作菜单点击回调：点击“删除”弹出确认框后执行批量删除；点击“移动”异步查询可选目标文件夹。
                 */
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }

            });
            return true;
        }

        /**
         * 刷新多选操作栏顶部标题的已选数量，并根据是否已全部选中动态切换“全选 / 取消全选”菜单文案。
         */
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            return false;
        }

        /**
         * 退出批量多选模式：关闭适配器复选框模式，恢复列表长按监听，并重新显示底部“新建便签”按钮。
         */
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            // 本次维护修正：回收站与通话便签是只读目录，退出批量模式后不应重新显示“新建便签”按钮
            if (mCurrentFolderId == Notes.ID_TRASH_FOLER
                    || mCurrentFolderId == Notes.ID_CALL_RECORD_FOLDER) {
                mAddNewNote.setVisibility(View.GONE);
            } else {
                mAddNewNote.setVisibility(View.VISIBLE);
            }
            // 本次维护修正：Activity 配置了 splitActionBarWhenNarrow，ActionMode 的按钮栏位于
            // 屏幕底部。ListView.startActionMode 会把列表切到 CHOICE_MODE_MULTIPLE_MODAL，
            // 若只清空适配器的选中下标而不复位列表的 choice mode，该底部容器不会收缩，
            // 会留下一条只剩“删除”文字的空白栏。这里显式复位并强制重新布局。
            mNotesListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        if (mNotesGridView != null) {
            mNotesGridView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        }
        // 本页 ActionBar 在主题里被设为 gone，只保留自定义标题栏；但 split 模式下的底部
        // ActionMode 容器复用了同一套 ActionBar 管理逻辑，ActionMode 结束后容器可能残留。
        // 显式 hide 一次 ActionBar，让框架重新计算底部分栏的可见性。
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        final View decor = getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override
            public void run() {
                decor.requestLayout();
                decor.invalidate();
            }
        });
        }

        // 主动结束并关闭当前的批量多选 ActionMode（可安全重复调用）
        public void finishActionMode() {
            if (mActionMode != null) {
                mActionMode.finish();
                mActionMode = null;
            }
        }

        // 单条便签勾选状态变化时更新适配器并刷新顶部计数菜单
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
            // 本次维护新增：根据当前选中集合的置顶状态切换菜单文案
            if (mPinMenu != null && mPinMenu.isVisible()) {
                boolean allPinned = DataUtils.areAllNotesPinned(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds());
                mPinMenu.setTitle(allPinned ? R.string.menu_unpin : R.string.menu_pin);
            }
        }

        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            // 本次维护修正：多选快照必须在退出多选模式之前取好。
            // setChoiceMode(false) 会清空适配器的选中下标，若先 finishActionMode 再读选中项，
            // 拿到的永远是空集合，表现为“点了确认但什么都没发生”。
            final HashSet<Long> selectedIds = mNotesListAdapter.getSelectedItemIds();
            final HashSet<AppWidgetAttribute> selectedWidgets = mNotesListAdapter.getSelectedWidget();
            int id = item.getItemId();
            boolean inTrash = (mCurrentFolderId == Notes.ID_TRASH_FOLER);
            if (id == R.id.delete) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                // 本次维护新增：回收站内的删除为不可恢复的彻底删除，提示文案与普通删除区分
                builder.setMessage(inTrash
                        ? getString(R.string.alert_message_delete_forever,
                                mNotesListAdapter.getSelectedCount())
                        : getString(R.string.alert_message_delete_notes,
                                mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                // 本次维护修正：这里只负责执行操作，不再提前调用 finishActionMode()。
                                // 提前 finish 会先清空适配器选中集合（导致批量操作为空），
                                // 且对话框尚未关闭时 finish 可能被吞掉、留下残留的操作栏。
                                // 关闭多选模式统一交给 BatchActionTask.onPostExecute。
                                if (inTrash) {
                                    batchDeleteForever(selectedIds, selectedWidgets);
                                } else {
                                    batchDelete(selectedIds, selectedWidgets);
                                }
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (id == R.id.restore) {
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.menu_restore));
                builder.setMessage(getString(R.string.alert_message_restore_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // 恢复同理：快照已在弹出菜单时取好，关闭多选模式交给任务完成回调
                                batchRestore(selectedIds, selectedWidgets);
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
            } else if (id == R.id.move) {
                startQueryDestinationFolders();
            } else if (id == R.id.pin) {
                // 本次维护新增：整组已置顶则取消，否则全部置顶；完成后退出多选并刷新列表
                boolean allPinned = DataUtils.areAllNotesPinned(mContentResolver, selectedIds);
                DataUtils.setNotesPinned(mContentResolver, selectedIds, !allPinned);
                Toast.makeText(NotesListActivity.this,
                        allPinned ? R.string.menu_unpin : R.string.menu_pin,
                        Toast.LENGTH_SHORT).show();
                finishActionMode();
                startAsyncNotesListQuery();
            } else if (id == R.id.tag) {
                showTagInputDialog(selectedIds);
            } else {
                return false;
            }
            return true;
        }
    }

    /**
     * 本次维护新增：批量设置标签输入框。单条便签时预填现有标签，留空提交即清除标签。
     */
    private void showTagInputDialog(final HashSet<Long> ids) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.tag_hint);
        if (ids != null && ids.size() == 1) {
            input.setText(DataUtils.getNoteTag(mContentResolver, ids.iterator().next()));
            input.setSelection(input.getText().length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tag_dialog_title);
        builder.setView(input);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String tag = input.getText() == null ? "" : input.getText().toString().trim();
                if (tag.length() > 12) {
                    // 标签过长会挤坏列表行布局，统一截断到 12 个字符
                    tag = tag.substring(0, 12);
                }
                for (Long id : ids) {
                    DataUtils.setNoteTag(mContentResolver, id.longValue(), tag);
                }
                finishActionModeIfNeeded();
                startAsyncNotesListQuery();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /**
     * 本次维护新增：若当前仍处于多选模式则退出，供标签设置等不需要后台任务的操作使用。
     */
    private void finishActionModeIfNeeded() {
        if (mModeCallBack != null) {
            mModeCallBack.finishActionMode();
        }
    }

    /**
     * “新建便签”按钮触摸透传监听内部类。
     * 当用户点击按钮上方不规则透明区域（y < -0.12x + 94）时，将触摸事件换算坐标后透传给底层的 ListView。
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    /**
                     * Minus TitleBar's height
                     */
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBarLayout.getHeight();
                        start -= mTitleBarLayout.getHeight();
                    }
                    /**
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94（Unit:pixel）
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }

    };

    /**
     * 本次维护新增：列表项左右滑动手势。
     * 按下时记录起点与命中的列表位置，横向位移明显大于纵向时判定为滑动；
     * 抬手时右滑切换置顶、左滑弹确认框后删除（进回收站）。
     * 文件夹类条目与回收站目录不响应手势，避免与点击/长按多选冲突。
     */
    private final View.OnTouchListener mSwipeTouchListener = new View.OnTouchListener() {
        private final float[] mSlopAndThreshold = new float[2];

        public boolean onTouch(View v, MotionEvent event) {
            if (mSlopAndThreshold[0] <= 0) {
                float density = getResources().getDisplayMetrics().density;
                mSlopAndThreshold[0] = 12f * density;
                mSlopAndThreshold[1] = 96f * density;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 多选模式下不识别滑动手势，避免与批量操作冲突
                    if (mNotesListAdapter != null && mNotesListAdapter.isInChoiceMode()) {
                        mSwipePosition = -1;
                        return false;
                    }
                    mSwipeDownX = event.getX();
                    mSwipeDownY = event.getY();
                    mSwipePosition = pointToPosition(v, (int) mSwipeDownX,
                            (int) mSwipeDownY);
                    mSwiping = false;
                    mSwipeTriggered = false;
                    mSwipeLastDx = 0;
                    break;
                case MotionEvent.ACTION_MOVE: {
                    if (mSwipePosition < 0) {
                        break;
                    }
                    float dx = event.getX() - mSwipeDownX;
                    float dy = event.getY() - mSwipeDownY;
                    mSwipeLastDx = dx;
                    if (!mSwiping && Math.abs(dx) > mSlopAndThreshold[0]
                            && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        mSwiping = true;
                        v.cancelLongPress();
                        // 本次调整：首个判定为滑动的 MOVE 也直接消费。放行会给 ListView 抢走手势，
                        // 之后收不到 UP 导致滑动动作不执行；长按由 cancelLongPress 与 onItemLongClick
                        // 里的横向位移判断兜住，不会再弹多选或菜单
                        return true;
                    }
                    if (mSwiping) {
                        // 之后的事件全部消费，阻止列表把这次横向拖动当成滚动
                        return true;
                    }
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    boolean wasSwiping = mSwiping;
                    Log.d(TAG, "swipe UP: swiping=" + mSwiping + " pos=" + mSwipePosition);
                    if (mSwiping && mSwipePosition >= 0) {
                        float dx = event.getX() - mSwipeDownX;
                        if (Math.abs(dx) >= mSlopAndThreshold[1]) {
                            handleSwipeItem(mSwipePosition, dx > 0);
                            mSwipeTriggered = true;
                        }
                    }
                    mSwiping = false;
                    mSwipePosition = -1;
                    mSwipeLastDx = 0;
                    // 横向拖动一律不当作点击，避免轻微滑动误打开便签
                    if (wasSwiping) {
                        return true;
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    mSwiping = false;
                    mSwipePosition = -1;
                    mSwipeLastDx = 0;
                    break;
                default:
                    break;
            }
            return mSwipeTriggered;
        }
    };

    /**
     * 本次维护新增：彻底退出多选状态，包含关闭操作栏、重置列表选择模式与清理 split 底栏残留。
     */
    private void hardResetChoiceMode() {
        if (mModeCallBack != null) {
            mModeCallBack.finishActionMode();
        }
        if (mNotesListAdapter != null && mNotesListAdapter.isInChoiceMode()) {
            mNotesListAdapter.setChoiceMode(false);
        }
        if (mNotesListView != null) {
            mNotesListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        }
        if (mNotesGridView != null) {
            mNotesGridView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        }
        if (getActionBar() != null) {
            getActionBar().hide();
        }
        final View decor = getWindow().getDecorView();
        decor.post(new Runnable() {
            public void run() {
                decor.requestLayout();
                decor.invalidate();
            }
        });
    }

    /**
     * 本次维护新增：执行滑动动作。右滑切换置顶，左滑弹出删除确认。
     */
    private void handleSwipeItem(int position, boolean toRight) {
        Log.d(TAG, "handleSwipeItem pos=" + position + " right=" + toRight);
        // 防御：模拟器上事件处理较慢时，长按可能先于滑动判定而拉起多选，这里立即完整收起
        hardResetChoiceMode();
        // 再延迟兜底一次：个别时序下长按计时器会在滑动判定之后才拉起操作栏
        mNotesListView.postDelayed(new Runnable() {
            public void run() {
                hardResetChoiceMode();
            }
        }, 800);
        Object item = getActiveList().getItemAtPosition(position);
        if (!(item instanceof Cursor)) {
            return;
        }
        NoteItemData data = new NoteItemData(this, (Cursor) item);
        if (data.getType() != Notes.TYPE_NOTE || mCurrentFolderId == Notes.ID_TRASH_FOLER) {
            return;
        }
        final long noteId = data.getId();
        if (toRight) {
            boolean pinned = data.isPinned();
            HashSet<Long> ids = new HashSet<Long>();
            ids.add(noteId);
            if (DataUtils.setNotesPinned(mContentResolver, ids, !pinned)) {
                Toast.makeText(this, pinned ? R.string.swipe_unpinned : R.string.swipe_pinned,
                        Toast.LENGTH_SHORT).show();
                startAsyncNotesListQuery();
            }
        } else {
            // 本次维护新增：左滑直接移入回收站，底部给出 5 秒内可撤销的提示条
            HashSet<Long> ids = new HashSet<Long>();
            ids.add(noteId);
            deleteNotesWithUndo(ids);
        }
    }

    /**
     * 启动异步查询加载当前文件夹列表。
     * 本次维护新增：三种查询形态——①搜索态走全文搜索 URI（摘要+正文，置顶优先）；
     * ②普通列表按“文件夹在前、置顶优先、修改/创建时间倒序”排序；③带标签筛选时追加 tag 条件。
     * 搜索结果为空时给出提示，避免用户面对空列表不知所措。
     */
    private void startAsyncNotesListQuery() {
        // 本次维护新增：标签筛选时在标题上标注当前标签，避免用户忘记处于筛选状态
        updateTitleWithTagFilter();
        // 本次维护新增：把当前关键词交给适配器，用于列表摘要中高亮命中的关键字
        mNotesListAdapter.setHighlightKeyword(
                (mSearchActive && !TextUtils.isEmpty(mKeyword)) ? mKeyword : null);
        // 搜索态且已输入关键词：忽略当前目录，按关键词在全部未删除便签中搜索；
        // 关键词为空时不走搜索（否则会得到空列表），仍按当前目录正常展示
        if (mSearchActive && !TextUtils.isEmpty(mKeyword)) {
            Uri searchUri = Notes.CONTENT_TEXT_SEARCH_URI.buildUpon()
                    .appendQueryParameter("pattern", mKeyword).build();
            mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                    searchUri, NoteItemData.PROJECTION, null, null, null);
            return;
        }

        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        String[] selectionArgs = new String[] { String.valueOf(mCurrentFolderId) };

        // 本次维护新增：按标签筛选时，在原有目录条件后追加 tag 等值条件
        if (!TextUtils.isEmpty(mTagFilter)) {
            selection = "(" + selection + ") AND " + NoteColumns.TAG_LABEL + "=?";
            selectionArgs = new String[] { String.valueOf(mCurrentFolderId), mTagFilter };
        }

        // 回收站内按进入回收站的时间倒序展示；其余目录按“文件夹前置 + 置顶优先 + 时间倒序”
        String sortOrder;
        if (mCurrentFolderId == Notes.ID_TRASH_FOLER) {
            sortOrder = NoteColumns.DELETED_DATE + " DESC," + NoteColumns.ID + " DESC";
        } else {
            boolean byCreated = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean(PREFERENCE_SORT_BY_CREATED, false);
            sortOrder = NoteColumns.PINNED + " DESC," + NoteColumns.TYPE + " DESC,"
                    + (byCreated ? NoteColumns.CREATED_DATE : NoteColumns.MODIFIED_DATE) + " DESC";
        }

        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, selectionArgs,
                sortOrder);
    }

    /**
     * 本次维护新增：弹出排序方式选择框（按修改时间 / 按创建时间），选择后写入偏好并刷新列表。
     */
    private void showSortDialog() {
        boolean byCreated = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREFERENCE_SORT_BY_CREATED, false);
        final String[] items = new String[] {
                getString(R.string.sort_by_modified), getString(R.string.sort_by_created)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(items, byCreated ? 1 : 0,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                PreferenceManager.getDefaultSharedPreferences(NotesListActivity.this)
                                        .edit()
                                        .putBoolean(PREFERENCE_SORT_BY_CREATED, which == 1)
                                        .apply();
                                dialog.dismiss();
                                startAsyncNotesListQuery();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 本次维护新增：根据查询结果与当前条件控制空态提示文案。
     */
    private void updateEmptyView(Cursor cursor) {
        if (mEmptyView == null) {
            return;
        }
        int count = (cursor == null) ? 0 : cursor.getCount();
        if (count > 0) {
            mEmptyView.setVisibility(View.GONE);
        } else if (mSearchActive && !TextUtils.isEmpty(mKeyword)) {
            mEmptyView.setText(R.string.search_no_result);
            mEmptyView.setVisibility(View.VISIBLE);
        } else if (!TextUtils.isEmpty(mTagFilter)) {
            mEmptyView.setText(R.string.tag_filter_no_result);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.GONE);
        }
    }

    /**
     * 本次维护新增：把标签筛选状态同步到标题栏，例如“全部便签 · work”。
     */
    private void updateTitleWithTagFilter() {
        if (TextUtils.isEmpty(mBaseTitle)) {
            return;
        }
        if (TextUtils.isEmpty(mTagFilter)) {
            mTitleBar.setText(mBaseTitle);
        } else {
            mTitleBar.setText(mBaseTitle + " · " + mTagFilter);
        }
    }

    /**
     * 本次维护新增：弹出标签筛选对话框。列出库中已使用的全部标签，"显示全部便签"用于清除筛选。
     */
    private void showTagFilterDialog() {
        ArrayList<String> tags = DataUtils.getAllTags(mContentResolver);
        if (tags.isEmpty()) {
            Toast.makeText(this, R.string.tag_filter_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] items = new String[tags.size() + 1];
        items[0] = getString(R.string.tag_filter_all);
        for (int i = 0; i < tags.size(); i++) {
            items[i + 1] = tags.get(i);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.tag_filter_title)
                .setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        mTagFilter = (which == 0) ? "" : items[which];
                        startAsyncNotesListQuery();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 异步 ContentResolver 查询处理器内部类，避免在 UI 主线程执行数据库查询造成卡顿。
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        /**
         * 异步查询完成回调：根据 token 更新主列表游标，或弹出“选择目标文件夹”移动对话框。
         */
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    // 本次维护新增：搜索/标签筛选无结果时给出空态提示，避免只剩一片空白
                    updateEmptyView(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    /**
     * 弹出目标文件夹选择对话框，用户点击某文件夹后将所有已勾选便签批量移入该文件夹并退出多选模式。
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /**
     * 携带当前所在文件夹 ID 启动 NoteEditActivity（ACTION_INSERT_OR_EDIT）以新建一条便签。
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 启动后台 AsyncTask 批量删除已勾选便签。
     * 本次维护调整：删除语义统一为“软删除”，无论是否开启同步，都先把便签移入回收站，
     * 由用户在回收站中决定恢复还是彻底删除，避免误删后数据不可挽回。
     * 完成后在主线程通知所有受影响的桌面小部件刷新并关闭多选操作栏。
     * 入参为主线程提前取好的多选快照，不能再从适配器实时读取（那时选中态已清空）。
     */
    private void batchDelete(HashSet<Long> ids, HashSet<AppWidgetAttribute> widgets) {
        new BatchActionTask(BATCH_ACTION_TRASH, widgets).execute(ids);
    }

    /**
     * 本次维护新增：把回收站中已勾选的便签恢复到删除前所在的文件夹。
     */
    private void batchRestore(HashSet<Long> ids, HashSet<AppWidgetAttribute> widgets) {
        new BatchActionTask(BATCH_ACTION_RESTORE, widgets).execute(ids);
    }

    /**
     * 本次维护新增：把回收站中已勾选的便签彻底删除（物理删除，级联清理正文与历史版本）。
     */
    private void batchDeleteForever(HashSet<Long> ids, HashSet<AppWidgetAttribute> widgets) {
        new BatchActionTask(BATCH_ACTION_DELETE_FOREVER, widgets).execute(ids);
    }

    /**
     * 本次维护新增：批量便签操作后台任务，统一处理“移入回收站 / 恢复 / 彻底删除”三种动作，
     * 避免三份几乎相同的 AsyncTask 代码重复实现桌面小部件刷新逻辑。
     */
    private class BatchActionTask extends
            AsyncTask<HashSet<Long>, Void, HashSet<AppWidgetAttribute>> {
        private final int mAction;
        private final HashSet<AppWidgetAttribute> mWidgets;

        BatchActionTask(int action, HashSet<AppWidgetAttribute> widgets) {
            mAction = action;
            mWidgets = widgets;
        }

        protected HashSet<AppWidgetAttribute> doInBackground(HashSet<Long>... params) {
            HashSet<Long> ids = (params != null && params.length > 0) ? params[0] : null;
            boolean success;
            if (mAction == BATCH_ACTION_RESTORE) {
                success = DataUtils.restoreNotes(mContentResolver, ids);
            } else if (mAction == BATCH_ACTION_DELETE_FOREVER) {
                success = DataUtils.batchDeleteNotes(mContentResolver, ids);
            } else {
                success = DataUtils.moveNotesToTrash(mContentResolver, ids);
            }
            if (!success) {
                Log.e(TAG, "Batch note action failed, action:" + mAction);
            }
            return mWidgets;
        }

        @Override
        protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
            if (widgets != null) {
                for (AppWidgetAttribute widget : widgets) {
                    if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                            && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                        updateWidget(widget.widgetId, widget.widgetType);
                    }
                }
            }
            mModeCallBack.finishActionMode();
            // 本次维护修正：批量操作完成后主动重查列表，保证界面与数据库一致
            startAsyncNotesListQuery();
        }
    }

    /**
     * 删除指定用户文件夹。
     * 本次维护调整：文件夹删除同样改为软删除（整体移入回收站），其下便签由数据库触发器
     * 联动移入回收站并记录删除时间，可整体恢复；同时刷新该文件夹内便签绑定的全部桌面小部件。
     */
    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!DataUtils.moveNotesToTrash(mContentResolver, ids)) {
            Log.e(TAG, "Move folder to trash error, should not happens");
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /**
     * 携带便签主键 ID 启动 NoteEditActivity（ACTION_VIEW）查看或编辑选中的便签。
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        // 本次维护新增：由搜索结果进入时把关键词带进编辑页，便于正文里高亮命中位置
        if (mSearchActive && !TextUtils.isEmpty(mKeyword)) {
            intent.putExtra(android.app.SearchManager.USER_QUERY, mKeyword);
        }
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 进入选中的用户子文件夹或通话便签文件夹：切换当前目录 ID 与界面状态、显示文件夹标题栏并重新异步加载列表。
     */
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        // 本次维护新增：切换目录时清空标签筛选与搜索条件，保证进入后看到该目录的完整内容
        mTagFilter = "";
        if (mSearchActive) {
            mSearchActive = false;
            mKeyword = "";
            mSearchInput.setText("");
            mSearchBar.setVisibility(View.GONE);
            mTitleBarLayout.setVisibility(View.VISIBLE);
        }
        if (data.getId() == Notes.ID_TRASH_FOLER) {
            // 进入回收站前先按保留期清理过期便签，避免列表展示已到期条目
            int purged = DataUtils.purgeExpiredTrash(mContentResolver);
            if (purged > 0) {
                Toast.makeText(NotesListActivity.this,
                        getString(R.string.trash_purged_format, purged), Toast.LENGTH_SHORT).show();
            }
        }
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else if (data.getId() == Notes.ID_TRASH_FOLER) {
            mState = ListEditState.TRASH_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
            // 进入回收站时向用户明确保留期规则，避免误以为便签会立即永久消失
            int trashCount = DataUtils.getTrashNoteCount(mContentResolver);
            Toast.makeText(NotesListActivity.this,
                    trashCount == 0 ? getString(R.string.trash_empty)
                            : getString(R.string.trash_retention_notice,
                                    TrashPolicy.RETENTION_DAYS),
                    Toast.LENGTH_LONG).show();
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else if (data.getId() == Notes.ID_TRASH_FOLER) {
            mTitleBar.setText(R.string.trash_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        // 本次维护新增：切换目录后刷新标题基线，标签筛选后缀随之重建
        mBaseTitle = mTitleBar.getText().toString();
        mTitleBar.setVisibility(View.VISIBLE);
        // 本次美化新增：进入子目录时标题栏与返回箭头一起显示
        mTitleBarLayout.setVisibility(View.VISIBLE);
        mBtnBack.setVisibility(View.VISIBLE);
    }

    /**
     * 本次美化新增：从任意子目录（普通文件夹 / 通话便签 / 回收站）统一返回根目录主列表。
     * 标题栏保持在根目录显示“全部便签”，仅隐藏返回箭头。
     */
    private void goToRootFolder() {
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        mState = ListEditState.NOTE_LIST;
        mAddNewNote.setVisibility(View.VISIBLE);
        mTitleBar.setText(R.string.all_notes_name);
        // 本次维护新增：回到根目录后同步标题基线
        mBaseTitle = mTitleBar.getText().toString();
        mTitleBarLayout.setVisibility(View.VISIBLE);
        mBtnBack.setVisibility(View.GONE);
        // 本次维护新增：回到根目录时清除标签筛选与搜索态，避免条件残留造成“列表莫名其妙变少”
        mTagFilter = "";
        if (mSearchActive) {
            mSearchActive = false;
            mKeyword = "";
            mSearchInput.setText("");
            mSearchBar.setVisibility(View.GONE);
        }
        startAsyncNotesListQuery();
    }

    /**
     * 响应界面按钮点击事件：点击底部“新建便签”按钮时调用 createNewNote()。
     */
    public void onClick(View v) {
        if (v.getId() == R.id.btn_new_note) {
            createNewNote();
        }
    }

    // 通过 InputMethodManager 强制弹出系统软键盘
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    // 隐藏指定输入框关联的系统软键盘
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 弹出新建文件夹或修改文件夹名称的输入对话框：
     * 实时监听输入为空时禁用确定按钮，提交前校验是否存在同名文件夹，通过后插入或更新 note 表文件夹记录。
     *
     * @param create true 表示新建文件夹，false 表示重命名当前长按选中的文件夹
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    /**
     * 返回键导航处理：若当前位于子文件夹或通话便签目录则返回根目录主列表，若已在根目录则正常退出界面。
     */
    public void onBackPressed() {
        // 本次维护新增：搜索态下返回键先退出搜索（回到当前目录的完整列表），而不是直接退到根目录
        if (mSearchActive) {
            closeSearch();
            return;
        }
        // 本次美化调整：位于任意子目录时，返回键统一回到根目录列表；
        // 修复此前回收站状态未列入分支、按返回键无响应的问题
        if (mState != ListEditState.NOTE_LIST) {
            goToRootFolder();
            return;
        }
        super.onBackPressed();
    }

    /**
     * 向指定 ID 和尺寸类型（2x2 / 4x4）的桌面小部件发送更新广播，使其同步反映便签删除状态。
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                appWidgetId
        });

        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    // 长按用户文件夹时弹出的上下文菜单监听器，提供“查看文件夹”、“删除文件夹”、“修改文件夹名称”三项操作
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };

    @Override
    /**
     * 文件夹上下文菜单关闭后清空 ListView 的上下文菜单监听器，避免影响普通便签长按行为。
     */
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    /**
     * 处理文件夹上下文菜单项点击：打开查看文件夹、二次确认后删除文件夹，或弹窗修改文件夹名称。
     */
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_folder));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                deleteFolder(mFocusNoteDataItem.getId());
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                break;
            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    /**
     * 根据当前列表状态（根目录 / 子文件夹 / 通话目录）加载对应选项菜单，并动态切换“同步 / 取消同步”文案。
     */
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else if (mState == ListEditState.TRASH_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    @Override
    /**
     * 选项菜单点击分发：处理新建文件夹、导出文本、启动/取消云同步（未设账号则跳设置）、打开设置、新建便签及搜索。
     */
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_new_folder) {
            showCreateOrModifyFolderDialog(true);
        } else if (id == R.id.menu_export_text) {
            exportNoteToText();
        } else if (id == R.id.menu_sync) {
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
        } else if (id == R.id.menu_setting) {
            startPreferenceActivity();
        } else if (id == R.id.menu_new_note) {
            createNewNote();
        } else if (id == R.id.menu_search) {
            // 本次维护调整：改为应用内搜索栏，直接在当前页按关键词过滤列表
            openSearch();
        } else if (id == R.id.menu_sort) {
            showSortDialog();
        } else if (id == R.id.menu_filter_tag) {
            showTagFilterDialog();
        } else if (id == R.id.menu_layout_mode) {
            // 本次维护新增：单列 / 双列视图切换
            toggleViewMode();
        }
        return true;
    }

    @Override
    /**
     * 唤起 Android 系统全局搜索栏以按关键词搜索便签内容。
     */
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    /**
     * 通过 AsyncTask 在后台调用 BackupUtils 将全部便签导出为 SD 卡 TXT 文件，并根据结果状态码弹出提示对话框。
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }

        }.execute();
    }

    // 判断当前是否配置了 Google Task 同步账号
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    // 启动 NotesPreferenceActivity 进入便签设置与同步账号管理界面
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    /**
     * 列表项单击事件监听内部类：多选模式下切换便签勾选状态；正常浏览模式下打开点击的文件夹或便签。
     */
    private class OnListItemClickListener implements OnItemClickListener {

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - (parent instanceof ListView
                                ? ((ListView) parent).getHeaderViewsCount() : 0);
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                    case TRASH_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    /**
     * 异步查询“移动到文件夹”操作的可选目标目录列表（排除回收站与当前所在目录；子目录内额外包含“上一级文件夹”）。
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
                "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 本次维护新增：切换单列列表 / 双列网格，并记忆用户选择。
     */
    private void applyViewMode(boolean grid) {
        mGridViewMode = grid;
        if (mNotesListAdapter == null) {
            return;
        }
        if (grid) {
            if (mNotesListView != null) {
                mNotesListView.setAdapter(null);
                mNotesListView.setVisibility(View.GONE);
            }
            if (mNotesGridView != null) {
                mNotesGridView.setVisibility(View.VISIBLE);
                mNotesGridView.setAdapter(mNotesListAdapter);
            }
        } else {
            if (mNotesGridView != null) {
                mNotesGridView.setAdapter(null);
                mNotesGridView.setVisibility(View.GONE);
            }
            if (mNotesListView != null) {
                mNotesListView.setVisibility(View.VISIBLE);
                mNotesListView.setAdapter(mNotesListAdapter);
            }
        }
        mNotesListAdapter.setCompactMode(grid);
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(PREFERENCE_VIEW_MODE, grid).apply();
    }

    /**
     * 本次维护新增：切换视图模式并重新加载列表。
     */
    private void toggleViewMode() {
        applyViewMode(!mGridViewMode);
        startAsyncNotesListQuery();
    }

    /**
     * 本次维护新增：返回当前可见的列表视图（单列 ListView 或双列 GridView）。
     */
    private AdapterView<?> getActiveList() {
        return mGridViewMode && mNotesGridView != null ? mNotesGridView : mNotesListView;
    }

    /**
     * 本次维护新增：把屏幕坐标换算为当前列表中的条目下标。
     */
    private int pointToPosition(View list, int x, int y) {
        if (list instanceof ListView) {
            return ((ListView) list).pointToPosition(x, y);
        } else if (list instanceof GridView) {
            return ((GridView) list).pointToPosition(x, y);
        }
        return AdapterView.INVALID_POSITION;
    }

    /**
     * 本次维护新增：弹出单条便签的行内“更多操作”菜单（置顶 / 标签 / 多选 / 移入回收站）。
     * anchor 为行内 ⋮ 按钮或整行视图，位置通过 getPositionForView 反查。
     */
    private void showItemMenu(final View anchor) {
        int position = getActiveList().getPositionForView(anchor);
        if (position == AdapterView.INVALID_POSITION) {
            return;
        }
        Object obj = getActiveList().getItemAtPosition(position);
        if (!(obj instanceof Cursor)) {
            return;
        }
        final NoteItemData data = new NoteItemData(this, (Cursor) obj);
        if (data.getType() != Notes.TYPE_NOTE) {
            return;
        }
        final long noteId = data.getId();
        final boolean pinned = data.isPinned();
        final int itemPosition = position;
        final HashSet<Long> ids = new HashSet<Long>();
        ids.add(noteId);

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.item_more, popup.getMenu());
        MenuItem pinItem = popup.getMenu().findItem(R.id.item_pin);
        if (pinItem != null) {
            pinItem.setTitle(pinned ? R.string.menu_unpin : R.string.menu_pin);
        }
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.item_pin) {
                    if (DataUtils.setNotesPinned(mContentResolver, ids, !pinned)) {
                        Toast.makeText(NotesListActivity.this,
                                pinned ? R.string.swipe_unpinned : R.string.swipe_pinned,
                                Toast.LENGTH_SHORT).show();
                        startAsyncNotesListQuery();
                    }
                    return true;
                } else if (itemId == R.id.item_tag) {
                    showTagInputDialog(ids);
                    return true;
                } else if (itemId == R.id.item_multi_select) {
                    // 本次维护新增：改用 Activity 级 ActionMode，单列与双列模式下都能正常进入多选
                    if (startActionMode(mModeCallBack) != null) {
                        mModeCallBack.onItemCheckedStateChanged(null, itemPosition, noteId, true);
                    }
                    return true;
                } else if (itemId == R.id.item_delete) {
                    deleteNotesWithUndo(ids);
                    return true;
                }
                return false;
            }
        });
        popup.show();
    }

    /**
     * 本次维护新增：把便签移入回收站并给出可撤销的底部提示条。
     */
    private void deleteNotesWithUndo(final HashSet<Long> ids) {
        if (!DataUtils.moveNotesToTrash(mContentResolver, ids)) {
            return;
        }
        startAsyncNotesListQuery();
        showUndoBar(ids);
    }

    /**
     * 本次维护新增：显示底部“已移入回收站 / 撤销”提示条，5 秒后自动隐藏。
     */
    private void showUndoBar(final HashSet<Long> ids) {
        if (mUndoBar == null) {
            return;
        }
        mPendingUndoIds = ids;
        mUndoBar.setVisibility(View.VISIBLE);
        mUndoBar.bringToFront();
        mUndoBar.removeCallbacks(mHideUndoRunnable);
        mUndoBar.postDelayed(mHideUndoRunnable, 5000);
    }

    /**
     * 本次维护新增：撤销上一次移入回收站的操作，把便签原样恢复到原目录。
     */
    private void undoLastDelete() {
        if (mPendingUndoIds == null || mPendingUndoIds.isEmpty()) {
            return;
        }
        if (DataUtils.restoreNotes(mContentResolver, mPendingUndoIds)) {
            Toast.makeText(this, R.string.undo_restored, Toast.LENGTH_SHORT).show();
        }
        mPendingUndoIds = null;
        if (mUndoBar != null) {
            mUndoBar.setVisibility(View.GONE);
            mUndoBar.removeCallbacks(mHideUndoRunnable);
        }
        startAsyncNotesListQuery();
    }

    /**
     * 列表项长按事件回调：长按普通便签弹出单条“更多操作”菜单（不再直接进入多选，避免与左右滑冲突）；
     * 长按用户文件夹注册上下文菜单。
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // 本次维护新增：本次手势已判定为横向滑动（或正在横向拖动）时不当作长按，
        // 避免同一手势既触发滑动动作、又弹出多选操作栏
        if (mSwipeTriggered
                || Math.abs(mSwipeLastDx) > getResources().getDisplayMetrics().density * 8f) {
            return true;
        }
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                // 本次维护新增：长按改为弹出单条菜单，多选入口移入菜单项
                mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                showItemMenu(view);
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}