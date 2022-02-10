package com.sprd.generalsecurity.utils;

import android.util.Log;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

/**UNISOC: add for bug1060848  @{*/
public class String2NumberUtil {

    static public Float string2Float(String s) {
        Float f;
        try {
            f = NumberFormat.getInstance(Locale.ENGLISH).parse(s).floatValue();
        } catch (ParseException e) {
            e.printStackTrace();
            f = Float.valueOf(0);
        }
        return f;
    }
}
