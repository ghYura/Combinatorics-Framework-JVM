# Refinements after results

All six scenarios were executed through the Framework. The covering reports and verdict tables are in [results_summary.md](../results_summary.md).

| Asset | Why it was added | Observed result |
|---|---|---|
| D001_accessible_names_t3 | Raise graph rule content × operation × scan boundary from pairwise to triples. | 18 cases; all expose unnamed inputs. |
| D002_sieve_spinner_t3 | Rule body pair/formula × value 0/1/Long.MAX_VALUE × next/previous direction. | Six enabled pair-rule cases fail with code 20; six disabled formula-rule boundaries pass. |
| D004_disguised_metrics_t3 | Corpus content × coverage format × single/double Analyze. | 32 cases: 16 valid/unverdicted cases pass; 16 misleading CSV/checkpoint cases fail with code 16. |
| R003_runner_interactions_t3 | Raise runner/settings/input combinations to triples after configuration and wrong-file triage. | All 20 feasible triples pass; Java with Python-only invalid settings remains excluded. |
| R004_sieve_guard_sequences | Exercise rule Up/Down and the stable Only when editor's condition construction, move and deletion controls. | One enabled-control sequence passes. |
| R005_cancel_impact | Start impact validation and activate Cancel work in the same EDT callback. | One sequence passes; the cancellation path completes teardown. |

The spinner refinement replaces the three existing factor slots in dictionary order, retaining watcher code 20. A failed calibration appended factors after TAIL, producing compile errors; that version is superseded. `manifest.json` describes the four combinatorial expansions, not an executable scenario. The two single-sequence supplements deliberately have no combinatorial growth.

`spinner-campaign.properties` applies a two-iteration bias campaign to D002, maximizing the violations metric and retaining a 0.25 exploration floor. The normal Framework `run` command enters campaign mode when iterations exceeds one. The final campaign ran 12 candidates per iteration, saved a seed to `target/guif-dogfood/spinner-campaign-seed.json`, and reported no second-round exclusions. The focus comes from the selected failing area; no reduction or improvement in coverage is claimed.

Regenerate the four expansions/configuration with `python3 guif_dogfood/refine_assets.py`. To execute only refinements, use `python3 guif_dogfood/postprocess.py --rerun --phase refined`; to execute the campaign, use `--phase campaign`. These commands do not invoke another JUnit baseline.
