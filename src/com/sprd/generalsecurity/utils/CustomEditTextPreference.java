/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sprd.generalsecurity.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.preference.DialogPreference;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sprd.generalsecurity.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CustomEditTextPreference extends DialogPreference {

    Context mContext;
    private final static long MAX_VALUE = 5000000;// max value, about 5000GB
    private final static String ZERO_DATA_FLOW = "0";
    private final static String UNIT_DATA_FLOW = " MB";
    private final static Character POINT = '.';
    private final static Character ZERO = '0';
    private EditText mEditFlowLimit;
    private TextView mTitle;

    // UNISOC: Bug852533 modify the logic to avoid to disappear EditText
    public CustomEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        setPersistent(true);
        setDialogLayoutResource(R.layout.data_flow_limit);
    }

    public CustomEditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.editTextPreferenceStyle);
    }

    public CustomEditTextPreference(Context context) {
        this(context, null);
    }


    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mEditFlowLimit = (EditText) view.findViewById(R.id.edit_flow_limit);
        mTitle = (TextView) view.findViewById(R.id.flow_limit_title);
        mTitle.setText(UNIT_DATA_FLOW);
        mEditFlowLimit.setText(
                (getPersistedString(ZERO_DATA_FLOW)
                        .equalsIgnoreCase("") ? ZERO_DATA_FLOW
                        : getPersistedString(ZERO_DATA_FLOW)),
                TextView.BufferType.NORMAL);
        mEditFlowLimit.setSelection(mEditFlowLimit.getText().length());
        mEditFlowLimit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //limit the input number as 2 number at most after the point
                int index = s.toString().indexOf('.');
                if (index > 0) {
                    if (s.toString().length() - index > 3) {
                        mEditFlowLimit.setText(s.toString().substring(0, index + 3));
                        mEditFlowLimit.setSelection(mEditFlowLimit.getText().length());
                    }
                }
                // UNISOC: Bug863225,891985 disable the positive button when clear text
                AlertDialog dialog = (AlertDialog) getDialog();
                if (dialog != null) {
                    if (s.length() == 0) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    } else {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String editable = s.toString();
                String standardValue = String.valueOf(MAX_VALUE);

                //disallow 0 as the first number if it is not include '.' .
                if(standardValue.equalsIgnoreCase(editable)){
                    //avoid loop call.
                    return;
                }

                //float values are not exact, so just check the numbers before '.' .
                if (editable.indexOf('.') > 0) {
                    String tmp = editable.substring(0, editable.indexOf('.'));
                    if (tmp.equalsIgnoreCase(standardValue)) {
                        mEditFlowLimit.setText(standardValue);
                        mEditFlowLimit.setSelection(mEditFlowLimit.getText().length());
                        Toast.makeText(mContext,R.string.max_data_flow_value,Toast.LENGTH_SHORT).show();
                        return;
                    }
                }

                //disallow 0 as the first number if it is not include '.' .
                if (mEditFlowLimit.getText().length() >= 2 && mEditFlowLimit.getText().charAt(0) == '0') {
                    if (mEditFlowLimit.getText().charAt(1) != '.') {
                        mEditFlowLimit.setText(mEditFlowLimit.getText().toString().substring(1));
                        mEditFlowLimit.setSelection(mEditFlowLimit.getText().length());
                    }
                }

                // disallow input as '.' only
                if (mEditFlowLimit.getText().toString().length() == 1 && mEditFlowLimit.getText().charAt(0) == '.') {
                    mEditFlowLimit.setText("");
                }

                if (TextUtils.isEmpty(mEditFlowLimit.getText().toString()) == false) {
                    /**UNISOC: modify for bug1060848  @{*/
                    if (String2NumberUtil.string2Float(mEditFlowLimit.getText().toString()) > MAX_VALUE) {
                    /** @} */

                        mEditFlowLimit.setText(standardValue);
                        mEditFlowLimit.setSelection(mEditFlowLimit.getText().length());
                        Toast.makeText(mContext,R.string.max_data_flow_value,Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

    /**UNISOC: modify for bug1060848、1064261  @{*/
        String editText = formatString(mEditFlowLimit.getText().toString());
        if (positiveResult && shouldPersist()) {
            persistString(editText);
            setSummary(editText + UNIT_DATA_FLOW);
        /**UNISOC: modify for bug1014082  @{*/
            callChangeListener(editText);
        }
        /** @} */
    }

    private String formatString(String summary) {
        if (summary != null && !summary.equalsIgnoreCase("")) {
            Float f = String2NumberUtil.string2Float(summary);
            DecimalFormat decimalFormat = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.ENGLISH));
            summary = decimalFormat.format(f);
        } else {
            summary = ZERO_DATA_FLOW;
        }

        return summary;
    }
    /** @} */
}