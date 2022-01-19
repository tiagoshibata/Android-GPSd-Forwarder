package io.github.tiagoshibata.gpsdclient;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.OnNmeaMessageListener;

import java.util.Locale;
//##########################################################################
// This Software is copied from https://github.com/iutinvg/compass.git
//##########################################################################
public class Compass implements SensorEventListener {
    private static final String TAG = "Compass";
    private static long prevtime;
    public interface CompassListener {
        void onNewAzimuth(float azimuth);
    }

    private CompassListener listener;

    private final SensorManager sensorManager;
    private final Sensor gsensor;
    private final Sensor msensor;

    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private float[] R = new float[9];
    private float[] I = new float[9];

    public float getAzimuth() {
        return azimuth;
    }

    private float azimuth;
   // private float pitch;
    private float azimuthFix;

    public Compass(Context context) {
        sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        assert sensorManager != null;
        gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void start() {
        sensorManager.registerListener(this, gsensor,
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, msensor,
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    public void setAzimuthFix(float fix) {
        azimuthFix = fix;
    }

    public void resetAzimuthFix() {
        setAzimuthFix(0);
    }

    public void setListener(CompassListener l) {
        listener = l;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final float alpha = 0.99f;

        long actualtime = 0;
        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                mGravity[0] = alpha * mGravity[0] + (1 - alpha)
                        * event.values[1];
                mGravity[1] = alpha * mGravity[1] + (1 - alpha)
                        * event.values[0];
                mGravity[2] = alpha * mGravity[2] + (1 - alpha)
                        * event.values[2];

                // mGravity = event.values;

                // Log.e(TAG, Float.toString(mGravity[0]));
            }

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                // mGeomagnetic = event.values;

                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha)
                        * event.values[1];
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha)
                        * event.values[0];
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha)
                        * event.values[2];
                // Log.e(TAG, Float.toString(event.values[0]));

            }

            boolean success = SensorManager.getRotationMatrix(R, I, mGravity,
                    mGeomagnetic);
            if (success) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);
                // Log.d(TAG, "azimuth (rad): " + azimuth);
                azimuth = (float) Math.toDegrees(orientation[0]); // orientation
                azimuth = (-azimuth + azimuthFix + 360) % 360;
                // Log.d(TAG, "azimuth (deg): " + azimuth);
             //   boolean b = 500 < ((actualtime - prevtime) & 0xEFFFFFFF);
                actualtime = System.currentTimeMillis();
                if ((listener != null ) & (250 < ((actualtime - prevtime) & 0xEFFFFFFF))){
                     listener.onNewAzimuth(azimuth);
                    prevtime =actualtime;
               //     listener.onNewAzimuth(pitch);
                }
            }
       }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }


}

