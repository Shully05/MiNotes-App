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
 * 笔记数据操作类
 * 处理笔记数据的本地存储操作，支持创建/更新数据记录
 * 实现与系统ContentProvider的交互及数据版本控制
 */
public class SqlNote {
    private static final String TAG = SqlNote.class.getSimpleName();

    private static final int INVALID_ID = -99999;

    // 笔记表查询字段投影（对应NOTE表结构）
    public static final String[] PROJECTION_NOTE = new String[] {
            NoteColumns.ID, NoteColumns.ALERTED_DATE, NoteColumns.BG_COLOR_ID,
            NoteColumns.CREATED_DATE, NoteColumns.HAS_ATTACHMENT, NoteColumns.MODIFIED_DATE,
            NoteColumns.NOTES_COUNT, NoteColumns.PARENT_ID, NoteColumns.SNIPPET, NoteColumns.TYPE,
            NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE, NoteColumns.SYNC_ID,
            NoteColumns.LOCAL_MODIFIED, NoteColumns.ORIGIN_PARENT_ID, NoteColumns.GTASK_ID,
            NoteColumns.VERSION
    };

    public static final int ID_COLUMN = 0;// 0.笔记ID
    public static final int ALERTED_DATE_COLUMN = 1;// 1.提醒时间
    public static final int BG_COLOR_ID_COLUMN = 2;// 2.背景颜色ID
    public static final int CREATED_DATE_COLUMN = 3;// 3.创建时间
    public static final int HAS_ATTACHMENT_COLUMN = 4;// 4.是否有附件
    public static final int MODIFIED_DATE_COLUMN = 5;// 5.修改时间
    public static final int NOTES_COUNT_COLUMN = 6;// 6.子笔记数量
    public static final int PARENT_ID_COLUMN = 7;// 7.父笔记ID
    public static final int SNIPPET_COLUMN = 8;// 8.内容摘要
    public static final int TYPE_COLUMN = 9;// 9.笔记类型
    public static final int WIDGET_ID_COLUMN = 10;// 10.关联的Widget ID
    public static final int WIDGET_TYPE_COLUMN = 11;// 11.关联的Widget类型
    public static final int SYNC_ID_COLUMN = 12;// 12.同步ID
    public static final int LOCAL_MODIFIED_COLUMN = 13;// 13.本地修改标识
    public static final int ORIGIN_PARENT_ID_COLUMN = 14;// 14.原始父笔记ID
    public static final int GTASK_ID_COLUMN = 15;// 15.关联的Google Task ID
    public static final int VERSION_COLUMN = 16;// 16.版本号

    
    private Context mContext;// 数据表查询字段投影（对应DATA表结构）

    private ContentResolver mContentResolver;// 系统内容解析器，用于数据库操作

    private boolean mIsCreate;// 标识当前笔记对象是否为新创建（未保存到数据库）

    private long mId;// 笔记ID，初始值为INVALID_ID表示未设置有效ID
    
    private long mAlertDate;// 提醒时间，单位为毫秒

    private int mBgColorId;// 背景颜色ID，关联资源文件中的颜色定义

    private long mCreatedDate;// 创建时间，单位为毫秒

    private int mHasAttachment;// 是否有附件，0表示没有，1表示有

    private long mModifiedDate;// 修改时间，单位为毫秒

    private long mParentId;// 父笔记ID，0表示没有父笔记

    private String mSnippet;// 内容摘要，通常为笔记内容的前几行文本

    private int mType;// 笔记类型，可能的值包括Notes.TYPE_NOTE、Notes.TYPE_FOLDER、Notes.TYPE_SYSTEM等

    private int mWidgetId;// 关联的Widget ID，如果笔记被某个Widget使用，则记录该Widget的ID，否则为INVALID_APPWIDGET_ID

    private int mWidgetType;// 关联的Widget类型，可能的值包括Notes.TYPE_WIDGET_INVALIDE、Notes.TYPE_WIDGET_1X1、Notes.TYPE_WIDGET_2X2等


    private long mOriginParent;// 原始父笔记ID，记录笔记被移动前的父笔记ID，用于同步时判断笔记是否被移动

    private long mVersion;// 版本号，用于数据版本控制，确保更新操作的正确性

    private ContentValues mDiffNoteValues;// 用于记录笔记变更的ContentValues对象，保存待更新的字段和值

    private ArrayList<SqlData> mDataList;// 笔记内容数据列表，包含与笔记关联的多个数据记录（如文本内容、图片等）

    // 数据表查询字段投影（对应DATA表结构）
    public SqlNote(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = true;
        mId = INVALID_ID;
        mAlertDate = 0;
        mBgColorId = ResourceParser.getDefaultBgId(context);
        mCreatedDate = System.currentTimeMillis();
        mHasAttachment = 0;
        mModifiedDate = System.currentTimeMillis();
        mParentId = 0;
        mSnippet = "";
        mType = Notes.TYPE_NOTE;
        mWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
        mOriginParent = 0;
        mVersion = 0;
        mDiffNoteValues = new ContentValues();
        mDataList = new ArrayList<SqlData>();
    }

    // 从数据库查询结果构造笔记对象,根据查询结果的Cursor初始化笔记对象的字段值，并加载关联的数据记录
    public SqlNote(Context context, Cursor c) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(c);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();
    }

    // 从数据库查询结果构造笔记对象,根据ID查询笔记记录并加载数据
    public SqlNote(Context context, long id) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mIsCreate = false;
        loadFromCursor(id);
        mDataList = new ArrayList<SqlData>();
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();
        mDiffNoteValues = new ContentValues();

    }

    // 从数据库查询结果构造笔记对象,根据ID查询笔记记录并加载数据,如果查询结果为空则抛出异常
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
                c.close();
        }
    }

    // 从数据库查询结果构造笔记对象,根据查询结果的Cursor初始化笔记对象的字段值
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

    // 加载笔记关联的数据记录,根据笔记ID查询关联的数据记录，并将查询结果转换为SqlData对象添加到数据列表中
    private void loadDataContent() {
        Cursor c = null;
        mDataList.clear();
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

    // 获取笔记关联的数据记录列表
    public boolean setContent(JSONObject js) {
        try {
            // 解析JSON对象，提取笔记字段和关联的数据记录，并更新当前笔记对象的字段值和数据列表
            JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
            if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_SYSTEM) {
                // 系统只读，我们只能更新系统文件夹的摘要
                Log.w(TAG, "cannot set system folder");
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_FOLDER) {
                // 对于文件夹，我们只能更新摘要和类型
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 文件夹类型只能是Notes.TYPE_FOLDER，如果JSON中没有指定类型或者指定的类型不合法，则默认为Notes.TYPE_FOLDER
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                // 只有当笔记类型发生变化或者当前对象是新创建时，才将类型更新到数据库，以避免不必要的数据库更新操作
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;
            } else if (note.getInt(NoteColumns.TYPE) == Notes.TYPE_NOTE) {
                // 对于普通笔记，我们需要更新所有字段，并处理关联的数据记录
                JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                long id = note.has(NoteColumns.ID) ? note.getLong(NoteColumns.ID) : INVALID_ID;
                if (mIsCreate || mId != id) {
                    mDiffNoteValues.put(NoteColumns.ID, id);
                }
                mId = id;

                // 提醒时间，如果JSON中没有指定提醒时间，则默认为0，表示没有提醒
                long alertDate = note.has(NoteColumns.ALERTED_DATE) ? note
                        .getLong(NoteColumns.ALERTED_DATE) : 0;
                if (mIsCreate || mAlertDate != alertDate) {
                    mDiffNoteValues.put(NoteColumns.ALERTED_DATE, alertDate);
                }
                mAlertDate = alertDate;

                // 背景颜色ID，如果JSON中没有指定背景颜色ID，则使用资源解析器获取默认的背景颜色ID
                int bgColorId = note.has(NoteColumns.BG_COLOR_ID) ? note
                        .getInt(NoteColumns.BG_COLOR_ID) : ResourceParser.getDefaultBgId(mContext);
                if (mIsCreate || mBgColorId != bgColorId) {
                    mDiffNoteValues.put(NoteColumns.BG_COLOR_ID, bgColorId);
                }
                mBgColorId = bgColorId;

                // 创建时间，如果JSON中没有指定创建时间，则默认为当前系统时间
                long createDate = note.has(NoteColumns.CREATED_DATE) ? note
                        .getLong(NoteColumns.CREATED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mCreatedDate != createDate) {
                    mDiffNoteValues.put(NoteColumns.CREATED_DATE, createDate);
                }
                mCreatedDate = createDate;

                // 是否有附件，如果JSON中没有指定该字段，则默认为0，表示没有附件
                int hasAttachment = note.has(NoteColumns.HAS_ATTACHMENT) ? note
                        .getInt(NoteColumns.HAS_ATTACHMENT) : 0;
                if (mIsCreate || mHasAttachment != hasAttachment) {
                    mDiffNoteValues.put(NoteColumns.HAS_ATTACHMENT, hasAttachment);
                }
                mHasAttachment = hasAttachment;

                // 修改时间，如果JSON中没有指定修改时间，则默认为当前系统时间
                long modifiedDate = note.has(NoteColumns.MODIFIED_DATE) ? note
                        .getLong(NoteColumns.MODIFIED_DATE) : System.currentTimeMillis();
                if (mIsCreate || mModifiedDate != modifiedDate) {
                    mDiffNoteValues.put(NoteColumns.MODIFIED_DATE, modifiedDate);
                }
                mModifiedDate = modifiedDate;

                // 父笔记ID，如果JSON中没有指定父笔记ID，则默认为0，表示没有父笔记
                long parentId = note.has(NoteColumns.PARENT_ID) ? note
                        .getLong(NoteColumns.PARENT_ID) : 0;
                if (mIsCreate || mParentId != parentId) {
                    mDiffNoteValues.put(NoteColumns.PARENT_ID, parentId);
                }
                mParentId = parentId;

                // 内容摘要，如果JSON中没有指定内容摘要，则默认为空字符串
                String snippet = note.has(NoteColumns.SNIPPET) ? note
                        .getString(NoteColumns.SNIPPET) : "";
                if (mIsCreate || !mSnippet.equals(snippet)) {
                    mDiffNoteValues.put(NoteColumns.SNIPPET, snippet);
                }
                mSnippet = snippet;

                // 笔记类型，如果JSON中没有指定笔记类型或者指定的类型不合法，则默认为Notes.TYPE_NOTE
                int type = note.has(NoteColumns.TYPE) ? note.getInt(NoteColumns.TYPE)
                        : Notes.TYPE_NOTE;
                if (mIsCreate || mType != type) {
                    mDiffNoteValues.put(NoteColumns.TYPE, type);
                }
                mType = type;

                // 关联的Widget ID，如果JSON中没有指定该字段，则默认为INVALID_APPWIDGET_ID，表示没有关联的Widget
                int widgetId = note.has(NoteColumns.WIDGET_ID) ? note.getInt(NoteColumns.WIDGET_ID)
                        : AppWidgetManager.INVALID_APPWIDGET_ID;
                if (mIsCreate || mWidgetId != widgetId) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_ID, widgetId);
                }
                mWidgetId = widgetId;

                // 关联的Widget类型，如果JSON中没有指定该字段或者指定的类型不合法，则默认为Notes.TYPE_WIDGET_INVALIDE，表示没有关联的Widget
                int widgetType = note.has(NoteColumns.WIDGET_TYPE) ? note
                        .getInt(NoteColumns.WIDGET_TYPE) : Notes.TYPE_WIDGET_INVALIDE;
                if (mIsCreate || mWidgetType != widgetType) {
                    mDiffNoteValues.put(NoteColumns.WIDGET_TYPE, widgetType);
                }
                mWidgetType = widgetType;

                // 原始父笔记ID，如果JSON中没有指定该字段，则默认为0，表示没有原始父笔记
                long originParent = note.has(NoteColumns.ORIGIN_PARENT_ID) ? note
                        .getLong(NoteColumns.ORIGIN_PARENT_ID) : 0;
                if (mIsCreate || mOriginParent != originParent) {
                    mDiffNoteValues.put(NoteColumns.ORIGIN_PARENT_ID, originParent);
                }
                mOriginParent = originParent;

                // 版本号，如果JSON中没有指定该字段，则默认为0，表示初始版本
                long version = note.has(NoteColumns.VERSION) ? note.getLong
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject data = dataArray.getJSONObject(i);
                    SqlData sqlData = null;
                    if (data.has(DataColumns.ID)) {
                        long dataId = data.getLong(DataColumns.ID);
                        for (SqlData temp : mDataList) {
                            if (dataId == temp.getId()) {
                                sqlData = temp;
                            }
                        }
                    }

                    // 如果在当前数据列表中没有找到对应ID的数据记录，则创建一个新的SqlData对象，并添加到数据列表中
                    if (sqlData == null) {
                        sqlData = new SqlData(mContext);
                        mDataList.add(sqlData);
                    }

                    // 将JSON对象中的数据字段设置到SqlData对象中，更新数据记录的内容
                    sqlData.setContent(data);
                }
            }
        } catch (JSONException e) {
            // 解析JSON对象时发生异常，记录错误日志并返回false表示设置内容失败
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 获取当前笔记对象的JSON表示
     * 
     * @return JSON对象（包含笔记字段和关联的数据记录）
     * @throws JSONException 序列化异常
     */
    public JSONObject getContent() {
        try {
            // 创建一个新的JSON对象，用于存储笔记的字段值和关联的数据记录
            JSONObject js = new JSONObject();

            // 如果当前笔记对象是新创建的（尚未保存到数据库），则无法获取有效的内容，记录错误日志并返回null
            if (mIsCreate) {
                Log.e(TAG, "it seems that we haven't created this in database yet");
                return null;
            }

            // 根据笔记类型构建JSON对象的内容，对于普通笔记需要包含所有字段和关联的数据记录，而对于文件夹和系统笔记只需要包含部分字段
            JSONObject note = new JSONObject();
            if (mType == Notes.TYPE_NOTE) {
                // 对于普通笔记，将所有字段值添加到JSON对象中，并将关联的数据记录转换为JSONArray添加到JSON对象中
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

                // 将关联的数据记录转换为JSONArray，并添加到JSON对象中，数据记录的内容通过调用SqlData对象的getContent方法获取
                JSONArray dataArray = new JSONArray();
                for (SqlData sqlData : mDataList) {
                    JSONObject data = sqlData.getContent();
                    if (data != null) {
                        dataArray.put(data);
                    }
                }
                js.put(GTaskStringUtils.META_HEAD_DATA, dataArray);
            } else if (mType == Notes.TYPE_FOLDER || mType == Notes.TYPE_SYSTEM) {
                // 对于文件夹和系统笔记，只将部分字段值添加到JSON对象中，不包含关联的数据记录，因为文件夹和系统笔记通常不包含数据记录
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

    // 设置笔记的父笔记ID，并将变更记录到ContentValues对象中，以便后续更新数据库
    public void setParentId(long id) {
        mParentId = id;
        mDiffNoteValues.put(NoteColumns.PARENT_ID, id);
    }

    // 设置笔记的内容摘要，并将变更记录到ContentValues对象中，以便后续更新数据库
    public void setGtaskId(String gid) {
        mDiffNoteValues.put(NoteColumns.GTASK_ID, gid);
    }

    // 设置笔记的同步ID，并将变更记录到ContentValues对象中，以便后续更新数据库
    public void setSyncId(long syncId) {
        mDiffNoteValues.put(NoteColumns.SYNC_ID, syncId);
    }

    // 设置笔记的内容摘要，并将变更记录到ContentValues对象中，以便后续更新数据库
    public void resetLocalModified() {
        mDiffNoteValues.put(NoteColumns.LOCAL_MODIFIED, 0);
    }

    // 获取笔记的ID
    public long getId() {
        return mId;
    }

    // 获取笔记的父笔记ID
    public long getParentId() {
        return mParentId;
    }

    // 获取笔记的内容摘要
    public String getSnippet() {
        return mSnippet;
    }

    // 获取笔记的类型
    public boolean isNoteType() {
        return mType == Notes.TYPE_NOTE;
    }

    // 获取笔记的版本号
    public void commit(boolean validateVersion) {
        if (mIsCreate) {
            // 如果当前笔记对象是新创建的（尚未保存到数据库），则执行插入操作，将笔记字段值保存到数据库中，并获取生成的笔记ID
            if (mId == INVALID_ID && mDiffNoteValues.containsKey(NoteColumns.ID)) {
                mDiffNoteValues.remove(NoteColumns.ID);
            }

            // 插入笔记记录到数据库，并获取生成的笔记ID，如果插入失败则抛出异常
            Uri uri = mContentResolver.insert(Notes.CONTENT_NOTE_URI, mDiffNoteValues);
            try {
                mId = Long.valueOf(uri.getPathSegments().get(1));
            } catch (NumberFormatException e) {
                Log.e(TAG, "Get note id error :" + e.toString());
                throw new ActionFailureException("create note failed");
            }
            if (mId == 0) {
                throw new IllegalStateException("Create thread id failed");
            }

            // 如果笔记类型是普通笔记，则提交关联的数据记录到数据库中，数据记录的内容通过调用SqlData对象的commit方法保存到数据库中
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, false, -1);
                }
            }
        } else {
            // 如果当前笔记对象已经存在于数据库中，则执行更新操作，将变更的字段值更新到数据库中，并根据版本控制机制确保更新的正确性
            if (mId <= 0 && mId != Notes.ID_ROOT_FOLDER && mId != Notes.ID_CALL_RECORD_FOLDER) {
                Log.e(TAG, "No such note");
                throw new IllegalStateException("Try to update note with invalid id");
            }
            if (mDiffNoteValues.size() > 0) {
                // 在执行更新操作之前，将版本号加1，以确保更新的版本控制机制能够正确地识别数据的变更，并避免数据冲突
                mVersion ++;
                int result = 0;
                if (!validateVersion) {
                    // 如果不需要验证版本号，则直接根据笔记ID更新数据库中的记录，不考虑版本号的匹配情况
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?)", new String[] {
                        String.valueOf(mId)
                    });
                } else {
                    result = mContentResolver.update(Notes.CONTENT_NOTE_URI, mDiffNoteValues, "("
                            + NoteColumns.ID + "=?) AND (" + NoteColumns.VERSION + "<=?)",
                            new String[] {
                                    String.valueOf(mId), String.valueOf(mVersion)
                            });
                }
                // 如果更新操作的结果为0，表示没有记录被更新，可能是因为版本号不匹配或者笔记被用户在同步时更新了，记录警告日志以提示可能的数据冲突情况
                if (result == 0) {
                    Log.w(TAG, "there is no update. maybe user updates note when syncing");
                }
            }

            // 如果笔记类型是普通笔记，则提交关联的数据记录到数据库中，数据记录的内容通过调用SqlData对象的commit方法保存到数据库中，提交时根据版本控制机制确保更新的正确性
            if (mType == Notes.TYPE_NOTE) {
                for (SqlData sqlData : mDataList) {
                    sqlData.commit(mId, validateVersion, mVersion);
                }
            }
        }

        // refresh local info
        loadFromCursor(mId);
        if (mType == Notes.TYPE_NOTE)
            loadDataContent();

        // 提交完成后，清空变更记录的ContentValues对象，并将新创建标识设置为false，以准备下一次的变更操作
        mDiffNoteValues.clear();
        mIsCreate = false;
    }
}
