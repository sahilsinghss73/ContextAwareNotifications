package com.example.contextawarenotifications;

class Constants
{

	//REFRESH RATES & CACHE
	public static final long CACHE_INVALID_TIME = 600000;   //in millis (10 mins) [time after which cache is invalidated]
	public static final long CONTEXT_REFRESH_TIME = 10000;
	public static final long LOCATION_REFRESH_TIME = 10000; //(10 secs)


	//NETWORKING RETRY TIMES
	public static final int VOLLEY_RETRY_REQUEST = 60000;
}
