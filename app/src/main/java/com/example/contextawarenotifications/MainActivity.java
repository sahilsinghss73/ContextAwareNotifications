package com.example.contextawarenotifications;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.material.snackbar.Snackbar;

import java.util.Date;
import java.util.HashSet;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    private Button button;
    private static int MY_PERMISSIONS_REQUEST_READ_CALENDAR = 1;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(this);
    }

    @Override
    public void onClick(View v)
    {
        if(v.getId()==button.getId())
        {
            Log.d("here","Button Clicked");
            getCalendarPermission();
        }
    }

    private void getCalendarPermission()
    {
        if(checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_DENIED)
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CALENDAR))
            {
                Snackbar.make((View)findViewById(R.id.main_layout), "Calendar Access required",
                        Snackbar.LENGTH_INDEFINITE).setAction("Ok", new View.OnClickListener() {
                    @Override
                    public void onClick(View view)
                    {
                        // Request the permission
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.READ_CALENDAR},
                                MY_PERMISSIONS_REQUEST_READ_CALENDAR);
                    }
                }).show();
            }
            else
            {
                Snackbar.make((View)findViewById(R.id.main_layout), "Calendar Unavailable", Snackbar.LENGTH_SHORT).show();
                // Request the permission. The result will be received in onRequestPermissionResult().
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_CALENDAR}, MY_PERMISSIONS_REQUEST_READ_CALENDAR);
            }
            Log.d("here","Requesting Permission");
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_CONTACTS}, MY_PERMISSIONS_REQUEST_READ_CALENDAR);
        }
        else
        {
            getCalendarInfo();
        }
    }
    private void getCalendarInfo()
    {
        String[] FIELDS =
                {
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
                };
        Uri CALENDAR_URI = Uri.parse("content://com.android.calendar/calendars");
        ContentResolver contentResolver = this.getContentResolver();
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
                    System.out.println(_id+" "+displayName);
                    calendarIds.add(_id);
                }
            }
        }
        catch (AssertionError ex)
        {
            Log.d("Exception",ex.toString());
        }
        cursor.close();

        for (String id : calendarIds)
        {
            Uri.Builder builder = Uri.parse("content://com.android.calendar/instances/when").buildUpon();
            long now = new Date().getTime();

            ContentUris.appendId(builder, now - DateUtils.DAY_IN_MILLIS * 10000);
            ContentUris.appendId(builder, now + DateUtils.DAY_IN_MILLIS * 10000);

            Cursor eventCursor = contentResolver.query(builder.build(),
                    new String[]  { "title", "begin", "end", "allDay"}, CalendarContract.Events.CALENDAR_ID + " = " + id,
                    null, "startDay ASC, startMinute ASC");

            System.out.println("eventCursor count="+eventCursor.getCount()+" id="+id);
            if(eventCursor.getCount()>0)
            {

                if(eventCursor.moveToFirst())
                {
                    do
                    {

                        final String title = eventCursor.getString(0);
                        final Date begin = new Date(eventCursor.getLong(1));
                        final Date end = new Date(eventCursor.getLong(2));
                        final Boolean allDay = !eventCursor.getString(3).equals("0");

						System.out.println("Title:"+title + " || StartTime:"+begin);
//						System.out.println("End:"+end);
//						System.out.println("All Day:"+allDay);

                    }
                    while(eventCursor.moveToNext());
                }
            }
            cursor.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_CALENDAR)
        {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                getCalendarInfo();
            }
        }
    }
}