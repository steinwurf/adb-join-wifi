package com.steinwurf.adbjoinwifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;

public class CheckSSIDBroadcastReceiver extends BroadcastReceiver
{
    public interface SSIDFoundListener
    {
        void SSIDFound();
        void WifiEnabled();
    }

    private String mSSID;
    private SSIDFoundListener mSSIDFoundListener;

    CheckSSIDBroadcastReceiver(String SSID)
    {
        mSSID = "\"".concat(SSID).concat("\"");
    }

    void setSSIDFoundListener(SSIDFoundListener listener)
    {
        mSSIDFoundListener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        WifiManager wifiManager = (WifiManager) context.getSystemService(
                AppCompatActivity.WIFI_SERVICE);
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION))
        {
            if (wifiManager.isWifiEnabled())
                mSSIDFoundListener.WifiEnabled();
        }
        else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION))
        {
            ConnectivityManager connectionManager = (ConnectivityManager) context.getSystemService(
                    Context.CONNECTIVITY_SERVICE);

            NetworkInfo wifiNetwork = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                for(Network i : connectionManager.getAllNetworks())
                {
                    NetworkInfo networkInfo = connectionManager.getNetworkInfo(i);
                    if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI)
                    {
                        wifiNetwork = networkInfo;
                        break;
                    }
                }
            }
            else
            {
                wifiNetwork = connectionManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            }


            if (wifiNetwork != null && wifiNetwork.isConnectedOrConnecting())
            {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo.getSSID().equals(mSSID))
                {
                    if (mSSIDFoundListener != null)
                    {
                        mSSIDFoundListener.SSIDFound();
                        mSSIDFoundListener = null;
                    }
                }
            }
        }
    }
}
