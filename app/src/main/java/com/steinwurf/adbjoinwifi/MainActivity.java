package com.steinwurf.adbjoinwifi;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements CheckSSIDBroadcastReceiver.SSIDFoundListener
{
    private static final String TAG = "MainActivity";

    private static final String WEP_PASSWORD = "WEP";
    private static final String WPA_PASSWORD = "WPA";

    private static final String SSID = "ssid";
    private static final String PASSWORD_TYPE = "password_type";
    private static final String PASSWORD = "password";

    String mSSID;
    String mPassword;
    String mPasswordType;

    CheckSSIDBroadcastReceiver broadcastReceiver;
    WifiManager mWifiManager;

    Thread mThread;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        // Get Content
        mSSID = getIntent().getStringExtra(SSID);
        mPasswordType = getIntent().getStringExtra(PASSWORD_TYPE);
        mPassword = getIntent().getStringExtra(PASSWORD);

        // Validate

        if ((mSSID == null) || // SSID REQUIRED
            (mPasswordType != null && mPassword == null) || // PASSWORD REQUIRED IF PASSWORD TYPE GIVEN
            (mPassword != null && mPasswordType == null) || // PASSWORD TYPE REQUIRED IF PASSWORD GIVEN
            (mPasswordType != null && !mPasswordType.equals(WPA_PASSWORD) && !mPasswordType.equals(WEP_PASSWORD))) // PASSWORD TYPE MUST BE NULL OR WPA OR WEP
        {
            Log.d(TAG, "No datastring provided. use the following adb command:");
            Log.d(TAG,
                    "adb shell am start " +
                            "-n com.steinwurf.adbjoinwifi/com.steinwurf.adbjoinwifi.MainActivity " +
                            "-e ssid SSID" +
                            "-e password_type [WEP|WPA]" +
                            "-e password PASSWORD");
            Toast.makeText(this, "This application is meant to be used with ADB",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        else
        {
            Log.d(TAG, "Trying to join:");
            Log.d(TAG, "SSID: " + mSSID);
            if(mPasswordType != null && mPassword != null)
            {
                Log.d(TAG, "Password Type: " + mPasswordType);
                Log.d(TAG, "Password: " + mPassword);
            }
        }

        // Setup layout

        LinearLayout layout = new LinearLayout(this);
        setContentView(layout);

        layout.setGravity(Gravity.CENTER);
        layout.setOrientation(LinearLayout.VERTICAL);

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        TextView textview = new TextView(this);
        textview.setText("Trying to connect to:");
        textview.setTextSize(20);
        layout.addView(textview, params);

        TextView SSIDtextview = new TextView(this);
        SSIDtextview.setText(mSSID);
        layout.addView(SSIDtextview, params);

        // Setup broadcast receiver

        broadcastReceiver = new CheckSSIDBroadcastReceiver(mSSID);
        broadcastReceiver.setSSIDFoundListener(this);

        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(broadcastReceiver, f);

        // Check if wifi is enabled, and act accordingly

        mWifiManager = (WifiManager)getSystemService(WIFI_SERVICE);


        if (!mWifiManager.isWifiEnabled())
            mWifiManager.setWifiEnabled(true);
        else
            WifiEnabled();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (broadcastReceiver != null)
        unregisterReceiver(broadcastReceiver);
        broadcastReceiver = null;
    }

    @Override
    public void SSIDFound()
    {
        Log.d(TAG, "Device Connected to " + mSSID);
        if (mThread != null)
        {
            mThread.interrupt();
            try
            {
                mThread.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        finish();
    }

    @Override
    public void WifiEnabled()
    {
        Log.d(TAG, "WifiEnabled");
        if (mThread != null)
            return;

        int networkId = checkIfSSIDExists();
        if (networkId == -1)
        {
            networkId = addWifiConfiguration(networkId);
        }
        if (networkId == -1)
        {
            Log.d(TAG, "Invalid wifi network");
            finish();
            return;
        }
        final int final_networkId = networkId;

        mThread = new Thread() {
            @Override
            public void run() {
                mWifiManager.disconnect();
                try
                {
                    while(!isInterrupted())
                    {
                        Log.d(TAG, "Joining");
                        mWifiManager.enableNetwork(final_networkId, true);
                        mWifiManager.reconnect();
                        // Wait and see if it worked. Otherwise try again.
                        sleep(10000);
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };
        mThread.start();
    }

    private int addWifiConfiguration(int networkId)
    {
        WifiConfiguration wfc = new WifiConfiguration();
        wfc.SSID = "\"".concat(mSSID).concat("\"");
        wfc.status = WifiConfiguration.Status.DISABLED;
        wfc.priority = 100;
        if (mPasswordType == null) // no password
        {
            wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wfc.allowedAuthAlgorithms.clear();
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        }
        else if (mPasswordType.equals(WEP_PASSWORD)) // WEP
        {
            wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            wfc.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

            // if hex string
            // wfc.wepKeys[0] = password;

            wfc.wepKeys[0] = "\"".concat(mPassword).concat("\"");
            wfc.wepTxKeyIndex = 0;
        }
        else if (mPasswordType.equals(WPA_PASSWORD)) // WPA(2)
        {
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            wfc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            wfc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            wfc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wfc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);

            wfc.preSharedKey = "\"".concat(mPassword).concat("\"");
        }
        int result = mWifiManager.addNetwork(wfc);
        if (result  != -1)
        {
            networkId = result;
        }
        return networkId;
    }

    private int checkIfSSIDExists()
    {
        for( WifiConfiguration i : mWifiManager.getConfiguredNetworks())
        {

            if(i.SSID != null && i.SSID.equals("\"".concat(mSSID).concat("\"")))
            {
                Log.d(TAG, "wifi network already exists.");
                return i.networkId;
            }
        }
        return -1;
    }
}
