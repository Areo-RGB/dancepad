import unittest
from extract_patterns import clock_for, parse_ssc, timing_for


class TimingTests(unittest.TestCase):
    def test_bpm_changes_and_offset(self):
        timing = timing_for({'BPMS': '0=120,4=60', 'OFFSET': '.1'}, {})
        clock, warnings = clock_for(timing)
        self.assertFalse(warnings)
        self.assertEqual(clock(0), -100)
        self.assertEqual(clock(4), 1900)
        self.assertEqual(clock(5), 2900)

    def test_stop_after_note_delay_before_note(self):
        timing = timing_for({'BPMS': '0=120', 'STOPS': '2=1', 'DELAYS': '2=.5'}, {})
        clock, _ = clock_for(timing)
        self.assertEqual(clock(2), 1500)
        self.assertEqual(clock(3), 3000)

    def test_split_timing_does_not_inherit_song_stops(self):
        timing = timing_for({'BPMS': '0=120', 'STOPS': '2=1', 'OFFSET': '.1'}, {'BPMS': '0=60'})
        self.assertEqual(timing['scope'], 'chart')
        self.assertEqual(timing['stops'], '')
        clock, _ = clock_for(timing)
        self.assertEqual(clock(3), 2900)

    def test_warps_preserved_without_approximate_timestamps(self):
        timing = timing_for({'BPMS': '0=120', 'WARPS': '4=16'}, {})
        clock, warnings = clock_for(timing)
        self.assertIsNone(clock)
        self.assertTrue(warnings)
        self.assertEqual(timing['warps'], '4=16')

    def test_metadata_comments_and_chart_blocks(self):
        song, charts = parse_ssc('''// example
#TITLE:Test;
#ORIGIN:https://example.com/;
#BPMS:0=120;
#NOTEDATA:;
#STEPSTYPE:dance-single;
#NOTES:1000 // left
0100;
#NOTEDATA:;
#STEPSTYPE:dance-double;
#NOTES:10000000;
''')
        self.assertEqual(song['ORIGIN'], 'https://example.com/')
        self.assertEqual(len(charts), 2)
        self.assertIn('0100', charts[0]['NOTES'])
        self.assertEqual(charts[1]['STEPSTYPE'], 'dance-double')


if __name__ == '__main__':
    unittest.main()
