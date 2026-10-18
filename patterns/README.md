# Extracted ITGmania step patterns

## Limitations retained explicitly

- Ten charts contain warps. Their exact beat positions and raw timing segments are preserved, but `time_ms` is null; this extractor does not approximate warp timing.
- One original chart contains unmatched hold tails. Its raw note symbols are preserved and each unmatched tail is listed in that chart's `warnings`.
- These files contain chart data only. Music, artwork, and game code are not bundled here. The Android practice app has not been changed to import these charts.

## Contents

Extracted from ITGmania **v1.3.0**:

- **35 songs**, **251 charts**, **150,488 note events**.
- **175 dance-single** charts, using four arrows: left, down, up, right.
- **65 dance-double** charts.
- 11 additional charts: dance-solo, dance-couple, dance-threepanel, pump-single, and pump-halfdouble.
- **70 beginner/easy four-arrow charts**, indexed separately for a future Android import.
- **241 charts** include calculated milliseconds, including supported BPM changes, stops, delays, and song/chart offsets.

The official repository contains 3 sample songs and 30 charts. The locally installed Windows release adds the 32 Club Fantastic songs and 221 charts. Both sets are included, with distinct source paths.

## Files

- `index.json`: full chart catalog, metadata, warnings, and links to each chart JSON.
- `beginner-and-easy.json`: the 70 beginner/easy dance-single catalog entries.
- `charts/*.json`: one extracted chart per file.
- `originals/repository/`: original SSC files fetched from the pinned GitHub release.
- `originals/installed/`: unchanged SSC files copied from the installed Club Fantastic packs.

Each chart records title, artist, difficulty, meter, author credit, step type, lane order, timing, source URL, original file path, source SHA-256, and individual note events.

## Events and timing

An event's `lane` indexes that chart's `lanes` array. For dance-single:

| Lane | Direction | Your mat signal |
| --- | --- | --- |
| 0 | Left | B1 |
| 1 | Down | B2 |
| 2 | Up | B3 |
| 3 | Right | B4 |

Notes at the same beat on different lanes are simultaneous steps/jumps. Empty rows remain reconstructible from the source measure and row information.

- `beat_fraction` preserves the exact authored beat fraction; `beat` is its numeric representation.
- `time_ms`, when available, is relative to the source song's audio timeline, including its SSC offset. This is chart timing, not a timestamp reported by the physical mat.
- `kind` and `symbol` preserve taps (`1`), hold starts (`2`), hold tails (`3`), roll starts (`4`), mines (`M`), lifts (`L`), fakes (`F`), keysounds (`K`), and attacks (`A`).
- Hold/roll heads include `end_beat` and `end_time_ms` when a matching tail exists.
- `in_fake_region` and `in_warp_region` preserve notes located inside special timing regions. An eventual player must interpret these flags and note kinds before scoring.
- `timing` retains BPMs, stops, delays, warps, fakes, time signatures, tick counts, combos, speed/scroll changes, labels, offset, and the song/chart timing scope. Raw segment strings retain all authored values.
- Stops affect notes after their beat; delays affect notes at and after their beat.
- A chart with split timing starts with the song's offset but uses its own timing segments, matching ITGmania's loader.

## Re-extract and test

From `C:\Users\paul\Projects\dancepad`:

```powershell
python extract_patterns.py --fetch-repo
python -m unittest discover -s tests -v
```

To use another local song folder:

```powershell
python extract_patterns.py --songs 'C:\path\to\Songs' --out '.\another-export'
```

The extractor currently reads SSC files. The sample song with both SM and SSC formats is extracted once using SSC. No external Python packages are required.

## Sources

- [Official ITGmania v1.3.0 release](https://github.com/itgmania/itgmania/releases/tag/v1.3.0)
- [Repository sample songs](https://github.com/itgmania/itgmania/tree/v1.3.0/Songs)
- [SSC loader and split timing behavior](https://github.com/itgmania/itgmania/blob/v1.3.0/src/NotesLoaderSSC.cpp)
- [Lane definitions](https://github.com/itgmania/itgmania/blob/v1.3.0/src/GameManager.cpp)
- [Game timing implementation](https://github.com/itgmania/itgmania/blob/v1.3.0/src/TimingData.cpp)

Original chart and pack credits remain in the SSC files and extracted metadata.
