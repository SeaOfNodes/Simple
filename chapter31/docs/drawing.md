# Drawing in Simple

`sys.draw` draws into an ordinary `u32[]`. It has no native dependencies and
does not open a window. `sys.gfx` presents the resulting pixels using the
optional [MiniFB backend](graphics.md).

```simple
val canvas = new sys.draw.Canvas(600,780);
u32 orange = 0xe47b57;
canvas.clear(sys.draw.WHITE);
canvas.fillRect(20,20,180,80,orange);
canvas.rect(20,20,180,80,3,sys.draw.BLACK);
canvas.circle(300,200,48,8,sys.draw.BLUE);
canvas.fillCircle(420,200,20,sys.draw.GREEN);
canvas.line(40,320,250,440,12,orange);
canvas.text(40,500,"YOUR TURN",4,sys.draw.BLACK);
// After sys.gfx.open succeeds:
sys.gfx.present(canvas.pixels);
```

## Small raster API

Coordinates start at the upper left; x increases rightward and y downward.
Drawing methods return the canvas for chaining. Everything is opaque RGB;
there is no blending, antialiasing, transform stack, or native drawing state.
Coordinates accept `i64` so ordinary widened arithmetic can be passed directly.
Circle radii and circle stroke widths are `int`. Use screen-sized coordinates,
extents, and stroke widths.

| Operation | Behavior |
| --- | --- |
| `rgb(r,g,b)` | Clamp components to 0–255 and pack RGB. `BLACK`, `WHITE`, `RED`, `GREEN`, and `BLUE` are available too. |
| `new Canvas(width,height)` | Allocate a zero-filled pixel array. Dimensions must be positive and their product must fit a signed 32-bit index; invalid sizes exit with status 1. Allocation can still fail for lack of memory. |
| `clear(color)` | Fill the entire canvas. |
| `pixel(x,y,color)` | Set one pixel; ignore points outside the canvas. |
| `fillRect(x,y,w,h,color)` | Fill a half-open rectangle: x through x+w-1, y through y+h-1. |
| `rect(x,y,w,h,thickness,color)` | Draw an inward outline. A sufficiently wide outline fills the rectangle. |
| `fillCircle(cx,cy,radius,color)` | Fill a circle; radius zero draws its center pixel. |
| `circle(cx,cy,radius,thickness,color)` | Draw an inward circular outline. |
| `line(x0,y0,x1,y1,thickness,color)` | Draw a round-ended segment. A zero-length segment draws a round dot. Thickness is its geometric diameter, sampled at integer pixel centers. |
| `text(x,y,string,scale,color)` | Draw a 3×5 bitmap font, with one column between characters. Upper/lowercase ASCII letters, digits, spaces, and basic punctuation are supported. Newline advances six scaled pixels. Unknown characters use `?`. |

Shapes clip to the canvas. Nonpositive rectangle extents, stroke widths, or
text scale do nothing; negative circle radii do nothing. Line and circle
coverage scans only the clipped bounding box, which is sufficient for small
turn-based games. This is a starting raster library rather than a fast animated
renderer. The pixel array remains available for direct drawing and inspection.

Packed colors use hexadecimal literals (`0xRRGGBB`); `rgb(r,g,b)` remains handy
for computed components. The font masks use leading-zero octal, with five
digits after the prefix: each digit is one three-pixel row, top to bottom.
For example, `A` is `025755`: rows `010`, `101`, `111`, `101`, `101`.

## Button widget

`new sys.draw.Button(x,y,width,height,label)` creates a rectangular software
button. It owns no window or native resources. Feed pointer coordinates and
the held mouse-button state to `update(x,y,down)` once per event-loop iteration.
It returns true once on release inside, provided the press began inside.
Dragging in from outside does not activate it; releasing outside cancels.

`hovered` and `pressed` expose visual state. Set `enabled=false` to cancel a
pending press and disable activation; `label` can be changed at any time.
`contains(x,y)` uses half-open bounds. `draw(canvas,scale,foreground,background,active)`
paints the button and centers its 3x5 label when it fits. Colors belong to the
caller. Keyboard shortcuts belong to the application; this small widget has
no focus system. The [clock example](examples/Clock.smp) shows mouse activation
and a Space shortcut in the same single-threaded loop.

## Graphical tic-tac-toe

From `chapter31`:

```sh
make run-graphical-tictactoe CTAGS=
```

Click an empty square or press **1–9**, numbered left-to-right, top-to-bottom.
You are X; the CPU is O. It wins when possible, blocks your immediate win,
then prefers the center, corners, and edges. The result stays visible with
the winning line highlighted. Press **R** or **Space** to start a new game,
or **Q**/**Escape** to quit. Restart is also available during a game.

Held buttons and keys do not take repeated turns. Moving the pointer highlights
empty cells. The canvas is redrawn only after a move, restart, or hover change;
polling still processes window events and waits between polls.

`make graphical-tictactoe CTAGS=` builds without opening a window or requiring
MiniFB. Its output is `build/examples/x86_64_v2_win64/TicTacToeGraphical.exe`
on Windows or `build/examples/x86_64_v2_SystemV/TicTacToeGraphical` on Linux.
Backend discovery and explicit overrides follow [the pixel-window instructions](graphics.md).
Passing any argument performs a five-poll, self-closing smoke test.

The original `make tictactoe` still builds the terminal game.
The graphical game also uses optional [background audio](audio.md): a soft
boop for accepted moves, rising tones for a player win, and falling tones for
a CPU win. Bad input is silent. The run target builds/configures both backends.
`make test CLASS=DrawTest CTAGS=` checks raster pixels and complete game turns
without a display. `make tests_nographics CTAGS=` runs the ordinary suite with
the backend disabled.
