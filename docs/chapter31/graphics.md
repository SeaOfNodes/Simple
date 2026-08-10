# Optional graphics

Simple owns the pixel array and drawing code. [MiniFB](https://github.com/emoon/minifb)
provides a window, input, and pixel presentation. The first interface is
`sys.gfx`: one fixed-size window, operated on the main thread. The
[Graphics example](examples/Graphics.smp) draws a color gradient in Simple.
The [drawing library and graphical tic-tac-toe](drawing.md) build on this pixel
interface. `make run-graphical-tictactoe CTAGS=` builds the optional backend and
opens the game; `make graphical-tictactoe CTAGS=` only builds its executable.
The game run target also builds optional [background audio](audio.md).

## Default: no graphics dependency

From `chapter31`, the usual `make release`, `make tests`, and `make tictactoe`
need no MiniFB, graphics headers, OpenGL, CMake, or running display. Nothing
downloads dependencies unless you explicitly request `make libs` or `make init`
(`make lib` remains a setup alias). Build and run targets never use the network.

`make tests_nographics CTAGS=` runs the entire ordinary suite with
`SIMPLE_MINIFB=off`. This includes the missing-backend tests, which never open
windows. It excludes the interactive `run-graphics` target.

The runtime contains a small loader using the OS dynamic-library facilities.
It does nothing until `sys.gfx.open`. Without an available backend, `open`
prints a diagnostic to stderr and returns `sys.gfx.ERROR` (-1). Programs can
return a nonzero status or fall back to text; the library does not terminate
the process. Non-graphics programs ignore even a broken graphics configuration.

## Build the optional backend

From `chapter31`:

```sh
make libs CTAGS=       # Once while online; fills the persistent lib/ cache.
make graphics graphics-example CTAGS=
make run-graphics CTAGS=
```

`libs` downloads the revision pinned in [graphics.mk](../graphics.mk) into
ignored `lib/minifb-<revision>.tar.gz` and extracts it under `lib/`.
`graphics` builds only its C framebuffer backend from these local sources. Windows
uses the MinGW-w64 GCC compiler (`x86_64-w64-mingw32-gcc`) and GDI; the rest of
Simple still uses the existing Cygwin GCC. Linux uses GCC and X11 (`libx11-dev`
on Ubuntu), including XWayland on a Wayland desktop. OpenGL, the C++ callback
wrappers, and upstream example programs are omitted. Native Wayland and macOS
builds are not provided here yet.

Outputs:

| Platform | Backend | Example |
| --- | --- | --- |
| Windows | `build/release/x86_64_v2_win64/simple_minifb.dll` | `build/examples/x86_64_v2_win64/Graphics.exe` |
| Linux | `build/release/x86_64_v2_SystemV/libsimple_minifb.so` | `build/examples/x86_64_v2_SystemV/Graphics` |

`MiniFB-LICENSE` is copied alongside the backend; include it when distributing
the library. MiniFB sources remain outside the tracked Simple sources.
The cache survives `make clean`. Editing a makefile or rebuilding a backend
does not fetch or check GitHub. Missing dependencies fail promptly with
instructions to run `make libs` while online.

`graphics-example` alone builds the executable without requiring MiniFB. It
also demonstrates the diagnostic and exit status 1 when graphics is absent.
`run-graphics` builds both, sets the backend path, and opens a window. Close it
or release Escape to exit. Passing any argument to the executable makes it
close after five polling iterations, for a quick smoke test.

To run directly from PowerShell:

```powershell
$env:SIMPLE_MINIFB = (Resolve-Path build/release/x86_64_v2_win64/simple_minifb.dll).Path
.\build\examples\x86_64_v2_win64\Graphics.exe
```

From Cygwin bash:

```sh
export SIMPLE_MINIFB="$PWD/build/release/x86_64_v2_win64/simple_minifb.dll"
./build/examples/x86_64_v2_win64/Graphics.exe
```

For WSL, from the repository root:

```sh
wsl -d Ubuntu-24.04 -- bash -lc 'cd chapter31 && make graphics graphics-example CLZDIR=build/classes-linux CTAGS='
```

Inside WSL, set `SIMPLE_MINIFB` to the full path of the Linux `.so`, then run
the Linux example with a working X11 display. For an automated virtual display:

```sh
SIMPLE_MINIFB="$PWD/build/release/x86_64_v2_SystemV/libsimple_minifb.so" \
    xvfb-run -a build/examples/x86_64_v2_SystemV/Graphics smoke
```

With no `SIMPLE_MINIFB` setting, the runtime searches beside the executable,
then its `../../release/<ABI>` and `../release/<ABI>` directories. These cover
the examples and test build layouts regardless of the current directory.
It also checks the current directory, `build/release/<ABI>`, and
`chapter31/build/release/<ABI>` relative to the current directory. Thus built
examples such as `Clock.exe` can be launched directly without configuration.

`SIMPLE_MINIFB` overrides discovery; an invalid explicit path reports an error
without falling back. `SIMPLE_MINIFB=off` disables graphics for headless tests.
An absent, unloadable, or incompatible backend produces a diagnostic.
A backend that loads but cannot create a window also reports an error cleanly.

## Initial interface

| Operation | Meaning |
| --- | --- |
| `open(title,width,height)` | Open one window; returns `OK` or `ERROR`. |
| `present(pixels)` | Present a `u32[~]` of exactly `width*height` pixels, packed `0x00RRGGBB`. |
| `poll(wait)` | Process input; `true` waits up to 17ms, waking for input. |
| `wait(timeoutMs)` | Wait for input or timeout, then process events; 0 polls, -1 waits indefinitely. |
| `mouseX()`, `mouseY()` | Current mouse position, origin at upper left. |
| `button(LEFT)`, `key(code)` | Current pressed state; printable keys use uppercase ASCII. |
| `close()` | Release native window and pixel storage; safe to repeat. |

`present`, `poll`, and `wait` return `OK` (0), `CLOSED` (1, normal close/Escape), or
`ERROR` (-1). Stop polling after either terminal result and call `close`.
The initial input API samples held state; it is not a queue of every transition.

Waits may return early; values below -1 are errors. Recheck the monotonic
clock after waking. The [time library and animation example](time.md) combine
this wait with cooperative timer deadlines. Backend API version 2 adds the
wait operation; rebuild older MiniFB adapters from the cached sources.

MiniFB retains its last framebuffer for repaint events. The adapter copies the
Simple array into native-owned storage before presenting; it never retains a
Simple heap pointer. Framebuffer allocation, the copy, and window/input calls
are the only native work here. Drawing primitives and the games can be built
in Simple on top of this boundary.
