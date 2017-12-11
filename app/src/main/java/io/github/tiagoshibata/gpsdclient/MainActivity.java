package io.github.tiagoshibata.gpsdclient;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_FINE_LOCATION = 0;
    private Intent gpsdClientServiceIntent;
    private TextView textView;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        private LoggingCallback logger = new LoggingCallback() {
            @Override
            public void log(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        print(message);
                    }
                });
            }
        };

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GpsdClientService.Binder binder = (GpsdClientService.Binder)service;
            binder.setLoggingCallback(logger);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logger.log("GpsdClientService disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);

        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_FINE_LOCATION);
        else
            startGpsdClientService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gpsdClientServiceIntent != null)
            stopService(gpsdClientServiceIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_LOCATION && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startGpsdClientService();
        else
            print("GPS permission denied");
    }

    private void startGpsdClientService() {
        gpsdClientServiceIntent = new Intent(this, GpsdClientService.class);
        gpsdClientServiceIntent
                .putExtra(GpsdClientService.GPSD_SERVER_ADDRESS, "10.0.0.131")
                .putExtra(GpsdClientService.GPSD_SERVER_PORT, 1414);
        if (!bindService(gpsdClientServiceIntent, serviceConnection, BIND_AUTO_CREATE) ||
                startService(gpsdClientServiceIntent) == null) {
            print("Failed to bind to service");
            unbindService(serviceConnection);
            gpsdClientServiceIntent = null;
        }
    }

    private void print(String message) {
        textView.append(message + "\n");
    }
}
