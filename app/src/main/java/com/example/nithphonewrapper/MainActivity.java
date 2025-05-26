package com.example.nithphonewrapper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "HeadTrackControllerJA";

    // UI Elements
    private TextView tvStatus, tvIpAddress, tvSensorData;
    private TextView tvNetworkStatus, tvLastCommand;
    private EditText etTargetIp, etTargetPort, etListenPort;
    private Button btnStartStop, btnTestVibration;

    // Sensor Variables
    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private Sensor gyroscopeSensor;
    private final float[] rotationVectorReading = new float[5];
    private final float[] orientationAngles = new float[3];
    private final float[] gyroscopeReading = new float[3];

    // Networking Variables
    private int currentTargetPort;
    private int currentListenPort;
    private InetAddress targetInetAddress;
    private DatagramSocket sendSocket;
    private UdpReceiver udpReceiver;
    private volatile boolean isTracking = false;

    // Vibration
    private Vibrator vibrator;
    private final int defaultVibrationAmplitude = VibrationEffect.DEFAULT_AMPLITUDE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI Elements
        tvStatus = findViewById(R.id.tvStatus);
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvSensorData = findViewById(R.id.tvSensorData);
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus);
        tvLastCommand = findViewById(R.id.tvLastCommand);
        etTargetIp = findViewById(R.id.etTargetIp);
        etTargetPort = findViewById(R.id.etTargetPort);
        etListenPort = findViewById(R.id.etListenPort);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnTestVibration = findViewById(R.id.btnTestVibration);

        // Initialize SensorManager and sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (rotationVectorSensor == null) {
            tvStatus.setText("Status: Rotation Vector Sensor NOT AVAILABLE.");
            Log.e(TAG, "Critical: Rotation Vector Sensor is not available.");
            Toast.makeText(this, "Rotation Vector Sensor not found. Head tracking disabled.", Toast.LENGTH_LONG).show();
            btnStartStop.setEnabled(false);
        } else {
            tvStatus.setText("Status: Ready. Rotation sensor found.");
        }

        // Initialize Vibrator service
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        displayIpAddress();

        // Set default port numbers in UI
        etTargetIp.setText("192.168.178.28");
        etTargetPort.setText("20103");
        etListenPort.setText("21103");

        // Setup listeners for buttons
        btnStartStop.setOnClickListener(v -> {
            if (isTracking) {
                stopTracking();
            } else {
                startTracking();
            }
        });

        btnTestVibration.setOnClickListener(v -> testVibration());

        // Configurazione pulsante broadcast
        Button btnTestBroadcast = findViewById(R.id.btnTestBroadcast);
        btnTestBroadcast.setOnClickListener(v -> testBroadcast());
    }

    private void testVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            Toast.makeText(this, "Test vibrazione in corso...", Toast.LENGTH_SHORT).show();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(300);
            }

            tvStatus.setText("Status: Test vibrazione eseguito");
        } else {
            Toast.makeText(this, "Vibrazione non disponibile", Toast.LENGTH_SHORT).show();
        }
    }

    private void testBroadcast() {
        if (!isTracking) {
            Toast.makeText(this, "Prima avvia il tracking", Toast.LENGTH_SHORT).show();
            return;
        }

        tvNetworkStatus.setText("Test broadcast in corso...");

        new Thread(() -> {
            try {
                String testMessage = "VIB:300:255";
                byte[] data = testMessage.getBytes();

                DatagramSocket tempSocket = new DatagramSocket();
                tempSocket.setBroadcast(true);

                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"),
                        currentListenPort);

                tempSocket.send(packet);
                tempSocket.close();

                runOnUiThread(() -> tvNetworkStatus.setText("Broadcast inviato sulla porta " + currentListenPort));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvNetworkStatus.setText("Broadcast fallito: " + e.getMessage());
                });
            }
        }).start();
    }

    private void displayIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            int ipAddressInt = wifiManager.getConnectionInfo().getIpAddress();
            String ipAddressString;
            if (ipAddressInt == 0) {
                ipAddressString = "WiFi Non Connesso";
            } else {
                ipAddressString = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                        (ipAddressInt & 0xff),
                        (ipAddressInt >> 8 & 0xff),
                        (ipAddressInt >> 16 & 0xff),
                        (ipAddressInt >> 24 & 0xff));
            }
            tvIpAddress.setText("My IP: " + ipAddressString);
        } else {
            tvIpAddress.setText("My IP: Non Disponibile");
        }
    }

    private void startTracking() {
        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Impossibile avviare: sensore mancante", Toast.LENGTH_LONG).show();
            return;
        }

        final String targetIpStr = etTargetIp.getText().toString();
        final String targetPortStr = etTargetPort.getText().toString();
        final String listenPortStr = etListenPort.getText().toString();

        if (targetIpStr.isEmpty() || targetPortStr.isEmpty() || listenPortStr.isEmpty()) {
            Toast.makeText(this, "IP e porte non possono essere vuoti", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            currentTargetPort = Integer.parseInt(targetPortStr);
            currentListenPort = Integer.parseInt(listenPortStr);
            targetInetAddress = InetAddress.getByName(targetIpStr);

            // Crea socket per invio
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);

            // Avvia il thread di ricezione
            udpReceiver = new UdpReceiver(currentListenPort);
            udpReceiver.start();

            // Registra i sensori
            registerSensors();

            isTracking = true;
            btnStartStop.setText("Stop Tracking");
            tvStatus.setText("Status: Tracking... Listening on UDP port " + currentListenPort);

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Formato porte non valido", Toast.LENGTH_LONG).show();
        } catch (UnknownHostException e) {
            Toast.makeText(this, "Indirizzo IP non valido", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Errore: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Errore avvio tracking", e);
        }
    }

    private void stopTracking() {
        isTracking = false;
        unregisterSensors();

        if (udpReceiver != null) {
            udpReceiver.stopListening();
            udpReceiver = null;
        }

        if (sendSocket != null && !sendSocket.isClosed()) {
            sendSocket.close();
            sendSocket = null;
        }

        btnStartStop.setText("Start Tracking");
        tvStatus.setText("Status: Idle. Tap Start.");
        tvSensorData.setText("Sensor Data: (stopped)");
    }

    private void registerSensors() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
        if (gyroscopeSensor != null) {
            sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    private void unregisterSensors() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isTracking || event == null) return;

        if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroscopeReading[0] = (float) Math.toDegrees(event.values[0]);
            gyroscopeReading[1] = (float) Math.toDegrees(event.values[1]);
            gyroscopeReading[2] = (float) Math.toDegrees(event.values[2]);
        }
        else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rotationVectorReading, 0, event.values.length);
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorReading);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            final float yaw = (float) Math.toDegrees(orientationAngles[0]);
            final float pitch = (float) Math.toDegrees(orientationAngles[1]);
            final float roll = (float) Math.toDegrees(orientationAngles[2]);

            // Aggiorna UI
            final String sensorDataText = String.format(Locale.US,
                    "Yaw: %.2f (acc: %.2f)\nPitch: %.2f (acc: %.2f)\nRoll: %.2f (acc: %.2f)",
                    yaw, gyroscopeReading[2], pitch, gyroscopeReading[0], roll, gyroscopeReading[1]);
            runOnUiThread(() -> tvSensorData.setText(sensorDataText));

            // Invia dati via UDP
            if (sendSocket != null && targetInetAddress != null && !sendSocket.isClosed()) {
                final String dataToSend = String.format(Locale.US,
                        "$NITHphoneWrapper-v0.1.0|OPR|head_pos_yaw=%.2f&head_pos_pitch=%.2f&head_pos_roll=%.2f&head_acc_yaw=%.2f&head_acc_pitch=%.2f&head_acc_roll=%.2f^",
                        yaw, pitch, roll, gyroscopeReading[2], gyroscopeReading[0], gyroscopeReading[1]);

                new Thread(() -> {
                    try {
                        byte[] buffer = dataToSend.getBytes();
                        DatagramPacket packet = new DatagramPacket(
                                buffer, buffer.length,
                                targetInetAddress, currentTargetPort);
                        sendSocket.send(packet);
                    } catch (IOException e) {
                        Log.e(TAG, "Errore invio UDP", e);
                    }
                }).start();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Accuratezza cambiata per " + sensor.getName() + ": " + accuracy);
    }

    private void processVibrationCommand(final String command) {
        Log.d(TAG, "Comando ricevuto: '" + command + "'");

        runOnUiThread(() -> {
            tvLastCommand.setText("Ultimo comando: " + command);

            try {
                String[] parts = command.split(":");
                if (parts.length > 0 && ("VIB".equalsIgnoreCase(parts[0]) || "VIBRATE".equalsIgnoreCase(parts[0]))) {
                    if (parts.length >= 2) {
                        long duration = Long.parseLong(parts[1]);
                        int amplitude = defaultVibrationAmplitude;

                        if (parts.length >= 3) {
                            amplitude = Integer.parseInt(parts[2]);
                        }

                        Log.d(TAG, "Vibrazione: durata=" + duration + "ms, ampiezza=" + amplitude);

                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
                            } else {
                                vibrator.vibrate(duration);
                            }
                            tvStatus.setText("Status: Vibrazione eseguita");
                        } else {
                            tvStatus.setText("Status: Vibrazione non disponibile");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Errore elaborazione comando: \"" + command + "\"", e);
            }
        });
    }

    // Thread dedicato alla ricezione UDP
    private class UdpReceiver extends Thread {
        private final int port;
        private volatile boolean running = true;
        private DatagramSocket socket;

        public UdpReceiver(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                socket = new DatagramSocket(port);
                Log.d(TAG, "UDP Receiver: avviato sulla porta " + port);

                runOnUiThread(() -> tvNetworkStatus.setText("UDP attivo: in ascolto sulla porta " + port));

                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength());
                        String sender = packet.getAddress().getHostAddress();

                        Log.d(TAG, "UDP Ricevuto da " + sender + ": " + message);
                        processVibrationCommand(message);

                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "Errore ricezione UDP", e);
                            runOnUiThread(() -> tvNetworkStatus.setText("Errore UDP: " + e.getMessage()));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Errore inizializzazione socket", e);
                runOnUiThread(() -> tvNetworkStatus.setText("Errore creazione socket: " + e.getMessage()));
            } finally {
                if (socket != null) {
                    socket.close();
                }
            }
        }

        public void stopListening() {
            running = false;
            if (socket != null) {
                socket.close();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIpAddress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTracking();
    }
}