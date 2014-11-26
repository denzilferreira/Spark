package com.aware.plugin.spark;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;

import com.aware.utils.IContextCard;

/**
 * Created by denzil on 25/11/14.
 */
public class ContextCard implements IContextCard {

    private SharedPreferences settings;

    public ContextCard(){}

    @Override
    public View getContextCard(Context context) {
        settings = context.getSharedPreferences("spark", Context.MODE_PRIVATE);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = inflater.inflate(R.layout.context_card, null);

        return card;
    }
}
