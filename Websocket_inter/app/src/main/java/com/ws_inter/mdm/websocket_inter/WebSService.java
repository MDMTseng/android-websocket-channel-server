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
import android.widget.Toast;

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
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;

public class WebSService extends Service {
    public static final String TAG = "MyService";
    WebSock websocket = null;

    class connectionGroup {
        String Name = null;
        ArrayList<WebSocket> member = null;

        connectionGroup(String name) {
            Name = name;
            member =new  ArrayList < WebSocket > ();
        }
        public void sendToOthers(WebSocket ex ,String text ) {
            synchronized ( member ) {
                for( WebSocket c : member ) {
                    if(ex!=c)
                        c.send( text );
                }
            }
        }

        public void sendToAll(String text ) {
            synchronized ( member ) {
                for( WebSocket c : member ) {
                        c.send( text );
                }
            }
        }
    }

    ArrayList<connectionGroup> connectionGroups = null;
    Hashtable<WebSocket, connectionGroup> FastLookTable=null;
    public WebSService() {

        Log.d(TAG, "WebSService() executed");

    }


    @Override
    public void onCreate() {
        super.onCreate();
        startWS();
        Log.d(TAG, "onCreate() executed");
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());

        Notification notification = new Notification(R.drawable.ic_launcher,
                "WS service is running", System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);
        notification.setLatestEventInfo(this, "WS service is running", ip,
                pendingIntent);
        startForeground(1, notification);

        connectionGroups = new ArrayList<connectionGroup>();
        FastLookTable = new Hashtable<WebSocket, connectionGroup>();
    }

    connectionGroup findWSGroup(ArrayList<connectionGroup> cGList,String name)
    {
        for(connectionGroup cG:cGList)
        {
            if(cG.Name.equals(name))
            {
                return cG;
            }
        }
        return null;
    }

    boolean startWS() {
        try {
            websocket = new WebSock(9999);
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
        public void onOpen( WebSocket conn, ClientHandshake handshake ) {
            String ResourceStr=handshake.getResourceDescriptor();
            int QM=ResourceStr.lastIndexOf('?');
            if(QM==-1)ResourceStr=ResourceStr.substring(1);
            else
            ResourceStr=ResourceStr.substring(1,QM);
            Log.d(TAG+"::onOpenResourceStr",ResourceStr );

            connectionGroup cG= findWSGroup(connectionGroups, ResourceStr);
            if(cG==null)
            {
                cG=new connectionGroup(ResourceStr);
                connectionGroups.add(cG);
            }
            if(!cG.member.contains(conn))
                cG.member.add(conn);
            if(!FastLookTable.containsKey(conn))
                FastLookTable.put(conn,cG);
            Log.d(TAG+"::WSonOpen", "RestGroup::"+connectionGroups.size());
        }
        @Override
        public void onClose( WebSocket conn, int code, String reason, boolean remote ) {

            connectionGroup cG= FastLookTable.get(conn);
            while(cG.member.contains(conn))cG.member.remove(conn);
            if(cG.member.size()==0)connectionGroups.remove(cG);
            FastLookTable.remove(conn);

            Log.d(TAG+"::WSonClose", "RestGroup::"+connectionGroups.size());
        }
        @Override
        public void onMessage(WebSocket conn, String message) {
            connectionGroup cG= FastLookTable.get(conn);
            if(message.charAt(0)=='@')
                new ThreadAjax(cG,message).start();
            else
                cG.sendToOthers(conn,message);
            return;

        }
    }


    class ThreadAjax extends Thread {
        connectionGroup sG;
        String  ajaxData;
        public ThreadAjax(connectionGroup sG,String ajaxData) {
            this.sG=sG;
            this.ajaxData=ajaxData;
        }

        void makeAjax(connectionGroup sG)
        {



        }
        public void run() {//@ajax>mdmx.me:5213/?a=b&e=d>datatype:text>data:aaaaaaaa

            String[] datas=ajaxData.split(">");

            if(datas[0].contentEquals("@ajax")) {
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
        }



        public void runApache() {//@mdmx.me:5213/?a=b&e=d>datatype:text>data:aaaaaaaa
            String[] datas=ajaxData.split(">");

            List<NameValuePair> vars=new ArrayList< NameValuePair>();
            HttpClient client = new DefaultHttpClient();
            try {
                HttpPost post = new HttpPost(datas[0].substring(1));
                for(int i=1;i<datas.length;i++)
                {
                    int breakIdx=datas[i].indexOf(':');
                    if(breakIdx<1)continue;
                    String key= datas[i].substring(0,breakIdx-1);
                    String data= datas[i].substring(breakIdx);
                    if(key.equals("data"))
                        post.setEntity(new ByteArrayEntity(data.getBytes("UTF8")));
                    else
                    {
                        vars.add(new BasicNameValuePair(key,data));
                    }

                }
                if(vars.size()!=0)
                    post.setEntity(new UrlEncodedFormEntity(vars, HTTP.UTF_8));

                ResponseHandler< String> h=new BasicResponseHandler();
                String response=new String(client.execute(post,h).getBytes(),HTTP.UTF_8);
                sG.sendToAll(response);

            }
            catch(UnsupportedEncodingException e)
            {
                sG.sendToAll("@UnsupportedEncodingException");
                e.printStackTrace();
            }
            catch(ClientProtocolException e)
            {
                sG.sendToAll("@ClientProtocolException");
                e.printStackTrace();
            }
            catch(IOException e)
            {
                sG.sendToAll("@IOException");
                e.printStackTrace();
            }
        }
    }
    class MyBinder extends Binder {

        public void startDownload() {
            Log.d("TAG", "startDownload() executed");
            // 执行具体的下载任务
        }

    }
}
