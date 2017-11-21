package com.steinwurf.adbjoinwifi;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.Class;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.NumberFormatException;
import java.lang.ReflectiveOperationException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements CheckSSIDBroadcastReceiver.SSIDFoundListener
{
    private static final String TAG = "adbjoinwifi";

    private static final String WEP_PASSWORD = "WEP";
    private static final String WPA_PASSWORD = "WPA";

    private static final String SSID = "ssid";
    private static final String PASSWORD_TYPE = "password_type";
    private static final String PASSWORD = "password";

    private static final String PROXY_HOST = "proxy_host";
    private static final String PROXY_PORT = "proxy_port";
    private static final String PROXY_BYPASS = "proxy_bypass";
    private static final String PROXY_PAC_URI = "proxy_pac_uri";

    private static final String CLEAR_DEVICE_ADMIN = "clear_device_admin";

    String mSSID;
    String mPassword;
    String mPasswordType;
    ProxyInfo mProxyInfo;

    CheckSSIDBroadcastReceiver broadcastReceiver;
    WifiManager mWifiManager;

    Thread mThread;

    private void printUsage()
    {
        Log.d(TAG, "No datastring provided. use the following adb command:");
        Log.d(TAG,
                "adb shell am start\n" +
                "    -n com.steinwurf.adbjoinwifi/.MainActivity " +
                "-e ssid SSID " +
                "-e password_type [WEP|WPA] " +
                "-e password PASSWORD " +
                "\nOptional proxy args:\n" +
                "    -e proxy_host HOSTNAME " +
                "-e proxy_port PORT " +
                "[-e proxy_bypass COMMA,SEPARATED,LIST]\n" +
                "    OR\n" +
                "    -e proxy_pac_uri http://my.proxy.config/url\n" +
                "If app was granted device owner using dpm, you can unset it with:\n" +
                "    -e clear_device_admin true");
        Toast.makeText(this, "This application is meant to be used with ADB",
                Toast.LENGTH_SHORT).show();
        finish();
    }

    private ProxyInfo parseProxyInfo(String host, String port, String bypass, String pacUri) throws ParseException
    {
        ProxyInfo proxyInfo = null;

        if (pacUri != null)
        {
            if (!Patterns.WEB_URL.matcher(pacUri).matches()) // PAC URI is invalid
            {
                throw new ParseException("Invalid PAC URL format", 0);
            }
            Log.d(TAG, "Using proxy auto-configuration URL: " + pacUri);
            proxyInfo = ProxyInfo.buildPacProxy(Uri.parse(pacUri));
        }
        else if (host != null && !host.isEmpty() && port != null)
        {
            int parsedPort;

            try
            {
                parsedPort = Integer.parseInt(port);
            }
            catch (NumberFormatException e)
            {
                throw new ParseException("Invalid proxy port", 0);
            }

            if (bypass != null)
            {
                List<String> bypassList = Arrays.asList(bypass.split(","));
                Log.d(TAG, "Using proxy <" + host + ":" + port +">, exclusion list: [" + TextUtils.join(", ", bypassList) + "]");
                proxyInfo = ProxyInfo.buildDirectProxy(host, parsedPort, bypassList);
            }
            else
            {
                Log.d(TAG, "Using proxy <" + host + ":" + port +">");
                proxyInfo = ProxyInfo.buildDirectProxy(host, parsedPort);
            }
        }
        else if (host != null && port == null)
        {
            throw new ParseException("Proxy host specified, but missing port", 0);
        }

        // If all values were null, proxyInfo is null
        return proxyInfo;
    }

    private void setProxy(WifiConfiguration wfc, ProxyInfo proxyInfo) throws IllegalArgumentException, ReflectiveOperationException
    {
        // This method is used since WifiConfiguration.setHttpProxy() isn't supported below sdk v.26

        // Code below adapted from
        //   https://stackoverflow.com/questions/12486441/how-can-i-set-proxysettings-and-proxyproperties-on-android-wi-fi-connection-usin/33949339#33949339
        Class proxySettings = Class.forName("android.net.IpConfiguration$ProxySettings");

        Class[] setProxyParams = new Class[2];
        setProxyParams[0] = proxySettings;
        setProxyParams[1] = ProxyInfo.class;

        Method setProxy = wfc.getClass().getDeclaredMethod("setProxy", setProxyParams);
        setProxy.setAccessible(true);

        Object[] methodParams = new Object[2];

        // Define methodParams[0] (proxy type: NONE, STATIC, or PAC)
        if (proxyInfo == null)
        {
            methodParams[0] = Enum.valueOf(proxySettings, "NONE");
        }
        else {
            // Double check that ProxyInfo is valid
            Method isValid = proxyInfo.getClass().getDeclaredMethod("isValid");
            isValid.setAccessible(true);
            boolean proxyInfoIsValid = (boolean) isValid.invoke(proxyInfo);

            if (!proxyInfoIsValid)
            {
                throw new IllegalArgumentException("Proxy settings are not valid");
            }

            if (!Uri.EMPTY.equals(proxyInfo.getPacFileUrl()))
            {
                methodParams[0] = Enum.valueOf(proxySettings, "PAC");
            }
            else if (proxyInfo.getHost() != null && proxyInfo.getPort() != 0)
            {
                methodParams[0] = Enum.valueOf(proxySettings, "STATIC");
            }
            else
            {
                methodParams[0] = Enum.valueOf(proxySettings, "NONE");
            }
        }

        // Define methodParams[1] (proxy connection info)
        methodParams[1] = proxyInfo;

        setProxy.invoke(wfc, methodParams);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        boolean clearDeviceAdmin = getIntent().getExtras() != null && getIntent().getExtras().containsKey(CLEAR_DEVICE_ADMIN);

        if (clearDeviceAdmin) {
            AdminReceiver.clearDeviceOwner(getApplicationContext());
            finish();
            return;
        }

        // Get Content
        mSSID = getIntent().getStringExtra(SSID);
        mPasswordType = getIntent().getStringExtra(PASSWORD_TYPE);
        mPassword = getIntent().getStringExtra(PASSWORD);

        String proxyHost = getIntent().getStringExtra(PROXY_HOST);
        String proxyPort = getIntent().getStringExtra(PROXY_PORT);
        String proxyBypass = getIntent().getStringExtra(PROXY_BYPASS);
        String proxyPacUri = getIntent().getStringExtra(PROXY_PAC_URI);


        // Validate

        if ((mSSID == null) || // SSID REQUIRED
            (mPasswordType != null && mPassword == null) || // PASSWORD REQUIRED IF PASSWORD TYPE GIVEN
            (mPassword != null && mPasswordType == null) || // PASSWORD TYPE REQUIRED IF PASSWORD GIVEN
            (mPasswordType != null && !mPasswordType.equals(WPA_PASSWORD) && !mPasswordType.equals(WEP_PASSWORD))) // PASSWORD TYPE MUST BE NULL OR WPA OR WEP
        {
            printUsage();
            return;
        }

        try
        {
            mProxyInfo = parseProxyInfo(proxyHost, proxyPort, proxyBypass, proxyPacUri);
        }
        catch (ParseException e) {
            Log.d(TAG, "Error parsing proxy settings");
            printUsage();
            return;
        }

        Log.d(TAG, "Trying to join:");
        Log.d(TAG, "SSID: " + mSSID);
        if(mPasswordType != null && mPassword != null)
        {
            Log.d(TAG, "Password Type: " + mPasswordType);
            Log.d(TAG, "Password: " + mPassword);
        }

        // Setup layout

        LinearLayout layout = new LinearLayout(this);
        setContentView(layout);

        layout.setGravity(Gravity.CENTER);
        layout.setOrientation(LinearLayout.VERTICAL);

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        TextView textview = new TextView(this);
        textview.setText(getString(R.string.trying_to_connect_to));
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
        mWifiManager = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);

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
                Log.e(TAG, "Hit exception", e);
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

        WifiConfiguration wfc = getExistingWifiConfiguration();
        int networkId;

        if (wfc == null)
        {
            // Wifi configuration didn't exist for this SSID, create it.
            wfc = new WifiConfiguration();
            networkId = mWifiManager.addNetwork(wfc);
        }
        else if (permittedToUpdate(wfc))
        {
            // Wifi configuration already exists, update if we can
            updateWifiConfiguration(wfc);
            networkId = mWifiManager.updateNetwork(wfc);
        }
        else {
            // Wifi configuration already exists, we cannot update it so just join it
            networkId = wfc.networkId;
        }

        if (networkId == -1)
        {
            Log.d(TAG, "Invalid wifi network (ensure this SSID exists, auth method and password are correct, etc.)");
            finish();
            return;
        }

        final int finalNetworkId = networkId;

        mThread = new Thread() {
            @Override
            public void run() {
                mWifiManager.disconnect();
                try
                {
                    while(!isInterrupted())
                    {
                        Log.d(TAG, "Joining, network id=" + Integer.toString(finalNetworkId));
                        mWifiManager.enableNetwork(finalNetworkId, true);
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

    private boolean permittedToUpdate(WifiConfiguration wfc)
    {
        Field field;
        int creatorUid;

        try
        {
            field = wfc.getClass().getDeclaredField("creatorUid");
            creatorUid = field.getInt(wfc);
        }
        catch (ReflectiveOperationException e)
        {
            Log.e(TAG, "Hit exception", e);
            return false;
        }

        Context c = getApplicationContext();

        if (creatorUid == c.getApplicationInfo().uid || AdminReceiver.canEditWifi(c))
        {
            Log.d(TAG, "App is permitted to modify this wifi configuration");
            return true;
        }
        else
        {
            // Since app doesn't have proper permissions, we will join the existing Wifi network as configured
            Log.w(TAG, "App does not have admin access, unable to modify a wifi network created by another app");
            return false;
        }
    }

    private void updateWifiConfiguration(WifiConfiguration wfc)
    {
        wfc.SSID = "\"".concat(mSSID).concat("\"");
        wfc.status = WifiConfiguration.Status.ENABLED;
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

        try
        {
            setProxy(wfc, mProxyInfo);
        }
        catch (IllegalArgumentException | ReflectiveOperationException e)
        {
            Log.e(TAG, "Failed to set proxy on wifi configuration", e);
        }
    }

    private WifiConfiguration getExistingWifiConfiguration()
    {
        for( WifiConfiguration i : mWifiManager.getConfiguredNetworks())
        {
            if(i.SSID != null && i.SSID.equals("\"".concat(mSSID).concat("\"")))
            {
                Log.d(TAG, "wifi network already exists.");
                return i;
            }
        }
        return null;
    }
}
