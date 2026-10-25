# JVM memory behavior with many verbs in one FW_Seq row

**2026-10-25 follow-up:** the confirmed issues and targeted optimizations have now been addressed; see [implemented fixes and validation](JVM_LONG_VERB_CHAINS_MEMORY_FIXES.md). The investigation and measurements below remain a record of the earlier implementation.

Investigation date: 2026-10-24. Sources: the current local JVM checkout, based on commit `7528149`. Verification used small JVM-only probes, with no QA comparison or SUT execution. No product code or configuration defaults were changed.

**JVM has substantial memory optimizations, especially compact keys and lazy generation. Their benefit depends on the operators in the chain. Distinctification can increase peak memory, and this investigation confirmed both avoidable retention of earlier tables and a memory-accounting gap.** My earlier description of JVM as the strongest foundation needs these qualifications; “fully optimized” or “all heap usage is strictly bounded” would overstate the evidence.

**Input format and verb count.** XLSX, JSON, YAML and TOML workbook inputs converge on the normalized scenario and the same planner/generator. A direct FW_Seq row uses lists of cells/directives and an integer stage index; its verb count is not constrained by the Byte key range. The [planner](../fw-plan/src/main/java/org/ghyura/cf/plan/SeqPlanner.java#L239) collects directives, and [SheetStages.run](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java#L135) executes them in order. There is no general fusion of repeated stages or removal of redundant verbs in these paths.

Probes wrote and loaded **32 verb cells in one row in each of XLSX, JSON, YAML and TOML**. All four retained all directives and generated the expected three final rows. Separate engine probes exercised 128-verb chains. These results establish those cases, not an unlimited-chain guarantee. The separate [ScheduleBuilder API](../fw-input/src/main/java/org/ghyura/cf/input/builder/ScheduleBuilder.java#L65) has a three-verb authoring limit; that restriction does not apply to direct workbook rows.

Format still affects loading memory. [XlsxWorkbookReader](../fw-input/src/main/java/org/ghyura/cf/input/XlsxWorkbookReader.java#L86) opens a POI workbook and builds normalized objects; this is not streaming XLSX processing. [InputSource](../fw-input/src/main/java/org/ghyura/cf/input/InputSource.java#L53) normally caps a document at 256 MiB and reads its bytes. The shared application loading path uses [ScenarioInputs](../fw-desktop/src/main/java/org/ghyura/cf/desktop/session/ScenarioInputs.java#L40) and [ScenarioLoader.readingBytes](../fw-input/src/main/java/org/ghyura/cf/input/ScenarioLoader.java#L167): ten times text-document bytes, or 25 times XLSX uncompressed-part bytes, using at least the archive's size. Those admission factors are estimates, not universal heap bounds. Direct embedding calls must choose the appropriate admission/ownership path.

**What the internal types save.**

| Item | Representation and consequence |
| --- | --- |
| Generated row content | `KeyRow` stores encoded `byte[]` labels; generated rows do not repeatedly store the full input strings. |
| Encoding | One encoding is selected for the dictionary/plan. `SIGNED_BYTE` uses one byte and all 255 labels −127…127; −128 is reserved. `PACKED_LEGACY16` uses two bytes and 65,535 usable labels. Native `AUTO_PRESERVE` can widen when necessary. |
| Input identity | Each value cell retains its own label, including equal-text cells. Equal text does not automatically share a key. Value and sheet namespaces both matter to capacity. |
| Stored row metadata | Two 64-bit integers for ID/parent, plus an integer end offset: 20 bytes per allocated row slot before payload/page overhead. |
| Computed counts/ranks | `BigInteger` supports spaces beyond `Long.MAX_VALUE`. Stored row IDs remain limited to the signed 64-bit range. Exact huge counts do not imply feasible enumeration/storage. |
| Verbs and working data | Directive objects, strings, lists, transient `int[]` arrays and other Java objects; they are not each compressed into one byte. |

Sources: [KeyEncoding](../fw-model/src/main/java/org/ghyura/cf/model/key/KeyEncoding.java#L40), [KeyDictionary](../fw-store/src/main/java/org/ghyura/cf/store/KeyDictionary.java#L44), [KeyRow](../fw-model/src/main/java/org/ghyura/cf/model/key/KeyRow.java#L38), [RowBlocks](../fw-store/src/main/java/org/ghyura/cf/store/RowBlocks.java#L49).

Probes confirmed one-byte encoding at 255 values and two-byte encoding at 256. A million generated rows does not require a million different key labels. Conversely, adding equal-text value cells can force widening because their identities remain separate. Widening affects key payloads; it does not simply double total run memory.

**The operation determines whether a chain stays lazy.**

| Operation / situation | Implementation and memory behavior |
| --- | --- |
| First ordinary generator | [TupleRows](../fw-generation/src/main/java/org/ghyura/cf/generation/TupleRows.java#L38) and ranked operators describe the space and compute requested rows; they do not store every output. |
| Later ungrouped pass with uniform input length and length-dependent count | [ExpandedRows](../fw-generation/src/main/java/org/ghyura/cf/generation/ExpandedRows.java#L87) divides an output rank into source rank and within-row rank. No generated-row table or prefix index is required. |
| Mixed lengths/content-dependent counts | `ExpandedRows` scans the source and builds [PrefixCounts](../fw-generation/src/main/java/org/ghyura/cf/generation/PrefixCounts.java#L38), one cumulative count per source row, using budgeted long columns and then BigInteger entries if needed. |
| Ordinary `FW_(...)` joiner | [JoinedRows](../fw-generation/src/main/java/org/ghyura/cf/generation/JoinedRows.java#L106) computes output rows. Mixed operand lengths can require rank/length indexes and prefix counts. |
| `FW_Group` followed by a generator | [groupedPass](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java#L353) loads/sorts prior rows as units and materializes output in `RowBlocks`. Rewrites also allocate temporary strings/arrays. |
| Grouped nested operand `FW_()G` | [operand](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java#L651) scans and concatenates the operand into one stored row, with temporary payload storage. |
| Stage distinctification | [distinctActive](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java#L694) skips known-unique tables; otherwise scans the stage and stores unique rows with a content index during construction. |
| Assembly distinctification | [Factor.distinct](../fw-generation/src/main/java/org/ghyura/cf/generation/Factor.java#L76) stores unique factor rows/original ranks, avoiding materialization of the whole Cartesian product. An already unique factor is still copied/scanned before this method discovers it can discard the copy. |

Group can hold units, the previous source and new output simultaneously. Its output loop uses ordinary `append`; distinctification follows afterward. Thus enabling distinctification does not stop Group from first storing duplicate output. Replacements that collapse many rows can still hit a resource limit before their eventual small unique result becomes available.

`RowBlocks` uses growing pages rather than copying a single enormous table array. Its first page reserves **5,632 bytes**: 256 bytes overhead + 64 × 20 bytes metadata + 4,096 bytes payload capacity. Pages grow up to 1,024 row slots and normally 64 KiB payload; a larger individual row needs a larger page. Three one-key rows still consume a complete first page.

**Measured strengths and costs.** “Generation reservations” below count generated-table/index leases, excluding dictionary/scenario and application plan/parser overhead. Zero does not mean zero objects or zero heap usage.

| Probe | Final rows | Generation reservations | Observation |
| --- | ---: | ---: | --- |
| 2,000 values: `Combi(2), Permut()`; distinct off | 3,998,000 | 0 B | First/last rows accessible without materializing the space. |
| 60 values: same chain; distinct off | 3,540 | 0 B | Lazy. |
| 60 values: same chain; distinct on | 3,540 | 340,480 B | No rows removed; the later pass was nevertheless stored. |
| Eight values: `Combi(all), Permut()`; distinct off | 109,600 | 32,832 B | Prefix counts for mixed lengths, not a stored output table. |
| Three values: `Combi(1), Group, Combi(2)` | 3 | 5,632 B | Group output materialized. |
| 25 values: one unpromoted `Permut()` | 25! = 15,511,210,043,330,985,984,000,000 | 0 B | Exact count/boundary-row access beyond the long range; no full enumeration. |

Four full permutation stages over three values represented **1,296 rows lazily** with distinctification off. With it on, the final result was **six rows**, with 16,896 bytes of retained generated tables. That substantially reduces downstream candidates/work, while increasing generation storage. Those are different benefits.

Assembly showed another peak: for the 3,540 already unique rows, total admitted memory before assembly was 19,872 bytes. Assembly distinctification off kept that peak. Turning it on raised the peak to **458,784 bytes**, then returned to 19,872 after discarding its temporary copy/index/ranks. Final retained memory alone hides this cost.

**Confirmed issue: earlier stored stages remain alive.** Repeated `FW_Combi(1)` over three values always ends with the same three one-key rows. With stage distinctification enabled:

| Verbs | Final rows | Retained generated tables | Total generation reservations | Final table alone |
| ---: | ---: | ---: | ---: | ---: |
| 2 | 3 | 1 | 5,632 B | 5,632 B |
| 8 | 3 | 7 | 39,424 B | 5,632 B |
| 32 | 3 | 31 | 174,592 B | 5,632 B |
| 128 | 3 | 127 | 715,264 B | 5,632 B |

[laterPass](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java#L337) registers each `ExpandedRows` in the generation [Arena](../fw-generation/src/main/java/org/ghyura/cf/generation/Arena.java#L34). Each source refers to its predecessor. Distinctification makes a new table, but releases its input early only when that input itself is a `RowBlocks`. A usual later-pass input is `ExpandedRows`; the preceding sources/tables therefore remain owned until generation closes.

Also, `laterPass` marks results known-distinct only for at most one output row. It does not propagate the stronger fact that this particular operation preserves uniqueness. Consequently these already unique three-row stages are repeatedly scanned and stored.

This is measured retention during a run, not a demonstrated permanent leak: all admitted leases returned to zero on close. Some old sources are necessary for lazy descendants/shared joiners. Early release must respect those dependencies and reuse semantics.

**Long lazy chains have overhead too.** With distinctification off, the repeated chains needed no generated-row leases, but retained source objects and formula strings. `ExpandedRows.of` embeds the preceding source's formula into a new string. The probe counted 294 retained formula characters at eight verbs, 4,650 at 32 and 73,914 at 128: roughly quadratic growth for this pattern. These are character counts, not heap measurements.

Accessing a final row also follows the source chain and reconstructs labels/tuples along it. Compact storage does not imply constant computation per row. Extremely deep chains can add stack pressure; no stack-exhaustion threshold was measured here.

**Confirmed issue: content-index scratch payload is not budgeted.** Hash lookup is followed by full content comparison, so hash collisions do not merge unequal rows. Main index/page allocations take leases. However, [RowBlocks.appendIfAbsent](../fw-store/src/main/java/org/ghyura/cf/store/RowBlocks.java#L186) grows a separate `scratch` byte array without reserving its variable payload size.

A direct storage probe inserted one 50,000-key row in one-byte encoding:

| Quantity | Bytes |
| --- | ---: |
| Configured budget | 60,000 |
| Reported reservation | 51,856 |
| Owned table payload array capacity | 50,000 |
| Owned scratch array capacity | 50,000 |

The two internal arrays alone contain 100,000 payload bytes, exceeding the budget before metadata/headers. This excludes the caller's input and does not rely on a noisy process-memory reading. Dropping the index clears scratch, but does not account for its construction peak. Long rows from Group/nested operands make this relevant.

The admission counter is therefore not an absolute upper bound on all JVM heap use. Temporary label arrays, rewrite strings, operator objects and formula strings also exist. Their complete cost was not profiled here; this investigation proves the scratch omission without claiming an exhaustive allocation audit.

**Budget enforcement and its limits.** [MemoryBudget.reserve](../fw-store/src/main/java/org/ghyura/cf/store/MemoryBudget.java#L87) refuses charged allocations beyond the limit. Pages, major indexes, Group unit allowances and dictionary storage use it. The full application additionally admits compiled plan/authoring metadata through [ScenarioValidation.prepare](../fw-desktop/src/main/java/org/ghyura/cf/desktop/session/ScenarioValidation.java#L335) and [RunHandle](../fw-campaign/src/main/java/org/ghyura/cf/campaign/RunHandle.java#L374). The engine-level measurements above deliberately exclude that application metadata reservation.

Both forced-limit probes—distinctification and Group units—returned `RESOURCE_LIMIT` and released all admitted leases. There is no table spill to disk or silent data removal to fit. Lease release, garbage collection and process resident memory are separate measurements.

Native `cf.memory.budget.bytes` defaults to 268,435,456 bytes (256 MiB). Generation uses that configured budget; `-Xmx` is a separate JVM limit. The `min(configured budget, heap/4)` rule in `Session.analysisBudget` applies to that analysis helper, not universally to generation. [ConfigConsistency](../fw-config/src/main/java/org/ghyura/cf/config/ConfigConsistency.java#L90) warns if budget exceeds maximum heap; it does not silently clamp every run. Room is still needed for JVM/parser overhead, temporary allocations and the accounting gaps above.

**Implications for authoring.** A long row of small uniform operations can remain economical; a short row containing an expansive Group or distinctification scan can be expensive. Input size, key count, verb count, intermediate cardinality and row width are separate dimensions. The final row count cannot predict peak memory.

Use these settings according to intended semantics:

```properties
# Use the full byte range and widen when necessary:
cf.keys.encoding=AUTO_PRESERVE

# Duplicate removal; these are not universal memory-saving switches:
cf.generation.distinctTables=true
cf.assembly.distinct=true
```

Both distinctification flags default to false. Enabling them can reduce downstream work but force scans/copies and change later Group semantics. Assembly-only distinctification cannot undo earlier costs or semantic effects. Lone-verb promotion is independent; see [the duplicated-verbs report](FW_SEQ_DUPLICATED_VERBS_HISTORY.md). Do not automatically delete authored repeated verbs.

A small preview/output limit does not guarantee cheap preparation. [Session.preview](../fw-desktop/src/main/java/org/ghyura/cf/desktop/session/Session.java#L234) generates stage sources before producing its bounded preview. Group, exact distinctification and prefix-index construction may process the preceding stage fully. Planning/resource estimates must be read with their assumptions.

The most useful implementation follow-ups are:

1. Reserve scratch growth before allocation, including overlap while resizing; verify long-row peaks.
2. Release obsolete stage resources after materialization using dependency-aware ownership, preserving sources still needed by lazy descendants, shared sheets or reuse.
3. Propagate proven uniqueness/shape properties through safe transformations to avoid unnecessary scans/copies.
4. Where semantics permit, distinctify Group output during production, preserving first-occurrence IDs/parents.
5. Share formula expression structures or render formulas on demand instead of retaining every expanded prefix string.

These are proposed improvements, not changes made by this task.

**Verification and reproduction.** Completed **27 bounded cases, 116 assertions passed**: 16 generation cases, two budget refusals, four input formats, one scratch-accounting case, two duplicate-heavy cases and two assembly cases. JVM heap was capped at `-Xmx256m`; ordinary probe budgets were 32 MiB, with smaller budgets for the refusal/accounting cases. No full enumeration of huge lazy spaces, SUT execution or QA-versus-JVM test occurred.

Probes used `fw-app/target/fw-app.jar`, SHA-256 `657d09065042b73d91471334c5d4d55044510b4414471673d8de2cd638075a4c`, on Java 25.0.3. Six relevant packaged classes matched local compiled classes, whose compilation timestamps followed source edits. Results measure admitted bytes and inspected array capacities; they are not heap/RSS profiling or performance benchmarks.

Local evidence: `target/long-verb-memory-20261024/` contains `measurements.tsv`, `duplicate-cost.tsv`, `assembly-cost.tsv`, `findings.txt`, both logs, generated format fixtures and the two probe Java files. This ignored target directory is not published source; the important results are reproduced above.

Reproduce from this checkout, where the investigation artifacts and existing test helper are present:

```bash
javac -cp fw-app/target/fw-app.jar \
  -d target/long-verb-memory-20261024/classes \
  fw-generation/src/test/java/org/ghyura/cf/generation/Scenario.java \
  target/long-verb-memory-20261024/MemoryChainProbe.java \
  target/long-verb-memory-20261024/DuplicateCostProbe.java

timeout 45s java -Xmx256m \
  -cp target/long-verb-memory-20261024/classes:fw-app/target/fw-app.jar \
  org.ghyura.cf.generation.MemoryChainProbe target/long-verb-memory-20261024

timeout 30s java -Xmx256m \
  -cp target/long-verb-memory-20261024/classes:fw-app/target/fw-app.jar \
  org.ghyura.cf.generation.DuplicateCostProbe target/long-verb-memory-20261024
```

For distinctification semantics across the editions, see [the history report](TABLE_DISTINCTIFICATION_HISTORY.md).
