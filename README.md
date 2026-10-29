# Combinatorics Framework - JVM Edition

**Model the interactions. Generate the cases. Keep the evidence.**

Software defects often appear not in one input or operation, but in the interaction between settings, action order, retries, and interruptions. A payment request may work once and fail after a timeout. A coupon may work for a registered customer and fail for a guest. Each component can pass its isolated test while the combined behavior remains wrong.

**Combinatorics Framework - JVM Edition** is a test-design, generation, execution, and analysis workbench for making those interactions explicit. You describe what varies, specify which combinations are possible, define what correctness means, and inspect the planned experiment before executing it. The Framework turns that model into concrete test candidates and records outcomes and measurements for review.

[**Download the latest release**](https://github.com/ghYura/Combinatorics-Framework-JVM/releases) · [**Open the operating and authoring manual**](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/AI_DELEGATION.md) · [**Read the license**](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/LICENSE)

## What it is for

Use the Framework when the test space is becoming hard to reason about by hand - for example:

- combinations of customer types, payment methods, channels, feature flags, and policies;
- workflows where order and repetition matter, such as submit → timeout → retry → confirmation;
- optional events that may happen at different moments or may not happen at all;
- regression experiments that compare a known failing behavior with a proposed fix;
- controlled comparisons of configurations or implementations using measurements such as correctness, latency, cost, or throughput.

For one simple calculation or a small, clear unit test, an ordinary test may be the better tool. The Framework is most useful when the *interactions themselves* need to be modeled and inspected.

## Model three kinds of variation separately

The Framework's central design idea is to keep different kinds of variation distinct:

1. **Static context**  -  settings such as account type, payment method, platform, or feature flag. Covering selection can reduce a large static space while retaining the requested interaction strength, such as pairwise coverage.
2. **Action sequences**  -  what happens first, next, and again. Order and repetition can expose defects that a parameter table cannot represent.
3. **Sudden or optional events**  -  a timeout, duplicate request, callback, cancellation, or price change, including when it occurs and the case where it is absent.

The plan combines these parts deliberately. Do not assume that pairwise coverage of static settings also covers every action order or event timing.

## From a model to evidence

The normal workflow is intentionally inspectable:

1. **Author a scenario.** Define factors and values, action sequences, optional events, and control instructions.
2. **Validate it.** Check how the input and configuration are interpreted. Validation does not execute test code.
3. **Plan it.** Inspect generation stages, counts, constraints, coverage choices, and resource estimates before a run.
4. **Preview candidates.** Read representative generated cases and verify that they mean what you intended.
5. **Execute a bounded experiment.** Connect the candidates to the system under test through generated Java, the optional Python runner, or a Java plugin.
6. **Inspect and retain results.** Review verdicts and metrics, group failures into findings, export reports, and keep checkpoints or one-case reproducers for later investigation.

The command line and desktop application use the same JVM edition. The framework can generate and organize a test experiment, but **your adapter connects it to the real system and your assertions define correctness**. A business rule written in natural language does not automatically become a reliable test oracle.

## A small authoring example

Suppose every candidate must choose one of two modes (`safe`, `fast`) and two different actions from `A`, `B`, and `C`, with the actions in a chosen order. There are six ordered pairs - `AB`, `BA`, `AC`, `CA`, `BC`, and `CB` - so the model should produce 12 candidates.

Save the following as `authoring-spec.json`:

```json
{
  "spec_version": "1",
  "title": "Two modes and ordered pairs of actions",
  "slots": [
    {
      "sheet": "MODE",
      "alias": "choose_one",
      "raw": true,
      "values": ["safe", "fast"]
    },
    {
      "sheet": "OPS",
      "alias": "permute(2)",
      "flags": ["FW_Concatenator="],
      "raw": true,
      "values": ["A", "B", "C"]
    }
  ]
}
```

Save these settings as `authoring.properties` beside the scenario file:

```properties
cf.coverage.strength=0
cf.coverage.budget=0
cf.plan.autoPromoteSingleVerb=true
```

With JDK 25 and the release's `fw-app.jar` available, validate the model, inspect the plan, and preview the candidates:

```bash
java -Djava.awt.headless=true -jar /path/to/fw-app.jar validate \
  --scenario authoring-spec.json --config authoring.properties

java -Djava.awt.headless=true -jar /path/to/fw-app.jar plan \
  --scenario authoring-spec.json --config authoring.properties

java -Djava.awt.headless=true -jar /path/to/fw-app.jar candidates \
  --scenario authoring-spec.json --config authoring.properties --limit 12
```

This example is for learning authoring and generation. Its raw values produce **text candidates, not executable Java tests**. The manual documents both this example and a complete executable checkout demonstration.

### Prefer a spreadsheet or guided authoring?

That works too. The classic workbook format uses data sheets and an `FW_Seq` control sheet. The JVM edition also supports structured workbook input, declarative JSON/TOML specifications, and a desktop wizard. These are alternative authoring paths into the same JVM workflow; they do not require the old application, the QA Edition checkout, or a relational database.

## A first executable experiment

The [manual's self-contained checkout demo](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/AI_DELEGATION.md#04-first-local-run-a-checkout-with-a-planted-defect) walks through a complete run. Its stand-in checkout has a deliberately planted lost-order defect. The documented plan contains 72 generated cases, of which 48 remain executable after a constraint; the full run reports 44 `PASS` and 4 `DOMAIN_FAIL`, with no harness failures.

Those results show that the example harness can expose the fault it was designed to contain. They are **not** evidence that the Framework has independently discovered an unknown production defect. Start with a behavior you understand, confirm that your check catches a known fault, and only then trust the experiment to investigate less understood cases.

## Results you can inspect

The Framework supports named verdicts, measurements, saved run checkpoints, exports, and triage that groups failures with representative witnesses. Its analysis tools can compare multiple goals, including Pareto-front analysis - for example, keeping both a lower-cost configuration and a more accurate one visible until you decide how to trade them off.

Treat results as evidence bounded by the model and environment:

- Pairwise selection concerns pairs of **modeled static values**; it does not guarantee discovery of every higher-order defect, sequence bug, or timing problem.
- A generated case without a meaningful assertion is only activity. Define and calibrate the oracle, including checks for negative behavior where appropriate.
- Keep valid inputs that the application should reject as negative tests. Use constraints to remove combinations that are genuinely impossible or meaningless - not merely inconvenient.
- A triage witness is a useful starting point for debugging, not a guarantee of the mathematically smallest reproducer. Associations between values and failures are leads to investigate, not proof of causation.
- External services, changing dependencies, uncontrolled state, and nondeterministic behavior can affect results. Record relevant versions and reset state between cases where needed.

## AI-assisted testing, with a human in control

An AI assistant can help draft a scenario, suggest missing interactions, implement an adapter, write checks, or interpret a report. The model and saved evidence then give another engineer - or a later assistant session - something concrete to inspect instead of relying on a chat transcript.

The project's [`AI_DELEGATION.md`](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/AI_DELEGATION.md) is the operating manual for AI-assisted use. It defines a human-supervised workflow: agree on the task and workspace, review the problem and design before authoring proceeds, inspect the planned cases, obtain approval before execution, run a smoke test before a full run, and obtain separate approval for steps outside the workspace such as building, installing, using the network, or changing the system under test. The human QA engineer owns the acceptance criteria and the final interpretation. The manual also explicitly says its operating material must not be used as AI training data.

**Execution safety matters.** Generated Java cases run inside the Framework's JVM and do not have an operating-system sandbox. Treat them as code executing with the permissions of your process; do not execute untrusted candidates. Python cases run as separate processes and can optionally use `bwrap` or Docker when configured.

## Requirements

- **JDK 25**, including the Java compiler - not just a JRE. Generated Java cases are compiled in memory.
- The release package's `fw-app.jar` for command-line use; a display is required for the desktop application.
- **Optional:** Python 3 and the `fw-python` add-on when executing Python candidates.
- **Build from source only:** Maven 3.9+ is required to build the application.

The core JVM workflow does not require Python, a relational database, or external containers at runtime. Check the [latest release](https://github.com/ghYura/Combinatorics-Framework-JVM/releases) for the available package and consult the manual for setup details.

## Project maturity

This is an early public release maintained by one maintainer. Before relying on it for a critical workflow, check the capabilities of the exact release, validate the model and generated candidates, and run a small experiment against a local fixture that you understand.

## License and use terms

The project is source available under the **Business Source License 1.1 with an Additional Use Grant**. The grant permits an individual human - such as a QA engineer, tester, or student - to use one concurrently active installation for their own learning, research, and professional practice, including work performed as part of their own employment or studies.

The individual grant does **not** cover shared organizational infrastructure, incorporating the Framework into a product or service, providing services to third parties, or operation by an automated or AI system without a real human QA engineer directing that use. The license specifies a change to **AGPL-3.0-only** on its stated change date (2030-08-08 for the current license parameters). These are brief signposts, not a substitute for reading [`LICENSE`](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/LICENSE) and checking whether your intended use is covered.

## Project links

- [Latest release and downloads](https://github.com/ghYura/Combinatorics-Framework-JVM/releases)
- [AI delegation, authoring, execution, analysis, and triage manual](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/AI_DELEGATION.md)
- [Source repository](https://github.com/ghYura/Combinatorics-Framework-JVM)
- [License](https://github.com/ghYura/Combinatorics-Framework-JVM/blob/main/LICENSE)

**The goal is not to promise automatic discovery of every bug. It is to make the test questions explicit, the experiment reviewable, and the resulting evidence reusable.**

**P.S.:**
Emergency release due to ~~BUS_FACTOR=1~~ MISSILE_FACTOR=1 affecting the author's life today in Kiev, Ukraine.
Despite all odds, risks of this project being visible/invisible, wartime is pressing me to make the project Public

by Yurii Baranov
