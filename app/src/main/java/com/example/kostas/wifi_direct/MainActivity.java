package com.example.kostas.wifi_direct;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Environment;
import android.os.Handler;
import android.os.StrictMode;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;

import android.util.Log;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager mManager;
    private Channel mChannel;
    private boolean shown;
    private final int port = 6000;
    private ServerSocket serverSocket;
    private Socket clientSocket,socketRequest;
    private boolean requestFromClient;
    public ListView msgView;
    public int Throughput;
    public ArrayAdapter<String> msgList;
    readingThread rThread;
    int numOfConnections=0;
    Handler updateMsgs;
    boolean isOwner=false;
    boolean connectedToGO=false;
    int previousSize=0;
    int answers,size,thisThr;
    URL url;
    ArrayList<filePart> fileParts = new ArrayList<filePart>();
    ArrayList<String> devices = new ArrayList<String>();
    ArrayList<Socket> connectionList = new ArrayList<Socket>(10);
    ArrayList<device> deviceList = new ArrayList<device>(10);
    private boolean isWifiP2pEnabled = false;
    public static List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private BroadcastReceiver receiver = null;
    ProgressDialog progressDialog = null;
    Context context;
    long start;
    boolean fileFound=false;
    boolean connected=false;
    private NetworkCallback networkCallback;
    private PeerListListener peerListListener;
    private ConnectionInfoListener connectionInfoListener;
    Thread serverThread = null;
    WifiManager wifiManager;//variables
    public void setIsWifiP2pEnabled(boolean _isWifiP2pEnabled) {
        this.isWifiP2pEnabled = _isWifiP2pEnabled;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        shown=false;
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        File direct = new File(Environment.getExternalStorageDirectory()
                + "/shared_files");

        if (!direct.exists()) {
            direct.mkdirs();
        }
        context = getApplicationContext();
        /*
            Peer list listener
         */
        peerListListener = new PeerListListener() {

            @Override
            public void onPeersAvailable(WifiP2pDeviceList peerList) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                int list=0;
                peers.clear();
                peers.addAll(peerList.getDeviceList());
                devices.clear();
                if (peers.size() == 0) {
                    Log.e("ee", "No devices found");
                    return;
                }else{
                    for(int i=0;i<peers.size();i++) {
                        if(!devices.contains(peers.get(i).deviceName)){
                            devices.add(peers.get(i).deviceName);
                        }
                        if(previousSize!=devices.size()) {
                            msgList.add("Device Found: " + devices.get(i));
                        }
                    }
                    previousSize=devices.size();
                }
            }
        };
        connectionInfoListener = new ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(final WifiP2pInfo info) {
                // InetAddress from WifiP2pInfo struct.
                try {
                    InetAddress groupOwnerAddress = InetAddress.getByName(info.groupOwnerAddress.getHostAddress());
                    Toast.makeText(context, "Ip "+groupOwnerAddress.toString(),
                            Toast.LENGTH_SHORT).show();//
                // After the group negotiation, we can determine the group owner.
                if (info.groupFormed && info.isGroupOwner) {
                    serverThread = new Thread(new serverStart());
                    serverThread.start();
                   // updateMsgs.post(new addMsg("Owner"));
                    isOwner=true;
                } else if (info.groupFormed) {
                    new Thread(new joinServer(groupOwnerAddress.toString())).start();
                }
                }catch(UnknownHostException uhe){
                }
            }
        };

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        msgView = (ListView) findViewById(R.id.list);
        msgList = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        msgView.setAdapter(msgList);
        updateMsgs = new Handler();
        requestFromClient=false;

        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo m3G = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);;
        //discover devices
        discover();
        final ImageView download =(ImageView)findViewById(R.id.dl_2);

        /*
        *Download Button
         */
        download.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    String url_s = "http://imageshack.com/a/img537/901/yV8x0G.jpg";
                    //https://imagizer.imageshack.us/v2/800x500q50/442/wowscrnshot021312235737.jpg
                    url = new URL(url_s);
                    //url = new URL("http://download875.mediafire.com/2lw9d3itkcyg/g375bqcu3xyu9mu/VID_20141208_141545.3gp");
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.connect();
                    start = System.currentTimeMillis();
                    String raw = urlConnection.getHeaderField("Content-Disposition");

                    Log.e("e", urlConnection.getContentType());
                    Log.e("e", urlConnection.getResponseCode() + ":" + urlConnection.getResponseMessage());
                    Log.e("e", "" + urlConnection.getContentLength());
                    Log.e("e",urlConnection.getResponseCode() + ":"+urlConnection.getResponseMessage());

                    BufferedInputStream in = new BufferedInputStream(urlConnection.getInputStream(), 4096);
                    Log.e("e",urlConnection.getResponseCode() + ":"+urlConnection.getResponseMessage());

                    size=urlConnection.getContentLength();
                    String fileName="";
                    if(raw != null && raw.indexOf("=") != -1) {
                        Log.e("e","not this");
                        fileName = raw.split("=")[1];
                        if(fileName.length()>2) {
                            fileName = fileName.substring(1, fileName.length() - 1);
                        }
                    } else {
                        Log.e("e","this");
                        fileName = URLUtil.guessFileName(url_s, null, null);
                        // fall back to random generated file name?
                    }
                    Log.e("e","name: " + fileName);
                    final String file_name;
                    if(fileName.length()>0){
                        file_name=fileName;
                    }else{
                        file_name="New File";
                    }
                    msgList.add("File Name: " + fileName);
                    msgList.add("Size: " + size);
                    requestFromClient=false;
                    if(isOwner) {
                        if (!connectionList.isEmpty()) {
                            new Thread(new writingThread(connectionList.get(0), new Messages(fileName, size))).start();
                        } else {
                            msgList.add("No connections");
                        }
                        urlConnection.disconnect();
                    }else{
                        if(connectedToGO){
                            new Thread(new writingThread(clientSocket, new Messages(fileName,url, size))).start();
                        } else {
                            msgList.add("No connections.");
                            Thread thread = new Thread(new Runnable(){
                                @Override
                                public void run(){
                                    try {
                                        byte[] buffer = downloadPart(url, 0, size);
                                        String name = file_name;
                                        File file = new File(Environment.getExternalStorageDirectory() + "/shared_files", name);
                                        file.getParentFile().mkdirs();
                                        file.createNewFile();
                                        FileOutputStream fos = new FileOutputStream(file);
                                        fos.write(buffer);
                                        fos.close();
                                        long elapsedTimeMillis = System.currentTimeMillis()-start;
                                        updateMsgs.post(new addMsg("Done!Time:"+elapsedTimeMillis));
                                        updateMsgs.post(new addMsg("Received File: " + name));
                                    }catch(Exception e){
                                        updateMsgs.post(new addMsg("error: " + e.getMessage()));
                                    }
                                }
                            });
                            thread.start();
                        }
                    }
                }catch(MalformedURLException mue) {
                    mue.printStackTrace();
                    msgList.add("error: " + mue.getMessage());
                }catch(IOException ioe) {
                    ioe.printStackTrace();
                    msgList.add("error: " + ioe.getMessage());
                }catch(Exception e) {
                    e.printStackTrace();
                    msgList.add("error: " + e.getMessage());
                }
            }
        });
        /*
            Connect Button
         */
        final ImageView connect =(ImageView)findViewById(R.id.connect);
        connect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if(peers.size()!=0) {
                    connect(0);
                }else{
                    msgList.add("No Devices Discovered");
                }
            }
        });

        final ImageView home =(ImageView)findViewById(R.id.home);
        home.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                disconnect();
                System.exit(0);
            }
        });
    }
    /*
        Forces the usage of mobile data while wifi is active
     */
    private boolean forceMobileConnectionForAddress(Context context, String address) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (null == connectivityManager) {
            Log.e("e", "ConnectivityManager is null, cannot try to force a mobile connection");
            return false;
        }
        //check if mobile connection is available and connected
        State state = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_HIPRI).getState();
        Log.e("e", "TYPE_MOBILE_HIPRI network state: " + state);
        if (0 == state.compareTo(State.CONNECTED) || 0 == state.compareTo(State.CONNECTING)) {
            return true;
        }
        //activate mobile connection in addition to other connection already activated
        int resultInt = connectivityManager.startUsingNetworkFeature(ConnectivityManager.TYPE_MOBILE, "enableHIPRI");
        Log.e("e", "startUsingNetworkFeature for enableHIPRI result: " + resultInt);
        //-1 means errors
        // 0 means already enabled
        // 1 means enabled
        // other values can be returned, because this method is vendor specific
        if (-1 == resultInt) {
            Log.e("e", "Wrong result of startUsingNetworkFeature, maybe problems");
            return false;
        }
        if (0 == resultInt) {
            Log.e("e", "No need to perform additional network settings");
            return true;
        }

        //find the host name to route
        String hostName = extractAddressFromUrl(address);
        Log.e("e", "Source address: " + address);
        Log.e("e", "Destination host address to route: " + hostName);
        if (TextUtils.isEmpty(hostName)) hostName = address;

        //create a route for the specified address
        int hostAddress = lookupHost(hostName);
        if (-1 == hostAddress) {
            Log.e("e", "Wrong host address transformation, result was -1");
            return false;
        }
        //wait some time needed to connection manager for waking up
        try {
            for (int counter=0; counter<30; counter++) {
                State checkState = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_HIPRI).getState();
                if (0 == checkState.compareTo(State.CONNECTED))
                    break;
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            //nothing to do
        }
        boolean resultBool = connectivityManager.requestRouteToHost(ConnectivityManager.TYPE_MOBILE_HIPRI, hostAddress);
        Log.e("e", "requestRouteToHost result: " + resultBool);
        if (!resultBool)
            Log.e("e", "Wrong requestRouteToHost result: expected true, but was false");

        return resultBool;
    }
    public static String extractAddressFromUrl(String url) {
        String urlToProcess = null;

        //find protocol
        int protocolEndIndex = url.indexOf("://");
        if(protocolEndIndex>0) {
            urlToProcess = url.substring(protocolEndIndex + 3);
        } else {
            urlToProcess = url;
        }

        // If we have port number in the address we strip everything
        // after the port number
        int pos = urlToProcess.indexOf(':');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have resource location in the address then we strip
        // everything after the '/'
        pos = urlToProcess.indexOf('/');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }

        // If we have ? in the address then we strip
        // everything after the '?'
        pos = urlToProcess.indexOf('?');
        if (pos >= 0) {
            urlToProcess = urlToProcess.substring(0, pos);
        }
        return urlToProcess;
    }

    /**
     * T
     * method
     *
     * @param hostname
     * @return -1 if the host doesn't exists, elsewhere its translation
     * to an integer
     */
    private static int lookupHost(String hostname) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }
        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8 )
                |  (addrBytes[0] & 0xff);
        return addr;
    }
    /*
        Peer discovery
     */
    public void discover(){
        onInitiateDiscovery();
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(context, "Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(context, "Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    /*
        Connect to peers
     */
    public void connect(int peer) {
        WifiP2pDevice device = peers.get(peer);
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(context, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    /*
        Disconnect from peers
     */
    public void disconnect() {

        mManager.removeGroup(mChannel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d("e", "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {

                //disconnected
            }

        });
    }
    public void onInitiateDiscovery() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = ProgressDialog.show(MainActivity.this, "Press back to cancel", "finding peers", true,
                true, new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {

                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    /*
        Broadcast Receiver
     */
    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        private WifiP2pManager mManager;
        private Channel mChannel;
        private MainActivity activity;

        /**
         * @param manager WifiP2pManager system service
         * @param channel Wifi p2p channel
         * @param activity activity associated with the receiver
         */
        public WiFiDirectBroadcastReceiver(WifiP2pManager manager, Channel channel,
                                           MainActivity activity) {
            super();
            this.mManager = manager;
            this.mChannel = channel;
            this.activity = activity;
        }
        /*
         * (non-Javadoc)
         * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
         * android.content.Intent)
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    activity.setIsWifiP2pEnabled(true);
                } else {
                    activity.setIsWifiP2pEnabled(false);
                }
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

                // Request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                if (mManager != null) {
                    mManager.requestPeers(mChannel, peerListListener);
                }
                Log.d("ee", "P2P peers changed");


            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mManager == null) {
                    return;
                }

                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

                if (networkInfo.isConnected()) {
                    updateMsgs.post(new addMsg("Connected to WiFi Direct"));
                    // We are connected with the other device, request connection
                    // info to find group owner IP

                    mManager.requestConnectionInfo(mChannel, connectionInfoListener);
                }


            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {


            }
        }
    }
    /*
        Thread for list updates
     */
    class addMsg implements Runnable {
        private String msg;

        public addMsg(String str) {
            this.msg = str;
        }

        @Override
        public void run() {
            msgList.add(msg);
        }

    }
    /*
     * Start Server thread
     */
    class serverStart implements Runnable {
        public void run() {

            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    connectionList.add(serverSocket.accept());
                    new Thread(new readingThread(connectionList.get(numOfConnections))).start();
                    updateMsgs.post(new addMsg("Devices Connected: "+peers.size()));
                    new Thread(new writingThread(connectionList.get(numOfConnections++),new Messages())).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    /*
        Join server thread
     */
    class joinServer implements Runnable {
        InetAddress address;

        public joinServer(String ip){
            try {
                address = InetAddress.getByName(ip.replace("/",""));
            } catch (UnknownHostException e1) {
                updateMsgs.post(new addMsg("invalid host: "+ip));
                //handler.post(new addToast("Invalid ip"));
            }

        }
        @Override
        public void run() {
            try {

                clientSocket = new Socket(address, port);
                connected=true;
                new Thread(new writingThread(clientSocket,new Messages())).start();
                //Otan sundethei kseninaei to reading thread.
                rThread = new readingThread(clientSocket);
                connectedToGO=true;
                new Thread(rThread).start();
            } catch (UnknownHostException e1) {
                Log.e("e","Invalid ip");
            } catch (Exception e1) {
                Log.e("e", "Exception " + e1.getMessage());
            }
        }
    }
    /*
     * Thread that sends messages to clients
     */
    class writingThread implements Runnable {
        private Socket connection;
        private ObjectOutputStream oOut;
        public transient Messages loc;

        public writingThread(Socket connection,Messages loc){
            this.connection = connection;
            this.loc=loc;
            try{
                oOut = new ObjectOutputStream(this.connection.getOutputStream());
            }catch(IOException ioe){
                ioe.printStackTrace();
            }
        }
        public void run(){
            try {
                oOut.writeObject(loc);
                oOut.flush();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }//
        }
    }
    /*
	 * Thread that receives messages from clients
	 */
    class readingThread implements Runnable {
        private Socket clientSocket;
        private ObjectInputStream oIn;
        private Messages read;
        private boolean controlFlag=false;
        public readingThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                oIn= new ObjectInputStream( this.clientSocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
                updateMsgs.post(new addMsg("error "+e.getMessage()));
            }catch (Exception e) {
                e.printStackTrace();
                updateMsgs.post(new addMsg("error "+e.getMessage()));
            }
        }
        //
        public void run() {
            thisThr=calcThroughput();
            while (!Thread.currentThread().isInterrupted()) {
                try {

                    read = (Messages)oIn.readObject();
                    //Reads the flag of incoming massages
                    if(read.flag.compareTo("name")==0){
                        fileFound=false;
                        answers =0;
                        int thr=0;
                        File extStore = Environment.getExternalStorageDirectory();
                        File myFile = new File(extStore.getAbsolutePath() + "/shared_files/"+read.name);
                        File directory = new File(extStore.getAbsolutePath() + "/shared_files/"+read.name);
                        if(myFile.exists() && myFile.length()==read.size){
                            byte [] buffer = new byte[(int)myFile.length()];
                            FileInputStream fis = new FileInputStream(myFile);
                            BufferedInputStream bis = new BufferedInputStream(fis);
                            bis.read(buffer,0,buffer.length);
                            new Thread(new writingThread(clientSocket, new Messages(read.name,buffer))).start();
                            //send file
                        }else{
                            new Thread(new writingThread(clientSocket, new Messages(thisThr,read.name))).start();
                        }
                    }
                    if(read.flag.compareTo("dl_part")==0){
                        int from=read.from;
                        int to=read.to;
                        String name=read.name;
                    }//
                    if(read.flag.compareTo("file_part")==0){
                        int from=read.from;
                        int to=read.to;
                        String name = read.name;
                        byte [] buffer=read.buffer;
                        fileParts.add(new filePart(buffer,from,to));
                        if(fileParts.size()==deviceList.size()+1){
                            //sort fileparts
                            Collections.sort(fileParts);

                            File file = new File(Environment.getExternalStorageDirectory() + "/shared_files", name);
                            file.getParentFile().mkdirs();
                            file.createNewFile();
                            FileOutputStream fos = new FileOutputStream(file);
                            for(int i = 0 ;i<fileParts.size();i++) {
                                //updateMsgs.post(new addMsg("Writing to file: " + fileParts.get(i).from+"-"+fileParts.get(i).to));
                                byte[] temp = fileParts.get(i).buffer;
                                fos.write(temp);
                            }
                            fos.close();
                            if(requestFromClient){
                                if(file.exists()){
                                    byte [] buffer_2 = new byte[(int)file.length()];
                                    FileInputStream fis = new FileInputStream(file);
                                    BufferedInputStream bis = new BufferedInputStream(fis);
                                    bis.read(buffer_2,0,buffer_2.length);
                                    new Thread(new writingThread(socketRequest, new Messages(read.name,buffer_2))).start();
                                    //send file
                                }
                            }else{
                                 long elapsedTimeMillis = System.currentTimeMillis()-start;
                                 updateMsgs.post(new addMsg("Done!Time:"+elapsedTimeMillis));
                                 updateMsgs.post(new addMsg("Received File: "+name));
                                 break;

                            }
                            requestFromClient=false;

                        }
                    }//
                    if(read.flag.compareTo("range_req")==0){
                        int from=read.from;
                        int to=read.to;
                        url = read.url;
                        byte [] buffer;
                        buffer=downloadPart(url,from,to);
                        new Thread(new writingThread(clientSocket, new Messages(read.name,buffer,from,to))).start();
                    }
                    if(read.flag.compareTo("thrput")==0){
                        answers++;
                        int device_counter=0;
                        int sum=0;
                        deviceList.add(new device(this.clientSocket,read.thr));
                        if(!fileFound && deviceList.size()==numOfConnections){
                            int []parts=new int[(deviceList.size()+1)*2];

                            sum=thisThr;
                            for(int i=0;i<deviceList.size();i++) {
                                sum = sum + deviceList.get(i).thr;
                            }
                            //first part to group owner
                            parts[0]=0;
                            parts[1]=(int)(size*((float)thisThr/(float)sum));
                            //second to n-th part
                            for(int i=2;i<(deviceList.size()+1)*2;i=i+2){
                                parts[i]=parts[i-1]+1;
                                parts[i+1]=parts[i]+(int)(size*((float)deviceList.get(device_counter).thr/(float)sum));
                                if(device_counter==deviceList.size()-1){
                                    parts[i+1]=size;
                                }
                                device_counter++;
                            }
                            device_counter=0;

                            for(int i=2;i<parts.length-1;i=i+2) {
                                new Thread(new writingThread(deviceList.get(device_counter).socket, new Messages(url,read.name,parts[i],parts[i+1]))).start();
                            }
                            byte [] buffer;
                            buffer=downloadPart(url,parts[0],parts[1]);

                            fileParts.add(new filePart(buffer,parts[0],parts[1]));
                        }
                    }
                    if(read.flag.compareTo("compl_file")==0){
                        if(!fileFound) {
                            fileFound = true;
                            byte[] buffer = read.buffer;
                            String name = read.name;
                            File file = new File(Environment.getExternalStorageDirectory() + "/shared_files", name);
                            file.getParentFile().mkdirs();
                            file.createNewFile();
                            FileOutputStream fos = new FileOutputStream(file);
                            fos.write(buffer);
                            fos.close();
                            long elapsedTimeMillis = System.currentTimeMillis()-start;
                            updateMsgs.post(new addMsg("Done!Time:"+elapsedTimeMillis));
                            updateMsgs.post(new addMsg("Received File: "+name));
                            break;
                        }
                    }
                    if(read.flag.compareTo("file_req")==0){
                        fileFound=false;
                        answers =0;
                        url=read.url;
                        int thr=0;
                        size=read.size;
                        File extStore = Environment.getExternalStorageDirectory();
                        File myFile = new File(extStore.getAbsolutePath() + "/shared_files/"+read.name);
                        File directory = new File(extStore.getAbsolutePath() + "/shared_files/"+read.name);
                        if(myFile.exists() && myFile.length()==read.size){
                            byte [] buffer = new byte[(int)myFile.length()];
                            FileInputStream fis = new FileInputStream(myFile);
                            BufferedInputStream bis = new BufferedInputStream(fis);
                            bis.read(buffer,0,buffer.length);
                            new Thread(new writingThread(clientSocket, new Messages(read.name,buffer))).start();
                            //send file
                        }else {

                            socketRequest = this.clientSocket;
                            requestFromClient = true;
                            if (isOwner) {
                                if (!connectionList.isEmpty()) {
                                    new Thread(new writingThread(connectionList.get(0), new Messages(read.name, size))).start();
                                } else {
                                    updateMsgs.post(new addMsg("No Connections"));
                                }
                            }
                        }
                    }
                    if(read.flag.compareTo("terminate")==0){
                        break;
                    }
                    oIn= new ObjectInputStream( this.clientSocket.getInputStream());
                } catch (IOException e) {

                    break;
                } catch (ClassNotFoundException e) {

                    break;
                }catch (Exception e) {

                    break;
                }
            }
            updateMsgs.post(new addMsg("Connection Ended"));
        }
    }
    /*
        Throughput calculation
     */
    public int calcThroughput(){
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo m3G = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);;
            if(m3G!=null) {
              forceMobileConnectionForAddress(context,"www.google.com");
            }
            URL url = new URL("http://www.google.com");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            String raw = urlConnection.getHeaderField("Content-Disposition");
            urlConnection.connect();
            Log.e("e","Respnse Code: " + urlConnection.getResponseCode());
            Log.e("e","Content-Length: " + urlConnection.getContentLength());

            InputStream inputStream = urlConnection.getInputStream();
            long size = 0;
            long start = System.currentTimeMillis();
            // make GET request to the given URL
            while(inputStream.read() != -1 )
                size++;
            // Time Functions After
            long elapsedTimeMillis = System.currentTimeMillis()-start;

            Throughput = (int)(size/((float)elapsedTimeMillis/1000));
        }catch(MalformedURLException mue) {
            Log.e("e","error1");
            updateMsgs.post(new addMsg("error:"+mue.getMessage()));
            mue.printStackTrace();
        }catch(Exception ioe) {
            Log.e("e",ioe.toString());
            updateMsgs.post(new addMsg("error:"+ioe.getMessage()));
            ioe.printStackTrace();
        }
        updateMsgs.post(new addMsg("Ready"));
        return  Throughput;
    }
    /*
        Download a part of the file using 3g (if available)
     */
    public byte[] downloadPart(URL url,int from ,int to){
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo m3G = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);;
        try {
            if(m3G!=null) {
                updateMsgs.post(new addMsg("3g usage:"+forceMobileConnectionForAddress(context,"http://imageshack.com")));
            }

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            if(to>0) {
                urlConnection.setRequestProperty("Range", "bytes=" + from + "-" + to);
            }
            urlConnection.connect();
            InputStream inputStream = urlConnection.getInputStream();
            ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
            long size = 0;
            int bytesRead = -1;
            updateMsgs.post(new addMsg("Downloading "+from+"-"+to));

            int nRead;
            byte[] data = new byte[to-from];
            int sum=0;

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                 outBuffer.write(data, 0, nRead);
                 sum=sum+nRead;
            }
            outBuffer.flush();
            updateMsgs.post(new addMsg("Downloaded: "+sum));

            inputStream.close();
            return outBuffer.toByteArray();
        }catch(Exception e){
            updateMsgs.post(new addMsg("error in part:"+e.getMessage()));
             return null;
        }

    }

}
