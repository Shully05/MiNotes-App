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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人工具类
 * 用于根据电话号码从系统通讯录中查询联系人姓名
 */
public class Contact {
    // 缓存：存储已查询过的电话号码和对应的联系人姓名，避免重复查询数据库
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    /**
     * 查询通讯录的SQL选择条件模板
     * 用于匹配电话号码，同时确保查询的是联系人中的电话类型数据
     *
     * 解析：
     * - PHONE_NUMBERS_EQUAL(Phone.NUMBER, ?) : 比较电话号码是否相等（支持国际化格式）
     * - Data.MIMETYPE = Phone.CONTENT_ITEM_TYPE : 只查询电话类型的数据
     * - Data.RAW_CONTACT_ID IN (SELECT raw_contact_id FROM phone_lookup WHERE min_match = '+')
     *   通过子查询找到匹配的联系人，其中 '+' 会被替换为实际号码的最小匹配值
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名
     *
     * @param context 上下文对象，用于访问ContentProvider
     * @param phoneNumber 要查询的电话号码
     * @return 对应的联系人姓名，如果找不到则返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 懒加载：首次使用时初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 先检查缓存中是否已有该号码对应的联系人信息
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 构建查询条件：将模板中的 '+' 替换为实际号码的最小匹配值
        // toCallerIDMinMatch 会提取号码的后几位数字用于模糊匹配，提高查询容错率
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询系统通讯录
        // Data.CONTENT_URI: 所有联系人数据的URI
        // 只查询 Phone.DISPLAY_NAME（联系人显示名称）
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },  // 投影：只需要姓名列
                selection,                             // 查询条件
                new String[] { phoneNumber },          // 查询参数（原始电话号码）
                null);                                 // 排序

        // 处理查询结果
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 从第一行获取联系人姓名
                String name = cursor.getString(0);
                // 将结果存入缓存，下次查询同一号码时直接返回
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 异常处理：游标获取数据出错
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 无论成功与否，都要关闭游标，释放资源
                cursor.close();
            }
        } else {
            // 未找到匹配的联系人
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}