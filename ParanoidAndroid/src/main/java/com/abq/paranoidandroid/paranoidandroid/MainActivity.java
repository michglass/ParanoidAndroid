package com.abq.paranoidandroid.paranoidandroid;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;


public class MainActivity extends Activity {

    // Debug
    private static final String TAG = "Main Activity";

    // Service Variables
    private Messenger mBluetoothServiceMessenger;
    private boolean mBound;
    private final Messenger clientMessenger = new Messenger(new ServiceHandler());

    // USB vars
    // Connection has to be permitted by user
    private PendingIntent mPermissionIntent; // sends ACTION_USB_PERMISSION with accepted or denied flag
    private static final String ACTION_USB_PERMISSION = "com.abq.arduinotest.arduinotest.USB_PERMISSION";
    private boolean mPermissionRequestPending;

    // sys service, manages interactions with sys port (requests/checks permission to connect to device)
    private UsbManager mUsbManager;
    private UsbAccessory mUsbAccessory; // reference to connected accessory (arduino board)
    private ParcelFileDescriptor mFileDescriptor; // Descriptor obtained when connection is established
    private FileInputStream mInputStream;
    // private FileOutputStream mOutputStream;

    // Protocol Vars, same as in arduino sketch (first 2 bytes of protocol)
    private static final byte COMMAND_BUTTON = 0x1;
    private static final byte TARGET_BUTTON = 0x1;
    private static final byte VALUE_PRESSED = 0x1; // 1, if button pressed
    private static final byte VALUE_NOTPRESSED = 0x0; // 0, if button not pressed

    private Vibrator mVibrator;
    private boolean isVibrating;

    private JSONObject mSettings;
    private static final String SP_SETTINGS = "settings";
    private static final String SCROLL_SPEED_KEY = "SCROLL_SPEED";
    private static final int SCROLL_SPEED_DEFAULT = 5;
    private static final String NUM_CONTACTS_KEY = "NUM_CONTACTS";
    private static final int NUM_CONTACTS_DEFAULT = 0;

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

        // USB Stuff
        mUsbManager = UsbManager.getInstance(this);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter intentFilter = new IntentFilter(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, intentFilter);

        mVibrator = ((Vibrator) getSystemService(VIBRATOR_SERVICE));
        isVibrating = false;

        // initialize in-memory settings object
        SharedPreferences sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE);
        mSettings = new JSONObject();

        try {
            mSettings.put(SCROLL_SPEED_KEY, sp.getInt(SCROLL_SPEED_KEY, SCROLL_SPEED_DEFAULT));
            mSettings.put(NUM_CONTACTS_KEY, sp.getInt(NUM_CONTACTS_KEY, NUM_CONTACTS_DEFAULT));

            // iterate through all contacts in SP, add to in-memory settings object
            final int num_contacts = mSettings.getInt(NUM_CONTACTS_KEY);
            for (int i = 1; i <= num_contacts; i++) {
                String name_key = "contact_" + i + "_name";
                String number_key = "contact_" + i + "_number";
                mSettings.put(name_key, sp.getString(name_key, ""));
                mSettings.put(number_key, sp.getString(number_key, ""));
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.addContact:
                Intent intent = new Intent(MainActivity.this, NewContactActivity.class);
                startActivityForResult(intent, 1);
                return true;
            case R.id.changeScrollSpeed:
                // ...
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {
            if(resultCode == RESULT_OK){
                final String name = data.getStringExtra("name");
                final String number = data.getStringExtra("number");
                addContact(name, number);
            }
        }
    }

    public void addContact(final String name, final String number) {
        final int contact_number = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE).getInt(NUM_CONTACTS_KEY, NUM_CONTACTS_DEFAULT) + 1; // we're adding a contact
        final String name_key = "contact_" + contact_number + "_name";
        final String number_key = "contact_" + contact_number + "_number";

        // insert contact into local storage, update number of contacts
        SharedPreferences.Editor editor = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE).edit();
        editor.putString(name_key, name);
        editor.putString(number_key, number);
        editor.putInt(NUM_CONTACTS_KEY, contact_number);
        editor.commit();

        // add contact to in-memory settings object
        try {
            mSettings.put(name_key, name);
            mSettings.put(number_key, number);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void updateScrollSpeed(final int scroll_speed) {
        getSharedPreferences(SP_SETTINGS, MODE_PRIVATE).edit().putInt(SCROLL_SPEED_KEY, scroll_speed).commit();
        try {
            mSettings.put(SCROLL_SPEED_KEY, scroll_speed);
        } catch (JSONException e) {
            e.printStackTrace();
        }
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
     * On Resume
     * Start USB connection
     */
    @Override
    protected void onResume() {
        Log.v(TAG, "On Resume");
        super.onResume();

        if(mInputStream != null) { return; }

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if(accessory != null) {
            if(mUsbManager.hasPermission(accessory)) {
                Log.v(TAG, "Has Permission");
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if(!mPermissionRequestPending) {
                        Log.v(TAG, "Doesn't have Permission");
                        // request users permission to connect to USB device
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else { Log.v(TAG, "Accessory is NULL"); }
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

        // USB
        closeAccessory();
        stopVibrate();
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

        // USB
        unregisterReceiver(mUsbReceiver);
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

    private void sendMessageToService(JSONObject jsonObject) {
        Message msg = new Message();

        msg.what = BluetoothService.GLASS_DATA;
        msg.obj = jsonObject.toString().getBytes();

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

                            // send settings to glass
                            sendMessageToService(mSettings);

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

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = UsbManager.getAccessory(intent);

                    if(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else { Log.v(TAG, "Permission denied for: " + accessory); }
                    mPermissionRequestPending = false;
                }
            } else if(UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if(accessory != null && accessory.equals(mUsbAccessory)) {
                    closeAccessory();
                }
            }
        }
    };


    // open accessory
    private void openAccessory(UsbAccessory acc) {

        mFileDescriptor = mUsbManager.openAccessory(acc);
        if(mFileDescriptor != null) {
            mUsbAccessory = acc;
            FileDescriptor fileDescriptor = mFileDescriptor.getFileDescriptor();

            mInputStream = new FileInputStream(fileDescriptor);

            Thread thread = new Thread (null, commRunnable, TAG);
            thread.start();
            Log.v(TAG, "Accessory open");
        } else { Log.v(TAG, "Accessory open fail"); }
    }

    // close accessory
    private void closeAccessory() {

        try {
            if(mFileDescriptor != null) {
                mFileDescriptor.close();
                Log.v(TAG, "Try File Desc Close");
            }
        } catch(IOException ioE) {
            Log.e(TAG, "File Desc Close Fail, ioE");
        } finally {
            mFileDescriptor = null;
            mUsbAccessory = null;
        }
    }

    // Communication Runnable
    Runnable commRunnable = new Runnable() {
        @Override
        public void run() {
            Log.v(TAG, "Run");

            int ret = 0;
            final byte[] buffer = new byte[3];

            while (ret >= 0) {
                try {
                    ret = mInputStream.read(buffer);
                    Log.v(TAG, "Try read inBuffer");
                } catch (IOException ioE) {
                    Log.e(TAG, "Read inBuffer Failed");
                    break;
                }

                switch (buffer[0]) {
                    case COMMAND_BUTTON:

                        if(buffer[1] == TARGET_BUTTON) {
                            if(buffer[2] == VALUE_PRESSED) {
                                Log.v(TAG, "Button Pressed");
                                sendMessageToService(BluetoothService.GLASS_OK);
                                startVibrate();
                            } else if(buffer[2] == VALUE_NOTPRESSED) {
                                Log.v(TAG, "Button not Pressed");
                                stopVibrate();
                            }
                        }
                        break;
                    default:
                        Log.v(TAG, "Unkown Msg: " + buffer[0]);
                        break;
                }
            }
        }
    };

    // start vibrate
    private void startVibrate() {
        if(mVibrator != null && !isVibrating) {
            Log.v(TAG, "Start Vibrating");
            isVibrating = true;
            mVibrator.vibrate(250);
        }
    }
    private void stopVibrate() {
        if(mVibrator != null && isVibrating) {
            Log.v(TAG, "Stop Vibrating");
            isVibrating = false;
            mVibrator.cancel();
        }
    }
}
