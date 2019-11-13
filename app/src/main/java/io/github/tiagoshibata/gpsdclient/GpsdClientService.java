package io.github.tiagoshibata.gpsdclient;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketException;

public class GpsdClientService extends Service implements LoggingCallback, OnNmeaMessageListenerCompat {
    public static final String GPSD_SERVER_ADDRESS = "io.github.tiagoshibata.GPSD_SERVER_ADDRESS";
    public static final String GPSD_SERVER_PORT = "io.github.tiagoshibata.GPSD_SERVER_PORT";
    private static final String TAG = "GpsdClientService";
    private static final String NOTIFICATION_CHANNEL = "gpsd_streaming";
    private static final int NOTIFICATION_ID = 1;
    private UdpSensorStream sensorStream;
    private Binder binder = new Binder();
    private LoggingCallback loggingCallback;
    private PowerManager.WakeLock wakeLock;
    private NmeaMessageListenerCompat nmeaMessageListener = new NmeaMessageListenerCompat();

    class Binder extends android.os.Binder {
        void setLoggingCallback(LoggingCallback callback) {
            loggingCallback = callback;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            nmeaMessageListener.start((LocationManager)getSystemService(Context.LOCATION_SERVICE), this, this);
        } catch (RuntimeException e) {
            log(e.getMessage());
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
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSdClient:GPSdStreaming");
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
        Context applicationContext = getApplicationContext();
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                new Notification.Builder(applicationContext, NOTIFICATION_CHANNEL) :
                new Notification.Builder(applicationContext);
        builder
                .setSmallIcon(R.drawable.notification_icon)
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
        nmeaMessageListener.stop();
        if (sensorStream != null)
            sensorStream.stop();
        wakeLock.release();
    }


    @Override
    public void onNmeaMessage(String nmeaMessage) {
        if (sensorStream != null)
            sensorStream.send(nmeaMessage + "\r\n");
    }

    @Override
    public void log(String message) {
        Log.i(TAG, message);
        if (loggingCallback != null)
            loggingCallback.log(message);
    }

    private void fail(String message) {
        log(message);
        stopForeground(true);
        stopSelf();
    }
}
