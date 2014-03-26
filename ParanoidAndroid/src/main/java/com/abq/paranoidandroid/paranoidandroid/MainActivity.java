package com.abq.paranoidandroid.paranoidandroid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends Activity {

    // Debug
    private static final String TAG = "Main Activity";

    // Service Variables
    private Messenger mBluetoothServiceMessenger;
    private boolean mBound;
    private final Messenger clientMessenger = new Messenger(new ServiceHandler());

    /**
     * Activity Lifecycle methods
     * On Create: Start Service
     * On Start: Bind to Service
     * On Stop: Unbind From Service
     * On Destroy: Stop Service
     */

    /**
     * On Create
     * @param savedInstanceState Saved Instance State
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "On Create");
        super.onCreate(savedInstanceState);

        // fullscreen activity
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // keep screen from dimming
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        /**
         * Service Stuff
         */
        // start service
        startService(new Intent(this, BluetoothService.class));
    }
    /**
     * On Start
     * Bind to Service
     */
    @Override
    protected void onStart() {
        Log.v(TAG, "On Start");
        super.onStart();

        // Bind to service if not bound
        if(!mBound) {
            bindService(new Intent(this, BluetoothService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
        }
    }
    /**
     * On Stop
     * Unbind from Service
     */
    @Override
    protected void onStop() {
        Log.v(TAG, "On Stop");

        super.onStop();

        // Unbind from Service
        if(mBound) {
            sendMessageToService(BluetoothService.UNREGISTER_CLIENT);
            unbindService(mConnection);
            mBound = false;
        }
    }
    /**
     * On Destroy
     * Stop Service (only in one Component!!)
     */
    @Override
    protected void onDestroy() {
        Log.v(TAG, "On Destroy");
        super.onDestroy();

        // stop service
        stopService(new Intent(this, BluetoothService.class));
        BluetoothService.BOUND_COUNT = 0;
    }

    public void OnButtonClick(View v) {

        switch(v.getId()) {

            case R.id.sendOKButton:
                Log.v(TAG, "Send OK to Service");
                sendMessageToService(BluetoothService.GLASS_OK);
                break;
            /*
            case R.id.sendBackButton:
                Log.v(TAG, "Send Back to Service");
                sendMessageToService(BluetoothService.GLASS_BACK);
                break;
                */
        }
    }

    /**
     * Service Util Functions
     * Service Connection: Get IF for comm with Service
     * Send Message To Service: Send a command to Glass over the BT Service
     * Set Up Message: Initial contact with service
     * Service Handler: Handles incoming messages from Service
     */

    /**
     * ServiceConnection
     * Callback Methods that get called when Client binds to Service
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.v(TAG, "On Service Connect");

            // set up messenger
            mBluetoothServiceMessenger = new Messenger(iBinder);
            mBound = true;

            setUpMessage();
        }

        /**
         * Only called when Service unexpectedly disconnected!!
         */
        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.v(TAG, "On Service Disconnect");
            mBound = false;
        }
    };
    /**
     * Send Message To Service
     * Sends a message over the Service to Android
     * @param message Command (right now just OK)
     */
    private void sendMessageToService(int message) {
        Message msg = new Message();
        msg.what = message;

        try {
            Log.v(TAG, "Try contacting Service");
            mBluetoothServiceMessenger.send(msg);
        } catch (RemoteException remE) {
            Log.e(TAG, "Couldn't contact Service", remE);
        }
    }
    /**
     * Set Up Message
     * First contact with Service
     * Has to be send!
     * (with clientMessenger in replyTo Param so Service can respond to client)
     */
    private void setUpMessage() {
        Message startMsg = new Message();
        startMsg.what = BluetoothService.REGISTER_CLIENT;
        startMsg.replyTo = clientMessenger;

        try {
            Log.v(TAG, "First time contact to service");
            mBluetoothServiceMessenger.send(startMsg);
        } catch (RemoteException remE) {
            Log.e(TAG, "Couldn't contact Service", remE);
        }
    }
    /**
     * Message Handler
     * Handles Messages from Glass
     * Messages wrt Connection State or text, int, picture
     */
    private class ServiceHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {

            switch(msg.what) {

                // connection state changed
                case BluetoothService.MESSAGE_STATE_CHANGE:
                    Toast.makeText(getApplicationContext(),
                            "Connection state changed", Toast.LENGTH_SHORT).show();
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            Toast.makeText(getApplicationContext(),
                                    "Connected", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            Toast.makeText(getApplicationContext(),
                                    "Connecting", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.STATE_LISTENING:
                            Toast.makeText(getApplicationContext(),
                                    "Listening", Toast.LENGTH_SHORT).show();
                            break;
                        case BluetoothService.STATE_NONE:
                            Toast.makeText(getApplicationContext(),
                                    "Doing Nothing", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case BluetoothService.MESSAGE_WRITE:
                    Log.v(TAG, "Sending message to Glass");
                    break;
                case BluetoothService.MESSAGE_RESTART:
                    Toast.makeText(getApplicationContext(),
                            "Restart Listening", Toast.LENGTH_SHORT).show();

                    // send message to service that it has to restart the connection
                    sendMessageToService(BluetoothService.MESSAGE_RESTART);
                    break;
                case BluetoothService.WAIT_FOR_CONNECTION:
                    Log.v(TAG, "Glass App hasn't started yet");
                    break;
                case BluetoothService.GLASS_STOPPED:
                    Toast.makeText(getApplicationContext(),
                            "Restart Listening", Toast.LENGTH_SHORT).show();
                    // send message to service that it has to restart the connection
                    sendMessageToService(BluetoothService.MESSAGE_RESTART);
                    break;
                case BluetoothService.STRING_MESSAGE:
                    String s = (String) msg.obj;
                    Log.v(TAG, s);
                    ArrayList<String> stringList = new ArrayList<String>(Arrays.asList(s.split(";")));
                    if(stringList.size() == 3){
                        Log.v(TAG, "Message prefix: " + stringList.get(0));
                        if(stringList.get(0).equals("comm")){
                            Log.v(TAG, "Sending SMS...");
                            SmsManager smsManager = SmsManager.getDefault();
                            smsManager.sendTextMessage(stringList.get(1), null, stringList.get(2), null, null);
                            Log.v(TAG, "SMS sending complete with message: " + stringList.get(1) + " " + stringList.get(2));
                        }
                    }
                    break;
                case BluetoothService.BITMAP_MESSAGE:
                    Log.v(TAG, "Bitmap Message");
                    break;
            }
        }
    }
}
