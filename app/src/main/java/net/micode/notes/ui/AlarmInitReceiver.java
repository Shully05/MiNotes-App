/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.micode.notes.ui;
//当手机开机（或者应用数据被清除后重新启动）时，它会去数据库里翻找所有已经设置好的闹钟便签，然后重新把它们注册到系统的“闹钟管家”（AlarmManager）里。
// 如果没有这个类，你设置好闹钟后重启手机，闹钟就会失效。
import android.app.AlarmManager;           // 系统闹钟服务，用来管理定时任务
import android.app.PendingIntent;         // 待执行的意图，这里用来包装闹钟触发时要做的事情
import android.content.BroadcastReceiver; // 广播接收器基类
import android.content.ContentUris;        // 用来操作 Uri（数据库 ID 转 Uri）
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;            // 数据库游标，用来读取查询结果

import net.micode.notes.data.Notes;        // 项目内部的常量类（包含数据库表名、字段名等）
import net.micode.notes.data.Notes.NoteColumns; // 专门定义笔记字段的类

/**
 * 闹钟初始化广播接收器
 * 
 * 作用：监听系统启动完成（或其他触发时机），将数据库中所有待触发的闹钟重新加载到系统中。
 * 为什么需要它？因为 Android 系统在重启后，内存中的闹钟会丢失，必须从数据库重新读取并注册。
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    // 1. 定义查询数据库需要的字段（投影）
    // 我们只需要两个信息：便签的 ID（为了构造跳转链接）和 闹钟时间（为了设置闹钟）
    private static final String [] PROJECTION = new String [] {
        NoteColumns.ID,               // 数据库主键 ID
        NoteColumns.ALERTED_DATE      // 用户设定的闹钟触发时间
    };

    // 2. 定义字段在查询结果中的索引位置（为了提高效率，避免用字符串查找）
    private static final int COLUMN_ID                = 0; // 对应 ID
    private static final int COLUMN_ALERTED_DATE      = 1; // 对应 闹钟时间

    /**
     * 广播接收回调方法
     * 当系统发送广播（如开机完成）时，系统会自动调用这个方法
     * 
     * @param context 上下文环境
     * @param intent  发送广播时携带的意图（包含广播动作信息）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        
        // 3. 获取当前系统时间（毫秒）
        // 用来做查询条件：只找那些“还没过期”的闹钟
        long currentDate = System.currentTimeMillis();

        // 4. 【数据库查询】去“便签表”里找所有需要闹钟提醒的笔记
        // 这是核心的数据源查询
        Cursor c = context.getContentResolver().query(
            Notes.CONTENT_NOTE_URI,           // 查询目标：便签内容的 Uri 地址
            PROJECTION,                       // 只要 ID 和 闹钟时间 这两列
            // 查询条件（WHERE）：
            //   ALERTED_DATE > 当前时间（闹钟时间还没到）
            //   AND 
            //   TYPE = TYPE_NOTE（并且是普通便签，不是文件夹等其他类型）
            NoteColumns.ALERTED_DATE + ">? AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
            new String[] { String.valueOf(currentDate) }, // ? 号的值，填入当前时间
            null                              // 不排序
        );

        // 5. 处理查询结果
        // 判断游标是否为空（防止数据库没初始化好）
        if (c != null) {
            
            // 移动到第一条数据
            if (c.moveToFirst()) {
                
                // 6. 循环遍历所有查到的闹钟
                // do-while 循环，处理每一条记录
                do {
                    // a. 从数据库读取“闹钟时间”
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // b. 【构建意图】：告诉系统“闹钟响了该干什么？”
                    // 这里的逻辑是：发送一个广播给 AlarmReceiver
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    
                    // c. 【关键步骤】给 Intent 设置一个唯一的“数据标识”
                    // 为什么要这样做？
                    // 如果不设置 Data 或 ID，系统会认为所有闹钟都是同一个 PendingIntent。
                    // 导致的结果是：你设了 3 个闹钟，系统只认最后一个，或者 3 个闹钟同时响同一个。
                    // ContentUris.withAppendedId(...)：把便签 ID 拼接到 Uri 后面，变成 content://notes/123
                    sender.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));

                    // d. 【打包任务】：创建 PendingIntent
                    // 这是一个“打包好的任务包”，现在不执行，而是交给 AlarmManager 等待执行。
                    // 参数 0 通常代表 FLAG，这里没有特殊标志。
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, sender, 0);

                    // e. 【获取闹钟管家】
                    // getSystemService 拿到系统的 AlarmManager 服务
                    AlarmManager alermManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

                    // f. 【注册闹钟】：把任务扔给系统
                    // set(类型, 触发时间, 扔给谁去执行)
                    // RTC_WAKEUP：表示即使手机休眠也要唤醒 CPU 执行
                    // alertDate：刚才从数据库读取的精确时间
                    // pendingIntent：刚才打包好的任务（包含要启动的 Receiver 和 数据 ID）
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                // 移动到下一条数据
                } while (c.moveToNext());
            }
            
            // 7. 【资源回收】
            // 用完数据库游标必须关闭，防止内存泄漏
            c.close();
        }
    }
}