import json
import socket
import threading
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import pc_pad_bridge as relay


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        relay.OPTIONS = SimpleNamespace(demo=False)
        caps = SimpleNamespace(mid=0x79, pid=6, name='Test controller')
        state = SimpleNamespace(buttons=4, x=32511, y=32511, pov=65535)
        self.patches = [patch.object(relay, 'devices', return_value=[(0, caps)]),
                        patch.object(relay, 'read_pad', return_value=state),
                        patch.object(relay, 'signals', return_value={'B3'})]
        for p in self.patches:
            p.start()
        self.server = relay.Bridge(('127.0.0.1', 0), relay.Stream)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={'poll_interval': .01}, daemon=True)
        self.thread.start()
        self.client = socket.create_connection(self.server.server_address, timeout=2)
        self.reader = self.client.makefile('r')

    def tearDown(self):
        self.reader.close()
        self.client.close()
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(1)
        for p in reversed(self.patches):
            p.stop()

    def receive(self, kind):
        for _ in range(20):
            message = json.loads(self.reader.readline())
            if message['type'] == kind:
                return message
        self.fail(f'No {kind} packet received')

    def test_timestamped_state_and_stable_heartbeat(self):
        first = self.receive('state')
        heartbeat = self.receive('state')
        self.assertEqual(first['protocol'], 2)
        self.assertEqual(first['signals'], ['B3'])
        self.assertTrue(first['connected'])
        self.assertGreater(first['pc_detected_ns'], 0)
        self.assertGreaterEqual(first['pc_sent_ns'], first['pc_detected_ns'])
        self.assertEqual(heartbeat['sequence'], first['sequence'])
        self.assertEqual(heartbeat['pc_detected_ns'], first['pc_detected_ns'])
        self.assertGreater(heartbeat['pc_sent_ns'], first['pc_sent_ns'])

    def test_sync_responses_and_fragmented_requests(self):
        self.client.sendall(b'{"type":"sy')
        self.client.sendall(b'nc","id":7}\n')
        response = self.receive('sync')
        self.assertEqual(response['id'], 7)
        self.assertGreater(response['pc_received_ns'], 0)
        self.assertGreaterEqual(response['pc_sent_ns'], response['pc_received_ns'])

    def test_malformed_requests_do_not_break_state_stream(self):
        self.client.sendall(b'not json\n[]\n{"type":"sync","id":"invalid"}\n{"type":"sync","id":9}\n')
        self.assertEqual(self.receive('sync')['id'], 9)
        self.assertEqual(self.receive('state')['signals'], ['B3'])


if __name__ == '__main__':
    unittest.main()
