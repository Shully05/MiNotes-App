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
 * 2x2尺寸的笔记桌面小部件提供者类
 * 继承自NoteWidgetProvider，专门处理2x2网格大小的桌面小部件
 *
 * 该类负责配置2x2小部件的特定属性，包括布局、背景资源和部件类型
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 更新桌面小部件时调用的方法
     * 当小部件需要更新显示内容时，系统会调用此方法
     *
     * @param context 应用程序上下文，用于访问资源和系统服务
     * @param appWidgetManager 小部件管理器，用于执行小部件更新操作
     * @param appWidgetIds 需要更新的小部件ID数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 调用父类的更新方法执行实际的小部件更新逻辑
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 获取2x2小部件的布局资源ID
     * 该方法返回专门为2x2尺寸设计的布局文件
     *
     * @return 2x2小部件布局的资源ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据背景ID获取对应的2x2小部件背景资源ID
     * 该方法将抽象的背景类型映射到具体的2x2尺寸背景图片资源
     *
     * @param bgId 背景类型的标识ID，来自ResourceParser.WidgetBgResources
     * @return 对应2x2小部件背景图片的资源ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 获取小部件类型
     * 返回表示2x2小部件的类型常量，用于标识和区分不同尺寸的小部件
     *
     * @return 2x2小部件的类型常量值
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}