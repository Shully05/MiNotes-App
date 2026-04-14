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

package net.micode.notes.ui; // 定义当前文件所在的包路径，属于项目的UI层

import android.app.Activity; // 基础 Activity 类，所有界面的基类
import android.app.AlarmManager; // 系统闹钟服务，用于设定定时任务（如便签提醒）
import android.app.AlertDialog; // 弹窗对话框，用于显示提示信息或确认框
import android.app.PendingIntent; // 延迟意图，通常配合 AlarmManager 或通知栏使用，用于在未来某个时间点触发动作
import android.app.SearchManager; // 系统搜索管理服务，用于处理全局搜索功能
import android.appwidget.AppWidgetManager; // 桌面小部件管理器，用于更新和管理桌面便签挂件
import android.content.ContentUris;// 内容 URI 工具类，用于构建访问数据库的 URI（如便签的 ID）
import android.content.Context; // 上下文环境，获取系统资源和服务的基础
import android.content.DialogInterface; // 对话框接口，用于处理对话框按钮点击事件
import android.content.Intent; // 意图，用于在组件（Activity, Service, Receiver）之间传递消息和跳转
import android.content.SharedPreferences; // 轻量级数据存储，用于保存应用的配置信息（如背景颜色、字号偏好）
import android.graphics.Paint; // 画笔类，用于处理图形绘制和文本样式（如删除线）
import android.os.Bundle; // 数据包，用于在 Activity 之间传递数据（如保存和恢复状态）
import android.preference.PreferenceManager; // 偏好设置管理器，用于获取默认的 SharedPreferences
import android.text.Spannable; // 可变文本接口，允许对文本的一部分应用样式
import android.text.SpannableString; // 可变字符串，用于实现文本高亮或特殊样式
import android.text.TextUtils; // 文本工具类，提供 isEmpty 等常用字符串判断方法
import android.text.format.DateUtils; // 日期格式化工具，用于将时间戳转换为友好的日期字符串
import android.text.style.BackgroundColorSpan; // 背景色样式跨度，用于给文本添加背景高亮
import android.util.Log; // 日志工具，用于在 Logcat 中打印调试信息
import android.view.LayoutInflater; // 布局加载器，用于将 XML 布局文件转换为 View 对象
import android.view.Menu; // 选项菜单接口，用于创建应用顶部的菜单栏
import android.view.MenuItem; // 菜单项接口，代表菜单中的具体选项
import android.view.MotionEvent; // 触摸事件类，用于处理屏幕触摸操作
import android.view.View; // 基础视图类，所有 UI 控件的基类
import android.view.View.OnClickListener; // 点击监听接口，用于处理控件点击事件
import android.view.WindowManager; // 窗口管理器，用于控制窗口的属性（如软键盘的显示/隐藏）
import android.widget.CheckBox; // 复选框控件，用于便签中的待办事项列表
import android.widget.CompoundButton; // 复合按钮基类
import android.widget.CompoundButton.OnCheckedChangeListener; // 状态改变监听接口，用于监听 CheckBox 的勾选状态变化
import android.widget.EditText; // 可编辑文本框，用户输入便签内容的核心控件
import android.widget.ImageView; // 图片控件，用于显示图标（如闹钟图标、背景图）
import android.widget.LinearLayout; // 线性布局容器，用于排列界面元素
import android.widget.TextView; // 文本显示控件，用于显示标题、时间等信息
import android.widget.Toast; // 吐司提示，用于显示短暂的轻量级消息提示

/* =========================================
   2. 项目内部模块引用 (net.micode.notes)
   ========================================= */

// --- 资源文件 ---
import net.micode.notes.R; // 自动生成的资源索引类，包含所有布局、图片、字符串资源的 ID

// --- 数据层 (Data) ---
import net.micode.notes.data.Notes; // 便签数据库的契约类，定义了表名、列名和常量
import net.micode.notes.data.Notes.TextNote; // 文本便签的具体数据结构定义
import net.micode.notes.model.WorkingNote; // 工作便签模型，封装了便签的编辑状态和数据库操作逻辑
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener; // 便签设置改变监听器（自定义接口）
import net.micode.notes.tool.DataUtils; // 数据工具类，封装了数据库查询、删除等常用操作
import net.micode.notes.tool.ResourceParser; // 资源解析工具类，用于根据 ID 获取颜色、字号等配置
import net.micode.notes.tool.ResourceParser.TextAppearanceResources; // 文本外观资源解析内部类

// --- 界面层 (UI) ---
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener; // 日期时间选择器对话框的回调接口
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener; // 自定义 EditText 的文本变化监听接口

// --- 桌面挂件 (Widget) ---
import net.micode.notes.widget.NoteWidgetProvider_2x; // 2x2 尺寸桌面便签挂件的更新提供者
import net.micode.notes.widget.NoteWidgetProvider_4x; // 4x4 尺寸桌面便签挂件的更新提供者

/* =========================================
   3. Java 标准库
   ========================================= */
import java.util.HashMap; // 哈希映射，用于存储键值对（如背景颜色 ID 映射到具体颜色值）
import java.util.HashSet; // 哈希集合，用于存储不重复的元素（如需要刷新的挂件 ID 列表）
import java.util.Map; // 映射接口，HashMap 的父接口
import java.util.regex.Matcher; // 正则表达式匹配器，用于查找文本中的模式（如高亮搜索关键词）
import java.util.regex.Pattern; // 正则表达式编译类，用于定义匹配规则

    //  - OnClickListener: 处理按钮点击（颜色、字号）。
    //  - NoteSettingChangedListener: 监听便签设置变化（如闹钟变化时的回调）。
    //  - OnTextViewChangeListener: 监听编辑框文本变化
public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    /* 
       内部类: HeadViewHolder头部视图持有者
       作用: ViewHolder 模式，缓存标题栏控件引用，优化 ListView/复杂布局性能。
    */
    private class HeadViewHolder {
        public TextView tvModified;     // 显示“最后修改时间”
        public ImageView ivAlertIcon;   // 显示闹钟图标
        public TextView tvAlertDate;    // 显示具体的闹钟倒计时/时间
        public ImageView ibSetBgColor;  // 背景颜色设置按钮
    }
    /* 
       静态映射表 (HashMap)
       作用: 将 UI 按钮 ID 映射到具体的业务逻辑值。
       1. sBgSelectorBtnsMap: 按钮 ID -> 颜色 ID (如 YELLOW, RED)
       2. sBgSelectorSelectionMap: 颜色 ID -> 选中状态图标 ID
       3. sFontSizeBtnsMap / sFontSelectorSelectionMap: 按钮 ID -> 字号 ID (如 LARGE, SMALL)
       目的: 通过查表法简化代码，避免冗长的 if-else 判断。
    */
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);
    }

    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);
    }

    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);
    }

    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);
    }

    private static final String TAG = "NoteEditActivity";

    /* 
       成员变量: UI 控件引用
    */
    private HeadViewHolder mNoteHeaderHolder; // 标题栏持有者
    private View mHeadViewPanel;              // 标题栏布局
    private View mNoteBgColorSelector;        // 背景色选择面板 (下拉菜单)
    private View mFontSizeSelector;           // 字号选择面板 (下拉菜单)
    private EditText mNoteEditor;             // 主文本编辑框
    private View mNoteEditorPanel;            // 编辑框背景面板
    /* 
       成员变量: 核心业务逻辑对象
       WorkingNote: 便签工作对象。它封装了便签的数据（内容、背景、闹钟时间）以及
                    与数据库交互的保存(save)、删除(delete)逻辑。
    */
    private WorkingNote mWorkingNote;
    /* 
       成员变量: 用户偏好设置
       用于读取和保存用户上次使用的字号等设置。
    */
    private SharedPreferences mSharedPrefs;
    private int mFontSizeId;

    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";

    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;

    /* 
       常量定义
       TAG: 日志标签。
       TAG_CHECKED / TAG_UNCHECKED: 待办清单 (CheckList) 的特殊字符标记。
    */
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');
    /* 
       其他变量
       mEditTextList: 用于存放待办清单 (CheckList) 模式的动态 View 列表。
       mUserQuery: 搜索关键词，用于高亮显示。
    */
    private LinearLayout mEditTextList;

    private String mUserQuery;
    private Pattern mPattern;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(android.R.style.Theme_Holo_Light_DarkActionBar);// 设置主题，使用带有暗色 ActionBar 的 Holo Light 主题，显示菜单键
        super.onCreate(savedInstanceState);
    /* 
       设置界面布局: 加载 res/layout/note_edit.xml
       这是一个包含标题栏和编辑框的复杂布局。
    */
        this.setContentView(R.layout.note_edit);

        // 强制显示溢出菜单（那三个点）
        getWindow().getDecorView().setSystemUiVisibility(0);

    /* 
       状态恢复逻辑:
       如果 savedInstanceState 不为空，说明 Activity 可能因内存不足被杀后重启。
       尝试从 Bundle 中恢复便签 ID 并重新加载数据。
       initActivityState: 见下方详细解释。
    */
        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();
            return;
        }
        initResources();
    }

    /**
     * 核心方法：恢复实例状态
     * 
     * 场景说明：
     * 1. 当系统内存不足时，Android 系统可能会杀死后台的 Activity（例如用户按 Home 键后，应用在后台运行了很久）。
     * 2. 当用户再次从任务列表（最近任务）回到这个 Activity 时，系统不会调用 onCreate，而是调用 onRestoreInstanceState。
     * 3. 我们需要利用这个机会，把之前保存在 Bundle 里的 Note ID 取出来，重新加载数据，让用户感觉不到 Activity 曾被杀死过。
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        //把恢复界面控件状态的任务交给系统去做
        super.onRestoreInstanceState(savedInstanceState);
        
        // 1. 安全检查：确保 Bundle 不为空，且里面包含我们保存的 Note ID (Intent.EXTRA_UID)
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            
            // 2. 重建 Intent：模拟一个“查看便签”的动作
            Intent intent = new Intent(Intent.ACTION_VIEW);
            
            // 3. 从 Bundle 中取出之前保存的 Note ID
            // 这个 ID 是在 onSaveInstanceState 中通过 outState.putLong(...) 保存的
            //把刚才从‘存档’里读出来的便签ID，重新塞进一张新的‘快递单’（Intent）里
            //intent.putExtra(...)填单
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            
            // 4. 重新初始化状态：根据取出的 ID 去数据库加载便签内容
            if (!initActivityState(intent)) {
                // 如果初始化失败（比如 ID 对应的便签已经被删除了），则直接关闭当前页面，防止崩溃或显示空壳
                finish();
                return;
            }
            
            // 5. 打印日志，标记这是一次“死而复生”的恢复过程
            Log.d(TAG, "Restoring from killed activity");
        }
    }
// 核心逻辑方法: initActivityState (页面入口分流)
    private boolean initActivityState(Intent intent) {

        mWorkingNote = null;

        // 判断用户的意图是否是“查看”某条便签（通常是从桌面快捷方式、通知栏或搜索点击进来的）
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            
            // 1. 尝试获取便签ID
            // 默认从 EXTRA_UID 中获取，如果没有则默认为 0
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = ""; // 初始化用户搜索词为空

            /**
             * 特殊情况处理：如果是从“搜索结果”点击进来的
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                // 搜索结果的 ID 通常存在 EXTRA_DATA_KEY 里，且是字符串格式，需要解析
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                // 同时取出用户的搜索关键词，可能用于后续的高亮显示
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 2. 安全检查：去数据库查询这个 ID 是否存在且可见
            // 防止用户点击了过期的快捷方式或通知，导致打开不存在的便签
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                // 如果便签不存在（可能被删除了）：
                
                // A. 准备跳转回列表页
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                
                // B. 提示用户“便签不存在”
                showToast(R.string.error_note_not_exist);
                
                // C. 结束当前页面，防止打开一个空壳
                finish();
                return false;
            } else {
                // 3. 加载数据：如果便签存在，则从数据库加载具体内容
                mWorkingNote = WorkingNote.load(this, noteId);
                
                // 双重保险：如果加载出来的对象是空的，说明出了严重错误
                if (mWorkingNote == null) {
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish(); // 关闭页面
                    return false;
                }
            }
            
            // 4. 窗口设置：设置软键盘模式
            // STATE_HIDDEN: 页面打开时默认隐藏键盘（因为是查看模式，不是新建模式）
            // ADJUST_RESIZE: 当键盘弹出时，调整界面大小，防止输入框被遮挡
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
            /* 
       分支 2: Intent.ACTION_INSERT_OR_EDIT (创建新便签)
       流程:
         1. 获取文件夹 ID、小组件 ID 等参数。
         2. 特殊处理通话记录: 检查 Intent 中是否有电话号码和时间。
            - 如果有: 尝试查找已有的通话记录便签，没有则创建新的通话便签 (convertToCallNote)。
         3. 普通处理: 调用 WorkingNote.createEmptyNote() 创建一个全新的空便签对象。
    */
        // 如果不是查看模式，而是“新建或编辑”模式（通常是点击“新建便签”按钮，或从桌面挂件进入）
        else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            
            // --- 1. 获取基础参数 ---
            // 获取文件夹ID（如果是从某个文件夹里点击新建，需要知道归属）
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            
            // 获取挂件信息（如果是桌面挂件点击进来的，需要记录是哪个挂件，以便后续更新桌面）
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            
            // 获取背景样式ID（如果没有指定，就用系统默认背景）
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // --- 2. 特殊处理：通话记录便签 ---
            // 尝试获取电话号码和通话时间。如果这两个都有值，说明是“通话记录”场景
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);
            
            // 如果既有号码又有时间，说明这是一条“通话便签”
            if (callDate != 0 && phoneNumber != null) {
                // 安全检查：虽然上面判断了不为空，但这里还是防御性地检查一下号码是否为空字符串
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                
                long noteId = 0;
                // 核心逻辑：去数据库查一下，这个电话号码在这个时间点，是否已经存在便签了？
                // 目的是防止重复创建。比如你挂了电话点一次“记录”，系统已经建了一条，你再点就不应该新建，而是编辑旧的。
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    
                    // 情况A：便签已存在 -> 加载它（编辑模式）防御性编程
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    // 情况B：便签不存在 -> 创建一个新的空便签，并把它标记为“通话便签”
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    // 这个方法会把电话号码和时间写入便签的头部信息
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // --- 3. 普通处理：新建普通便签 ---
                // 如果没有通话信息，那就是最普通的“新建便签”
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            // --- 4. 窗口设置 ---
            // 新建便签时，用户肯定是想打字的，所以：
            // STATE_VISIBLE: 强制键盘自动弹出来，方便用户直接输入
            // ADJUST_RESIZE: 键盘弹出时，调整界面大小
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }else {
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * 生命周期回调：当 Activity 处于“前台运行”状态时调用。
     * 每次用户回到这个编辑页面时，都会执行此方法。
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 重新初始化屏幕显示，确保数据和界面是最新的
        initNoteScreen();
    }

    /**
     * 核心方法：初始化便签编辑界面的 UI
     * 职责：将 WorkingNote 中的数据（背景、内容、时间等）加载到对应的控件上
     */
    private void initNoteScreen() {
        // 1. 设置字体样式（大小、颜色）
        // 从资源映射中获取对应的样式ID，并应用到编辑框 mNoteEditor
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));

        // 2. 处理内容显示模式（普通文本 vs 待办清单）
        // 判断当前便签是否为“待办清单模式”
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 如果是清单模式，切换到带复选框的列表视图
            switchToListMode(mWorkingNote.getContent());
        } else {
            // 如果是普通文本模式
            // 获取内容，如果有搜索关键词(mUserQuery)，则高亮显示
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            // 将光标移动到文本的最末尾
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }

        // 3. 隐藏所有背景选择器按钮
        // 遍历背景选择器映射表，将所有颜色的选中状态图标设为 GONE (隐藏)
        // 目的是先清空状态，后面再根据当前便签的实际颜色显示对应的选中图标
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }

        // 4. 设置背景资源
        // 设置标题栏的背景（对应不同颜色的头部）
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        // 设置编辑区域的背景（对应不同颜色的便签纸）
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 5. 更新“最后修改时间”
        // 使用系统 DateUtils 格式化时间戳，显示格式如：2023年10月27日 10:30
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        // 6. 刷新闹钟头部显示
        // 检查是否有闹钟设定，如果有，显示闹钟图标和时间；如果没有，隐藏它们
        showAlertHeader();
    }

    /**
     * 核心方法：更新标题栏中“闹钟提醒”区域的显示状态
     * 逻辑：
     * 1. 检查当前便签是否设置了闹钟。
     * 2. 如果设置了：判断时间是“未来”还是“过去”，并显示相应的提示文本。
     * 3. 如果未设置：隐藏闹钟图标和时间文本。
     */
    private void showAlertHeader() {
        // 1. 判断当前便签对象是否包含闹钟设置
        if (mWorkingNote.hasClockAlert()) {
            
            // 获取当前系统时间
            long time = System.currentTimeMillis();
            
            // 2. 比较当前时间与设定的闹钟时间
            if (time > mWorkingNote.getAlertDate()) {
                // 情况A：当前时间 晚于 闹钟时间 -> 说明闹钟已经过期（响了但没处理，或者错过了）
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired); // 显示“已过期”
            } else {
                // 情况B：当前时间 早于 闹钟时间 -> 说明是未来的提醒
                // 使用系统工具类生成人性化的相对时间字符串（例如：“10分钟后”、“明天”）
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), // 目标时间（闹钟时间）
                        time,                      // 当前时间
                        DateUtils.MINUTE_IN_MILLIS // 最小单位：分钟
                ));
            }
            
            // 3. 显示闹钟相关的控件（图标和文字）
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
            
        } else {
            // 4. 如果没有设置闹钟，则隐藏闹钟相关的控件
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    /**
     * 核心方法：处理新的 Intent 启动
     * 场景：
     * 当 Activity 的启动模式是 singleTop，或者 Activity 已经在栈顶时，再次启动该 Activity
     * 不会创建新实例，而是调用此方法。
     * 例如：从通知栏点击同一个便签的提醒，可能会触发此方法。
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 重新初始化 Activity 的状态（判断是新建还是查看，加载对应的数据）
        // 这确保了页面内容会根据新的 Intent 进行刷新
        initActivityState(intent);
    }

    /**
     * 核心方法：保存实例状态
     * 场景：
     * 当系统因为内存不足即将杀死 Activity（例如用户按 Home 键，或者屏幕旋转导致重建）时调用。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        /**
         * 逻辑说明：
         * 对于还没有 ID 的新便签（即用户刚输入内容还没保存），必须先强制保存一次。
         * 为什么要这么做？
         * 因为如果便签没有 ID，系统无法在恢复时找到它。
         * 如果内容不值得保存（空内容），saveNote() 可能会失败或返回 false，
         * 此时没有 ID，相当于还是新建状态。
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote(); // 强制保存，确保生成一个唯一的 NoteId
        }
        
        // 将生成的 NoteId 存入 Bundle 中
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        
        // 打印日志，方便调试追踪 ID 的保存情况
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 核心方法：触摸事件分发
     * 场景：
     * 拦截屏幕上的所有触摸事件。
     * 主要用途：实现“点击空白处收起下拉菜单”的功能。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 1. 处理背景颜色选择面板的收起
        // 如果颜色选择面板是可见的，并且点击的位置不在该面板范围内
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE); // 隐藏面板
            return true; // 消费掉这个事件，不再向下传递（防止误触其他控件）
        }

        // 2. 处理字号选择面板的收起
        // 如果字号选择面板是可见的，并且点击的位置不在该面板范围内
        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE); // 隐藏面板
            return true; // 消费掉这个事件
        }
        
        // 3. 其他情况
        // 如果不是上述情况，交给父类（Activity）处理正常的触摸事件（如点击按钮、输入文字）
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 辅助方法：判断触摸事件是否发生在指定 View 的范围内
     * 用途：主要用于 dispatchTouchEvent 中，判断用户是否点击了“空白处”来收起菜单。
     * 
     * @param view 目标视图（如背景色选择面板）
     * @param ev 触摸事件
     * @return true: 点击在 View 内部; false: 点击在 View 外部
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        // 获取 View 在屏幕上的绝对坐标 (x, y)
        int []location = new int[2];
        view.getLocationOnScreen(location);
        int x = location[0]; // 左上角 X 坐标
        int y = location[1]; // 左上角 Y 坐标
        
        // 判断触摸点 (ev.getX(), ev.getY()) 是否超出了 View 的矩形区域
        // 注意：这里使用的是屏幕坐标，因为 getLocationOnScreen 返回的是屏幕坐标
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
                    return false; // 超出范围，返回 false
                }
        return true; // 在范围内
    }

    /**
     * 核心方法：初始化界面资源与绑定事件
     * 职责：
     * 1. 查找控件 (findViewById)
     * 2. 设置监听器 (setOnClickListener)
     * 3. 读取用户偏好设置 (SharedPreferences)
     */
    private void initResources() {
        // --- 1. 初始化标题栏区域 ---
        mHeadViewPanel = findViewById(R.id.note_title); // 标题栏根布局
        mNoteHeaderHolder = new HeadViewHolder();       // 实例化 ViewHolder
        
        // 绑定标题栏子控件
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        
        // 设置“背景颜色”按钮的点击监听
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);

        // --- 2. 初始化编辑区域 ---
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view); // 核心编辑框
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);         // 编辑框背景面板

        // --- 3. 初始化背景色选择器 ---
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector); // 背景色下拉面板
        // 遍历背景色映射表，为每个颜色按钮绑定点击事件
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);
        }

        // --- 4. 初始化字号选择器 ---
        mFontSizeSelector = findViewById(R.id.font_size_selector); // 字号下拉面板
        // 遍历字号映射表，为每个字号按钮绑定点击事件
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);
        }

        // --- 5. 读取用户偏好设置 ---
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        // 获取用户上次选择的字号，如果没有则使用默认值
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);
        
        /**
         * HACKME: 修复 SharedPreferences 存储资源 ID 的潜在 Bug。
         * 如果资源文件更新导致 ID 变化，旧的 ID 可能超出当前资源数组的范围。
         * 此时强制重置为默认字号，防止数组越界异常。
         */
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }
        
        // --- 6. 初始化待办清单容器 ---
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list); // 用于存放 CheckList 模式的条目
    }

    /**
     * 生命周期回调：页面暂停
     * 场景：用户按 Home 键、跳转到其他 Activity、或来电时调用。
     * 核心任务：保存数据，防止内容丢失。
     */
    @Override
    protected void onPause() {
        super.onPause();
        
        // 保存便签内容到数据库
        // 如果保存成功，打印日志记录内容长度
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        
        // 清理界面状态（如隐藏弹出的颜色/字号选择器），确保下次打开时是干净的状态
        clearSettingState();
    }

    /**
     * 核心方法：更新桌面小部件 (Widget)
     * 场景：当便签内容修改后，需要同步更新桌面上对应的 Widget 显示。
     */
    private void updateWidget() {
        // 创建更新广播的 Intent
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        
        // 根据当前便签关联的 Widget 类型（2x2 或 4x4），指定广播接收者
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type"); // 未知类型，记录错误日志
            return;
        }

        // 放入需要更新的 Widget ID
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            mWorkingNote.getWidgetId()
        });

        // 发送广播：系统收到后会触发对应 WidgetProvider 的 onUpdate 方法，刷新桌面控件
        sendBroadcast(intent);
        
        // 设置 Activity 的结果（通常用于通知调用者操作成功）
        setResult(RESULT_OK, intent);
    }

    /**
     * 统一的点击事件监听器 (implements OnClickListener)
     * 负责处理：背景色按钮、颜色选择器、字号选择器的点击逻辑。
     */
    public void onClick(View v) {
        int id = v.getId();
        
        // 1. 点击“设置背景色”按钮
        if (id == R.id.btn_set_bg_color) {
            // 显示背景色选择面板
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            // 高亮显示当前便签已选中的颜色（通过显示一个“对勾”图标）
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        } 
        // 2. 点击了某个具体的颜色
        else if (sBgSelectorBtnsMap.containsKey(id)) {
            // 先隐藏当前颜色的“对勾”图标
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.GONE);
            // 更新 WorkingNote 对象中的背景色ID
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            // 隐藏颜色选择面板
            mNoteBgColorSelector.setVisibility(View.GONE);
            // 注意：实际的背景更新是在 onBackgroundColorChanged() 回调中完成的
        } 
        // 3. 点击了某个具体的字号
        else if (sFontSizeBtnsMap.containsKey(id)) {
            // 先隐藏当前字号的“对勾”图标
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            // 更新当前字号ID
            mFontSizeId = sFontSizeBtnsMap.get(id);
            // 将用户的选择保存到 SharedPreferences，作为默认字号
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            // 高亮显示新选中的字号
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
            
            // 根据当前模式应用新字号
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                // 如果是待办清单模式，需要重新获取文本并重绘整个列表
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                // 如果是普通文本模式，直接改变 EditText 的文本样式
                mNoteEditor.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            // 隐藏字号选择面板
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 拦截系统返回键
     * 核心逻辑：优先处理UI状态，再保存数据，最后退出
     */
    @Override
    public void onBackPressed() {
        // 1. 优先处理：如果颜色或字号选择面板是打开的，点击返回键应该先关闭它们，而不是退出页面
        if(clearSettingState()) {
            return; // 如果关闭了面板，就直接返回，不执行后续操作
        }

        // 2. 保存数据：如果面板都已关闭，则保存当前便签内容
        saveNote();
        
        // 3. 执行默认的返回操作（关闭当前Activity）
        super.onBackPressed();
    }
    /**
     * 清理界面状态：关闭所有打开的选择面板（颜色、字号）
     * @return true: 如果有面板被关闭; false: 如果所有面板都已经是关闭状态
     */
    private boolean clearSettingState() {
        // 如果背景色选择面板是可见的，则关闭它
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } 
        // 如果字号选择面板是可见的，则关闭它
        else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        // 所有面板都已是关闭状态
        return false;
    }
    /**
     * 监听器回调：当便签的背景颜色数据改变时调用
     * 职责：同步更新UI，显示新的背景和选中状态
     */
    public void onBackgroundColorChanged() {
        // 高亮显示新选中的背景色（显示“对勾”图标）
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(View.VISIBLE);
        // 更新编辑区域的背景图
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        // 更新标题栏的背景图（标题栏背景也会随之改变）
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 准备选项菜单：在菜单显示前动态调整菜单项
     * 核心逻辑：根据便签类型、模式、闹钟状态来决定显示哪些菜单
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 如果 Activity 正在结束，则不处理菜单
        if (isFinishing()) {
            return true;
        }
        
        // 1. 清理状态：打开菜单前，先关闭颜色/字号选择面板
        clearSettingState();
        
        // 2. 清空旧菜单，根据便签类型加载新菜单
        menu.clear();
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 如果是通话记录便签，加载专用的菜单
            getMenuInflater().inflate(R.menu.call_note_edit, menu);
        } else {
            // 否则加载普通便签菜单
            getMenuInflater().inflate(R.menu.note_edit, menu);
        }
        
        // 3. 动态修改“清单模式”菜单项的文字
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode); // 当前是清单模式，显示“切换到普通模式”
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode); // 当前是普通模式，显示“切换到清单模式”
        }
        
        // 4. 根据闹钟状态，互斥显示“设置提醒”和“删除提醒”
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false); // 已有闹钟，隐藏“设置提醒”
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false); // 没有闹钟，隐藏“删除提醒”
        }
        
        return true;
    }

    /**
     * 处理菜单项的点击事件
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_note: // 新建便签
                createNewNote();
                break;
                
            case R.id.menu_delete: // 用户点击了菜单栏里的“删除”按钮
                // 1. 建造者模式：创建一个对话框的“构建器”
                AlertDialog.Builder builder = new AlertDialog.Builder(this);   
                // 2. 设置对话框的标题（例如：“删除便签”）
                builder.setTitle(getString(R.string.alert_title_delete));            
                // 3. 设置对话框的图标（使用系统自带的警告图标 ⚠️）
                builder.setIcon(android.R.drawable.ic_dialog_alert);             
                // 4. 设置提示语（例如：“确定要删除这条便签吗？”）
                builder.setMessage(getString(R.string.alert_message_delete_note));             
                // 5. 设置“确定”按钮
                // android.R.string.ok 是系统自带的“确定”文字
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // --- 用户点了“确定”，这里执行真正的删除逻辑 ---
                        deleteCurrentNote(); // 调用方法，从数据库删除这条便签
                        finish();            // 删除成功后，关闭当前的编辑页面，返回列表
                    }
                });
                
                // 6. 设置“取消”按钮
                // android.R.string.cancel 是系统自带的“取消”文字
                // 第二个参数传 null，表示点击后直接关闭对话框，不执行任何操作
                builder.setNegativeButton(android.R.string.cancel, null);
                
                // 7. 最后一步：把对话框显示出来！
                builder.show();
                break;
                
            case R.id.menu_font_size: // 设置字号
                // 显示字号选择面板，并高亮当前字号
                mFontSizeSelector.setVisibility(View.VISIBLE);
                findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
                break;
                
            case R.id.menu_list_mode: // 切换清单模式
                // 在“清单模式”和“普通模式”之间切换
                mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ? TextNote.MODE_CHECK_LIST : 0);
                break;
                
            case R.id.menu_share: // 分享便签
                getWorkingText(); // 先获取最新的文本内容
                sendTo(this, mWorkingNote.getContent()); // 调用系统分享功能
                break;
                
            case R.id.menu_send_to_desktop: // 发送到桌面（创建快捷方式）
                sendToDesktop();
                break;
                
            case R.id.menu_alert: // 设置闹钟
                setReminder(); // 打开时间选择器
                break;
                
            case R.id.menu_delete_remind: // 删除闹钟
                mWorkingNote.setAlertDate(0, false); // 将闹钟时间设为0，取消提醒
                break;

            case R.id.menu_copy:    //一键复制
                copyNoteContent();  // 调用新方法，复制内容到剪贴板
                break;

            case R.id.menu_clear:
                // 核心逻辑：直接把编辑框的内容设为空字符串
                mNoteEditor.getText().clear();
                break;
            default:
                break;
        }
        return true;
    }

        /**
         * 新增的方法：复制内容到剪贴板
         */
        private void copyNoteContent() {
            // 1. 获取编辑框中的文本
            // 小米便签中，编辑内容的控件通常叫 mNoteEditor 或类似名字
            // 如果你不确定变量名，可以查看类定义的顶部
            String content = mNoteEditor.getText().toString();

            if (content == null || content.length() == 0) {
                Toast.makeText(this, "便签内容为空，无法复制", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. 获取系统剪贴板服务
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            // 3. 创建剪贴板数据
            android.content.ClipData clip = android.content.ClipData.newPlainText("NoteContent", content);

            // 4. 设置数据到剪贴板
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "内容已复制到剪贴板", Toast.LENGTH_SHORT).show();
            }
        }

    /**
     * 核心方法：打开时间选择器以设置提醒
     */
    private void setReminder() {
        // 1. 创建时间选择对话框，默认时间设为当前时间
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        
        // 2. 设置监听器：当用户选择好时间后回调
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                // 调用 WorkingNote 的方法更新闹钟时间
                // 参数：date (闹钟时间戳), true (表示开启闹钟)
                mWorkingNote.setAlertDate(date, true);
            }
        });
        d.show(); // 显示对话框
    }

    /**
     * 核心方法：分享便签
     * 功能：调用系统分享接口 (ACTION_SEND)，将便签内容以纯文本形式发送
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        // 将便签内容放入 Intent 的 EXTRA_TEXT 字段
        intent.putExtra(Intent.EXTRA_TEXT, info);
        // 设置数据类型为纯文本
        intent.setType("text/plain");
        // 启动系统选择器（微信、短信、邮件等）
        context.startActivity(intent);
    }

    /**
     * 核心方法：新建便签
     * 流程：先保存当前内容 -> 关闭当前页 -> 启动一个新的空白编辑页
     */
    private void createNewNote() {
        // 1. 保存当前正在编辑的便签（防止数据丢失）
        saveNote();

        // 2. 结束当前 Activity
        finish();
        
        // 3. 启动一个新的 NoteEditActivity
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT); // 设置为“插入或编辑”模式
        // 传递文件夹 ID，确保新便签属于同一个文件夹
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 核心方法：删除当前便签
     * 逻辑：区分“同步模式”和“非同步模式”
     */
    private void deleteCurrentNote() {
        // 只有当便签存在于数据库中时才执行删除
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            
            // 安全检查：防止删除根文件夹
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }
            
            // 判断是否为同步模式（即是否开启了云同步账号）
            if (!isSyncMode()) {
                // 非同步模式：直接物理删除
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                // 同步模式：逻辑删除（移动到“回收站”文件夹），以便云端同步删除
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        // 标记当前工作便签为“已删除”状态
        mWorkingNote.markDeleted(true);
    }

    /**
     * 辅助方法：判断是否为同步模式
     * 逻辑：检查是否配置了同步账号
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 监听器回调：当闹钟状态改变时调用（实现 NoteSettingChangedListener）
     * 核心逻辑：注册或取消系统闹钟广播
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * 安全检查：
         * 用户可能在一个空便签（未保存，无ID）上设置闹钟。
         * 因为闹钟 Intent 需要携带 Note ID，所以必须先保存便签。
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }
        
        // 确保便签有合法的 ID
        if (mWorkingNote.getNoteId() > 0) {
            // 1. 构建 Intent：目标是 AlarmReceiver
            Intent intent = new Intent(this, AlarmReceiver.class);
            // 2. 将 Note ID 拼接到 Data URI 中
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));
            
            // 3. 创建 PendingIntent：用于系统在未来触发
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            
            // 4. 获取系统闹钟服务
            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            
            // 5. 更新 UI 头部（显示闹钟图标）
            showAlertHeader();
            
            // 6. 执行操作
            if(!set) {
                // 取消闹钟
                alarmManager.cancel(pendingIntent);
            } else {
                // 设置闹钟：RTC_WAKEUP 表示即使手机休眠也会唤醒
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * 异常情况：
             * 用户没有输入任何内容（便签为空，不值得保存），导致没有 ID。
             * 此时无法设置闹钟，提示用户。
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 监听器回调：当桌面小部件（Widget）发生变化时调用
     * 职责：触发 Widget 更新
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 监听器回调：当待办清单（CheckList）中的某一项被删除时调用
     * 场景：用户在清单中按了退格键，删除了当前行，需要将文本合并到上一行
     * @param index 被删除的项的索引
     * @param text 被删除项的文本内容（需要保留并合并）
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        // 如果只剩一行，不允许删除（保持至少一行）
        if (childCount == 1) {
            return;
        }

        // 重新索引：删除后，后面所有项的索引都要减 1
        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        // 从界面中移除该项
        mEditTextList.removeViewAt(index);
        
        NoteEditText edit = null;
        // 确定光标焦点应该跳到哪里：如果是第一行被删，焦点给新的第一行；否则给上一行
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(R.id.et_edit_text);
        }
        
        // 将删除行的文本追加到焦点行后面
        int length = edit.length();
        edit.append(text);
        edit.requestFocus();
        // 光标移动到拼接处
        edit.setSelection(length);
    }

    /**
     * 监听器回调：当待办清单（CheckList）中按回车键时调用
     * 场景：用户在清单的一行中间按回车，需要拆分成两行
     * @param index 新行插入的位置
     * @param text 新行的文本内容（回车后的后半部分）
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * 安全检查：防止索引越界
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        // 1. 创建一个新的列表项 View
        View view = getListItem(text, index);
        // 2. 插入到指定位置
        mEditTextList.addView(view, index);
        
        // 3. 获取新插入的 EditText，设置焦点和光标位置
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();
        edit.setSelection(0); // 光标在开头
        
        // 4. 重新索引：插入后，后面所有项的索引都要更新
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

      /**
     * 核心方法：切换到待办清单（CheckList）模式
     * 功能：将普通的 EditText 隐藏，把文本内容解析为多行带 CheckBox 的列表
     * @param text 原始文本内容（用换行符分隔）
     */
    private void switchToListMode(String text) {
        // 1. 清空列表容器
        mEditTextList.removeAllViews();
        
        // 2. 按换行符分割字符串
        String[] items = text.split("\n");
        int index = 0;
        
        // 3. 遍历每一行，生成对应的 View
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }
        
        // 4. 在列表末尾添加一个空行，方便用户继续输入
        mEditTextList.addView(getListItem("", index));
        // 5. 自动聚焦到最后一行
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        // 6. 切换显示状态：隐藏普通编辑框，显示列表容器
        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 辅助方法：处理搜索高亮
     * 功能：如果用户是通过搜索进入的，将搜索关键词高亮显示（黄色背景）
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        
        // 如果有搜索关键词
        if (!TextUtils.isEmpty(userQuery)) {
            // 使用正则表达式匹配关键词
            mPattern = Pattern.compile(userQuery);
            Matcher m = mPattern.matcher(fullText);
            int start = 0;
            while (m.find(start)) {
                // 设置背景色跨度（高亮）
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(R.color.user_query_highlight)), 
                        m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 核心方法：生成单个列表项 View
     * 功能：加载布局，设置文本，绑定 CheckBox 事件，处理已完成/未完成状态
     */
    private View getListItem(String item, int index) {
        // 1. 加载列表项布局 (包含 CheckBox + EditText)
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        
        // 设置字体样式
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
        
        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        
        // 2. 绑定 CheckBox 监听：勾选时添加删除线，取消时去除
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 添加删除线标志
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    // 去除删除线，保留抗锯齿等标志
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        // 3. 解析文本前缀（判断是已完成还是未完成）
        if (item.startsWith(TAG_CHECKED)) { // 以 "√" 开头
            cb.setChecked(true);
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            item = item.substring(TAG_CHECKED.length(), item.length()).trim(); // 去除前缀
        } else if (item.startsWith(TAG_UNCHECKED)) { // 以 "□" 开头
            cb.setChecked(false);
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim(); // 去除前缀
        }

        // 4. 设置监听器和索引，用于处理回车和删除
        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        
        // 5. 设置文本内容（并处理高亮）
        edit.setText(getHighlightQueryResult(item, mUserQuery));
        return view;
    }

    /**
     * 监听器回调：当列表项文本发生变化时调用
     * 功能：如果文本为空，隐藏对应的 CheckBox（美观）
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 监听器回调：当便签模式改变时调用（普通 <-> 清单）
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            // 切换到清单模式：解析文本并显示列表
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            // 切换回普通模式
            if (!getWorkingText()) {
                // 如果没有已勾选的项目，清理掉文本中的 "□" 符号
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ", ""));
            }
            // 恢复显示普通 EditText
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 核心方法：从界面提取文本（保存到内存对象）
     * 功能：根据当前模式（清单/普通），将界面内容组装成字符串存入 mWorkingNote
     * @return boolean 是否有已完成的项目
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;
        
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 清单模式：遍历所有列表项
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                
                if (!TextUtils.isEmpty(edit.getText())) {
                    // 根据 CheckBox 状态添加前缀 "√" 或 "□"
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            // 普通模式：直接获取文本
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 核心方法：保存便签
     * 流程：提取界面数据 -> 调用 WorkingNote 保存 -> 设置返回结果
     */
    private boolean saveNote() {
        // 1. 先从界面获取最新文本
        getWorkingText();
        
        // 2. 保存到数据库
        boolean saved = mWorkingNote.saveNote();
        
        if (saved) {
            /**
             * 设置 Activity 返回结果为 RESULT_OK。
             * 这主要用于通知列表页（NotesListActivity）：
             * 用户刚刚创建或编辑了一个便签，列表页需要刷新数据。
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 核心方法：发送到桌面（创建快捷方式）
     * 功能：在启动器上生成一个图标，点击直接打开此便签
     */
    private void sendToDesktop() {
        /**
         * 安全检查：快捷方式需要绑定 Note ID，所以必须先保存
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            
            // 1. 构建点击快捷方式后启动的 Intent
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId()); // 携带 ID
            
            // 2. 组装快捷方式信息
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent); // 动作
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME, makeShortcutIconTitle(mWorkingNote.getContent())); // 名称
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app)); // 图标
            sender.putExtra("duplicate", true); // 允许重复创建
            
            // 3. 发送广播：安装快捷方式
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);
        } else {
            // 异常情况：便签为空，无法创建快捷方式
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 辅助方法：制作快捷方式标题
     * 功能：去除特殊符号（√ □），并限制长度
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");
        content = content.replace(TAG_UNCHECKED, "");
        // 截断超长文本
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN ? content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN) : content;
    }

    // --- Toast 工具方法 ---
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }
}