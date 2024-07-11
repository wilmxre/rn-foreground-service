package com.supersami.foregroundservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class RestartReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            Log.d("RestartReceiver", "Restarting app");
            Class<?> mainActivityClass = Class.forName("com.loopeliemptywithkiosk.MainActivity");
            Intent restartIntent = new Intent(context, mainActivityClass);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(restartIntent);
        } catch (ClassNotFoundException e) {
            Log.e("RestartReceiver", "MainActivity class not found", e);
        }
    }
}
