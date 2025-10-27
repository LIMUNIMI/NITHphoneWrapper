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
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.lang.reflect.Method;

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

    // Discovery sender
    private DiscoverySender discoverySender;
    private final int discoveryPort = 20500; // porta nota per discovery (usare la stessa sul PC)
    private final int discoveryIntervalMs = 2000;

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
        etTargetIp.setText("");
        etTargetPort.setText("20103");
        etListenPort.setText("21103");

        // Setup listeners for buttons
        btnStartStop.setOnClickListener(v -> {
            if (isTracking) stopTracking();
            else startTracking();
        });

        btnTestVibration.setOnClickListener(v -> testVibration());
        findViewById(R.id.btnTestBroadcast).setOnClickListener(v -> testBroadcast());
    }

    private void testVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            Toast.makeText(this, "Vibration test running...", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, defaultVibrationAmplitude));
            } else {
                vibrator.vibrate(300);
            }
            tvStatus.setText("Status: Vibration test executed");
        } else {
            Toast.makeText(this, "Vibration not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void testBroadcast() {
        tvNetworkStatus.setText("Discovery broadcast running...");
        new Thread(() -> {
            DatagramSocket tempSocket = null;
            try {
                String myIp = getIpAddress();
                // Message format requested: "Hey! I'm NITHphoneWrapper. I'm listening on this IP={ip}&port={port}"
                // Use default listen port if tracking not started
                int listenPort = isTracking ? currentListenPort : 21103;
                String message = String.format(Locale.US, "NITHphoneWrapper|ip=%s&port=%d", myIp, listenPort);
                byte[] data = message.getBytes();
                tempSocket = new DatagramSocket();
                tempSocket.setBroadcast(true);
                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"),
                        discoveryPort);
                tempSocket.send(packet);
                runOnUiThread(() -> tvNetworkStatus.setText("Discovery broadcast sent on port " + discoveryPort));
            } catch (Exception e) {
                runOnUiThread(() -> tvNetworkStatus.setText("Discovery broadcast failed: " + e.getMessage()));
            } finally {
                if (tempSocket != null && !tempSocket.isClosed()) tempSocket.close();
            }
        }).start();
    }

    private void displayIpAddress() {
        String ipString = getIpAddress();
        tvIpAddress.setText("My IP: " + ipString);
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            // Check if hotspot is enabled
            try {
                Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                boolean isHotspot = (Boolean) method.invoke(wifiManager);
                if (isHotspot) {
                    // Hotspot IP is typically 192.168.43.1
                    return "192.168.43.1";
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking hotspot", e);
            }
            // Check WiFi IP
            int ip = wifiManager.getConnectionInfo().getIpAddress();
            if (ip != 0) {
                return String.format(Locale.getDefault(),
                        "%d.%d.%d.%d",
                        (ip & 0xff),
                        (ip >> 8 & 0xff),
                        (ip >> 16 & 0xff),
                        (ip >> 24 & 0xff));
            }
        }
        return "Not Available";
    }

    private void startTracking() {
        if (rotationVectorSensor == null) {
            Toast.makeText(this, "Cannot start: missing sensor", Toast.LENGTH_LONG).show();
            return;
        }
        String targetIp = etTargetIp.getText().toString();
        String targetPort = etTargetPort.getText().toString();
        String listenPort = etListenPort.getText().toString();
        if (targetIp.isEmpty() || targetPort.isEmpty() || listenPort.isEmpty()) {
            Toast.makeText(this, "IP and ports cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            currentTargetPort = Integer.parseInt(targetPort);
            currentListenPort = Integer.parseInt(listenPort);
            targetInetAddress = InetAddress.getByName(targetIp);
            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);
            udpReceiver = new UdpReceiver(currentListenPort);
            udpReceiver.start();
            // Start periodic discovery broadcasts so the PC can auto-discover this phone
            startDiscovery();
            registerSensors();
            isTracking = true;
            btnStartStop.setText("Stop Tracking");
            tvStatus.setText("Status: Tracking... Listening on UDP port " + currentListenPort);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port format", Toast.LENGTH_LONG).show();
        } catch (UnknownHostException e) {
            Toast.makeText(this, "Invalid IP address", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error starting tracking", e);
        }
    }

    private void stopTracking() {
        isTracking = false;
        unregisterSensors();
        // stop discovery before closing sockets
        stopDiscovery();
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
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rotationVectorReading, 0, event.values.length);
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorReading);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);
            float yaw = (float) Math.toDegrees(orientationAngles[0]);
            float pitch = (float) Math.toDegrees(orientationAngles[1]);
            float roll = (float) Math.toDegrees(orientationAngles[2]);
            String sensorDataText = String.format(Locale.US,
                    "Yaw: %.2f (acc: %.2f)\nPitch: %.2f (acc: %.2f)\nRoll: %.2f (acc: %.2f)",
                    yaw, gyroscopeReading[2],
                    pitch, gyroscopeReading[0],
                    roll, gyroscopeReading[1]);
            runOnUiThread(() -> tvSensorData.setText(sensorDataText));
            if (sendSocket != null && targetInetAddress != null && !sendSocket.isClosed()) {
                // add device metadata so a discovery/listener on PC can identify the phone
                String devInfo = Build.MANUFACTURER + "_" + Build.MODEL;
                String myIp = getIpAddress();
                // Build payload: main data ends with '^' then append metadata after '^'
                String mainPayload = String.format(Locale.US,
                        "$NITHphoneWrapper-v0.1.0|OPR|head_pos_yaw=%.2f&head_pos_pitch=%.2f&head_pos_roll=%.2f&head_acc_yaw=%.2f&head_acc_pitch=%.2f&head_acc_roll=%.2f^",
                        yaw, pitch, roll,
                        gyroscopeReading[2],
                        gyroscopeReading[0],
                        gyroscopeReading[1]);
                String metadata = String.format("dev=%s&phone_ip=%s", devInfo, myIp);
                String dataToSend = mainPayload + metadata;
                new Thread(() -> {
                    try {
                        byte[] buffer = dataToSend.getBytes();
                        DatagramPacket packet = new DatagramPacket(
                                buffer, buffer.length,
                                targetInetAddress, currentTargetPort);
                        sendSocket.send(packet);
                    } catch (IOException e) {
                        Log.e(TAG, "UDP send error", e);
                    }
                }).start();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Accuracy changed for " + sensor.getName() + ": " + accuracy);
    }

    private void processVibrationCommand(final String command) {
        Log.d(TAG, "Received command: '" + command + "'");
        runOnUiThread(() -> {
            tvLastCommand.setText("Last command: " + command);
            try {
                String[] parts = command.split(":");
                if (parts.length > 0 && ("VIB".equalsIgnoreCase(parts[0]) || "VIBRATE".equalsIgnoreCase(parts[0]))) {
                    if (parts.length >= 2) {
                        long duration = Long.parseLong(parts[1]);
                        int amplitude = defaultVibrationAmplitude;
                        if (parts.length >= 3) {
                            amplitude = Integer.parseInt(parts[2]);
                        }
                        Log.d(TAG, "Vibration: duration=" + duration + "ms, amplitude=" + amplitude);
                        if (vibrator != null && vibrator.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
                            } else {
                                vibrator.vibrate(duration);
                            }
                            tvStatus.setText("Status: Vibration executed");
                        } else {
                            tvStatus.setText("Status: Vibration not available");
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing command: \"" + command + "\"", e);
            }
        });
    }

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
                Log.d(TAG, "UDP Receiver: started on port " + port);
                runOnUiThread(() -> tvNetworkStatus.setText("UDP active: listening on port " + port));
                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        String message = new String(packet.getData(), 0, packet.getLength());
                        String sender = packet.getAddress().getHostAddress();
                        Log.d(TAG, "UDP received from " + sender + ": " + message);
                        // If this is a discovery response from a PC, handle it and auto-configure target IP/port
                        if (message != null && message.startsWith("$NITHpc-disc-resp")) {
                            handleDiscoveryResponse(message, sender);
                        } else {
                            processVibrationCommand(message);
                        }
                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "UDP receive error", e);
                            runOnUiThread(() -> tvNetworkStatus.setText("UDP error: " + e.getMessage()));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Socket initialization error", e);
                runOnUiThread(() -> tvNetworkStatus.setText("Socket creation error: " + e.getMessage()));
            } finally {
                if (socket != null) socket.close();
            }
        }

        public void stopListening() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    // Parse and handle discovery response packets from PC
    private void handleDiscoveryResponse(String message, String senderIp) {
        try {
            // Two accepted response formats:
            // 1) old: $NITHpc-disc-resp|PC|ip=...&port=...^
            // 2) new plain text: "Hey! I'm the receiver. I have this IP={ip}&port={port}"
            String kvs = null;
            if (message.startsWith("$NITHpc-disc-resp")) {
                int pipe = message.indexOf('|');
                int secPipe = message.indexOf('|', pipe + 1);
                int caret = message.indexOf('^');
                if (secPipe != -1 && caret != -1) {
                    kvs = message.substring(secPipe + 1, caret);
                }
            } else if (message.contains("I'm the receiver") || message.contains("receiver")) {
                // try to extract the key=value part after the last space or after ':'
                int idx = message.indexOf("IP=");
                if (idx == -1) idx = message.indexOf("ip=");
                if (idx != -1) {
                    kvs = message.substring(idx);
                } else {
                    // fallback: try to find substring with '&' which contains port
                    int amp = message.indexOf('&');
                    if (amp != -1) {
                        // find start by searching backwards for a space
                        int start = message.lastIndexOf(' ', amp);
                        if (start == -1) start = 0; else start += 1;
                        kvs = message.substring(start);
                    }
                }
            }

            if (kvs != null) {
                Map<String, String> kv = new HashMap<>();
                String[] parts = kvs.split("&");
                for (String p : parts) {
                    String[] kvp = p.split("=", 2);
                    if (kvp.length == 2) {
                        String key = kvp[0].trim();
                        String value = kvp[1].trim();
                        // strip possible trailing chars like ^ or punctuation
                        value = value.replaceAll("[^0-9a-zA-Z\\.\\-:_]$", "");
                        if (value.endsWith("^")) value = value.substring(0, value.length()-1);
                        kv.put(key, value);
                    }
                }
                String ip = kv.getOrDefault("ip", kv.getOrDefault("IP", senderIp));
                String portStr = kv.getOrDefault("port", kv.get("PORT"));
                if (ip != null && portStr != null) {
                    final String finalIp = ip;
                    final int finalPort = Integer.parseInt(portStr.replaceAll("[^0-9]", ""));
                    runOnUiThread(() -> {
                        etTargetIp.setText(finalIp);
                        etTargetPort.setText(String.valueOf(finalPort));
                        tvNetworkStatus.setText("Discovered PC at " + finalIp + ":" + finalPort);
                        try {
                            targetInetAddress = InetAddress.getByName(finalIp);
                            currentTargetPort = finalPort;
                        } catch (Exception e) {
                            Log.e(TAG, "Error setting discovered target", e);
                        }
                    });
                    Log.d(TAG, "Auto-configured target to " + ip + ":" + portStr);
                    return;
                }
            }
            Log.d(TAG, "Discovery response ignored or missing ip/port: " + message);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing discovery response", e);
        }
    }

    // ----- Discovery: periodic broadcast so the computer can auto-detect the phone -----
    private void startDiscovery() {
        stopDiscovery();
        discoverySender = new DiscoverySender(discoveryPort, discoveryIntervalMs, currentListenPort);
        discoverySender.start();
        runOnUiThread(() -> tvNetworkStatus.setText("Discovery: broadcasting on port " + discoveryPort));
    }

    private void stopDiscovery() {
        if (discoverySender != null) {
            discoverySender.shutdown();
            discoverySender = null;
        }
    }

    private class DiscoverySender extends Thread {
        private final int port;
        private final int intervalMs;
        private final int listenPort;
        private volatile boolean running = true;

        DiscoverySender(int port, int intervalMs, int listenPort) {
            this.port = port;
            this.intervalMs = intervalMs;
            this.listenPort = listenPort;
        }

        void shutdown() {
            running = false;
            interrupt();
        }

        @Override
        public void run() {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                socket.setBroadcast(true);
                String devInfo = Build.MANUFACTURER + "_" + Build.MODEL;
                String myIp = getIpAddress();
                while (running) {
                    try {
                    // New discovery format requested: NITHphoneWrapper-version|DIS|device_ip=(ip)&device_port=(port)
                    String payload = String.format(Locale.US,
                        "NITHphoneWrapper-v0.1.0|DIS|device_ip=%s&device_port=%d",
                        myIp, listenPort);
                        byte[] data = payload.getBytes();
                        DatagramPacket packet = new DatagramPacket(
                                data, data.length,
                                InetAddress.getByName("255.255.255.255"), port);
                        socket.send(packet);
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException ie) {
                        break;
                    } catch (IOException ioe) {
                        Log.e(TAG, "Discovery send error", ioe);
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Discovery socket error", e);
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
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
