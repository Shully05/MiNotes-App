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

package net.micode.notes.gtask.remote;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.data.MetaData;
import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.SqlNote;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.GTaskStringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;


/// 自Google Task Sync Adapter，做了部分修改以适应Note的同步需求
public class GTaskManager {
    // debug用，应该在每个类中定义日志标签等，之后应该删除
    private static final String TAG = GTaskManager.class.getSimpleName();

    public static final int STATE_SUCCESS = 0;// sync成功

    public static final int STATE_NETWORK_ERROR = 1;// 网络异常，可能是网络不可用或者登录验证失败等引起的

    public static final int STATE_INTERNAL_ERROR = 2;// 内部错误，可能是数据解析错误或者数据库操作错误等引起的

    public static final int STATE_SYNC_IN_PROGRESS = 3;// 已经有一个同步正在进行中

    public static final int STATE_SYNC_CANCELLED = 4;// 同步被用户取消

    private static GTaskManager mInstance = null;// 单例

    private Activity mActivity;// 用于获取authtoken，注意不要在非UI线程中使用这个context，否则可能会引起问题

    private Context mContext;// 用于数据库操作等，注意不要在UI线程中使用这个context，否则可能会引起问题

    private ContentResolver mContentResolver;// 用于数据库操作

    // 核心同步锁与标志位
    private boolean mSyncing;// 是否正在同步
    private boolean mCancelled;// 是否取消同步

    //云端数据缓存：保存从 Google Task 服务器获取的 TaskList 和 Task
    private HashMap<String, TaskList> mGTaskListHashMap;// gid -> TaskList
    private HashMap<String, Node> mGTaskHashMap;// gid -> Node (Task/TaskList)

    private HashMap<String, MetaData> mMetaHashMap;// key: gid, value: MetaData,保存从google task服务器上获取的元数据

    private TaskList mMetaList;// 元数据列表

    private HashSet<Long> mLocalDeleteIdMap;// 保存本地被删除的note id

    // 双向 ID 映射表：这是同步的核心，解决云端 GID 与本地 NID 的对应问题
    private HashMap<String, Long> mGidToNid;// key: gid, value: nid, 保存google task id到本地note id的映射
    private HashMap<Long, String> mNidToGid;// key: nid, value: gid, 保存本地note id到google task id的映射

    // 构造函数私有化，单例模式
    private GTaskManager() {
        mSyncing = false;
        mCancelled = false;
        mGTaskListHashMap = new HashMap<String, TaskList>();
        mGTaskHashMap = new HashMap<String, Node>();
        mMetaHashMap = new HashMap<String, MetaData>();
        mMetaList = null;
        mLocalDeleteIdMap = new HashSet<Long>();
        mGidToNid = new HashMap<String, Long>();
        mNidToGid = new HashMap<Long, String>();
    }

    // 获取单例对象
    public static synchronized GTaskManager getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskManager();
        }
        return mInstance;
    }

    // 设置Activity context，注意不要在非UI线程中使用这个context，否则可能会引起问题
    public synchronized void setActivityContext(Activity activity) {
        // used for getting authtoken
        mActivity = activity;
    }

    // 同步入口，返回同步结果状态
    public int sync(Context context, GTaskASyncTask asyncTask) {
        // 1. 状态检查：防止并发同步
        if (mSyncing) {
            Log.d(TAG, "Sync is in progress");
            return STATE_SYNC_IN_PROGRESS;
        }
        // 2. 初始化环境变量与清理旧缓存
        mContext = context;
        mContentResolver = mContext.getContentResolver();
        mSyncing = true;
        mCancelled = false;
        mGTaskListHashMap.clear();
        mGTaskHashMap.clear();
        mMetaHashMap.clear();
        mLocalDeleteIdMap.clear();
        mGidToNid.clear();
        mNidToGid.clear();

        try {
            GTaskClient client = GTaskClient.getInstance();
            client.resetUpdateArray();

            // 3. 登录认证
            // login google task
            if (!mCancelled) {
                if (!client.login(mActivity)) {
                    throw new NetworkFailureException("login google task failed");
                }
            }

            // 4. 初始化云端列表（关键步骤）
            // 作用：将云端所有的 TaskList, Task, MetaData 下载并解析到内存 HashMap 中
            // 这样后续比对时，可以直接通过 Key 查找，而不需要遍历
            // get the task list from google
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_init_list));
            initGTaskList();

            // 5. 核心内容同步
            // 作用：比较本地数据库与内存中的云端数据，执行增删改查
            // do content sync work
            asyncTask.publishProgess(mContext.getString(R.string.sync_progress_syncing));
            syncContent();
        } catch (NetworkFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_NETWORK_ERROR;
        } catch (ActionFailureException e) {
            Log.e(TAG, e.toString());
            return STATE_INTERNAL_ERROR;
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return STATE_INTERNAL_ERROR;
        } finally {
            // 6. 资源释放与状态重置
            mGTaskListHashMap.clear();
            mGTaskHashMap.clear();
            mMetaHashMap.clear();
            mLocalDeleteIdMap.clear();
            mGidToNid.clear();
            mNidToGid.clear();
            mSyncing = false;
        }

        return mCancelled ? STATE_SYNC_CANCELLED : STATE_SUCCESS;
    }

    // 从google task服务器上获取task list和task，并保存到内存中，方便后续同步使用
    private void initGTaskList() throws NetworkFailureException {
        if (mCancelled)
            return;
        GTaskClient client = GTaskClient.getInstance();
        try {
            JSONArray jsTaskLists = client.getTaskLists();

            // init meta list first
            mMetaList = null;
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name
                        .equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_META)) {
                    mMetaList = new TaskList();
                    mMetaList.setContentByRemoteJSON(object);

                    // load meta data
                    JSONArray jsMetas = client.getTaskList(gid);
                    for (int j = 0; j < jsMetas.length(); j++) {
                        object = (JSONObject) jsMetas.getJSONObject(j);
                        MetaData metaData = new MetaData();
                        metaData.setContentByRemoteJSON(object);
                        if (metaData.isWorthSaving()) {
                            mMetaList.addChildTask(metaData);
                            if (metaData.getGid() != null) {
                                mMetaHashMap.put(metaData.getRelatedGid(), metaData);
                            }
                        }
                    }
                }
            }

            // create meta list if not existed
            if (mMetaList == null) {
                mMetaList = new TaskList();
                mMetaList.setName(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                        + GTaskStringUtils.FOLDER_META);
                GTaskClient.getInstance().createTaskList(mMetaList);
            }

            // init task list
            for (int i = 0; i < jsTaskLists.length(); i++) {
                JSONObject object = jsTaskLists.getJSONObject(i);
                String gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                String name = object.getString(GTaskStringUtils.GTASK_JSON_NAME);

                if (name.startsWith(GTaskStringUtils.MIUI_FOLDER_PREFFIX)
                        && !name.equals(GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                + GTaskStringUtils.FOLDER_META)) {
                    TaskList tasklist = new TaskList();
                    tasklist.setContentByRemoteJSON(object);
                    mGTaskListHashMap.put(gid, tasklist);
                    mGTaskHashMap.put(gid, tasklist);

                    // load tasks
                    JSONArray jsTasks = client.getTaskList(gid);
                    for (int j = 0; j < jsTasks.length(); j++) {
                        object = (JSONObject) jsTasks.getJSONObject(j);
                        gid = object.getString(GTaskStringUtils.GTASK_JSON_ID);
                        Task task = new Task();
                        task.setContentByRemoteJSON(object);
                        if (task.isWorthSaving()) {
                            task.setMetaInfo(mMetaHashMap.get(gid));
                            tasklist.addChildTask(task);
                            mGTaskHashMap.put(gid, task);
                        }
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("initGTaskList: handing JSONObject failed");
        }
    }

    // 同步内容，核心同步逻辑，比较复杂，主要分为以下几个步骤：
    // 1. 处理本地被删除的note，google task服务器上对应的note应该被删除
    // 2. 处理本地存在的note，比较google task服务器上对应的note，如果google task服务器上不存在，说明被远程删除了；如果存在，比较两者的修改时间，决定是更新本地还是更新远程
    // 3. 处理google task服务器上存在但本地不存在的note，说明是远程新增的，应该添加到本地
    // 4. 最后提交google task服务器上的修改，并刷新本地的sync id
    // 注意：在每一步操作之前都要检查同步是否被取消了，如果被取消了就直接返回，停止后续的同步操作
    private void syncContent() throws NetworkFailureException {
        int syncType;
        Cursor c = null;
        String gid;// Google Task ID
        Node node;// 云端节点对象 (Task/TaskList)

        mLocalDeleteIdMap.clear();// 清空上一次同步的本地删除记录

        if (mCancelled) {
            return;// 检查同步是否已被用户取消
        }

        // PHASE 1: 处理【本地已删除】的笔记 (回收站同步)
        // 目标：确保云端也删除了本地回收站里的内容
        // for local deleted note
        try {
            // 1. 查询本地数据库中所有在"回收站"里的笔记
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id=?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, null);
            if (c != null) {
                while (c.moveToNext()) {
                    // 2. 获取该笔记对应的云端 GID
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    // 3. 检查云端是否也存在这个 GID
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        // 云端存在 -> 需要在云端执行删除
                        mGTaskHashMap.remove(gid);// 从待处理列表移除，避免后续重复处理
                        doContentSync(Node.SYNC_ACTION_DEL_REMOTE, node, c);// 标记：云端删除
                    }
                    // 记录本地 ID，稍后在本地数据库物理删除
                    mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                }
            } else {
                Log.w(TAG, "failed to query trash folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // PHASE 2: 同步【文件夹】 (Folder First)
        // 原因：笔记（Note）依赖于文件夹（Folder），必须先确保存在容器
        // sync folder first
        syncFolder();

        // PHASE 3: 处理【本地存在的笔记】 (增量同步核心)
        // 逻辑：遍历本地所有正常笔记，对比云端状态
        // for note existing in database
        try {
            // 1. 查询本地所有正常的笔记 (非系统、非回收站)
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_NOTE), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);// 检查云端是否有此 ID
                    if (node != null) {
                        // 情况 A: 本地 ID 与 云端 ID 存在映射 (Update or Conflict)
                        mGTaskHashMap.remove(gid);// 从云端残留列表中移除
                        // 建立双向 ID 映射表 (GID <-> NID)
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        // 核心判断：比较修改时间，决定是"本地覆盖云端"还是"云端覆盖本地"
                        syncType = node.getSyncAction(c);
                    } else {
                        // 情况 B: 云端找不到此 GID (可能是新增或云端已删)
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // 子情况 B1: 本地笔记没有 GID -> 这是一个全新的笔记
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // 子情况 B2: 本地有 GID，但云端没有 -> 云端可能已删除
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing note in database");
            }

        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // PHASE 4: 处理【云端新增】的笔记 (Remaining Items)
        // 逻辑：经过 Phase 3 后，mGTaskHashMap 中剩下的 Node 就是本地没有的
        // go through remaining items
        Iterator<Map.Entry<String, Node>> iter = mGTaskHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Node> entry = iter.next();
            node = entry.getValue();
            doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
        }

        // PHASE 5: 提交与清理 (Commit & Finalize)
        // 1. 批量清理本地回收站 (物理删除)
        // mCancelled can be set by another thread, so we neet to check one by
        // one
        // clear local delete table
        if (!mCancelled) {
            if (!DataUtils.batchDeleteNotes(mContentResolver, mLocalDeleteIdMap)) {
                throw new ActionFailureException("failed to batch-delete local deleted notes");
            }
        }

        // 2. 提交云端变更 & 刷新本地版本号
        // refresh local sync id
        if (!mCancelled) {
            GTaskClient.getInstance().commitUpdate();
            // 更新本地的 Sync ID
            // 作用：记录云端最新的版本状态，确保下次同步只拉取"增量"数据
            refreshLocalSyncId();
        }

    }

    // 同步内容中首先要同步文件夹，因为note依赖于folder，如果folder都不同步，note的同步会比较麻烦
    private void syncFolder() throws NetworkFailureException {
        Cursor c = null;
        String gid;
        Node node;
        int syncType;

        if (mCancelled) {
            return;
        }

        // STEP 1: 同步【根文件夹】 (Root Folder)
        // 策略：通常是强制匹配，只更新名称，不允许删除或新增，因为根文件夹是系统预定义的，必须存在 
        // for root folder
        try {
            // 1. 查询本地根文件夹 (ID_ROOT_FOLDER)
            c = mContentResolver.query(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                    Notes.ID_ROOT_FOLDER), SqlNote.PROJECTION_NOTE, null, null, null);
            if (c != null) {
                c.moveToNext();
                gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                node = mGTaskHashMap.get(gid);// 检查云端是否存在
                if (node != null) {
                    // 云端存在 -> 建立映射
                    mGTaskHashMap.remove(gid);
                    mGidToNid.put(gid, (long) Notes.ID_ROOT_FOLDER);
                    mNidToGid.put((long) Notes.ID_ROOT_FOLDER, gid);
                    // 特殊逻辑：系统文件夹通常只同步名称
                    // 如果云端名称不对，强制更新云端名称
                    // for system folder, only update remote name if necessary
                    if (!node.getName().equals(
                            GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT))
                        doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                } else {
                    // 云端不存在 -> 需要上传本地根文件夹
                    doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                }
            } else {
                Log.w(TAG, "failed to query root folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // STEP 2: 同步【通话记录文件夹】 (Call Note Folder)
        // 策略：同上，也是系统预设文件夹
        // for call-note folder
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE, "(_id=?)",
                    new String[] {
                        String.valueOf(Notes.ID_CALL_RECORD_FOLDER)
                    }, null);
            if (c != null) {
                if (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, (long) Notes.ID_CALL_RECORD_FOLDER);
                        mNidToGid.put((long) Notes.ID_CALL_RECORD_FOLDER, gid);
                        // for system folder, only update remote name if
                        // necessary
                        if (!node.getName().equals(
                                GTaskStringUtils.MIUI_FOLDER_PREFFIX
                                        + GTaskStringUtils.FOLDER_CALL_NOTE))
                            doContentSync(Node.SYNC_ACTION_UPDATE_REMOTE, node, c);
                    } else {
                        doContentSync(Node.SYNC_ACTION_ADD_REMOTE, node, c);
                    }
                }
            } else {
                Log.w(TAG, "failed to query call note folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // STEP 3: 同步【普通文件夹】 (User-defined Folders)
        // 逻辑：标准的增删改查判断，但需要注意的是，文件夹的删除可能会导致下面的笔记也被删除，所以在删除文件夹时要特别小心，确保同时处理好文件夹内的笔记
        // for local existing folders
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type=? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_FOLDER), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        mGidToNid.put(gid, c.getLong(SqlNote.ID_COLUMN));
                        mNidToGid.put(c.getLong(SqlNote.ID_COLUMN), gid);
                        syncType = node.getSyncAction(c);
                    } else {
                        if (c.getString(SqlNote.GTASK_ID_COLUMN).trim().length() == 0) {
                            // local add
                            syncType = Node.SYNC_ACTION_ADD_REMOTE;
                        } else {
                            // remote delete
                            syncType = Node.SYNC_ACTION_DEL_LOCAL;
                        }
                    }
                    doContentSync(syncType, node, c);
                }
            } else {
                Log.w(TAG, "failed to query existing folder");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }

        // STEP 4: 同步【云端新增】的文件夹 (Remaining Items)
        // 逻辑：经过上面的循环，mGTaskHashMap 里剩下的就是本地没有的
        // for remote add folders
        Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, TaskList> entry = iter.next();
            gid = entry.getKey();
            node = entry.getValue();
            // 如果云端有，且本地 mGTaskHashMap 里也有（说明本地没处理过）
            if (mGTaskHashMap.containsKey(gid)) {
                mGTaskHashMap.remove(gid);
                // 下载到本地
                doContentSync(Node.SYNC_ACTION_ADD_LOCAL, node, null);
            }
        }

        // 提交本次文件夹的修改，确保云端状态更新后再进行笔记的同步
        if (!mCancelled)
            GTaskClient.getInstance().commitUpdate();
    }

    // 同步内容的核心方法，根据不同的同步类型执行相应的增删改查操作
    private void doContentSync(int syncType, Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        MetaData meta;
        switch (syncType) {
            // 本地新增：云端需要新增
            case Node.SYNC_ACTION_ADD_LOCAL:
                addLocalNode(node);
                break;
            // 云端新增：本地需要新增
            case Node.SYNC_ACTION_ADD_REMOTE:
                addRemoteNode(node, c);
                break;
            // 本地删除：云端需要删除
            case Node.SYNC_ACTION_DEL_LOCAL:
                meta = mMetaHashMap.get(c.getString(SqlNote.GTASK_ID_COLUMN));
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                mLocalDeleteIdMap.add(c.getLong(SqlNote.ID_COLUMN));
                break;
            // 云端删除：本地需要删除
            case Node.SYNC_ACTION_DEL_REMOTE:
                meta = mMetaHashMap.get(node.getGid());
                if (meta != null) {
                    GTaskClient.getInstance().deleteNode(meta);
                }
                GTaskClient.getInstance().deleteNode(node);
                break;
            // 本地修改：云端需要更新
            case Node.SYNC_ACTION_UPDATE_LOCAL:
                updateLocalNode(node, c);
                break;
            // 云端修改：本地需要更新
            case Node.SYNC_ACTION_UPDATE_REMOTE:
                updateRemoteNode(node, c);
                break;
            // 冲突修改：本地和云端都有修改，优先级不确定，需要合并
            case Node.SYNC_ACTION_UPDATE_CONFLICT:
                // merging both modifications maybe a good idea
                // right now just use local update simply
                updateRemoteNode(node, c);
                break;
            // 无需修改：本地和云端都没有修改，或者修改后状态相同，不需要任何操作
            case Node.SYNC_ACTION_NONE:
                break;
            // 其他未知类型，抛出异常
            case Node.SYNC_ACTION_ERROR:
            // 没有默认情况，专门用于错误处理
            default:
                throw new ActionFailureException("unkown sync action type");
        }
    }

    // 下面是针对不同同步类型的具体操作实现，包括添加、更新、删除等，涉及到本地数据库操作和云端 API 调用
    private void addLocalNode(Node node) throws NetworkFailureException {
        // 添加本地节点，通常是从云端下载到本地
        if (mCancelled) {
            return;
        }

        // 构造本地 SqlNote 对象，准备插入数据库
        SqlNote sqlNote;
        // 如果是文件夹，根据名称判断是根文件夹、通话记录文件夹还是普通文件夹，设置相应的属性
        if (node instanceof TaskList) {
            if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_DEFAULT)) {
                sqlNote = new SqlNote(mContext, Notes.ID_ROOT_FOLDER);
            } else if (node.getName().equals(
                    GTaskStringUtils.MIUI_FOLDER_PREFFIX + GTaskStringUtils.FOLDER_CALL_NOTE)) {
                sqlNote = new SqlNote(mContext, Notes.ID_CALL_RECORD_FOLDER);
            } else {
                sqlNote = new SqlNote(mContext);
                sqlNote.setContent(node.getLocalJSONFromContent());
                sqlNote.setParentId(Notes.ID_ROOT_FOLDER);
            }
        } else {
            // 如果是笔记，直接根据云端内容构造 SqlNote，并设置父 ID
            sqlNote = new SqlNote(mContext);
            JSONObject js = node.getLocalJSONFromContent();
            try {
                if (js.has(GTaskStringUtils.META_HEAD_NOTE)) {
                    JSONObject note = js.getJSONObject(GTaskStringUtils.META_HEAD_NOTE);
                    if (note.has(NoteColumns.ID)) {
                        long id = note.getLong(NoteColumns.ID);
                        if (DataUtils.existInNoteDatabase(mContentResolver, id)) {
                            // the id is not available, have to create a new one
                            note.remove(NoteColumns.ID);
                        }
                    }
                }

                if (js.has(GTaskStringUtils.META_HEAD_DATA)) {
                    JSONArray dataArray = js.getJSONArray(GTaskStringUtils.META_HEAD_DATA);
                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONObject data = dataArray.getJSONObject(i);
                        if (data.has(DataColumns.ID)) {
                            long dataId = data.getLong(DataColumns.ID);
                            if (DataUtils.existInDataDatabase(mContentResolver, dataId)) {
                                // the data id is not available, have to create
                                // a new one
                                data.remove(DataColumns.ID);
                            }
                        }
                    }

                }
            } catch (JSONException e) {
                Log.w(TAG, e.toString());
                e.printStackTrace();
            }
            sqlNote.setContent(js);

            Long parentId = mGidToNid.get(((Task) node).getParent().getGid());
            if (parentId == null) {
                Log.e(TAG, "cannot find task's parent id locally");
                throw new ActionFailureException("cannot add local node");
            }
            sqlNote.setParentId(parentId.longValue());
        }

        // create the local node
        sqlNote.setGtaskId(node.getGid());
        sqlNote.commit(false);

        // update gid-nid mapping
        mGidToNid.put(node.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), node.getGid());

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    // 更新本地节点，通常是云端修改后同步到本地
    private void updateLocalNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // 构造 SqlNote 对象，准备更新数据库
        SqlNote sqlNote;
        // update the note locally
        sqlNote = new SqlNote(mContext, c);
        sqlNote.setContent(node.getLocalJSONFromContent());

        // 更新父 ID，如果是笔记，需要根据云端父节点的 GID 查找本地父 ID；如果是文件夹，父 ID 就是根文件夹
        Long parentId = (node instanceof Task) ? mGidToNid.get(((Task) node).getParent().getGid())
                : new Long(Notes.ID_ROOT_FOLDER);
        if (parentId == null) {
            Log.e(TAG, "cannot find task's parent id locally");
            throw new ActionFailureException("cannot update local node");
        }
        sqlNote.setParentId(parentId.longValue());
        sqlNote.commit(true);

        // update meta info
        updateRemoteMeta(node.getGid(), sqlNote);
    }

    // 添加远程节点，通常是本地新增后需要上传到云端，或者云端新增后需要下载到本地
    private void addRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);
        Node n;

        // update remotely
        if (sqlNote.isNoteType()) {
            Task task = new Task();
            task.setContentByLocalJSON(sqlNote.getContent());

            String parentGid = mNidToGid.get(sqlNote.getParentId());
            if (parentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot add remote task");
            }
            mGTaskListHashMap.get(parentGid).addChildTask(task);

            GTaskClient.getInstance().createTask(task);
            n = (Node) task;

            // add meta
            updateRemoteMeta(task.getGid(), sqlNote);
        } else {
            TaskList tasklist = null;

            // we need to skip folder if it has already existed
            String folderName = GTaskStringUtils.MIUI_FOLDER_PREFFIX;
            if (sqlNote.getId() == Notes.ID_ROOT_FOLDER)
                folderName += GTaskStringUtils.FOLDER_DEFAULT;
            else if (sqlNote.getId() == Notes.ID_CALL_RECORD_FOLDER)
                folderName += GTaskStringUtils.FOLDER_CALL_NOTE;
            else
                folderName += sqlNote.getSnippet();

            Iterator<Map.Entry<String, TaskList>> iter = mGTaskListHashMap.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, TaskList> entry = iter.next();
                String gid = entry.getKey();
                TaskList list = entry.getValue();

                if (list.getName().equals(folderName)) {
                    tasklist = list;
                    if (mGTaskHashMap.containsKey(gid)) {
                        mGTaskHashMap.remove(gid);
                    }
                    break;
                }
            }

            // no match we can add now
            if (tasklist == null) {
                tasklist = new TaskList();
                tasklist.setContentByLocalJSON(sqlNote.getContent());
                GTaskClient.getInstance().createTaskList(tasklist);
                mGTaskListHashMap.put(tasklist.getGid(), tasklist);
            }
            n = (Node) tasklist;
        }

        // update local note
        sqlNote.setGtaskId(n.getGid());
        sqlNote.commit(false);
        sqlNote.resetLocalModified();
        sqlNote.commit(true);

        // gid-id mapping
        mGidToNid.put(n.getGid(), sqlNote.getId());
        mNidToGid.put(sqlNote.getId(), n.getGid());
    }

    // 更新远程节点，通常是本地修改后需要上传到云端，或者云端修改后需要下载到本地
    private void updateRemoteNode(Node node, Cursor c) throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        SqlNote sqlNote = new SqlNote(mContext, c);

        // update remotely
        node.setContentByLocalJSON(sqlNote.getContent());
        GTaskClient.getInstance().addUpdateNode(node);

        // update meta
        updateRemoteMeta(node.getGid(), sqlNote);

        // move task if necessary
        if (sqlNote.isNoteType()) {
            Task task = (Task) node;
            TaskList preParentList = task.getParent();

            String curParentGid = mNidToGid.get(sqlNote.getParentId());
            if (curParentGid == null) {
                Log.e(TAG, "cannot find task's parent tasklist");
                throw new ActionFailureException("cannot update remote task");
            }
            TaskList curParentList = mGTaskListHashMap.get(curParentGid);

            if (preParentList != curParentList) {
                preParentList.removeChildTask(task);
                curParentList.addChildTask(task);
                GTaskClient.getInstance().moveTask(task, preParentList, curParentList);
            }
        }

        // clear local modified flag
        sqlNote.resetLocalModified();
        sqlNote.commit(true);
    }

    // 更新远程元数据，通常是本地修改后需要上传到云端，或者云端修改后需要下载到本地
    private void updateRemoteMeta(String gid, SqlNote sqlNote) throws NetworkFailureException {
        if (sqlNote != null && sqlNote.isNoteType()) {
            MetaData metaData = mMetaHashMap.get(gid);
            if (metaData != null) {
                metaData.setMeta(gid, sqlNote.getContent());
                GTaskClient.getInstance().addUpdateNode(metaData);
            } else {
                metaData = new MetaData();
                metaData.setMeta(gid, sqlNote.getContent());
                mMetaList.addChildTask(metaData);
                mMetaHashMap.put(gid, metaData);
                GTaskClient.getInstance().createTask(metaData);
            }
        }
    }

    // 刷新本地 Sync ID，确保下次同步时能够正确识别增量变化
    private void refreshLocalSyncId() throws NetworkFailureException {
        if (mCancelled) {
            return;
        }

        // get the latest gtask list
        mGTaskHashMap.clear();
        mGTaskListHashMap.clear();
        mMetaHashMap.clear();
        initGTaskList();

        Cursor c = null;
        try {
            c = mContentResolver.query(Notes.CONTENT_NOTE_URI, SqlNote.PROJECTION_NOTE,
                    "(type<>? AND parent_id<>?)", new String[] {
                            String.valueOf(Notes.TYPE_SYSTEM), String.valueOf(Notes.ID_TRASH_FOLER)
                    }, NoteColumns.TYPE + " DESC");
            if (c != null) {
                while (c.moveToNext()) {
                    String gid = c.getString(SqlNote.GTASK_ID_COLUMN);
                    Node node = mGTaskHashMap.get(gid);
                    if (node != null) {
                        mGTaskHashMap.remove(gid);
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SYNC_ID, node.getLastModified());
                        mContentResolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI,
                                c.getLong(SqlNote.ID_COLUMN)), values, null, null);
                    } else {
                        Log.e(TAG, "something is missed");
                        throw new ActionFailureException(
                                "some local items don't have gid after sync");
                    }
                }
            } else {
                Log.w(TAG, "failed to query local note to refresh sync id");
            }
        } finally {
            if (c != null) {
                c.close();
                c = null;
            }
        }
    }

    // 获取当前用于同步的 Google 账号名称，通常用于显示在 UI 上或者日志中，帮助用户识别正在使用哪个账号进行同步
    public String getSyncAccount() {
        return GTaskClient.getInstance().getSyncAccount().name;
    }

    // 取消同步，通常是用户在 UI 上点击了"取消"按钮，或者系统发出了取消同步的信号，这个方法会被调用，设置 mCancelled 标志，通知正在进行的同步操作停止
    public void cancelSync() {
        mCancelled = true;
    }
}
