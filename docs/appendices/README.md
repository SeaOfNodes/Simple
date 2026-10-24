# Appendices

These notes supplement the numbered compiler snapshots; they do not introduce
another compiler directory.

- [Native library construction](native-libraries.md): bundled `sys`, native
  symbols and thunks, runtime support, and executable examples in Chapter 28b.
- [Optimizer and backend tuning](optimizer-tuning.md): spill ranking,
  rematerialization, inlining, and historical measurements from the split's
  original snapshots. [Measurement methods](../regalloc-spills.md) describe
  what those numbers include.

The [roadmap](../roadmap.md), [incremental build design](../incremental-builds.md),
and [review queue](../chapter-backports.md) distinguish future work from current
behavior. The [stage audit](../stage-audit.md) records known limits and cleanup.
