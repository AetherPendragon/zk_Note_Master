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
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
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
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import net.micode.notes.ui.BackgroundManager;
import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import android.net.Uri;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * 小米笔记的主界面类，负责管理笔记列表、文件夹、搜索和批量操作
 * 实现了笔记的创建、编辑、删除、移动等核心功能
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {
    /**
     * 异步查询的Token常量，用于区分不同类型的查询请求
     */
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0; // 查询文件夹下的笔记列表
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1; // 查询文件夹列表

    /**
     * 文件夹上下文菜单的菜单项ID
     */
    private static final int MENU_FOLDER_DELETE      = 0; // 删除文件夹
    private static final int MENU_FOLDER_VIEW        = 1; // 查看文件夹内容
    private static final int MENU_FOLDER_CHANGE_NAME = 2; // 重命名文件夹

    /**
     * SharedPreferences键名，用于标记是否显示过添加笔记的介绍
     */
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    /**
     * 列表编辑状态枚举，用于管理不同的列表视图状态
     */
    private enum ListEditState {
        NOTE_LIST,          // 根目录笔记列表
        SUB_FOLDER,         // 子文件夹笔记列表
        CALL_RECORD_FOLDER, // 通话记录文件夹列表
        TRASH_FOLDER        // 回收站文件夹列表
    };

    private ListEditState mState;                         // 当前列表状态
    private BackgroundQueryHandler mBackgroundQueryHandler; // 异步查询处理器，用于后台加载数据

    private NotesListAdapter mNotesListAdapter;       // 笔记列表适配器，负责数据与UI的绑定
    private ListView mNotesListView;                  // 显示笔记列表的ListView组件
    private Button mAddNewNote;                       // 添加新笔记按钮
    private boolean mDispatch;                        // 触摸事件分发标志
    private int mOriginY;                             // 触摸事件起始Y坐标
    private int mDispatchY;                           // 触摸事件分发Y坐标
    private TextView mTitleBar;                       // 标题栏视图
    private long mCurrentFolderId;                    // 当前文件夹ID
    private ContentResolver mContentResolver;         // 内容解析器，用于访问数据库
    private ModeCallback mModeCallBack;               // 多选模式回调，处理批量操作
    private static final String TAG = "NotesListActivity"; // 日志标签
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30; // ListView滚动速率
    private NoteItemData mFocusNoteDataItem;          // 当前聚焦的笔记数据项
    private Button mMemoryBottle;                     // 记忆瓶按钮
    private ImageButton mTrashButton;                 // 回收站按钮
    private TrashManager mTrashManager;               // 回收站管理器
    private EncryptedFolderManager mEncryptedFolderManager; // 加密文件夹管理器
    private View mNotesRootView;                      // 笔记根视图
    private BackgroundManager mBackgroundManager;     // 背景管理器
    private MemoryBottleDialog mMemoryBottleDialog;   // 记忆瓶对话框

    /**
     * 数据库查询条件
     */
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?"; // 普通文件夹查询条件
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    private static final String PREF_MEMORY_FOLDER_ID = "pref_memory_bottle_folder_id";

    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;

    @Override
    protected void onCreate(Bundle savedInstanceState) {// 初始化活动，设置布局和资源
        super.onCreate(savedInstanceState);
        setContentView(R.layout.note_list);
        initResources();

        // 注册Android 13+的返回键回调
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            handleBackPress();
                        }
                    }
            );
        }
        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {// 处理子活动返回结果
        //如果是背景管理器的结果，交给背景管理器处理
        if (mBackgroundManager != null && mBackgroundManager.handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 首次使用应用时插入介绍笔记
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);// 亮点：SharedPreferences 是Android平台上一个轻量级的存储类，主要用于保存应用的一些常用配置。它通过键值对的形式将数据保存在XML文件中
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {// 检查是否已添加介绍笔记
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                 in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in, "UTF-8");// 亮点：指定字符编码为UTF-8，确保能够正确读取包含特殊字符的文本文件
                    BufferedReader br = new BufferedReader(isr);// 亮点：BufferedReader 是Java IO库中的一个字符流类，用于读取字符数据。它提供了缓冲区的功能，能够一次读取多个字符，提高了读取效率。
                    char [] buf = new char[1024];
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);//将读取到的字符数组buf中的0到len-1位置的字符添加到StringBuilder中
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

    /**
     * 活动启动时执行，开始异步查询笔记列表
     */
    @Override
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    /**
     * 初始化资源和UI组件
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
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        mMemoryBottle = (Button) findViewById(R.id.btn_memory_bottle);
        mMemoryBottle.setOnClickListener(this);
        mTrashButton = (ImageButton) findViewById(R.id.btn_trash);
        mTrashButton.setOnClickListener(this);
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
        mTrashManager = new TrashManager(this, mContentResolver, new TrashManager.Callback() {
            @Override
            public void onWidgetsNeedUpdate(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
            }

            @Override
            public void onListChanged() {
                startAsyncNotesListQuery();
            }

            @Override
            public void onActionModeFinished() {
                mModeCallBack.finishActionMode();
            }

            @Override
            public void onRestoreInvalid() {
                Toast.makeText(NotesListActivity.this, R.string.trash_restore_invalid,
                        Toast.LENGTH_SHORT).show();
            }
        });
        mEncryptedFolderManager = new EncryptedFolderManager(this, mContentResolver,
                new EncryptedFolderManager.Callback() {
                    @Override
                    public void onEncryptedFolderCreated() {
                        startAsyncNotesListQuery();
                    }

                    @Override
                    public void onEncryptedFolderUnlocked(NoteItemData data) {
                        openFolderInternal(data);
                    }
                });
        updateMemoryButtonVisibility();
        updateTrashButtonVisibility();
    

        //初始化背景管理器
        mNotesRootView = findViewById(R.id.notes_root);
                
        mBackgroundManager = new BackgroundManager(this, R.id.notes_root);
        mBackgroundManager.applyBackgroundFromPrefs();
    }

    

 //显示背景设置对话框
    private void showBackgroundSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.background_settings_dialog, null);
        builder.setView(view);
        
        Button btnSolidColor = (Button) view.findViewById(R.id.btn_solid_color);
        Button btnBuiltinImage = (Button) view.findViewById(R.id.btn_builtin_image);
        Button btnGalleryImage = (Button) view.findViewById(R.id.btn_gallery_image);
        Button btnResetDefault = (Button) view.findViewById(R.id.btn_reset_default);
        
        final AlertDialog dialog = builder.create();
        
        btnSolidColor.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                    showColorChoiceDialog();
            }
        });
        
        btnBuiltinImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                    showBuiltinChoiceDialog();
            }
        });
        
        btnGalleryImage.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                    if (mBackgroundManager != null) mBackgroundManager.pickImageFromGallery();
            }
        });
        
        btnResetDefault.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                    if (mBackgroundManager != null) mBackgroundManager.resetToDefaultAndClear();
            }
        });
        
        dialog.show();
    }

    private void showColorChoiceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.color_choice_dialog, null);
        builder.setView(view);
        
        View colorWhite = view.findViewById(R.id.color_white);
        View colorYellow = view.findViewById(R.id.color_yellow);
        View colorRed = view.findViewById(R.id.color_red);
        View colorGreen = view.findViewById(R.id.color_green);
        View colorBlue = view.findViewById(R.id.color_blue);
        
        final AlertDialog dialog = builder.create();
        
        colorWhite.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyColorAndSave(0xFFFFFFFF);
            }
        });
        
        colorYellow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyColorAndSave(0xFFFFFFCC);
            }
        });
        
        colorRed.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyColorAndSave(0xFFFFF0F0);
            }
        });
        
        colorGreen.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyColorAndSave(0xFFE8FFF0);
            }
        });
        
        colorBlue.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyColorAndSave(0xFFDDE8FF);
            }
        });
        
        dialog.show();
    }

    private void showBuiltinChoiceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.builtin_image_choice_dialog, null);
        builder.setView(view);
        
        ImageView imageBuiltin1 = (ImageView) view.findViewById(R.id.image_builtin_1);
        ImageView imageBuiltin2 = (ImageView) view.findViewById(R.id.image_builtin_2);
        ImageView imageBuiltin3 = (ImageView) view.findViewById(R.id.image_builtin_3);
        ImageView imageBuiltin4 = (ImageView) view.findViewById(R.id.image_builtin_4);
        
        final AlertDialog dialog = builder.create();
        
        imageBuiltin1.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyBuiltinAndSave(R.drawable.background_1);
            }
        });
        
        imageBuiltin2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyBuiltinAndSave(R.drawable.background_2);
            }
        });
        
        imageBuiltin3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyBuiltinAndSave(R.drawable.background_3);
            }
        });
        
        imageBuiltin4.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (mBackgroundManager != null) mBackgroundManager.applyBuiltinAndSave(R.drawable.background_4);
            }
        });
        
        dialog.show();
    }

    
    /**
     * 多选模式回调类，处理批量操作和菜单项点击事件
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        /** 下拉菜单，用于显示选择操作选项 */
        private DropdownMenu mDropDownMenu;
        /** ActionMode实例，用于管理多选模式的生命周期 */
        private ActionMode mActionMode;
        /** 移动菜单项，用于批量移动笔记 */
        private MenuItem mMoveMenu;
        /** 恢复菜单项，用于从回收站恢复笔记 */
        private MenuItem mRestoreMenu;

        /**
         * 创建多选模式时的初始化操作
         * @param mode ActionMode实例
         * @param menu 菜单对象
         * @return 是否成功创建多选模式
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            MenuItem deleteMenu = menu.findItem(R.id.delete);
            deleteMenu.setOnMenuItemClickListener(this);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            mRestoreMenu = menu.findItem(R.id.restore);
            if (mState == ListEditState.TRASH_FOLDER) {
                mMoveMenu.setVisible(false);
                mRestoreMenu.setVisible(true);
                mRestoreMenu.setOnMenuItemClickListener(this);
                deleteMenu.setTitle(R.string.menu_delete_permanent);
            } else {
                mRestoreMenu.setVisible(false);
                if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                        || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                    mMoveMenu.setVisible(false);
                } else {
                    mMoveMenu.setVisible(true);
                    mMoveMenu.setOnMenuItemClickListener(this);
                }
                deleteMenu.setTitle(R.string.menu_delete);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true, mState == ListEditState.TRASH_FOLDER);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);
            if (mMemoryBottle != null) {
                mMemoryBottle.setVisibility(View.GONE);
            }
            if (mTrashButton != null) {
                mTrashButton.setVisibility(View.GONE);
            }

            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }

            });
            return true;
        }

        private void updateMenu() {// 更新下拉菜单显示的选中项数量
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);// 查找选择所有项的菜单项
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

        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {// 准备多选模式时的操作
            // TODO Auto-generated method stub
            return false;
        }

        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {// 处理下拉菜单项点击事件
            // TODO Auto-generated method stub
            return false;
        }

        public void onDestroyActionMode(ActionMode mode) {// 多选模式结束时的操作
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            if (mState == ListEditState.CALL_RECORD_FOLDER) {
                mAddNewNote.setVisibility(View.GONE);
            } else {
                mAddNewNote.setVisibility(View.VISIBLE);
            }
            updateMemoryButtonVisibility();
            updateTrashButtonVisibility();
        }

        public void finishActionMode() {// 结束多选模式
            mActionMode.finish();
        }

        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {// 处理列表项选中状态改变事件，注意actionmode的生命周期
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        public boolean onMenuItemClick(MenuItem item) {// 处理下拉菜单项点击事件
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            switch (item.getItemId()) {
                case R.id.delete:
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.alert_title_delete));
                    builder.setIcon(android.R.drawable.ic_dialog_alert);
                    if (mState == ListEditState.TRASH_FOLDER) {
                        builder.setMessage(getString(R.string.alert_message_delete_notes,
                                mNotesListAdapter.getSelectedCount()));
                    } else {
                        builder.setMessage(getString(R.string.alert_message_delete_notes,
                                mNotesListAdapter.getSelectedCount()));
                    }
                    builder.setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    batchDelete();
                                }
                            });
                    builder.setNegativeButton(android.R.string.cancel, null);
                    builder.show();
                    break;
                case R.id.move:
                    startQueryDestinationFolders();
                    break;
                case R.id.restore:
                    restoreSelected();
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    private class NewNoteOnTouchListener implements OnTouchListener {// 处理"新建笔记"按钮的触摸事件
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && !mDispatch) {
                // 当触摸结束且没有分发到列表视图时，直接触发点击事件
                v.performClick();
                return true;
            }
            
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
                    if (mState == ListEditState.SUB_FOLDER) {// 如果当前状态是子文件夹
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
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
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {// 如果点击位置在"新建笔记"按钮的透明部分
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());// 获取列表项的底部视图
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {// 如果点击位置在"新建笔记"按钮的透明部分且在列表项范围内
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    mDispatch = false;
                    return false;
                }
                case MotionEvent.ACTION_MOVE: {// 处理"新建笔记"按钮的移动事件
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    return false;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        boolean result = mNotesListView.dispatchTouchEvent(event);
                        mDispatch = false;
                        return result;
                    }
                    return false;
                }
            }
        }
    };

    /**
     * 启动异步查询笔记列表的方法
     * 根据当前文件夹ID构建查询条件，使用异步查询处理器加载数据
     */
    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        String[] selectionArgs;
        if (mCurrentFolderId == Notes.ID_ROOT_FOLDER) {
            long memoryFolderId = getMemoryBottleFolderId();
            if (memoryFolderId > 0) {
                selection = "(" + selection + ") AND " + NoteColumns.ID + "<> ?";
                selectionArgs = new String[] {
                        String.valueOf(mCurrentFolderId),
                        String.valueOf(memoryFolderId)
                };
            } else {
                selectionArgs = new String[] { String.valueOf(mCurrentFolderId) };
            }
        } else {
            selectionArgs = new String[] { String.valueOf(mCurrentFolderId) };
        }
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, selectionArgs,
                NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 异步查询处理器，用于在后台执行数据库查询操作
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        /**
         * 构造函数
         * @param contentResolver 内容解析器，用于访问数据库
         */
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        /**
         * 查询完成后的回调处理
         * @param token 查询标识，区分不同类型的查询
         * @param cookie 额外数据
         * @param cursor 查询结果游标
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:// 查询笔记列表
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:// 查询文件夹列表
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

    private void showFolderListMenu(Cursor cursor) {// 显示文件夹列表菜单
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

    private void createNewNote() {// 创建新笔记
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    private void batchDelete() {// 批量删除笔记
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                HashSet<Long> ids = mNotesListAdapter.getSelectedItemIds();
                boolean inTrash = mState == ListEditState.TRASH_FOLDER;
                mTrashManager.batchDelete(inTrash, ids, widgets, mCurrentFolderId);
                if (!isSyncMode()) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {// 批量删除笔记完成后的回调处理
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }
    private void restoreSelected() {
        HashSet<Long> ids = mNotesListAdapter.getSelectedItemIds();
        HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
        mTrashManager.restoreSelected(ids, widgets);
    }

    private void deleteFolder(long folderId) {// 删除文件夹
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
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
    private void updateMemoryButtonVisibility() {
        if (mMemoryBottle == null) {
            return;
        }
        if (mState == ListEditState.NOTE_LIST) {
            mMemoryBottle.setVisibility(View.VISIBLE);
        } else {
            mMemoryBottle.setVisibility(View.GONE);
        }
    }

    private void updateTrashButtonVisibility() {
        if (mTrashButton == null) {
            return;
        }
        if (mState == ListEditState.NOTE_LIST) {
            mTrashButton.setVisibility(View.VISIBLE);
        } else {
            mTrashButton.setVisibility(View.GONE);
        }
    }


    private long getMemoryBottleFolderId() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        long folderId = sp.getLong(PREF_MEMORY_FOLDER_ID, -1);
        if (folderId > 0 && DataUtils.visibleInNoteDatabase(mContentResolver, folderId,
                Notes.TYPE_FOLDER)) {
            return folderId;
        }
        return -1;
    }
    private void openNode(NoteItemData data) {// 打开笔记
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    private void openFolder(NoteItemData data) {
        if (data.getId() == Notes.ID_TRASH_FOLER) {
            openTrashFolder();
            return;
        }
        EncryptedFolderManager.EncryptedFolderInfo encryptedInfo =
                mEncryptedFolderManager.getEncryptedFolderInfo(data.getId());
        if (encryptedInfo != null) {
            mEncryptedFolderManager.showEncryptedUnlockDialog(encryptedInfo, data);
            return;
        }
        openFolderInternal(data);
    }

    private void openFolderInternal(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
        updateMemoryButtonVisibility();
        updateTrashButtonVisibility();
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_new_note:
                createNewNote();
                break;
            case R.id.btn_memory_bottle:
                openMemoryBottle();
                break;
            case R.id.btn_trash:
                openTrashFolder();
                break;
            default:
                break;
        }
    }

    private void openMemoryBottle() {
        if (mMemoryBottleDialog == null) {
            mMemoryBottleDialog = new MemoryBottleDialog(this);
        }
        mMemoryBottleDialog.show();
    }

    private void openTrashFolder() {
        mCurrentFolderId = Notes.ID_TRASH_FOLER;
        mState = ListEditState.TRASH_FOLDER;
        mTrashManager.cleanupExpiredTrash();
        startAsyncNotesListQuery();
        mTitleBar.setText(R.string.trash_folder_name);
        mTitleBar.setVisibility(View.VISIBLE);
        mAddNewNote.setVisibility(View.GONE);
        updateMemoryButtonVisibility();
        updateTrashButtonVisibility();
    }

    @Override
    protected void onDestroy() {
        if (mMemoryBottleDialog != null && mMemoryBottleDialog.isShowing()) {
            mMemoryBottleDialog.dismiss();
        }
        mMemoryBottleDialog = null;
        super.onDestroy();
    }


    private void showSoftInput() {// 显示软键盘
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void hideSoftInput(View view) {// 隐藏软键盘
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showCreateOrModifyFolderDialog(final boolean create) {// 显示创建或修改文件夹对话框
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
        positive.setOnClickListener(new OnClickListener() {// 确认创建或修改文件夹
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
                    if (!TextUtils.isEmpty(name)) {// 修改文件夹名称
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {// 创建新文件夹
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        if (TextUtils.isEmpty(etName.getText())) {// 输入框为空时禁用确认按钮
            positive.setEnabled(false);
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {// 监听输入框文本变化
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub

            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {// 输入框文本变化时更新确认按钮状态
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }

            public void afterTextChanged(Editable s) {// 输入框文本变化后更新确认按钮状态
                // TODO Auto-generated method stub

            }
        });
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }
    public void handleBackPress() {// 处理返回键按下事件，根据当前状态执行相应操作
        switch (mState) {
            case SUB_FOLDER:// 返回上一级文件夹
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                updateMemoryButtonVisibility();
                updateTrashButtonVisibility();
                invalidateOptionsMenu();
                break;
            case CALL_RECORD_FOLDER:// 返回上一级通话记录文件夹
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                updateMemoryButtonVisibility();
                updateTrashButtonVisibility();
                break;
            case TRASH_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                mAddNewNote.setVisibility(View.VISIBLE);
                updateMemoryButtonVisibility();
                updateTrashButtonVisibility();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }

    private void updateWidget(int appWidgetId, int appWidgetType) {// 更新指定应用小部件
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

    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {// 文件夹上下文菜单创建监听
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
    public void onContextMenuClosed(Menu menu) {// 上下文菜单关闭时移除监听
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:// 查看文件夹
                openFolder(mFocusNoteDataItem);
                break;
            case MENU_FOLDER_DELETE:// 删除文件夹
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
            case MENU_FOLDER_CHANGE_NAME:// 重命名文件夹
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {// 准备选项菜单
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
            MenuItem newNote = menu.findItem(R.id.menu_new_note);
            if (newNote != null) {
                newNote.setVisible(false);
            }
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {// 选项菜单项点击事件处理
        switch (item.getItemId()) {
            case R.id.menu_new_folder: {// 新建文件夹
                showCreateOrModifyFolderDialog(true);
                break;
            }
            case R.id.menu_new_encrypted_folder: {
                mEncryptedFolderManager.showCreateEncryptedFolderDialog();
                break;
            }
            case R.id.menu_export_text: {
                exportNoteToText();
                break;
            }
            case R.id.menu_sync: {// 同步笔记
                if (isSyncMode()) {
                    if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {// 同步笔记
                        GTaskSyncService.startSync(this);
                    } else {
                        GTaskSyncService.cancelSync(this);
                    }
                } else {
                    startPreferenceActivity();
                }
                break;
            }
            case R.id.menu_setting: {// 打开偏好设置
                startPreferenceActivity();
                break;
            }
            case R.id.menu_new_note: {// 新建笔记
                createNewNote();
                break;
            }
            case R.id.menu_search: {// 搜索笔记
                onSearchRequested();
                break;
            }
            
            //如果是背景设置菜单项
            case R.id.menu_background_settings: {
                showBackgroundSettingsDialog();
                break;
            }
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onSearchRequested() {// 搜索笔记
        startSearch(null, false, null /* appData */, false);
        return true;
    }

    private void exportNoteToText() {// 导出笔记为文本文件
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {// 后台导出笔记为文本文件
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {// 导出笔记为文本文件完成后的回调
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);// 导出笔记为文本文件失败对话框
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);// 导出笔记为文本文件成功对话框
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {// 导出笔记为文本文件系统错误对话框
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

    private boolean isSyncMode() {// 判断是否为同步模式
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    private void startPreferenceActivity() {// 打开偏好设置活动
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    private class OnListItemClickListener implements OnItemClickListener {// 列表项点击监听器

        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {// 列表项点击事件
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                switch (mState) {// 根据当前状态处理点击事件
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER// 点击文件夹项
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
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    case TRASH_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Toast.makeText(NotesListActivity.this,
                                    R.string.menu_restore, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    default:
                        break;
                }
            }
        }

    }

    private void startQueryDestinationFolders() {// 查询目标文件夹
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

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {// 列表项长按事件
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mState == ListEditState.TRASH_FOLDER && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}
