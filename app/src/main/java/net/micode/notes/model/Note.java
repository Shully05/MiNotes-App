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

package net.micode.notes.model;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

/**
 * 笔记数据模型类
 * 封装了笔记的数据操作，包括笔记本身和关联的数据内容
 * 负责将业务层的数据变更同步到数据库
 */
public class Note {
    // 笔记本身的字段变更（如修改时间、父文件夹等）
    private ContentValues mNoteDiffValues;
    // 笔记关联的数据内容（文本内容、通话记录等）
    private NoteData mNoteData;
    private static final String TAG = "Note";

    /**
     * 创建新笔记，返回新笔记的ID
     *
     * @param context  上下文
     * @param folderId 所属文件夹ID
     * @return 新创建的笔记ID
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // 在数据库中创建新笔记
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);      // 创建时间
        values.put(NoteColumns.MODIFIED_DATE, createdTime);     // 修改时间
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);          // 类型：笔记
        values.put(NoteColumns.LOCAL_MODIFIED, 1);              // 标记本地已修改
        values.put(NoteColumns.PARENT_ID, folderId);            // 父文件夹ID

        // 通过 ContentProvider 插入数据
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);

        long noteId = 0;
        try {
            // 从返回的 URI 中解析出笔记ID
            noteId = Long.valueOf(uri.getPathSegments().get(1));
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    /**
     * 构造函数：初始化数据容器
     */
    public Note() {
        mNoteDiffValues = new ContentValues();  // 存储笔记字段的修改
        mNoteData = new NoteData();              // 存储关联数据的修改
    }

    /**
     * 设置笔记字段的值
     *
     * @param key   字段名
     * @param value 字段值
     */
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);              // 标记本地修改
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis()); // 更新修改时间
    }

    /**
     * 设置文本数据
     */
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    /**
     * 设置文本数据ID（用于更新已有数据）
     */
    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    /**
     * 获取文本数据ID
     */
    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    /**
     * 设置通话记录数据ID
     */
    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    /**
     * 设置通话记录数据
     */
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    /**
     * 判断笔记是否有本地修改
     */
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    /**
     * 同步笔记到数据库
     *
     * @param context 上下文
     * @param noteId  笔记ID
     * @return 是否同步成功
     */
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        // 没有本地修改，无需同步
        if (!isLocalModified()) {
            return true;
        }

        /**
         * 先更新笔记主表
         * 包含：本地修改标记、修改时间等元数据
         */
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                mNoteDiffValues, null, null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // 继续执行，不返回
        }
        mNoteDiffValues.clear();  // 清除已同步的修改

        // 更新关联的数据内容
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    /**
     * 笔记数据内部类
     * 管理笔记关联的具体数据内容（文本内容、通话记录等）
     * 支持两种类型：TextNote（文本笔记）和 CallNote（通话记录）
     */
    private class NoteData {
        private long mTextDataId;          // 文本数据的ID（用于更新时定位）
        private ContentValues mTextDataValues;  // 文本数据的待修改内容

        private long mCallDataId;          // 通话记录的ID
        private ContentValues mCallDataValues; // 通话记录的待修改内容

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        /**
         * 判断是否有本地修改
         */
        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        /**
         * 设置文本数据ID
         */
        void setTextDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        /**
         * 设置通话记录ID
         */
        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        /**
         * 设置通话记录数据
         */
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 设置文本数据
         */
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        /**
         * 将数据变更推送到 ContentProvider
         *
         * @param context 上下文
         * @param noteId  关联的笔记ID
         * @return 成功返回笔记URI，失败返回null
         */
        Uri pushIntoContentResolver(Context context, long noteId) {
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            // 批量操作列表
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // ========== 处理文本数据 ==========
            if (mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);  // 关联笔记ID

                if (mTextDataId == 0) {
                    // 新增文本数据
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有文本数据
                    builder = ContentProviderOperation.newUpdate(
                            ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear();
            }

            // ========== 处理通话记录数据 ==========
            if (mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);  // 关联笔记ID

                if (mCallDataId == 0) {
                    // 新增通话记录
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    // 更新已有通话记录
                    builder = ContentProviderOperation.newUpdate(
                            ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // ========== 执行批量操作 ==========
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null)
                            ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}