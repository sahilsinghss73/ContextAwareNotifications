package com.example.contextawarenotifications;

import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    private AudioManager audioManager;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Buttons
        Button silentButton = (Button) findViewById(R.id.silent_button);
        Button vibrateButton = (Button) findViewById(R.id.vibrate_button);
        Button ringerButton = (Button) findViewById(R.id.ringer_button);

        silentButton.setOnClickListener(this);
        vibrateButton.setOnClickListener(this);
        ringerButton.setOnClickListener(this);

        audioManager= (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if(audioManager.isVolumeFixed())
        {
            Log.d("here","Volume Fixed");
        }
        else
        {
            Log.d("here","Volume not Fixed");
        }
    }

    @Override
    public void onClick(View v)
    {
        switch(v.getId())
        {
            //TODO The silent mode has bugs which have to be resolved accordingly.
            case R.id.silent_button:
                Log.d("here","silent");
                NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                if (!notificationManager.isNotificationPolicyAccessGranted())
                {
                    Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
                    startActivity(intent);
                }
                audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                break;
            case R.id.vibrate_button:
                Log.d("here","vibrate");
                audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                break;
            case R.id.ringer_button:
                Log.d("here","ringer");
                audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                break;
        }
    }
}