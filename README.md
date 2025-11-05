# NITHphoneWrapper - Head Tracking App for Android

**Part of the [NITH Framework](https://neeqstock.notion.site/NITH-framework-1a0de56844cd8099b97df618da497fc1) - A collection of NITHwrappers for multimodal interaction design**

A lightweight Android head-tracking application that sends real-time orientation and angular velocity data over UDP to a receiver application (HeadBower). Designed for hands-free head-controlled interfaces with support for different mounting orientations. Fully compatible with the NITH framework for multimodal sensing and interaction.

![NITHphoneWrapper Interface](screenshots/NITHphoneWrapper.jpg)

## Overview

NITHphoneWrapper turns your Android phone into a head-tracking sensor by utilizing the device's rotation vector and gyroscope sensors.

### What is the NITH Framework?

**NITH** is a framework for designing multimodal interactive systems that integrate heterogeneous sensor streams. NITHphoneWrapper is one of several "NITHwrappers" that provide standardized sensor data transmission over UDP, enabling seamless integration into NITH-based applications.

For more information about the NITH framework and its ecosystem, see:
- 📘 **NITH Framework Documentation**: https://neeqstock.notion.site/NITH-framework-1a0de56844cd8099b97df618da497fc1

### Key Features

- ✅ **NITH Framework Compatible**: Sends standardized sensor data compatible with NITH receivers
- ✅ **Drift-Free Orientation**: Uses TYPE_ROTATION_VECTOR for stable pitch/roll tracking
- ✅ **Angular Velocity Rates**: Sends real-time gyroscope angular velocity (yaw/pitch/roll)
- ✅ **Flexible Mounting**: Independent pitch and yaw inversion switches for any phone orientation
- ✅ **Lightweight Protocol**: Efficient UDP-based communication (v0.2.0)
- ✅ **Network Discovery**: Auto-discovery of receiver on local network
- ✅ **Vibration Feedback**: Test vibration to confirm device communication

## Hardware Requirements

- **Android Device**: API 24+ (Android 7.0 or later)
- **Sensors Required**:
  - TYPE_ROTATION_VECTOR (required for head tracking)
  - TYPE_GYROSCOPE (optional, for angular velocity)
- **Network**: WiFi or Ethernet connection to HeadBower receiver
- **Permissions**: Internet, Network State, WiFi State, Vibrate

## Installation

### Option 1: Build from Source

```bash
# Clone the repository
git clone https://github.com/LIMUNIMI/NITHphoneWrapper.git
cd NITHphoneWrapper

# Build debug APK
./gradlew assembleDebug

# APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

### Option 2: Install Pre-built APK

1. Download the latest APK from releases
2. Transfer to your Android device
3. Enable "Install from Unknown Sources" in Settings > Security
4. Open the APK file and tap Install

## Quick Start Guide

### Step 1: Connect to Network

1. **Open the app** on your Android device
2. **Note your device IP**: Displayed at top as "My IP: X.X.X.X"
3. **Ensure WiFi connection**: Both phone and HeadBower receiver must be on the same network

### Step 2: Configure Receiver Settings

Enter the HeadBower receiver details:

- **Target IP Address**: The IP address of your HeadBower receiver PC
- **Target Port**: The port HeadBower is listening on (default: 20103)
- **Listen Port**: This phone's listening port for vibration feedback (default: 21103)

**Example:**
```
Target IP: 192.168.1.100
Target Port: 20103
Listen Port: 21103
```

### Step 3: Find Receivers (Optional)

1. Tap **"Find Receivers"** to broadcast your IP on the network
2. HeadBower receivers listening on port 21103 will receive:
   - Your phone's IP address
   - Your listening port number

### Step 4: Start Tracking

1. **Calibrate Mounting Orientation**:
   - Move your head to see current pitch/roll values
   - Tap **"Invert Pitch"** switch if pitch is inverted
   - Tap **"Invert Yaw"** switch if yaw rotation is inverted

2. **Start Tracking**: Tap **"Start Tracking"**
   - Screen will lock to portrait orientation
   - Sensor data will begin updating
   - UDP packets will be sent to the receiver

3. **Monitor Data**:
   - **Left Panel**: Current pitch and roll (degrees)
   - **Right Panel**: Angular velocity for yaw, pitch, and roll (rad/s)

## UI Components

### Status Information
- **Status**: Current state (Waiting, Ready, Tracking, Idle)
- **My IP**: Your device's IP address on the network
- **Sensors**: Availability of Rotation Vector (RV) and Gyroscope (Gyro)

### Orientation Display
```
Pitch: 15.2°      ω_y: 0.45 rad/s
Roll: -8.5°       ω_p: 0.12 rad/s
                  ω_r: -0.33 rad/s
```

### Network Configuration
- **Target IP Address**: HeadBower receiver IP
- **Target Port**: Receiver listening port
- **Listen Port**: This phone's vibration feedback port

### Control Buttons
- **Find Receivers**: Broadcast your IP to network
- **Test Vibration**: Check vibration feedback capability
- **Start/Stop Tracking**: Begin or end head tracking session

### Toggle Switches
- **Invert Pitch**: Reverse pitch sign (for upside-down or rotated mounting)
- **Invert Yaw**: Reverse yaw rotation sign (for different handedness/orientation)

### Network Status
- Real-time network connection status
- Last command received from HeadBower

## UDP Protocol (v0.2.0)

### Message Format

```
$NITHphoneWrapper-v0.2.0|OPR|head_pos_pitch=X.XX&head_pos_roll=X.XX&head_vel_yaw=X.XXXX&head_vel_pitch=X.XXXX&head_vel_roll=X.XXXX^dev=DEVICE&phone_ip=X.X.X.X
```

### Field Descriptions

| Field | Type | Unit | Range | Description |
|-------|------|------|-------|-------------|
| `head_pos_pitch` | float | degrees | -90 to +90 | Pitch angle (inverted if switch ON) |
| `head_pos_roll` | float | degrees | -180 to +180 | Roll angle |
| `head_vel_yaw` | float | rad/s | ±6.28 | Yaw angular velocity (inverted if switch ON) |
| `head_vel_pitch` | float | rad/s | ±6.28 | Pitch angular velocity |
| `head_vel_roll` | float | rad/s | ±6.28 | Roll angular velocity |
| `dev` | string | - | - | Device manufacturer and model |
| `phone_ip` | string | - | - | Phone's IP address |

### Update Rate
- **Default**: ~100 Hz (SensorManager.SENSOR_DELAY_GAME)
- **Jitter**: ±5 ms due to Android sensor pipeline

## Technical Details

### NITH Framework Integration

NITHphoneWrapper is designed as a **NITH sensor wrapper** that standardizes Android phone head-tracking data for integration into NITH-based multimodal applications. The UDP protocol follows NITH conventions for:

- **Standardized message format**: Protocol version, device identification, and metadata
- **Modular sensor design**: Easily combined with other NITHwrappers (eye-tracking, hand-tracking, etc.)
- **Zero-configuration networking**: Auto-discovery of NITH receivers on local network
- **Feedback channels**: Vibration feedback for user confirmation and haptic interaction

For details on the NITH framework architecture and how to build multimodal applications, see the [NITH Framework Documentation](https://neeqstock.notion.site/NITH-framework-1a0de56844cd8099b97df618da497fc1).

### Sensor Fusion Strategy

**Problem Solved**: Traditional approaches using magnetometer fusion (TYPE_ROTATION_VECTOR with magnetometer) suffer from yaw drift on devices with broken sensor fusion firmware.

**Solution Implemented**:
1. **Pitch/Roll from TYPE_ROTATION_VECTOR**: Naturally drift-free for tilt angles (accelerometer + gyro fusion, no magnetometer)
2. **Yaw Angular Velocity from TYPE_GYROSCOPE**: Raw rotation rate without integration, no drift accumulation
3. **Inversion Switches**: Allow flexible phone mounting without recalibration

### Removed Components
- ❌ Magnetometer (source of drift and slow response)
- ❌ Accelerometer (redundant with rotation vector)
- ❌ Complex AHRS filter (replaced with native Android sensors)
- ❌ Yaw position tracking (replaced with angular velocity)

### Why No Yaw Position?

Yaw position inherently drifts when integrated from gyroscope data without magnetometer correction. By sending only:
- Stable pitch/roll orientation (from accelerometer fusion)
- Instantaneous yaw angular velocity (no integration)

We eliminate drift while preserving useful head tracking information.

## Troubleshooting

### Device Not Starting Tracking
**Problem**: "Start Tracking" button is disabled or greyed out

**Solutions**:
- ✓ Check if Rotation Vector sensor is available: See "Sensors: RV ✗"
- ✓ Restart the app
- ✓ Check device specifications - older phones may lack TYPE_ROTATION_VECTOR

### No Data Received on Receiver
**Problem**: HeadBower shows no incoming data

**Solutions**:
- ✓ Verify IP address is correct: Check "My IP" on phone
- ✓ Verify target port matches HeadBower listening port
- ✓ Ensure both devices are on same WiFi network
- ✓ Check firewall on receiver PC allows UDP on the port
- ✓ Tap "Test Vibration" to verify network connectivity

### Orientation Appears Inverted
**Problem**: Pitch or yaw is upside down / reversed

**Solutions**:
- ✓ Check if phone is mounted upside down or rotated
- ✓ Tap "Invert Pitch" switch to reverse pitch sign
- ✓ Tap "Invert Yaw" switch to reverse yaw rotation sign
- ✓ You may need to invert one or both depending on mounting

### Data Jitters Excessively
**Problem**: Pitch/Roll values jumping around

**Solutions**:
- ✓ This is normal if phone is moving rapidly
- ✓ Android gyroscope noise is expected without heavy filtering
- ✓ Keep phone stable during tracking
- ✓ Ensure phone doesn't have magnetic interference nearby

### Vibration Not Working
**Problem**: Device doesn't vibrate on test

**Solutions**:
- ✓ Check if device has vibrator: "Test Vibration" should show message
- ✓ Check if vibration is enabled in device settings
- ✓ Verify app has VIBRATE permission: Settings > Apps > NITHphoneWrapper > Permissions

## Performance Characteristics

### Latency
- **Typical**: 50-150 ms end-to-end
- **Network RTT**: 1-5 ms (UDP over WiFi)
- **Sensor sampling**: 10 ms (100 Hz)
- **Processing**: 1-2 ms
- **Network transmission**: 1 ms

### Accuracy
- **Pitch/Roll**: ±2-5° (limited by TYPE_ROTATION_VECTOR accuracy)
- **Angular Velocity**: ±0.05 rad/s (limited by gyroscope noise)
- **No Yaw Drift**: ✓ Angular velocity doesn't accumulate error

### Power Consumption
- **Sensor Processing**: ~5-10 mA
- **WiFi Transmission**: ~20-30 mA
- **Display**: ~50-100 mA (depends on brightness)
- **Total**: ~100-150 mA typical

### Network Bandwidth
- **UDP Packet Size**: ~110-150 bytes
- **Transmission Rate**: 100 Hz
- **Bandwidth Usage**: ~100-150 kbps
- **Very suitable for WiFi networks**

## Advanced Configuration

### Changing Sensor Update Rate

Edit `MainActivity.java`, line with `SENSOR_DELAY_GAME`:

```java
// Options:
// SensorManager.SENSOR_DELAY_FASTEST  // ~2-5 ms (high power)
// SensorManager.SENSOR_DELAY_GAME     // ~10 ms (balanced, default)
// SensorManager.SENSOR_DELAY_UI       // ~67 ms (low power)
// SensorManager.SENSOR_DELAY_NORMAL   // ~200 ms (very low power)

sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
```

### Changing Default Ports

Edit `MainActivity.java`:

```java
private static final int DEFAULT_RECEIVER_PORT = 20103;  // Target port
private static final int VIBRATION_PORT = 21103;         // Listen port
```

### Custom UDP Payload

Edit the UDP payload formatting in `onSensorChanged()` to add/remove fields as needed.

## Development

### Dependencies

- **Android SDK**: API 24+
- **AndroidX**: Core, AppCompat, ConstraintLayout
- **Kotlin Runtime**: Optional (build includes Kotlin support)
- **Gradle**: 8.10.0 or later

### Building from Source

```bash
# Requirements
# - Java 11+ (or Android Studio's bundled JBR)
# - Gradle 8.10.0+

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore)
./gradlew assembleRelease

# Run tests
./gradlew test

# Clean build
./gradlew clean
```

## License

See LICENSE file for details.

## Support

For issues, feature requests, or contributions:
1. Check existing issues on GitHub
2. Provide device model and Android version
3. Include sensor availability information from app status
4. Describe expected vs actual behavior

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Follow existing code style
4. Test on multiple devices if possible
5. Submit a pull request