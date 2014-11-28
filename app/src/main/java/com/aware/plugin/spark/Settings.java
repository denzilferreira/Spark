package com.aware.plugin.spark;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

/**
 * Created by denzil on 10/10/14.
 */
public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * State of this plugin
     */
    public static final String STATUS_PLUGIN_SPARK = "status_plugin_spark";

    /**
     * How frequently do we check the medication of Parkinson
     */
    public static final String FREQUENCY_SPARK = "frequency_plugin_spark";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        syncPreferences();
    }

    private void syncPreferences() {
        CheckBoxPreference check = (CheckBoxPreference) findPreference(STATUS_PLUGIN_SPARK);
        check.setChecked(Aware.getSetting(this, STATUS_PLUGIN_SPARK).equals("true"));
        EditTextPreference frequency = (EditTextPreference) findPreference(FREQUENCY_SPARK);
        frequency.setSummary(Aware.getSetting(getApplicationContext(), FREQUENCY_SPARK) + " minutes");
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncPreferences();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if( key.equals(STATUS_PLUGIN_SPARK)) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            if( sharedPreferences.getBoolean(key, false) ) {
                Aware.startPlugin(this, getPackageName());
            } else {
                Aware.stopPlugin(this, getPackageName());
            }
        }
        if( key.equals(FREQUENCY_SPARK) ) {
            Aware.setSetting(getApplicationContext(), FREQUENCY_SPARK, sharedPreferences.getString(key, "5"));
            Aware.startPlugin(getApplicationContext(), getPackageName());
        }
        syncPreferences();
    }
}
