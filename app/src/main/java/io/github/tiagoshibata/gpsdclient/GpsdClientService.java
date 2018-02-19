package io.github.tiagoshibata.gpsdclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketException;

// TODO NmeaListener is deprecated in API level 24
// Replace with OnNmeaMessageListener when support for old devices is dropped
public class GpsdClientService extends Service implements LocationListener, NmeaListener {
    public static final String GPSD_SERVER_ADDRESS = "io.github.tiagoshibata.GPSD_SERVER_ADDRESS";
    public static final String GPSD_SERVER_PORT = "io.github.tiagoshibata.GPSD_SERVER_PORT";
    private static final String TAG = "GpsdClientService";
    private static final String NOTIFICATION_CHANNEL = "gpsd_streaming";
    private static final int NOTIFICATION_ID = 1;
    private LocationManager locationManager;
    private UdpSensorStream sensorStream;
    private Binder binder = new Binder();
    private LoggingCallback loggingCallback;
    private PowerManager.WakeLock wakeLock;

    class Binder extends android.os.Binder {
        void setLoggingCallback(LoggingCallback callback) {
            loggingCallback = callback;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try {
            locationManager.addNmeaListener(this);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (IllegalArgumentException e) {
                fail("No GPS available");
            }
        } catch (SecurityException e) {
            fail("No permission to access GPS");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Setup notification channel
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW));
        }
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSd Client");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String serverAddress = intent.getStringExtra(GPSD_SERVER_ADDRESS);
        int serverPort = intent.getIntExtra(GPSD_SERVER_PORT, -1);
        if (serverAddress == null || serverPort <= 0)
            throw new RuntimeException(
                    "GpsdClientService requires parameters " + GPSD_SERVER_ADDRESS + " and " + GPSD_SERVER_PORT);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL) :
                new Notification.Builder(getApplicationContext());
        builder
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Streaming GPS")
                .setContentText("Streaming to " + serverAddress + ":" + serverPort)
                .build();
        startForeground(NOTIFICATION_ID, builder.build());
        if (sensorStream != null)
            sensorStream.stop();
        // Note: GPSD_SERVER_ADDRESS must in a resolved form.
        // An exception will be thrown if a hostname is given, since the service's main thread is
        // the UI thread when sharing the process between the activity and the service, and
        // networking on the UI thread is forbidden. See:
        // https://developer.android.com/reference/android/app/Service.html#onStartCommand(android.content.Intent, int, int)
        InetSocketAddress server = new InetSocketAddress(serverAddress, serverPort);
        try {
            sensorStream = new UdpSensorStream(server);
        } catch (SocketException e) {
            fail(e.toString());
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeNmeaListener(this);
        locationManager.removeUpdates(this);
        if (sensorStream != null)
            sensorStream.stop();
        wakeLock.release();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        if (sensorStream != null)
            sensorStream.send(nmea + "\r\n");
    }

    @Override
    public void onLocationChanged(Location location) {
        // Ignored
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // GnssStatus.Callback provides more satellite information if desired, and information when
        // the system enables or disables the hardware
        String message = provider + " status: " + gpsStatusToString(status);
        int satellites = extras.getInt("satellites", -1);
        if (satellites == -1)
            log(message);
        else
            log(message + " with " + Integer.toString(satellites) + " satellites");
    }

    @Override
    public void onProviderEnabled(String provider) {
        log("Location provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        log("Location provider disabled: " + provider);
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (loggingCallback != null)
            loggingCallback.log(message);
    }

    private void fail(String message) {
        log(message);
        stopForeground(true);
        stopSelf();
    }

    private String gpsStatusToString(int status) {
        switch (status) {
            case LocationProvider.OUT_OF_SERVICE:
                return "Out of service";
            case LocationProvider.TEMPORARILY_UNAVAILABLE:
                return "Temporarily unavailable";
            case LocationProvider.AVAILABLE:
                return "Available";
            default:
                return "Unknown";
        }
    }
}
