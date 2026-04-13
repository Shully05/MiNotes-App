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

import org.json.JSONObject;

/**
 * 抽象节点类，用于数据同步管理
 * 定义节点基础属性和同步操作行为模板，子类需实现具体同步逻辑
 */
public abstract class Node {
    // 同步动作类型常量
    public static final int SYNC_ACTION_NONE = 0;
    // 无同步操作

    public static final int SYNC_ACTION_ADD_REMOTE = 1;
    // 需要向远程服务器添加数据

    public static final int SYNC_ACTION_ADD_LOCAL = 2;
    // 需要向本地数据库添加数据

    public static final int SYNC_ACTION_DEL_REMOTE = 3;
    // 需要删除远程服务器数据

    public static final int SYNC_ACTION_DEL_LOCAL = 4;
    // 需要删除本地数据库数据

    public static final int SYNC_ACTION_UPDATE_REMOTE = 5;
    // 需要更新远程服务器数据

    public static final int SYNC_ACTION_UPDATE_LOCAL = 6;
    // 需要更新本地数据库数据

    public static final int SYNC_ACTION_UPDATE_CONFLICT = 7;
    // 存在数据冲突需要处理

    public static final int SYNC_ACTION_ERROR = 8;
    // 同步过程中发生错误

    private String mGid;// 全局唯一标识符，确保数据在本地和远程之间的一致性

    private String mName;// 节点名称，便于识别和管理

    private long mLastModified;// 上次修改时间戳，用于同步时判断数据是否需要更新(单位：毫秒)

    private boolean mDeleted;// 删除标志，指示节点是否已被删除，便于同步时处理删除操作

    // 构造函数，初始化节点属性
    public Node() {
        mGid = null;
        mName = "";
        mLastModified = 0;
        mDeleted = false;
    }

    // 抽象方法，子类需实现具体的创建操作逻辑，根据不同的actionId返回相应的包含创建指令的JSON对象
    public abstract JSONObject getCreateAction(int actionId);

    // 抽象方法，子类需实现具体的更新操作逻辑，根据不同的actionId返回相应的包含更新指令的JSON对象
    public abstract JSONObject getUpdateAction(int actionId);

    // 抽象方法，子类需实现具体的删除操作逻辑，根据不同的actionId返回相应的包含删除指令的JSON对象
    public abstract void setContentByRemoteJSON(JSONObject js);

    // 抽象方法，子类需实现具体的内容设置逻辑，根据远程服务器返回的JSON对象设置节点内容
    public abstract void setContentByLocalJSON(JSONObject js);

    // 抽象方法，子类需实现具体的本地JSON对象获取逻辑，根据节点内容生成用于本地数据库存储的JSON对象
    public abstract JSONObject getLocalJSONFromContent();

    /**
     * 根据数据库记录判断同步动作类型
     * 
     * @param c 数据库游标（指向当前记录）
     * @return 需要执行的同步动作类型常量
     */
    public abstract int getSyncAction(Cursor c);

    /* 基础属性访问方法 */
    
    /**
     * 设置全局唯一标识符
     * 
     * @param gid 任务系统分配的全局ID
     */
    public void setGid(String gid) {
        this.mGid = gid;
    }

    /**
     * 设置节点名称
     * 
     * @param name 节点名称
     */
    public void setName(String name) {
        this.mName = name;
    }

    /**
     * 设置上次修改时间戳
     * 
     * @param lastModified 上次修改时间戳(毫秒级)，用于同步时判断数据是否需要更新
     */
    public void setLastModified(long lastModified) {
        this.mLastModified = lastModified;
    }

    /**
     * 设置删除标志
     * 
     * @param deleted 删除标志  true-标记为已删除 false-正常状态
     */
    public void setDeleted(boolean deleted) {
        this.mDeleted = deleted;
    }

    /**
     * 获取全局唯一标识符
     * 
     * @return 任务系统分配的全局ID 可能为null（表示未同步到远程的新建节点）
     */
    public String getGid() {
        return this.mGid;
    }

    /**
    * 获取节点名称
    * 
    * @return 节点名称 可能为空字符串（表示未设置名称）
    */
    public String getName() {
        return this.mName;
    }

    /**
     * 获取上次修改时间戳
     * 
     * @return 上次修改时间戳(毫秒级) 可能为0（表示未设置或未知的修改时间）
     */
    public long getLastModified() {
        return this.mLastModified;
    }

    /**
     * 获取删除标志
     * 
     * @return 删除标志  true-标记为已删除 false-正常状态
     */
    public boolean getDeleted() {
        return this.mDeleted;
    }

}
