package com.overtake.data;

import java.util.Hashtable;


public class DataStatus {

    public DataOnlineStatus onlineStatus;
    public int total;
    public boolean hasMore;
    public Hashtable<String, Object> userInfo;
    public long timestamp;

    public DataStatus() {
        userInfo = new Hashtable<String, Object>();
    }
}
