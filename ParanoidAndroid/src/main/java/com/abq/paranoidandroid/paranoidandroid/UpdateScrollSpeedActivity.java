package com.abq.paranoidandroid.paranoidandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.NumberPicker;


public class UpdateScrollSpeedActivity extends Activity implements View.OnClickListener {

    private NumberPicker npScrollSpeedPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_scroll_speed);

        npScrollSpeedPicker = (NumberPicker) findViewById(R.id.npScrollSpeedPicker);
        npScrollSpeedPicker.setMaxValue(10);
        npScrollSpeedPicker.setMinValue(1);
        npScrollSpeedPicker.setValue(5);

        findViewById(R.id.btnSave).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSave:
                Intent intent = new Intent();
                intent.putExtra(MainActivity.SCROLL_SPEED_KEY, npScrollSpeedPicker.getValue());
                setResult(RESULT_OK, intent);
                break;
            case R.id.btnCancel:
                setResult(RESULT_CANCELED);
                break;
        }
        finish();
    }
}
