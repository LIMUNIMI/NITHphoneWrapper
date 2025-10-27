#!/usr/bin/env python3
"""
PC discovery responder for NITHphoneWrapper.

Listens for broadcast discovery packets from the phone on UDP port 21104 (default).
When a discovery packet is received, replies directly to the sender with a small
message containing this PC's IP and the target port the PC expects to receive
head-tracking data on.

Usage:
  python tools/pc_discovery_responder.py --port 20103

This script prints logs and runs until interrupted (Ctrl-C).
"""

import socket
import argparse
import sys

DISCOVERY_PORT = 20500
BUFFER_SIZE = 2048


def get_local_ip(remote_addr=('8.8.8.8', 53)):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(remote_addr)
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip


def run_responder(listen_port, discovery_port=DISCOVERY_PORT):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(('0.0.0.0', discovery_port))
    except Exception as e:
        print(f'Failed to bind to {discovery_port}: {e}')
        return
    print(f'Listening for discovery broadcasts on UDP {discovery_port}...')
    local_ip = get_local_ip()
    print(f'Local IP: {local_ip} (will announce this to phones)')
    try:
        while True:
            data, addr = sock.recvfrom(BUFFER_SIZE)
            text = data.decode(errors='ignore')
            print(f'Received from {addr}: {text}')
            # New simple protocol: phone sends
            # "Hey! I'm NITHphoneWrapper. I'm listening on this IP={ip}&port={port}"
            # PC replies:
            # "Hey! I'm the receiver. I have this IP={ip}&port={port}"
            try:
                if 'NITHphoneWrapper' in text:
                    # try to extract key/value pairs (ip and port)
                    kvs = None
                    # find 'IP=' or 'ip='
                    idx = text.find('IP=')
                    if idx == -1:
                        idx = text.find('ip=')
                    if idx != -1:
                        kvs = text[idx:]
                    else:
                        # fallback: find '&' and take substring around it
                        amp = text.find('&')
                        if amp != -1:
                            start = text.rfind(' ', 0, amp)
                            if start == -1:
                                start = 0
                            kvs = text[start:]

                    # prepare reply using local_ip and listen_port
                    payload = f"Hey! I'm the receiver. I have this IP={local_ip}&port={listen_port}"
                    sock.sendto(payload.encode(), addr)
                    print(f'Replied to {addr} with {payload}')
                else:
                    print('Ignoring non-discovery packet')
            except Exception as e:
                print('Error handling packet:', e)
    except KeyboardInterrupt:
        print('\nStopping responder')
    finally:
        sock.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='PC discovery responder for NITHphoneWrapper')
    parser.add_argument('--port', '-p', type=int, required=True, help='Port where PC listens for head-tracking UDP data (phone target port)')
    parser.add_argument('--disc-port', type=int, default=DISCOVERY_PORT, help='Discovery broadcast port (default 21104)')
    args = parser.parse_args()
    run_responder(args.port, args.disc_port)
