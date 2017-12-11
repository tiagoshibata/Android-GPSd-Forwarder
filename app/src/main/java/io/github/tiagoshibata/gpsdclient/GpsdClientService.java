package io.github.tiagoshibata.gpsdclient;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;

// TODO NmeaListener is deprecated in API level 24
// Replace with OnNmeaMessageListener when support for old devices is dropped
public class GpsdClientService extends Service implements LocationListener, NmeaListener {
    public static final String GPSD_SERVER_ADDRESS = "io.github.tiagoshibata.GPSD_SERVER_ADDRESS";
    public static final String GPSD_SERVER_PORT = "io.github.tiagoshibata.GPSD_SERVER_PORT";
    private static final String TAG = "GpsdClientService";
    private LocationManager locationManager;
    private UdpSensorStream sensorStream;

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        try {
            locationManager.addNmeaListener(this);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "No GPS available");
                throw new RuntimeException("No GPS available");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to access GPS");
            throw e;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        String serverAddress = intent.getStringExtra(GPSD_SERVER_ADDRESS);
        int serverPort = intent.getIntExtra(GPSD_SERVER_PORT, -1);
        if (serverAddress == null || serverPort <= 0)
            throw new RuntimeException(
                    "GpsdClientService requires parameters " + GPSD_SERVER_ADDRESS + " and " + GPSD_SERVER_PORT);
//        SocketAddress server = 	InetSocketAddress.createUnresolved(serverAddress, serverPort);
        SocketAddress server = 	new InetSocketAddress(serverAddress, serverPort);
        try {
            sensorStream = new UdpSensorStream(server);
        } catch (SocketException e) {
            log(e.toString());
            throw new RuntimeException(e);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO send logs to main activity: https://developer.android.com/reference/android/os/IBinder.html
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        locationManager.removeNmeaListener(this);
        locationManager.removeUpdates(this);
        sensorStream.stop();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        log(nmea);
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
        log(provider + " status: " + gpsStatusToString(status));
        int satellites = extras.getInt("satellites", -1);
        if (satellites != -1)
            log(Integer.toString(satellites) + " satellites");
    }

    @Override
    public void onProviderEnabled(String provider) {
        log( "Location provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        log( "Location provider disabled: " + provider);
    }

    private void log(String msg) {
        Log.i(TAG, msg);
        // TODO IBinder communication
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
