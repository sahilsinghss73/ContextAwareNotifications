package com.example.contextawarenotifications;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.icu.util.Calendar;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ResultReceiver;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.max;

public class NotificationService extends Service
{
	private PendingIntent mActivityPendingIntent;
	private ActivityUpdateReceiver mUpdateReceiver;
	private final String TAG = "ACT_REC";
	private final int period = 0;
	private AudioManager audioManager;

	private final String ACTIVITY_RECOGNITION_ACTION = BuildConfig.APPLICATION_ID + "ACTIVITY_RECOGNITION_ACTION";

	private DetectedActivity activity;
	private long activityUpdateTime;

	LocationCallback locationCallback;
	private String locationType;
	private long locationUpdateTime;
	private Set<String> restrictedLocations = new HashSet<String>();


	private Timer locationTimer;
	private Timer calendarTimer;

	public NotificationService()
	{
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent.getExtras().getBoolean("STOP"))
		{
			onDestroy();
		}
		createNotificationChannel();
		initApp();

		Intent intent1 = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent1, 0);
		Notification notification = new NotificationCompat.Builder(this, "ChannelId")
				.setContentTitle("Actively Tracking Activity & Calendar")
				.setContentText("My Text")
				.setSmallIcon(R.mipmap.ic_launcher_round)
				.setContentIntent(pendingIntent).build();

		startForeground(1, notification);
		refreshCalendarInfo();
		getCurrentLocation();
		return START_STICKY;
	}

	private void initApp()
	{
		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		Intent intent = new Intent(ACTIVITY_RECOGNITION_ACTION);
		mActivityPendingIntent = PendingIntent.getBroadcast(NotificationService.this, 0, intent, 0);
		mUpdateReceiver = new ActivityUpdateReceiver();
		registerReceiver(mUpdateReceiver, new IntentFilter(ACTIVITY_RECOGNITION_ACTION));
		enableActivityUpdates(this);

		restrictedLocations.add("hospital");
		restrictedLocations.add("bank");
		restrictedLocations.add("industrial");
		restrictedLocations.add("college");
		restrictedLocations.add("university");
		restrictedLocations.add("restaurant");
	}

	private void createNotificationChannel()
	{
		NotificationChannel notificationChannel = new NotificationChannel("ChannelId", "Foreground Notification", NotificationManager.IMPORTANCE_LOW);
		NotificationManager notificationManager = getSystemService(NotificationManager.class);
		notificationManager.createNotificationChannel(notificationChannel);
	}

	@Override
	public IBinder onBind(Intent intent)
	{
		// TODO: Return the communication channel to the service.
		throw new UnsupportedOperationException("Not yet implemented");
	}

	@Override
	public void onDestroy()
	{
		LocationServices.getFusedLocationProviderClient(NotificationService.this).removeLocationUpdates(locationCallback);
		disableActivityUpdates(this);
		if(calendarTimer != null)
		{
			calendarTimer.cancel();
		}
//		if(locationTimer != null)
//		{
//			locationTimer.cancel();
//		}
		stopForeground(true);
		unregisterReceiver(mUpdateReceiver);
		stopSelf();
		super.onDestroy();
	}

	private void disableActivityUpdates(final Context context)
	{
		Task<Void> task = ActivityRecognition.getClient(context)
				.removeActivityUpdates(mActivityPendingIntent);
		task.addOnSuccessListener(new OnSuccessListener<Void>()
		{
			@Override
			public void onSuccess(Void aVoid)
			{
				Log.d(TAG, "Activity Tracking stopped.");
			}
		});
		task.addOnFailureListener(new OnFailureListener()
		{
			@Override
			public void onFailure(@NonNull Exception e)
			{
				Log.d(TAG, "Activity Tracking could not be stopped: " + e);
			}
		});
	}


	public class ActivityUpdateReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (!TextUtils.equals(ACTIVITY_RECOGNITION_ACTION, intent.getAction()))
			{
				Log.d(TAG, "Unsupported Action: " + intent.getAction());
				return;
			}

			// Extract activity transition information from listener.
			if (ActivityRecognitionResult.hasResult(intent))
			{
				ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
				activity = result.getMostProbableActivity();
				activityUpdateTime = System.currentTimeMillis();
			} else
			{
				Log.d(TAG, "No Result.");
			}
		}
	}

	private void enableActivityUpdates(final Context context)
	{
		Task<Void> task = ActivityRecognition.getClient(context)
				.requestActivityUpdates(Constants.ACTIVITY_REFRESH_TIME, mActivityPendingIntent);

		task.addOnSuccessListener(
				new OnSuccessListener<Void>()
				{
					@Override
					public void onSuccess(Void result)
					{
						Log.d(TAG, "Activity Tracking started.");
					}
				});
		task.addOnFailureListener(
				new OnFailureListener()
				{
					@Override
					public void onFailure(@NonNull Exception e)
					{
						Log.d(TAG, "Activity Tracking could not be started: " + e);
					}
				});
	}

	private class getCalendarInfo extends AsyncTask<Void, Void, List<String>>
	{
		@RequiresApi(api = Build.VERSION_CODES.N)
		@Override
		protected List<String> doInBackground(Void... voids)
		{
			String[] FIELDS =
					{
							CalendarContract.Calendars._ID,
							CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
					};
			Uri CALENDAR_URI = Uri.parse("content://com.android.calendar/calendars");
			ContentResolver contentResolver = NotificationService.this.getContentResolver();
			HashSet<String> calendarIds = new HashSet<String>();
			Cursor cursor = contentResolver.query(CALENDAR_URI, FIELDS, null, null, null);

			try
			{
				if (cursor.getCount() > 0)
				{
					while (cursor.moveToNext())
					{
						String _id = cursor.getString(cursor.getColumnIndex(CalendarContract.Calendars._ID));
						String displayName = cursor.getString(cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME));
						System.out.println(_id + " " + displayName);
						calendarIds.add(_id);
					}
				}
			} catch (AssertionError ex)
			{
				Log.d("Exception", ex.toString());
			}
			cursor.close();
			List<String> events = new ArrayList<String>();
			for (String id : calendarIds)
			{
				Uri.Builder builder = Uri.parse("content://com.android.calendar/instances/when").buildUpon();
				long now = new Date().getTime();

				ContentUris.appendId(builder, now - DateUtils.DAY_IN_MILLIS * 10);
				ContentUris.appendId(builder, now + DateUtils.DAY_IN_MILLIS * 10);//+- 10 Days

				Cursor eventCursor = contentResolver.query(builder.build(),
						new String[]{"title", "begin", "end", "allDay"}, CalendarContract.Events.CALENDAR_ID + " = " + id,
						null, "startDay ASC, startMinute ASC");

				if (eventCursor.getCount() > 0)
				{
					if (eventCursor.moveToFirst())
					{
						do
						{
							final String title = eventCursor.getString(0);
							final Date begin = new Date(eventCursor.getLong(1));
							final Date end = new Date(eventCursor.getLong(2));
							final Boolean allDay = !eventCursor.getString(3).equals("0");

							System.out.println("Title:" + title + " || StartTime:" + begin + " || EndTime:" + end);
							Date currentDate = Calendar.getInstance().getTime();

							long duration = end.getTime() - begin.getTime();
							if (currentDate.after(begin) && currentDate.before(end) && duration < 7200000)//duration is less than 2 hrs
							{
								events.add(title);
							}
						}
						while (eventCursor.moveToNext());
					}
				}
				eventCursor.close();
			}
			return events;
		}

		@RequiresApi(api = Build.VERSION_CODES.O)
		@Override
		protected void onPostExecute(List<String> calendarEvents)
		{
			StringBuilder sb = new StringBuilder();
			int level = 0;

			if (calendarEvents != null && calendarEvents.size() > 0)
			{
				//TODO check if any title in 'calendarEvents' corresponds to a meeting
				sb.append("In meeting");
				level = 2;
			}
			else
			{
				sb.append("No meetings");
			}

			if(activity != null && System.currentTimeMillis()-activityUpdateTime <= Constants.CACHE_INVALID_TIME )
			{
				switch(activity.getType())
				{
					case DetectedActivity.STILL:
						sb.append(" | Still");
						break;
					case DetectedActivity.IN_VEHICLE:
						sb.append(" | Driving");
						level = max(level, 1);
						break;
					case DetectedActivity.ON_BICYCLE:
						sb.append(" | Cycling");
						level = max(level, 1);
						break;
					case DetectedActivity.ON_FOOT:
					case DetectedActivity.WALKING:
						sb.append(" | Walking");
						level = max(level, 1);
						break;
					case DetectedActivity.RUNNING:
						sb.append(" | Running");
						level = max(level, 1);
						break;
					default:
						sb.append(" | Unknown");
						break;
				}
			}

			if(locationType != null && System.currentTimeMillis()-locationUpdateTime <= Constants.CACHE_INVALID_TIME )
			{
				Log.d("MY_LOC", locationType);
				if(restrictedLocations.contains(locationType))
				{
					level = max(level, 1);
				}
				sb.append(" | At a "+locationType);
			}

			if (level == 0) {
				ringerMode();
			}
			else if (level == 1) {
				vibrationMode();
			}
			else {
				silentMode();
			}

			Intent intent1 = new Intent(getApplicationContext(), MainActivity.class);
			PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent1, 0);
			Notification notification = new NotificationCompat.Builder(getApplicationContext(), "ChannelId")
					.setContentTitle("Actively Tracking Activity & Calendar")
					.setContentText(sb.toString())
					.setSmallIcon(R.mipmap.ic_launcher_round)
					.setContentIntent(pendingIntent).build();
			startForeground(1, notification);
			Log.d(TAG, sb.toString());
			Log.d(TAG, getApplicationContext().toString());
		}
	}

	private void silentMode()
	{
		Log.d("here", "silent");
		audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
	}

	private void vibrationMode()
	{
		Log.d("here", "vibration");
		audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
	}

	private void ringerMode()
	{
		Log.d("here", "ringer");
		audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
	}

	private void refreshCalendarInfo()
	{
		final Handler handler = new Handler();
		calendarTimer = new Timer();
		TimerTask task = new TimerTask()
		{
			@Override
			public void run()
			{
				handler.post(new Runnable()
				{
					@RequiresApi(api = Build.VERSION_CODES.N)
					public void run()
					{
						new getCalendarInfo().execute();
					}
				});
			}
		};
		calendarTimer.schedule(task, 0, Constants.CONTEXT_REFRESH_TIME);//request for new Calendar info every 10 secs;
	}

	private void getCurrentLocation()
	{
		LocationRequest locationRequest = new LocationRequest();
		locationRequest.setInterval(Constants.LOCATION_REFRESH_TIME);
		locationRequest.setFastestInterval(Constants.LOCATION_REFRESH_TIME/2);
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

		if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			Intent dialogIntent = new Intent(NotificationService.this, MainActivity.class);
			dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(dialogIntent);
			return ;
		}
		locationCallback = new LocationCallback()
		{
			@Override
			public void onLocationResult(LocationResult locationResult)
			{
				super.onLocationResult(locationResult);

				if(locationResult != null && locationResult.getLocations().size()>0)
				{
					int latestLocationIndex = locationResult.getLocations().size()-1;
					double latitude = locationResult.getLocations().get(latestLocationIndex).getLatitude();
					double longitude = locationResult.getLocations().get(latestLocationIndex).getLongitude();
					fetchAddressFromLatLong(latitude,longitude);
					Log.d("coordinates",String.format("%s %s",latitude,longitude));
				}
			}
		};
		LocationServices.getFusedLocationProviderClient(NotificationService.this)
				.requestLocationUpdates(locationRequest,locationCallback,Looper.getMainLooper());
	}


	private void fetchAddressFromLatLong(double latitude,double longitude)
	{
		//TODO access location type by making a volley request. and update 'time' & 'type';
		String URL = String.format("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%s&lon=%s",latitude,longitude);
		Log.d("here","1122");
		Log.d("coordinates2",String.format("%s %s",latitude,longitude));
		JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
				(Request.Method.GET, URL, null, new Response.Listener<JSONObject>()
				{
					@Override
					public void onResponse(JSONObject response)
					{
						Log.d("ResponseMessage: " , response.toString());
						try
						{
							locationType = response.getString("type");
							locationUpdateTime = System.currentTimeMillis();
							Log.d("locationtype",locationType);
						}
						catch (JSONException e)
						{
							e.printStackTrace();
						}

					}
				}, new Response.ErrorListener()
				{
					@Override
					public void onErrorResponse(VolleyError error)
					{
						// TODO: Handle error
						Log.d("MY_LOC", error.toString());
					}
				}){
			@Override
			public Request.Priority getPriority()
			{
				return Request.Priority.IMMEDIATE;
			}
		};

		VolleyController.getInstance(this).addToRequestQueueWithRetry(jsonObjectRequest);
	}
}