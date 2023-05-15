package com.example.datarecorderapp;
import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
public class SampleAdminReceiver extends DeviceAdminReceiver {
    void showToast(Context context, CharSequence msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }
    @Override
    public void onEnabled(Context context, Intent intent) {
        showToast(context, "Device admin enabled");
    }
    @Override
    public void onDisabled(Context context, Intent intent) {
        showToast(context, "Device admin disabled");
    }
}