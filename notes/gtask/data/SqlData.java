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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;
import net.micode.notes.gtask.exception.ActionFailureException;

import org.json.JSONException;
import org.json.JSONObject;

/**     
  * 
  * @Package:        net.micode.notes.gtask.data
  * @ClassName:      SqlData
  * @Description:     为Google Task同步功能提供数据层支持，将便签数据抽象为可序列化的对象
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    private static final int INVALID_ID = -99999;

    //包含便签数据表的核心字段，用于查询和加载数据
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID, DataColumns.MIME_TYPE, DataColumns.CONTENT, DataColumns.DATA1,
            DataColumns.DATA3
    };

    // 查询结果列索引常量，提高代码可读性和维护性
    public static final int DATA_ID_COLUMN = 0;

    public static final int DATA_MIME_TYPE_COLUMN = 1;

    public static final int DATA_CONTENT_COLUMN = 2;

    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;

    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver;

    private boolean mIsCreate;// 标记是否为新建数据

    private long mDataId;

    private String mDataMimeType;

    private String mDataContent;

    private long mDataContentData1;

    private String mDataContentData3;

    private ContentValues mDiffDataValues;// 存储数据变化的ContentValues

    //构造函数：新建便签
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;
        mDataContent = "";// 初始内容为空
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    //构造函数：从数据库游标加载现有数据
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    //从数据库游标加载数据到对象字段
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    //将JSON格式的数据解析并设置到对象字段
    public void setContent(JSONObject js) throws JSONException {
        // 处理数据ID
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;//如果有ID字段则取值，否则设为无效ID
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }//如果是新建数据或ID发生变化，记录变化
        mDataId = dataId;

        //处理MIME类型
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        //处理数据内容
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        //处理扩展数据1
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 处理扩展数据3
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    //将对象字段序列化为JSON格式，用于网络传输或数据交换
    public JSONObject getContent() throws JSONException {
        if (mIsCreate) {
            Log.e(TAG, "it seems that we haven't created this in database yet");
            return null;
        }
        JSONObject js = new JSONObject();
        js.put(DataColumns.ID, mDataId);
        js.put(DataColumns.MIME_TYPE, mDataMimeType);
        js.put(DataColumns.CONTENT, mDataContent);
        js.put(DataColumns.DATA1, mDataContentData1);
        js.put(DataColumns.DATA3, mDataContentData3);
        return js;
    }

    /**
     * @method  commit
     * @description 提交数据到数据库
     * 执行流程：
     *     1. 新数据：执行INSERT操作，插入后从URI中提取新数据的ID
     *     2. 现有数据：执行UPDATE操作，支持版本验证
     *     3. 清除变更记录，更新对象状态
     * @param noteId 关联的便签ID
     * @param validateVersion 是否验证版本号
     * @param version 期望的版本号
     */
    public void commit(long noteId, boolean validateVersion, long version) {
        // 新数据：执行INSERT操作
        if (mIsCreate) {
            // 如果ID是无效值，从变更记录中移除ID字段
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 设置关联的便签ID
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);

            // 执行插入操作
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);

            try {
                mDataId = Long.valueOf(uri.getPathSegments().get(1));//从返回的URI中提取新数据的ID
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        }
        //现有数据：执行UPDATE操作
        else {
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不验证版本：直接更新
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues,
                            " ? in (SELECT " + NoteColumns.ID + " FROM " + TABLE.NOTE
                                    + " WHERE " + NoteColumns.VERSION + "=?)", new String[] {
                                    String.valueOf(noteId), String.valueOf(version)
                            });
                }
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }
        }

        // 清除变更记录，更新对象状态
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    public long getId() {
        return mDataId;
    }
}
