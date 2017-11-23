package com.steinwurf.adbjoinwifi;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver
{
    private static final String TAG = "adbjoinwifi";

    @Override
    public void onEnabled(Context context, Intent intent)
    {
        Log.d(TAG, "AdminContextManager onEnabled");
        super.onEnabled(context, intent);
    }

    @Override
    public void onDisabled(Context context, Intent intent)
    {
        Log.d(TAG, "AdminContextManager onDisabled");
        super.onDisabled(context, intent);
    }
}
