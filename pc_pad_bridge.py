"""Stream Windows dance-pad signals to the Android viewer over ADB reverse.

Run start_pad_bridge.ps1 for the connected Xiaomi tablet, or:
    adb -s SERIAL reverse tcp:8765 tcp:8765
    python pc_pad_bridge.py
Only listens on the PC's loopback interface. No extra Python packages.
"""
import argparse
import json
import logging
import socket
import socketserver
import time

from pad_monitor import devices, read_pad, signals


class Bridge(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


class Stream(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.request.settimeout(3)
        logging.info('Android viewer connected')
        device, caps = None, None
        next_scan = sent = 0
        last = None
        try:
            while True:
                now = time.monotonic()
                packet = {'source': 'PC TEST' if OPTIONS.demo else 'PC USB',
                          'connected': False, 'signals': [], 'device': 'No pad connected'}
                if OPTIONS.demo:
                    steps = [[], ['B3'], [], ['B1', 'B4'], [], ['Y+'], [], ['B7']]
                    packet.update(connected=True, signals=steps[int(now) % len(steps)], device='Synthetic bridge test')
                else:
                    if device is None and now >= next_scan:
                        found = devices()
                        match = next(((d, c) for d, c in found if (c.mid, c.pid) == (0x79, 6)), None)
                        if match:
                            device, caps = match
                        next_scan = now + 1
                    if device is not None:
                        state = read_pad(device)
                        if state is None:
                            device, caps = None, None
                        else:
                            packet.update(connected=True, signals=sorted(signals(state, caps)),
                                          device=f'{caps.name} [{caps.mid:04X}:{caps.pid:04X}]',
                                          buttons=state.buttons, x=state.x, y=state.y, pov=state.pov)
                encoded = json.dumps(packet, separators=(',', ':'))
                if encoded != last or now - sent >= .5:
                    self.request.sendall((encoded + '\n').encode('utf-8'))
                    if encoded != last:
                        logging.info('%s connected=%s active=%s', packet['source'], packet['connected'], packet['signals'])
                    last, sent = encoded, now
                time.sleep(1 / 120)
        except (OSError, ConnectionError):
            logging.info('Android viewer disconnected')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8765)
    parser.add_argument('--demo', action='store_true', help='Send clearly labelled synthetic test signals instead of reading the mat')
    parser.add_argument('--log', help='Optional log file')
    OPTIONS = parser.parse_args()
    logging.basicConfig(filename=OPTIONS.log, level=logging.INFO, format='%(asctime)s %(message)s')
    with Bridge(('127.0.0.1', OPTIONS.port), Stream) as server:
        logging.info('Pad bridge listening on 127.0.0.1:%s, demo=%s', OPTIONS.port, OPTIONS.demo)
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            pass
