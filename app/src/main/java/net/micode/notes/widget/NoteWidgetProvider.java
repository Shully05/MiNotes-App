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

package net.micode.notes.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.RemoteViews;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NoteEditActivity;
import net.micode.notes.ui.NotesListActivity;

/**
 * 桌面小部件提供者抽象基类
 *
 * 该类是所有笔记桌面小部件的基类，提供了小部件的通用功能实现。
 * 具体的小部件（如2x2、4x4）需要继承此类并实现抽象方法。
 *
 * 主要功能：
 * - 管理小部件与笔记的关联关系（通过widget_id字段）
 * - 从数据库查询笔记内容并显示在小部件上
 * - 处理小部件的点击事件（跳转到笔记编辑页面）
 * - 支持隐私模式（访客模式下显示提示文本）
 * - 小部件被删除时自动清除关联关系
 *
 * 工作流程：
 * 1. 用户添加小部件 → onUpdate() 被调用
 * 2. 查询数据库获取关联的笔记内容
 * 3. 根据子类提供的布局和背景资源构建RemoteViews
 * 4. 设置点击跳转的PendingIntent
 * 5. 更新小部件显示
 *
 * @author MiCode Open Source Community
 * @see NoteWidgetProvider_2x 2x2尺寸小部件
 * @see NoteWidgetProvider_4x 4x4尺寸小部件
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    /**
     * 数据库查询投影（需要查询的字段）
     * 用于从小部件关联的笔记中查询必要信息
     */
    public static final String [] PROJECTION = new String [] {
            NoteColumns.ID,           // 笔记ID，用于跳转时定位
            NoteColumns.BG_COLOR_ID,  // 背景颜色ID，用于设置小部件背景
            NoteColumns.SNIPPET       // 笔记摘要，用于小部件显示内容
    };

    /** 笔记ID在投影中的索引位置（第0列） */
    public static final int COLUMN_ID           = 0;

    /** 背景颜色ID在投影中的索引位置（第1列） */
    public static final int COLUMN_BG_COLOR_ID  = 1;

    /** 笔记摘要（显示内容）在投影中的索引位置（第2列） */
    public static final int COLUMN_SNIPPET      = 2;

    /** 日志标签，用于调试和错误输出 */
    private static final String TAG = "NoteWidgetProvider";

    /**
     * 小部件被删除时调用
     *
     * 当用户从桌面删除小部件时，系统会调用此方法。
     * 该方法负责清除数据库中与该小部件关联的widget_id字段，
     * 使得该笔记不再与小部件关联。
     *
     * @param context 应用上下文
     * @param appWidgetIds 被删除的小部件ID数组
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 创建ContentValues对象，准备更新数据库
        ContentValues values = new ContentValues();
        // 将widget_id设置为无效值，表示该笔记不再关联任何小部件
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // 遍历所有被删除的小部件ID
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 更新note表：将widget_id匹配的记录更新为无效值
            context.getContentResolver().update(Notes.CONTENT_NOTE_URI,
                    values,
                    NoteColumns.WIDGET_ID + "=?",
                    new String[] { String.valueOf(appWidgetIds[i])});
        }
    }

    /**
     * 根据小部件ID查询关联的笔记信息
     *
     * 从数据库中查询指定小部件ID关联的笔记数据，
     * 返回包含笔记ID、背景颜色和摘要的Cursor。
     *
     * @param context 应用上下文
     * @param widgetId 小部件ID
     * @return 包含笔记信息的Cursor，如果没有关联笔记则返回空Cursor
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        // 查询条件：widget_id匹配且笔记不在回收站中
        return context.getContentResolver().query(Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[] { String.valueOf(widgetId), String.valueOf(Notes.ID_TRASH_FOLER) },
                null);
    }

    /**
     * 更新小部件（非隐私模式）
     *
     * 这是update方法的简化版本，默认以非隐私模式更新小部件。
     *
     * @param context 应用上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 需要更新的小部件ID数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 更新小部件（核心方法）
     *
     * 这是小部件更新的核心方法，负责：
     * 1. 遍历所有需要更新小部件
     * 2. 查询数据库获取关联的笔记内容
     * 3. 构建RemoteViews（设置布局、背景、文本）
     * 4. 配置点击跳转的PendingIntent
     * 5. 调用AppWidgetManager更新小部件
     *
     * @param context 应用上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds 需要更新的小部件ID数组
     * @param privacyMode 隐私模式标志
     *        true: 隐私模式，显示提示文本，点击跳转到列表页
     *        false: 正常模式，显示笔记内容，点击跳转到编辑页
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        // 遍历所有需要更新小部件
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 检查小部件ID是否有效
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // 初始化默认值
                int bgId = ResourceParser.getDefaultBgId(context);  // 默认背景颜色
                String snippet = "";                                 // 默认显示内容

                // 创建跳转Intent（先创建，后续根据情况调整）
                Intent intent = new Intent(context, NoteEditActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);  // 保证单实例
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // 查询数据库获取小部件关联的笔记信息
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);

                if (c != null && c.moveToFirst()) {
                    // 安全检查：同一个widget_id不应该关联多个笔记
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;
                    }
                    // 从Cursor中读取笔记信息
                    snippet = c.getString(COLUMN_SNIPPET);           // 笔记摘要
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);             // 背景颜色
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID)); // 笔记ID
                    intent.setAction(Intent.ACTION_VIEW);            // 查看模式
                } else {
                    // 没有关联笔记时，显示默认文本
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);  // 新建模式
                }

                // 关闭Cursor，释放资源
                if (c != null) {
                    c.close();
                }

                // 构建RemoteViews（小部件的视图）
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());

                // 设置小部件背景图片
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));

                // 将背景颜色ID也传递给Intent，保持UI一致性
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

                /**
                 * 生成PendingIntent，用于处理小部件点击事件
                 *
                 * PendingIntent的作用：
                 * - 允许其他应用（如Launcher）以当前应用的权限执行Intent
                 * - 小部件点击时，系统会触发这个PendingIntent
                 */
                PendingIntent pendingIntent = null;

                if (privacyMode) {
                    // 隐私模式：显示提示文本，点击跳转到列表页
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], new Intent(
                            context, NotesListActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
                } else {
                    // 正常模式：显示笔记内容，点击跳转到编辑页
                    rv.setTextViewText(R.id.widget_text, snippet);
                    pendingIntent = PendingIntent.getActivity(context, appWidgetIds[i], intent,
                            PendingIntent.FLAG_UPDATE_CURRENT);
                }

                // 为小部件文本区域设置点击事件
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);

                // 通知AppWidgetManager更新小部件显示
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    /**
     * 获取背景图片资源ID（抽象方法）
     *
     * 子类需要实现此方法，根据背景颜色ID返回对应尺寸的背景图片资源。
     *
     * @param bgId 背景颜色ID（0:黄,1:蓝,2:白,3:绿,4:红）
     * @return 对应颜色和尺寸的背景图片资源ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 获取小部件布局资源ID（抽象方法）
     *
     * 子类需要实现此方法，返回对应尺寸的布局文件资源ID。
     *
     * @return 小部件布局资源ID
     */
    protected abstract int getLayoutId();

    /**
     * 获取小部件类型（抽象方法）
     *
     * 子类需要实现此方法，返回小部件的类型标识。
     *
     * @return 小部件类型（TYPE_WIDGET_2X 或 TYPE_WIDGET_4X）
     * @see Notes#TYPE_WIDGET_2X
     * @see Notes#TYPE_WIDGET_4X
     */
    protected abstract int getWidgetType();
}