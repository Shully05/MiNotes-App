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

/**
 * 闹钟提醒界面
 * 功能：在便签闹钟触发时，弹出对话框并播放铃声
 */
package net.micode.notes.ui;

import android.app.Activity;//意味着它是一个独立的页面
import android.app.AlertDialog;//弹窗：用来创建那个弹出的对话框
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;//音频管理：用来检查手机现在的模式（比如是静音模式还是响铃模式），决定要不要播放声音
import android.media.MediaPlayer; // 媒体播放器，用于播放闹钟铃声
import android.media.RingtoneManager;//铃声管理：专门用来获取系统默认的闹钟铃声文件路径
import android.net.Uri;//资源定位符
import android.os.Bundle;
import android.os.PowerManager; // 电源管理，用于检测屏幕状态
import android.provider.Settings;//系统设置：用来读取系统的全局设置
import android.view.Window;
import android.view.WindowManager;//窗口管理：这是高级用法。因为闹钟需要在锁屏界面也能弹出来，所以需要直接操作“窗口”层级，告诉系统“把这个页面盖在锁屏上面”。

import net.micode.notes.R; // 资源文件
import net.micode.notes.data.Notes; // 数据常量定义
import net.micode.notes.tool.DataUtils; // 👈 你关注的工具类：用于处理数据和资源解析

import java.io.IOException;


/**
 * 闹钟提醒 Activity
 * 实现了按钮点击监听和对话框关闭监听
 */
// 定义一个叫 AlarmAlertActivity 的页面，
// 它本质上是一个系统页面 (extends Activity)，
// 并且它承诺能处理点击事件 (implements OnClickListener)，
// 还能处理对话框消失的事件 (implements OnDismissListener)。
public class AlarmAlertActivity extends Activity implements OnClickListener, OnDismissImpl {

    private long mNoteId;      // 当前闹钟关联的便签ID
    private String mSnippet;   // 便签的摘要内容（显示在弹窗里）
    private static final int SNIPPET_PREW_MAX_LEN = 60; // 摘要最大长度
    MediaPlayer mPlayer;       // 媒体播放器实例

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. 窗口设置：无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // FLAG_SHOW_WHEN_LOCKED 允许窗口在锁屏状态下显示
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 2. 屏幕状态检测：如果屏幕是熄灭的，需要点亮屏幕并保持常亮
        if (!isScreenOn()) {
            win.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |      // 保持屏幕开启
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |       // 打开屏幕
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON | // 允许屏幕开启时锁屏
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        // 3. 获取启动意图 (Intent)
        Intent intent = getIntent();
        
        // 4. 解析数据：从 Intent 的 Uri 中提取便签 ID
        try {
            // 例如 Uri 是 content://notes/123，getPathSegments().get(1) 取出 "123"
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));
            intent.getData()：获取整个地址对象。
            //.getPathSegments()：把地址按斜杠 / 切开，变成一个列表：

            // 👈 使用 DataUtils 工具类：根据 ID 从数据库读取便签内容片段
            //this.getContentResolver()：这是安卓提供的“数据库钥匙”，用来访问内容提供者。
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);
            
            // 5. 内容处理：截取摘要，防止过长
            if (mSnippet.length() > SNIPPET_PREW_MAX_LEN) {
                mSnippet = mSnippet.substring(0, SNIPPET_PREW_MAX_LEN) + 
                           getResources().getString(R.string.notelist_string_info);
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return; // 解析失败直接退出
        }

        // 6. 初始化播放器
        mPlayer = new MediaPlayer();
        
        // 7. 核心逻辑：检查数据库，确认该便签是否还存在且有效
        // 👈 再次使用 DataUtils：检查便签在数据库中是否存在且可见
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            showActionDialog(); // 显示提醒对话框
            playAlarmSound();   // 播放闹钟声音
        } else {
            // 便签已被删除或无效，直接关闭页面
            finish();
        }
    }

    /**
     * 检查屏幕是否处于开启状态
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);//获取“电源管家”
        return pm.isScreenOn();
    }

    /**
     * 播放闹钟铃声
     */
    /**
     * 播放闹钟铃声
     * 核心逻辑：获取铃声文件 -> 设置音频通道 -> 准备并播放
     */
    private void playAlarmSound() {
        // 1. 获取铃声文件地址
        // RingtoneManager.TYPE_ALARM：指定获取“闹钟”类型的铃声（区别于来电铃声或通知音）
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);

        // 2. 处理静音/震动模式下的音频策略
        // 读取系统设置，判断当前是否有特殊的音频流限制
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 3. 设置音频流类型 (AudioStreamType)
        // 这是一个位运算判断：检查系统是否限制了闹钟声音在静音模式下播放
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            // 如果系统有特殊限制，使用系统定义的静音策略流类型
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 默认情况：使用标准的闹钟音频通道 (STREAM_ALARM)
            // 这意味着即使手机静音，闹钟通常也会响（除非闹钟音量也被调为0）
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }
        
        // 4. 配置并启动播放器
        try {
            // 设置数据源：告诉播放器去哪里读取音频文件（刚才获取的 Uri）
            mPlayer.setDataSource(this, url);
            
            // 准备播放：这一步是同步的，会加载音频数据，完成后才能播放
            mPlayer.prepare();
            
            // 设置循环：true 表示无限循环。闹钟必须一直响，直到用户手动停止
            mPlayer.setLooping(true);
            
            // 开始播放：喇叭开始发声
            mPlayer.start();
        } catch (Exception e) {
            // 异常处理：如果铃声文件丢失或加载失败，打印错误堆栈，防止程序崩溃
            e.printStackTrace();
        }
    }

    /**
     * 显示闹钟提醒对话框
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setTitle(R.string.app_name); // 标题：MiCode Notes
        dialog.setMessage(mSnippet);        // 内容：便签的摘要
        
        // 确定按钮
        dialog.setPositiveButton(R.string.notealert_ok, this);
        
        // 如果屏幕是亮的，显示“进入”按钮（方便直接跳转编辑）
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }
        
        // 显示对话框，并设置关闭监听
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 对话框按钮点击事件处理
     * DialogInterface.BUTTON_NEGATIVE 对应 "Enter" (进入) 按钮
     * DialogInterface.BUTTON_POSITIVE 对应 "OK" (确定) 按钮
     */
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE: // 用户点击了“进入”
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);//跳转
                intent.putExtra(Intent.EXTRA_UID, mNoteId); // 传入便签ID
                startActivity(intent);
                break;
            default:
                break; // 点击确定默认关闭对话框
        }
    }

    /**
     * 对话框关闭时的回调
     * 无论用户点击哪个按钮，对话框消失时都会调用
     */
    public void onDismiss(DialogInterface dialog) {
        stopAlarmSound(); // 停止播放铃声
        finish();         // 销毁 Activity
    }

    /**
     * 停止闹钟声音
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
    }
}