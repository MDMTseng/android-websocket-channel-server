package com.ws_inter.mdm.websocket_inter;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.text.format.Formatter;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class WebSService extends Service {
    public static final String TAG = "MyService";
    WebSock websocket = null;
    Service MainService = this;

    static class connectionGroup {
        public class WebSocketMember {
            String name;
            WebSocket WS;

            WebSocketMember(String Name, WebSocket WS) {
                this.name = Name;
                this.WS = WS;
            }
        }

        String name = null;
        String password = null;
        private ArrayList<WebSocketMember> member = null;

        connectionGroup(String name, String password) {
            this.name = name;
            this.password = password;
            member = new ArrayList<WebSocketMember>();
        }

        public boolean contains(WebSocketMember WSM) {
            return member.contains(WSM);
        }

        public WebSocketMember contains(WebSocket WS) {
            for (WebSocketMember WSM : member) {
                if (WSM.WS == WS) return WSM;
            }

            return null;
        }

        public WebSocketMember contains(String name) {
            for (WebSocketMember WSM : member) {
                if (WSM.name.contentEquals(name)) return WSM;
            }

            return null;
        }

        public boolean add(WebSocketMember WSM, String password) {


            if (this.password == null || this.password.contentEquals(password))
                return member.add(WSM);
            return false;
        }


        public boolean add(String name, WebSocket WS, String password) {

            return add(new WebSocketMember(name, WS), password);
        }

        public void sendTo(String name, String text) {
            synchronized (member) {
                for (WebSocketMember c : member) {
                    if (c.name.contentEquals(name)) {
                        c.WS.send(text);
                        break;
                    }
                }
            }
        }

        public void sendToOthersStartWith(WebSocket ex,String name, String text) {
            synchronized (member) {
                for (WebSocketMember c : member) {
                    if (c.WS!=ex&&c.name.startsWith(name))
                        c.WS.send(text);
                }
            }
        }

        public void sendToOthers(WebSocket ex, String text) {
            synchronized (member) {
                for (WebSocketMember c : member) {
                    if (ex != c.WS)
                        c.WS.send(text);
                }
            }
        }

        public void sendToOthers(WebSocketMember ex, String text) {
            synchronized (member) {
                for (WebSocketMember c : member) {
                    if (ex != c)
                        c.WS.send(text);
                }
            }
        }

        public void sendToAll(String text) {
            synchronized (member) {
                for (WebSocketMember c : member) {
                    c.WS.send(text);
                }
            }
        }
    }

    ArrayList<connectionGroup> connectionGroups = null;
    Hashtable<WebSocket, connectionGroup> FastLookTable = null;

    public WebSService() {

        Log.d(TAG, "WebSService() executed");

    }


    @Override
    public void onCreate() {
        super.onCreate();

    }

    connectionGroup findWSGroup(ArrayList<connectionGroup> cGList, String name) {
        for (connectionGroup cG : cGList) {
            if (cG.name.equals(name)) {
                return cG;
            }
        }
        return null;
    }

    boolean startWS(int port) {
        try {
            websocket = new WebSock(port);
            websocket.start();
            return true;
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        websocket = null;
        return false;
    }

    boolean stopWS() {
        if (websocket != null) {
            try {
                websocket.stop();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        return false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand() executed");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopWS();
        super.onDestroy();
        Log.d(TAG, "onDestroy() executed");
    }

    private MyBinder mBinder = new MyBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    WebSocket connClient = null;

    class WebSock extends WebSock_ {
        public WebSock(int port) throws UnknownHostException {
            super(new InetSocketAddress(port));
        }


        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            String ResourceStr = handshake.getResourceDescriptor();
            String ChannelName = null;
            int QM = ResourceStr.lastIndexOf('?');
            if (QM == -1) ChannelName = ResourceStr.substring(1);
            else
                ChannelName = ResourceStr.substring(1, QM);


            String[] Configs = ResourceStr.substring(QM + 1).split("&");
            Log.d(TAG + "::onOpenResourceStr", ChannelName + "::::");
            String connectorName = null;
            String connectorPWD = null;
            for (String conf : Configs) {
                Log.d(TAG + "::onOpenResourceStr", conf);
                if (conf.startsWith("name"))
                    connectorName = conf.substring(5);
                if (conf.startsWith("pwd"))
                    connectorPWD = conf.substring(4);
            }


            connectionGroup cG = findWSGroup(connectionGroups, ChannelName);
            if (cG == null) {//channel creator
                cG = new connectionGroup(ChannelName, connectorPWD);
                cG.add(connectorName, conn, connectorPWD);
                connectionGroups.add(cG);

            } else if (cG.contains(conn) == null) {//channel member
                if (connectorName != null)//has name
                {
                    connectionGroup.WebSocketMember WSM = cG.contains(connectorName);//check is the name used
                    if (WSM != null)//means same name
                    {
                        //TODO send same name error MSG
                        conn.send(">error:the name has been used>");
                        //conn.close(100);
                        conn.close(-1);
                        return;
                    }
                }
                if (!cG.add(connectorName, conn, connectorPWD)) {//check if join failed=> might be password problem
                    conn.send(">error:wrong password>");
                    conn.close(-1);
                    //conn.close(100);
                    return;
                }

            }
            if (!FastLookTable.containsKey(conn))
                FastLookTable.put(conn, cG);
            Log.d(TAG + "::WSonOpen", "RestGroup::" + connectionGroups.size());
        }

        void removeWebSocketFromGroup(WebSocket conn)
        {
            connectionGroup cG = FastLookTable.get(conn);
            if(cG==null)return;
            connectionGroup.WebSocketMember WSM = cG.contains(conn);
            if (WSM == null) return;
            while (cG.member.contains(WSM)) cG.member.remove(WSM);
            if (cG.member.size() == 0) connectionGroups.remove(cG);
            FastLookTable.remove(conn);

            Log.d(TAG + "::WSonClose", "RestGroup::" + connectionGroups.size());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {

            removeWebSocketFromGroup( conn);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            removeWebSocketFromGroup( conn);
           // conn.close(-1);
            //conn.close(100);
        }

        @Override
        public void onMessage(WebSocket conn, String message) {//@XXXX@data
            connectionGroup cG = FastLookTable.get(conn);
            if(cG==null){

                conn.close(-1);
                return;
            }
            if (message.charAt(0) == '>') {
                if (message.startsWith(">ajax")) {
                    new ThreadAjax(cG, message).start();
                    return;
                } else if (message.startsWith(">tosp:"))//@tosp:XXXX@data
                {
                    int secIdx = message.indexOf('>', 6);
                    if (secIdx != -1) {
                        String toMember = message.substring(6, secIdx);
                        String data = message.substring(secIdx + 1);
                        connectionGroup.WebSocketMember WSM = cG.contains(conn);
                        cG.sendTo(toMember, ">from:" + WSM.name + '>' + data);//@from:YYYY@data


                        return;
                    }
                } else if (message.startsWith(">tosw:"))//@tosp:XXXX@data
                {
                    int secIdx = message.indexOf('>', 6);
                    if (secIdx != -1) {
                        String toMember = message.substring(6, secIdx);
                        String data = message.substring(secIdx + 1);
                        connectionGroup.WebSocketMember WSM = cG.contains(conn);
                        cG.sendToOthersStartWith(conn,toMember, ">from:" + WSM.name + '>' + data);//@from:YYYY@data


                        return;
                    }
                }
            }
            cG.sendToOthers(conn, message);
            return;

        }
    }


    class ThreadAjax extends Thread {
        connectionGroup sG;
        String ajaxData;

        public ThreadAjax(connectionGroup sG, String ajaxData) {
            this.sG = sG;
            this.ajaxData = ajaxData;
        }

        void makeAjax(connectionGroup sG) {


        }

        public void run() {//@ajax>mdmx.me:5213/?a=b&e=d>datatype:text>data:aaaaaaaa

            String[] datas = ajaxData.split(">");

            List<NameValuePair> vars = new ArrayList<NameValuePair>();
            HttpClient client = new DefaultHttpClient();
            try {

                HttpPost post = new HttpPost(datas[1]);
                for (int i = 2; i < datas.length; i++) {
                    int breakIdx = datas[i].indexOf(':');
                    if (breakIdx < 1) continue;
                    String key = datas[i].substring(0, breakIdx);
                    String data = datas[i].substring(breakIdx + 1);
                    Log.d(TAG + "::ddddddddddd", key + "::" + data);
                    if (key.contains("data"))
                        post.setEntity(new ByteArrayEntity(data.getBytes()));
                    else {
                        vars.add(new BasicNameValuePair(key, data));
                    }

                }
                if (vars.size() != 0)
                    post.setEntity(new UrlEncodedFormEntity(vars, HTTP.UTF_8));

                ResponseHandler<String> h = new BasicResponseHandler();
                String response = new String(client.execute(post, h).getBytes(), HTTP.UTF_8);
                sG.sendToAll(response);


            } catch (UnsupportedEncodingException e) {
                sG.sendToAll("@UnsupportedEncodingException" + e.getMessage());
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                sG.sendToAll("@ClientProtocolException::" + e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                sG.sendToAll("@IOException" + e.getMessage());
                e.printStackTrace();
            }
        }


        public void runApache() {//@mdmx.me:5213/?a=b&e=d>datatype:text>data:aaaaaaaa
            String[] datas = ajaxData.split(">");

            List<NameValuePair> vars = new ArrayList<NameValuePair>();
            HttpClient client = new DefaultHttpClient();
            try {
                HttpPost post = new HttpPost(datas[0].substring(1));
                for (int i = 1; i < datas.length; i++) {
                    int breakIdx = datas[i].indexOf(':');
                    if (breakIdx < 1) continue;
                    String key = datas[i].substring(0, breakIdx - 1);
                    String data = datas[i].substring(breakIdx);
                    if (key.equals("data"))
                        post.setEntity(new ByteArrayEntity(data.getBytes("UTF8")));
                    else {
                        vars.add(new BasicNameValuePair(key, data));
                    }

                }
                if (vars.size() != 0)
                    post.setEntity(new UrlEncodedFormEntity(vars, HTTP.UTF_8));

                ResponseHandler<String> h = new BasicResponseHandler();
                String response = new String(client.execute(post, h).getBytes(), HTTP.UTF_8);
                sG.sendToAll(response);

            } catch (UnsupportedEncodingException e) {
                sG.sendToAll("@UnsupportedEncodingException");
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                sG.sendToAll("@ClientProtocolException");
                e.printStackTrace();
            } catch (IOException e) {
                sG.sendToAll("@IOException");
                e.printStackTrace();
            }
        }
    }

    class MyBinder extends Binder {

        public void startServer(int port) {
            startWS(port);
            Log.d(TAG, "onCreate() executed");
            WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
            String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

            Notification notification = new Notification(R.drawable.ic_launcher,
                    "WS service is running", System.currentTimeMillis());
            Intent notificationIntent = new Intent(MainService, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(MainService, 0,
                    notificationIntent, 0);
            notification.setLatestEventInfo(MainService, "WS service is running", ip + "::" + port,
                    pendingIntent);
            startForeground(1, notification);

            connectionGroups = new ArrayList<connectionGroup>();
            FastLookTable = new Hashtable<WebSocket, connectionGroup>();
        }

    }
}
