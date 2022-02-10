
package com.sprd.generalsecurity.network;

// Need the following import to get access to the app resources, since this
// class is in a sub-package.


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sprd.generalsecurity.R;
import com.sprd.generalsecurity.utils.Contract;

import android.util.Log;
import android.content.Context;


public class AlertActivity extends Activity implements View.OnClickListener {

    /** UNISOC: add for 1014151 the msg not translate when switch language @{ */
    private static final String WARNIN_SIM_COUNT= "simcount";
    private static final String WARNIN_SIM_INDEX= "simindex";
    /** UNISOC: add for bug1115913  @{ */
    private static final String WARNIN_IS_REMAIN= "isRemain";
    /** @} */
    private static final int SIM1_INDEX = 1;
    private static final int SIM2_INDEX = 2;

    private static final int COUNT_SINGLE = 1;
    private static final int COUNT_DUAL = 2;
    private static final String SIM1 = "SIM1";
    private static final String SIM2 = "SIM2";
    /** @} */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Be sure to call the super class.
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        setContentView(R.layout.alert);
        Intent it = getIntent();
        TextView text = (TextView)findViewById(R.id.text);
        getWindow().setFeatureDrawableResource(Window.FEATURE_LEFT_ICON,
                android.R.drawable.ic_dialog_alert);

        Button button = (Button)findViewById(R.id.ok);
        button.setOnClickListener(this);
        /** UNISOC: add for 1014151 the msg not translate when switch language @{ */
        int simCount = it.getIntExtra(WARNIN_SIM_COUNT, -1);
        int simIndex = it.getIntExtra(WARNIN_SIM_INDEX, -1);
        int type = it.getIntExtra(Contract.EXTRA_ALERT_TYPE, 0);
        /** UNISOC: add for bug1115913  @{ */
        boolean isRemain = it.getBooleanExtra(WARNIN_IS_REMAIN,false);

        if (simCount == -1 || simIndex == -1) {
           return;
        }
        if (getIntent().getBooleanExtra(Contract.EXTRA_SIM_PROMPT, false)) {
            // prompt user reset sim related data
            text.setText(getResources().getString(R.string.sim_changed));
            return;
        }

        if (type == Contract.ALERT_TYPE_MONTH) {
            String msgMonthSIM = "";
            if (simCount == COUNT_SINGLE) {
                msgMonthSIM = String.format(getResources()
                                .getString(isRemain ? R.string.warning_remain_msg : R.string.warning_msg_month_SIM), "");
            } else if (simCount == COUNT_DUAL) {
                if (simIndex == SIM1_INDEX) {
                    msgMonthSIM = String.format(getResources()
                                    .getString(isRemain ? R.string.warning_remain_msg : R.string.warning_msg_month_SIM), SIM1);
                } else if (simIndex == SIM2_INDEX) {
                    msgMonthSIM = String.format(getResources()
                                    .getString(isRemain ? R.string.warning_remain_msg : R.string.warning_msg_month_SIM), SIM2);
                }
            }
        /** @} */
            text.setText(msgMonthSIM);
        } else if (type == Contract.ALERT_TYPE_DAY) {
            String msgDaySIM = "";
            if (simCount == COUNT_SINGLE) {
                msgDaySIM = String.format(getResources().
                            getString(R.string.warning_msg_day_SIM), "");
            } else if (simCount == COUNT_DUAL) {
                if (simIndex == SIM1_INDEX) {
                    msgDaySIM = String.format(getResources().
                            getString(R.string.warning_msg_day_SIM), SIM1);
                } else {
                    msgDaySIM = String.format(getResources().
                            getString(R.string.warning_msg_day_SIM), SIM2);
                }
            }
            text.setText(msgDaySIM);
        }
        /** @} */
    }

    @Override
    public void onClick(View v) {
        finish();
    }
}
