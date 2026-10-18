"""Device check: deliver a PC TEST press 200 ms late, then restore the real bridge.

Requires the updated app installed, adb, and Android CLI on PATH. Temporarily
routes this tablet's bridge to a local test source on port 8766. No physical
button press or modification of the real relay is needed.
"""
import argparse
import json
from pathlib import Path
import re
import select
import socketserver
import subprocess
import tempfile
import threading
import time


class Source(socketserver.BaseRequestHandler):
    def handle(self):
        self.request.settimeout(2)
        pending = b''
        last_send, sequence = 0, 1
        detected_ns = time.perf_counter_ns()
        active = []
        pressed = released = False

        def send(value):
            self.request.sendall((json.dumps(value) + '\n').encode())

        try:
            while True:
                now = time.perf_counter()
                start = getattr(self.server, 'round_start', None)
                changed = False
                if start and not pressed and now >= start + 3.2:
                    active, sequence, pressed, changed = ['B1'], 2, True, True
                    detected_ns = int((start + 3) * 1e9)
                if start and pressed and not released and now >= start + 3.24:
                    active, sequence, released, changed = [], 3, True, True
                    detected_ns = int((start + 3.04) * 1e9)
                if changed or now - last_send >= .25:
                    send({'type': 'state', 'protocol': 2, 'sequence': sequence,
                          'source': 'PC TEST', 'device': 'Timestamp check: 200 ms delayed delivery',
                          'connected': True, 'signals': active,
                          'pc_detected_ns': detected_ns, 'pc_sent_ns': time.perf_counter_ns()})
                    last_send = now
                if select.select([self.request], [], [], .002)[0]:
                    data = self.request.recv(4096)
                    pc_received = time.perf_counter_ns()
                    if not data:
                        return
                    pending += data
                    while b'\n' in pending:
                        line, pending = pending.split(b'\n', 1)
                        packet = json.loads(line)
                        if packet.get('type') == 'sync':
                            send({'type': 'sync', 'id': packet['id'],
                                  'pc_received_ns': pc_received, 'pc_sent_ns': time.perf_counter_ns()})
        except OSError:
            pass


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main(serial):
    def adb(*args):
        return subprocess.check_output(['adb', '-s', serial, *map(str, args)])

    with tempfile.TemporaryDirectory(prefix='dancepad-timestamp-check-') as temp:
        layout = Path(temp) / 'layout.json'

        def nodes():
            subprocess.run(['android', 'layout', '--device=' + serial, '--no-idle', '--output=' + str(layout)],
                           check=True, stdout=subprocess.DEVNULL)
            result = []

            def visit(value):
                if isinstance(value, dict):
                    if 'class' in value:
                        result.append(value)
                    for child in value.values():
                        visit(child)
                elif isinstance(value, list):
                    for child in value:
                        visit(child)
            visit(json.loads(layout.read_text(encoding='utf-8')))
            return result

        def tap(item):
            x, y = re.findall(r'\d+', item['center'])
            adb('shell', 'input', 'tap', x, y)

        with Server(('127.0.0.1', 8766), Source) as server:
            thread = threading.Thread(target=server.serve_forever, kwargs={'poll_interval': .01}, daemon=True)
            thread.start()
            try:
                adb('reverse', 'tcp:8765', 'tcp:8766')
                adb('shell', 'am', 'force-stop', 'dev.paul.dancepad')
                adb('shell', 'am', 'start', '-n', 'dev.paul.dancepad/.PadActivity', '--ez', 'pc_bridge', 'true')
                items = []
                for _ in range(8):
                    items = nodes()
                    if any('PC clock synced' in n.get('text', '') for n in items):
                        break
                    time.sleep(.25)
                assert any('PC clock synced' in n.get('text', '') for n in items), 'Clock sync did not complete'
                tap(next(n for n in items if n.get('text', '').startswith('Play arrows')))
                items = nodes()
                start = next(n for n in items if n.get('text') == 'Start / Restart')
                pause = next(n for n in items if n.get('text') == 'Pause')
                tap(start)
                # adb's tap command waits for dispatch to finish. Anchor after
                # it returns so PC detection is near/slightly after the target.
                server.round_start = time.perf_counter()
                time.sleep(max(0, server.round_start + 3.4 - time.perf_counter()))
                tap(pause)
                description = next(n['content-desc'] for n in nodes()
                                   if n.get('content-desc', '').startswith('Rhythm practice:'))
                score = int(re.search(r'Score (\d+)', description).group(1))
                assert score >= 60 and 'Misses 0.' in description, description
                error = int(re.search(r'Timing error (-?\d+) ms', description).group(1))
                delivery = int(re.search(r'Delivery delay (\d+) ms', description).group(1))
                assert abs(error) <= 150 and delivery >= 180 and error + delivery > 150, description
                print('PASS: a PC timestamped press delivered 200 ms late scored on Android')
                print(description)
            finally:
                adb('reverse', 'tcp:8765', 'tcp:8765')
                adb('shell', 'am', 'force-stop', 'dev.paul.dancepad')
                adb('shell', 'am', 'start', '-n', 'dev.paul.dancepad/.PadActivity', '--ez', 'pc_bridge', 'true')
                server.shutdown()
                thread.join(1)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', default='4c637b9e')
    main(parser.parse_args().serial)
