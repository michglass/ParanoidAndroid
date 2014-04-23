package com.abq.paranoidandroid.paranoidandroid;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Oliver
 * Date: 3/15/14.
 */
public class BluetoothService extends Service {

    // Debug
    private final static String TAG = "Bluetooth Service Android";

    // Unique UUID of this app (same as UUID on Glass)
    private static final UUID btUUID = UUID.fromString("bfdd94e0-9a5e-11e3-a5e2-0800200c9a66");

    // Bluetooth Vars
    private final BluetoothAdapter mbtAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice mbtDevice;
    private int mCurrState; // current state of the connection

    // Threads to initiate and handle connection
    private ConnectThread mConnectThread;
    private AcceptThread mAcceptThread;
    private ConnectedThread mConnectedThread;

    // Variables that indicate the Connection State
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTENING = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;
    public static final int BT_DISABLED = 15;
    public static final int NOT_PAIRED = 16;

    // Messages send to Client
    public static final int MESSAGE_STATE_CHANGE = 4;
    public static final int MESSAGE_WRITE = 5;
    public static final int MESSAGE_RESTART = 6;
    public static final int WAIT_FOR_CONNECTION = 7;
    public static final int THIS_STOPPED = 8; // (== ANDROID_STOPPED on Glass)

    // Msgs from client
    public static final int REGISTER_CLIENT = 12;
    public static final int UNREGISTER_CLIENT = 13;

    // Messages from/to Glass
    public static final int GLASS_STOPPED = 9; // (== THIS_STOPPED on Glass)
    public static final int GLASS_DATA = 14; // Indicating to send a message to glass via bt
    public static final int GLASS_MESSAGE = 11; // indicating that we have received a msg from glass
    public static final int GLASS_OK = 10;

    // Service Variables
    // Messenger that gets puplished to client
    private final Messenger mBluetoothServiceMessenger = new Messenger(new ClientHandler());
    private Messenger mClientMessenger; // Messenger to send Messages to Client
    public static int BOUND_COUNT = 0; // indicates how many clients are bound (max = 1)

    // important boolean that make sure that BT is turned on and phone paired to glass
    private boolean isPaired;
    private boolean isBTEnabled;

    /**
     * Service Methods
     * Lifecycle methods
     * Handling messages from Client
     * Sending Messages to Client
     */

    /**
     * Life cycle methods of the Service
     */
    @Override
    public void onCreate() {
        Log.v(TAG, "Create Service");
        super.onCreate();

        if(!AdapterEnabled()) {
            // Should always be enabled!
            Log.v(TAG, "Bluetooth not enabled");

            isBTEnabled = false;
        } else {
            Log.v(TAG, "Bluetooth already enabled"); // usually on Glass

            isBTEnabled = true;

            // find paired devices (returns true if it found glass, false otherwise)
            isPaired = queryDevices();
        }
    }
    /**
     * On Start Command
     * Start up Bluetooth Connection
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "On Start Command: " + startId);

        // send out connection request
        connect();

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * On Bind
     * Client can use Bluetooth Connection
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.v(TAG, "On Bind");
        return mBluetoothServiceMessenger.getBinder();
    }

    /**
     * On Unbind
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(TAG, "On Unbind");

        return true;
    }

    /**
     * On Rebind
     */
    @Override
    public void onRebind(Intent intent) {
        Log.v(TAG, "On Rebind");
        super.onRebind(intent);
    }

    /**
     * On Destroy
     * Let Glass know that the Android app has stopped
     * End Bluetooth connection
     */
    @Override
    public void onDestroy() {
        Log.v(TAG, "Destroy Service");

        // notify glass that android has stopped
        byte[] stopMsg = ByteBuffer.allocate(4).putInt(THIS_STOPPED).array();
        sendToGlass(stopMsg);

        // Disconnect from Bluetooth
        disconnect();

        super.onDestroy();
    }

    /**
     * Client Handler
     * Handles incoming Messages from Client
     * Important messages: GLASS_X messages that get send via Bluetooth to Glass
     */
    private class ClientHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {

                case GLASS_DATA:
                    byte[] glassmsg;
                    if(msg.obj != null) {
                        glassmsg = (byte[]) msg.obj;
                        sendToGlass(glassmsg);
                    }
                    else
                        Log.e(TAG, "MSG for Glass NULL");
                    break;
                case REGISTER_CLIENT:
                    Log.v(TAG, "Register Client");
                    BluetoothService.BOUND_COUNT++;
                    Log.v(TAG, "Bound Clients: " + BluetoothService.BOUND_COUNT);

                    // register Client to be able to send Messages back
                    mClientMessenger = msg.replyTo;
                    Log.v(TAG, "Client Messenger NULL?: " + (mClientMessenger == null));
                    break;
                case UNREGISTER_CLIENT:
                    Log.v(TAG, "Unregister Client");
                    BluetoothService.BOUND_COUNT--;
                    Log.v(TAG, "Bound Clients: " + BluetoothService.BOUND_COUNT);
                    break;
                case MESSAGE_RESTART:
                    Log.v(TAG, "Restart Listening");
                    restartListeningToIncomingRequests();
                    break;
            }
        }
    }
    /**
     * Send message to client (1)
     * Sending a msg that hasn't been received from Glass
     * Connection state msgs for example
     * @param connectionMsg Msg regarding connection
     */
    private void sendMessageToClient(int connectionMsg) {
        Message msg = new Message();
        msg.what = connectionMsg;

        try {
            mClientMessenger.send(msg);
        } catch (RemoteException remE) {
            Log.e(TAG, "Couldn't contact Client");
        }
    }
    /**
     * Send message to client (2)
     * Send a message received from Glass! to client
     * @param msgFromGlass message (as a String) received from glass
     */
    private void sendMessageToClient(String msgFromGlass) {
        Message msg = new Message();
        msg.what = BluetoothService.GLASS_MESSAGE;
        msg.obj = msgFromGlass;
        try {
            mClientMessenger.send(msg);
        } catch (RemoteException remE) {
            Log.e(TAG, "Client Messenger NULL");
        }
    }

    /**
     * Methods to handle the Threads
     * Start Threads
     * Manage Connection
     * Stop Connection/Threads
     */

    /**
     * Connect
     * Start Up Connection by starting ConnectThread
     * Called by Activity onResume
     */
    public void connect() {
        Log.v(TAG, "Connect");

        // Cancel all Threads currently trying to set up a connection
        if(mCurrState == STATE_CONNECTING) {
            if(mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel Thread that currently runs a connection
        if(mConnectedThread != null) {
            mConnectedThread.cancel(); // close socket
            mConnectedThread = null;
        }

        // Start thread to connect to device
        // Device is passed to obtain socket and Handler for sending messages to Activity

        // make sure that bt is enabled and phone is paired with glass
        if(!isBTEnabled) {
            setState(BT_DISABLED);
        } else if(!isPaired) {
            setState(NOT_PAIRED);
        } else {
            // paired and bt enabled, start connecting
            mConnectThread = new ConnectThread(mbtDevice);
            mConnectThread.start();

            // set state to connecting and send message to activity
            setState(STATE_CONNECTING);
        }
    }
    /**
     * Listen to incoming requests
     * If connection failed Glass is set up as a Server
     * Listens to incoming connection requests from Android
     * @param btAdapter The adapter for setting up the Server Socket in the Accept Thread
     */
    private void listenToIncomingRequests(BluetoothAdapter btAdapter) {

        Log.v(TAG, "Listen to incoming Requests");

        // Close all Threads trying to establish a connection
        if(mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Close Threads managing a connection
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Close Threads listening to conection requests
        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // Start listening to incoming requests
        mAcceptThread = new AcceptThread(btAdapter);
        mAcceptThread.start();

        setState(STATE_LISTENING);
    }
    /**
     * Manage Connection
     * Open ConnectedThread to handle the connection
     * @param btSocket Socket that is used in the ConnectedThread
     *                 to get I/O streams
     */
    public void manageConnection(BluetoothSocket btSocket) {
        Log.v(TAG, "start managing connection");

        // close thread that currently runs a connection
        if(mConnectedThread != null) {
            mConnectedThread.cancel(); // closes server socket
            mConnectedThread = null;
        }

        // start thread to manage connection
        mConnectedThread = new ConnectedThread(btSocket);
        mConnectedThread.start();

        // connection was successful, set state to connected
        setState(STATE_CONNECTED);
    }
    /**
     * Disconnect
     * Cancel Sockets
     */
    /**
     * Stop Threads
     * Stop all threads
     * Called by Activity onDestroy
     */
    public void disconnect() {
        Log.v(TAG, "Stop all Threads");

        // cancel connecting thread if it exists
        if(mConnectThread != null) {
            mConnectThread.cancel(); // cancels the socket
            mConnectThread = null;
        }

        // cancel accept thread
        if(mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        // cancel thread running the connection
        if(mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // all threads stopped, set state to none and send message to UI
        if(mCurrState != BluetoothService.STATE_NONE)
            setState(STATE_NONE);
    }
    /**
     * Restart
     * If writing to Glass fails (Glass app shut down) restart in listening mode
     */
    public void restartListeningToIncomingRequests() {
        Log.v(TAG, "Restart Connection");
        // cancel all running threads
        this.disconnect();

        // try listening again
        this.listenToIncomingRequests(mbtAdapter);
    }
    /**
     * Send To Glass
     * Send a generic (== byte array) message to glass
     * @param msgForGlass Byte array we want to send to glass
     */
    public void sendToGlass(byte[] msgForGlass) {
        write(msgForGlass);
    }
    /**
     * Write
     * Write to ConnectedThread (unsynchronized)
     * Write to the OutputStream to Glass
     */
    public void write(byte[] out) {

        if(mConnectedThread != null) // if trying to write out when still listening
            mConnectedThread.write(out);
        else {
            sendMessageToClient(BluetoothService.WAIT_FOR_CONNECTION);
        }
    }


    /**
     * Util Methods
     * Query Devices
     * Set State
     */

    /**
     * Adapter Enabled
     * Checks if the BT Adapter is enabled
     * @return True if Adapter is enabled, false otherwise
     */
    public boolean AdapterEnabled() {
        return this.mbtAdapter.isEnabled();
    }
    /**
     * Query Devices
     * Find Glass
     */
    public boolean queryDevices() {
        Log.v(TAG, "Query devices");
        // get all paired devices
        Set<BluetoothDevice> pairedDevices = mbtAdapter.getBondedDevices();
        Log.v(TAG, mbtAdapter.getName());
        try {
            if(pairedDevices != null) {
                // start looking only if there's at least one device
                if(pairedDevices.size() > 0) {
                    // find specific Device (Glass)
                    for (BluetoothDevice btDevice : pairedDevices) {
                        // if device is found save it in member var
                        if (pairedDevices.size() > 1) {
                            Log.e(TAG, "Paired to more than One Device");
                            return false;
                        } else {
                            mbtDevice = btDevice;
                            Log.v(TAG, "Device Name: " + mbtDevice.getName());
                        }
                    }
                } else {
                    Log.e(TAG, "Paired Devices > 0");
                    return false;
                }
            } else {
                Log.e(TAG, "Paired Devices == NULL");
                return false;
            }
        } catch (Exception e) {
            Log.v(TAG, "No devices found");
            return false;
        }
        // if none of the exceptions occured, we are paired
        return true;
    }
    /**
     * Set State
     * Change State of Connection
     * Send message to Main Activity
     * @param toState New State of Connection
     */
    public void setState(int toState) {
        Log.v(TAG, "State changed from " + mCurrState + "-->"+ toState);
        mCurrState = toState;

        // send Message to bound client to indicate connection state change
        Message msg = new Message();
        msg.what = MESSAGE_STATE_CHANGE;
        msg.arg1 = toState;

        if(mClientMessenger != null) {
            try {
                mClientMessenger.send(msg);
            } catch (RemoteException remE) {
                Log.e(TAG, "Couldn't contact Client");
            }
        } else { Log.e(TAG, "Client Messenger NULL"); }
    }


    /**
     * Connect Thread
     * Send out connection request
     * Start ConnectedThread
     */
    private class ConnectThread extends Thread {
        // Debug
        private static final String TAG = "Connect Thread";

        // Bluetooth Vars
        private final BluetoothSocket mmBtSocket;

        /**
         * Constructor
         * Set up the device
         * Set up the Handler (sending messages back to Main Activity)
         * Set up Socket
         * @param device Bluetooth device variable (e.g. Grace's phone)
         */
        public ConnectThread(BluetoothDevice device) {
            Log.v(TAG, "Constructor");

            BluetoothSocket tempSocket = null;

            // set up bluetooth socket with UUID
            try {
                Log.v(TAG, "Try setting up Socket");
                // returns a BT Socket ready for outgoing connection
                tempSocket = device.createRfcommSocketToServiceRecord(btUUID);
            } catch (Exception e) {
                Log.e(TAG, "Socket Setup Failed");
            }
            mmBtSocket = tempSocket;
        }
        /**
         * Run
         * Send out request for connection
         * When connection established, start Manage Connection
         */
        @Override
        public void run() {

            boolean connected = true; // indicates if we connected successfully

            Log.v(TAG, "Run");
            // Connect device through Socket
            // Blocking call!
            try {
                Log.v(TAG, "Try connecting through socket");
                mmBtSocket.connect();
            } catch (IOException connectException) {
                // try closing socket
                Log.v(TAG, "Unable to Connect");

                // unable to connect, start listening to incoming requests
                connected = false;
                listenToIncomingRequests(mbtAdapter);

                try {
                    Log.v(TAG, "Try Closing Socket");
                    mmBtSocket.close();
                } catch (IOException ioE) {
                    Log.e(TAG, "Closing Socket Failed");
                }
            }

            if(connected) {
                // connection established, manage connection
                manageConnection(mmBtSocket);
                Log.v(TAG, "Run Return after Success");
            }
        }
        /**
         * Cancel
         * Close Socket
         */
        public void cancel() {
            try {
                Log.v(TAG, "Try Closing Socket");
                mmBtSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing Socket Failed", e);
            }
        }
    }
    /**
     * Accept Thread
     * Listens to incoming connection requests
     * Initiates ConnectedThread
     */
    private class AcceptThread extends Thread {

        // Debug
        private static final String TAG = "Accept Thread";

        // Bluetooth variables
        private final BluetoothServerSocket mBTServerSocket; // only used to listen for incoming requests
        private final BluetoothAdapter mBTAdapter;

        /**
         * Constructor
         * Set up BTAdapter
         * @param btAdapter Bluetooth Adapter
         */
        public AcceptThread(BluetoothAdapter btAdapter) {
            Log.v(TAG, "Constructor");

            mBTAdapter = btAdapter;

            // mmBTServerSocket is final -> use temp socket
            BluetoothServerSocket tempServSocket = null;
            try {
                tempServSocket = mBTAdapter.
                        listenUsingRfcommWithServiceRecord("Android Bluetooth", btUUID);
            } catch (IOException ioE) {
                Log.e(TAG, "Can't set up Server Socket", ioE);
            }
            // if successful assign mmBTServerSocket
            mBTServerSocket = tempServSocket;
        }
        /**
         * Run
         * Listen to an incoming connection request
         * Start ConnectedThread if connection successful
         */
        @Override
        public void run() {
            Log.v(TAG, "Run");

            // set up socket that will manage the connection
            BluetoothSocket btSocket;

            // keep listening until socket returned by accept or exception occurs
            while (true) {

                try {
                    Log.v(TAG, "Listen to incoming request");
                    btSocket = mBTServerSocket.accept();
                } catch (IOException ioE) {
                    Log.e(TAG, "Listening failed", ioE);
                    Log.v(TAG, "Run Return Fail");
                    break;
                }
                // if a connection was accepted
                // btSocket is already connected (no need to call connect())
                if(btSocket != null) {
                    Log.v(TAG, "Connection Accepted");
                    // start managing connection
                    manageConnection(btSocket);
                    // Server Socket no longer needed
                    try {
                        mBTServerSocket.close();
                    } catch (IOException ioE) {
                        Log.e(TAG, "Closing Server Socket failed", ioE);
                        break;
                    }
                    // break loop if connection successful
                    break;
                }
            }
            Log.v(TAG, "Run Return");
        }
        /**
         * Cancel
         * Closes the Server Socket (listening socket)
         */
        public void cancel() {
            try {
                Log.v(TAG, "Try closing Server Socket");
                mBTServerSocket.close();
            } catch (IOException ioE) {
                Log.e(TAG, "Closing Server Socket failed", ioE);
            }
        }
    }
    /**
     * Connected Thread
     * Manages connection
     * Writes to OutStream
     */
    private class ConnectedThread extends Thread {

        // Debug
        private static final String TAG = "Connected Thread";

        // BT member vars
        private final BluetoothSocket mmBTSocket;
        private final OutputStream mmOutStream;
        private final InputStream mmInStream;


        /**
         * Constructor
         * Set up Socket
         * Get OutputStream
         * @param btSocket Socket to get I/O Streams
         */
        public ConnectedThread(BluetoothSocket btSocket) {
            Log.v(TAG, "Constructor");

            mmBTSocket = btSocket;
            OutputStream tempOut = null;
            InputStream tempIn = null;

            // try getting the output stream
            try {
                Log.v(TAG, "Try getting Out Stream");
                tempOut = btSocket.getOutputStream();
                tempIn = btSocket.getInputStream();
            } catch (IOException ioE) {
                Log.e(TAG, "failed getting outStream");
            }
            // if successful assign to mmOutStream
            mmOutStream = tempOut;
            mmInStream = tempIn;
        }
        /**
         * Run
         * Listen to Glass Input constantly
         * Glass sends message when it shuts down
         */
        @Override
        public void run() {
            Log.v(TAG, "Run");

            byte[] buffer = new byte[1024]; // input buffer that stores the message from glass
            int bytes;

            while(true) {
                Log.v(TAG, "Loop Connected Thread");
                try {
                    bytes = mmInStream.read(buffer);
                    Log.v(TAG, "Message from Glass length: " + bytes);
                    ByteBuffer wrapper = ByteBuffer.wrap(buffer);
                    int check = wrapper.getInt();
                    if(check == GLASS_STOPPED) {
                        sendMessageToClient(GLASS_STOPPED);
                        Log.v(TAG, "Glass Stopped Message received: " + check);
                    } else {
                        String msg = new String(buffer,0,bytes);
                        sendMessageToClient(msg);
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Failed reading from Glass", e);
                    break;
                }
            }
            Log.v(TAG, "Run Return");
        }
        /**
         * Write
         * Write bytes to OutputStream
         * Send Message to Main Activity
         * @param bytes Bytes to write out
         */
        public void write(byte[] bytes) {
            Log.v(TAG, "Write out");
            try {
                mmOutStream.write(bytes);
                // send message to Main Activity
                sendMessageToClient(BluetoothService.MESSAGE_WRITE);
                // sendMessageToClient(INT_MESSAGE, BluetoothService.MESSAGE_WRITE);
            } catch (IOException ioE) {
                Log.e(TAG, "Write Failed", ioE);

                // send message to main activity to restart listening
                // sendMessageToClient(INT_MESSAGE, BluetoothService.MESSAGE_RESTART);
            }
        }
        /**
         * Call from activity to shut down connection
         */
        public void cancel() {

            try {
                Log.v(TAG, "Try closing Socket");
                mmBTSocket.close();
            } catch (IOException ioE) {
                Log.e(TAG, "closing socket failed", ioE);
            }
        }
    }
}
