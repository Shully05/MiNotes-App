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
 * 桌面小部件的抽象基类（Abstract Base Class）
 *
 * 【设计理念】
 * 使用模板方法模式（Template Method Pattern），定义小部件更新的通用流程，
 * 而将具体的布局ID、背景资源等细节延迟到子类实现。
 *
 * 【继承关系】
 * AppWidgetProvider (Android系统类)
 *     └── NoteWidgetProvider (本类 - 抽象类)
 *             ├── NoteWidgetProvider_2x2 (2x2尺寸小部件)
 *             └── NoteWidgetProvider_4x4 (4x4尺寸小部件)
 *
 * 【核心功能】
 * 1. 更新小部件显示内容（便签摘要、背景色）
 * 2. 处理小部件点击事件（跳转到编辑页或列表页）
 * 3. 处理小部件删除事件（解除数据库绑定）
 * 4. 支持隐私模式（隐藏便签内容）
 */
public abstract class NoteWidgetProvider extends AppWidgetProvider {

    /**
     * 数据库查询的投影（Projection）
     * 定义从数据库中需要获取的列
     */
    public static final String[] PROJECTION = new String[]{
            NoteColumns.ID,           // 便签ID
            NoteColumns.BG_COLOR_ID,  // 背景颜色ID
            NoteColumns.SNIPPET       // 便签内容摘要（前N个字符）
    };

    /**
     * 列索引常量 - 用于从Cursor中快速获取数据
     * 与PROJECTION数组的顺序一一对应
     */
    public static final int COLUMN_ID = 0;           // 便签ID的索引
    public static final int COLUMN_BG_COLOR_ID = 1;  // 背景颜色ID的索引
    public static final int COLUMN_SNIPPET = 2;      // 内容摘要的索引

    /**
     * 日志标签，用于Logcat过滤和调试
     */
    private static final String TAG = "NoteWidgetProvider";

    /**
     * 当小部件从桌面上被删除时由系统调用
     *
     * 【触发时机】
     * 用户长按桌面小部件并将其拖拽到"移除"区域时触发
     *
     * 【主要操作】
     * 将数据库中对应widgetId的记录的WIDGET_ID字段重置为INVALID_APPWIDGET_ID
     * 实现小部件与便签数据的"解绑"
     *
     * @param context      应用上下文
     * @param appWidgetIds 被删除的小部件ID数组（可能同时删除多个）
     */
    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        // 构建更新内容：将WIDGET_ID设置为无效值
        ContentValues values = new ContentValues();
        values.put(NoteColumns.WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        // 遍历所有被删除的小部件ID，逐一解除绑定
        for (int i = 0; i < appWidgetIds.length; i++) {
            context.getContentResolver().update(
                    Notes.CONTENT_NOTE_URI,           // 要更新的数据URI
                    values,                           // 新值
                    NoteColumns.WIDGET_ID + "=?",     // WHERE条件
                    new String[]{String.valueOf(appWidgetIds[i])}  // 条件参数
            );
        }
    }

    /**
     * 根据小部件ID查询绑定的便签信息
     *
     * 【查询逻辑】
     * 1. 通过WIDGET_ID查找与该小部件绑定的便签
     * 2. 排除回收站中的便签（PARENT_ID != ID_TRASH_FOLDER）
     *
     * @param context  应用上下文
     * @param widgetId 要查询的小部件ID
     * @return 包含便签信息的Cursor，如果没有绑定则返回null或空Cursor
     */
    private Cursor getNoteWidgetInfo(Context context, int widgetId) {
        return context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,    // 查询的URI
                PROJECTION,                // 需要返回的列
                // WHERE条件：widgetId匹配 且 不在回收站中
                NoteColumns.WIDGET_ID + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{
                        String.valueOf(widgetId),                    // widgetId参数
                        String.valueOf(Notes.ID_TRASH_FOLER)        // 回收站ID参数
                },
                null                        // 不需要排序
        );
    }

    /**
     * 公开的更新方法 - 正常模式
     *
     * 【作用】
     * 供外部调用的入口方法，默认使用非隐私模式更新小部件
     *
     * 【调用时机】
     * - 系统触发onUpdate()回调时
     * - 便签内容发生变更需要刷新小部件时
     * - 小部件配置发生变化时
     *
     * @param context          应用上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds     需要更新的小部件ID数组
     */
    protected void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 调用私有方法，默认非隐私模式（privacyMode = false）
        update(context, appWidgetManager, appWidgetIds, false);
    }

    /**
     * 私有的核心更新方法 - 支持隐私模式
     *
     * 【隐私模式说明】
     * 当用户开启了应用锁或隐私保护时，小部件不应显示便签内容
     * 而是显示"访问模式"提示，点击后跳转到需要验证的列表页
     *
     * 【更新流程】
     * 1. 遍历所有需要更新的小部件ID
     * 2. 查询数据库获取绑定的便签信息
     * 3. 根据是否有绑定便签，设置不同的显示内容和点击行为
     * 4. 构建RemoteViews并更新到桌面
     *
     * @param context          应用上下文
     * @param appWidgetManager 小部件管理器
     * @param appWidgetIds     需要更新的小部件ID数组
     * @param privacyMode      是否启用隐私模式（true=隐藏内容，false=正常显示）
     */
    private void update(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds,
                        boolean privacyMode) {
        // 遍历所有需要更新的小部件
        for (int i = 0; i < appWidgetIds.length; i++) {
            // 跳过无效的小部件ID
            if (appWidgetIds[i] != AppWidgetManager.INVALID_APPWIDGET_ID) {

                // ========== 第一步：初始化默认值 ==========
                int bgId = ResourceParser.getDefaultBgId(context);  // 默认背景ID
                String snippet = "";                                 // 默认内容为空

                // ========== 第二步：构建点击跳转的Intent ==========
                // 创建跳转到编辑页的Intent
                Intent intent = new Intent(context, NoteEditActivity.class);
                // 设置启动模式：如果编辑页已在前台，则复用而不是新建
                intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                // 传递小部件ID，用于在编辑页中识别来源
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_ID, appWidgetIds[i]);
                // 传递小部件类型（2x2或4x4），用于适配不同的布局
                intent.putExtra(Notes.INTENT_EXTRA_WIDGET_TYPE, getWidgetType());

                // ========== 第三步：查询数据库获取便签信息 ==========
                Cursor c = getNoteWidgetInfo(context, appWidgetIds[i]);

                if (c != null && c.moveToFirst()) {
                    // 【已绑定便签的情况】

                    // 异常检查：同一个widgetId不应绑定多条便签
                    if (c.getCount() > 1) {
                        Log.e(TAG, "Multiple message with same widget id:" + appWidgetIds[i]);
                        c.close();
                        return;  // 数据异常，直接返回
                    }

                    // 从Cursor中提取便签数据
                    snippet = c.getString(COLUMN_SNIPPET);      // 便签内容摘要
                    bgId = c.getInt(COLUMN_BG_COLOR_ID);        // 背景颜色ID

                    // 设置Intent参数：传递便签ID
                    intent.putExtra(Intent.EXTRA_UID, c.getLong(COLUMN_ID));
                    // 设置Action为VIEW，表示查看/编辑已有便签
                    intent.setAction(Intent.ACTION_VIEW);

                } else {
                    // 【未绑定便签的情况】

                    // 显示默认提示文本（如："暂无内容，点击添加"）
                    snippet = context.getResources().getString(R.string.widget_havenot_content);
                    // 设置Action为INSERT_OR_EDIT，表示新建或编辑便签
                    intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
                }

                // 关闭Cursor，释放资源
                if (c != null) {
                    c.close();
                }

                // ========== 第四步：构建RemoteViews并更新UI ==========
                // 创建RemoteViews对象（跨进程更新UI的关键）
                // 参数：包名、布局资源ID（由子类实现）
                RemoteViews rv = new RemoteViews(context.getPackageName(), getLayoutId());

                // 设置小部件背景图片（根据背景ID获取对应的资源）
                rv.setImageViewResource(R.id.widget_bg_image, getBgResourceId(bgId));

                // 将背景ID也传递给编辑页，保持背景一致性
                intent.putExtra(Notes.INTENT_EXTRA_BACKGROUND_ID, bgId);

                // ========== 第五步：根据隐私模式设置显示内容和点击事件 ==========
                PendingIntent pendingIntent = null;

                if (privacyMode) {
                    // 【隐私模式】隐藏便签内容，显示锁定提示
                    rv.setTextViewText(R.id.widget_text,
                            context.getString(R.string.widget_under_visit_mode));

                    // 点击后跳转到列表页（需要先解锁应用）
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetIds[i],
                            new Intent(context, NotesListActivity.class),  // 跳转到列表页
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
                } else {
                    // 【正常模式】显示便签内容摘要
                    rv.setTextViewText(R.id.widget_text, snippet);

                    // 点击后直接跳转到编辑页
                    pendingIntent = PendingIntent.getActivity(
                            context,
                            appWidgetIds[i],
                            intent,  // 跳转到编辑页
                            PendingIntent.FLAG_UPDATE_CURRENT
                    );
                }

                // ========== 第六步：绑定点击事件并更新小部件 ==========
                // 为小部件的文本视图设置点击 PendingIntent
                rv.setOnClickPendingIntent(R.id.widget_text, pendingIntent);

                // 通知AppWidgetManager更新指定的小部件
                appWidgetManager.updateAppWidget(appWidgetIds[i], rv);
            }
        }
    }

    /**
     * 抽象方法：获取指定背景ID对应的资源ID
     *
     * 【作用】
     * 将数据库中的背景ID（数字）映射为实际的drawable资源
     * 不同尺寸的小部件可能使用不同的背景图资源
     *
     * 【示例实现】
     * 子类可能返回：
     * - R.drawable.widget_bg_blue_2x2
     * - R.drawable.widget_bg_blue_4x4
     *
     * @param bgId 背景颜色ID（来自数据库）
     * @return 对应的drawable资源ID
     */
    protected abstract int getBgResourceId(int bgId);

    /**
     * 抽象方法：获取小部件的布局资源ID
     *
     * 【作用】
     * 不同尺寸的小部件使用不同的布局文件
     *
     * 【示例实现】
     * - NoteWidgetProvider_2x2 返回 R.layout.widget_2x2
     * - NoteWidgetProvider_4x4 返回 R.layout.widget_4x4
     *
     * @return 布局资源ID
     */
    protected abstract int getLayoutId();

    /**
     * 抽象方法：获取小部件的类型标识
     *
     * 【作用】
     * 用于标识小部件的尺寸类型，存储到数据库中
     * 在打开编辑页时，可以根据类型适配不同的UI
     *
     * 【示例实现】
     * - NoteWidgetProvider_2x2 返回 Notes.TYPE_WIDGET_2X2
     * - NoteWidgetProvider_4x4 返回 Notes.TYPE_WIDGET_4X4
     *
     * @return 小部件类型常量
     */
    protected abstract int getWidgetType();
}