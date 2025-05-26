# NITHphoneWrapper
_An Android application that transforms your smartphone into a head tracking sensor, streaming orientation data and receiving vibration commands_

<p align="center">
  [Image placeholder for the project]
</p>

NITHphoneWrapper is an Android application that uses smartphone rotation and gyroscope sensors to detect the user's head orientation in real-time. It's part of NITH, a software and hardware framework designed for developing accessible applications for people with motor disabilities.

## Main Features

The application extracts the following movement parameters in real-time:
- Head rotation on three axes (yaw, pitch, and roll)
- Rotation acceleration on three axes

The detected data is transmitted via UDP protocol to a user-configured IP address and port, using a standardized communication protocol. This allows other applications to receive and incorporate this data.

Additionally, the application can:
- Receive UDP commands to activate the device's vibration motor
- Display sensor values in real-time
- Show connection status and the last received command

## Communication Protocol

### Outgoing Data
Data is sent as a string with the following structure:
```
$NITHphoneWrapper-v0.1.0|OPR|head_pos_yaw=X&head_pos_pitch=Y&head_pos_roll=Z&head_acc_yaw=A&head_acc_pitch=B&head_acc_roll=C^
```
where X, Y, Z are orientation values in degrees, and A, B, C are acceleration values.

### Incoming Commands
The app receives vibration commands in the format:
```
VIB:duration:amplitude
```
or
```
VIBRATE:duration:amplitude
```
where:
- duration: time in milliseconds
- amplitude: value between 1-255 (optional, default: maximum amplitude)

Examples:
- `VIB:100:255` - vibrate for 100ms at maximum amplitude
- `VIB:50` - vibrate for 50ms with default amplitude

## Installation Guide

### Requirements
- Android device with version 6.0 (API level 24) or higher
- Rotation vector and gyroscope sensors (available in most modern smartphones)
- WiFi or mobile data connection

### Download and Installation
1. Download the latest APK from the [releases page](https://github.com/LIMUNIMI/NITHphoneWrapper/releases)
2. On your Android device, enable installation from "Unknown Sources" in Security settings
3. Open the downloaded APK file and follow the installation instructions

### Building from Source
If you prefer to build the app from source code:

1. Clone the repository:
```bash
git clone https://github.com/LIMUNIMI/NITHphoneWrapper.git
```

2. Open the project in Android Studio

3. Select "Build > Build Bundle(s) / APK(s) > Build APK(s)"

4. Install the generated APK on your device

## Using the Application

1. Launch NITHphoneWrapper on your device
2. The app will display your local IP address
3. Configure the destination IP address and port for sending data
4. Configure the listening port for receiving commands
5. Press the "Start Tracking" button to begin detecting and sending data
6. To test vibration, you can use the "Test Vibration" button or send a UDP command to the configured port

For optimal experience:
- Position the phone in a stable mount on your head (e.g., a headband or VR headset mount)
- Ensure the device is connected to the same WiFi network as the receiving application
- For long-distance communications, consider using a public IP address and proper port forwarding on your router

## Contributions and Support

NITHphoneWrapper is distributed under the GNU GPL-v3 Free Open-Source software license. Feel free to contribute!

You can open an Issue for any requests regarding this code, or contact the developer directly via email at *nicola.davanzo@unimi.it*.

## Acknowledgements

This application is part of NITH, a framework developed at the Laboratory of Music Informatics (LIM) at the University of Milan, Italy.