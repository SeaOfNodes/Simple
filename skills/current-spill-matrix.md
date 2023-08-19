# Current allocator comparison

`make spill-stats` measures Chapters 20, 21, 22, 23, 24b, and 28b against the
historical test cohorts. Cohort IDs and test names retain their old numbers;
column headings identify the current compiler directories.

These are separate from the dated [pre-split measurements](../docs/regalloc-spills.md).
After a successful run, `python build-support/spill-matrix.py --reuse --update-docs`
updates only this table. Fresh logs use 24b/28b names so old 24/25 results cannot
silently become measurements of new compiler snapshots.

<!-- spill-matrix:start -->
No post-split measurement has been published yet.
<!-- spill-matrix:end -->
