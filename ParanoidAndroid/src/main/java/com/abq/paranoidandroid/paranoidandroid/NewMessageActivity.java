package com.abq.paranoidandroid.paranoidandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


public class NewMessageActivity extends Activity implements View.OnClickListener {

    EditText etMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_message);

        etMessage = (EditText) findViewById(R.id.etMessage);

        findViewById(R.id.btnSave).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnSave:
                if (etMessage.getText().toString().length() > 0) {
                    Intent intent = new Intent();
                    intent.putExtra("message", etMessage.getText().toString());
                    setResult(RESULT_OK, intent);
                    finish();
                }
                else {
                    etMessage.setText("");
                    Toast.makeText(this, "Enter valid message!", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.btnCancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
        }
    }
}
