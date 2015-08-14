package com.overtake.data;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

import junit.framework.Assert;

import org.json.simple.JSONValue;

import com.overtake.app.*;
import com.overtake.utils.FileUtil;
import com.overtake.utils.KLog;
import com.overtake.utils.Utils;
import com.loopj.android.http.AsyncHttpRequest;

public abstract class DataModel {

    private static final boolean DEBUG = false;

    private final HashMap<String, Object> _data;
    private final HashMap<String, DataStatus> _dataStatus;
    private final String _dataCategory;

    public Hashtable<String, Object> args;

    public DataModel() {
        _data = new HashMap<String, Object>();
        _dataStatus = new HashMap<String, DataStatus>();
        _dataCategory = this.getClass().getSimpleName();
        loadAllCache();
    }

    public static DataModel getInstance(Class clz) {
        DataModel data = DataManager.getInstance().getDataForCategory(clz.getSimpleName());
        return data;
    }

    public String getDataCategory() {
        return _dataCategory;
    }

    public String getDataId(long dataId) {
        return String.format("%d", dataId);
    }

    /*
     * 如果是数据是数组就返回ArrayList.class, 如果是字典则返回HashMap.class
     */
    public abstract Class<?> getDataObjectClassForDataId(long dataId);


    /**
     * 处理列表数据，则应该重载这个方法
     *
     * @param dataId
     * @return
     */
    public JsonData getListData(long dataId) {
        Assert.assertEquals("this should be override", true, false);
        return null;
    }

    public Class<?> getDataStatusClassForDataId(long dataId) {
        // 暂时只支持DataStatus
        return DataStatus.class;
    }

    public DataStatus getDataStatusForDataId(long dataId) {

        DataStatus dataStatus = _dataStatus.get(this.getDataId(dataId));

        if (dataStatus == null) {

            Class<?> clazz = this.getDataStatusClassForDataId(dataId);

            if (clazz.isAssignableFrom(DataStatus.class)) {

                dataStatus = new DataStatus();

            } else {

                try {
                    dataStatus = (DataStatus) clazz.newInstance();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            //try to fill data status in init
            _dataStatus.put(this.getDataId(dataId), dataStatus);
        }

        return dataStatus;
    }

    public String getUserId() {

        return null;
    }

    public boolean isAutoCache() {

        return false;
    }

    public boolean isAutoCacheForTask(DataTask task) {

        if (task.requestType == Consts.DATA_REQUEST_REFRESH) {

            return true;
        }

        return false;
    }

    public String getBaseDir() {

        Assert.assertNotNull("this should be config according to project", null);

        return FileUtil.getDocumentDirectory("/ikinder");
    }

    public String getCacheDir() {

        String userId = this.getUserId();

        if (Utils.isNullOrEmpty(userId))
            return null;

        return String.format("%s/%s/%s/", this.getBaseDir(), userId, _dataCategory);
    }

    public void clearCacheForDataId(long dataId) {

        _dataStatus.remove(this.getDataId(dataId));
        _data.remove(this.getDataId(dataId));

        String cacheDir = this.getCacheDir();
        if (!Utils.isNullOrEmpty(cacheDir)) {

            cacheDir = String.format("%s%d.dat", cacheDir, dataId);
            FileUtil.removeFile(cacheDir);
        }
    }

    public void clearAllCache() {

        String cacheDir = this.getCacheDir();

        if (!Utils.isNullOrEmpty(cacheDir)) {

            FileUtil.removeFolder(cacheDir);
        }

        _dataStatus.clear();
        _data.clear();
    }

    public void saveCacheForDataId(long dataId) {

        String cacheDir = this.getCacheDir();

        if (Utils.isNullOrEmpty(cacheDir))
            return;

        FileUtil.createFolder(cacheDir);
        final String filePath = String.format("%s%d.dat", cacheDir, dataId);
        KLog.i(this, filePath);

        final Object data = _data.get(this.getDataId(dataId));
        DataManager.getInstance().getThreadPool().submit(new Runnable() {

            @Override
            public void run() {
                String json = JSONValue.toJSONString(data);
                if (!FileUtil.writeStringToFile(json, filePath)) {
                    KLog.i(this, "write cache failed!!!");
                }
            }
        });
    }

    public void loadAllCache() {
        _data.clear();
        String userId = this.getUserId();

        if (Utils.isNullOrEmpty(userId))
            return;

        String cacheDir = String.format("%s/%s/%s/", this.getBaseDir(), userId, _dataCategory);
        File[] files = new File(cacheDir).listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File arg0, String arg1) {

                return arg1.toLowerCase().endsWith(".dat");
            }
        });

        if (files == null || files.length == 0)
            return;

        for (File file : files) {

            long dataId = Long.parseLong(FileUtil.getFileNameWithoutExtension(file));

            String dataCacheDir = String.format("%s/%d.dat", cacheDir, dataId);
            Object data = JSONValue.parse(FileUtil.readStringFromFile(dataCacheDir));

            if (data != null) {
                _data.put(getDataId(dataId), data);
            }
        }
    }

    public JsonData getJsonDataForDataId(long dataId) {

        Object data = _data.get(this.getDataId(dataId));

        if (data == null) {

            try {

                data = this.getDataObjectClassForDataId(dataId).newInstance();
                _data.put(this.getDataId(dataId), data);

            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return JsonData.create(data);
    }

    public int getDataCountForDataId(long dataId) {

        return this.getJsonDataForDataId(dataId).length();
    }

    public boolean canGetMoreForDataId(long dataId) {

        return this.getDataStatusForDataId(dataId).hasMore;
    }

    public int getPageSize() {

        return Consts.DATA_PAGE_SIZE_DEFAULT;
    }

    /*
     * 生成网络请求的 KXDataRequest, 子类需重写返回符合需求的 KXDataRequest
     */
    public DataRequest getDataRequestForTask(DataTask task) {

        DataRequest request = new DataRequest();
        request.task = task;
        request.httpMethod = HttpMethod.Get;

        return request;
    }

    /*
     * 针对任务设置缓存时间(秒)，两次请求的时候超过这个时间，则发请求
     */
    public int cacheIntervalForTask(DataTask task) {

        return 0;
    }

    public boolean needRequestDataForTask(DataTask task) {

        if (!isAutoCache() || !isAutoCacheForTask(task) || getDataCountForDataId(task.dataId) == 0) {
            return true;
        }

        DataStatus status = getDataStatusForDataId(task.dataId);
        long now = System.currentTimeMillis();

        if (now - status.timestamp < cacheIntervalForTask(task) * 1000) {

            status.onlineStatus = DataOnlineStatus.Cached;
            return false;
        }

        return true;
    }

    public boolean processHttpRequest(JsonData rawData, AsyncHttpRequest request) {

        boolean processed = true;
        DataTask task = ((DataRequest) request.userInfo.get(Consts.DATA_REQUEST)).task;

        if (rawData == null || rawData.getRawData() == null) {
            return false;
        } else {
            processed = processRawDataJson(rawData, task);
        }

        if (processed) {

            DataStatus dataStatus = getDataStatusForDataId(task.dataId);
            dataStatus.onlineStatus = DataOnlineStatus.Gained;
            dataStatus.timestamp = System.currentTimeMillis();
            _dataStatus.put(getDataId(task.dataId), dataStatus);

            if (isAutoCache() && isAutoCacheForTask(task)) {

                saveCacheForDataId(task.dataId);
            }
        }

        return processed;
    }

    public boolean processRawDataJson(JsonData rawData, DataTask task) {

        if (rawData == null)
            return false;

        JsonData json = rawData.optJson(Consts.DATA_REQUEST_KEY_RESULT);
        if (json.optInt(Consts.DATA_REQUEST_KEY_RET) != Consts.DATA_REQUEST_RET_CODE_SUCCESS) {

            return false;
        }

        boolean processed = processJson(json, task);
        if (DEBUG)
            KLog.i(this, "processed:" + processed + "task.requestType" + task.requestType);

        if (processed && (task.requestType == Consts.DATA_REQUEST_REFRESH || task.requestType == Consts.DATA_REQUEST_GET_MORE)) {
            if (DEBUG)
                KLog.i(this, "before fill status");

            fillDataStatus(json, task.dataId);
        }

        return processed;
    }

    protected void fillDataStatus(JsonData json, long dataId) {
        if (json.has(Consts.DATA_REQUEST_KEY_TOTAL)) {
            this.getDataStatusForDataId(dataId).total = json.optInt(Consts.DATA_REQUEST_KEY_TOTAL);
        }

        if (json.has(Consts.DATA_REQUEST_KEY_HAS_MORE)) {
            this.getDataStatusForDataId(dataId).hasMore = json.optBoolean(Consts.DATA_REQUEST_KEY_HAS_MORE);
        }
    }

    public boolean processJson(JsonData json, DataTask task) {
        if (json.has(Consts.DATA_REQUEST_KEY_TOTAL)) {
            DataStatus status = getDataStatusForDataId(task.dataId);
            status.total = json.optInt(Consts.DATA_REQUEST_KEY_TOTAL);
        }
        return true;
    }

    public void onError(DataTask task, com.overtake.app.Error error, String content, JsonData rawData) {

    }

    public void onSucceed(JsonData rawData, DataTask task) {

    }
}