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

package net.micode.notes.data;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/**
 * 笔记应用的 ContentProvider
 * 作为数据访问的统一入口，封装了数据库操作，对外提供标准的数据接口
 * 其他应用可以通过 ContentResolver 访问笔记数据
 */
public class NotesProvider extends ContentProvider {
    // URI 匹配器，用于解析和匹配传入的 URI
    private static final UriMatcher mMatcher;
    // 数据库帮助类实例
    private NotesDatabaseHelper mHelper;
    private static final String TAG = "NotesProvider";

    // URI 匹配码定义
    private static final int URI_NOTE            = 1;  // 操作 note 表（集合）
    private static final int URI_NOTE_ITEM       = 2;  // 操作 note 表中的单条记录
    private static final int URI_DATA            = 3;  // 操作 data 表（集合）
    private static final int URI_DATA_ITEM       = 4;  // 操作 data 表中的单条记录
    private static final int URI_SEARCH          = 5;  // 搜索笔记
    private static final int URI_SEARCH_SUGGEST  = 6;  // 搜索建议（用于全局搜索）

    // 静态初始化块：注册 URI 匹配规则
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        // 注册 note 表的 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);           // content://micode_notes/note
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM);    // content://micode_notes/note/123

        // 注册 data 表的 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);           // content://micode_notes/data
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);    // content://micode_notes/data/123

        // 注册搜索相关的 URI 规则
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);       // 普通搜索
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);      // 搜索建议
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST); // 带参数的搜索建议
    }

    /**
     * 搜索结果的投影字段定义
     * 用于将笔记数据适配为系统全局搜索需要的格式
     * x'0A' 代表 SQLite 中的换行符，需要去除以便显示更多内容
     */
    private static final String NOTES_SEARCH_PROJECTION =
            NoteColumns.ID + ","                                    // 笔记ID
                    + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","  // 额外数据（ID）
                    + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","  // 第一行文本
                    + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","  // 第二行文本
                    + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","  // 搜索图标
                    + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","  // 点击意图（查看详情）
                    + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;  // 数据类型

    /**
     * 搜索查询的 SQL 语句
     * 从 note 表中搜索摘要内容匹配关键字的笔记（排除垃圾箱）
     */
    private static String NOTES_SNIPPET_SEARCH_QUERY =
            "SELECT " + NOTES_SEARCH_PROJECTION
                    + " FROM " + TABLE.NOTE
                    + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"           // 摘要内容模糊匹配
                    + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER  // 排除垃圾箱
                    + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;   // 只搜索笔记，不搜索文件夹

    /**
     * ContentProvider 创建时的回调
     * 初始化数据库帮助类实例
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 查询数据
     * 根据不同的 URI 类型执行相应的查询操作
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase();
        String id = null;

        switch (mMatcher.match(uri)) {
            // 查询 note 表（多条记录）
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            // 查询 note 表单条记录
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);  // 从 URI 中提取 ID
                c = db.query(TABLE.NOTE, projection,
                        NoteColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            // 查询 data 表（多条记录）
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null, sortOrder);
                break;

            // 查询 data 表单条记录
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection,
                        DataColumns.ID + "=" + id + parseSelection(selection),
                        selectionArgs, null, null, sortOrder);
                break;

            // 搜索笔记
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 搜索查询不允许指定排序、投影等参数
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection with this query");
                }

                // 获取搜索关键字
                String searchString = null;
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    // 搜索建议：从 URI 路径中获取
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    // 普通搜索：从查询参数中获取
                    searchString = uri.getQueryParameter("pattern");
                }

                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 执行模糊搜索（前后加 %）
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY, new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 设置通知 URI，当数据变化时通知观察者
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 插入数据
     * 根据 URI 类型插入到对应的表
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase();
        long dataId = 0, noteId = 0, insertedId = 0;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 插入 note 表
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;

            case URI_DATA:
                // 插入 data 表，需要验证 NOTE_ID 字段是否存在
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知 note 表数据变化
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // 通知 data 表数据变化
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        // 返回新插入记录的 URI
        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 删除数据
     * 根据 URI 类型删除对应的记录
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 删除 note 表记录（不能删除系统文件夹，ID <= 0）
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                long noteId = Long.valueOf(id);
                // ID 小于等于 0 的是系统文件夹，不允许删除
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知数据变化
        if (count > 0) {
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 更新数据
     * 根据 URI 类型更新对应的记录，同时更新版本号
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新 note 表（多条），同时增加版本号
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;

            case URI_NOTE_ITEM:
                // 更新 note 表单条记录，同时增加版本号
                id = uri.getPathSegments().get(1);
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;

            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;

            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                updateData = true;
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // 通知数据变化
        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 解析 selection 条件，添加 AND 前缀
     */
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 增加笔记版本号
     * 用于同步时的乐观锁机制
     *
     * @param id 笔记ID，-1 表示不限制
     * @param selection 额外的查询条件
     * @param selectionArgs 查询条件参数
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        // 构建 WHERE 条件
        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            // 替换参数占位符
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    /**
     * 获取 MIME 类型（暂未实现）
     */
    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }
}