package com.overtake.data;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import com.overtake.app.*;
import com.overtake.app.Error;
import com.overtake.utils.KLog;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpRequest;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import junit.framework.Assert;

public final class RequestManager {

    public enum NetworkStatus {
        NotReachable, ReachableViaWiFi, ReachableViaMobile,
    }

    private static RequestManager _manager;

    // 存储每个数据分类的观察者列表
    private final HashMap<String, List<RequestObserver>> _dataObservers;

    // 全局的json数据处理勾子
    private final ArrayList<JsonHook> _globalJsonHooks;
    private final ArrayList<RequestHook> _globalRequestHooks;

    private final AsyncHttpClient _client;
    private String _apiUrlPrefix;
    private Context _context;

    public static final int REQUEST_TIMEOUT_STANDARD = 10 * 1000;
    public static final int REQUEST_TIMEOUT_UPLOAD = 10 * 60 * 1000;

    private RequestManager() {
        _dataObservers = new HashMap<String, List<RequestObserver>>();
        _globalJsonHooks = new ArrayList<JsonHook>();
        _globalRequestHooks = new ArrayList<RequestHook>();
        _client = new AsyncHttpClient();
    }

    public void setRequestTimeout(int milliseconds) {
        _client.setTimeout(milliseconds);
    }

    public void setRequestTimeoutStandard() {
        setRequestTimeout(REQUEST_TIMEOUT_STANDARD);
    }

    public void setRequestTimeoutUpload() {
        setRequestTimeout(REQUEST_TIMEOUT_UPLOAD);
    }

    public synchronized static RequestManager getInstance() {
        if (_manager == null) {
            _manager = new RequestManager();
        }

        return _manager;
    }

    public void initialize(String apiPrefix, Context context) {
        _apiUrlPrefix = apiPrefix;
        _context = context;
    }

    public AsyncHttpRequest getRequestForTask(DataTask task) {
        for (AsyncHttpRequest request : _client.httpRequests) {
            DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
            if (dataRequest.task == task && dataRequest.task.category.equals(task.category) && dataRequest.task.requestType == task.requestType) {
                return request;
            }
        }
        return null;
    }

    public boolean addTask(DataTask task) {

        DataModel dataProvider = DataManager.getInstance().getDataForCategory(task.category);
        DataRequest dataRequest = dataProvider.getDataRequestForTask(task);
        if (dataRequest.task == null)
            dataRequest.task = task;

        if (!dataProvider.needRequestDataForTask(task)) {
            notifyRequestSuccessForTask(task);
            return false;
        }


        Assert.assertNotNull("error: query url should not be null!", dataRequest.url);

        if (!dataRequest.url.startsWith("http://") && !dataRequest.url.startsWith("https://")) {
            dataRequest.url = _apiUrlPrefix + dataRequest.url;
        }

        boolean needAdd = true;
        ArrayList<AsyncHttpRequest> removedList = new ArrayList<AsyncHttpRequest>();
        for (AsyncHttpRequest request : _client.httpRequests) {
            if (dataRequest.task.requestType == Consts.DATA_REQUEST_REFRESH) {
                DataRequest requestInQueue = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
                if (requestInQueue.equals(dataRequest)) {
                    Future<?> future = request.futureRef.get();
                    if (future != null) {
                        future.cancel(true);
                        removedList.add(request);
                        KLog.i(this, "remove : " + dataRequest.task.category + "" + dataRequest.task.dataId);
                    }
                }
            }
        }

        for (AsyncHttpRequest request : removedList) {
            _client.httpRequests.remove(request);
        }

        if (needAdd) {
            if (dataRequest.url.startsWith(Consts.DATA_REQUEST_KEY_LOCALHOST)) {

            } else {
                addRequestToQueue(dataRequest);
            }
        }

        return false;
    }

    void addRequestToQueue(DataRequest dataRequest) {
        HashMap<String, String> params = new HashMap<String, String>();

        AsyncHttpRequest request = createRequest(dataRequest, params);
        if (request != null) {
            _client.httpRequests.add(request);
        }
        DataModel data = DataManager.getInstance().getDataForCategory(dataRequest.task.category);
        notifyRequestAdded(dataRequest.task);
    }

    private AsyncHttpRequest createRequest(DataRequest request, HashMap<String, String> paramsMap) {
        RequestParams params = new RequestParams(paramsMap);
        AsyncHttpRequest httpRequest = request.httpMethod == HttpMethod.Get ? createGetRequest(request, params) : createPostRequest(request, params);
        httpRequest.userInfo = new Hashtable<String, Object>();
        httpRequest.userInfo.put(Consts.DATA_REQUEST, request);
        httpRequest.userInfo.put(Consts.DATA_REQUEST_KEY_TIME, String.valueOf(System.currentTimeMillis()));

        return httpRequest;
    }

    private AsyncHttpRequest createGetRequest(DataRequest request, RequestParams params) {

        if (request.requestParams != null) {
            Iterator<Entry<String, String>> iterator = request.requestParams.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                params.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        KLog.i(this, request.url + "?" + params.toString());

        return _client.get(_context, request.url, params, createHandler());
    }

    private AsyncHttpRequest createPostRequest(DataRequest request, RequestParams params) {

        if (request.requestParams != null) {
            Iterator<Entry<String, String>> iterator = request.requestParams.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<String, String> entry = iterator.next();
                params.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        if (request.dataParams != null) {
            Iterator<Entry<String, Object>> iterator = request.dataParams.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<String, Object> entry = iterator.next();
                if (entry.getValue() instanceof File) {
                    try {
                        params.put(entry.getKey(), (File) entry.getValue());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else if (entry.getValue() instanceof InputStream) {
                    params.put(entry.getKey(), (InputStream) entry.getValue());
                }
            }
        }

        if (request.imageParams != null) {
            Iterator<Entry<String, Object>> iterator = request.imageParams.entrySet().iterator();
            while (iterator.hasNext()) {

                Map.Entry<String, Object> entry = iterator.next();
                if (entry.getValue() instanceof File) {
                    try {
                        params.put(entry.getKey(), (File) entry.getValue());

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                } else if (entry.getValue() instanceof String) {
                    File tempFile = new File(entry.getValue().toString());
                    try {
                        params.put(entry.getKey(), tempFile);

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                } else if (entry.getValue() instanceof InputStream) {
                    params.put(entry.getKey(), (InputStream) entry.getValue(), entry.getKey() + ".jpeg", "image/jpeg");
                }
            }
        }

        KLog.i(this, request.url + "?" + params.toString());

        return _client.post(_context, request.url, params, createHandler());
    }

    private AsyncHttpResponseHandler createHandler() {
        return new AsyncHttpResponseHandler() {
            public void onSuccess(String content, AsyncHttpRequest request) {
                RequestManager.this.onSuccess(content, request);
            }

            public void onFailure(Throwable error, String content, AsyncHttpRequest request) {

                RequestManager.this.onFailure(error, content, request);
            }

            public void onProgress(long position, long total, AsyncHttpRequest request) {
                RequestManager.this.onProgress(position, total, request);
            }
        };
    }

    public void notifyRequestSuccessForTask(DataTask task) {

        List<RequestObserver> observers = _dataObservers.get(task.category);
        if (observers == null || observers.size() == 0)
            return;

        for (RequestObserver dataObserver : observers) {
            dataObserver.requestSuccessForTask(task);
        }
    }

    public void notifyRequestFailedForTask(DataTask task, Error error, String content, JsonData rawData) {

        DataModel dataProvider = DataManager.getInstance().getDataForCategory(task.category);
        dataProvider.onError(task, error, content, rawData);

        List<RequestObserver> observers = _dataObservers.get(task.category);
        if (observers == null || observers.size() == 0)
            return;

        for (RequestObserver dataObserver : observers) {
            dataObserver.requestFailedForTask(task, error);
        }
    }

    public void notifyRequestDataModify(DataTask task) {

        List<RequestObserver> observers = _dataObservers.get(task.category);
        if (observers == null || observers.size() == 0)
            return;

        for (RequestObserver dataObserver : observers) {

            dataObserver.requestDataModifyForTask(task);
        }
    }

    public void notifyRequestAdded(DataTask task) {

        List<RequestObserver> observers = _dataObservers.get(task.category);
        if (observers == null || observers.size() == 0)
            return;

        for (RequestObserver dataObserver : observers) {
            dataObserver.taskAddedToRequestManager(task);
        }
    }

    /*
     * 注册/反注册网络请求观察者
     */
    public void registerRequestObserver(RequestObserver observer, String dataCategory) {

        List<RequestObserver> observers = _dataObservers.get(dataCategory);

        if (observers == null) {

            observers = new ArrayList<RequestObserver>();
            _dataObservers.put(dataCategory, observers);
        }

        boolean needAdd = true;
        for (RequestObserver observerInArray : observers) {

            if (observerInArray.equals(observer)) {
                needAdd = false;
                break;
            }
        }

        if (needAdd) {

            observers.add(observer);
        }
    }

    public void unregisterRequestObserver(RequestObserver observer, String dataCategory) {

        List<RequestObserver> observers = _dataObservers.get(dataCategory);

        if (observers != null) {

            observers.remove(observer);
        }
    }

    public void unregisterRequestObserver(RequestObserver observer) {
        Collection<List<RequestObserver>> values = _dataObservers.values();

        for (Iterator iterator = values.iterator(); iterator.hasNext(); ) {
            List<RequestObserver> list = (List<RequestObserver>) iterator.next();
            for (Iterator iterator2 = list.iterator(); iterator2.hasNext(); ) {
                RequestObserver kxRequestObserver = (RequestObserver) iterator2.next();
                if (observer.equals(kxRequestObserver)) {
                    iterator2.remove();
                }
            }
        }
    }

    /*
     * 注册及反注册全局json处理勾子
     */
    public void registerGlobalJsonHook(JsonHook jsonHook) {
        if (_globalJsonHooks.indexOf(jsonHook) == -1)
            _globalJsonHooks.add(jsonHook);
    }

    public void unregisterGlobalJsonHook(JsonHook jsonHook) {
        if (_globalJsonHooks.indexOf(jsonHook) != -1)
            _globalJsonHooks.remove(jsonHook);
    }

    public void clearAllGlobalJsonHooks() {
        _globalJsonHooks.clear();
    }

    /*
 * 注册及反注册全局request处理勾子
 */
    public void registerGlobalRequestHook(RequestHook jsonHook) {
        if (_globalRequestHooks.indexOf(jsonHook) == -1)
            _globalRequestHooks.add(jsonHook);
    }

    public void unregisterGlobalRequestHook(RequestHook jsonHook) {
        if (_globalRequestHooks.indexOf(jsonHook) != -1)
            _globalRequestHooks.remove(jsonHook);
    }

    public void clearAllGlobalRequestHooks() {
        _globalRequestHooks.clear();
    }

    public void cancelAllTasksBySender(Object sender) {
        ArrayList<AsyncHttpRequest> allRequests = _client.httpRequests;
        ArrayList<AsyncHttpRequest> removedList = new ArrayList<AsyncHttpRequest>();
        for (AsyncHttpRequest request : allRequests) {
            DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
            if (dataRequest.task.senders.contains(sender)) {
                dataRequest.task.senders.remove(sender);
                if (dataRequest.task.senders.size() == 0) {
                    Future<?> future = request.futureRef.get();
                    if (future != null) {
                        future.cancel(true);
                    }
                    removedList.add(request);
                }
            }
        }
        for (AsyncHttpRequest request : removedList) {
            _client.httpRequests.remove(request);
        }
    }

    public void cancelAllRequest() {
        ArrayList<AsyncHttpRequest> allRequests = _client.httpRequests;
        ArrayList<AsyncHttpRequest> removedList = new ArrayList<AsyncHttpRequest>();

        for (AsyncHttpRequest request : allRequests) {
            Future<?> future = request.futureRef.get();
            if (future != null) {
                future.cancel(true);
                removedList.add(request);
            }
        }
        for (AsyncHttpRequest request : removedList) {
            _client.httpRequests.remove(request);
            DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
            notifyRequestFailedForTask(dataRequest.task, new Error("KaiXinErrorDomain", "请求已取消", 0), "", new JsonData());
        }
    }


    public void cancelTask(DataTask task, DataTask.ITaskComparator comparator) {
        ArrayList<AsyncHttpRequest> removedList = new ArrayList<AsyncHttpRequest>();
        for (AsyncHttpRequest request : _client.httpRequests) {
            DataRequest requestInQueue = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
            if (comparator != null && comparator.isEquals(task, requestInQueue.task)) {
                Future<?> future = request.futureRef.get();
                if (future != null) {
                    future.cancel(true);
                    removedList.add(request);
                }
            }
        }

        _client.httpRequests.removeAll(removedList);
    }

    public void onProgress(long position, long total, AsyncHttpRequest request) {
        DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
        DataTask task = dataRequest.task;
        if (task.uploadProgressHandler != null) {
            task.uploadProgressHandler.updateProgress(position, total);
        }
    }

    public void onSuccess(String content, AsyncHttpRequest request) {
        DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
        DataModel dataProvider = DataManager.getInstance().getDataForCategory(dataRequest.task.category);
        if (dataProvider == null) {
            _client.httpRequests.remove(request);
        } else {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                new DataPreProcessor(dataProvider, content, request).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                new DataPreProcessor(dataProvider, content, request).execute();
            }

        }
    }

    public void onFailure(Throwable error, String content, AsyncHttpRequest request) {
        DataRequest dataRequest = (DataRequest) request.userInfo.get(Consts.DATA_REQUEST);
        notifyRequestFailedForTask(dataRequest.task, new Error("KaiXinErrorDomain", "网络链接错误", 0), content, new JsonData());
        _client.httpRequests.remove(request);
    }

    public NetworkStatus getNetworkReachableType() {

        ConnectivityManager manager = (ConnectivityManager) _context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = manager.getActiveNetworkInfo();
        if (info != null && info.isConnected()) {

            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                return NetworkStatus.ReachableViaMobile;
            } else {
                return NetworkStatus.ReachableViaWiFi;
            }
        }

        return NetworkStatus.NotReachable;
    }

    public String getApiUrlPrefix() {
        return _apiUrlPrefix;
    }

    // ===========================================================
    // Inner and Anonymous Classes
    // ===========================================================
    private class DataPreProcessor extends AsyncTask<Void, Void, Boolean> {

        private String mContent;
        private AsyncHttpRequest mRequest;
        private DataModel mDataProvider;
        private JsonData rawData;

        public DataPreProcessor(DataModel dataProvider, String content, AsyncHttpRequest request) {
            mDataProvider = dataProvider;
            mContent = content;
            mRequest = request;
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            if (mContent != null && mContent.length() > 0) {
                rawData = JsonData.create(mContent);
            }

            if (rawData != null) {
                KLog.i("request finished", rawData.toString());
            }

            DataRequest dataRequest = (DataRequest) mRequest.userInfo.get(Consts.DATA_REQUEST);
            DataModel dataProvider = DataManager.getInstance().getDataForCategory(dataRequest.task.category);
            if (dataProvider == null) {
                _client.httpRequests.remove(mRequest);
                return false;
            }
            boolean processed = dataProvider.processHttpRequest(rawData, mRequest);
            return processed;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            DataRequest dataRequest = (DataRequest) mRequest.userInfo.get(Consts.DATA_REQUEST);

            JsonData json = rawData.optJson(Consts.DATA_REQUEST_KEY_RESULT);

            //check request hook first
            boolean isGoOn = true;
            for (Iterator<RequestHook> iterator = _globalRequestHooks.iterator(); iterator.hasNext(); ) {
                RequestHook hook = iterator.next();
                if (!hook.globalRequestHook(json, dataRequest.task)) {
                    isGoOn = false;
                }
            }

            if (!isGoOn) return;

            if (result) {
                for (Iterator<JsonHook> iterator = _globalJsonHooks.iterator(); iterator.hasNext(); ) {
                    JsonHook hook = iterator.next();
                    hook.globalJsonHook(json, dataRequest.task);
                }

                notifyRequestSuccessForTask(dataRequest.task);
                mDataProvider.onSucceed(json, dataRequest.task);

            } else {
                final String errorDomain = "KaiXinErrorDomain";
                Error error = null;
                if (rawData == null) {
                    error = new Error(errorDomain, "网络链接错误", 0);
                } else {
                    int ret = json.optInt(Consts.DATA_REQUEST_KEY_RET);
                    if (ret == 0) {
                        error = new Error(errorDomain, "json数据不合法，json:" + json.toString(), 0);
                    } else {
                        error = new com.overtake.app.Error(errorDomain, json.optString(Consts.DATA_REQUEST_KEY_MSG), ret);
                    }
                }
                notifyRequestFailedForTask(dataRequest.task, error, mContent, json);
                mDataProvider.onError(dataRequest.task, error, mContent, json);
            }
            _client.httpRequests.remove(mRequest);
        }
    }
}
