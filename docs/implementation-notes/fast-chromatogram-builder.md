# Fast chromatogram builder

## Intention

The ADAP chromatogram builder sorts all data points of all selected scans by intensity, which is
slow, holds every data point as an object in one int-indexed array and assigns data points to fixed,
non-overlapping Guava m/z ranges around the first seed data point. The ranges cause holes (a data
point of an ion falls into the range of a neighbor seeded at another retention time) and duplicate
chromatograms (the scatter of one ion spills into an adjacent, clipped range). The fast
chromatogram builder produces the same kind of result, one extracted ion chromatogram per m/z
channel across all selected scans, without a global sort, with memory proportional to the output
and without these holes and duplicates.

## Decisions

- The parameter names, including the legacy names ADAP maps on load, equal the ADAP builder
  parameters so a batch step can switch modules. The image builder still uses the ADAP task,
  imaging is not supported here. The batch wizard still uses the ADAP builder.
- Pass 1 loops once over the scans. Each m/z sorted mass list is merged against the active traces,
  which are sorted by their intensity weighted center. A data point joins the trace with the lowest
  cost within the tolerance, a trace takes at most one data point per scan, conflicts are resolved
  by a greedy matching so a losing data point can join its next best trace. The cost is the squared
  relative m/z distance plus a penalty for intensity jumps beyond 5x between neighboring scans; the
  penalty keeps noise close to the center from replacing the signal of a trace.
- Only statistics of closed traces are kept. Pass 2 replays pass 1 and routes the data points.
  Contract: `MassTraceSweeper` must stay deterministic, the replay relies on identical trace ids.
- Channel consolidation visits recorded traces by decreasing maximum intensity. A trace joins the
  closest channel seed within the tolerance, otherwise it starts a channel if it reaches the
  minimum height (only intense signals start chromatograms, like ADAP). Traces that repeatedly
  received data points in the same scans (collisions) stay in separate channels: they are signals
  resolved by the instrument within the tolerance, which ADAP merges or loses.
- Complementary traces up to 2x the tolerance join a channel if they overlap in time with its seed
  or continue it within the gap allowance and never share a scan. One ion yields one data point per
  scan, so this is one ion with more scatter than the tolerance or a centroid jump, not a second
  ion.
- Traces with a single data point, and short traces that cannot start a channel, are not recorded.
  Their data points are loose: in pass 2 a loose data point fills the closest channel within the
  tolerance that has no data point in this scan. This fills holes and keeps the low level signals of
  the full chromatogram, which the resolvers use for their chromatographic threshold.
- One data point per channel and scan: two member traces keep the more intense data point, member
  data points win over loose ones, among loose data points the closest to the center wins.
- Traces are closed after 3 scans without data point, traces with a single data point after 1 scan.
  The early close keeps the number of active noise traces low. It cannot connect an ion whose m/z
  alternates every scan by more than the tolerance, `singleDataPointMaxGapScans = 1` can, at ~50%
  more runtime on noisy data.
- The height filter is applied within the consecutive segment, which the ADAP code comment
  describes but its implementation does not do (it never resets the maximum). ADAP therefore keeps
  chromatograms whose only consecutive segment stays below the minimum height, the fast builder
  does not.
- Zero, negative and non-finite values are skipped. Flanking zeros are added next to every detected
  data point like in ADAP, with the unweighted mean m/z.
- MS2 scans are found with a precursor m/z sorted index and return the same scans in the same order
  as `ScanUtils.streamAllMS2FragmentScans`.
- Internal tuning values are grouped in `FastChromatogramBuilderOptions` and are not user
  parameters. `ChromatogramBuilderBenchmark` (tag `benchmark`, run with `gradlew benchmark`)
  compares both builders on synthetic data with ground truth and on real data.
