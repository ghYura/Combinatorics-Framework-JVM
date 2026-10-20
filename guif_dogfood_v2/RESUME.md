# v2: how to rerun and reproduce

V1 is completed and preserved in [../guif_dogfood](../guif_dogfood/README.md). V2 was designed and partly run by Codex, paused at its quota limit, then continued and completed by Claude on 2026-10-19 (see the last section of [design.md](design.md)). Product code is unchanged; [report.md](report.md) is the result.

Run from the repository root. Everything is headless: the remembered trail is in memory, `XDG_CONFIG_HOME` points into `target/guif-dogfood-v2/config`, and nothing opens on the live display.

1. Build the application jar once, offline: `mvn -o -B -q package -DskipTests -pl fw-app -am`
2. Inventory, verdict codes, scenarios and manifest: `python3 guif_dogfood_v2/build_assets.py --sweeps --repro`
3. Run families through the Framework (all of the manifest without names): `python3 guif_dogfood_v2/run_suite.py run [name ...]`. It compiles the probe, runs `validate`, `candidates` and `run` per family, rebuilds the evidence index, and fails if a protected file changed.
4. Ledgers from the selected evidence only: `python3 guif_dogfood_v2/report.py`
5. Closing JUnit baseline and protected-file check: `python3 guif_dogfood_v2/run_suite.py end`

After a product fix, check it as its own record without touching the investigation's: `python3 guif_dogfood_v2/run_suite.py verify <phase> <name> ...` (writes `runs-<phase>.json`, `evidence-<phase>.json` and a protected-file snapshot taken after the fix), then `python3 guif_dogfood_v2/compare.py <phase> "<title>" <button id> ...` for the before/after page (see [fix1.md](fix1.md)).

One reproducer (one candidate, the verdict names the button):

```
java -Xmx1400m -Djava.awt.headless=true -cp fw-app/target/fw-app.jar:target/guif-dogfood-v2/probe.jar \
  org.ghyura.cf.app.Main run --scenario guif_dogfood_v2/repro/<name>.json --config guif_dogfood_v2/repro/<name>.properties
```

Its evidence is written to `target/guif-dogfood-v2/fixtures/<family>-<number>/evidence.json`, named by the `evidence_id` the candidate prints.

The calibration driver (`probe/CalibrateV2.java`) runs sweep rows one after another in one JVM for checking a setup; its output is not evidence, and its fixture folders are deleted afterwards.
