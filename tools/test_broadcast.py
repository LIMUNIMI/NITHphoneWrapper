#!/usr/bin/env python3
"""Test script to send a discovery broadcast like the phone would."""

import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)

message = "Hey! I'm NITHphoneWrapper. I'm listening on this IP=192.168.1.100&port=21103"
print(f"Sending broadcast: {message}")

sock.sendto(message.encode(), ('255.255.255.255', 20500))
print("Broadcast sent to 255.255.255.255:20500")

sock.close()
