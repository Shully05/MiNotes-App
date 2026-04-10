/* 
 * 版权声明和 Apache 2.0 许可证信息
 * 这意味着该代码是开源的，允许你自由使用、修改，但需保留版权声明。
 */
package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager; // 用于读取用户设置的偏好

import net.micode.notes.R; // 引用项目的资源索引
import net.micode.notes.ui.NotesPreferenceActivity; // 引用偏好设置界面，用于获取键值

/**
 * 资源解析器 (ResourceParser)
 * 
 * 该类是整个应用的“视觉资源字典”。
 * 它的主要职责是将便签的背景颜色、字体大小等属性的“内部代码”（整数）
 * 转换为 Android 系统能识别的“资源 ID”（指向具体的图片或样式）。
 * 
 * 为什么需要它？
 * 1. 数据存储：SharedPreferences 通常存储简单的 int 类型（如 colorId=0）。
 * 2. 资源调用：UI 绘制需要具体的资源 ID（如 R.drawable.edit_yellow）。
 * 3. 统一管理：如果以后要增加一种新颜色，只需要在这里修改数组，而不需要改动业务逻辑代码。
 */
public class ResourceParser {

    // 定义颜色的内部代码（Constants）
    // 这些数字代表了不同的背景颜色
    public static final int YELLOW           = 0; // 黄色
    public static final int BLUE             = 1; // 蓝色
    public static final int WHITE            = 2; // 白色
    public static final int GREEN            = 3; // 绿色
    public static final int RED              = 4; // 红色

    // 默认设置：如果没有特别指定，便签默认是黄色的
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // 定义字体大小的内部代码
    public static final int TEXT_SMALL       = 0; // 小号
    public static final int TEXT_MEDIUM      = 1; // 中号（默认）
    public static final int TEXT_LARGE       = 2; // 大号
    public static final int TEXT_SUPER       = 3; // 超大号

    // 字体默认设置
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    
    /**
     * 便签编辑界面的背景资源类
     * 
     * 作用：当用户点击一个便签进入编辑模式时，提供背景图和标题图。
     * 结构：这是一个静态内部类，用于封装与“编辑状态”相关的资源映射。
     */
    public static class NoteBgResources {
        // 私有静态数组：存储编辑状态下便签主体的背景图资源 ID
        // 数组顺序必须严格对应上面定义的 YELLOW=0, BLUE=1... 的顺序
        private final static int [] BG_EDIT_RESOURCES = new int [] {
            R.drawable.edit_yellow,  // ID 0 -> 黄色背景
            R.drawable.edit_blue,    // ID 1 -> 蓝色背景
            R.drawable.edit_white,   // ID 2 -> 白色背景
            R.drawable.edit_green,   // ID 3 -> 绿色背景
            R.drawable.edit_red      // ID 4 -> 红色背景
        };

        // 私有静态数组：存储编辑状态下便签标题栏的背景图资源 ID
        // 通常标题栏和主体颜色一致，但图片资源是分开的（可能是为了圆角处理）
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
            R.drawable.edit_title_yellow,
            R.drawable.edit_title_blue,
            R.drawable.edit_title_white,
            R.drawable.edit_title_green,
            R.drawable.edit_title_red
        };

        /**
         * 获取便签主体背景资源
         * 
         * @param id 颜色 ID (0-4)
         * @return 对应的 Drawable 资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取便签标题背景资源
         * 
         * @param id 颜色 ID (0- 4)
         * @return 对应的 Drawable 资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认的背景 ID
     * 
     * 逻辑：检查用户的全局设置。
     * 如果用户开启了“随机背景色”选项，则返回一个随机颜色 ID；
     * 否则返回默认的黄色 (BG_DEFAULT_COLOR)。
     * 
     * @param context 上下文，用于获取 SharedPreferences
     * @return int 颜色 ID
     */
    public static int getDefaultBgId(Context context) {
        // 读取 SharedPreferences
        // 键值: NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY
        // 如果该键值不存在或为 false，则返回默认颜色
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // HACKME: 这里使用了 Math.random() 生成随机索引
            // 注意：如果数组长度变化，这里需要确保不会越界
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            return BG_DEFAULT_COLOR;
        }
    }

    
    /**
     * 便签列表项背景资源类
     * 
     * 作用：在便签列表页面（ListView/RecyclerView）中，根据便签在列表中的位置
     * （第一个、最后一个、中间、单独一个）显示不同的背景图（用于实现圆角拼接效果）。
     * 
     * 为什么分这么细？
     * 为了实现美观的列表视觉效果：
     * - 单独一行：四角都是圆角
     * - 第一行：顶部圆角，底部直角
     * - 中间行：上下都是直角
     * - 最后一行：顶部直角，底部圆角
     */
    public static class NoteItemBgResources {
        // 列表项 - 第一行的背景（通常只有底部是直角，顶部圆角）
        private final static int [] BG_FIRST_RESOURCES = new int [] {
            R.drawable.list_yellow_up,
            R.drawable.list_blue_up,
            R.drawable.list_white_up,
            R.drawable.list_green_up,
            R.drawable.list_red_up
        };

        // 列表项 - 中间的背景（上下都是直角，用于拼接）
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
            R.drawable.list_yellow_middle,
            R.drawable.list_blue_middle,
            R.drawable.list_white_middle,
            R.drawable.list_green_middle,
            R.drawable.list_red_middle
        };

        // 列表项 - 最后一行的背景（通常只有顶部是直角，底部圆角）
        private final static int [] BG_LAST_RESOURCES = new int [] {
            R.drawable.list_yellow_down,
            R.drawable.list_blue_down,
            R.drawable.list_white_down,
            R.drawable.list_green_down,
            R.drawable.list_red_down,
        };

        // 列表项 - 单独存在的背景（四角圆角）
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
            R.drawable.list_yellow_single,
            R.drawable.list_blue_single,
            R.drawable.list_white_single,
            R.drawable.list_green_single,
            R.drawable.list_red_single
        };

        // 提供公共的 Getter 方法供 ListView Adapter 调用
        public static int getNoteBgFirstRes(int id) { return BG_FIRST_RESOURCES[id]; }
        public static int getNoteBgLastRes(int id) { return BG_LAST_RESOURCES[id]; }
        public static int getNoteBgSingleRes(int id) { return BG_SINGLE_RESOURCES[id]; }
        public static int getNoteBgNormalRes(int id) { return BG_NORMAL_RESOURCES[id]; }

        // 文件夹的背景是固定的，不分颜色
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    
    /**
     * 桌面小部件 (Widget) 背景资源类
     * 
     * 作用：为 Android 桌面小部件提供不同尺寸（2x, 4x）和不同颜色的背景图。
     */
    public static class WidgetBgResources {
        // 2x2 小部件的背景资源数组
        private final static int [] BG_2X_RESOURCES = new int [] {
            R.drawable.widget_2x_yellow,
            R.drawable.widget_2x_blue,
            R.drawable.widget_2x_white,
            R.drawable.widget_2x_green,
            R.drawable.widget_2x_red,
        };

        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        // 4x 某尺寸 小部件的背景资源数组
        private final static int [] BG_4X_RESOURCES = new int [] {
            R.drawable.widget_4x_yellow,
            R.drawable.widget_4x_blue,
            R.drawable.widget_4x_white,
            R.drawable.widget_4x_green,
            R.drawable.widget_4x_red
        };

        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    
    /**
     * 文本外观 (样式) 资源类

     * 作用：将字体大小 ID 映射到具体的 Android Style 资源。
     */
    public static class TextAppearanceResources {
        // 定义一个数组，存储不同字号对应的 Style 资源 ID
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
            R.style.TextAppearanceNormal, // ID 0 -> 正常字号
            R.style.TextAppearanceMedium, // ID 1 -> 中等字号
            R.style.TextAppearanceLarge,  // ID 2 -> 大字号
            R.style.TextAppearanceSuper   // ID 3 -> 超大字号
        };

        /**
         * 获取文本外观资源 ID
         * 
         * @param id 字号 ID
         * @return 对应的 Style 资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 这是一个防御性编程的补丁 (Fix bug)。
             * 场景：用户在旧版本中设置了字号 ID=5（可能旧版本有5种字号）。
             *      现在新版本只有4种字号（数组长度为4）。
             *      如果直接返回 TEXTAPPEARANCE_RESOURCES[5]，程序会崩溃（ArrayIndexOutOfBoundsException）。
             * 逻辑：如果传入的 ID 大于等于数组长度，说明这个 ID 是无效的（过时的或错误的），
             *       此时强制返回默认的中等字号 (BG_DEFAULT_FONT_SIZE)，保证程序不崩溃。
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        // 返回可用的字号种类数量
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}