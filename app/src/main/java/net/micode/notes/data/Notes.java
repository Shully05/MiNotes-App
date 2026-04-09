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

import android.net.Uri;

/**
 * 笔记应用的数据契约类
 * 定义了数据库表名、字段名、URI、常量等
 * 类似于一个数据字典，统一管理所有数据相关的常量
 */
public class Notes {
    // ContentProvider 的授权名称，用于访问数据
    public static final String AUTHORITY = "micode_notes";
    public static final String TAG = "Notes";

    // 笔记类型常量
    public static final int TYPE_NOTE     = 0;  // 普通笔记
    public static final int TYPE_FOLDER   = 1;  // 文件夹
    public static final int TYPE_SYSTEM   = 2;  // 系统文件夹

    /**
     * 系统文件夹的ID定义
     * {@link Notes#ID_ROOT_FOLDER } 根文件夹（默认文件夹）
     * {@link Notes#ID_TEMPARAY_FOLDER } 临时文件夹（用于存放不属于任何文件夹的笔记）
     * {@link Notes#ID_CALL_RECORD_FOLDER} 通话记录文件夹（自动保存通话记录）
     * {@link Notes#ID_TRASH_FOLER} 垃圾箱文件夹（存放已删除的笔记）
     */
    public static final int ID_ROOT_FOLDER = 0;
    public static final int ID_TEMPARAY_FOLDER = -1;
    public static final int ID_CALL_RECORD_FOLDER = -2;
    public static final int ID_TRASH_FOLER = -3;

    // Intent 传递数据时使用的额外参数键名
    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date";           // 提醒时间
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id"; // 背景颜色ID
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id";              // 小部件ID
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type";          // 小部件类型
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id";              // 文件夹ID
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date";              // 通话日期

    // 小部件类型常量
    public static final int TYPE_WIDGET_INVALIDE      = -1;  // 无效小部件
    public static final int TYPE_WIDGET_2X            = 0;   // 2x2 小部件
    public static final int TYPE_WIDGET_4X            = 1;   // 4x4 小部件

    /**
     * 数据类型常量类
     * 定义了支持的数据MIME类型
     */
    public static class DataConstants {
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE;      // 普通文本笔记类型
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE; // 通话记录笔记类型
    }

    /**
     * 查询所有笔记和文件夹的URI
     * 格式：content://micode_notes/note
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note");

    /**
     * 查询数据的URI
     * 格式：content://micode_notes/data
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data");

    /**
     * 笔记表（note）的列定义接口
     * 定义了数据库表中所有字段的名称和说明
     */
    public interface NoteColumns {
        /**
         * 行的唯一标识ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * 父文件夹的ID，用于实现文件夹层级结构
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id";

        /**
         * 笔记或文件夹的创建时间
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 最后修改时间
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 提醒时间（闹钟提醒）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date";

        /**
         * 文件夹的名称 或 笔记的文本摘要
         * <P> 类型: TEXT </P>
         */
        public static final String SNIPPET = "snippet";

        /**
         * 桌面小部件的ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id";

        /**
         * 桌面小部件的类型（2x2 或 4x4）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type";

        /**
         * 笔记背景颜色的ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id";

        /**
         * 是否包含附件（图片、音频等）
         * 0: 无附件, 1: 有附件
         * <P> 类型: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment";

        /**
         * 文件夹中包含的笔记数量
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count";

        /**
         * 记录类型：0=笔记, 1=文件夹, 2=系统
         * <P> 类型: INTEGER </P>
         */
        public static final String TYPE = "type";

        /**
         * 上次同步ID（用于云端同步）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id";

        /**
         * 标记本地是否有修改（用于同步冲突处理）
         * 0: 无修改, 1: 有修改
         * <P> 类型: INTEGER </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified";

        /**
         * 移入临时文件夹前的原始父文件夹ID
         * 用于恢复操作
         * <P> 类型: INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id";

        /**
         * Google Tasks 的同步ID
         * 用于与Google Tasks同步
         * <P> 类型: TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id";

        /**
         * 版本号，用于乐观锁同步冲突检测
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String VERSION = "version";
    }

    /**
     * 数据表（data）的列定义接口
     * 存储笔记的具体内容，支持多种数据类型
     */
    public interface DataColumns {
        /**
         * 行的唯一标识ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id";

        /**
         * MIME类型，表示数据的类型
         * 如：文本笔记、通话记录等
         * <P> 类型: Text </P>
         */
        public static final String MIME_TYPE = "mime_type";

        /**
         * 关联的笔记ID（外键，指向 note 表的 _id）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id";

        /**
         * 数据的创建时间
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date";

        /**
         * 数据的最后修改时间
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date";

        /**
         * 数据的实际内容（笔记正文）
         * <P> 类型: TEXT </P>
         */
        public static final String CONTENT = "content";

        /**
         * 通用数据列1，具体含义由 MIME_TYPE 决定
         * 用于存储整数类型数据
         * 在 TextNote 中表示：模式（普通/清单）
         * 在 CallNote 中表示：通话时间
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA1 = "data1";

        /**
         * 通用数据列2，用于存储整数类型数据
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA2 = "data2";

        /**
         * 通用数据列3，用于存储文本类型数据
         * 在 CallNote 中表示：电话号码
         * <P> 类型: TEXT </P>
         */
        public static final String DATA3 = "data3";

        /**
         * 通用数据列4，用于存储文本类型数据
         * <P> 类型: TEXT </P>
         */
        public static final String DATA4 = "data4";

        /**
         * 通用数据列5，用于存储文本类型数据
         * <P> 类型: TEXT </P>
         */
        public static final String DATA5 = "data5";
    }

    /**
     * 文本笔记数据类
     * 实现了 DataColumns 接口，表示普通文本类型的笔记
     */
    public static final class TextNote implements DataColumns {
        /**
         * 模式字段，使用 DATA1 列存储
         * 0: 普通模式, 1: 清单模式（待办列表）
         * <P> 类型: Integer </P>
         */
        public static final String MODE = DATA1;

        public static final int MODE_CHECK_LIST = 1;  // 清单模式

        // 目录类型的MIME类型
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note";
        // 单项数据的MIME类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note";

        // 查询文本笔记的URI
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note");
    }

    /**
     * 通话记录笔记数据类
     * 实现了 DataColumns 接口，表示通话记录类型的笔记
     */
    public static final class CallNote implements DataColumns {
        /**
         * 通话日期字段，使用 DATA1 列存储
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1;

        /**
         * 电话号码字段，使用 DATA3 列存储
         * <P> 类型: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3;

        // 目录类型的MIME类型
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note";
        // 单项数据的MIME类型
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note";

        // 查询通话记录的URI
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note");
    }
}