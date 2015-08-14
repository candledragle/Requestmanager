package com.overtake.data;

import com.overtake.app.JsonData;

public interface JsonHook {

    /**
     * global
     * @param json
     * @param task
     */
	void globalJsonHook(JsonData json, DataTask task);
}
