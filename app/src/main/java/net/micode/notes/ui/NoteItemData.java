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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 列表项数据模型
 * 封装单个列表项（笔记/文件夹）的所有显示数据
 */
public class NoteItemData {

    // 数据库查询字段投影
    static final String [] PROJECTION = new String [] {
            NoteColumns.ID,               // 0: 笔记ID
            NoteColumns.ALERTED_DATE,     // 1: 提醒时间
            NoteColumns.BG_COLOR_ID,      // 2: 背景颜色ID
            NoteColumns.CREATED_DATE,     // 3: 创建时间
            NoteColumns.HAS_ATTACHMENT,   // 4: 是否有附件
            NoteColumns.MODIFIED_DATE,    // 5: 修改时间
            NoteColumns.NOTES_COUNT,      // 6: 笔记数量（仅文件夹）
            NoteColumns.PARENT_ID,        // 7: 父文件夹ID
            NoteColumns.SNIPPET,          // 8: 摘要/文件夹名
            NoteColumns.TYPE,             // 9: 类型（0笔记/1文件夹/2系统）
            NoteColumns.WIDGET_ID,        // 10: 小部件ID
            NoteColumns.WIDGET_TYPE,      // 11: 小部件类型
    };

    // 字段索引常量
    private static final int ID_COLUMN                    = 0;
    private static final int ALERTED_DATE_COLUMN          = 1;
    private static final int BG_COLOR_ID_COLUMN           = 2;
    private static final int CREATED_DATE_COLUMN          = 3;
    private static final int HAS_ATTACHMENT_COLUMN        = 4;
    private static final int MODIFIED_DATE_COLUMN         = 5;
    private static final int NOTES_COUNT_COLUMN           = 6;
    private static final int PARENT_ID_COLUMN             = 7;
    private static final int SNIPPET_COLUMN               = 8;
    private static final int TYPE_COLUMN                  = 9;
    private static final int WIDGET_ID_COLUMN             = 10;
    private static final int WIDGET_TYPE_COLUMN           = 11;

    // 基础数据字段
    private long mId;                 // 笔记ID
    private long mAlertDate;          // 提醒时间
    private int mBgColorId;           // 背景颜色ID
    private long mCreatedDate;        // 创建时间
    private boolean mHasAttachment;   // 是否有附件
    private long mModifiedDate;       // 修改时间
    private int mNotesCount;          // 笔记数量（文件夹用）
    private long mParentId;           // 父文件夹ID
    private String mSnippet;          // 标题/摘要
    private int mType;                // 类型
    private int mWidgetId;            // 小部件ID
    private int mWidgetType;          // 小部件类型
    private String mName;             // 联系人姓名（通话记录用）
    private String mPhoneNumber;      // 电话号码（通话记录用）

    // 列表位置信息（用于背景圆角样式）
    private boolean mIsLastItem;               // 是否是最后一项
    private boolean mIsFirstItem;              // 是否是第一项
    private boolean mIsOnlyOneItem;            // 是否只有一项
    private boolean mIsOneNoteFollowingFolder; // 是否只有一个笔记跟在文件夹后
    private boolean mIsMultiNotesFollowingFolder; // 是否有多个笔记跟在文件夹后

    /**
     * 构造函数：从Cursor中读取数据封装
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 读取基础字段
        mId = cursor.getLong(ID_COLUMN);
        mAlertDate = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate = cursor.getLong(CREATED_DATE_COLUMN);
        mHasAttachment = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0) ? true : false;
        mModifiedDate = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet = cursor.getString(SNIPPET_COLUMN);
        // 移除列表模式标记符号
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "").replace(
                NoteEditActivity.TAG_UNCHECKED, "");
        mType = cursor.getInt(TYPE_COLUMN);
        mWidgetId = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        // 通话记录文件夹：获取联系人信息
        mPhoneNumber = "";
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        if (mName == null) {
            mName = "";
        }
        checkPostion(cursor);
    }

    /**
     * 检查列表项位置信息（用于决定背景圆角样式）
     */
    private void checkPostion(Cursor cursor) {
        mIsLastItem = cursor.isLast() ? true : false;
        mIsFirstItem = cursor.isFirst() ? true : false;
        mIsOnlyOneItem = (cursor.getCount() == 1);
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder = false;

        // 判断笔记是否跟在文件夹后面
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();
            if (cursor.moveToPrevious()) {
                if (cursor.getInt(TYPE_COLUMN) == Notes.TYPE_FOLDER
                        || cursor.getInt(TYPE_COLUMN) == Notes.TYPE_SYSTEM) {
                    if (cursor.getCount() > (position + 1)) {
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        mIsOneNoteFollowingFolder = true;
                    }
                }
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException("cursor move to previous but can't move back");
                }
            }
        }
    }

    // ==================== Getter方法 ====================

    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    public boolean isLast() {
        return mIsLastItem;
    }

    public String getCallName() {
        return mName;
    }

    public boolean isFirst() {
        return mIsFirstItem;
    }

    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    public long getId() {
        return mId;
    }

    public long getAlertDate() {
        return mAlertDate;
    }

    public long getCreatedDate() {
        return mCreatedDate;
    }

    public boolean hasAttachment() {
        return mHasAttachment;
    }

    public long getModifiedDate() {
        return mModifiedDate;
    }

    public int getBgColorId() {
        return mBgColorId;
    }

    public long getParentId() {
        return mParentId;
    }

    public int getNotesCount() {
        return mNotesCount;
    }

    public long getFolderId () {
        return mParentId;
    }

    public int getType() {
        return mType;
    }

    public int getWidgetType() {
        return mWidgetType;
    }

    public int getWidgetId() {
        return mWidgetId;
    }

    public String getSnippet() {
        return mSnippet;
    }

    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 获取笔记类型（静态方法，用于适配器遍历时判断）
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}