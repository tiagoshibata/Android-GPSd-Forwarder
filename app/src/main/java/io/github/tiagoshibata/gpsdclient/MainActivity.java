package io.github.tiagoshibata.gpsdclient;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_FINE_LOCATION = 0;
    private static final String SERVER_ADDRESS = "SERVER_ADDRESS";
    private static final String SERVER_PORT = "SERVER_PORT";
    private Intent gpsdForwarderServiceIntent;
    private SharedPreferences preferences;
    private TextView textView;
    private TextView serverAddressTextView;
    private TextView serverPortTextView;
    private Button startStopButton;
    private boolean connected;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        private LoggingCallback logger = message -> runOnUiThread(() -> print(message));

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            GpsdForwarderService.Binder binder = (GpsdForwarderService.Binder)service;
            binder.setLoggingCallback(logger);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            logger.log("GpsdForwarderService died");
            setServiceConnected(false);
            startStopButton.setEnabled(true);
        }
    };
    private AsyncTask<String, Void, String> gpsdServiceTask;

    private void initializeUi() {
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());
        serverAddressTextView = findViewById(R.id.serverAddress);
        serverPortTextView = findViewById(R.id.serverPort);
        startStopButton = findViewById(R.id.startStopButton);

        serverPortTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0) {
                    String text = editable.toString();
                    int value = Integer.parseInt(text);
                    if (value == 0) {
                        serverPortTextView.setText("");
                        return;
                    }
                    if (value > 65535)
                        serverPortTextView.setText("65535");
                    else if (text.charAt(0) == '0')
                        serverPortTextView.setText(Integer.toString(value));
                    startStopButton.setEnabled(true);
                } else {
                    startStopButton.setEnabled(false);
                }
            }
        });

        preferences = getPreferences(MODE_PRIVATE);
        serverAddressTextView.setText(preferences.getString(SERVER_ADDRESS, ""));
        serverPortTextView.setText(preferences.getString(SERVER_PORT, ""));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeUi();

        LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            print("GPS is not enabled! Go to Settings and enable a location mode with GPS");
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }
        ensureLocationPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopGpsdService();
    }

    private boolean ensureLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            return true;
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_FINE_LOCATION);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_LOCATION && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            print("GPS access allowed");
        else {
            print("GPS permission denied");
        }
    }

    public void startStopButtonOnClick(View view) {
        if (!ensureLocationPermission())
            return;
        if (!connected) {
            String serverAddress = serverAddressTextView.getText().toString();
            String serverPort = serverPortTextView.getText().toString();
            preferences.edit()
                    .putString(SERVER_ADDRESS, serverAddress)
                    .putString(SERVER_PORT, serverPort)
                    .apply();
            gpsdServiceTask = new StartGpsdServiceTask(this);
            gpsdServiceTask.execute(serverAddress, serverPort);
            startStopButton.setEnabled(false);
        } else {
            stopGpsdService();
        }
        setServiceConnected(!connected);
    }

    private static class StartGpsdServiceTask extends AsyncTask<String, Void, String> {
        private WeakReference<MainActivity> activityRef;
        private int port;

        StartGpsdServiceTask(MainActivity activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... host) {
            port = Integer.parseInt(host[1]);
            try {
                return InetAddress.getByName(host[0]).getHostAddress();
            } catch (UnknownHostException e) {
                cancel(false);
                return "Can't resolve " + host[0];
            }
        }

        @Override
        protected void onCancelled(String result) {
            MainActivity activity = activityRef.get();
            if (activity == null)
                return;
            activity.print(result != null ? result : "StartGpsdServiceTask was cancelled");
            activity.setServiceConnected(false);
            activity.startStopButton.setEnabled(true);
        }

        @Override
        protected void onPostExecute(String address) {
            MainActivity activity = activityRef.get();
            if (activity == null)
                return;
            Intent intent = new Intent(activity, GpsdForwarderService.class);
            intent.putExtra(GpsdForwarderService.GPSD_SERVER_ADDRESS, address)
                    .putExtra(GpsdForwarderService.GPSD_SERVER_PORT, port);
            activity.print("Streaming to " + address + ":" + port);
            try {
                if (!activity.bindService(intent, activity.serviceConnection, BIND_ABOVE_CLIENT | BIND_IMPORTANT)) {
                    throw new RuntimeException("Failed to bind to service");
                }
                if (activity.startService(intent) == null) {
                    activity.unbindService(activity.serviceConnection);
                    throw new RuntimeException("Failed to start service");
                }
                activity.gpsdForwarderServiceIntent = intent;
            } catch (RuntimeException e) {
                activity.setServiceConnected(false);
                activity.print(e.getMessage());
            }
            activity.startStopButton.setEnabled(true);
            activity.gpsdServiceTask = null;
        }
    }

    private void stopGpsdService() {
        if (gpsdServiceTask != null) {
            gpsdServiceTask.cancel(true);
            gpsdServiceTask = null;
        }
        if (gpsdForwarderServiceIntent != null) {
            unbindService(serviceConnection);
            stopService(gpsdForwarderServiceIntent);
            gpsdForwarderServiceIntent = null;
        }
    }

    private void setServiceConnected(boolean connected) {
        this.connected = connected;
        startStopButton.setText(connected ? R.string.stop : R.string.start);
        serverAddressTextView.setEnabled(!connected);
        serverPortTextView.setEnabled(!connected);
    }

    private void print(String message) {
        textView.append(message + "\n");
    }
}
