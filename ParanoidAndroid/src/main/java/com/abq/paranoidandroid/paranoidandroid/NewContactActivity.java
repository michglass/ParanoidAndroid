package com.abq.paranoidandroid.paranoidandroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


public class NewContactActivity extends Activity implements View.OnClickListener {

    private EditText etName;
    private EditText etNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_contact);

        etName = (EditText) findViewById(R.id.etName);
        etNumber = (EditText) findViewById(R.id.etName);

        findViewById(R.id.btnSave).setOnClickListener(this);
        findViewById(R.id.btnCancel).setOnClickListener(this);
    }

    private boolean isValidNumber(String number) {
        return (number.length() == 10) ? true : false;
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnSave:
                if (etName.getText().toString().length() > 0 && isValidNumber(etNumber.getText().toString())) {
                    Intent intent = new Intent();
                    intent.putExtra("name", etName.getText().toString());
                    intent.putExtra("number", etNumber.getText().toString());
                    setResult(RESULT_OK, intent);
                    finish();
                }
                else {
                    etNumber.setText("");
                    etName.setText("");
                    Toast.makeText(this, "Enter valid name and number!", Toast.LENGTH_SHORT).show();
                }

                break;
            case R.id.btnCancel:
                setResult(RESULT_CANCELED);
                finish();
                break;
        }
    }
}
