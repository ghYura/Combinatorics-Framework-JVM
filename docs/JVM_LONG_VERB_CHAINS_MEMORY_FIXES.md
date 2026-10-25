# Long verb-chain memory fixes — 2026-10-25

Implemented the fixes identified in [the memory investigation](JVM_LONG_VERB_CHAINS_MEMORY_INVESTIGATION.md). The earlier report's measurements describe the implementation before this change.

The changes preserve the sequence of authored verbs, generated content, row IDs, first-occurrence parents and configuration defaults:

- **Budgeted distinctification scratch.** Scratch capacity is reserved before allocation; growing a buffer accounts for both old and new storage during replacement. Dropping the index or closing the table releases its lease.
- **Safe index-growth refusal.** An index grows before its new row is appended. A failed reservation therefore cannot leave a row absent from the duplicate index. Duplicate lookup also rejects mismatched key encodings before comparing payload bytes.
- **Earlier release of obsolete stages.** The generation arena retains active row sources and their dependencies, including completed sheets exposed by the result. After materialization it closes unreachable tables/indexes. Shared operands and lazy descendants remain readable.
- **Conservative uniqueness propagation.** First-stage proofs, exact distinctification and safe full-row selection transformations carry uniqueness information forward. Already proven unique factors bypass assembly's temporary duplicate-removal copy. Unknown cases still use exact scanning.
- **Distinctification during Group output.** When enabled, duplicate rewritten Group rows are discarded as they are produced. Raw IDs continue to advance, so subsequent stages retain the previous ID/parent semantics.
- **Formula text on demand.** An expanded stage retains its own formula fragment and source reference, without a stored copy of the entire preceding formula string.

The implementation is in [RowBlocks](../fw-store/src/main/java/org/ghyura/cf/store/RowBlocks.java), [Arena](../fw-generation/src/main/java/org/ghyura/cf/generation/Arena.java), [RowSources](../fw-generation/src/main/java/org/ghyura/cf/generation/RowSources.java), [SheetStages](../fw-generation/src/main/java/org/ghyura/cf/generation/SheetStages.java), [ExpandedRows](../fw-generation/src/main/java/org/ghyura/cf/generation/ExpandedRows.java), and the corresponding source/dependency and assembly helpers.

**Measured changes.** Row counts and content checks passed in both the original investigation and the updated probes. Byte figures below exclude dictionary/scenario and application plan/parser overhead.

| Case | Before | After |
| --- | ---: | ---: |
| 128 repeated `FW_Combi(1)` stages, three final rows, stage distinctification on | 127 retained tables; 715,264 B | No stored intermediate tables; 0 B of generated-row/index leases |
| Four `FW_Permut()` stages, six final distinct rows | Three retained tables; 16,896 B | One final table; 5,632 B |
| Retained formula characters in the 128-stage lazy chain, distinctification off | 73,914 | 762 |
| 50,000-key row plus distinctification scratch under a 60,000 B budget | Accepted despite 100,000 B of internal payload arrays | `RESOURCE_LIMIT` before inserting the row |
| Same row under a 120,000 B budget | Scratch payload omitted from accounting | Accepted with 101,888 B admitted; buffers released correctly |

Regression tests also verify that a Group producing 6,561 raw tuples, all rewritten to the same eight-key row, completes within 16 KiB beyond its dictionary reservation. The following authored stage still gets row ID 6,562 and parent ID 1. A separate 64-stage permutation test fits in the same additional budget while retaining the final table only. Tests cover shared/reused operands and first-occurrence IDs after pruning.

**Validation.** Passed 405 core tests, 14 desktop integration tests and 22 repository/architecture gates: **441 tests, zero failures/errors/skips in the final selected checks**. The rebuilt application JAR also passed **119 probe assertions**, including the XLSX, JSON, YAML and TOML reader paths, large lazy spaces, both key widths and explicit resource refusals. Eight new regression tests are in [StoredTablesTest](../fw-store/src/test/java/org/ghyura/cf/store/StoredTablesTest.java) and [BoundedGenerationTest](../fw-generation/src/test/java/org/ghyura/cf/generation/BoundedGenerationTest.java). No QA-versus-JVM comparison or SUT execution was run.

The initial gate run in the working directory found two unrelated failures: untracked `guif_parity/*.py`/`*.sh` files violate the Java-source gate, and its untracked `ParityProbe.java` lacks the required notice. Those files were preserved. All 22 gates then passed against a snapshot of **tracked working-tree source including these edits**, linked to the current compiled outputs. No gate was disabled or weakened. An unfiltered gate scan of that original working directory still sees the separate untracked harness.

Logs and measurements are local under `target/memory-fixes-20261025/`: `core-tests.log`, `integration-gates.log`, `review-gates.log`, `package.log`, `measurements.tsv`, `duplicate-cost.tsv`, `assembly-cost.tsv` and `findings.txt`. The original evidence remains under `target/long-verb-memory-20261024/`.

**Remaining limits.** Exact duplicate removal can still require a scan/index when uniqueness is unknown. The 3,540-row `Combi(2), Permut()` example without a generation uniqueness proof still incurs that assembly scan; it is not silently assumed unique. Group units, genuinely unique outputs and mixed-length indexes can remain large. Formula rendering and row access still traverse lazy dependencies. These changes do not establish an absolute bound on all JVM/parser/transient heap use or make arbitrary combinatorial growth feasible.

The local runnable application was rebuilt at `fw-app/target/fw-app.jar`. This work does not update an already published GitHub asset or the existing Oct2026 release ZIP. Changes remain available for review and manual commit/publication.
