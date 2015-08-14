package com.overtake.data;

import java.util.HashMap;
import java.util.HashSet;

import com.loopj.android.http.RequestProgressHandler;

public final class DataTask {

    public static interface ITaskComparator {
        public boolean isEquals(DataTask taskA, DataTask taskB);
    }

    public String category;
    public int requestType;
    public long dataId;
    public HashMap<String, String> args;
    public boolean ignoreAuth;
    public HashMap<String, Object> userInfo;
    public HashSet<Object> senders;
    public RequestProgressHandler uploadProgressHandler;

    public DataTask() {
        this.args = new HashMap<String, String>();
        this.userInfo = new HashMap<String, Object>();
        this.senders = new HashSet<Object>();
    }

    public static DataTask createTask(String dataCategory, int requestType, long dataId) {

        return createTask(null, dataCategory, requestType, dataId);
    }

    public static DataTask createTask(Object sender, String dataCategory, int requestType, long dataId) {

        DataTask task = new DataTask();
        if (sender != null) {
            task.senders.add(sender);
        }

        task.category = dataCategory;
        task.requestType = requestType;
        task.dataId = dataId;

        return task;
    }

    public void execute() {
        RequestManager.getInstance().addTask(this);
    }
}
