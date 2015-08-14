package com.overtake.data;

public interface RequestObserver {

	void requestSuccessForTask(DataTask task);
	void requestFailedForTask(DataTask task, com.overtake.app.Error error);
	
	void requestDataModifyForTask(DataTask task);
	void taskAddedToRequestManager(DataTask task);
}