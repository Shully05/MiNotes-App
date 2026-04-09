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

import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.tool.ResourceParser.NoteBgResources;

/**
 * 工作笔记类 - 业务层核心模型
 * 负责笔记的业务逻辑处理，是 UI 层和数据层之间的桥梁
 * 封装了笔记的创建、加载、保存、修改等操作
 */
public class WorkingNote {
    // 底层数据模型
    private Note mNote;
    // 笔记ID
    private long mNoteId;
    // 笔记内容
    private String mContent;
    // 笔记模式（普通/清单）
    private int mMode;

    private long mAlertDate;      // 提醒时间
    private long mModifiedDate;   // 修改时间
    private int mBgColorId;       // 背景颜色ID
    private int mWidgetId;        // 桌面小部件ID
    private int mWidgetType;      // 小部件类型
    private long mFolderId;       // 所属文件夹ID
    private Context mContext;     // 上下文
    private static final String TAG = "WorkingNote";
    private boolean mIsDeleted;   // 是否已删除

    // 笔记设置变化监听器（用于 UI 更新）
    private NoteSettingChangedListener mNoteSettingStatusListener;

    /**
     * 数据表查询投影字段
     * 定义查询 data 表时需要获取的列
     */
    public static final String[] DATA_PROJECTION = new String[] {
            DataColumns.ID,           // 数据ID
            DataColumns.CONTENT,      // 内容
            DataColumns.MIME_TYPE,    // MIME类型
            DataColumns.DATA1,        // 扩展字段1（模式/通话时间）
            DataColumns.DATA2,        // 扩展字段2
            DataColumns.DATA3,        // 扩展字段3（电话号码）
            DataColumns.DATA4,        // 扩展字段4
    };

    /**
     * 笔记表查询投影字段
     * 定义查询 note 表时需要获取的列
     */
    public static final String[] NOTE_PROJECTION = new String[] {
            NoteColumns.PARENT_ID,        // 父文件夹ID
            NoteColumns.ALERTED_DATE,     // 提醒时间
            NoteColumns.BG_COLOR_ID,      // 背景颜色ID
            NoteColumns.WIDGET_ID,        // 小部件ID
            NoteColumns.WIDGET_TYPE,      // 小部件类型
            NoteColumns.MODIFIED_DATE     // 修改时间
    };

    // 数据表游标列索引常量
    private static final int DATA_ID_COLUMN = 0;          // 数据ID
    private static final int DATA_CONTENT_COLUMN = 1;     // 内容
    private static final int DATA_MIME_TYPE_COLUMN = 2;   // MIME类型
    private static final int DATA_MODE_COLUMN = 3;        // 模式

    // 笔记表游标列索引常量
    private static final int NOTE_PARENT_ID_COLUMN = 0;       // 父文件夹ID
    private static final int NOTE_ALERTED_DATE_COLUMN = 1;    // 提醒时间
    private static final int NOTE_BG_COLOR_ID_COLUMN = 2;     // 背景颜色ID
    private static final int NOTE_WIDGET_ID_COLUMN = 3;       // 小部件ID
    private static final int NOTE_WIDGET_TYPE_COLUMN = 4;     // 小部件类型
    private static final int NOTE_MODIFIED_DATE_COLUMN = 5;   // 修改时间

    /**
     * 私有构造函数 - 创建新笔记
     *
     * @param context  上下文
     * @param folderId 所属文件夹ID
     */
    private WorkingNote(Context context, long folderId) {
        mContext = context;
        mAlertDate = 0;
        mModifiedDate = System.currentTimeMillis();
        mFolderId = folderId;
        mNote = new Note();
        mNoteId = 0;
        mIsDeleted = false;
        mMode = 0;
        mWidgetType = Notes.TYPE_WIDGET_INVALIDE;
    }

    /**
     * 私有构造函数 - 加载已有笔记
     *
     * @param context 上下文
     * @param noteId  笔记ID
     * @param folderId 文件夹ID（暂未使用）
     */
    private WorkingNote(Context context, long noteId, long folderId) {
        mContext = context;
        mNoteId = noteId;
        mFolderId = folderId;
        mIsDeleted = false;
        mNote = new Note();
        loadNote();  // 从数据库加载笔记数据
    }

    /**
     * 从数据库加载笔记基本信息（note 表）
     */
    private void loadNote() {
        // 查询 note 表
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mNoteId),
                NOTE_PROJECTION, null, null, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mFolderId = cursor.getLong(NOTE_PARENT_ID_COLUMN);
                mBgColorId = cursor.getInt(NOTE_BG_COLOR_ID_COLUMN);
                mWidgetId = cursor.getInt(NOTE_WIDGET_ID_COLUMN);
                mWidgetType = cursor.getInt(NOTE_WIDGET_TYPE_COLUMN);
                mAlertDate = cursor.getLong(NOTE_ALERTED_DATE_COLUMN);
                mModifiedDate = cursor.getLong(NOTE_MODIFIED_DATE_COLUMN);
            }
            cursor.close();
        } else {
            Log.e(TAG, "No note with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note with id " + mNoteId);
        }
        loadNoteData();  // 加载笔记的详细数据
    }

    /**
     * 加载笔记的详细数据（data 表）
     * 包括文本内容、模式、通话记录等
     */
    private void loadNoteData() {
        Cursor cursor = mContext.getContentResolver().query(
                Notes.CONTENT_DATA_URI,
                DATA_PROJECTION,
                DataColumns.NOTE_ID + "=?",
                new String[] { String.valueOf(mNoteId) },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(DATA_MIME_TYPE_COLUMN);
                    if (DataConstants.NOTE.equals(type)) {
                        // 文本笔记：加载内容和模式
                        mContent = cursor.getString(DATA_CONTENT_COLUMN);
                        mMode = cursor.getInt(DATA_MODE_COLUMN);
                        mNote.setTextDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else if (DataConstants.CALL_NOTE.equals(type)) {
                        // 通话记录笔记：保存数据ID
                        mNote.setCallDataId(cursor.getLong(DATA_ID_COLUMN));
                    } else {
                        Log.d(TAG, "Wrong note type with type:" + type);
                    }
                } while (cursor.moveToNext());
            }
            cursor.close();
        } else {
            Log.e(TAG, "No data with id:" + mNoteId);
            throw new IllegalArgumentException("Unable to find note's data with id " + mNoteId);
        }
    }

    /**
     * 创建空白笔记（工厂方法）
     *
     * @param context          上下文
     * @param folderId         所属文件夹ID
     * @param widgetId         小部件ID
     * @param widgetType       小部件类型
     * @param defaultBgColorId 默认背景颜色ID
     * @return 新创建的 WorkingNote 实例
     */
    public static WorkingNote createEmptyNote(Context context, long folderId, int widgetId,
                                              int widgetType, int defaultBgColorId) {
        WorkingNote note = new WorkingNote(context, folderId);
        note.setBgColorId(defaultBgColorId);
        note.setWidgetId(widgetId);
        note.setWidgetType(widgetType);
        return note;
    }

    /**
     * 加载已有笔记（工厂方法）
     *
     * @param context 上下文
     * @param id      笔记ID
     * @return WorkingNote 实例
     */
    public static WorkingNote load(Context context, long id) {
        return new WorkingNote(context, id, 0);
    }

    /**
     * 保存笔记到数据库
     *
     * @return 是否保存成功
     */
    public synchronized boolean saveNote() {
        if (isWorthSaving()) {
            if (!existInDatabase()) {
                // 新笔记：先在数据库创建记录，获取 noteId
                if ((mNoteId = Note.getNewNoteId(mContext, mFolderId)) == 0) {
                    Log.e(TAG, "Create new note fail with id:" + mNoteId);
                    return false;
                }
            }

            // 同步笔记数据到数据库
            mNote.syncNote(mContext, mNoteId);

            /**
             * 如果笔记有关联的小部件，通知小部件更新内容
             */
            if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                    && mWidgetType != Notes.TYPE_WIDGET_INVALIDE
                    && mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onWidgetChanged();
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * 判断笔记是否已存在于数据库中
     */
    public boolean existInDatabase() {
        return mNoteId > 0;
    }

    /**
     * 判断笔记是否值得保存
     * 条件：未被删除、且有实际内容或本地修改
     */
    private boolean isWorthSaving() {
        if (mIsDeleted || (!existInDatabase() && TextUtils.isEmpty(mContent))
                || (existInDatabase() && !mNote.isLocalModified())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 设置笔记设置变化监听器
     */
    public void setOnSettingStatusChangedListener(NoteSettingChangedListener l) {
        mNoteSettingStatusListener = l;
    }

    /**
     * 设置提醒时间
     *
     * @param date 提醒时间（毫秒时间戳）
     * @param set  是否设置提醒
     */
    public void setAlertDate(long date, boolean set) {
        if (date != mAlertDate) {
            mAlertDate = date;
            mNote.setNoteValue(NoteColumns.ALERTED_DATE, String.valueOf(mAlertDate));
        }
        if (mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onClockAlertChanged(date, set);
        }
    }

    /**
     * 标记笔记为已删除
     */
    public void markDeleted(boolean mark) {
        mIsDeleted = mark;
        if (mWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                && mWidgetType != Notes.TYPE_WIDGET_INVALIDE && mNoteSettingStatusListener != null) {
            mNoteSettingStatusListener.onWidgetChanged();
        }
    }

    /**
     * 设置背景颜色
     */
    public void setBgColorId(int id) {
        if (id != mBgColorId) {
            mBgColorId = id;
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onBackgroundColorChanged();
            }
            mNote.setNoteValue(NoteColumns.BG_COLOR_ID, String.valueOf(id));
        }
    }

    /**
     * 设置清单模式
     *
     * @param mode 0:普通模式, 1:清单模式
     */
    public void setCheckListMode(int mode) {
        if (mMode != mode) {
            if (mNoteSettingStatusListener != null) {
                mNoteSettingStatusListener.onCheckListModeChanged(mMode, mode);
            }
            mMode = mode;
            mNote.setTextData(TextNote.MODE, String.valueOf(mMode));
        }
    }

    /**
     * 设置小部件类型
     */
    public void setWidgetType(int type) {
        if (type != mWidgetType) {
            mWidgetType = type;
            mNote.setNoteValue(NoteColumns.WIDGET_TYPE, String.valueOf(mWidgetType));
        }
    }

    /**
     * 设置小部件ID
     */
    public void setWidgetId(int id) {
        if (id != mWidgetId) {
            mWidgetId = id;
            mNote.setNoteValue(NoteColumns.WIDGET_ID, String.valueOf(mWidgetId));
        }
    }

    /**
     * 设置笔记内容
     *
     * @param text 新的笔记内容
     */
    public void setWorkingText(String text) {
        if (!TextUtils.equals(mContent, text)) {
            mContent = text;
            mNote.setTextData(DataColumns.CONTENT, mContent);
        }
    }

    /**
     * 将笔记转换为通话记录笔记
     *
     * @param phoneNumber 电话号码
     * @param callDate    通话时间
     */
    public void convertToCallNote(String phoneNumber, long callDate) {
        mNote.setCallData(CallNote.CALL_DATE, String.valueOf(callDate));
        mNote.setCallData(CallNote.PHONE_NUMBER, phoneNumber);
        mNote.setNoteValue(NoteColumns.PARENT_ID, String.valueOf(Notes.ID_CALL_RECORD_FOLDER));
    }

    // ==================== Getter 方法 ====================

    public boolean hasClockAlert() {
        return (mAlertDate > 0 ? true : false);
    }

    public String getContent() {
        return mContent;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorResId() {
        return NoteBgResources.getNoteBgResource(mBgColorId);
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public int getTitleBgResId() {
        return NoteBgResources.getNoteTitleBgResource(mBgColorId);
    }

    public int getCheckListMode() {
        return mMode;
    }

    public long getNoteId() {
        return mNoteId;
    }

    public long getFolderId() {
        return mFolderId;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 笔记设置变化监听器接口
     * 当笔记属性发生变化时，回调相应的 UI 更新方法
     */
    public interface NoteSettingChangedListener {
        /**
         * 背景颜色变化时回调
         */
        void onBackgroundColorChanged();

        /**
         * 提醒时间变化时回调
         */
        void onClockAlertChanged(long date, boolean set);

        /**
         * 小部件内容变化时回调
         */
        void onWidgetChanged();

        /**
         * 清单模式切换时回调
         */
        void onCheckListModeChanged(int oldMode, int newMode);
    }
}