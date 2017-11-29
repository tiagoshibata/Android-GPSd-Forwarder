package io.github.tiagoshibata.gpsdclient;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.GpsStatus.NmeaListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;


// TODO NmeaListener/GpsStatus.Listener are deprecated in API level 24
// Replace with OnNmeaMessageListener/GnssStatus.Callback when support for old devices is dropped
public class GpsdClientService extends Service implements GpsStatus.Listener, LocationListener, NmeaListener, SensorEventListener {
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private final String TAG = "GpsdClientService";

    @Override
    public void onCreate() {
        locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        try {
            locationManager.addNmeaListener(this);
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "No GPS available");
                throw e;  // FIXME can onCreate throw?
            }
            if (!locationManager.addGpsStatusListener(this))
                Log.w(TAG, "Failed to register for GPS status notifications");
        } catch (SecurityException e) {
            Log.e(TAG, "No permission to access GPS");
            throw e;  // FIXME can onCreate throw?
        }

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSd position updates");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO auto stop after a given number of errors
        // TODO send logs to main activity: https://developer.android.com/reference/android/os/IBinder.html
        return null;
    }

    @Override
    public void onDestroy() {
        ((SensorManager) getSystemService(SENSOR_SERVICE)).unregisterListener(this);
        wakeLock.release();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        log(nmea);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // No sensors registered for onSensorChanged
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        log( "GPS accuracy: " + accuracy);
    }

    @Override
    public void onGpsStatusChanged(int status) {
        log( "GPS status: " + gpsStatusToString(status));
    }

    @Override
    public void onLocationChanged(Location location) {
        // Ignored
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

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
            case GpsStatus.GPS_EVENT_STARTED:
                return "System started";
            case GpsStatus.GPS_EVENT_STOPPED:
                return "System stopped";
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                return "Got first fix";
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                try {
                    return "Satellite count changed: " + locationManager.getGpsStatus(null).getSatellites();
                } catch (SecurityException e) {  // User revoked GPS permission
                    return "Satellite count changed";
                }
        }
        return "";
    }
}
