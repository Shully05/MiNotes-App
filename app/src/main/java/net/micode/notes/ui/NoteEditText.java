/* 
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 * 
 * Licensed under the Apache License, Version 2.0
 * 详情见：http://www.apache.org/licenses/LICENSE-2.0
 */

package net.micode.notes.ui;

import android.content.Context;           // 上下文环境
import android.graphics.Rect;            // 矩形区域（用于焦点变化）
import android.text.Layout;              // 文本布局（用于计算光标位置）
import android.text.Selection;           // 文本选区操作
import android.text.Spanned;             // 包含样式的文本（如超链接）
import android.text.TextUtils;           // 文本工具类（判空等）
import android.text.style.URLSpan;       // URL 样式跨度（用于处理链接点击）
import android.util.AttributeSet;        // XML 属性集
import android.util.Log;                 // 日志工具
import android.view.ContextMenu;         // 上下文菜单（长按菜单）
import android.view.KeyEvent;            // 按键事件
import android.view.MenuItem;            // 菜单项
import android.view.MenuItem.OnMenuItemClickListener; // 菜单项点击监听
import android.view.MotionEvent;         // 触摸事件
import android.widget.EditText;          // 基础编辑文本控件

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 EditText
 * 
 * 用途：
 * 在 MiCode Notes 中，便签内容由多个独立的 EditText 块组成。
 * 这个类负责处理文本编辑、链接识别，以及最重要的——
 * 向外部容器（NoteEditActivity）报告“删除当前块”或“插入新块”的请求。
 * 
 * 关键机制：
 * 通过接口 OnTextViewChangeListener 与 Activity 通信，实现视图与逻辑的解耦。
 */
public class NoteEditText extends EditText {
    
    // 日志标签，用于调试
    private static final String TAG = "NoteEditText";
    
    // 当前 EditText 块在列表中的索引位置
    // 用于告诉 Activity "我是第几个块"，以便进行增删改查
    private int mIndex;
    
    // 记录删除键按下时的光标位置
    // 用于判断删除操作是否发生在行首（这是合并块的关键逻辑）
    private int mSelectionStartBeforeDelete;

    // 定义支持的 URL 协议常量
// CHEME_TEL	"tel:"	电话协议	点击后会打开拨号盘，并填入号码。
// SCHEME_HTTP	"http:"	网页协议	点击后会打开浏览器，访问网址。
// SCHEME_EMAIL	"mailto:"	邮件协议	点击后会打开邮件 App，并填入收件人
    private static final String SCHEME_TEL = "tel:";
    private static final String SCHEME_HTTP = "http:";
    private static final String SCHEME_EMAIL = "mailto:";

    // 映射表：将 URL 协议映射到对应的字符串资源 ID
    // 用于在长按时生成更友好的菜单项（如“拨打号码”、“发送邮件”）
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();
    
    // 静态代码块：初始化映射表
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 通信接口：回调接口
     * NoteEditText 本身不知道如何删除自己或创建新块（它只负责显示），
     * 所以它通过这个接口告诉 Activity："用户按了回车/删除，请处理逻辑"。
     */
    public interface OnTextViewChangeListener {
        
        /**
         * 回调方法：请求删除当前块
         */
        void onEditTextDelete(int index, String text);

        /**
         * 回调方法：请求插入新块
         * 当用户在当前块按下回车键时触发。
         */
        void onEditTextEnter(int index, String text);

        /**
         * 回调方法：文本状态变化

         */
        void onTextChange(int index, boolean hasText);
    }

    // 接口实例，由外部 Activity 设置
    private OnTextViewChangeListener mOnTextViewChangeListener;

    // 构造函数 1：代码中直接 new 时调用
    public NoteEditText(Context context) {
        // 调用父类构造，并传入 null 属性
        super(context, null);
        mIndex = 0; // 初始化索引
    }

    // 提供方法供外部设置当前块的索引
    public void setIndex(int index) {
        mIndex = index;
    }

    // 提供方法供外部设置监听器（建立通信桥梁）
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    // 构造函数 2：布局文件中声明时调用（带自定义属性）
    public NoteEditText(Context context, AttributeSet attrs) {
        // 使用系统默认的 EditText 样式
        super(context, attrs, android.R.attr.editTextStyle);
    }

    // 构造函数 3：带自定义样式的构造函数
    // 通常用于更复杂的自定义主题
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        // TODO Auto-generated constructor stub
    }

    /**
     * 触摸事件处理
     * 重写此方法是为了修正光标定位逻辑（可能针对特定版本的兼容性修复）。
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 1. 获取触摸点坐标
                int x = (int) event.getX();
                int y = (int) event.getY();
                
                // 2. 调整坐标
                // 减去内边距：将坐标系原点移到文本显示区域的左上角
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();
                // 加上滚动偏移：处理 ScrollView 滚动后坐标的变化
                x += getScrollX();
                y += getScrollY();

                // 3. 获取布局对象（负责文本排版）
                Layout layout = getLayout();
                // 计算触摸点所在的行
                int line = layout.getLineForVertical(y);
                // 计算触摸点在该行的字符偏移量（即光标应插入的位置）
                int off = layout.getOffsetForHorizontal(line, x);
                
                // 4. 设置光标位置
                Selection.setSelection(getText(), off);
                break;
        }
        // 继续执行父类的触摸逻辑（如绘制光标、输入文字）
        return super.onTouchEvent(event);
    }

    /**
     * 按键按下事件（预处理）
     * 在 onKeyUp 之前调用，主要用于记录状态。
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: // 回车键
                // 如果设置了监听器，这里不做拦截，让 onKeyUp 处理
                // 如果没设置监听器，返回 false 让系统处理默认换行
                if (mOnTextViewChangeListener != null) {
                    return false;
                }
                break;
                
            case KeyEvent.KEYCODE_DEL: // 删除键
                // 关键逻辑：记录按下删除键时的光标位置
                // 这是为了在 onKeyUp 时判断光标是否在行首
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
                
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键抬起事件（核心逻辑）
     * 这里处理了便签块的“分裂”和“合并”逻辑。
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            
            // --- 场景 A：处理删除键 (合并块逻辑) ---
            case KeyEvent.KEYCODE_DEL:
                // 1. 检查是否有监听器（防止空指针）
                if (mOnTextViewChangeListener != null) {
                    // 2. 核心判断逻辑：
                    // (0 == mSelectionStartBeforeDelete) -> 光标在行首
                    // (mIndex != 0) -> 不是第一个块（防止误删整个笔记）
                    // 满足这两个条件，说明用户想删除当前这个空块，并把内容接回上一个块
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        // 3. 触发回调：请求删除当前块
                        // 传入当前索引和当前文本，让 Activity 处理合并逻辑
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true; // 消费该事件，不再传递
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;

            // --- 场景 B：处理回车键 (分裂块逻辑) ---
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 1. 获取当前光标位置
                    int selectionStart = getSelectionStart();
                    
                    // 2. 截取文本：
                    // 将当前块光标之后的文本（回车后的内容）提取出来
                    // 这部分文本将被放入新的块中
                    String text = getText().subSequence(selectionStart, length()).toString();
                    
                    // 3. 修正当前块文本：
                    // 将当前块光标之后的文本截断（模拟系统回车的前半部分效果）
                    setText(getText().subSequence(0, selectionStart));
                    
                    // 4. 触发回调：请求插入新块
                    // 传入建议的索引 (mIndex + 1) 和需要移动的文本
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
                
            default:
                break;
        }
        // 默认情况下，让系统处理按键（如输入字符）
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化监听
     * 用于检测文本框是否为空，以便隐藏不必要的操作按钮。
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            // 逻辑：如果失去焦点 (focused=false) 且 文本为空
            // 则通知外部隐藏该块的操作选项
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                // 否则，显示操作选项
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 上下文菜单（长按菜单）创建
     * 当用户长按选中的文字时，如果文字包含链接，会显示自定义菜单。
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        // 1. 检查文本是否为 Spanned（即是否包含样式信息，如链接）
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            // 2. 确定选中区域的范围
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 3. 查找选中区域内的 URLSpan
            // URLSpan 是 Android 中用于标记链接的 Span
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            
            // 4. 如果恰好选中了一个链接
            if (urls.length == 1) {
                int defaultResId = 0;
                
                // 5. 遍历协议映射表，识别链接类型
                for(String schema : sSchemaActionResMap.keySet()) {
                    // 检查 URL 是否包含特定协议头 (tel:, http:, mailto:)
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        // 找到匹配的协议，获取对应的字符串资源 ID
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 6. 如果没有匹配到特定协议，显示通用菜单项
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 7. 创建菜单项
                menu.add(0, 0, 0, defaultResId)
                    .setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 8. 菜单点击处理
                                // 调用 URLSpan 的 onClick 方法
                                // 这通常会启动一个新的 Intent (例如跳转浏览器或拨号盘)
                                urls[0].onClick(NoteEditText.this);
                                return true; // 消费点击事件
                            }
                        });
            }
        }
        // 调用父类方法，确保其他默认菜单项（如复制、粘贴）也能正常显示
        super.onCreateContextMenu(menu);
    }
}