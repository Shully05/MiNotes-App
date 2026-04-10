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

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x2尺寸桌面小部件提供者
 *
 * 该类负责创建和管理2x2尺寸的笔记桌面小部件。
 * 继承自NoteWidgetProvider基类，实现了特定于2x2小部件的布局、背景和类型配置。
 *
 * 主要功能：
 * - 提供2x2小部件的布局资源
 * - 根据背景颜色ID返回对应的2x2小部件背景图片
 * - 标识小部件类型为2x2尺寸
 *
 * @author MiCode Open Source Community
 * @see NoteWidgetProvider 小部件基类
 * @see NoteWidgetProvider_4x 4x4尺寸小部件
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 更新小部件时调用
     *
     * 当小部件需要更新时（如时间变化、用户操作等），系统会调用此方法。
     * 该方法调用父类的update方法来完成实际的小部件更新逻辑。
     *
     * @param context 应用上下文，用于访问资源和系统服务
     * @param appWidgetManager 小部件管理器，用于更新小部件
     * @param appWidgetIds 需要更新的小部件ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 调用父类的update方法执行实际的更新逻辑
        // 父类会处理：查询数据库获取笔记内容、设置背景、配置点击跳转等
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 获取小部件的布局资源ID
     *
     * 返回2x2小部件对应的布局文件。
     * 布局文件定义了小部件的UI结构，包括背景图片和文本显示区域。
     *
     * @return 2x2小部件的布局资源ID（R.layout.widget_2x）
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据背景颜色ID获取对应的背景图片资源
     *
     * 2x2小部件支持多种背景颜色（黄、蓝、白、绿、红），
     * 该方法根据传入的背景颜色ID返回对应的图片资源。
     *
     * @param bgId 背景颜色ID
     *            可选值：ResourceParser.YELLOW(0)、BLUE(1)、WHITE(2)、GREEN(3)、RED(4)
     * @return 对应颜色的2x2小部件背景图片资源ID
     * @see ResourceParser.WidgetBgResources#getWidget2xBgResource(int)
     */
    @Override
    protected int getBgResourceId(int bgId) {
        // 调用工具类获取指定颜色ID对应的2x2小部件背景资源
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 获取小部件类型标识
     *
     * 返回当前小部件的类型，用于区分不同尺寸的小部件。
     * 该类型值会存储在数据库的widget_type字段中。
     *
     * @return 小部件类型标识，值为Notes.TYPE_WIDGET_2X
     * @see Notes#TYPE_WIDGET_2X 2x2小部件类型常量
     * @see Notes#TYPE_WIDGET_4X 4x4小部件类型常量
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}