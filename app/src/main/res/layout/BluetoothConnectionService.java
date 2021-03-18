package com.example.android_bluetoothchat;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.nfc.Tag;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import static java.util.Calendar.SECOND;

public class BluetoothConnectionService {

    private static final String TAG = "TAG";

    private static final String appName = "MYAPP";

    //private static final UUID MY_UUID_INSECURE =
    //        UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter mBluetoothAdapter;
    Context mContext;

    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private BluetoothDevice mmDevice;
    private UUID deviceUUID;
    Calendar calendar;
    ProgressDialog mProgressDialog;

    private ConnectedThread mConnectedThread;

    public BluetoothConnectionService(Context context) {
        mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    /**
     *This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * or until it is cancelled
     */
    private class AcceptThread extends Thread{
        //local service socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread(){
            BluetoothServerSocket tmp = null;

            //create a new listening server socket
            try {
                tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID_INSECURE);
                Log.d(TAG, "AcceptThread: Setting up server using" + MY_UUID_INSECURE);
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: IOException: " + e.getMessage());
            }

            mmServerSocket = tmp;
        }

        public void run(){
            Log.d(TAG, "run: AcceptThread running");

            BluetoothSocket socket = null;

            try {
                //this is a blocking call and will only return on
                //successful connection or an exception
                Log.d(TAG, "run: RFCOM server socket start....");
                socket = mmServerSocket.accept();
                Log.d(TAG, "run: RFCOM server socket accepted connection");
            } catch (IOException e) {
                Log.e(TAG, "AcceptThread: IOException: " + e.getMessage());
            }

            if (socket != null){
                    connected(socket, mmDevice);
            }

            Log.i(TAG, "END mAcceptThread");
        }

        public void cancel(){
            Log.d(TAG, "cancel: Cancelling AcceptThread");
            try{
                mmServerSocket.close();
            } catch (IOException e){
                Log.e(TAG, "cancel: Close of AcceptThread Server Failed. " + e.getMessage());
            }
        }
    }

    /**
     * This thread runs while aacepting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails
     */
    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Log.d(TAG, "ConnectThread: started");
            mmDevice = device;
            deviceUUID = uuid;
        }

        public void run(){
            BluetoothSocket tmp = null;
            Log.i(TAG, "RUN mConnetThread");

            //get a BluetoothSocket for a connection with the
            //given BluetoothDevice

            try {
                Log.d(TAG, "ConnectThread: trying to create InsecureRfcommSocket using my UUID"
                + MY_UUID_INSECURE);
                tmp = mmDevice.createRfcommSocketToServiceRecord(deviceUUID);
            } catch (IOException e) {
                Log.e(TAG, "ConnectThread: could not create InsecureRfcommSoccet " + e.getMessage());
            }

            mmSocket = tmp;

            //cancel discovery, cause it slows down the connection
            mBluetoothAdapter.cancelDiscovery();

            //make a connection to the BluetoothSocket

            try {
                //This is a blocking call and will return only a
                //successful connection or an exception
                mmSocket.connect();

                Log.d(TAG, "run: COnnectThread connected");
            } catch (IOException e) {
                //close socket
                try {
                    mmSocket.close();
                    Log.d(TAG, "run: Closed socket");
                } catch (IOException ioException) {
                    Log.e(TAG, "mConnectThread: run: Unable to close the socket " + ioException.getMessage());
                }
                Log.d(TAG, "Could not connect to UUID" + MY_UUID_INSECURE);
            }

            connected (mmSocket, mmDevice);
        }

        public void cancel(){
            Log.d(TAG, "cancel: Closing Client Socket");
            try{
                mmSocket.close();
            } catch (IOException e){
                Log.e(TAG, "cancel: close() of mmSocket in ConnectThread Failed. " + e.getMessage());
            }
        }
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start(){
        Log.d(TAG, "Start");

        //cancel any thread attempting to make a connection
        if (mConnectThread != null){
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mInsecureAcceptThread == null){
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    /**
     * AcceptThread starts and sits waiting for a connection
     * Then COnnectThread starts and attempts to make a connection with the other devices from AcceptTHread
     */

    public void startClient(BluetoothDevice device, UUID uuid){
        Log.d(TAG, "startClient: Started");

        //initprogress dialog
        mProgressDialog = ProgressDialog.show(mContext, "Connecting Bluetooth",
                "Please, wait...", true);

        mConnectThread = new ConnectThread(device, uuid);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInputStream;
        private final OutputStream mmOutputStream;

        public ConnectedThread (BluetoothSocket socket){
            Log.d(TAG, "ConnectedThread: Starting");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            //dismiss the progressdialog when connection is established
            mProgressDialog.dismiss();

            try {
                tmpIn = mmSocket.getInputStream();
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "ConnectedThread: could not get input/output stream");
            }

            mmInputStream = tmpIn;
            mmOutputStream = tmpOut;
        }

        public void run(){
            byte[] buffer = new byte[1024];

            int bytes; //bytes returned from read()

            String fullString = "";
            calendar = Calendar.getInstance();

            //Keep listening to the InputStream until an exception occurs
            while (true){
                //read from the InputStream
                try {
                    bytes = mmInputStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                    fullString += incomingMessage;
                    Log.d(TAG, "InputStream: " + incomingMessage);
                    if (fullString.endsWith("?")){
                        break;
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Received message: " + fullString);
                    Log.e(TAG, "write: Error reading inputstream. " + e.getMessage());
                    break;
                    //break if an ecxeption occurs
                }
            }
            //array[0] - период снятия
            //array[1] - кодировка единиц измерения
            //array[2] - диапазон
            //array[3] - сами значения (с ? в конце)
            String[] array = fullString.split("\\u000A");
            String[] arrayData = array[3].split(" ");
            arrayData[arrayData.length - 1] = calendar.toString();
            Log.d(TAG, "Data transfer start time: " + calendar.toString());
            Calendar roundDate = new GregorianCalendar(2021,03,17,16,39,00);
            roundDate.set(roundDate.SECOND, - 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, roundDate.getTime().toString());
            }
            /*
            for (int i = 0; i < arrayData.length - 1; i++){
                Log.d(TAG, arrayData[i]);
            }*/
        }

        //call this from the main activity to send a message
        public void write(byte[] bytes){
            String text = new String(bytes, Charset.defaultCharset());
            Log.d(TAG, "write: writing to outputstream: " + text);
            try {
                mmOutputStream.write(bytes);
            } catch (IOException e) {
                Log.e(TAG, "write: Error writing to outputstream. " + e.getMessage());
            }
        }

        // call this from the main activity to shut down the connection
        public void cancel(){
            try{
                mmSocket.close();
            } catch (IOException e){

            }
        }
    }

    private void connected(BluetoothSocket mmSocket, BluetoothDevice mmDevice){
        Log.d(TAG, "connected: Starting");

        //start the thread to manage the connection and perform transmission
        mConnectedThread = new ConnectedThread(mmSocket);
        mConnectedThread.start();
    }

    public void write (byte[] out){
        //create temporary object
        ConnectedThread r;

        // Synchronize a copy og the ConnectionThread
        Log.d(TAG, "write: Write Called");
        mConnectedThread.write(out);
    }

}








