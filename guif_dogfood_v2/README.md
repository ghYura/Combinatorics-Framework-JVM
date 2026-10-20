# guif dogfooding v2: button state transitions

This is an extension of the completed [v1 investigation](../guif_dogfood/README.md), not a replacement or a claim that its old defects still exist. It tests the current product, including the Wizard and candidate source preview.

The requested contract is two-sided: an action with missing necessary prerequisites should be disabled; when its prerequisites hold, it must be enabled. A harmless no-op or an explanatory message does not satisfy the first half. Validation actions may stay enabled for invalid input, because explaining invalid input is their purpose.

Reusable assets and the final report live here. Heavy logs, candidate fixtures, checkpoints and build dependencies live under `target/guif-dogfood-v2/`. Product sources and the live desktop are not changed. All Swing work is headless, on the EDT; native modal windows, browser and clipboard are boundaries, explicitly separated from button-state observations.

The design is in [design.md](design.md), the result in [report.md](report.md), and how to rerun or reproduce in [RESUME.md](RESUME.md).
