package com.abq.paranoidandroid.paranoidandroid;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Build;
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
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

//import com.android.future.usb.UsbAccessory;
//import com.android.future.usb.UsbManager;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
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

    boolean haveBackend = true;

    private JSONObject mSettings;
    private static final String SP_SETTINGS = "settings";
    public static final String SCROLL_SPEED_KEY = "SCROLL_SPEED";
    public static final int SCROLL_SPEED_DEFAULT = 5;
    public static final String NUM_CONTACTS_KEY = "NUM_CONTACTS";
    public static final int NUM_CONTACTS_DEFAULT = 0;
    public static final String NAME_KEY = "name";
    public static final String NUMBER_KEY = "number";
    public final Context MainContext = this;
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
    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
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
        //mUsbManager = UsbManager.getInstance(this);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter intentFilter = new IntentFilter(ACTION_USB_PERMISSION);
        intentFilter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        //registerReceiver(mUsbReceiver, intentFilter);

        mVibrator = ((Vibrator) getSystemService(VIBRATOR_SERVICE));
        isVibrating = false;

        // initialize in-memory settings object
        SharedPreferences sp = getSharedPreferences(SP_SETTINGS, MODE_PRIVATE);
        mSettings = new JSONObject();

        if(haveBackend){
            MyGETJSON webContactString = new MyGETJSON();
            webContactString.execute("contacts");
            Log.v(TAG, "after execute");
        }

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

        findViewById(R.id.btnSettings).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle("Choose a settings option")
                        .setItems(R.array.settings, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent();
                                int reqCode = 0;

                                switch (which) {
                                    case 0:
                                        // change scroll speed
                                        intent.setClass(MainActivity.this, UpdateScrollSpeedActivity.class);
                                        reqCode = 1;
                                        break;
                                    case 1:
                                        // add a contact
                                        intent.setClass(MainActivity.this, NewContactActivity.class);
                                        reqCode = 2;
                                        break;
                                    case 2:
                                        // cancel
                                        return;
                                }

                                assert reqCode != 0;
                                startActivityForResult(intent, reqCode);
                            }
                        });

                builder.create().show();

                return false;
            }
        });
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != RESULT_OK)
            return;

        if (requestCode == 1) {
            final String name = data.getStringExtra(NAME_KEY);
            final String number = data.getStringExtra(NUMBER_KEY);
            assert name.length() > 0;
            assert number.length() == 10;
            addContact(name, number);
        }
        else if (requestCode == 2) {
            final int scroll_speed = data.getIntExtra(SCROLL_SPEED_KEY, SCROLL_SPEED_DEFAULT);
            assert scroll_speed >= 1 && scroll_speed <= 10;
            updateScrollSpeed(scroll_speed);
        }

        //sendToGlass(mSettings);
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
        /*
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
        */
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
        //unregisterReceiver(mUsbReceiver);
    }

    public void OnButtonClick(View v) {

        switch(v.getId()) {

            case R.id.sendOKButton:
                Log.v(TAG, "Send OK to Service");
                byte[] okCommand = ByteBuffer.allocate(4).putInt(BluetoothService.GLASS_OK).array();
                sendToGlass(okCommand);
                break;
        }
    }

    /**
     * Send SMS
     * Parses a string from glass and sends it as an SMS
     */
    @TargetApi(Build.VERSION_CODES.DONUT)
    private void sendSMS(JSONObject object) {
        Log.v(TAG, object.toString());
        Log.v(TAG, "Sending SMS...");
        try{
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(object.getString("number"), null, object.getString("name"), null, null);
            Log.v(TAG, "SMS sending complete with message: " + object.getString("name") + " " + object.getString("number"));
        }
        catch(JSONException j){
            Log.e(TAG, j.toString());
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
     * Send To Glass (1)
     * Send a message(byte array) to glass
     * @param msgForGlass msg that you want to send to glass
     */
    private void sendToGlass(byte[] msgForGlass) {
        sendMessageToService(BluetoothService.GLASS_DATA, msgForGlass);
    }
    /**
     * Send To Glass (2)
     * Send a JSON Object to glass
     * @param json Json Message we want to send to glass
     */
    private void sendToGlass(JSONObject json) {
        byte[] glassmsg = json.toString().getBytes();

        sendToGlass(glassmsg);
    }
    /**
     * Send Message To Service (1)
     * Send a non-Glass message to Service
     * @param message Message that will not be transfered to Glass
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
     * Send Message To Service (2)
     * Send a message to the Service that will be transmitted to Glass
     * @param glassMsg byte array that represents the glass data
     * @param w What field for msg object
     */
    private void sendMessageToService(int w, byte[] glassMsg) {
        Message msg = new Message();
        msg.what = w;
        msg.obj = glassMsg;
        try {
            Log.v(TAG, "Try contacting service");
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

                            //TODO send settings to glass
                            //sendToGlass(mSettings);

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
                case BluetoothService.GLASS_MESSAGE:
                    try {
                        JSONObject message = new JSONObject((String) msg.obj);
                        Log.v(TAG, message.getString("type"));
                        if(message.getString("type").equals("email")){
                            Email e = new Email(message.getString("emailAddress"),
                                    "Sent From google glass!",message.getString("message"), MainContext);
                            Log.v(TAG, "THIS HAPPENS!!!!!!!");
                            e.sendEmail();
                        }
                        else if(message.getString("type").equals("text")){
                            sendSMS(message);
                        }
                    }
                    catch(JSONException j){
                        Log.e(TAG, j.toString());
                    }
                    Log.v(TAG, "Glass Msg: " + (String)msg.obj);
                    //TODO Rodney: do sth with json string
                    break;
            }
        }
    }
    /*
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
    */

    // open accessory
    /*
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
    */
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
                                byte[] glassmsg = ByteBuffer.allocate(4).putInt(BluetoothService.GLASS_OK).array();
                                sendToGlass(glassmsg);
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

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private class MyGETJSON extends AsyncTask<String, Void, String> {

        String contactString;
        JSONObject mSettings = new JSONObject();
        String settingArray[] = new String[2];

        public void handleContacts(String input) {
            try {
                System.out.println("handleContacts ="+input);
                JSONArray json = new JSONArray(input);
                for(int i = 0; i <json.length();i++){
                    //sendToGlass(json.getJSONObject(i));
                    String name = json.getJSONObject(i).getString("contactName");
                    String email = json.getJSONObject(i).getString("contactEmail");
                    String number = json.getJSONObject(i).getString("contactNumber");
                    contactString = "Contact("+i+") Name: "+name + " Number: "+ number + " Email: " + email;
                    System.out.println(contactString);
                }            /*
            System.out.println("array="+json);

            contactString = "Contacts:";
            for(int i = 0 ; i < json.length(); i++){
                String name = "contact_" + json.getJSONObject(i).getString("contactName") + "_name";
                String number = "contact_" + json.getJSONObject(i).getString("contactNumber") + "_number";
                String email = "contact_" + json.getJSONObject(i).getString("contactEmail") + "_email";
                //Integer games = json.getJSONObject(i).getInt("games");
                contactString = contactString + "Contact Name: " + name + EOL + "Contact Number " + number + EOL + "Contact Email: "+ email + EOL;
                System.out.println(contactString);
            }
            */
            } catch (Exception e) {
                System.out.println("Exception "+e.getMessage());
            }
        }

        @Override
        public String doInBackground(String... params) {
            String script = null;
            String urlString = "http://glassbackend-12366.onmodulus.net/api";
            String contactURL = urlString + "/contacts";
            String messageURL = urlString + "/messages";
            String responseString = "";

            //contactArray = new JSONArray;
            for(String whatever : params){
                System.out.println("P="+whatever);
                script = whatever;
            }
            try {
                HttpClient httpclient = new DefaultHttpClient();
                URI contactWebsite = new URI(contactURL);
                URI messageWebsite = new URI(messageURL);
                HttpGet getContacts = new HttpGet();
                //HttpGet getMessages = new HttpGet();
                //getMessages.setURI(messageWebsite);
                getContacts.setURI(contactWebsite);
                HttpResponse contactResponse = httpclient.execute(getContacts);
                //HttpResponse messageResponse = httpclient.execute(getMessages);
                //StatusLine messageStatusLine = messageResponse.getStatusLine();
                StatusLine contactStatusLine = contactResponse.getStatusLine();
                System.out.println("SL="+contactStatusLine);
                if(contactStatusLine.getStatusCode() == HttpStatus.SC_OK){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    contactResponse.getEntity().writeTo(out);
                    out.close();
                    responseString = out.toString();
                    System.out.println("Response\n");
                    System.out.println(responseString);
                    settingArray[0] = responseString;
                    //if ( script.startsWith("messages")) handleMessages(responseString);
                    //if ( script.startsWith("settings")) handleSettings(responseString);
                } else {
                    //Closes the connection.
                    contactResponse.getEntity().getContent().close();
                    throw new IOException(contactStatusLine.getReasonPhrase());
                }
                /*
                if(messageStatusLine.getStatusCode() == HttpStatus.SC_OK){
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    messageResponse.getEntity().writeTo(out);
                    out.close();
                    responseString = out.toString();
                    System.out.println("Response\n");
                    System.out.println(responseString);
                    settingArray[1] = responseString;
                } else {
                    //Closes the connection.
                    messageResponse.getEntity().getContent().close();
                    throw new IOException(messageStatusLine.getReasonPhrase());
                }
                */
            } catch (Exception e) {
                System.out.println("Exception "+e.getMessage());
            }
            return responseString;
        }


        @Override
        protected void onPostExecute(String result) {
            Log.v(TAG, "in post");
            int num_contacts = 0;
            for (int i = 0; i < settingArray.length; i++) {
                System.out.println(i + settingArray[i]);
            }
            JSONObject webSettings = new JSONObject();
            JSONArray jsonContacts = null;
            try {
                jsonContacts = new JSONArray(settingArray[0]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            System.out.println("Contacts: " + jsonContacts.length());
            try {
                webSettings.put(SCROLL_SPEED_KEY, 5);
                webSettings.put(NUM_CONTACTS_KEY, jsonContacts.length() );
                num_contacts = webSettings.getInt(NUM_CONTACTS_KEY);
                for (int i = 0; i < num_contacts; i++) {
                    String name_key = "contact_" + i + "_name";
                    String number_key = "contact_" + i + "_number";
                    webSettings.put(name_key, jsonContacts.getJSONObject(i).getString("contactName"));
                    webSettings.put(number_key, jsonContacts.getJSONObject(i).getString("contactNumber"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            System.out.println("Websettings: " + webSettings);

            if(num_contacts != 0){
                Toast.makeText(getApplicationContext(),
                        "Sent " + num_contacts + " contacts to Glass", Toast.LENGTH_SHORT).show();
               sendToGlass(webSettings);
            }
        }
    }


}
