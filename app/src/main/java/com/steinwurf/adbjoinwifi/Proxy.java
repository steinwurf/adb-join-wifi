package com.steinwurf.adbjoinwifi;

import android.annotation.SuppressLint;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import java.lang.reflect.Method;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

public class Proxy {

    private static final String TAG = "adbjoinwifi";

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static ProxyInfo parseProxyInfo(String host, String port, String bypass, String pacUri) throws ParseException
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

    @SuppressLint("PrivateApi")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    static void setProxy(WifiConfiguration wfc, ProxyInfo proxyInfo) throws IllegalArgumentException, ReflectiveOperationException
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
}
