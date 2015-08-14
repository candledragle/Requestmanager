package com.overtake.data;

import com.overtake.app.JsonData;

/**
 * Created by kevinhoo on 14-3-19.
 */
public interface RequestHook {

    /**
     * global hook for request
     *
     * @param json
     * @param task
     * @return false means **skip** data process, usually to use to to something before all
     */
    boolean globalRequestHook(JsonData json, DataTask task);
}
