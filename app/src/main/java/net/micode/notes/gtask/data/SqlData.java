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
 * 数据库交互工具类
 * 处理笔记数据的本地存储操作，支持创建/更新数据记录
 * 实现与系统ContentProvider的交互及数据版本控制
 */
public class SqlData {
    private static final String TAG = SqlData.class.getSimpleName();

    private static final int INVALID_ID = -99999;// 无效ID标识

    // 数据表查询字段投影（对应DATA表结构）
    public static final String[] PROJECTION_DATA = new String[] {
            DataColumns.ID,// 0.数据记录ID
             DataColumns.MIME_TYPE, // 1. MIME类型
             DataColumns.CONTENT, // 2. 内容主体
             DataColumns.DATA1,   // 3. 拓展数据1（长整型）
            DataColumns.DATA3      // 4. 拓展数据3（字符串）
    };

    // 数据表查询结果列索引常量
    public static final int DATA_ID_COLUMN = 0;
    public static final int DATA_MIME_TYPE_COLUMN = 1;
    public static final int DATA_CONTENT_COLUMN = 2;
    public static final int DATA_CONTENT_DATA_1_COLUMN = 3;
    public static final int DATA_CONTENT_DATA_3_COLUMN = 4;

    private ContentResolver mContentResolver;// 系统内容解析器，用于数据库操作
    private boolean mIsCreate;// 标识当前数据对象是否为新创建（未保存到数据库）
    private long mDataId;// 当前数据记录ID
    private String mDataMimeType;// 当前数据记录的MIME类型
    private String mDataContent;// 当前数据记录的内容主体
    private long mDataContentData1;// 当前数据记录的拓展数据1（长整型）
    private String mDataContentData3;// 当前数据记录的拓展数据3（字符串）
    private ContentValues mDiffDataValues;// 用于记录数据变更的ContentValues对象，保存待更新的字段和值


    /**
     * 新建数据构造器
     * 
     * @param context 上下文对象
     */
    public SqlData(Context context) {
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mDataId = INVALID_ID;
        mDataMimeType = DataConstants.NOTE;// 默认MIME类型为笔记
        mDataContent = "";
        mDataContentData1 = 0;
        mDataContentData3 = "";
        mDiffDataValues = new ContentValues();
    }

    /**
    * 从数据库查询结果构造数据对象
    * 
    * @param context 上下文对象
    * @param c 数据库查询结果Cursor，包含数据记录的字段值
    */
    public SqlData(Context context, Cursor c) {
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDiffDataValues = new ContentValues();
    }

    /**
     * 从游标加载数据
     * 
     * @param c 已定位到目标记录的数据库游标
     */
    private void loadFromCursor(Cursor c) {
        mDataId = c.getLong(DATA_ID_COLUMN);
        mDataMimeType = c.getString(DATA_MIME_TYPE_COLUMN);
        mDataContent = c.getString(DATA_CONTENT_COLUMN);
        mDataContentData1 = c.getLong(DATA_CONTENT_DATA_1_COLUMN);
        mDataContentData3 = c.getString(DATA_CONTENT_DATA_3_COLUMN);
    }

    /**
     * 从JSON对象加载数据
     * 
     * @param js 包含数据字段的JSON对象
     * @throws JSONException 解析异常
     */    
    public void setContent(JSONObject js) throws JSONException {
        // ID处理，解析JSON对象中的数据字段，并与当前对象的字段值进行比较，记录变更的字段和值
        long dataId = js.has(DataColumns.ID) ? js.getLong(DataColumns.ID) : INVALID_ID;
        if (mIsCreate || mDataId != dataId) {
            mDiffDataValues.put(DataColumns.ID, dataId);
        }
        mDataId = dataId;

        // 解析MIME类型，默认为NOTE，如果是新建或MIME类型发生变化，则记录变更
        String dataMimeType = js.has(DataColumns.MIME_TYPE) ? js.getString(DataColumns.MIME_TYPE)
                : DataConstants.NOTE;
        if (mIsCreate || !mDataMimeType.equals(dataMimeType)) {
            mDiffDataValues.put(DataColumns.MIME_TYPE, dataMimeType);
        }
        mDataMimeType = dataMimeType;

        // 解析内容主体，如果是新建或内容发生变化，则记录变更
        String dataContent = js.has(DataColumns.CONTENT) ? js.getString(DataColumns.CONTENT) : "";
        if (mIsCreate || !mDataContent.equals(dataContent)) {
            mDiffDataValues.put(DataColumns.CONTENT, dataContent);
        }
        mDataContent = dataContent;

        // 解析拓展数据1（长整型），如果是新建或数据发生变化，则记录变更
        long dataContentData1 = js.has(DataColumns.DATA1) ? js.getLong(DataColumns.DATA1) : 0;
        if (mIsCreate || mDataContentData1 != dataContentData1) {
            mDiffDataValues.put(DataColumns.DATA1, dataContentData1);
        }
        mDataContentData1 = dataContentData1;

        // 解析拓展数据3（字符串），如果是新建或数据发生变化，则记录变更
        String dataContentData3 = js.has(DataColumns.DATA3) ? js.getString(DataColumns.DATA3) : "";
        if (mIsCreate || !mDataContentData3.equals(dataContentData3)) {
            mDiffDataValues.put(DataColumns.DATA3, dataContentData3);
        }
        mDataContentData3 = dataContentData3;
    }

    /**
     * 生成当前数据的JSON表示
     * 
     * @return JSON对象（包含所有数据字段）
     * @throws JSONException 序列化异常
     */    
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
     * 提交数据变更到数据库
     * 
     * @param noteId          关联的笔记ID
     * @param validateVersion 是否启用版本验证
     * @param version         当前数据版本号
     * @throws ActionFailureException 数据库操作失败时抛出
     */    
    public void commit(long noteId, boolean validateVersion, long version) {

        if (mIsCreate) {
            // 新建数据记录时，如果ID无效且变更记录中包含ID字段，则移除ID字段，避免插入时冲突
            if (mDataId == INVALID_ID && mDiffDataValues.containsKey(DataColumns.ID)) {
                mDiffDataValues.remove(DataColumns.ID);
            }

            // 新建数据记录时，必须关联一个有效的笔记ID，因此将笔记ID添加到变更记录中，并执行插入操作
            mDiffDataValues.put(DataColumns.NOTE_ID, noteId);
            Uri uri = mContentResolver.insert(Notes.CONTENT_DATA_URI, mDiffDataValues);
            try {
                // 从插入结果的URI中解析出新创建的数据记录ID，并更新当前对象的ID字段
                mDataId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
        } else {
            // 更新数据记录，如果存在变更字段，则执行更新操作。根据validateVersion参数决定是否启用版本验证，确保数据一致性
            if (mDiffDataValues.size() > 0) {
                int result = 0;
                if (!validateVersion) {
                    // 不启用版本验证，直接根据数据ID更新记录
                    result = mContentResolver.update(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mDataId), mDiffDataValues, null, null);
                } else {
                    // 启用版本验证，更新时添加条件，确保只有当笔记ID和版本号匹配时才执行更新，避免数据冲突
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

        // 提交后清空变更记录，并将新建标识设为false，表示当前对象已保存到数据库
        mDiffDataValues.clear();
        mIsCreate = false;
    }

    /**
     * 获取当前数据记录ID
     * 
     * @return 有效ID或INVALID_ID
     */    
    public long getId() {
        return mDataId;
    }
}
