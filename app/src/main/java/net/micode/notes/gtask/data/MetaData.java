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

package net.micode.notes.gtask.data;

import android.database.Cursor;
import android.util.Log;

import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 元数据类，继承自Task，用于处理与Google任务相关的元数据信息
 */
public class MetaData extends Task {
    private final static String TAG = MetaData.class.getSimpleName();// 日志标签

    private String mRelatedGid = null;// 存储相关联的Google任务组ID（GID）

    public void setMeta(String gid, JSONObject metaInfo) {
        try {
            // 将GID添加到元数据JSON中
            metaInfo.put(GTaskStringUtils.META_HEAD_GTASK_ID, gid);
        } catch (JSONException e) {
            Log.e(TAG, "failed to put related gid");
        }
        // 将JSON转为字符串并设置为内容
        setNotes(metaInfo.toString());
        // 设置元数据任务的名称
        setName(GTaskStringUtils.META_NOTE_NAME);
    }

    /**
     * 获取相关联的Google任务组ID
     * 
     * @return 关联的GID字符串
     */
    public String getRelatedGid() {
        return mRelatedGid;
    }

    /**
     * 判断元数据是否需要保存（内容不为空时需保存）
     * 
     * @return 是否需要保存的布尔值
     */
    @Override
    public boolean isWorthSaving() {
        return getNotes() != null;
    }

    /**
     * 从远程JSON数据解析元数据内容
     * 
     * @param js 包含元数据的远程JSON对象
     */    
    @Override
    public void setContentByRemoteJSON(JSONObject js) {
        super.setContentByRemoteJSON(js);
        if (getNotes() != null) {
            try {
                // 从内容字符串解析JSON并提取相关GID
                JSONObject metaInfo = new JSONObject(getNotes().trim());
                mRelatedGid = metaInfo.getString(GTaskStringUtils.META_HEAD_GTASK_ID);
            } catch (JSONException e) {
                Log.w(TAG, "failed to get related gid");
                mRelatedGid = null;// 解析失败时重置GID
            }
        }
    }

    /**
     * （禁止调用）不支持从本地JSON设置内容
     * 
     * @param js 本地JSON对象
     * @throws IllegalAccessError 总抛出异常，提示方法不可用
     */    
    @Override
    public void setContentByLocalJSON(JSONObject js) {
        // this function should not be called
        throw new IllegalAccessError("MetaData:setContentByLocalJSON should not be called");
    }

    /**
     * （禁止调用）不支持生成本地JSON内容
     * 
     * @throws IllegalAccessError 总抛出异常，提示方法不可用
     */    
    @Override
    public JSONObject getLocalJSONFromContent() {
        throw new IllegalAccessError("MetaData:getLocalJSONFromContent should not be called");
    }

    /**
     * （禁止调用）不支持获取同步动作
     * 
     * @param c 数据库游标
     * @throws IllegalAccessError 总抛出异常，提示方法不可用
     */    
    @Override
    public int getSyncAction(Cursor c) {
        throw new IllegalAccessError("MetaData:getSyncAction should not be called");
    }

}
