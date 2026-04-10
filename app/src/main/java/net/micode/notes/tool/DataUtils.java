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

package net.micode.notes.tool;

// 内容提供者操作类：用于构建批量数据库操作（如一次性删除多条数据）
import android.content.ContentProviderOperation;
// 批量操作的结果类：接收数据库执行后的反馈
import android.content.ContentProviderResult;
// 内容解析器：核心工具，用于与数据库（ContentProvider）进行通信和交互
import android.content.ContentResolver;
// URI 工具类：用于拼接数据库地址，例如通过 ID 生成特定便签的访问路径
import android.content.ContentUris;
// 内容值类：类似于 Map，用于存放要存入数据库的键值对数据
import android.content.ContentValues;

// 异常类：处理批量操作失败或进程间通信错误时抛出
import android.content.OperationApplicationException;

// 数据库游标：用于存储和遍历查询结果（类似 ResultSet）
import android.database.Cursor;
// 远程异常类：处理跨进程调用时可能出现的异常
import android.os.RemoteException;

// 日志工具：用于在控制台打印调试信息
import android.util.Log;

// 便签数据常量类：定义了数据库表名、字段名、URI 等核心常量
import net.micode.notes.data.Notes;
// 通话便签字段类：专门处理通话记录便签（包含电话号码、通话时间等字段）
import net.micode.notes.data.Notes.CallNote;
// 便签通用字段类：定义便签的基础信息（如标题、创建时间、父文件夹 ID 等）
import net.micode.notes.data.Notes.NoteColumns;
// 桌面小部件属性类：用于处理桌面插件（Widget）相关的数据属性
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;


import java.util.ArrayList;
import java.util.HashSet;

public class DataUtils {
    // 日志标签，用于在 Logcat 中过滤查看 DataUtils 的日志
    public static final String TAG = "DataUtils";

    /**
     * 1. 批量删除便签

     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // --- 1. 安全检查 ---
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true; // 没东西删，视为成功
        }
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // --- 2. 准备批量操作容器 ---
        // 使用 ArrayList 暂存所有的删除指令，稍后一次性提交
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        
        for (long id : ids) {
            // --- 3. 核心保护逻辑 ---
            // 如果 ID 是系统根文件夹，绝对禁止删除！防止系统崩溃
            if(id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue; // 跳过本次循环，不加入删除队列
            }
            
            // 构建删除指令：
            // ContentUris.withAppendedId(...) 会生成类似 content://notes/123 的 URI
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            
            // 将指令加入队列
            operationList.add(builder.build());
        }

        // --- 4. 执行批量事务 ---
        try {
            // applyBatch: 将队列中的所有操作一次性提交给数据库
            // 这是一个原子操作：要么全部成功，要么全部失败
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            
            // 检查结果
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            // 处理进程间通信异常
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            // 处理应用层操作异常
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 2. 移动单个便签到指定文件夹

     */
    public static void moveNoteToFoler(ContentResolver resolver, long id, long srcFolderId, long desFolderId) {
        // 创建数据容器，用于存放更新后的字段值
        ContentValues values = new ContentValues();
        
        // 更新父ID（即移动位置）
        values.put(NoteColumns.PARENT_ID, desFolderId);
        
        // 记录原始父ID（用于同步逻辑，判断是否发生过移动）
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        
        // 标记为“本地已修改”
        // 这是一个关键标志，告诉同步服务：这条数据变了，下次需要同步到服务器
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        
        // 执行更新操作
        // null, null 表示不使用额外的筛选条件，直接更新指定 URI 的那条数据
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id), values, null, null);
    }

    /**
     * 3. 批量移动便签到指定文件夹

     */
    public static boolean batchMoveToFolder(ContentResolver resolver, HashSet<Long> ids,
            long folderId) {
        // --- 1. 安全检查 ---
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        // --- 2. 准备批量操作容器 ---
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        
        for (long id : ids) {
            // 构建更新指令 (newUpdate)
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            
            // 设置新值：修改父文件夹ID
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            
            // 设置新值：标记为本地已修改（触发同步）
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            
            // 加入队列
            operationList.add(builder.build());
        }

        // --- 3. 执行批量事务 ---
        try {
            ContentProviderResult[] results = resolver.applyBatch(Notes.AUTHORITY, operationList);
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }
    /**
     * 获取用户文件夹数量（排除系统文件夹）
     * 
     * 作用：用于统计用户创建了多少个文件夹（不包括系统默认的回收站、根目录等）。
     * 
     * SQL逻辑：SELECT COUNT(*) FROM notes WHERE type = TYPE_FOLDER AND parent_id != ID_TRASH_FOLER
     * 
     * @param resolver ContentResolver
     * @return int 用户文件夹的数量
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        // 执行查询
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                // 只查询一个字段：COUNT(*)，即统计行数
                new String[] { "COUNT(*)" },
                // 筛选条件：类型是文件夹 且 父ID不等于回收站ID
                // 这意味着：只统计正常的、未被删除的文件夹
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                // 参数绑定：第一个?是 TYPE_FOLDER，第二个?是 ID_TRASH_FOLER
                new String[] { String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)},
                null); // 排序方式：不需要

        int count = 0;
        if(cursor != null) {
            // 移动游标到第一行（因为 COUNT(*) 只会返回一行数据）
            if(cursor.moveToFirst()) {
                try {
                    count = cursor.getInt(0); // 获取第一列的整数值
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    cursor.close(); // 务必在 finally 中关闭游标，释放资源
                }
            }
        }
        return count;
    }

    /**
     * 检查指定ID的便签是否在数据库中可见
     * 
     * 作用：判断一个便签是否真实存在，且**不在回收站中**。
     * 场景：在编辑页面加载时，确认数据是否有效。
     * 
     * @param resolver ContentResolver
     * @param noteId   便签ID
     * @param type     便签类型（如 TYPE_NOTE）
     * @return boolean 如果存在且可见返回 true
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver, long noteId, int type) {
        // 查询指定 ID 的便签
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, // 查询所有字段
                // 筛选条件：类型匹配 且 父ID不等于回收站ID
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER,
                new String [] {String.valueOf(type)},
                null);

        boolean exist = false;
        if (cursor != null) {
            // 如果结果集行数大于0，说明找到了记录
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查便签ID是否存在于 Note 表中
     * 
     * 作用：纯粹检查 ID 是否存在，**不判断是否在回收站**。
     * 场景：通常用于底层数据校验。
     * 
     * @param resolver ContentResolver
     * @param noteId   便签ID
     * @return boolean 是否存在
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        // 查询指定 ID
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null); // 无条件查询

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查数据ID是否存在于 Data 表中
     * 
     * 作用：Data 表存储便签的具体内容（文本、图片）。
     * 此方法用于检查某个内容片段（Data ID）是否还存在。
     * 
     * @param resolver ContentResolver
     * @param dataId   数据ID（不是 Note ID）
     * @return boolean 是否存在
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        // 注意：这里查询的是 CONTENT_DATA_URI，而不是 NOTE_URI
        Cursor cursor = resolver.query(ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查文件夹名称是否可见（即是否已存在）
     * 
     * 作用：用于**新建文件夹时的重名检查**。
     * 如果返回 true，说明已经有一个同名的文件夹存在了。
     * 
     * @param resolver ContentResolver
     * @param name     文件夹名称
     * @return boolean 如果名称已存在返回 true
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                // 筛选条件：
                // 1. 类型必须是文件夹
                // 2. 不在回收站中
                // 3. 名称（Snippet）等于传入的 name
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER +
                " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER +
                " AND " + NoteColumns.SNIPPET + "=?",
                new String[] { name }, null);
        
        boolean exist = false;
        if(cursor != null) {
            if(cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取文件夹下关联的桌面小部件（Widget）信息
     * 
     * 作用：查询某个文件夹下的便签中，有哪些被添加到了桌面小部件。
     * 场景：当文件夹数据变更时，需要通知桌面小部件进行刷新。
     * 
     * @param resolver ContentResolver
     * @param folderId 文件夹ID
     * @return HashSet<AppWidgetAttribute> 包含 WidgetID 和类型的集合
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver, long folderId) {
        // 查询该文件夹下的所有便签，但只取 widget_id 和 widget_type 两列
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[] { NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE },
                NoteColumns.PARENT_ID + "=?", // 条件：属于该文件夹
                new String[] { String.valueOf(folderId) },
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);    // 获取 Widget ID
                        widget.widgetType = c.getInt(1);  // 获取 Widget 类型
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            c.close();
        }
        return set;
    }

       /**
     * 通过便签ID获取电话号码
     * 
     * 作用：针对“通话记录便签”，从 Data 表中提取关联的电话号码。
     * 原理：通话记录便签的号码不存储在 Note 表，而是存储在 Data 表的 PHONE_NUMBER 字段中。
     * 
     * @param resolver ContentResolver
     * @param noteId   便签ID
     * @return String 电话号码（如果找不到则返回空字符串）
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        // 查询 Data 表
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                // 只查询 PHONE_NUMBER 这一列
                new String [] { CallNote.PHONE_NUMBER },
                // 筛选条件：属于该 Note ID 且 数据类型是 CallNote
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                // 参数绑定
                new String [] { String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE },
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                return cursor.getString(0); // 返回第一列（电话号码）
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                cursor.close(); // 记得关闭游标
            }
        }
        return ""; // 没找到或出错，返回空字符串
    }

    /**
     * 通过电话号码和通话时间获取便签ID
     * 
     * 作用：防止重复创建通话记录。
     * 场景：接到电话时，先查数据库里有没有这个号码、这个时间的记录。如果有，直接复用；没有才新建。
     * 
     * 亮点：使用了 PHONE_NUMBERS_EQUAL 函数，这是 Android 系统提供的 SQL 函数，
     *      用于忽略号码格式差异（如 +86, 空格, -）进行比对。
     * 
     * @param resolver   ContentResolver
     * @param phoneNumber 电话号码
     * @param callDate   通话时间戳
     * @return long 便签ID（如果找不到返回 0）
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver, String phoneNumber, long callDate) {
        // 查询 Data 表
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                // 只查询 NOTE_ID 这一列
                new String [] { CallNote.NOTE_ID },
                // 复杂的 SQL 筛选：
                // 1. 时间匹配
                // 2. 类型是 CallNote
                // 3. 号码匹配（使用 PHONE_NUMBERS_EQUAL 函数处理格式差异）
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                + CallNote.PHONE_NUMBER + ",?)",
                // 参数绑定：时间, 类型, 号码
                new String [] { String.valueOf(callDate), CallNote.CONTENT_ITEM_TYPE, phoneNumber },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    return cursor.getLong(0); // 返回找到的 Note ID
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            cursor.close();
        }
        return 0; // 没找到，返回 0
    }

    /**
     * 通过便签ID获取便签摘要（Snippet）
     * 
     * 作用：获取便签的第一行文本或标题，用于在列表页展示。
     * 注意：这里直接查询 Note 表的 SNIPPET 字段。
     * 
     * @param resolver ContentResolver
     * @param noteId   便签ID
     * @return String 便签摘要
     * @throws IllegalArgumentException 如果 ID 不存在则抛出异常
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        // 查询 Note 表
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                // 只查询 SNIPPET 这一列
                new String [] { NoteColumns.SNIPPET },
                // 根据 ID 查找
                NoteColumns.ID + "=?",
                new String [] { String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                snippet = cursor.getString(0);
            }
            cursor.close();
            return snippet;
        }
        // 如果游标为 null，说明查不到数据，抛出异常
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化便签摘要
     * 
     * 作用：对获取到的摘要文本进行清洗，以便在 UI 上美观展示。
     * 处理逻辑：
     * 1. 去除首尾空格
     * 2. 只保留第一行（遇到换行符截断）
     * 
     * @param snippet 原始摘要文本
     * @return String 格式化后的文本
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            snippet = snippet.trim(); // 去除空格
            int index = snippet.indexOf('\n'); // 查找第一个换行符的位置
            if (index != -1) {
                // 如果包含换行符，只截取换行符之前的内容
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}
