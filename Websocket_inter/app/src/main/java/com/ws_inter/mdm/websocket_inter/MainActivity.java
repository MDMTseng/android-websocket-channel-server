package com.ws_inter.mdm.websocket_inter;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;


public class MainActivity extends ActionBarActivity implements View.OnClickListener {
    int serverPort = 9999;
    ActionBarActivity mainAct = this;
    String serverPortRefKey = "serverPort";
    EditText editT_port = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        Button startService = (Button) findViewById(R.id.btn_wsstart);
        Button stopService = (Button) findViewById(R.id.btn_wsstop);
        startService.setOnClickListener(this);
        stopService.setOnClickListener(this);
        SharedPreferences sharedPref = this.getPreferences(Context.MODE_PRIVATE);
        serverPort = 9999;
        try {
            serverPort = sharedPref.getInt(serverPortRefKey, 9999);
        } catch (Exception e) {
        }
        editT_port = (EditText) findViewById(R.id.editText_port);
        editT_port.setText(serverPort + "");
        startService();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private WebSService.MyBinder myBinder = null;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
            myBinder = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {

            myBinder = (WebSService.MyBinder) service;
            serverPort = Integer.parseInt(editT_port.getText().toString());
            myBinder.startServer(serverPort);

            SharedPreferences sharedPref = mainAct.getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt(serverPortRefKey, serverPort);
            editor.commit();
            Log.v("onServiceConnected", "port::" + serverPort);
        }
    };

    void startService() {
        Intent startIntent = getRunningServiceInternt(WebSService.class);
        if (startIntent == null) {
            startIntent = new Intent(this, WebSService.class);
            startService(startIntent);
        }
        // if(myBinder==null)
        bindService(startIntent, connection, BIND_AUTO_CREATE);
    }

    void stopService() {
        try {
            if (myBinder != null)
                unbindService(connection);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
        Intent stopIntent = getRunningServiceInternt(WebSService.class);
        if (stopIntent != null) {

            stopIntent = new Intent(this, WebSService.class);
            try {
                stopService(stopIntent);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }


    private Intent getRunningServiceInternt(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return new Intent(this, serviceClass);
            }
        }
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_wsstart:
                startService();
                break;
            case R.id.btn_wsstop:
                stopService();
                break;
            default:
                break;
        }
    }


}
