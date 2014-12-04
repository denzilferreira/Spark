package com.aware.plugin.spark;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.aware.Aware;
import com.aware.Wear_Service;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;

/**
 * Created by denzil on 08/10/14.
 *
 * Service that receives commands from the phone to control watch's functionality
 */
public class Spark_Wear extends Wear_Service {

    private SharedPreferences settings;

    @Override
    public void onCreate() {
        super.onCreate();
        settings = getSharedPreferences("spark", MODE_PRIVATE);
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d(Spark.TAG, "Data changing on Spark");
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);

        if( messageEvent.getPath().equals("/spark")) {
            Log.d(Spark.TAG, "Message received on Spark");

            String received = new String(messageEvent.getData());
            Log.d(Spark.TAG, "Received from " + (( Aware.is_watch(getApplicationContext()) )?"smartphone":"watch") + " Data = " + received);

            String setting = received.substring(0, received.indexOf(":"));
            String value = received.substring(received.indexOf(":")+1, received.length());

            Log.d(Spark.TAG, "Setting = " + setting + " Value = " + value);

            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(setting, Integer.parseInt(value));
            editor.commit();

            Intent applySetting = new Intent(Spark.ACTION_AWARE_PLUGIN_SPARK);
            applySetting.putExtra(Spark.EXTRA_SETTING, setting);
            applySetting.putExtra(Spark.EXTRA_VALUE, value);
            sendBroadcast(applySetting);
        }

        if( messageEvent.getPath().equals("/flow") ) {
            Intent unlock = new Intent(Spark.ACTION_AWARE_PLUGIN_SPARK_UNLOCK);
            sendBroadcast(unlock);
        }
    }
}
