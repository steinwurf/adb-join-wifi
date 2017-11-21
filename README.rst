=============
adb-join-wifi
=============
A simple app for making it possible to join a certain wifi access point from ADB
without requiring a rooted device. Also supports modifying proxy settings.

.. contents:: Table of Contents:
   :local:

Usage
=====
Build the app, either by importing the project into Android Studio or by using
the following command (make sure you have set the ``ANDROID_HOME`` environment
variable if you choose the latter)::

    ./gradlew assembleDebug

install the app::

   adb install app/build/outputs/apk/app-debug.apk

Use the Activity Manager (``am``) to start the application with the appropriate
parameters:

To join a wifi network with no password::

   adb shell am start -n com.steinwurf.adbjoinwifi/.MainActivity -e ssid SSID

To join a password protected wifi network::

    adb shell am start -n com.steinwurf.adbjoinwifi/.MainActivity \
        -e ssid SSID -e password_type WEP|WPA -e password PASSWORD

To join a wifi network and set a static proxy (with optional bypass list)::
    
    adb shell am start -n com.steinwurf.adbjoinwifi/.MainActivity \
        -e ssid SSID -e password_type WEP|WPA -e password PASSWORD \
        -e proxy_host HOSTNAME -e proxy_port PORT [-e proxy_bypass COMMA,SEPARATED,LIST]

To join a wifi network and set a proxy auto-configuration URL::
    
    adb shell am start -n com.steinwurf.adbjoinwifi/.MainActivity \
        -e ssid SSID -e password_type WEP|WPA -e password PASSWORD \
        -e proxy_pac_uri http://my.pac/url

To clear proxy settings, simply join the same network again and do not pass proxy arguments.

Modifying existing Wifi configurations
=============================
Note that android apps are not allowed to change the wifi configuration if it
was created by another app (for example -- by the user in the Settings app). For
this reason, if you try to use this app to join/modify an existing wifi network,
this app will not modify it and will join it as-configured.

To get around this for testing purposes and modify any wifi configuration, you
can grant this app device owner privileges::

    adb shell dpm set-device-owner "com.steinwurf.adbjoinwifi/.AdminReceiver"

This requires that your device has no provisioned accounts on it.
If you wish to demote this app and remove its device owner privileges, run this::

    adb shell am start -n com.steinwurf.adbjoinwifi/.MainActivity -e clear_device_admin true


License
=======
adb-join-wifi is available under the BSD license.
