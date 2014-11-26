package com.aware.plugin.spark;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aware.Accelerometer;
import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Accelerometer_Provider;
import com.aware.providers.Battery_Provider;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

public class Spark extends Activity {

    private static Button decrease_participant, decrease_task, increase_participant, increase_task, activator;
    private static TextView participants, tasks, watch_battery, timer, watch_label, task_label, frequency;
    private static SharedPreferences settings;
    private static RelativeLayout watch_main;

    public static final String ACTION_AWARE_PLUGIN_SPARK = "ACTION_AWARE_PLUGIN_SPARK";
    public static final String EXTRA_SETTING = "setting";
    public static final String EXTRA_VALUE = "value";

    private GoogleApiClient googleClient;
    private Node peer;

    public static final String TAG = "AWARE::Spark";

    private static final int WATCH_SAMPLING = 0;

    private static long start_task = 0;
    private static Context sContext;

    private static Handler timerTask = new Handler();
    private static final Runnable refreshTime = new Runnable() {
        @Override
        public void run() {
            if( start_task != 0 ) {
                if( timer != null ) timer.setText(DateUtils.formatElapsedTime((System.currentTimeMillis()-start_task)/1000));
            } else {
                if( timer != null ) timer.setText(DateUtils.formatElapsedTime(0));
            }
            timerTask.postDelayed(refreshTime, 1000);
        }
    };

    private static Handler frequencyChecker = new Handler();
    private static final Runnable frequencyCheck = new Runnable() {
        @Override
        public void run() {
            if( frequency != null ) frequency.setText(Accelerometer.getFrequency(sContext) + " Hz");
            frequencyChecker.postDelayed(frequencyCheck, 1000);
        }
    };

    private AlertDialog setScore() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        AlertDialog.Builder builder = new AlertDialog.Builder(Spark.this);

        View scoring_layout = inflater.inflate(R.layout.layout_scoring, null);

        final SharedPreferences.Editor editor = settings.edit();

        RadioGroup ratings = (RadioGroup) scoring_layout.findViewById(R.id.ratings);
        ratings.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch(i) {
                    case R.id.normal:
                        editor.putInt("score", 0);
                        editor.commit();
                        break;
                    case R.id.slight:
                        editor.putInt("score", 1);
                        editor.commit();
                        break;
                    case R.id.mild:
                        editor.putInt("score", 2);
                        editor.commit();
                        break;
                    case R.id.moderate:
                        editor.putInt("score", 3);
                        editor.commit();
                        break;
                    case R.id.severe:
                        editor.putInt("score", 4);
                        editor.commit();
                        break;
                }
            }
        });

        builder.setTitle("Score");
        builder.setView(scoring_layout);
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String message = "score:" + settings.getInt("score", -1);
                Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                stop();
                dialog.dismiss();
            }
        });

        return builder.create();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = getSharedPreferences("spark", MODE_PRIVATE);
        sContext = getApplicationContext();

        //Link to AWARE
        Intent framework = new Intent(this, Aware.class);
        startService(framework);

        //Start this plugin
        Aware.startPlugin(this, getPackageName());

        if( Aware.getSetting(this, Settings.STATUS_PLUGIN_SPARK).length() == 0 ) {
            Aware.setSetting(this, Settings.STATUS_PLUGIN_SPARK, true);
        }
        if( Aware.getSetting(this, Settings.FREQUENCY_SPARK).length() == 0 ) {
            Aware.setSetting(this, Settings.FREQUENCY_SPARK, 5);
        }

        googleClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(Bundle bundle) {
                Log.d(TAG, "Spark connected!");
                new GetPeerTask().execute();
            }

            @Override
            public void onConnectionSuspended(int i) {
                googleClient.reconnect();
            }
        }).addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
            @Override
            public void onConnectionFailed(ConnectionResult connectionResult) {
                googleClient.reconnect();
            }
        }).addApi(Wearable.API).build();
        googleClient.connect();

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

        //Reset score to -1 every time we start
        setScore(this, -1);

        if( Aware.is_watch( getApplicationContext() ) ) {

            setContentView(R.layout.activity_watch);
            watch_label = (TextView) findViewById(R.id.watch_label);
            watch_label.setText(settings.getInt("participant",1) + ":" + settings.getInt("task",1)+":" + settings.getInt("score", -1));

            watch_main = (RelativeLayout) findViewById(R.id.watch_main);
            frequency = (TextView) findViewById(R.id.frequency);

            //On create, turn off the accelerometer by default
            Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, false);
            Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, WATCH_SAMPLING);

            //We'll want battery and android wear communication active
            Aware.setSetting(this, Aware_Preferences.STATUS_ANDROID_WEAR, true);
            Aware.setSetting(this, Aware_Preferences.STATUS_BATTERY, true);

            Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
            sendBroadcast(apply);

        } else {

            setContentView(R.layout.activity_phone);

            task_label = (TextView) findViewById(R.id.task_label);
            setTaskLabel(settings.getInt("task",1));

            decrease_participant = (Button) findViewById(R.id.decrease_participant);
            decrease_participant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    setParticipant(getApplicationContext(), settings.getInt("participant", 1)-1);

                    String message = "participant:" + settings.getInt("participant", 1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    //When changing participant, reset task to 1 & score to -1
                    setTask(getApplicationContext(), 1);
                    setTaskLabel(1);

                    message = "task:1";
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    setScore(getApplicationContext(), -1);
                    message = "score:" + settings.getInt("score", -1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());
                }
            });

            increase_participant = (Button) findViewById(R.id.increase_participant);
            increase_participant.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    setParticipant(getApplicationContext(), settings.getInt("participant", 1)+1);

                    String message = "participant:" + settings.getInt("participant", 1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    //When changing participant, reset task to 1, score to -1
                    setTask(getApplicationContext(), 1);
                    setTaskLabel(1);

                    message = "task:1";
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    setScore(getApplicationContext(), -1);
                    message = "score:" + settings.getInt("score", -1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());
                }
            });

            decrease_task = (Button) findViewById(R.id.decrease_task);
            decrease_task.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setTask(getApplicationContext(), settings.getInt("task", 1)-1);
                    setTaskLabel(settings.getInt("task", 1));

                    String message = "task:" + settings.getInt("task", 1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    setScore(getApplicationContext(), -1);
                    message = "score:" + settings.getInt("score", -1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());
                }
            });

            increase_task = (Button) findViewById(R.id.increase_task);
            increase_task.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    setTask(getApplicationContext(), settings.getInt("task", 1)+1);
                    setTaskLabel(settings.getInt("task", 1));

                    String message = "task:" + settings.getInt("task", 1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

                    setScore(getApplicationContext(), -1);
                    message = "score:" + settings.getInt("score", -1);
                    Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());
                }
            });

            activator = (Button) findViewById(R.id.activator);
            if (settings.getInt("active", 0) == 0) {
                activator.setBackgroundColor(Color.GREEN);
            } else {
                activator.setBackgroundColor(Color.RED);
            }
            activator.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (activator.getText().equals("START")) {
                        start();
                    } else {
                        stop();
                    }
                }
            });

            participants = (TextView) findViewById(R.id.count_participant);
            participants.setText(String.valueOf(settings.getInt("participant", 1)));

            tasks = (TextView) findViewById(R.id.count_task);
            tasks.setText(String.valueOf(settings.getInt("task", 1)));

            timer = (TextView) findViewById(R.id.timer);
            timer.setText(DateUtils.formatElapsedTime(0));

            watch_battery = (TextView) findViewById(R.id.watch_battery);

            Cursor watch_battery_level = getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI, new String[]{Battery_Provider.Battery_Data.LEVEL}, Battery_Provider.Battery_Data.DEVICE_ID + " NOT LIKE '" + Aware.getSetting(this, Aware_Preferences.DEVICE_ID)+"'", null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
            if( watch_battery_level != null && watch_battery_level.moveToFirst() ) {
                watch_battery.setText("Watch battery: " + watch_battery_level.getInt(watch_battery_level.getColumnIndex(Battery_Provider.Battery_Data.LEVEL)) + "%");
            }
            if( watch_battery_level != null && ! watch_battery_level.isClosed() ) watch_battery_level.close();
        }
    }

    private void start() {
        activator.setText("STOP");
        activator.setBackgroundColor(Color.RED);

        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("active", 1);
        editor.commit();

        //we are starting the experiment
        String message = "active:1";
        Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

        feedback(getApplicationContext());

        start_task = System.currentTimeMillis();
        timerTask.post(refreshTime);

        AlertDialog dialog = setScore();
        dialog.show();
    }

    private void stop() {
        activator.setText("START");
        activator.setBackgroundColor(Color.GREEN);

        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("active", 0);
        editor.commit();

        //send to watch the label
        String message = "score:"+settings.getInt("score",-1);
        Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

        //we are stopping the experiment
        message = "active:0";
        Wearable.MessageApi.sendMessage(googleClient, peer.getId(), "/spark", message.getBytes());

        start_task = 0;
        timerTask.removeCallbacksAndMessages(null);
        timer.setText(DateUtils.formatElapsedTime(0));

        feedback(getApplicationContext());
    }

    private void setTaskLabel(int task) {
        switch(task) {
            case 1:
                task_label.setText("Speech");
                break;
            case 2:
                task_label.setText("Facial expression");
                break;
            case 3:
                task_label.setText("Right arm - Finger tapping");
                break;
            case 4:
                task_label.setText("Right arm - Hand movements");
                break;
            case 5:
                task_label.setText("Right arm - Pronation-Supination");
                break;
            case 6:
                task_label.setText("Right arm - Postural tremor");
                break;
            case 7:
                task_label.setText("Right arm - Kinetic tremor");
                break;
            case 8:
                task_label.setText("Right arm - Rest tremor");
                break;
            case 9:
                task_label.setText("Right arm - Tap phone");
                break;
            case 10:
                task_label.setText("Right arm - Tap watch");
                break;
            case 11:
                task_label.setText("Left arm - Finger tapping");
                break;
            case 12:
                task_label.setText("Left arm - Hand movements");
                break;
            case 13:
                task_label.setText("Left arm - Pronation-Supination");
                break;
            case 14:
                task_label.setText("Left arm - Postural Tremor");
                break;
            case 15:
                task_label.setText("Left arm - Kinetic tremor");
                break;
            case 16:
                task_label.setText("Left arm - Rest tremor");
                break;
            case 17:
                task_label.setText("Left arm - Tap phone");
                break;
            case 18:
                task_label.setText("Left arm - Tap watch");
                break;
            case 19:
                task_label.setText("Right leg - Toe tapping");
                break;
            case 20:
                task_label.setText("Right leg - Leg agility");
                break;
            case 21:
                task_label.setText("Right leg - Arising from chair");
                break;
            case 22:
                task_label.setText("Right leg - Gait");
                break;
            case 23:
                task_label.setText("Right leg - Rest tremor of legs");
                break;
            case 24:
                task_label.setText("Left leg - Toe tapping");
                break;
            case 25:
                task_label.setText("Left leg - Leg agility");
                break;
            case 26:
                task_label.setText("Left leg - Arising from chair");
                break;
            case 27:
                task_label.setText("Left leg - Gait");
                break;
            case 28:
                task_label.setText("Left - Rest tremor of legs");
                break;
            default:
                task_label.setText("N/A");
                break;
        }
    }

    public static void updateLabel(Context c, String label) {
        if( watch_label != null ) watch_label.setText(label);
        Intent accelerometerLabel = new Intent(Accelerometer.ACTION_AWARE_ACCELEROMETER_LABEL);
        accelerometerLabel.putExtra( Accelerometer.EXTRA_LABEL, label );
        c.sendBroadcast(accelerometerLabel);
    }

    public static void feedback(Context c) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(1000);
    }

    /**
     * Interface command receiver. Depending on visible UI, different actions.
     */
    public static class SparkListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( intent.getAction().equals(ACTION_AWARE_PLUGIN_SPARK) ) {
                String setting = intent.getStringExtra(EXTRA_SETTING);
                String value = intent.getStringExtra(EXTRA_VALUE);

                Cursor watch_battery_level = context.getContentResolver().query(Battery_Provider.Battery_Data.CONTENT_URI, new String[]{Battery_Provider.Battery_Data.LEVEL}, Battery_Provider.Battery_Data.DEVICE_ID + " NOT LIKE '" + Aware.getSetting(context, Aware_Preferences.DEVICE_ID)+"'", null, Battery_Provider.Battery_Data.TIMESTAMP + " DESC LIMIT 1");
                if( watch_battery_level != null && watch_battery_level.moveToFirst() ) {
                    if( watch_battery != null ) watch_battery.setText("Watch battery: " + watch_battery_level.getInt(watch_battery_level.getColumnIndex(Battery_Provider.Battery_Data.LEVEL)) + "%");
                }
                if( watch_battery_level != null && ! watch_battery_level.isClosed() ) watch_battery_level.close();

                if( setting.equals("active") ) {
                    if( value.equals("1") ) {
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("active", 1);
                        editor.commit();

                        if( Aware.is_watch(context) ) {
                            watch_main.setBackgroundColor(Color.RED);
                            feedback(context);
                            frequencyChecker.post(frequencyCheck);
                            startExperiment(context);
                        } else {
                            activator.setText("STOP");
                            activator.setBackgroundColor(Color.RED);
                            start_task = System.currentTimeMillis();
                            timerTask.post(refreshTime);
                        }
                    }
                    if( value.equals("0") ) {
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("active", 0);
                        editor.commit();

                        if( Aware.is_watch(context) ) {
                            watch_main.setBackgroundColor(Color.BLACK);
                            stopExperiment(context);
                            feedback(context);
                            frequencyChecker.removeCallbacksAndMessages(null);
                            frequency.setText("0 Hz");
                        } else {
                            activator.setText("START");
                            activator.setBackgroundColor(Color.GREEN);
                            start_task = 0;
                            timerTask.removeCallbacksAndMessages(null);
                            timer.setText(DateUtils.formatElapsedTime(0));
                        }
                    }
                }
                if( setting.equals("participant") ) {
                    setParticipant( context, Integer.parseInt(value) );
                }
                if( setting.equals("task") ) {
                    setTask( context, Integer.parseInt(value) );
                }
                if( setting.equals("score") ) {
                    setScore( context, Integer.parseInt(value) );
                }
            }
        }
    }

    private class GetPeerTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
            if( nodes.getNodes().size() > 0 ) peer = nodes.getNodes().get(0);
            return null;
        }
    }

    private static void startExperiment(Context c) {
        Aware.setSetting(c, Aware_Preferences.STATUS_ACCELEROMETER, true);
        Aware.setSetting(c, Aware_Preferences.FREQUENCY_ACCELEROMETER, WATCH_SAMPLING);

        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        c.sendBroadcast(apply);

        //When sampling starts, score is -1
        setScore(c, -1);
    }

    private static void stopExperiment(Context c) {
        Aware.setSetting(c, Aware_Preferences.STATUS_ACCELEROMETER, false);
        Aware.setSetting(c, Aware_Preferences.FREQUENCY_ACCELEROMETER, WATCH_SAMPLING);

        Intent apply = new Intent(Aware.ACTION_AWARE_REFRESH);
        c.sendBroadcast(apply);

        ContentValues rowData = new ContentValues();
        rowData.put(Accelerometer_Provider.Accelerometer_Data.LABEL, settings.getInt("participant",1)+":"+settings.getInt("task",1) + ":" + settings.getInt("score",-1));
        int updated = c.getContentResolver().update(Accelerometer_Provider.Accelerometer_Data.CONTENT_URI, rowData, Accelerometer_Provider.Accelerometer_Data.LABEL + " LIKE ''", null);
        Log.d(Plugin.TAG, "Relabeled: " + updated + " rows with " + rowData.toString());

        //When sampling ends, remove label
        removeLabel(c);
    }

    private static void removeLabel(Context c) {
        updateLabel(c, "");
    }

    private static void setScore(Context c, int value) {
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("score", value);
        editor.commit();
        updateLabel(c, settings.getInt("participant", 1) + ":"+ settings.getInt("task", 1) + ":" + settings.getInt("score", -1) );
    }

    private static void setParticipant(Context c, int value) {
        if( participants != null ) participants.setText(String.valueOf(value));
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("participant", value);
        editor.commit();
        updateLabel(c, settings.getInt("participant", 1) + ":"+ settings.getInt("task", 1) + ":" + settings.getInt("score", -1));
    }

    private static void setTask(Context c, int value) {
        if( tasks != null ) tasks.setText(String.valueOf(value));
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("task", value);
        editor.commit();
        updateLabel(c, settings.getInt("participant", 1) + ":"+ settings.getInt("task", 1) + ":" + settings.getInt("score", -1));
    }
}
