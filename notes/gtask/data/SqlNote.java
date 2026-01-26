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

package net.micode.notes.gtask.data;

import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.tool.ResourceParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;


/**
  *
  * @Package:        net.micode.notes.gtask.data
  * @ClassName:      SqlNote
  * @Description:    管理便签的所有属性、数据和关联关系
 */
public class SqlNote {
    private static final String TAG = SqlNote.class.getSimpleName();  // 日志标签

    private static final int INVALID_ID = -99999;  // 无效ID常量，表示便签尚未保存到数据库

    //便签表的所有字段，用于查询和加载便签
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID,                 // 便签ID
            NoteColumns.ALERTED_DATE,       // 提醒日期
            NoteColumns.BG_COLOR_ID,        // 背景颜色ID
            NoteColumns.CREATED_DATE,       // 创建日期
            NoteColumns.HAS_ATTACHMENT,     // 是否有附件
            NoteColumns.MODIFIED_DATE,      // 修改日期
            NoteColumns.NOTES_COUNT,        // 子便签数量
            NoteColumns.PARENT_ID,          // 父文件夹ID
            NoteColumns.SNIPPET,            // 片段内容
            NoteColumns.TYPE,               // 便签类型
            NoteColumns.WIDGET_ID,          // 小部件ID
            NoteColumns.WIDGET_TYPE,        // 小部件类型
            NoteColumns.SYNC_ID,            // 同步ID
            NoteColumns.LOCAL_MODIFIED,     // 本地修改标记
            NoteColumns.ORIGIN_PARENT_ID,   // 原始父文件夹ID
            NoteColumns.GTASK_ID,           // Google Task ID
            NoteColumns.VERSION             // 版本号
    };

    // 查询结果列索引常量
    public static final int ID_COLUMN = 0;
    public static final int ALERTED_DATE_COLUMN = 1;
    public static final int BG_COLOR_ID_COLUMN = 2;
    public static final int CREATED_DATE_COLUMN = 3;
    public static final int HAS_ATTACHMENT_COLUMN = 4;
    public static final int MODIFIED_DATE_COLUMN = 5;
    public static final int NOTES_COUNT_COLUMN = 6;
    public static final int PARENT_ID_COLUMN = 7;
    public static final int SNIPPET_COLUMN = 8;
    public static final int TYPE_COLUMN = 9;
    public static final int WIDGET_ID_COLUMN = 10;
    public static final int WIDGET_TYPE_COLUMN = 11;
    public static final int SYNC_ID_COLUMN = 12;
    public static final int LOCAL_MODIFIED_COLUMN = 13;
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;
    public static final int GTASK_ID_COLUMN = 15;
    public static final int VERSION_COLUMN = 16;

    private Context mContext;                // Android上下文
    private ContentResolver mContentResolver; // ContentResolver，用于数据库操作
    private boolean mIsCreate;               // 标记是否为新建便签
    private long mId;
    private long mAlertDate;
    private int mBgColorId;
    private long mCreatedDate;
    private int mHasAttachment;              // 是否有附件（0无，1有）
    private long mModifiedDate;
    private long mParentId;                  // 父文件夹ID
    private String mSnippet;                 // 摘要
    private int mType;                       // 便签类型（NOTE/FOLDER/SYSTEM）
    private int mWidgetId;
    private int mWidgetType;
    private long mOriginParent;              // 原始父文件夹ID（用于恢复操作）
    private long mVersion;                   // 版本号

    private ContentValues mDiffNoteValues;   // 存储便签变化的ContentValues
    private ArrayList<SqlData> mDataList;    // 关联的数据列表（仅TYPE_NOTE类型）

    //构造函数：创建新便签
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;                              // 新便签ID无效
        mAlertDate = 0;                                // 默认无提醒
        mBgColorId = ResourceParser.getDefaultBgId(context);
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;                                 // 默认父文件夹为根目录
        mSnippet = "";                                 // 默认摘要内容为空
        mType = Notes.TYPE_NOTE;                       // 默认为普通便签
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    //构造函数：从数据库游标加载现有便签
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDataList = new ArrayList<SqlData>(); // 初始化数据列表
        if (mType == Notes.TYPE_NOTE)         // 如果是普通便签，加载数据内容
            loadDataContent();
        mDiffNoteValues = new ContentValues(); // 初始化变化值容器
    }

    //构造函数：根据便签ID加载现有便签
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);                   // 根据ID加载便签
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }


    //根据便签ID从数据库加载便签
    private void loadFromCursor(long id) {
        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                            String.valueOf(id)
                    }, null);
            if (c != null) {
                c.moveToNext();
                loadFromCursor(c);
            } else {
                Log.w(TAG, "loadFromCursor: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();  // 关闭游标
        }
    }

    //从数据库游标加载便签属性
    private void loadFromCursor(Cursor c) {
        mId = c.getLong(ID_COLUMN);
        mAlertDate = c.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = c.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = c.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = c.getInt(HAS_ATTACHMENT_COLUMN);
        mModifiedDate = c.getLong(MODIFIED_DATE_COLUMN);
        mParentId = c.getLong(PARENT_ID_COLUMN);
        mSnippet = c.getString(SNIPPET_COLUMN);
        mType = c.getInt(TYPE_COLUMN);
        mWidgetId = c.getInt(WIDGET_ID_COLUMN);
        mWidgetType = c.getInt(WIDGET_TYPE_COLUMN);
        mVersion = c.getLong(VERSION_COLUMN);
    }

    /**
     * 加载便签关联的数据内容
     * 执行流程：
     * 1. 根据便签ID查询数据表
     * 2. 遍历查询结果，创建SqlData对象
     * 3. 将SqlData对象添加到数据列表
     */
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();  // 清空现有数据列表
        try {
            c = mContentResolver.query(Notes.CONTENT_DATA_URI, SqlData.PROJECTION_DATA,
                    "(note_id=?)", new String[] {
                            String.valueOf(mId)
                    }, null);
            if (c != null) {
                if (c.getCount() == 0) {
                    Log.w(TAG, "it seems that the note has not data");
                    return;
                }
                while (c.moveToNext()) {
                    SqlData data = new SqlData(mContext, c);
                    mDataList.add(data);
                }
            } else {
                Log.w(TAG, "loadDataContent: cursor = null");
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * @method  setContent
     * @description 功能：将JSON格式的便签数据解析并设置到对象字段
     *              执行流程：
     *               1. 提取便签元数据部分
     *               2. 根据便签类型分别处理
     *               3. 记录哪些字段发生了变化，用于后续的增量更新
     * @param js 包含便签信息的JSON对象
     * @return
     */
    public boolean setContent(JSONObject js) {
        try {
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);

            // 系统文件夹：不能设置
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                Log.w(TAG, "cannot set system folder");
            }
            // 文件夹：只能更新片段和类型
            else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 处理片段内容
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 处理类型
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            }
            // 普通便签：处理所有字段
            else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);

                // 处理便签ID
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                // 处理提醒日期
                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                // 处理背景颜色ID
                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                // 处理创建日期
                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                // 处理附件标记
                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                // 处理修改日期
                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                // 处理父文件夹ID
                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                // 处理片段内容
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 处理类型
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                // 处理小部件ID
                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                // 处理小部件类型
                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                // 处理原始父文件夹ID
                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 处理数据内容
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;

                    // 查找现有的数据对象
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                                break;
                            }
                        }
                    }

                    // 如果没找到，创建新的数据对象
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    // 设置数据内容
                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }


    /**
     * @method  getContent
     * @description 将便签对象序列化为JSON格式，用于网络传输或数据交换
     * @param
     * @return JSONObject 包含便签内容的JSON对象
     */
    public JSONObject getContent() {
        try {
            JSONObject js = new JSONObject();

            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            JSONObject note = new JSONObject();

            // 普通便签：包含完整字段和数据
            if (mType == Notes.TYPE_NOTE) {
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.ALERTED_DATE, mAlertDate);
                note.put(NoteColumns.BG_COLOR_ID, mBgColorId);
                note.put(NoteColumns.CREATED_DATE, mCreatedDate);
                note.put(NoteColumns.HAS_ATTACHMENT, mHasAttachment);
                note.put(NoteColumns.MODIFIED_DATE, mModifiedDate);
                note.put(NoteColumns.PARENT_ID, mParentId);
                note.put(NoteColumns.SNIPPET, mSnippet);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.WIDGET_ID, mWidgetId);
                note.put(NoteColumns.WIDGET_TYPE, mWidgetType);
                note.put(NoteColumns.ORIGIN_PARENT_ID, mOriginParent);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);

                // 添加数据内容
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            }
            // 文件夹和系统文件夹：仅包含ID、类型和片段
            else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                note.put(NoteColumns.ID, mId);
                note.put(NoteColumns.TYPE, mType);
                note.put(NoteColumns.SNIPPET, mSnippet);
                js.put(GTaskStringUtils.META_HEAD_NOTE, note);
            }

            return js;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
        return null;
    }


    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    //设置Google Task ID
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    //设置同步ID
    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    //重置本地修改标记。在同步完成后调用，表示便签已经与远程同步
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }


    public long getId() {
        return mId;
    }


    public long getParentId() {
        return mParentId;
    }


    public String getSnippet() {
        return mSnippet;
    }

    //判断是否为普通便签类型
    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }


    /**
     * @method  commit
     * @description 提交便签到数据库
     * @param validateVersion 是否验证版本号
     * @return
     */
    public void commit(boolean validateVersion) {
        // 新便签：执行INSERT操作
        if (mIsCreate) {
            // 如果ID是无效值，从变更记录中移除ID字段，让数据库自动生成
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 执行插入操作
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                // 从返回的URI中提取新便签的ID
                // URI格式：content://authority/note/{note_id}
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }

            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果是普通便签，提交关联的所有数据
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        }
        // 现有便签：执行UPDATE操作
        else {
            // 检查便签ID有效性
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }

            // 如果有变更，执行更新操作
            if (mDiffNoteValues.size() > 0) {
                mVersion ++;  // 版本号自增
                int result = 0;

                if (!validateVersion) {
                    // 不验证版本：直接更新
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                            String.valueOf(mId)
                    });
                } else {
                    // 验证版本：使用乐观锁机制
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                                    + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }

                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果是普通便签，提交关联的所有数据
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // 刷新本地信息：重新从数据库加载，确保数据一致性
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        // 清除变更记录，更新对象状态
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}