# Fixed-semantics C intrinsics

An intrinsic is an ordinary C binding with a reserved external symbol and a
fixed signature and meaning. The compiler may replace a known call with ideal
IR nodes; it does not match the spelling of the caller's local variable or a
Simple function with the same name. No new expression syntax is involved.

The first intrinsics preserve IEEE binary64 representations:

| External C symbol | Signature | Public alias |
| --- | --- | --- |
| `simple_f64_bits` | `{ f64 -> i64 }` | `sys.math.f64Bits` |
| `simple_f64_from_bits` | `{ i64 -> f64 }` | `sys.math.f64FromBits` |

```simple
i64 bits = sys.math.f64Bits(1.0);          // 0x3ff0000000000000
f64 value = sys.math.f64FromBits(bits);    // 1.0
int numeric = value;                     // Numeric conversion, still 1.
```

Both operations preserve signed zero, infinities, subnormals, and NaN signs and
payloads, including the signaling bit. They perform no arithmetic and have no
memory effects. An `i64` holds the bit pattern; its sign is irrelevant to the
conversion. Applying either operation followed by the other is an identity.
Floating-point arithmetic between conversions retains its usual semantics and
may change a NaN representation.

`Intrinsic.java` owns the reserved-symbol table, signatures, effect queries,
inlining, and ideal nodes. Call/CallEnd delegate to it. The cheap inliner checks
intrinsics before requiring a Simple body or Return edge. It validates the
signature and actual arguments, then replaces the complete control/memory/value
state. For these pure operations, control and memory pass through unchanged.
Aliases and imported bindings use the existing external-function identity map.

Unknown indirect targets keep ordinary calls. `simple_crt.c` supplies real C
definitions, using `memcpy` to preserve representations without aliasing tricks.
These symbols have fixed semantics: a replacement implementation must honor the
same contract. Ordinary C functions remain opaque to the optimizer.

The ideal nodes support constant folding, inverse cancellation, evaluation, and
serialization. Floating-point constant interning and serialization retain raw
NaN bits. x86 emits `MOVQ` between XMM and integer registers; ARM emits `FMOV`,
and RISC-V emits `FMV.X.D` / `FMV.D.X`. Register allocation may add copies or
spills, and normal module-initialization checks still apply.

Future intrinsics such as compare-and-swap or fences must define their memory
and escape effects explicitly and expand to nodes with appropriate ordering
dependencies. They must not inherit the pure conversions' passthrough memory
behavior. Atomic operations and hardware square root are not introduced here.

`make test CLASS=IntrinsicTest CTAGS=` checks folding, representation preservation,
type errors, side effects, indirect C fallback, binary imports, and instruction
selection on all three backends, plus ARM/RISC-V emulation and native x86 execution.
