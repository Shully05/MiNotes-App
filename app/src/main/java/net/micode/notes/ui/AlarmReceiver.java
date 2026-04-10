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

package net.micode.notes.ui;

// 导入的包
import android.content.BroadcastReceiver; // 广播接收器基类
import android.content.Context;           // 上下文环境
import android.content.Intent;            // 意图，用来跳转和传递数据

/**
 * 闹钟广播接收器
 * 
 * 作用：它是闹钟功能的“最后一公里”。
 * 当系统闹钟（AlarmManager）时间一到，系统会发送一个广播给它。
 * 它收到广播后，负责把“响铃界面”（AlarmAlertActivity）启动起来。
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收广播的回调方法
     * 当闹钟时间到了，系统会自动调用这个方法
     * 
     * @param context 上下文环境
     * @param intent  系统发送过来的意图
     *                 （注意：这个 intent 携带了你在 AlarmManager 里设置的数据，比如便签 ID）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        
        // 1. 【设定目标】：告诉 Intent “你要去哪？”
        // setClass：把当前的 Intent 的目标组件（Component）设置为 AlarmAlertActivity
        // 这一步相当于：把“车票”塞进 Intent 里，告诉它要启动哪个页面
        intent.setClass(context, AlarmAlertActivity.class);

        // 2. 【添加特殊标记】：为 Intent 添加启动标志
        // FLAG_ACTIVITY_NEW_TASK：这是一个关键标志
        // 原因：BroadcastReceiver 是在后台运行的（没有界面），而我们要启动的是一个 Activity（有界面）。
        // Android 规定：后台服务或广播无法直接启动前台 Activity，必须加上这个标志，表示“我要开启一个新的任务栈”。
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 3. 【执行跳转】：启动 Activity
        // context.startActivity(intent)：拿着刚才设置好目标和标志的 Intent，去启动页面
        // 这一刻，手机屏幕会亮起，跳转到 AlarmAlertActivity 页面，开始播放音乐。
        context.startActivity(intent);
    }
}