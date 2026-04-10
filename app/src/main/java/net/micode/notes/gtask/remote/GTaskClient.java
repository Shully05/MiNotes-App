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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

//一个访问谷歌任务服务的客户端，它被设计为单例模式，并且这个类中的所有方法都是阻塞的，所以请在工作线程中调用它们。
public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();// "GTaskClient"类的简单名称

    private static final String GTASK_URL = "https://mail.google.com/tasks/";   // 这个URL是谷歌任务服务的基本URL，后续会根据账户类型进行调整

    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig"; // 这个URL用于获取任务列表和任务详情等信息，后续会根据账户类型进行调整

    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";  // 这个URL用于提交任务的创建、更新、删除等操作，后续会根据账户类型进行调整

    private static GTaskClient mInstance = null;    // GTaskClient的单例实例

    private DefaultHttpClient mHttpClient;  // 用于执行HTTP请求的客户端对象

    private String mGetUrl; // 用于获取任务列表和任务详情等信息的URL，初始值为GTASK_GET_URL，后续会根据账户类型进行调整

    private String mPostUrl;    // 用于提交任务的创建、更新、删除等操作的URL，初始值为GTASK_POST_URL，后续会根据账户类型进行调整

    private long mClientVersion;    // 用于标识客户端版本的变量，初始值为-1，登录成功后会从服务器获取实际的版本号

    private boolean mLoggedin;  // 标识是否已经登录成功的变量，初始值为false，登录成功后会设置为true

    private long mLastLoginTime;    // 记录上次登录时间的变量，初始值为0，每次登录成功后会更新为当前时间，用于判断是否需要重新登录

    private int mActionId;  // 用于生成唯一的操作ID的变量，初始值为1，每次生成一个新的操作ID后会自增1

    private Account mAccount;   // 记录当前使用的Google账户的变量，初始值为null，登录成功后会设置为对应的Account对象

    private JSONArray mUpdateArray; // 用于批量提交更新操作的JSONArray对象，初始值为null，每次添加一个更新操作后会将对应的JSON对象添加到这个数组中，当数组中的操作数量超过一定阈值时会自动提交一次更新请求

    // 私有构造函数，初始化成员变量,并且设置默认的URL和登录状态等信息
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    // 获取GTaskClient的单例实例，如果实例不存在则创建一个新的实例并返回，否则直接返回现有的实例
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    // 登录谷歌账户并获取访问谷歌任务服务所需的认证信息，如果已经登录且认证信息未过期则直接返回true，否则重新登录并获取认证信息，登录成功后会更新登录状态和相关变量，并返回true，如果登录失败则返回false
    public boolean login(Activity activity) {
        // we suppose that the cookie would expire after 5 minutes
        // then we need to re-login
        final long interval = 1000 * 60 * 5;
        // 检查cookie是否过期，如果过期，则将登录状态设置为false并重新登录，否则直接返回true。
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        //账户切换后需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                        .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis();
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // login with custom domain if necessary
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // try to login with google official url
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    // 登录谷歌账户并获取认证信息的具体实现方法，首先获取设备上所有的Google账户，并根据设置中保存的账户名称找到对应的Account对象，如果找不到则返回null；然后使用AccountManager获取该账户的认证Token，如果获取失败则返回null；如果获取成功但需要重新验证Token，则调用invalidateAuthToken方法使当前Token失效，并再次调用loginGoogleAccount方法获取新的Token；最后返回获取到的认证Token字符串。
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // get the token now
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    // 尝试使用提供的认证Token登录谷歌任务服务，如果登录失败则可能是Token过期了，这时会使当前Token失效并重新获取一个新的Token，然后再次尝试登录，如果登录成功则返回true，否则返回false。
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // maybe the auth token is out of date, now let's invalidate the
            // token and try again
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    // 使用提供的认证Token登录谷歌任务服务的具体实现方法，首先设置HTTP连接和Socket的超时时间，并创建一个新的DefaultHttpClient对象；然后使用HttpGet请求登录URL，并获取响应；接着从响应中获取Cookie，检查是否包含认证Cookie；最后从响应内容中提取客户端版本号并保存到mClientVersion变量中，如果过程中发生任何异常则返回false，否则返回true。
    private boolean loginGtask(String authToken) {
        int timeoutConnection = 10000;
        int timeoutSocket = 15000;
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        mHttpClient = new DefaultHttpClient(httpParameters);
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // login gtask
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the cookie now
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // get the client version
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // simply catch all exceptions
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    // 生成一个新的操作ID，并将mActionId变量自增1，以确保每次调用这个方法都会返回一个唯一的操作ID。
    private int getActionId() {
        return mActionId++;
    }

    // 创建一个新的HttpPost对象，并设置请求的URL和必要的请求头信息，返回这个HttpPost对象以供后续使用。
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    // 从HTTP响应的实体中读取内容，并根据Content-Encoding头信息进行相应的解压缩处理，最后将解压后的内容转换为字符串并返回，如果过程中发生任何异常则抛出IOException。
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        InputStream input = entity.getContent();
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        } else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }

        try {
            InputStreamReader isr = new InputStreamReader(input);
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();

            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    return sb.toString();
                }
                sb = sb.append(buff);
            }
        } finally {
            input.close();
        }
    }

    // 发送POST请求并返回响应的JSON对象，首先检查是否已经登录，如果没有登录则抛出ActionFailureException异常；然后创建一个HttpPost对象，并将提供的JSON对象作为请求的参数进行编码；接着执行POST请求并获取响应；最后从响应中读取内容并转换为JSON对象返回，如果过程中发生任何异常则根据异常类型抛出相应的NetworkFailureException或ActionFailureException异常。
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // execute the post
            HttpResponse response = mHttpClient.execute(httpPost);
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);

        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    // 创建一个新的任务，首先调用commitUpdate方法提交之前积累的更新操作；
    // 然后构建一个包含创建任务操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后从响应中提取新创建任务的ID并设置到Task对象中，如果过程中发生任何异常则抛出相应的ActionFailureException异常。
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    // 创建一个新的任务列表，首先调用commitUpdate方法提交之前积累的更新操作；
    // 然后构建一个包含创建任务列表操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后从响应中提取新创建任务列表的ID并设置到TaskList对象中，如果过程中发生任何异常则抛出相应的ActionFailureException异常。
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // post
            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    // 提交之前积累的更新操作，如果mUpdateArray不为null，则构建一个包含这些更新操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后将mUpdateArray设置为null以清空积累的更新操作，如果过程中发生任何异常则抛出相应的ActionFailureException异常。
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // action_list
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // client_version
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                postRequest(jsPost);
                mUpdateArray = null;
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    // 添加一个更新操作到mUpdateArray中，
    // 如果mUpdateArray中的操作数量超过10个，则先调用commitUpdate方法提交之前积累的更新操作，
    // 然后再将新的更新操作添加到mUpdateArray中，以避免一次提交过多的更新操作导致请求失败。
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // too many update items may result in an error
            // set max to 10 items
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    // 移动一个任务到新的父任务或新的任务列表中，首先调用commitUpdate方法提交之前积累的更新操作；
    // 然后构建一个包含移动任务操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后如果过程中发生任何异常则抛出相应的ActionFailureException异常。
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());
            if (preParent == curParent && task.getPriorSibling() != null) {
                // put prioring_sibing_id only if moving within the tasklist and
                // it is not the first one
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());
            if (preParent != curParent) {
                // put the dest_list only if moving between tasklists
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    // 删除一个任务或任务列表，首先调用commitUpdate方法提交之前积累的更新操作；
    // 然后构建一个包含删除操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后将mUpdateArray设置为null以清空积累的更新操作，如果过程中发生任何异常则抛出相应的ActionFailureException异常。
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // action_list
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    // 获取所有的任务列表，首先检查是否已经登录，如果没有登录则抛出ActionFailureException异常；
    // 然后使用HttpGet请求获取任务列表的URL，并获取响应；
    // 接着从响应内容中提取包含任务列表信息的JSON字符串，并将其转换为JSONObject对象；
    // 最后从JSONObject对象中提取任务列表的JSONArray并返回，如果过程中发生任何异常则根据异常类型抛出相应的NetworkFailureException或ActionFailureException异常。
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // get the task list
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
        } catch (ClientProtocolException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    // 获取指定任务列表中的所有任务，首先调用commitUpdate方法提交之前积累的更新操作；
    // 然后构建一个包含获取任务列表操作的JSON对象，并将其作为参数调用postRequest方法发送POST请求；
    // 最后从响应中提取任务列表的JSONArray并返回，如果过程中发生任何异常则根据异常类型抛出相应的NetworkFailureException或ActionFailureException异常。
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    // 获取当前使用的Google账户的Account对象，如果没有登录则返回null，否则返回对应的Account对象。
    public Account getSyncAccount() {
        return mAccount;
    }

    // 获取当前客户端版本号，如果没有登录则返回-1，否则返回从服务器获取到的实际版本号。
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}
