package com.example.nithphonewrapper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Locale;

public class ButtonActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "ButtonActivity";
    
    // UI Elements
    private Button button1, button2;
    
    // Button States
    private volatile boolean button1Pressed = false;
    private volatile boolean button2Pressed = false;
    
    // Sensor Variables
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor gyroscopeSensor;
    private final float[] rotationVectorReading = new float[5];
    private final float[] orientationAngles = new float[3];
    private final float[] gyroscopeReading = new float[3];
    
    // Orientation data (from rotation vector)
    private float currentPitch = 0f;
    private float currentRoll = 0f;
    
    // Angular velocity (from gyroscope - rad/s)
    private float angularVelYaw = 0f;
    private float angularVelPitch = 0f;
    private float angularVelRoll = 0f;
    
    // Settings from MainActivity
    private boolean invertPitch = false;
    private boolean invertYaw = false;
    private boolean vibrateOnPress = false;
    
    // Networking Variables
    private String targetIp;
    private int targetPort;
    private InetAddress targetInetAddress;
    private DatagramSocket sendSocket;
    private String deviceInfo;
    private String phoneIp;
    
    // Vibration
    private Vibrator vibrator;
    private final int defaultVibrationAmplitude = VibrationEffect.DEFAULT_AMPLITUDE;
    
    // Thread for continuous UDP sending
    private Thread udpSenderThread;
    private volatile boolean isRunning = false;
    
    // Colors
    private static final int COLOR_NORMAL = Color.parseColor("#808080");
    private static final int COLOR_PRESSED = Color.parseColor("#FF0000");
    
    // Continuous vibration state
    private volatile boolean shouldVibrateButton1 = false;
    private volatile boolean shouldVibrateButton2 = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_button);

        // Get extras from intent
        targetIp = getIntent().getStringExtra("TARGET_IP");
        targetPort = getIntent().getIntExtra("TARGET_PORT", 20103);
        invertPitch = getIntent().getBooleanExtra("INVERT_PITCH", false);
        invertYaw = getIntent().getBooleanExtra("INVERT_YAW", false);
        vibrateOnPress = getIntent().getBooleanExtra("VIBRATE_ON_PRESS", false);
        deviceInfo = getIntent().getStringExtra("DEVICE_INFO");
        phoneIp = getIntent().getStringExtra("PHONE_IP");

        if (targetIp == null || targetIp.isEmpty()) {
            Toast.makeText(this, "No target IP set. Cannot send button data.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Initialize UI Elements
        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);

        // Initialize Sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
        }

        // Initialize Vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Setup networking
        try {
            targetInetAddress = InetAddress.getByName(targetIp);
            sendSocket = new DatagramSocket();
            Log.d(TAG, "UDP socket initialized for " + targetIp + ":" + targetPort);
        } catch (UnknownHostException e) {
            Toast.makeText(this, "Invalid target IP", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Invalid target IP", e);
            finish();
            return;
        } catch (SocketException e) {
            Toast.makeText(this, "Socket error", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Socket error", e);
            finish();
            return;
        }

        // Setup button touch listeners
        setupButtonListeners();

        // Start UDP sender thread
        isRunning = true;
        startUdpSender();
    }

    private void setupButtonListeners() {
        button1.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    button1Pressed = true;
                    button1.setBackgroundColor(COLOR_PRESSED);
                    if (vibrateOnPress) {
                        shouldVibrateButton1 = true;
                        startContinuousVibration();
                    }
                    Log.d(TAG, "Button 1 pressed");
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button1Pressed = false;
                    button1.setBackgroundColor(COLOR_NORMAL);
                    shouldVibrateButton1 = false;
                    stopVibrationIfNeeded();
                    Log.d(TAG, "Button 1 released");
                    break;
            }
            return true;
        });

        button2.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    button2Pressed = true;
                    button2.setBackgroundColor(COLOR_PRESSED);
                    if (vibrateOnPress) {
                        shouldVibrateButton2 = true;
                        startContinuousVibration();
                    }
                    Log.d(TAG, "Button 2 pressed");
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    button2Pressed = false;
                    button2.setBackgroundColor(COLOR_NORMAL);
                    shouldVibrateButton2 = false;
                    stopVibrationIfNeeded();
                    Log.d(TAG, "Button 2 released");
                    break;
            }
            return true;
        });
    }

    private void startContinuousVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create a repeating waveform pattern: 100ms on, 0ms off
                long[] timings = {0, 100};
                int[] amplitudes = {0, 255};
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0));
            } else {
                // For older devices, use a repeating pattern
                long[] pattern = {0, 100};
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopVibrationIfNeeded() {
        // Only stop vibration if no buttons are pressed
        if (!shouldVibrateButton1 && !shouldVibrateButton2) {
            if (vibrator != null) {
                vibrator.cancel();
            }
        }
    }

    private void startUdpSender() {
        udpSenderThread = new Thread(() -> {
            while (isRunning) {
                sendUdpPacket();
                try {
                    Thread.sleep(50); // Send at ~20Hz
                } catch (InterruptedException e) {
                    Log.d(TAG, "UDP sender interrupted");
                    break;
                }
            }
        });
        udpSenderThread.start();
    }

    private void sendUdpPacket() {
        if (sendSocket == null || targetInetAddress == null || sendSocket.isClosed()) {
            return;
        }

        // Apply inversion settings
        float outputPitch = invertPitch ? -currentPitch : currentPitch;
        float outputYaw = invertYaw ? -angularVelYaw : angularVelYaw;

        // Build payload with button states in extra field
        String payload = String.format(Locale.US,
                "$NITHphoneWrapper-v0.2.0|OPR|head_pos_pitch=%.2f&head_pos_roll=%.2f&head_vel_yaw=%.4f&head_vel_pitch=%.4f&head_vel_roll=%.4f^dev=%s&phone_ip=%s&button1=%s&button2=%s",
                outputPitch, currentRoll, outputYaw, angularVelPitch, angularVelRoll,
                deviceInfo, phoneIp,
                button1Pressed ? "true" : "false",
                button2Pressed ? "true" : "false");

        try {
            byte[] buffer = payload.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length,
                    targetInetAddress, targetPort);
            sendSocket.send(packet);
        } catch (IOException e) {
            Log.e(TAG, "UDP send error", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) return;

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            // Gyroscope gives angular velocity in rad/s
            angularVelYaw = event.values[2];   // Z axis (yaw rotation rate)
            angularVelPitch = event.values[0]; // X axis (pitch rotation rate)
            angularVelRoll = event.values[1];  // Y axis (roll rotation rate)
            
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rotationVectorReading, 0, event.values.length);

            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorReading);
            
            // Get orientation from rotation vector
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            
            // Extract pitch and roll
            currentPitch = (float) Math.toDegrees(orientationAngles[1]);
            currentRoll = (float) Math.toDegrees(orientationAngles[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanup();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void cleanup() {
        isRunning = false;
        
        if (udpSenderThread != null) {
            udpSenderThread.interrupt();
            udpSenderThread = null;
        }

        // Stop any ongoing vibration
        if (vibrator != null) {
            vibrator.cancel();
        }

        sensorManager.unregisterListener(this);

        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
            sendSocket = null;
        }
    }
}
