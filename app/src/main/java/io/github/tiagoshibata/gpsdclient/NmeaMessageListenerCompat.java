package io.github.tiagoshibata.gpsdclient;

import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class NmeaMessageListenerCompat implements LocationListener {
    private LocationManager locationManager;
    private LoggingCallback loggingCallback;
    private GpsStatus.NmeaListener nmeaListener;
    private OnNmeaMessageListener onNmeaMessageListener;

    void start(LocationManager locationManager, OnNmeaMessageListenerCompat listener, LoggingCallback loggingCallback) {
        this.locationManager = locationManager;
        this.loggingCallback = loggingCallback;

        try {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("No GPS available");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                onNmeaMessageListener = (message, timestamp) -> listener.onNmeaMessage(message);
                locationManager.addNmeaListener(onNmeaMessageListener);
            } else {
                nmeaListener = (timestamp, message) -> listener.onNmeaMessage(message);
                // Workaround SDK 29 bug: https://issuetracker.google.com/issues/141019880
                try {
                    Method addNmeaListener = LocationManager.class.getMethod("addNmeaListener", GpsStatus.NmeaListener.class);
                    addNmeaListener.invoke(locationManager, nmeaListener);
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException("Failed to call addNmeaListener through reflection: " + e.toString());
                }
            }
        } catch (SecurityException e) {
            throw new RuntimeException("No permission to access GPS");
        }
    }

    void stop() {
        locationManager.removeUpdates(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationManager.removeNmeaListener(onNmeaMessageListener);
        } else {
            // Workaround SDK 29 bug: https://issuetracker.google.com/issues/141019880
            try {
                Method removeNmeaListener = LocationManager.class.getMethod("removeNmeaListener", GpsStatus.NmeaListener.class);
                removeNmeaListener.invoke(locationManager, nmeaListener);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                loggingCallback.log("Failed to call removeNmeaListener through reflection: " + e.toString());
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {}  // Ignored

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Deprecated, the callback is never invoked on API level >= 29.
        // GnssStatus.Callback provides more satellite information if desired, and information when
        // the system enables or disables the hardware
        String message = provider + " status: " + gpsStatusToString(status);
        int satellites = extras.getInt("satellites", -1);
        if (satellites == -1)
            loggingCallback.log(message);
        else
            loggingCallback.log(message + " with " + Integer.toString(satellites) + " satellites");
    }

    @Override
    public void onProviderEnabled(String provider) {
        loggingCallback.log("Location provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        loggingCallback.log("Location provider disabled: " + provider);
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
