package com.overtake.data;

public enum DataOnlineStatus {
	
	Failed, 	// not connect to the network to gain data
	Gained,  	// have been conneted to the network to gain the data
	Cached		// 使用上一次缓存的数据

}
