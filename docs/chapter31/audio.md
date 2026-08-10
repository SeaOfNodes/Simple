# Optional background audio

`sys.audio` uses [miniaudio](https://github.com/mackron/miniaudio) through an
optional native C backend. Playback runs on its own thread; the game keeps
processing input and drawing. No Python, CMake, codec packages, or sound files
are needed. Default builds/tests never download miniaudio or open a device.

From `chapter31`:

```sh
make libs CTAGS=       # Once while online, or use make init.
make audio CTAGS=
make run-graphical-tictactoe CTAGS=
```

The last command builds both optional backends and runs the game. Accepted
moves get a quiet 70ms sine boop. A player win gets a 480ms rising C-E-G
celebration; a CPU win reverses the notes. Invalid input and occupied squares
are silent. Replay stops any previous cue. Win/loss cues replace the ordinary
move boop; draws use the ordinary boop.

For direct execution in PowerShell, alongside `SIMPLE_MINIFB`:

```powershell
$env:SIMPLE_MINIAUDIO = (Resolve-Path build/release/x86_64_v2_win64/simple_miniaudio.dll).Path
.\build\examples\x86_64_v2_win64\TicTacToeGraphical.exe
```

Linux uses `libsimple_miniaudio.so` in the corresponding release directory.
`SIMPLE_MINIAUDIO` must name the full backend path. Unset it or set it to `off`
for silent play. An unavailable audio device returns `ERROR` and the game
continues. Audio and graphics can be configured independently.

| Operation | Meaning |
| --- | --- |
| `open()` | Open output and generate built-in sounds; `OK` (0) or `ERROR` (-1). Repeated open is safe. |
| `boop()`, `win()`, `lose()` | Play a built-in cue; same as `play(BOOP/WIN/LOSE)`. |
| `load(samples)` | Copy `i16[~]` mono PCM at `RATE` (48000Hz); return handle or `ERROR`. |
| `play(handle)` | Start/restart, replacing the current sound. Return `OK` or `ERROR`. |
| `stop()` | Stop playback; safe before open/after close. |
| `playing()` | Whether samples remain to submit; final device buffering may remain. |
| `close()` | Join the audio thread and free device/buffers; safe to repeat. |

All calls belong on the main/game thread. Load custom sounds at startup:
there are 28 custom slots, each up to ten seconds, retained until close.
Custom handles must be reloaded after close/reopen. Signed samples range
from -32768 to 32767; quiet effects should use much smaller amplitudes.
Native code copies the array and never retains a Simple heap pointer or calls
Simple from the audio thread. Playback itself neither allocates nor waits for
the sound duration. This first interface plays one sound at a time.

`audio.mk` pins miniaudio 0.11.23, cached under `lib/miniaudio-0.11.23/` by
`make libs` (or `init`; `lib` remains a setup alias). Only these explicit setup
targets can download dependencies. All builds, runs, and tests use local
copies, surviving `make clean`; missing cache entries fail promptly with setup
instructions. Windows uses the existing MinGW-w64 compiler;
Linux uses GCC and system pthread/math/dl libraries. No audio development
headers need installing. The upstream header includes its public-domain and
MIT-0 license options, copied beside the backend as `miniaudio-LICENSE`.
`make test-audio CTAGS=` builds the backend checks from the local cache
and runs a real audio thread with miniaudio's silent null
device; normal tests exercise the loader and game without hardware.
