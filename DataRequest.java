package com.overtake.data;

import java.util.HashMap;

public class DataRequest {

    public String url;
    public HashMap<String, String> requestParams;
    public HashMap<String, Object> dataParams;
    public HashMap<String, Object> imageParams;
    public DataTask task;
    public HttpMethod httpMethod;

    public DataRequest() {

        this.requestParams = new HashMap<String, String>();
        this.dataParams = new HashMap<String, Object>();
        this.imageParams = new HashMap<String, Object>();
    }

    public DataRequest(String url) {

        this.url = url;
    }

    public boolean equals(DataRequest request) {

        if (request == this)
            return true;
        if (request == null)
            return false;

        boolean same = true;
        if (!this.task.category.equals(request.task.category) ||
                this.task.requestType != request.task.requestType ||
                this.task.dataId != request.task.dataId) {

            same = false;
        }

        if (same) {

            same = this.task.args.equals(request.task.args);
        }

        return same;
    }
}
