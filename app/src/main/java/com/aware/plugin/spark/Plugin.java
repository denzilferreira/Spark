package com.aware.plugin.spark;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

/**
 * Created by denzil on 08/10/14.
 */
public class Plugin extends Aware_Plugin {

    private static SharedPreferences settings;
    private Intent aware;

    private long last_check = 0;

    private Handler parkinsonHandler = new Handler();
    private Runnable parkinsonAI = new Runnable() {
        @Override
        public void run() {
            if( (System.currentTimeMillis()-last_check) < Integer.valueOf(Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_SPARK)) * 60 * 1000 ) {
                return;
            }

            last_check = System.currentTimeMillis();

            SharedPreferences.Editor editor = settings.edit();
            editor.putLong("last_time_classify", last_check);
            editor.commit();

            //TODO: Implement here the Parkinson detection on the available data.
            Log.d(TAG,"Analysing Parkinson data every " + Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_SPARK) + " minutes");

            parkinsonHandler.postDelayed(parkinsonAI, Integer.valueOf(Aware.getSetting(getApplicationContext(), Settings.FREQUENCY_SPARK)) * 60 * 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        TAG = "AWARE::Spark";
        if( Aware.DEBUG ) Log.d(TAG, "Spark created!");

        settings = getSharedPreferences("spark", MODE_PRIVATE);

        if( ! settings.contains("participant") ) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("participant", 1);
            editor.commit();
        }
        if( ! settings.contains("task") ) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("task", 1);
            editor.commit();
        }
        if( ! settings.contains("active") ) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("active", 0);
            editor.commit();
        }
        if( ! settings.contains("score") ) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt("score", -1);
            editor.commit();
        }

        if( ! settings.contains(Settings.FREQUENCY_SPARK) ) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(Settings.FREQUENCY_SPARK, 5);
            editor.commit();
            Aware.setSetting(this, Settings.FREQUENCY_SPARK, 5);
        }

        aware = new Intent(this, Aware.class);
        startService(aware);

        if( Aware.getSetting(this, Settings.STATUS_PLUGIN_SPARK).length() == 0 ) {
            Aware.setSetting(this, Settings.STATUS_PLUGIN_SPARK, true);
        }
        if( Aware.getSetting(this, Settings.FREQUENCY_SPARK).length() == 0 ) {
            Aware.setSetting(this, Settings.FREQUENCY_SPARK, 5);
        }

        Aware.setSetting(this, Aware_Preferences.STATUS_ANDROID_WEAR, true);

        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        sendBroadcast(apply);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if( Aware.DEBUG ) Log.d(TAG, "Spark active...");

        //Processing is done on the phone, not watch.
        if( ! Aware.is_watch(this) ) {
            parkinsonHandler.post(parkinsonAI);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if( Aware.DEBUG ) Log.d(TAG, "Spark terminated...");

        Aware.setSetting(this, Aware_Preferences.STATUS_ANDROID_WEAR, false);

        Intent apply = new Intent( Aware.ACTION_AWARE_REFRESH );
        sendBroadcast(apply);

        stopService(aware);
    }
}
