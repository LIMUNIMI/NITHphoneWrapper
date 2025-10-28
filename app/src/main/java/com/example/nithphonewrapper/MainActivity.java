package com.example.nithphonewrapper;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.DhcpInfo;
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
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "HeadTrackControllerJA";

    // Discovery Ports
    private static final int DISCOVERY_PORT = 20500;
    private static final int VIBRATION_PORT = 21103;
    private static final int DEFAULT_RECEIVER_PORT = 20103;

    // UI Elements
    private TextView tvStatus, tvIpAddress, tvSensorData;
    private TextView tvNetworkStatus, tvLastCommand;
    private EditText etTargetIp, etTargetPort, etListenPort;
    private Button btnStartStop, btnTestVibration, btnDiscoverPc;

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

    // Discovery and Vibration Listeners
    private DiscoveryListener discoveryListener;
    private VibrationCommandListener vibrationListener;
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
        btnDiscoverPc = findViewById(R.id.btnDiscoverPc);

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
        etTargetPort.setText(String.valueOf(DEFAULT_RECEIVER_PORT));
        etListenPort.setText(String.valueOf(VIBRATION_PORT));

        // Setup listeners for buttons
        btnStartStop.setOnClickListener(v -> {
            if (isTracking) stopTracking();
            else startTracking();
        });

        btnTestVibration.setOnClickListener(v -> testVibration());
        btnDiscoverPc.setOnClickListener(v -> sendDiscoveryBroadcast());
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

    /**
     * Sends a discovery broadcast to find HeadBower on the network.
     * Uses subnet-specific broadcast address instead of global broadcast.
     * Format: "NITHphoneWrapper-1.0|device_ip=X.X.X.X&device_port=21103"
     */
    private void sendDiscoveryBroadcast() {
        tvNetworkStatus.setText("Sending discovery broadcast...");
        new Thread(() -> {
            DatagramSocket tempSocket = null;
            try {
                String myIp = getIpAddress();
                String broadcastAddress = getSubnetBroadcastAddress();
                String deviceIdentifier = "NITHphoneWrapper-1.0";

                int listenPort;
                try {
                    listenPort = Integer.parseInt(etListenPort.getText().toString());
                } catch (NumberFormatException e) {
                    listenPort = VIBRATION_PORT;
                    final int finalPort = listenPort;
                    runOnUiThread(() -> etListenPort.setText(String.valueOf(finalPort)));
                }

                // Format: devicename-version|device_ip=ip&device_port=port
                String message = String.format(Locale.US,
                        "%s|device_ip=%s&device_port=%d",
                        deviceIdentifier, myIp, listenPort);

                Log.d(TAG, "Discovery broadcast: " + message + " to " + broadcastAddress + ":" + DISCOVERY_PORT);

                byte[] data = message.getBytes();
                tempSocket = new DatagramSocket();
                tempSocket.setBroadcast(true);

                DatagramPacket packet = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName(broadcastAddress),
                        DISCOVERY_PORT);

                tempSocket.send(packet);
                runOnUiThread(() -> tvNetworkStatus.setText("Discovery sent to " + broadcastAddress + ". Listening for reply..."));

            } catch (Exception e) {
                runOnUiThread(() -> tvNetworkStatus.setText("Discovery broadcast failed: " + e.getMessage()));
                Log.e(TAG, "Discovery broadcast error", e);
            } finally {
                if (tempSocket != null && !tempSocket.isClosed()) {
                    tempSocket.close();
                }
            }
        }).start();
    }

    /**
     * Calculates the subnet broadcast address based on device's IP and subnet mask.
     * For example: 192.168.87.68 with mask 255.255.255.0 -> 192.168.87.255
     */
    private String getSubnetBroadcastAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                DhcpInfo dhcp = wifiManager.getDhcpInfo();
                if (dhcp != null) {
                    int ipAddress = dhcp.ipAddress;
                    int subnetMask = dhcp.netmask;
                    if (subnetMask != 0) {
                        int broadcast = (ipAddress & subnetMask) | (~subnetMask & 0xFFFFFFFF);
                        return String.format(Locale.getDefault(),
                                "%d.%d.%d.%d",
                                (broadcast & 0xff),
                                (broadcast >> 8 & 0xff),
                                (broadcast >> 16 & 0xff),
                                (broadcast >> 24 & 0xff));
                    }
                }
                // fallback: try connectionInfo IP-based calculation if DhcpInfo not available
                int ip = wifiManager.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    // assume /24 fallback
                    int broadcast = (ip & 0x00ffffff) | 0xff000000;
                    return String.format(Locale.getDefault(),
                            "%d.%d.%d.%d",
                            (broadcast & 0xff),
                            (broadcast >> 8 & 0xff),
                            (broadcast >> 16 & 0xff),
                            (broadcast >> 24 & 0xff));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating broadcast address", e);
        }

        // Fallback to global broadcast
        return "255.255.255.255";
    }

    private void displayIpAddress() {
        String ipString = getIpAddress();
        tvIpAddress.setText("My IP: " + ipString);
    }

    private String getIpAddress() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            try {
                Method method = wifiManager.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                boolean isHotspot = (Boolean) method.invoke(wifiManager);
                if (isHotspot) return "192.168.43.1";
            } catch (Exception e) {
                Log.e(TAG, "Error checking hotspot", e);
            }
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

        if (targetIp.isEmpty() || targetPort.isEmpty()) {
            Toast.makeText(this, "Target IP and Port cannot be empty. Use 'Discover PC' first.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            currentTargetPort = Integer.parseInt(targetPort);
            targetInetAddress = InetAddress.getByName(targetIp);
            sendSocket = new DatagramSocket();

            registerSensors();
            isTracking = true;
            btnStartStop.setText("Stop Tracking");
            tvStatus.setText("Status: Tracking... Sending data to " + targetIp);
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
            System.arraycopy(event.values, 0, gyroscopeReading, 0, event.values.length);
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rotationVectorReading, 0, event.values.length);

            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVectorReading);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float yaw = (float) Math.toDegrees(orientationAngles[0]);
            float pitch = (float) Math.toDegrees(orientationAngles[1]);
            float roll = (float) Math.toDegrees(orientationAngles[2]);

            String sensorDataText = String.format(Locale.US,
                    "Yaw: %.2f\nPitch: %.2f\nRoll: %.2f",
                    yaw, pitch, roll);
            runOnUiThread(() -> tvSensorData.setText(sensorDataText));

            if (sendSocket != null && targetInetAddress != null && !sendSocket.isClosed()) {
                String devInfo = Build.MANUFACTURER + "_" + Build.MODEL;
                String myIp = getIpAddress();
                String payload = String.format(Locale.US,
                        "$NITHphoneWrapper-v0.1.0|OPR|head_pos_yaw=%.2f&head_pos_pitch=%.2f&head_pos_roll=%.2f^dev=%s&phone_ip=%s",
                        yaw, pitch, roll, devInfo, myIp);

                new Thread(() -> {
                    try {
                        byte[] buffer = payload.getBytes();
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
        Log.d(TAG, "Received vibration command: '" + command + "'");
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
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing vibration command: \"" + command + "\"", e);
            }
        });
    }

    /**
     * Listener for discovery responses on port 20500.
     * Receives: "NITHreceiver|receiver_ip=X.X.X.X&expected_port=20103"
     */
    private class DiscoveryListener extends Thread {
        private volatile boolean running = true;
        private DatagramSocket socket;

        @Override
        public void run() {
            try {
                socket = new DatagramSocket(DISCOVERY_PORT);
                Log.d(TAG, "Discovery Listener: started on port " + DISCOVERY_PORT);
                runOnUiThread(() -> tvNetworkStatus.setText("Network: Listening for discovery on port " + DISCOVERY_PORT));

                byte[] buffer = new byte[256];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength());
                        String sender = packet.getAddress().getHostAddress();
                        Log.d(TAG, "Discovery: received from " + sender + ": " + message);

                        // Only process discovery responses
                        if (message.startsWith("NITHreceiver|")) {
                            handleDiscoveryResponse(message, sender);
                        }
                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "Discovery receive error", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Discovery socket error", e);
                runOnUiThread(() -> tvNetworkStatus.setText("Discovery socket error: " + e.getMessage()));
            } finally {
                if (socket != null) socket.close();
                Log.d(TAG, "Discovery Listener thread finished.");
            }
        }

        void stopListening() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    /**
     * Listener for vibration commands on port 21103.
     * Receives: "VIB:300:255" etc.
     */
    private class VibrationCommandListener extends Thread {
        private volatile boolean running = true;
        private DatagramSocket socket;
        private final int port;

        VibrationCommandListener(int port) {
            this.port = port;
        }

        @Override
        public void run() {
            try {
                socket = new DatagramSocket(port);
                Log.d(TAG, "Vibration Listener: started on port " + port);
                runOnUiThread(() -> tvNetworkStatus.setText("Network: Listening on port " + port));

                byte[] buffer = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);

                        String message = new String(packet.getData(), 0, packet.getLength());
                        String sender = packet.getAddress().getHostAddress();
                        Log.d(TAG, "Vibration command from " + sender + ": " + message);

                        // Process vibration commands
                        processVibrationCommand(message);
                    } catch (IOException e) {
                        if (running) {
                            Log.e(TAG, "Vibration receive error", e);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Vibration socket error", e);
                runOnUiThread(() -> tvNetworkStatus.setText("Socket error: " + e.getMessage()));
            } finally {
                if (socket != null) socket.close();
                Log.d(TAG, "Vibration Listener thread finished.");
            }
        }

        void stopListening() {
            running = false;
            if (socket != null) socket.close();
        }
    }

    /**
     * Parses discovery response and updates settings.
     * Format: "NITHreceiver|receiver_ip=192.168.1.50&expected_port=20103"
     */
    private void handleDiscoveryResponse(String message, String senderIp) {
        try {
            if (!message.startsWith("NITHreceiver|")) {
                Log.w(TAG, "Ignored discovery response with unexpected prefix");
                return;
            }

            // Extract parameters
            String paramsString = message.substring("NITHreceiver|".length());

            // Parse key=value pairs
            Map<String, String> params = new HashMap<>();
            for (String param : paramsString.split("&")) {
                String[] parts = param.split("=", 2);
                if (parts.length == 2) {
                    params.put(parts[0].trim(), parts[1].trim());
                }
            }

            String receiverIp = params.get("receiver_ip");
            String portStr = params.get("expected_port");

            if (receiverIp == null || portStr == null) {
                Log.w(TAG, "Discovery response missing required fields");
                return;
            }

            final String finalIp = receiverIp;
            final int finalPort = Integer.parseInt(portStr.replaceAll("[^0-9]", ""));

            runOnUiThread(() -> {
                etTargetIp.setText(finalIp);
                etTargetPort.setText(String.valueOf(finalPort));
                tvNetworkStatus.setText("✓ HeadBower found: " + finalIp + ":" + finalPort);
                Toast.makeText(MainActivity.this, "HeadBower discovered!", Toast.LENGTH_SHORT).show();

                try {
                    targetInetAddress = InetAddress.getByName(finalIp);
                    currentTargetPort = finalPort;
                } catch (Exception e) {
                    Log.e(TAG, "Error setting discovered target", e);
                }
            });

            Log.d(TAG, "Discovery successful: " + finalIp + ":" + finalPort);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing discovery response", e);
        }
    }

    // --- Lifecycle Management ---

    private void startDiscoveryListener() {
        if (discoveryListener != null && discoveryListener.isAlive()) {
            Log.d(TAG, "Discovery Listener is already running.");
            return;
        }
        try {
            discoveryListener = new DiscoveryListener();
            discoveryListener.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting discovery listener", e);
            tvNetworkStatus.setText("Error starting discovery listener: " + e.getMessage());
        }
    }

    private void stopDiscoveryListener() {
        if (discoveryListener != null) {
            discoveryListener.stopListening();
            discoveryListener = null;
        }
    }

    private void startVibrationListener() {
        if (vibrationListener != null && vibrationListener.isAlive()) {
            Log.d(TAG, "Vibration Listener is already running.");
            return;
        }
        try {
            currentListenPort = Integer.parseInt(etListenPort.getText().toString());
            vibrationListener = new VibrationCommandListener(currentListenPort);
            vibrationListener.start();
        } catch (NumberFormatException e) {
            tvNetworkStatus.setText("Invalid Listen Port");
            Toast.makeText(this, "Invalid listen port. Cannot start listener.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopVibrationListener() {
        if (vibrationListener != null) {
            vibrationListener.stopListening();
            vibrationListener = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        displayIpAddress();
        startDiscoveryListener();    // Listen for discovery responses on port 20500
        startVibrationListener();    // Listen for vibration commands on port 21103
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isTracking) {
            stopTracking();
        }
        stopDiscoveryListener();
        stopVibrationListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup is handled by onPause
    }
}
