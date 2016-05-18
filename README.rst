=============
adb-join-wifi
=============
A simple app for making it possible to join a certain wifi access point from ADB
without requiring a rooted device.

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
parameters::

   adb shell am start -n com.steinwurf.adbjoinwifi/com.steinwurf.adbjoinwifi.MainActivity -e ssid [SSID] -e password_type [PASSWORD_TYPE] -e password [WIFI PASSWORD]

If you want to join an access point without a password you shouldn't specify the
password or password type.
If you need to join an access point with a password you need to specify both.
The password type should be either:

* ``WPA``, or
* ``WEP``

License
=======
adb-join-wifi is available under the BSD license.
