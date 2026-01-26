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


/**     
  * 
  * @Package:        net.micode.notes.gtask.remote
  * @ClassName:      GTaskClient
  * @Description:    1. 封装与Google Task服务端的所有HTTP通信
 *                   2. 处理用户认证、登录和会话管理
 *                   3. 提供任务和任务列表的增删改查API
 *                   4. 管理请求的动作ID和批量更新
 */
public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    private static final String GTASK_URL = "https://mail.google.com/tasks/";  // Google Task基础URL

    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";  // GET请求URL

    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";  // POST请求URL

    private static GTaskClient mInstance = null;

    private DefaultHttpClient mHttpClient;        // HTTP客户端，用于执行网络请求
    private String mGetUrl;                       // 当前使用的GET请求URL
    private String mPostUrl;                      // 当前使用的POST请求URL
    private long mClientVersion;                  // 客户端版本号
    private boolean mLoggedin;                    // 登录状态标记
    private long mLastLoginTime;                  // 上次登录时间，用于会话超时控制
    private int mActionId;                        // 动作ID计数器，确保每个请求有唯一ID
    private Account mAccount;                     // 当前同步的Google账户
    private JSONArray mUpdateArray;               // 批量更新数组，用于缓存更新操作


    // 构造函数：实现单例模式

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

    //获取单例实例
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录Google Task服务
     * @param activity  用于获取账户授权令牌
     * @return
     */
    public boolean login(Activity activity) {
        // 检查会话超时：假设Cookie在5分钟后过期
        final long interval = 1000 * 60 * 5;  // 5分钟
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        // 检查账户切换：如果当前账户与设置中的账户不同，需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        // 如果已经登录，直接返回成功
        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        // 记录本次登录时间
        mLastLoginTime = System.currentTimeMillis();

        // 获取Google账户授权令牌
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // 尝试使用自定义域名登录（针对非Gmail用户）
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

        // 如果自定义域名登录失败，尝试使用官方Google域名登录
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

    
    /**
     * @method  loginGoogleAccount
     * @description 登录Google账户并获取授权令牌
     *
     *  执行流程：
     *  1. 获取设备上的所有Google账户
     *  2. 根据设置中的账户名称匹配账户
     *  3. 使用AccountManager获取授权令牌
     *  4. 可选择使令牌失效并重新获取（用于令牌过期情况）
     *
     * @param activity
     * @param invalidateToken 是否使当前令牌失效并重新获取
     * @return String 授权令牌字符串，获取失败返回null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);// 获取账户管理器
        Account[] accounts = accountManager.getAccountsByType("com.google");// 获取所有Google账户

        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 根据设置中的账户名称查找对应账户
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

        // 获取授权令牌
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);

            // 如果令牌失效，重新获取
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


    /**
     * @method  tryToLoginGtask
     * @description 描尝试登录Google Task服务
     * 执行流程：
     * 1. 使用授权令牌登录Google Task
     * 2. 如果失败，可能是令牌过期，使令牌失效后重新获取并重试
     *
     * @param activity
     * @param authToken 授权令牌
     * @return
     */ 
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // 令牌可能过期，使令牌失效并重新获取
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


    /**
     * @method  loginGtask
     * @description 使用授权令牌登录Google Task服务
     * 执行流程：
     * 1. 配置HTTP客户端参数（连接超时、Socket超时）
     * 2. 设置Cookie存储
     * 3. 构建登录URL并发送GET请求
     * 4. 验证返回的Cookie中是否包含认证Cookie（GTL）
     * 5. 解析响应内容，获取客户端版本号
     *
     * @param authToken 授权令牌
     * @return true登录成功，false登录失败
     */
    private boolean loginGtask(String authToken) {
        // 配置HTTP客户端参数
        int timeoutConnection = 10000;    // 连接超时10秒
        int timeoutSocket = 15000;        // Socket超时15秒
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        mHttpClient = new DefaultHttpClient(httpParameters);

        // 设置Cookie存储
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // 登录Google Task
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 检查认证Cookie是否获取成功
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

            // 解析响应内容，获取客户端版本号
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
            // 捕获所有异常，确保稳定性
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    //获取下一个动作ID
    private int getActionId() {
        return mActionId++;
    }

    //创建HTTP POST请求对象
    private HttpPost createHttpPost() {
        HttpPost httpPost = new HttpPost(mPostUrl);
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");  // AT=1表示使用Cookie认证
        return httpPost;
    }

    /**
     * @method  getResponseContent
     * @description 从HttpEntity获取响应内容
     * @param entity HTTP响应实体
     * @return 响应内容字符串
     * @throws IOException 读取响应内容时发生IO异常
     */
    private String getResponseContent(HttpEntity entity) throws IOException {
        String contentEncoding = null;
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }

        InputStream input = entity.getContent();
        // 根据压缩编码选择对应的解压流
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
            input.close();  // 确保流被关闭
        }
    }

    /**
     *
    /**
     * @method  postRequest
     * @description 发送POST请求到Google Task服务
     * 执行流程：
     * 1. 验证登录状态
     * 2. 创建HttpPost请求
     * 3. 将JSON对象编码为表单参数
     * 4. 执行请求并获取响应
     * 5. 解析响应内容为JSON对象
     *
     * @param js 要发送的JSON对象
     * @return 服务器响应的JSON对象
     * @throws NetworkFailureException 网络请求失败
     * @throws ActionFailureException JSON解析或处理失败
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        HttpPost httpPost = createHttpPost();
        try {
            // 将JSON对象编码为表单参数
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            list.add(new BasicNameValuePair("r", js.toString()));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            httpPost.setEntity(entity);

            // 执行POST请求
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

    /**
     * @method  createTask
     * @description 创建任务
     * 执行流程：
     * 1. 提交已有的批量更新
     * 2. 构建创建任务的JSON请求
     * 3. 发送请求到服务器
     * 4. 从响应中提取新任务的ID并设置到任务对象
     *
     * @param task 要创建的任务对象
     * @throws NetworkFailureException 网络请求失败
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate();  // 先提交已有的批量更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 构建动作列表：包含创建任务的动作
            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并处理响应
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


    /**
     * @method  createTaskList
     * @description 创建任务列表
     * 执行流程：
     * 1. 提交已有的批量更新
     * 2. 构建创建任务列表的JSON请求
     * 3. 发送请求到服务器
     * 4. 从响应中提取新任务列表的ID并设置到任务列表对象
     *
     * @param tasklist 要创建的任务列表对象
     * @throws NetworkFailureException 网络请求失败
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();  // 先提交已有的批量更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 构建动作列表：包含创建任务列表的动作
            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并处理响应
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


    //提交批量更新
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();

                // 添加缓存的更新动作列表
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // 添加客户端版本号
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                // 发送请求
                postRequest(jsPost);
                mUpdateArray = null;  // 清空更新数组
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }


    //将节点的更新操作添加到批量更新数组中
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 优化：更新项太多可能导致错误，设置最大为10项
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            // 初始化更新数组并添加更新动作
            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * @method  moveTask
     * @description 将任务从一个任务列表移动到另一个任务列表
     * @param task 要移动的任务
     * @param preParent 移动前的父任务列表
     * @param curParent 移动后的父任务列表
     * @throws NetworkFailureException 网络请求失败
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();  // 先提交已有的批量更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 构建移动动作
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());

            // 在同一任务列表内移动且不是第一个时，设置前一个兄弟节点ID
            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }

            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());

            // 在不同任务列表之间移动时，设置目标列表ID
            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }

            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }


    //Google Task中删除节点
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();  // 先提交已有的批量更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            // 构建删除动作
            node.setDeleted(true);
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;  // 清空更新数组
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }


    //获取当前账户下的所有任务列表
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 解析响应内容，提取任务列表信息
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

    //获取特定任务列表下的所有任务
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();  // 先提交已有的批量更新
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 构建获取所有任务的动作
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);  // 不获取已删除的任务
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 添加客户端版本号
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送请求并获取响应
            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    //获取当前同步账户
    public Account getSyncAccount() {
        return mAccount;
    }

    //重置更新数组
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}