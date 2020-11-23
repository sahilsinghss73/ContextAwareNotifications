package com.example.contextawarenotifications;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity
{
    private SwitchCompat switchCompat;
    private static int PERMISSIONS_REQUEST_CODE = 1;
    private final String[] PERMISSIONS =
            {
                    Manifest.permission.ACTIVITY_RECOGNITION,
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.ACCESS_NOTIFICATION_POLICY,
                    Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        switchCompat = (SwitchCompat) findViewById(R.id.service_switch);
        switchCompat.setChecked(isMyServiceRunning(NotificationService.class));


        final Intent intent = new Intent(this, NotificationService.class);
        switchCompat.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if(isChecked)
                {
                    intent.putExtra("STOP",false);
                }
                else
                {
                    intent.putExtra("STOP",true);
                }
                startForegroundService(intent);
            }
        });
        checkAndRequestPermissions();

        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!notificationManager.isNotificationPolicyAccessGranted())
        {
            Intent intent1 = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
            startActivity(intent1);
        }

    }

    private boolean isMyServiceRunning(Class<?> serviceClass)
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkAndRequestPermissions()
    {
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String permission : PERMISSIONS)
        {
            if (this.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
            {
                listPermissionsNeeded.add(permission);
            }
        }
        if (!listPermissionsNeeded.isEmpty())
        {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                    PERMISSIONS_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == PERMISSIONS_REQUEST_CODE)
        {
            HashMap<String, Integer> permissionsResults = new HashMap<>();
            int count = 0;
            for (int i = 0; i < grantResults.length; ++i)
            {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                {
                    permissionsResults.put(permissions[i], grantResults[i]);
                    ++count;
                }
            }
            if(count>0)
            {
                for (Map.Entry<String, Integer> entry : permissionsResults.entrySet())
                {
                    String permName = entry.getKey();
                    int permResult = entry.getValue();
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permName))
                    {
                        Snackbar.make((View) findViewById(R.id.main_layout), "This app needs Calendar and Physical Activity permissions to work properly.",
                                Snackbar.LENGTH_INDEFINITE).setAction("Ok", new View.OnClickListener()
                        {
                            @RequiresApi(api = Build.VERSION_CODES.M)
                            @Override
                            public void onClick(View view)
                            {
                                checkAndRequestPermissions();
                            }
                        }).show();
                    }
                    else
                    {
                        String TAG = "ACT_REC";
                        Log.d(TAG, "App could not be initialized.");
                    }
                }
            }
        }
    }
}