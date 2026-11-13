# Time and single-threaded animation

`sys.time` uses bare `i64` values: absolute instants are milliseconds since
the Unix epoch in UTC; durations are milliseconds. Monotonic readings belong
to a separate clock domain. Never subtract a wall-clock reading from a
monotonic deadline.

## Clocks and waits

| Call | Result |
| --- | --- |
| `time.now()` | Unix-epoch milliseconds; may jump when the system clock changes. |
| `time.monotonic()` | Monotonic milliseconds for elapsed time and deadlines. |
| `time.nanoTime()` | Monotonic nanoseconds, with the clock's available resolution. |
| `time.sleep(duration_ms)` | Sleep for a nonnegative duration. |
| `time.sleepUntil(deadline_ms)` | Wait for a monotonic millisecond deadline; returns immediately if past. |
| `time.remaining(deadline_ms, current_ms)` | Nonnegative difference, saturating on overflow. |

Clock errors return `-1`; sleeps return zero on success or `-1` on error,
with `sys.libc.errno()` available. Interrupted sleeps resume rather than
returning early. Wakeup times are approximate, and these calls do not wake a
suspended laptop. Sleeping blocks the one Simple execution thread; use
`gfx.wait` when window input must remain responsive.

`seconds(n)`, `minutes(n)`, `hours(n)`, and `days(n)` convert signed counts
to milliseconds. `toSeconds(ms)`, `toMinutes(ms)`, `toHours(ms)`, and
`toDays(ms)` truncate toward zero. A duration day is exactly 24 hours,
independent of daylight saving. `toNanos(ms)` and `fromNanos(ns)` convert
between millisecond and nanosecond units. Multiplication overflow returns
`-1`. Constants `SECOND`, `MINUTE`, `HOUR`, and `DAY` are also available,
defined as `1000`, `60*SECOND`, `60*MINUTE`, and `24*HOUR` respectively.
`NANOS_PER_MS` is `1000*1000`. Overflow checks use the shared
`sys.integer.MIN_I64` and `MAX_I64` bounds; `MAX_I32` and `MAX_U32` in that
module also describe the library's array and timer-ID limits.

## String interpolation

Time formatting is built into the existing string interpolation syntax:

| Format | Example | Named style |
| --- | --- | --- |
| `%t(instant)` or `%tF(instant)` | `2026-11-12T00:34:05.123Z` | `time.ISO` |
| `%tr(instant)` | `12:34am` | `time.CLOCK12` |
| `%tR(instant)` | `00:34` | `time.CLOCK24` |
| `%tT(instant)` | `00:34:05` | `time.CLOCK24_SECONDS` |
| `%tD(instant)` | `12/11/26` | `time.DATE` |
| `%tM(duration)` | `02:03.456` | `time.ELAPSED` |

For example, `"UTC %tR(sys.time.now())"`. Width and left alignment work as
for strings: `%30t(instant)` and `%-30t(instant)`. Precision and numeric flags
are rejected. `%tM` formats a duration, with total minutes rather than wrapping
at an hour; negative durations keep their sign.

Interpolation uses UTC, making its output stable across machines.
`time.format(instant, style)` is the equivalent library call;
`time.formatLocal(instant, style)` uses the system's local zone. Local ISO
output includes a numeric offset instead of `Z` when nonzero, retaining
offset seconds for historical zones. Invalid calendar values/styles produce
`<invalid time>`. The ISO form retains all millisecond bits within the supported
calendar range; short formats intentionally omit information.

## Calendar values and absolute parsing

`time.utc(instant)` and `time.local(instant)` return a `DateTime` with fields
`year`, `month`, `day`, `hour`, `minute`, `second`, `millisecond`, `weekday`
(Sunday = 0), `offsetSeconds` (east of UTC), and `daylight`. A zero year means
conversion failed. `time.zoneName(instant)` returns the local abbreviation or
an empty string on error. Local conversion uses the operating system's zone
rules, including DST at that instant; no time-zone database is downloaded.

`time.makeUTC(year, month, day, hour, minute, second, millisecond)` validates
and constructs an instant, returning `-1` on invalid fields. `time.valid(...)`,
`time.leap(year)`, and `time.monthDays(year, month)` support calendar checks.
Calendar conversion supports Gregorian years 1 through 9999, bounded by
`time.MIN` and `time.MAX`, and rejects leap seconds.

`time.parse(text)` accepts strict absolute ISO text:
`YYYY-MM-DDTHH:mm:ss[.SSS]Z` or the same with an explicit `+HH:MM`/`-HH:MM`
offset (optionally with `:SS`). Fractions have exactly three digits. No relative
time parsing, implicit local zone, locale-dependent parsing, or named-zone
conversion is provided. Offsets must have hours 00–23 and minutes/seconds 00–59.
Invalid text or an out-of-range result returns `-1`. By design, this sentinel
is indistinguishable from the valid instant `1969-12-31T23:59:59.999Z`;
formatting and calendar conversion still support that instant.

## Cooperative timers

Create `val timers = new sys.time.Timers();` on the game thread:

Timer IDs are `u32`, with `0` reserved for registration failure or no timer due.
Deadlines, delays, and periods remain `i64` milliseconds; their parameter,
field, and local names end in `_ms` to distinguish them from IDs.

| Operation | Meaning |
| --- | --- |
| `after(delay_ms)` | Register a one-shot timer; return its positive `u32` ID, or `0`. |
| `every(period_ms)` | Register a repeating timer; period must be positive. Returns a `u32` ID, or `0`. |
| `afterAt(current_ms, delay_ms)`, `everyAt(current_ms, period_ms)` | Supply a monotonic reading explicitly, useful for deterministic tests. |
| `cancel(id)` | Remove a timer; return whether it was present. |
| `nextDeadline()` | Earliest monotonic deadline, or `-1` if empty. |
| `poll(current_ms)` | Return the next due `u32` ID, or `0` if none. |

Poll repeatedly to drain due timers. Deadlines order delivery, then registration
ID breaks ties. Repeating timers advance from their previous deadline, skipping
missed ticks to the first future deadline rather than drifting or replaying a
backlog. Deadline overflow rejects registration or cancels a repeating timer.
IDs are never reused within a collection; registration fails once all nonzero
`u32` IDs have been issued.
There are no callbacks or timer threads: the caller handles each returned ID.

`gfx.wait(timeout_ms)` processes events after waiting for input or a timeout:
zero polls, `-1` waits indefinitely, values below `-1` fail. It returns the same
`OK`, `CLOSED`, or `ERROR` statuses as `gfx.poll`, and may wake early. Re-read the
monotonic clock after each wake. This allows one thread to handle timers,
input, simulation, and drawing without busy waiting.

The [digital and analog clock example](examples/Clock.smp) combines these pieces. From
the repository root, with graphics dependencies already cached:

```sh
make -C chapter31 clock-example CTAGS=  # Build without opening a window.
make -C chapter31 run-clock CTAGS=      # Build the backend and run.
```

Click the button or press Space to switch between green seven-segment digits
and an analog dial with smoothly moving hands. Both display system-local time;
Q/Escape exits. Passing any argument directly to the executable closes it after
ten frames, switching faces halfway through. Hands are computed from the current
time, so delayed frames do not create a simulation catch-up backlog.
