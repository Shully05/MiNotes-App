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
 * 4x4尺寸桌面小部件提供者
 *
 * 该类负责创建和管理4x4尺寸的笔记桌面小部件。
 * 继承自NoteWidgetProvider基类，实现了特定于4x4小部件的布局、背景和类型配置。
 *
 * 主要功能：
 * - 提供4x4小部件的布局资源（比2x2小部件显示更多内容）
 * - 根据背景颜色ID返回对应的4x4小部件背景图片
 * - 标识小部件类型为4x4尺寸
 *
 * 与2x2小部件的区别：
 * - 4x4小部件占用更大的桌面空间，可以显示更多笔记内容
 * - 使用不同的布局文件和背景资源
 * - 类型标识不同，便于系统区分和管理
 *
 * @author MiCode Open Source Community
 * @see NoteWidgetProvider 小部件基类
 * @see NoteWidgetProvider_2x 2x2尺寸小部件
 */
public class NoteWidgetProvider_4x extends NoteWidgetProvider {

    /**
     * 更新小部件时调用
     *
     * 当小部件需要更新时（如时间变化、用户操作、笔记内容变更等），
     * 系统会调用此方法。该方法调用父类的update方法来完成实际的小部件更新逻辑。
     *
     * 更新流程：
     * 1. 查询数据库获取关联的笔记内容
     * 2. 根据getLayoutId()获取4x4布局
     * 3. 根据getBgResourceId()设置背景颜色
     * 4. 设置点击跳转Intent
     * 5. 刷新小部件显示
     *
     * @param context 应用上下文，用于访问资源和系统服务
     * @param appWidgetManager 小部件管理器，用于更新小部件
     * @param appWidgetIds 需要更新的小部件ID数组（可能包含多个小部件）
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
     * 返回4x4小部件对应的布局文件。
     * 布局文件定义了小部件的UI结构，包括背景图片和文本显示区域。
     *
     * 注意：此方法没有@Override注解，因为父类中getLayoutId()是抽象方法，
     * 但为了代码规范和可读性，建议添加@Override注解。
     *
     * @return 4x4小部件的布局资源ID（R.layout.widget_4x）
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    /**
     * 根据背景颜色ID获取对应的背景图片资源
     *
     * 4x4小部件支持多种背景颜色（黄、蓝、白、绿、红），
     * 该方法根据传入的背景颜色ID返回对应的图片资源。
     *
     * 背景颜色ID与颜色的对应关系：
     * - ResourceParser.YELLOW (0): 黄色背景
     * - ResourceParser.BLUE   (1): 蓝色背景
     * - ResourceParser.WHITE  (2): 白色背景
     * - ResourceParser.GREEN  (3): 绿色背景
     * - ResourceParser.RED    (4): 红色背景
     *
     * @param bgId 背景颜色ID，取值范围0-4
     * @return 对应颜色的4x4小部件背景图片资源ID
     * @see ResourceParser.WidgetBgResources#getWidget4xBgResource(int)
     */
    @Override
    protected int getBgResourceId(int bgId) {
        // 调用工具类获取指定颜色ID对应的4x4小部件背景资源
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    /**
     * 获取小部件类型标识
     *
     * 返回当前小部件的类型，用于区分不同尺寸的小部件。
     * 该类型值会存储在数据库的widget_type字段中，
     * 当笔记被删除或修改时，系统根据此类型找到对应的小部件进行更新。
     *
     * @return 小部件类型标识，值为Notes.TYPE_WIDGET_4X
     * @see Notes#TYPE_WIDGET_2X 2x2小部件类型常量
     * @see Notes#TYPE_WIDGET_4X 4x4小部件类型常量
     * @see Notes#TYPE_WIDGET_INVALIDE 无效小部件类型
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}