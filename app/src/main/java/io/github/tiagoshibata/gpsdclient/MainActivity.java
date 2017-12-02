package io.github.tiagoshibata.gpsdclient;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    private final int REQUEST_CODE_FINE_LOCATION = 0;
    private Intent gpsdServiceIntent;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.textView);
        gpsdServiceIntent = new Intent(this, GpsdClientService.class);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_FINE_LOCATION);
        else
            startGpsdService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopService(gpsdServiceIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == REQUEST_CODE_FINE_LOCATION && grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            startGpsdService();
    }

    private void startGpsdService() {
        try {
            startService(gpsdServiceIntent);
        } catch (Exception e) {
            textView.setText(e.toString());
        }
    }
}
