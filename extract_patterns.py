"""Extract real ITGmania SSC charts into JSON, with original source charts retained.

python extract_patterns.py --fetch-repo
Uses only the Python standard library. No music or images are copied.
"""
import argparse
from collections import Counter
from fractions import Fraction
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import urllib.parse
import urllib.request

TAG = re.compile(r'#([A-Za-z0-9_]+):(.*?);', re.S)
TIMING = ('BPMS', 'STOPS', 'DELAYS', 'WARPS', 'FAKES', 'TIMESIGNATURES',
          'TICKCOUNTS', 'COMBOS', 'SPEEDS', 'SCROLLS', 'LABELS')
KINDS = {'1': 'tap', '2': 'hold_start', '3': 'hold_end', '4': 'roll_start',
         'M': 'mine', 'L': 'lift', 'F': 'fake', 'K': 'keysound', 'A': 'attack'}
LANES = {'dance-single': ['left', 'down', 'up', 'right'],
         'dance-double': ['p1_left', 'p1_down', 'p1_up', 'p1_right',
                          'p2_left', 'p2_down', 'p2_up', 'p2_right'],
         'dance-couple': ['p1_left', 'p1_down', 'p1_up', 'p1_right',
                          'p2_left', 'p2_down', 'p2_up', 'p2_right'],
         'dance-solo': ['left', 'up_left', 'down', 'up', 'up_right', 'right'],
         'dance-threepanel': ['left', 'up', 'right'],
         'pump-single': ['down_left', 'up_left', 'center', 'up_right', 'down_right'],
         'pump-halfdouble': ['p1_center', 'p1_up_right', 'p1_down_right',
                             'p2_down_left', 'p2_up_left', 'p2_center']}
RELEASE = 'v1.3.0'


def fetch_json(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'dancepad-pattern-extractor'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def repo_sources(out):
    tree = fetch_json(f'https://api.github.com/repos/itgmania/itgmania/git/trees/{RELEASE}?recursive=1')
    paths = [entry['path'] for entry in tree['tree']
             if entry['path'].startswith('Songs/') and entry['path'].endswith('.ssc')]
    for relative in paths:
        url = f'https://raw.githubusercontent.com/itgmania/itgmania/{RELEASE}/' + urllib.parse.quote(relative)
        target = out / 'originals' / 'repository' / Path(relative).relative_to('Songs')
        target.parent.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(url, timeout=30) as response:
            target.write_bytes(response.read())
        yield target, url, target.relative_to(out).as_posix()


def parse_ssc(text):
    # Comments are line based in these bundled SSC files.
    text = re.sub(r'(?<!:)//[^\r\n]*', '', text)
    song, charts, current = {}, [], None
    for key, value in TAG.findall(text):
        key, value = key.upper(), value.strip()
        if key == 'NOTEDATA':
            current = {}
            charts.append(current)
        else:
            (song if current is None else current)[key] = value
    return song, charts


def pairs(raw):
    return sorted((float(parts[0]), float(parts[1])) for entry in raw.split(',')
                  if (parts := entry.strip().split('=')) and len(parts) >= 2 and parts[0])


def timing_for(song, chart):
    # ITGmania initializes split timing with the song offset, but a new set of
    # timing segments. Missing chart stops/warps do not inherit song segments.
    own = any(key in chart for key in TIMING + ('OFFSET',))
    source = chart if own else song
    timing = {key.lower(): source.get(key, '') for key in TIMING}
    timing['offset_seconds'] = float(chart.get('OFFSET', song.get('OFFSET', '0')))
    timing['scope'] = 'chart' if own else 'song'
    return timing


def clock_for(timing):
    bpms, stops, delays = (pairs(timing[key]) for key in ('bpms', 'stops', 'delays'))
    reasons = []
    if timing['warps'].strip():
        reasons.append('Warps retained in timing; absolute milliseconds are not flattened.')
    if not bpms or bpms[0][0] != 0 or any(b < 0 or bpm <= 0 for b, bpm in bpms):
        reasons.append('Nonstandard BPM origin or nonpositive BPM; milliseconds omitted.')
    if any(b < 0 or duration < 0 for b, duration in stops + delays):
        reasons.append('Negative stop/delay timing; milliseconds omitted.')
    if reasons:
        return None, reasons

    def clock(beat):
        seconds = -timing['offset_seconds']
        for i, (start, bpm) in enumerate(bpms):
            end = bpms[i + 1][0] if i + 1 < len(bpms) else beat
            if start >= beat:
                break
            seconds += (min(end, beat) - start) * 60 / bpm
        # A stop is after notes on its beat; a delay is before notes on its beat.
        seconds += sum(duration for b, duration in stops if b < beat)
        seconds += sum(duration for b, duration in delays if b <= beat)
        return round(seconds * 1000, 3)
    return clock, []


def chart_data(song, chart, chart_index, source_path, original, source_url):
    mode = chart.get('STEPSTYPE', '')
    lanes = LANES.get(mode)
    if lanes is None:
        raise ValueError(f'Unsupported step type: {mode}')
    timing = timing_for(song, chart)
    clock, warnings = clock_for(timing)
    events, holds = [], {}
    fake_regions = pairs(timing['fakes'])
    warp_regions = pairs(timing['warps'])
    for measure, block in enumerate(chart.get('NOTES', '').split(',')):
        rows = [line.strip() for line in block.splitlines() if line.strip()]
        for index, row in enumerate(rows):
            if len(row) != len(lanes) or any(c not in '01234MLFKA' for c in row):
                raise ValueError(f'Unsupported note row in measure {measure}: {row!r}')
            beat_fraction = Fraction(measure * 4) + Fraction(index * 4, len(rows))
            beat = float(beat_fraction)
            for lane, symbol in enumerate(row):
                if symbol == '0':
                    continue
                event = {'beat': round(beat, 9), 'beat_fraction': str(beat_fraction),
                         'measure': measure, 'row': index, 'rows_in_measure': len(rows),
                         'lane': lane, 'direction': lanes[lane], 'kind': KINDS[symbol],
                         'symbol': symbol, 'time_ms': clock(beat) if clock else None,
                         'in_fake_region': any(start <= beat < start + length for start, length in fake_regions),
                         'in_warp_region': any(start < beat < start + length for start, length in warp_regions)}
                events.append(event)
                if symbol in ('2', '4'):
                    if lane in holds:
                        warnings.append(f'Overlapping hold heads in lane {lane}, beat {beat}.')
                    holds[lane] = event
                elif symbol == '3':
                    head = holds.pop(lane, None)
                    if head is None:
                        warnings.append(f'Unpaired hold tail in lane {lane}, beat {beat}.')
                    else:
                        head['end_beat'] = round(beat, 9)
                        head['end_time_ms'] = event['time_ms']
    for head in holds.values():
        warnings.append(f'Unpaired hold head at beat {head["beat"]}, lane {head["lane"]}.')
    chart_id = hashlib.sha256((original + ':' + str(chart_index)).encode()).hexdigest()[:12]
    result = {'id': chart_id, 'title': song.get('TITLE', source_path.parent.name),
              'artist': song.get('ARTIST', ''), 'group': source_path.parent.parent.name,
              'step_type': mode, 'difficulty': chart.get('DIFFICULTY', ''),
              'meter': chart.get('METER', ''), 'description': chart.get('DESCRIPTION', ''),
              'credit': chart.get('CREDIT', song.get('CREDIT', '')),
              'lanes': lanes, 'timing': timing, 'milliseconds_available': clock is not None,
              'music_reference_only': song.get('MUSIC', ''),
              'source_original': original, 'source_url': source_url,
              'source_sha256': hashlib.sha256(source_path.read_bytes()).hexdigest(),
              'chart_index': chart_index, 'warnings': sorted(set(warnings)),
              'event_counts': dict(Counter(e['kind'] for e in events)), 'events': events}
    return result


def export(songs, out, fetch_repo):
    out.mkdir(parents=True, exist_ok=True)
    (out / 'charts').mkdir(exist_ok=True)
    sources = []
    if songs.is_dir():
        for path in sorted(songs.rglob('*.ssc')):
            target = out / 'originals' / 'installed' / path.relative_to(songs)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, target)
            sources.append((target, 'https://github.com/itgmania/itgmania/releases/tag/' + RELEASE,
                            target.relative_to(out).as_posix()))
    if fetch_repo:
        sources.extend(repo_sources(out))
    if not sources:
        raise ValueError('No SSC files found; supply --songs or --fetch-repo.')
    charts, errors = [], []
    for path, url, original in sources:
        song, blocks = parse_ssc(path.read_text(encoding='utf-8-sig'))
        for i, chart in enumerate(blocks):
            try:
                result = chart_data(song, chart, i, path, original, url)
                slug = re.sub(r'[^a-z0-9]+', '-', result['title'].lower()).strip('-')
                name = f'charts/{slug}-{result["difficulty"].lower()}-{result["id"]}.json'
                (out / name).write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
                charts.append({key: value for key, value in result.items() if key != 'events'} | {'file': name, 'event_count': len(result['events'])})
            except (ValueError, KeyError) as error:
                errors.append({'source': original, 'chart_index': i, 'error': str(error)})
    summary = {'schema_version': 1, 'source_release': RELEASE, 'songs': len(sources),
               'charts': len(charts), 'by_step_type': dict(Counter(c['step_type'] for c in charts)),
               'by_difficulty': dict(Counter(c['difficulty'] for c in charts)),
               'events': sum(c['event_count'] for c in charts),
               'charts_without_milliseconds': sum(not c['milliseconds_available'] for c in charts),
               'errors': errors}
    (out / 'index.json').write_text(json.dumps(summary | {'entries': charts}, ensure_ascii=False, indent=2), encoding='utf-8')
    # Small, easy four-lane charts are indexed separately for future Android use.
    easy = [c for c in charts if c['step_type'] == 'dance-single' and c['difficulty'] in ('Beginner', 'Easy')]
    (out / 'beginner-and-easy.json').write_text(json.dumps(easy, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--songs', type=Path, default=Path(os.environ['LOCALAPPDATA']) / 'Programs/itgmania/Songs')
    parser.add_argument('--out', type=Path, default=Path(__file__).resolve().parent / 'patterns')
    parser.add_argument('--fetch-repo', action='store_true', help='Also extract the three sample songs in the official v1.3.0 repository')
    options = parser.parse_args()
    result = export(options.songs, options.out, options.fetch_repo)
    raise SystemExit(1 if result['errors'] else 0)
