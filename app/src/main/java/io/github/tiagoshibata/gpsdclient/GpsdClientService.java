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
import android.os.PowerManager;
import android.util.Log;


// TODO NmeaListener are deprecated in API level 24
// Replace with OnNmeaMessageListener when support for old devices is dropped
public class GpsdClientService extends Service implements LocationListener, NmeaListener {
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
        locationManager.removeNmeaListener(this);
        locationManager.removeUpdates(this);
        wakeLock.release();
    }

    @Override
    public void onNmeaReceived(long timestamp, String nmea) {
        log(nmea);
    }

    @Override
    public void onLocationChanged(Location location) {
        // Ignored
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // GnssStatus.Callback provides more satellite information if desired
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
