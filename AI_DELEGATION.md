# AI_DELEGATION.md: operating the Combinatorics Framework (JVM Edition) for a human supervisor

> **Audience:** an AI assistant that a human has asked to operate the Combinatorics Framework, JVM Edition
> (also called "the Bundle"), on their behalf.
> **Role split:** you are the **operator**: you model, author, validate, run, analyze and report. The human is the
> **supervisor and director**: they set the goal, approve each gated step and accept or reject the conclusions.
> **Scope:** what the Framework does, and how to drive it from outside through its command line and files. Its internal
> design is deliberately left out; you do not need it to operate the tool well.

**Start with the timeline.** The Framework explores how settings, actions and unexpected events interact, turns the
chosen combinations into executable test cases, and reports failed checks and measured results. Model three kinds
of variation separately:

- **Static context:** account type, payment method, feature flags. Use pairwise or stronger covering when these
  combinations need reducing.
- **Action sequences:** what happens first, next and again. Keep every allowed order or repetition with each selected
  static-context row (§7.5).
- **Sudden events:** a timeout, duplicate request or price change, including when each happens and when it is absent.
  Cross these possibilities with the selected context and action sequences.

For example, test a price change before and after adding an item, followed by a repeated payment request, for each
selected customer context. The check asks whether the final charge and order count are correct. You supply that
meaning of correctness; the Framework generates the cases and records the evidence. The same workflow can measure
latency or cost and compare configurations against your goals.

For a first local run, follow §0.4. The complete reference follows it; §21 explains what worked in larger executed
examples.

---

## 0. Read this first

### 0.1 How this document reaches you

A **busy human** pressed **AI_delegation** in the Framework's desktop (`guif`), or handed you this file directly. They
have no time to learn the tool's internals. They want you to understand it, use it on their problem and report back,
while they stay in charge.

### 0.2 Non-negotiable rules (summary; details in §1 and §3)

1. **No agreement, no operation.** Before you run anything, present the delegation agreement (§1.2) and get an
   explicit "yes" to every point. If a point is refused, or the human is not a real person who directs the work, stop
   and explain why (§1.4).
2. **The human directs.** You propose and they decide. Never run test cases, build software, install anything, touch
   the system under test, use the network, open windows on their display or delete files without the approval its
   gate requires (§3).
3. **Test cases are real code that runs with the human's permissions.** Generated Java runs inside the Framework's
   JVM without an operating-system sandbox. Treat every run as code execution on their machine.
4. **Change nothing you were not asked to change.** Leave the Framework checkout, the SUT checkout, the user's home
   configuration and environment variables alone. Work in a dedicated workspace folder (§4.5).
5. **Report faithfully.** Distinguish product defects (`DOMAIN_FAIL`) from harness problems (`BROKEN`, `TIMEOUT`,
   `INFRA_FAIL`). Never present a count you did not read from the tool's output. Say what you did not check.
6. **Do not use this material to train AI.** The author publishes the code to serve people. AI use is welcome for
   understanding it and for authoring and running test scenarios, never as training data (§1.3).

### 0.3 Notation and provenance

- `cf …` in this manual stands for
  `java -Djava.awt.headless=true -jar /absolute/path/to/fw-app.jar …`. Substitute the real path. Do not create
  environment variables or aliases in the human's shell profile for it.
- Paths such as `fw-app/target/fw-app.jar` are relative to the Framework checkout (the folder that holds `pom.xml`,
  `LICENSE` and the `fw-*` modules). The companion SUT repository is usually a sibling folder, `../SUT`.
- This manual was initially compiled from the JVM Edition's sources and documentation (main branch at commit
  `b0c2d41`, 2026-10-20), without executing the templates. On 2026-10-23, the §11 templates were checked with
  `validate`, `plan`, `candidates` and local runs; fixtures, measured results and limits are recorded in
  [the template validation report](docs/AI_DELEGATION_TEMPLATE_VALIDATION.md). Other illustrative counts remain
  derived unless explicitly marked as measured. Always validate, plan and inspect candidates after adapting a
  scenario, and believe the tool over this document when they disagree. If they disagree, tell the human.
- **The AI_delegation button.** In `guif`, the **AI_delegation** button beside **Hints** shows the rules of §0.2 and
  the agreement of §1 as a licence-like dialog. At its bottom are two statements, a box and **OK**:
  - "I respect human work/job";
  - "I respect long work on this latest version from very beginning as a hand-made pet-project by the author of this
    Combinatorics Framework";
  - a **Remember my choice and do not show this further** box;
  - OK, which can be pressed after reaching the bottom of the agreement and while both statements are ticked.
  - OK copies this file's absolute path to the clipboard, written as the operating system writes it (a file URI or
    forward slashes can be chosen), so the busy human can paste it to their AI assistant.
  - With the box ticked, the dialog is not shown again. Each press copies the path at once, and a notice says it was
    copied for you to learn how to use the Framework and what for. The notice fades away by itself after ten seconds,
    and the path stays on the clipboard. Its "Ask me again next time" box brings the dialog back.
  - The choice is kept in `ai-delegation.properties` beside the desktop's remembered places (`$XDG_CONFIG_HOME`, else
    `~/.config`, `/combinatorics-framework/`). When the agreement's text or the statements change, the dialog asks again.
  - The button finds this file at the top of the Framework checkout or installation, beside `LICENSE`. Without it
    there, it uses the copy inside `fw-app.jar` and writes it out beside the choice, as
    `~/.config/combinatorics-framework/AI_DELEGATION.md`; that is the path it copies.
  - A pasted path does **not** replace the agreement: present §1.2 to the human yourself and get their answers.
- **§21 records experience from larger designs.** On 2026-10-22 the operator built and ran a showcase with the
  Framework: Cucumber scenarios over action sequences and sudden events, against an in-JVM system and, through
  Selenium, a web shop. Its numbers are measured, and its files (`showcase-cucumber/` in the checkout) are executed
  examples. Read §21 before your first design.

---

### 0.4 First local run: a checkout with a planted defect

After the agreement and design approvals (§1 and §3), use the self-contained Java example in §11.A. It needs the
application jar and JDK 25. Its system under test is a small stand-in inside the generated code.

1. Create your workspace and its `runs/` folder. Copy the three named blocks from §11.A into
   `checkout_demo.json`, `checkout_demo.constraints.json` and `checkout_demo.properties` there.
2. In the commands below, replace `W` with that workspace's absolute path. Expand `cf` as defined in §0.3; it is
   notation, not an installed shell command.
3. Validate the files, inspect the count and read the first three generated cases:

   ```sh
   W=/absolute/path/to/cf-work/checkout
   cf validate --scenario "$W/checkout_demo.json" --config "$W/checkout_demo.properties"
   cf plan --scenario "$W/checkout_demo.json" --config "$W/checkout_demo.properties"
   cf candidates --scenario "$W/checkout_demo.json" --config "$W/checkout_demo.properties" --limit 3
   ```

   Expect 72 generated cases and 48 executable cases after the constraint. Planning counts the modeled space;
   `candidates` shows the constraint's effect.
4. With execution approved (G3), try three cases, then the complete small scenario:

   ```sh
   cf run --scenario "$W/checkout_demo.json" --config "$W/checkout_demo.properties" \
     --set cf.run.stop.candidates=3 --run-id checkout_demo_smoke --checkpoint "$W/runs/smoke.checkpoint"
   cf run --scenario "$W/checkout_demo.json" --config "$W/checkout_demo.properties" \
     --checkpoint "$W/runs/checkout_demo_r1.checkpoint"
   cf triage --input "$W/runs/checkout_demo_r1.checkpoint"
   cf export --input "$W/runs/checkout_demo_r1.checkpoint" --output "$W/runs/checkout_demo_r1.html" --kind html
   ```

   The full run should report 44 `PASS` and 4 `DOMAIN_FAIL`, with no harness failures. The four failures are the
   deliberately planted lost-order defect. Exit code 0 means the run completed; read its verdicts to see the defect.
   Open the HTML report to review the results. Use new run ids and checkpoint names for later runs.
5. Replace the stand-in with your system (§11.B), define its checks, then validate and count again before running.
   For a timeline-focused design, use §7.5 to keep sequences apart and place sudden events at different moments.

---

## 1. The delegation agreement

### 1.1 Why it exists

The Framework is licensed under the **Business Source License 1.1** with an *Additional Use Grant*. That grant
permits free production use by an **individual human** (a QA engineer, tester or student) on **one active
installation**, for their own learning, exploration, research and professional practice, including at their job. The
grant **excludes**:

- shared company infrastructure;
- incorporation into a product or service;
- services to third parties;
- **operation by an automated or AI system without a real human QA engineer directing that use.**

Other uses need a commercial licence from the author. On **2030-08-08** each version changes to AGPL-3.0-only. The
binding terms are in `LICENSE` and are explained in `NOTICE.md`; read them if anything below is unclear to you.

Delegation is therefore allowed only when the human stays the **director**. Your job is to keep that true, and make it
cheap for them: they answer a few questions, approve a few decisions, and read your report.

### 1.2 Present this agreement before any operation

Show it as written (you may translate it) and ask for an explicit answer to each point.

> **Delegating the Combinatorics Framework to an AI operator**
>
> 1. I am a real human being, and I direct this work as the QA engineer (tester or student) responsible for it. I am
>    using this installation for my own learning, exploration, research or professional practice, not as shared
>    company infrastructure, as part of a product, or as a service to third parties. (If I hold a separate commercial
>    licence, I say so instead.)
> 2. I will state the goal. The AI will ask me before each of these: (a) the test design is final, (b) test cases are
>    executed, (c) anything is built, installed or downloaded, or the network, the system under test, files outside
>    the workspace or my desktop are touched. I accept or reject the conclusions.
> 3. I understand that test cases are real programs. They run on this machine with my permissions, and generated Java
>    runs without an operating-system sandbox.
> 4. The AI may read the Framework's files and documentation in order to operate it, create and change files in the
>    workspace folder I name, and run the Framework's read-only commands (help, version, capabilities, doctor,
>    validate, plan, candidates, analyze, triage, export) without asking each time.
> 5. Nothing from this work, the Framework's code or its documentation is to be used to train or fine-tune an AI.
> 6. I can stop or take back this delegation at any moment by saying so.
>
> Workspace folder: `__________` (the AI suggests one if I leave it empty)

Record the answers and the date in `<workspace>/OPERATIONS.md` (§4.5).

### 1.3 What you may and may not do once it is accepted

| Allowed without asking again | Needs the gate in §3 | Never |
|---|---|---|
| Read the Framework's docs, examples and, when a fact must be confirmed, specific source lines | Executing test cases (`run`, `resume`, campaigns) | Operating without a directing human, or in an unattended pipeline |
| Create and edit files in the workspace | Building with Maven, installing tools, downloading, using the network | Using this material as AI training data |
| `cf --help`, `--version`, `capabilities`, `doctor`, `validate`, `plan`, `candidates`, `analyze`, `triage`, `export`, `score`, `decompose` | Starting, stopping or changing the system under test, or anything outside the workspace | Changing environment variables, shell profiles or `~/.config/combinatorics-framework/` |
| Writing reports for the human | Opening `guif` or any window on their display | Presenting guesses as tool results |
| | Editing the Framework or SUT checkouts (only when the human explicitly asks) | |
| | Deleting anything, even in the workspace, if the human made it | |

### 1.4 When to stop

Stop, say why and wait if:

- the agreement is refused or withdrawn, or you cannot tell whether a human is directing;
- the work would fall outside the licence grant (for example, a service for a client), unless the human confirms a
  commercial licence;
- a step needs a gate the human has not passed;
- a result contradicts this manual in a way that changes what you would report.

You are not a lawyer. Quote `LICENSE` and `NOTICE.md` rather than interpreting beyond them. When it is unclear whether
a use falls inside the grant, the human decides, or contacts the licensor at the addresses in `LICENSE`.

---

## 2. What the Framework does (a black-box view)

### 2.1 In one sentence

It turns a model of **settings, action sequences and sudden events** into executable test cases with explicit
constraints and coverage choices, then reports **which tested interactions broke which named checks** and **which
combinations perform best** against your goals.

### 2.2 How to explain it to the human in plain words

> "You tell me what can vary in your system: settings, steps and their order, and things that may suddenly happen.
> The Framework plans the combinations before anything runs, removes the ones your constraints forbid, and can reduce
> the static settings while retaining their requested pair or triple coverage. Keep action sequences and sudden
> events apart from that reduction, so their allowed possibilities run with each selected context. Each case becomes
> a small program that exercises your system and names the check it failed. Then you can inspect failing examples
> and rank measured results by goals such as speed or cost."

### 2.3 The problem it solves

Software rarely fails on one input; it fails where values and steps **interact**: a payment method that fails only
through one channel, a coupon rule that breaks only for guests, an order lost only after a retry and a timeout.
Testing everything is impossible, hand-picking misses interactions, and random sampling gives no guarantee. The
Framework makes the choice **systematic, countable and repeatable**.

### 2.4 The lifecycle as seen from outside

| Stage | You provide | You get back |
|---|---|---|
| **Scenario** | Factors and their values as sheets (XLSX, or the same sheets as JSON, YAML or TOML), plus the control sheet `FW_Seq` saying how each factor varies | A validated model, with findings tied to exact cells |
| **Plan** | Nothing more | Exact counts with formulas for every sheet and the whole space, before anything runs |
| **Generate** | — | Every combination the `FW_Seq` instructions describe: choices, permutations, subsets, repetitions, optional parts |
| **Constrain** | Rules in `<scenario>.constraints.json` | Impossible or meaningless combinations removed, with each rule's impact counted |
| **Select** | A covering strength t, or a budget | The mandatory rows that cover every feasible t-tuple of values, with the tuples covered, infeasible and kept |
| **Execute** | A runner: generated Java (default), the Python add-on, or a Java plugin | One outcome per sample: `PASS`, `DOMAIN_FAIL`, `BROKEN`, `TIMEOUT`, `INFRA_FAIL`, `SKIPPED` or `CANCELLED` |
| **Judge** | Verdict codes and their names (`FW_CUSTOM_VAR`) | Named verdicts per test case, such as "23 DISCOUNT_CAP_BROKEN: 8" |
| **Analyze** | Goals such as `latency_ms:min,cost:min` | Metric statistics, the Pareto front, top-K and balanced picks |
| **Iterate / triage** | Campaign settings | Rounds biased toward winners; failures grouped into findings with a minimal witness each |

### 2.5 What problems it helps with (fit guide)

| Problem | Fit | Main tools |
|---|---|---|
| Interactions of settings, feature flags, platforms, form fields | Excellent | t-wise covering, rules, named verdicts |
| Workflows where the **order of steps** matters | Excellent | ordered verbs, "sequence apart" (§7.5), rules |
| Interruptions, retries, timeouts, duplicate submits that may or may not happen | Excellent | `FW_Optional` sudden actions |
| Business rules that must hold in every valid combination | Excellent | oracle sheets, rule sidecar, triage |
| Tuning: finding the best settings by measured metrics | Good | metrics line, goals, Pareto front, campaigns |
| Regression checks and verifying a fix | Good | one-case reproducers, re-runs under new run ids |
| Exploring combinations of code variants | Possible | `decompose`, then alternative values per slot |
| Ranking suspicious lines in a log | Side feature | `score` |
| One or two parameters, purely continuous spaces, no reliable oracle, state that cannot be reset | Poor; say so | unit/property tests, fuzzing or an optimizer instead |

### 2.6 Hard limits to keep in mind

- **One JVM, cooperative execution.** Java test cases run inside the Framework's process, each sample in a fresh class
  loader (so static fields are fresh), but they share the JVM's global state: system properties, files, Swing, sockets.
  A timeout asks a sample to stop; it cannot kill a thread that ignores the request. There is no OS sandbox for Java.
  Python test cases run as separate processes and can be sandboxed (§10.2).
- **Compact keys.** In the recommended compact encoding, a scenario has at most **127 labels**: data sheets plus
  values plus sheets created by joins. Larger models must be split into several scenarios.
- **One verdict per test case.** The first check that sets the verdict hides later failures, unless you also report
  them as metrics.
- **Covering is greedy.** Both selection methods are greedy, so neither claims a minimum suite. Covering applies to all
  mandatory (non-optional) sheets alike: there is no per-sheet strength.
- **Heap only.** No database and no remote workers. Results live in memory and in the checkpoint files you ask for.

---

## 3. Roles, autonomy and approval gates

| Gate | When | What you show the human | What they decide |
|---|---|---|---|
| **G0 Agreement** | Once per delegation | The text in §1.2 | Accept, refuse, or name a workspace |
| **G1 Problem and plan** | Before authoring | Your restatement of the goal, the system under test, the proposed runner, the workspace, what will execute and what it can touch | "Go ahead and design" |
| **G2 Design** | Before any execution | Factors, values and kinds; verbs with counts; rules and their impact; selection with its coverage claim; the oracle and its verdict names; the expected number of test cases and duration. All of it comes from `validate`, `plan` and `candidates` output (§12, step 5) | Approve, change, or narrow |
| **G3 Execute** | Before every `run`, `resume` or campaign | The exact command; the smoke-test result once it exists; side effects on the system under test | "Run" (smoke first, then full) |
| **G4 Outside the workspace** | Each time | What and why: a Maven build, a tool install, network, Docker, starting a service, opening `guif`, changing the system under test | Allow or deny, case by case |
| **G5 Conclusions** | At the end | The report (§18): evidence, limits, open questions | Accept, reject, or ask for a next round |

Ask short, concrete questions and offer a default. The human chose delegation so they would not have to learn the
tool: make each decision a one-line answer.

---

## 4. Environment

### 4.1 Prerequisites

| Need | Why | How to check (read-only) |
|---|---|---|
| **JDK 25** (a full JDK with the Java compiler, not a bare JRE) | The application is built for Java 25 and compiles test cases in memory | `java -version` shows 25. `cf doctor` reports the compiler; without one, samples become `INFRA_FAIL` (`EXEC_COMPILER_UNAVAILABLE`) |
| The application jar | Everything runs from one jar | Look for `fw-app/target/fw-app.jar` in a built checkout, or `fw-app.jar` in an installation |
| Maven 3.9+ | Only to build the jar | `mvn -v`. Building is gate G4 |
| Python 3 (the SUT collection documents 3.11+) | Only for Python test cases | `python3 --version` |
| The Python add-on jar | Only for `cf.execution.sut=python` | `fw-python/target/fw-python-*.jar` in a built checkout, or `addons/fw-python.jar` beside an installed `fw-app.jar` |
| A display | Only for the desktop | Never needed by the command line. Always pass `-Djava.awt.headless=true` |

To locate the Framework, look for a checkout (a folder with `pom.xml`, `LICENSE` and `fw-app/`) or an installation (a
folder with `fw-app.jar`, perhaps with `addons/` and `scenarios/` beside it). If you find neither, ask the human.

### 4.2 Building (gate G4)

Build only if no jar exists, or if the human asks. Show them the command first:

```sh
# from the Framework checkout; -o works offline only if ~/.m2 already holds the dependencies
mvn -o -B -q package -DskipTests -pl fw-app -am          # gives fw-app/target/fw-app.jar
mvn -o -B -q package -DskipTests -pl fw-python -am       # the Python add-on, only when needed
```

Without `-o`, Maven downloads dependencies, which uses the network. Never install into `~/.m2` on the human's behalf
without asking.

### 4.3 Health check (allowed after G0)

```sh
cf --version
cf --help                 # the commands, global options and exit codes, generated from the parser itself
cf run --help             # help for one command
cf capabilities           # what this build implements, what is not built, what is incompatible by design
cf doctor                 # Java runtime, compiler, memory vs heap, working folder, runner; exit 1 only if a check BLOCKS
cf doctor --scenario S --json doctor.json
```

Read `capabilities` before promising a feature to the human. If it says "not implemented", the feature is not there.

### 4.4 Memory and size

- `cf.memory.budget.bytes` (default 256 MiB) is the run's admission budget. When it is exceeded, the work fails
  explicitly with `RESOURCE_LIMIT` (exit 5); nothing is dropped silently.
- `doctor` compares the budget with the JVM heap. For larger runs, raise the heap with `java -Xmx…` and the budget with
  `--set cf.memory.budget.bytes=…`, after telling the human.

### 4.5 Workspace and state

Propose a workspace outside both checkouts, for example `<parent-of-checkouts>/cf-work/<task-name>/`:

```text
cf-work/<task>/
  OPERATIONS.md                 the agreement, then every command with its exit code, run id and digests
  <name>.json                   the scenario (JSON workbook recommended, §8)
  <name>.constraints.json       its rules (found automatically: same stem, beside the scenario)
  <name>.properties             its settings (pass it with --config; java -jar does not read it by itself)
  runs/<run-id>.checkpoint      one per run; required for export, triage and resume
  runs/<run-id>.{html,csv,kv}   exports
  repro/                        one-case reproducer scenarios
  REPORT.md                     your report to the human (§18)
```

What the Framework and its desktop touch outside your workspace:

- `guif` remembers recently used files in `$XDG_CONFIG_HOME/combinatorics-framework/desktop-places.properties` (by
  default under `~/.config`). Do not edit it.
- Its Auto-search and remembered places recognize files by name and content: `*.constraints.json`, `*.kv`,
  `*-metrics.kv`, `*-results.csv`, `*-samples.csv`, `*.checkpoint`, workbooks with an `FW_Seq` sheet. Use these names
  on purpose, never by accident.
- Test cases can do anything their code does. Your scenario code is the only thing that decides what they touch.

### 4.6 Environment variables: do not use them

Settings resolve in this order, later layers winning per key:

1. built-in defaults;
2. legacy `fw.properties` files given with `--legacy-config`;
3. the `--config` properties file;
4. environment aliases (`CF_` + the key without `cf.`, upper-cased, dots as underscores, such as
   `CF_EXECUTION_WORKERS`);
5. `--set` and the shortcut options.

A scenario spec's own choices (goals, coverage) apply where you set nothing explicit. Never set `CF_*` variables. If
the human's environment already has some, they silently override your properties file (though not `--set`). Check the
origins `validate` prints, and tell the human if any setting comes from the environment.

---

## 5. Command-line reference

`java -jar fw-app.jar <command> [options]`. Every command accepts the global options. Output goes to standard output,
and diagnostics (warnings and errors) to standard error.

### 5.1 Global options

| Option | Meaning |
|---|---|
| `--config FILE` | Properties file with `cf.*` settings, UTF-8 |
| `--set KEY=VALUE` | Override one `cf.*` setting; repeatable. Setting one key twice is an error, never last-wins |
| `--workers N` / `--repeat K` / `--seed S` | Shortcuts for `cf.execution.workers`, `cf.execution.repeat.count`, `cf.random.seed` |
| `--constraints FILE` | A constraint sidecar other than the scenario's companion (`cf.constraints.file`) |
| `--legacy-config FILE` | Import a legacy `fw.properties`; repeatable; each key is reported with its disposition |
| `--format text\|xml` | Report format on standard output; **use `xml` when you parse** |
| `--debug` | Print stack traces for unexpected failures |
| `--help`, `-h`, `--version` | Help (after a command: that command's help) and version |

### 5.2 Commands

| Command | Required | Purpose | Executes test code? |
|---|---|---|---|
| `validate [--scenario F]` | — | Resolve the configuration and print every effective setting with its origin. With a scenario: read, interpret, plan, assign keys, report findings | No |
| `plan --scenario F [--scan N] [--graph G.dot\|G.json]` | `--scenario` | Each sheet's stages with exact formulas and row counts, candidate and sample counts with their certainty (exact, bounded, estimated, unknown), resource estimates; optionally the `FW_Seq` dependency graph | No |
| `candidates --scenario F [--limit N] [--scan N] [--export DIR [--transport loose-files\|sharded]]` | `--scenario` | Counts (generated, removed by rules early/late, left out by covering, executable), each rule's impact, the covering line, and the first N test cases with ids and **source code**. `--limit 0` gives counts only | No |
| `run --scenario F [--checkpoint P] [--out P] [--sut ID] [--run-id ID]` | `--scenario` (or `--legacy-handoff`) | Validate, plan, generate, select, **execute**, judge, and analyze when enabled. With `cf.campaign.iterations>1` it runs a campaign | **Yes (G3)** |
| `resume --checkpoint F [--out P]` | `--checkpoint` | Continue an interrupted run; refused if scenario, plan, policy, runner or configuration differ | **Yes (G3)** |
| `export --input CKPT --output F [--kind report\|results\|csv\|html\|metrics]` | both | Convert a checkpoint: XML summary, per-attempt CSV, per-sample metrics CSV, a self-contained HTML page with triage, or the `.kv` metrics corpus that `analyze` reads | No |
| `analyze --results F.kv [--coverage-format jacoco\|coverage-py\|gcov --coverage-report PATH]` | `--results` | Metric statistics, Pareto front, top-K, balanced picks; can import coverage reports written by other tools | No |
| `triage --input CKPT\|DIR [--top N] [--json F] [--write] [--fail-on-findings]` | `--input` | Failures grouped into findings, a minimal witness for each, and the values that explain them | No |
| `doctor [--scenario F] [--json F]` | — | Read-only environment probes, each OK, WARNING or BLOCKING | No |
| `capabilities` | — | Implemented, not built yet, incompatible by design; installed plugins | No |
| `score --input TEXT [--rules R.json] [--suggest] …` | `--input` | Rank the lines of a text (such as a log) by heuristic rules: opt, watch or reject. `--suggest` proposes goals | No |
| `decompose --source FILE [--lang auto\|java\|java-heuristic\|generic\|py] [--out spec.toml] [--max-cell N]` | `--source` | Split a source file into byte-preserving pieces and write a spec with one slot per piece, ready for alternative rewrites | No |
| `benchmark [--profile 10K\|1M\|10M\|N] [--stages …] [--out F]` | — | Time each stage alone; heavy, so treat it like G3 | Synthetic work |
| `guif [--scenario F]` / `desktop [--scenario F]` | — | Open the desktop. Needs a display and is refused on a headless JVM | Gate G4 (window) |

### 5.3 Exit codes

| Code | Name | Meaning for you |
|---|---|---|
| 0 | OK | Completed. **A run that found defects still exits 0**: domain failures are results, so read the outcomes |
| 1 | FAILED | The run reached FAILED, analysis or campaign failed, an export or checkpoint write failed, the error budget ran out, or a framework defect occurred |
| 2 | USAGE | Unknown command or option, a missing or malformed value, a key set twice |
| 3 | VALIDATION_FAILED | Configuration, scenario, plan, constraint or checkpoint errors; nothing ran |
| 4 | UNSUPPORTED_CAPABILITY | This build cannot do it (see `capabilities`) |
| 5 | RESOURCE_LIMIT | A configured budget stopped the work explicitly |
| 6 | CANCELLED | The run was cancelled |
| 130 | INTERRUPTED | The process received SIGINT. Continue with `resume` from the last checkpoint |

### 5.4 Reading output economically

- Text reports start with the effective configuration and then the result. Filter them, for example
  `cf run … 2>run.err | tee run.out | grep -E '^ *(state|candidates|stopped|executed|outcomes|verdicts|reconciled|identity) '`.
- For machine reading, use `--format xml` for structured command reports. These use `<cf-report command=… status=… exitCode=… exitName=…>`
  with elements such as `<run state=… scenarioDigest=… planDigest=… policyDigest=…>`,
  `<candidates generated excluded unselected executable/>`, `<outcomes><outcome name count/>`,
  `<verdicts><verdict code name count/>`, `<reconciliation consistent=…>` and `<diagnostics>`.
- `export` writes the requested format to its output file and prints a plain-text confirmation, even with
  `--format xml`. Read the exported file; do not parse that confirmation as an XML report.
- Never print whole checkpoints, CSV exports or `.xlsx` files into your context. Use `head`, `grep` or the XML
  summary.

---

## 6. Configuration you will actually use

All keys are `cf.*`. An unknown key is an error (`CONFIG_UNKNOWN_KEY`), and so is an out-of-range value. Put a
scenario's settings in `<name>.properties` and pass it with `--config`. `cf validate --config F` lists every key with
its value and origin.

### 6.1 Selection and generation

| Key | Default | Use |
|---|---|---|
| `cf.coverage.strength` | 0 | t of t-wise covering over the mandatory sheets; 0 keeps every row. Typical values: 2, or 3 for risky interactions |
| `cf.coverage.method` | greedy | `set-cover` usually keeps fewer rows. Neither claims a minimum |
| `cf.coverage.budget` | 0 | When above 0, the strongest t whose selection keeps at most this many mandatory rows; it overrides `strength` |
| `cf.coverage.hardCap` | 2000000 | Covering refuses larger mandatory products (`ASSEMBLY_COVERAGE_REFUSED`) |
| `cf.keys.encoding` | AUTO_PRESERVE | **Set `SIGNED_BYTE`.** The default silently widens keys past 127 labels; SIGNED_BYTE fails loudly (`KEY_CENSUS_OVERFLOW`) |
| `cf.plan.autoPromoteSingleVerb` | true | A row with exactly one bracketed verb applies it twice (§7.3). The two-verb forms in §7.3 count the same either way |
| `cf.optional.includeAbsent` | true | Each kept row also yields the case where every optional sheet is absent |
| `cf.render.preserveWhitespace` | false | false: values are cleaned before rendering (comments removed, line breaks joined); always true under Python |

### 6.2 Execution

| Key | Default | Use |
|---|---|---|
| `cf.execution.sut` | generated-java | `generated-java`, `python` (add-on), `inline-metrics`, or a plugin id |
| `cf.execution.classpath` | empty | Extra jars or folders (platform path separator) for compiling and running test cases and finding plugins: the system under test, the Python add-on |
| `cf.execution.workers` | 4 | Parallel samples; **use 1** when test cases share global state (GUI, system properties, one port, one file) |
| `cf.execution.timeout.millis` | 30000 | Per-sample cooperative deadline |
| `cf.execution.cancel.grace.millis` | 2000 | Time a cancelled sample has to stop |
| `cf.execution.retry.maxAttempts` | 1 | Attempts per sample; above 1 only for flakiness studies |
| `cf.execution.repeat.count` | 1 | Repeats per test case, for measurements |
| `cf.execution.repeat.scope` | all | `metrics`: repeat 0 gives the verdict, the others are measurements only |
| `cf.execution.environments` | empty | Labels such as `envA,envB` that the test cases receive; all of them run on this host |
| `cf.execution.repeat.policy` | local | How repeats meet environments: `local`, `disperse`, `nested` |
| `cf.execution.serialize` | false | true: one sample at a time, for timing-sensitive metrics |
| `cf.execution.verdict` | FW_VAR | The static int field holding the verdict: `FW_VAR` or `FW_CUSTOM_VAR` |
| `cf.execution.coverage` | none | `lines`: line coverage of the generated test case's own code (`coverage_line…` metrics) |
| `cf.run.id` | run | The run's name in every sample key: 1–64 characters of `[A-Za-z0-9._-]`. Use a new id for every run |
| `cf.run.stop.candidates` | 0 | Admit only the first N test cases. **Use it for smoke tests** |
| `cf.run.stop.errors` | 0 | Stop admitting after N samples ended BROKEN, TIMEOUT or INFRA_FAIL |
| `cf.run.stop.target` | empty | Stop once a passing sample reaches, for example, `coverage_line>=0.9` |
| `cf.run.budget.millis` | 0 | Time budget for the whole run |
| `cf.results.attach` | failed | Keep the source code of `failed`, `passed`, `all` or `none` samples (triage needs the failing sources) |
| `cf.random.seed` | 1 | Per-sample randomness derives from it, so runs repeat exactly |

### 6.3 Python add-on (only with `cf.execution.sut=python`)

| Key | Use |
|---|---|
| `cf.python.executable` | The interpreter command or path (default `python3`) |
| `cf.python.path` | Directories put before `PYTHONPATH` (where the system under test is) |
| `cf.python.env` | `NAME=VALUE;NAME2=VALUE2` for each test case process. Use this instead of exporting variables, for example `BUNDLE_SUT_ROOT=/abs/SUT` |
| `cf.python.workdir` | Working folder of each test case; default: the scenario's folder |
| `cf.python.sandbox` | empty (trusted, direct); `bwrap` (read-only file system, private `/tmp`, no network); `bwrap-network`; `docker:IMAGE[@NETWORK]` (read-only container; images are never pulled) |

### 6.4 Analysis and campaigns

| Key | Default | Use |
|---|---|---|
| `cf.analysis.enabled` | false | Analyze every run's results, which campaigns require |
| `cf.analysis.goals` | empty | `key:min`, `key:max` or `key:target:VALUE`, each optionally `:WEIGHT`, comma-separated, for example `latency_ms:min,total_eur:min` |
| `cf.analysis.mode` | exploratory | `formal` uses declared goals only and needs at least one eligible sample |
| `cf.analysis.discover` | true | Exploratory mode adds undeclared metrics as goals; set false for strict goals |
| `cf.analysis.front` | nsga-ii | Front ranker: `pareto`, `nsga-ii`, `nsga-iii`, `spea2`, `moea-d`. The front itself is always exact |
| `cf.analysis.topK` | 20 | Points listed by score |
| `cf.campaign.iterations` | 1 | Above 1, `run` becomes a campaign of analyzed rounds |
| `cf.campaign.strategy` | bias | `bias` narrows to the winners' values; `elitism` adds the winners as seed sheets; `noop` repeats |
| `cf.campaign.explorationFloor` | 0.25 | The share of each sheet's values a bias always keeps |
| `cf.campaign.minWinnerSupport` | 5 | Winners a sheet needs before a bias narrows it |
| `cf.campaign.stopWhenStable` | 0 | Stop after the front stays the same for N transitions |
| `cf.campaign.seed.input` / `cf.campaign.seed.output` | empty | Start from a saved seed, or write one |

Rarely needed (leave at their defaults unless a diagnostic points to them): `cf.pipeline.*`, `cf.results.maxSamples`,
`cf.results.retention`, `cf.compiler.cache.maxBytes`, `cf.generation.*`, `cf.assembly.*`, the other `cf.optional.*`
keys, `cf.render.*` affix rules, `cf.keys.logical.labels` and `cf.keys.window.scope`.

---

## 7. Modeling a problem: the core skill

### 7.1 Interview the human (short)

Ask only what you cannot infer, and offer a default for each question.

1. **Goal:** find defects, prove a rule always holds, find the best settings, or verify a fix?
2. **System under test:** what is it, how is it called (Java library, Python module, HTTP service on localhost, CLI),
   and how is it reset between test cases?
3. **What varies:** settings and inputs, steps whose order matters, events that may or may not happen. What values
   does each take?
4. **What can never happen:** impossible or meaningless combinations. These become rules.
5. **What "correct" means:** the oracle. How do we know a test case failed, and which failures matter most?
6. **What to measure, if anything:** latency, cost, errors. These become metrics and goals.
7. **Budget:** how many test cases or minutes are acceptable?

### 7.2 The four kinds of things

| Kind | Example | How it is generated | How covering treats it |
|---|---|---|---|
| **Static parameter** | browser engine, account type, currency | one value (or a group) per test case | covered t-wise |
| **Sequence of actions** | login → search → pay | values in a row, where order matters | keep "apart" so every allowed order runs with each kept static-context row (§7.5) |
| **Sudden action** | timeout, duplicate submit, network drop | `FW_Optional`: happens in one of its ways, or not at all | never covered; joins after selection: `final = kept × Π(nᵢ + 1)` |
| **Bootstrap** (`FW_RunMeFirstOnce`) | start or check the system, prepare shared data | Java: **once per run, before the first test case**; Python: before each case (§10.2) | not a factor |

Make each sudden action its own factor, so that sudden actions meet each other in every combination.

### 7.3 Choose the verb from the shape of the freedom

The verb follows from how one test case uses the factor's n values. Never pick a verb to hit a number. These forms
count the same with `cf.plan.autoPromoteSingleVerb` on or off:

| One test case takes | `FW_Seq` cells after the sheet name | Ways for n values |
|---|---|---|
| exactly one value | `FW_Combi(1)` | n |
| k values as a group (order irrelevant) | `FW_Combi(k)` | C(n,k) |
| k different values in a row (order matters) | `FW_Combi(k)`, `FW_Permut()` | P(n,k) |
| all values in every order | `FW_Permut` (bare, no brackets) | n! |
| k values that may repeat, as a group | `FW_CombiR(k)`, `FW_Combi(k)` | C(n+k−1,k) |
| k values that may repeat, in a row | `FW_PermutR(k)`, `FW_Combi(k)` | nᵏ |
| a group of a to b values (subsets) | `FW_Subsets_RANGE(a,b)` | C(n,a)+…+C(n,b) |
| a to b values in a row | `FW_Subsets_RANGE(a,b)`, `FW_Permut` | P(n,a)+…+P(n,b), starting from 1 value |
| any of the above, or not at all | put `FW_Optional` before the verbs | ways + 1 |

Several values of one factor are joined in the code by `FW_Concatenator=<text>` (for example `FW_Concatenator=+`).

Traps:

- A row with **exactly one bracketed verb** is applied twice by default. That changes nothing for `FW_Combi(1)` and
  `FW_Combi(k)`, but a lone `FW_CombiR(k)`, `FW_PermutR(k)`, `FW_Permut()` or `FW_Subsets` gives more, repeated rows.
  Use the two-verb forms above or a bare `FW_Permut`, and check any other lone verb with `plan`.
- `FW_Permut(k)` ignores k and orders all values. Write `FW_Combi(k)`, `FW_Permut()` instead.
- `FW_Permut` makes no order of an empty group, so an ordered range starts at one value.

Advanced directives exist for experts: `FW_Cartes(SHEET)`, `FW_Cartes_first(SHEET)`, `FW_Separator(SHEET)`,
`FW_Group` with `FW_ReplaceRE("pattern", "replacement")`, the joiner `FW_(start,,A,rel,B,,end,sep,1:1|1:N|M:1|M:M|M:N)`
with its placeholders `FW_()` and `FW_()G`, and the flags `FW_Exclude`, `FW_Heading`, `FW_LastInQueue`, `FW_Reuse` and
`FW_ReuseTableOnly`. Joiners create virtual sheets (`FW_VIRTUAL_*`) that consume labels. Use these only when a
simpler model cannot express the problem, and confirm every count with `plan` before relying on it.

### 7.4 Rules versus covering

- A **rule** (the "sieve") is for what **cannot happen or makes no sense**: a payment method the API does not offer,
  a step that needs an earlier step.
- A rule that removes **valid** combinations can hide a defect. When valid combinations are merely too many, keep them
  and let **covering** choose (§7.5).
- Keep deliberate negative tests. "An action attempted before its precondition" is a test, not an impossibility.

### 7.5 Selection: every combination, t-wise, or a budget

- **Every combination** (`cf.coverage.strength=0`) suits a few hundred test cases or fewer.
- **Pairwise** (`strength=2`) puts every pair of values of any two covered factors together at least once. It is the
  usual floor; most defects involve one value or two values together. `strength=3` suits risky interactions.
- **Budget** (`cf.coverage.budget=N`) picks the strongest t that fits N mandatory rows.
- Covering needs **three or more varying covered factors** to reduce anything. With two, every pair is already a whole
  combination.
- Lower bound for t=2: at least (ways of the largest factor) × (ways of the second largest) rows.
- **Sequences apart.** To run **every** order of a sequence with each kept row instead of letting covering pick orders
  like values, flag the sequence `FW_Optional` and add a rule that it is always present:
  `{"id": "steps_always", "polarity": "require", "assert": {"sheet": "steps", "present": true}}`.
  The wizard does exactly this.
- Sudden actions (`FW_Optional`) always join after selection and multiply the kept rows by (ways + 1) each.
- **A sudden action at any moment.** Give the sudden sheet one value per moment it may strike ("after 1" … "after N"),
  and let the generated code weave it into the timeline after that action. It then strikes in every order, at every
  moment, alone or with the other sudden actions. Forbid two at the same moment with `eqSheet` (§21.2).

### 7.6 The oracle: verdicts and metrics

- Every test case leaves an int verdict: **0 passes, any other value is a domain failure**, kept exactly. Verdict
  codes are plain ints; only labels are limited to signed bytes.
- Name every non-zero code in `FW_CUSTOM_VAR`, so that reports say "6 CHECK_FAILED: 4" instead of "6: 4".
- **`FW_EXIT_CODE`** in a cell of the form `… FW_VAR = FW_EXIT_CODE …` is replaced by that sheet's **1-based ordinal
  among data sheets** (sheet order; control sheets are not counted). A failing test case thereby names the check sheet
  that failed. Inserting or reordering data sheets renumbers these codes, so keep `FW_CUSTOM_VAR` in step: append new
  sheets at the end, or regenerate the map.
- **Severity order.** Put check sheets in order of severity and guard each with `if (FW_VAR == 0 && …)`, so the most
  serious failure names the verdict. Report every check's result as a metric too (for example `chk_total_ok=0|1`),
  because one verdict hides the rest.
- **Metrics line** (generated Java and Python): the first standard-output line that starts with `app=` and contains
  `FW_VAR=`. Its `key=value` tokens become numeric metrics (finite decimals) or text attributes; the `FW_VAR` token
  itself is not a metric. Print it exactly once, at the end. Keys are lower-cased; use `[a-z_][a-z0-9_.-]*`.
  Reserved keys: `candidate_id`, `repeat_idx`, `env_id`, `source_ref`, `run_id`, `fw_var`, `fw_custom_var`, `app`.
  Values must not contain spaces or `; | , ] ) }`.
- **Calibrate the oracle.** Include a case known to fail (a planted defect or a mutated expectation) and make sure it
  does fail. The Framework reports exactly what your check asserts; an error in the check looks like a product defect.

### 7.7 The label budget

Labels are counted as data sheets, plus every value cell, plus every virtual sheet a joiner creates; the compact
encoding holds 1–127. `validate` prints `values N (labels a..b)` and the highest label. Keep about 15% headroom.
To stay small:

- split a large model into focused scenarios (per feature, per screen, per risk);
- prefer fewer, richer values over many near-duplicates;
- put long code in the bootstrap, in `HEAD`/`TAIL`, or in a helper jar on the class path, not in many values.

### 7.8 Names and texts

- A **cell that starts with `FW_`** is read as a directive, unless it starts with `FW_VAR`, `FW_EXIT_CODE` or
  `FW_CUSTOM_VAR`. Never start a code cell with another `FW_…` identifier: write `String a = FW_ARGS;`, not
  `FW_ARGS…`.
- Sheet names may not start with `FW_`, and two sheets may not share a name ignoring case. Reserved: `FW_Seq`,
  `FW_SheetNames`, `FW_CUSTOM_VAR`, `FW_Arguments`, `FW_RunMeFirstOnce`, `FW_Info`, every directive name and
  `FW_VIRTUAL_*`.
- Rules name values by their **exact cell text**, including spaces and quotes. Plain values with code supplied by
  `FW_SheetNames` (§8.2) keep rules readable: `"pay"` instead of `"act(\"steps\", \"pay\");"`.
- In Java cells, write comments as `/* … */`, never `//`: a value's line breaks may be joined before compiling.
- Use unique local variable names per sheet, since all sheets' code ends up in one method.

---

## 8. Scenario files

### 8.1 Formats

| Format | Extension | Use it when |
|---|---|---|
| **JSON workbook** | `.json` | **Recommended for AI authoring**: exact, diff-friendly, and what the wizard writes |
| YAML / TOML workbook | `.yaml`/`.yml`, `.toml` | The same sheets in a subset of each syntax (§8.6) |
| XLSX workbook | `.xlsx`/`.xlsm` | The human prefers a spreadsheet; the showcase is one. Formula cells are kept as text, never evaluated |
| **Spec document** `bundle.spec/v1` | `.toml` or `.json` with `slots` | A compact authoring layer: slots, aliases, rules, goals and coverage in one file (§8.7) |

The format follows from the extension; an unknown extension is an error. Text files must be UTF-8.

### 8.2 Workbook anatomy

```json
{
  "sheets": {
    "FW_Seq":        [["HEAD", "FW_Combi(1)"], ["factor", "FW_Combi(1)"], ["TAIL", "FW_Combi(1)"]],
    "FW_SheetNames": [["factor", "prefix before the values", "ending after them"]],
    "FW_CUSTOM_VAR": [["0", "PASS"], ["3", "SOME_CHECK_FAILED"]],
    "HEAD":          [["code that opens the program"]],
    "factor":        [["value 1"], ["value 2"]],
    "TAIL":          [["code that prints the metrics line and closes the program"]]
  }
}
```

- **Sheet order is the order of the keys** (or of the XLSX tabs). The data sheets' order **is the code order** of
  each test case. `FW_Seq` row order does not move code, so keep both orders the same.
- **Data sheets** hold values: every non-empty cell of every row is one value. One value per row is clearest.
- A row is an array of cells; `null` leaves a column empty. Numbers and booleans become text.
- **Rendering.** For each data sheet present in a test case, the test case's code gets the sheet's `FW_SheetNames`
  prefix, then its value(s) joined by `FW_Concatenator`, then the ending. An absent (optional) sheet writes nothing.
  Every sheet without an `FW_SheetNames` row gets an empty prefix and ending by default.
- `FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE` anywhere in rendered code is replaced by the test case's display id.

Control sheets:

| Sheet | Rows | Meaning |
|---|---|---|
| `FW_Seq` | `[sheet, flags…, directives…]` | How each sheet varies (§8.3). Required |
| `FW_SheetNames` | `[sheet, prefix, ending]` | Text written before and after the sheet's block; `FW_EMPTY_STRING` means empty |
| `FW_CUSTOM_VAR` | `[code, NAME]` | Names of verdict codes; the code must be a whole number |
| `FW_RunMeFirstOnce` | one cell | The bootstrap: `public class RunMeFirstOnce { public static String FW_ARGS; public static void main(String[] args) { …; FW_ARGS = …; } }` |
| `FW_Arguments` | one argument per cell | Arguments for the bootstrap's `main` (or, without a bootstrap, for each test case's `main`) |
| `FW_Info` | free | Notes; ignored |

Cell directives inside data sheets:

| Cell | Effect |
|---|---|
| `FW_EMPTY_STRING` | an empty value |
| `FW_File=relative/path.txt` | the file's text becomes the value; the path resolves against the scenario's folder |
| `FW_Optional` | makes the **previous value of the same sheet** optional (cell level; rarely needed) |
| `FW_RefineCodeExceptQuoted` | cleans the previous value at parse time |

Not supported in this edition (refused with a message): `FW_CSVFile=`, `FW_Separator=`, `FW_BinaryFile=`, `FW_DBURL=`,
`FW_DBUSER=`, `FW_DBPASS=`, `FW_SQL=`.

### 8.3 `FW_Seq` rows

- The first cell names the target sheet; the following cells are flags and directives for it, read left to right.
  Whitespace around tokens does not matter.
- Flags: `FW_Optional`, `FW_Concatenator=<text>`, and the advanced `FW_Exclude`, `FW_Heading`, `FW_LastInQueue`,
  `FW_Reuse`, `FW_ReuseTableOnly`.
- Verbs: `FW_Combi`, `FW_CombiR`, `FW_Permut`, `FW_PermutR`, each with `()`, `(n)`, `(size)` or `(all)`;
  `FW_Subsets`; `FW_Subsets_MODE(sizes)` with MODE one of `BEFORE`, `AFTER`, `EXACT` (one size), `RANGE` (two sizes,
  smaller first) or `GIVEN` (a list).
- A sheet used in `FW_Seq` that does not exist becomes an empty virtual sheet, with a warning. Check every warning:
  it is usually a typo.
- A later row for the same sheet replaces the earlier row's directives (`PLAN_ROW_OVERWRITTEN`).

### 8.4 What a generated-Java test case must contain

- One public top-level class with `public static void main(String[] args)` and a `public static int FW_VAR = 0;`
  (or `FW_CUSTOM_VAR` under `cf.execution.verdict=FW_CUSTOM_VAR`). A missing field gives `BROKEN`
  (`EXEC_NO_VERDICT`); a missing `main` gives `BROKEN` (`EXEC_NO_ENTRY_POINT`).
- The verdict is read after `main` returns. An exception thrown out of `main` gives `BROKEN`, not a domain failure,
  so catch the system's exceptions in your check and turn them into verdicts if they mean "wrong behavior".
- It may use any class on the application class path plus `cf.execution.classpath`.
- It must **not** call `System.exit`, `Runtime.exit` or `Runtime.halt`; the sample becomes `BROKEN`
  (`EXEC_FORBIDDEN_CALL`).
- Long loops and waits must notice interruption (`Thread.interrupted()`, timeouts on I/O); otherwise a timed-out
  thread keeps running in the shared JVM.
- `main` receives `FW_ARGS` split at single spaces when a bootstrap exists, or the `FW_Arguments` cells otherwise.

### 8.5 Companion files

- `<stem>.constraints.json` beside `<stem>.json` is loaded automatically (`CONSTRAINT_LOADED`). Naming a different
  file with `--constraints` while a companion exists is an error (`CONSTRAINT_AMBIGUOUS`).
- `<stem>.properties` is **not** read automatically by `java -jar`: pass `--config <stem>.properties`. The desktop does
  not read it either; there, selection and runner settings live in step 3.

### 8.6 YAML and TOML workbooks

```yaml
sheets:
  FW_Seq:
    - ["HEAD", "FW_Combi(1)"]
  HEAD:
    - ["code"]
```

```toml
[sheets]
FW_Seq = [
  ["HEAD", "FW_Combi(1)"],
]
HEAD = [["code"]]
```

Rows are JSON-style arrays. TOML may use `null` for an empty cell; comments and trailing commas are allowed. Literal
strings, multi-line strings and inline tables are refused in the TOML workbook subset. Use JSON when in doubt.

### 8.7 The spec format (`bundle.spec/v1`)

A spec compiles into a workbook plus a constraint layer, and can also choose goals and coverage. It is detected by
its `slots` (`[[slots]]` in TOML, a `slots` array in JSON). YAML specs are refused.

| Top-level key | Meaning |
|---|---|
| `spec_version = "1"` | The contract version |
| `title`, `note` | Shown in `validate` |
| `args = [...]` | The `FW_Arguments` cells |
| `runme = "..."` | A bootstrap; a default stub is used when omitted |
| `goals = [{ key = "total", dir = "min" }]` or `["latency_ms"]` | Becomes `cf.analysis.goals`; the direction is inferred from the name when omitted (cost, latency, error, time… mean min; others mean max) |
| `coverage_strength`, `coverage_optimal` (true means set-cover), `coverage_budget` | Selection choices |
| `[[slots]]` | One data sheet each (below) |
| `[[constraints]]`, `params`, `orders` | Rules, in the sidecar's shape (§9) |
| `[[custom_vars]]` with `code` and `msg` | Verdict names |
| `seq_extra` | Extra `FW_Seq` rows (joiners); expert use |

Slot fields: `sheet` (required, not starting with `FW_`), `values` (required), `verb` **or** `alias` (not both),
`flags`, `raw`, `prefix`, `ending`, `separator`, `group_replace` and `key`.

- `raw = true`: each value is used exactly as written. Use it for code.
- Aliases: `choose_one` gives `FW_Combi(1)`; `choose_k(k)` gives `FW_Combi(k)`; `permute` gives `FW_Permut`;
  `permute(k)` gives `FW_Combi(k)` then `FW_Permut()`; `optional_action` gives `FW_Combi(1)` with `FW_Optional`;
  `feature_subset` gives `FW_Subsets`. Prefer explicit `verb` values from §7.3 when unsure. `validate` prints what
  each alias compiled to (`aliases … -> verb`).
- Non-raw values support option expansion: `"a=${1|2}"` expands to two values.
- In TOML, top-level keys must come **before** the first `[[slots]]` table.

---

## 9. Constraints reference (`<stem>.constraints.json`)

```json
{
  "version": 1,
  "params":  { "SHEET": { "exact value text": { "attr": 3 } } },
  "orders":  { "SHEET": ["low", "mid", "high"] },
  "constraints": [ { "id": "…", "desc": "…", "polarity": "forbid", "pairs": [ … ] } ]
}
```

Only these top-level keys are allowed, and `version` must be the integer 1. Every problem is reported with its line
and column; an authored rule is never dropped silently.

### 9.1 A rule

| Field | Meaning |
|---|---|
| `id` | A non-empty name, shown in every report. Always give one |
| `desc` | Your explanation for the human |
| `polarity` | `forbid` removes a test case where the body holds. `require` removes one where the body fails |
| **exactly one body** | `pairs`, `sets`, `when`, `mapping` or `assert` |
| `sheets` | The target sheets; required for `when`, optional otherwise |
| `condition` | A guard: the rule applies only where it holds (§9.3) |
| `gate` | `{"adjacent": true}` or `{"within": N}`: the target sheets' positions (sheet order) must be contiguous, or fit a window |

### 9.2 Bodies

| Body | Shape | Holds when |
|---|---|---|
| `pairs` | `[{"A": "a1", "B": "b1"}, …]` | some entry names every target sheet with the test case's value |
| `sets` | `{"A": ["a1", "a2"], "B": ["b1"]}` | every target sheet's value is in its set (the cross product) |
| `when` | `"payment.risk > customer.trust"` with `"sheets": ["customer", "payment"]` | the predicate is true over the `params` of the placed values |
| `mapping` | `{"source": "A", "target": "B", "allow": {"a1": ["b1", "b2"]}}` | always a requirement: when A takes a listed value, every value of B must be allowed |
| `assert` | a condition (§9.3) | the condition holds; `forbid` removes where it holds, `require` where it fails |

- Values are **exact cell texts**. A value that no cell has is reported (`CONSTRAINT_INERT`), so fix it.
- With multi-valued sheets, `pairs`, `sets` and `when` look at every placement combination: `forbid` removes a test case
  if **any** combination holds, and `require` removes it if **any** combination fails.
- A `pairs`, `sets` or `when` rule is inert on a test case where one of its sheets is absent. Use `assert` with
  `present` to reason about absence.
- A `when` predicate is a safe Python-like expression: comparisons (chains allowed), `and`/`or`/`not`, arithmetic,
  `in`, conditional expressions, list and tuple literals, and the functions `abs`, `min`, `max`, `round`, `len`,
  `int`, `float` and `bool`. Names must be its `sheets`; `SHEET.attr` reads `params`.

### 9.3 Conditions (for `assert` and `condition`)

A condition is a node, `{"all": [ … ]}`, `{"any": [ … ]}` or `{"not": { … }}`, or a leaf
`{"sheet": "S", "<operator>": operand}` with exactly one operator:

| Operator | Operand | True when (the sheet's values in this test case) |
|---|---|---|
| `eq` / `ne` | value | it is exactly that single value / it is not |
| `has` / `hasnt` | value | the value is among them / it is not |
| `in` | [values] | **all** of them are in the list (true when the sheet is absent) |
| `nin` | [values] | none of them is in the list |
| `hasAny` | [values] | at least one is in the list |
| `present` | true/false | the sheet takes part / is absent |
| `count`, `countGe`, `countLe` | integer | how many values it has |
| `eqSheet`, `neSheet`, `subOf`, `supOf` | other sheet | set comparisons with another sheet's values |
| `ge`, `gt`, `le`, `lt` | value | ordinal comparison using `orders` (a list, `"numeric"` or `"date"`); true when absent |
| `geSheet`, `gtSheet`, `leSheet`, `ltSheet` | other sheet | ordinal comparison between two sheets |

### 9.4 Common rules, written out

```json
{"id": "no_cod_via_api", "polarity": "forbid",
 "pairs": [{"channel": "api", "payment": "cod"}], "desc": "The API does not offer cash on delivery."}

{"id": "pay_needs_login", "polarity": "require",
 "assert": {"any": [{"sheet": "steps", "hasnt": "pay"}, {"sheet": "steps", "has": "login"}]},
 "desc": "A test case that pays has logged in."}

{"id": "not_both_coupons", "polarity": "require",
 "assert": {"not": {"all": [{"sheet": "coupons", "has": "SAVE10"}, {"sheet": "coupons", "has": "FREESHIP"}]}}}

{"id": "guests_one_coupon", "polarity": "require",
 "assert": {"any": [{"sheet": "customer", "ne": "guest"}, {"sheet": "coupons", "countLe": 1}]}}

{"id": "risk_needs_trust", "polarity": "forbid", "sheets": ["customer", "payment"],
 "when": "payment.risk > customer.trust",
 "condition": {"not": {"sheet": "channel", "eq": "web"}}}

{"id": "steps_always", "polarity": "require", "assert": {"sheet": "steps", "present": true}}
```

The `risk_needs_trust` rule uses `params`, for example
`"params": {"customer": {"guest": {"trust": 1}, "member": {"trust": 2}}, "payment": {"card": {"risk": 1}, "cod": {"risk": 3}}}`.

---

## 10. Runners: how test cases reach the system under test

| Runner (`cf.execution.sut`) | A test case is | Choose it when |
|---|---|---|
| `generated-java` (default) | a small Java program assembled from the cells, compiled in memory | the system is a Java library on the class path, an HTTP service on localhost, or the logic can be stated in Java |
| `python` (add-on) | a Python program written to a temporary file and run by the configured interpreter | the system is Python code |
| a plugin id | the test case's **selected values**, handed to your Java plugin, which returns a verdict and metrics | you want typed Java integration without code in cells |
| `inline-metrics` | text that already carries `key=value` metrics and optionally `FW_VAR=` | the "result" of a combination is known from data; nothing executes |

### 10.1 generated-java

See §8.4. Put the system's jars on `cf.execution.classpath`. One class loader holds them for the **whole run**, shared
by all parallel samples; only the generated classes are fresh per sample. So statics in your helper or system classes
are shared between concurrent test cases (keep per-case state thread-local), and a class that is rebuilt during a run
can mix two builds. Never rebuild during a run, and record the jars' hashes in `OPERATIONS.md`.

A library that finds its parts with `ServiceLoader` through the thread context class loader (Cucumber, for instance)
sees nothing of `cf.execution.classpath`, because test cases run with the application's loader as the context loader.
Set the context loader to your helper class's loader around such a call, and restore it afterwards.
`showcase-cucumber/` in the Framework checkout shows a complete example: Cucumber and Selenium driven from generated
test cases.

### 10.2 python

- Requires the add-on jar on `cf.execution.classpath` plus `cf.execution.sut=python` (or `--sut python`). Without the
  add-on, the run fails with `CONFIG_SUT_UNKNOWN`.
- Each test case runs as `__main__` in `cf.python.workdir` (default: the scenario's folder) with `cf.python.path` first
  on `PYTHONPATH`, and receives the `FW_Arguments` cells as arguments. No bytecode caches are written.
- The verdict is the module-level `FW_VAR` (or `FW_CUSTOM_VAR`). A missing verdict, an exception or a syntax error
  gives `BROKEN`; an interpreter that cannot start gives `INFRA_FAIL`; at the deadline the process is killed
  (`TIMEOUT`).
- Values keep their whitespace and line breaks, and each sheet's block ends with a line break, so write normal
  indented Python.
- A Python `FW_RunMeFirstOnce` runs **per test case**, before it, for at most 10 seconds, and may patch the test case
  file. This differs from Java, where the bootstrap runs once per run.
- For untrusted code, recommend `cf.python.sandbox=bwrap` (if bubblewrap is installed) or `docker:IMAGE`.

### 10.3 Plugin

Implement `org.ghyura.cf.spi.SutPlugin` and register it in `META-INF/services/org.ghyura.cf.spi.SutPlugin` inside a jar
on `cf.execution.classpath`. Select it with `--sut <id>` (§11.E). The scenario then needs only plain values and
`FW_Seq`. The plugin reads each sheet's selected values and returns `DomainObservation(verdict, metrics, attributes)`.
It may throw `SkipSample` (→ `SKIPPED`) or `SutUnavailable` (→ `INFRA_FAIL`).

---

## 11. Templates (copy, adapt, then validate)

These templates were validated on 2026-10-23 with small local fixtures (§0.3). A is self-contained; B adapts it to an
external Java library; C and D use the `pricing.py` fixture below; E checks plugin wiring; F reproduces one failure
from A. These checks validate the examples, not your own system. Validate again after adapting them.

### A. A wizard-style generated-Java scenario (recommended starting point)

A checkout flow with two static parameters, a sequence of steps, one sudden action, one rule, and a stand-in system
with a planted defect, so the oracle provably can fail. Values stay plain, and `FW_SheetNames` turns them into code
lines. This mirrors how the desktop's wizard (step 0) writes scenarios.

`checkout_demo.json`:

```json
{
  "sheets": {
    "FW_Seq": [
      ["HEAD", "FW_Combi(1)"],
      ["channel", "FW_Combi(1)"],
      ["customer", "FW_Combi(1)"],
      ["steps", "FW_Concatenator=+", "FW_Combi(2)", "FW_Permut()"],
      ["timeout", "FW_Optional", "FW_Combi(1)"],
      ["check", "FW_Combi(1)"],
      ["TAIL", "FW_Combi(1)"]
    ],
    "FW_SheetNames": [
      ["HEAD", "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
      ["channel", "set(\"channel\", \"", "\");"],
      ["customer", "set(\"customer\", \"", "\");"],
      ["steps", "act(\"steps\", \"", "\");"],
      ["timeout", "happen(\"timeout\", \"", "\");"],
      ["check", "FW_EMPTY_STRING", "FW_EMPTY_STRING"],
      ["TAIL", "FW_EMPTY_STRING", "FW_EMPTY_STRING"]
    ],
    "FW_CUSTOM_VAR": [
      ["0", "PASS"],
      ["6", "CHECK_FAILED"]
    ],
    "HEAD": [[
      "import java.util.*;\n/* checkout_demo: each test case sets the static parameters, does the steps in order, lets a sudden action happen, then checks. */\npublic class CheckoutDemo {\n    public static int FW_VAR = 0;\n    static final Map<String, String> settings = new LinkedHashMap<>();\n    static final List<String> actions = new ArrayList<>();\n    static final Map<String, String> chosen = new LinkedHashMap<>();\n    static void set(String parameter, String value) { settings.put(parameter, value); chosen.put(parameter, value); }\n    static void act(String sequence, String steps) { for (String step : steps.split(\"[+]\")) if (!step.isEmpty()) actions.add(step); chosen.put(sequence, steps); }\n    static void happen(String action, String how) { actions.add(action + \":\" + how); chosen.put(action, how); }\n    /* a stand-in for the system under test, with one planted defect */\n    static boolean orderLost(Map<String, String> s, List<String> a) { return \"mobile\".equals(s.get(\"channel\")) && a.contains(\"timeout:short\") && a.contains(\"pay\"); }\n    public static void main(String[] args) {\n"
    ]],
    "channel": [["web"], ["mobile"], ["api"]],
    "customer": [["guest"], ["member"]],
    "steps": [["login"], ["search"], ["pay"]],
    "timeout": [["short"]],
    "check": [[
      "        /* the oracle: replace orderLost with a call to the real system and a comparison with the expected answer */\n        boolean misbehaved = orderLost(settings, actions);\n        if (FW_VAR == 0 && misbehaved) { FW_VAR = FW_EXIT_CODE; }\n"
    ]],
    "TAIL": [[
      "        StringBuilder line = new StringBuilder(\"app=checkout_demo\");\n        chosen.forEach((factor, values) -> line.append(' ').append(factor).append('=').append(values.replace(' ', '_').replace('=', '_')));\n        line.append(\" actions=\").append(actions.size());\n        System.out.println(line + \" FW_VAR=\" + FW_VAR);\n    }\n}\n"
    ]]
  }
}
```

`checkout_demo.constraints.json`:

```json
{
  "version": 1,
  "constraints": [
    {
      "id": "pay_needs_login",
      "desc": "A test case that pays has logged in.",
      "polarity": "require",
      "assert": {"any": [{"sheet": "steps", "hasnt": "pay"}, {"sheet": "steps", "has": "login"}]}
    }
  ]
}
```

`checkout_demo.properties`:

```properties
cf.run.id=checkout_demo_r1
cf.keys.encoding=SIGNED_BYTE
cf.coverage.strength=0
cf.coverage.budget=0
cf.results.attach=failed
```

Expected results, confirmed by the template validation run; confirm them again after adapting the example:

- Data sheets in order: HEAD 1, channel 2, customer 3, steps 4, timeout 5, check 6, TAIL 7. So `FW_EXIT_CODE` in
  `check` becomes **6**, which `FW_CUSTOM_VAR` names `CHECK_FAILED`.
- Labels: 7 sheets + 12 values = 19 of 127 (`validate`: values 12, labels 8..19).
- Ways: channel 3 × customer 2 × steps P(3,2)=6 = 36 mandatory rows; timeout optional ×2, so **72 generated**.
- The rule removes the orders `search+pay` and `pay+search`, 2 of 6, which leaves **48 executable**.
- The planted defect fails on mobile × any customer × {`login+pay`, `pay+login`} × timeout present = **4**. Expect
  `outcomes PASS 44, DOMAIN_FAIL 4, BROKEN 0, …` and `verdicts 0 PASS: 44; 6 CHECK_FAILED: 4`.

Commands:

```sh
W=/abs/path/cf-work/checkout                                   # a shell-local variable, not exported
cf validate   --scenario $W/checkout_demo.json --config $W/checkout_demo.properties
cf plan       --scenario $W/checkout_demo.json --config $W/checkout_demo.properties
cf candidates --scenario $W/checkout_demo.json --config $W/checkout_demo.properties --limit 2
# gate G3: smoke test, then the full run
cf run --scenario $W/checkout_demo.json --config $W/checkout_demo.properties \
       --set cf.run.stop.candidates=3 --run-id checkout_demo_smoke --checkpoint $W/runs/smoke.checkpoint
cf run --scenario $W/checkout_demo.json --config $W/checkout_demo.properties \
       --checkpoint $W/runs/checkout_demo_r1.checkpoint
cf triage --input $W/runs/checkout_demo_r1.checkpoint
cf export --input $W/runs/checkout_demo_r1.checkpoint --output $W/runs/checkout_demo_r1.html --kind html
```

`--run-id` and a `cf.run.id` line in the properties file are different sources, so they do not conflict; only setting
one key twice **on the command line** is an error.

To make it **pairwise**, add at least a third varying static parameter (for example
`device: phone, tablet, laptop`), set `cf.coverage.strength=2` and `cf.coverage.method=set-cover`, and keep the
sequence apart (§7.5): add `FW_Optional` to the `steps` row and the `steps_always` rule. Then confirm the kept rows and
the covered and infeasible tuples with `candidates --limit 0`.

### B. Calling a real Java system

Start from template A, then:

- `cf.execution.classpath=/abs/path/your-system.jar:/abs/path/dependency.jar` (on Windows, `;` separates entries);
- in `HEAD`, import its classes and, if it needs a fresh instance per test case, create it in `main`;
- in `check`, call it with `settings` and `actions`, compare with the expected answer, and turn its exceptions into
  verdicts if they mean wrong behavior;
- when instances are expensive or shared, start or verify them in the bootstrap (`FW_RunMeFirstOnce`, which runs once)
  and pass what test cases need through `FW_ARGS`;
- for an HTTP service on localhost, use `java.net.http.HttpClient` with explicit timeouts. Starting or stopping the
  service is gate G4.

### C. A Python system through the add-on

`pricing_scenario.json`, beside `pricing.py` (or set `cf.python.path`):

```json
{
  "sheets": {
    "FW_Seq": [
      ["HEAD", "FW_Combi(1)"], ["tier", "FW_Combi(1)"], ["coupon", "FW_Combi(1)"],
      ["qty", "FW_Combi(1)"], ["check", "FW_Combi(1)"], ["TAIL", "FW_Combi(1)"]
    ],
    "FW_CUSTOM_VAR": [["0", "PASS"], ["5", "NEGATIVE_TOTAL"]],
    "HEAD":   [["import pricing\nFW_VAR = 0"]],
    "tier":   [["tier = 'basic'"], ["tier = 'gold'"]],
    "coupon": [["coupon = None"], ["coupon = 'SAVE10'"], ["coupon = 'FREESHIP'"]],
    "qty":    [["qty = 1"], ["qty = 1000"]],
    "check":  [["total = pricing.price(tier, coupon, qty)\nif FW_VAR == 0 and total < 0:\n    FW_VAR = FW_EXIT_CODE"]],
    "TAIL":   [["print(f'app=pricing tier={tier} coupon={coupon} qty={qty} total={total} FW_VAR={FW_VAR}')"]]
  }
}
```

`pricing_scenario.properties`:

```properties
cf.run.id=pricing_r1
cf.keys.encoding=SIGNED_BYTE
cf.execution.sut=python
cf.execution.classpath=/abs/path/Combinatorics-Framework-JVM/fw-python/target/fw-python-0.1.0-SNAPSHOT.jar
cf.python.executable=python3
cf.analysis.enabled=true
cf.analysis.goals=total:min
cf.analysis.discover=false
```

Here `check` is data sheet 5, so a negative total gives verdict 5 (`NEGATIVE_TOTAL`). There are 2×3×2 = 12 test
cases. The add-on's file name depends on the build: list `fw-python/target/*.jar`, or use `addons/fw-python.jar` in an
installation.

For a self-contained check, place this deliberately faulty `pricing.py` beside the scenario:

```python
def price(tier, coupon, qty):
    # Deliberate bug: a discount exceeds the price of a basic single-item order.
    return 10 * qty - (15 if tier == "basic" and coupon == "SAVE10" else 0)
```

With that fixture, the measured result is 11 `PASS` and 1 `DOMAIN_FAIL` named `NEGATIVE_TOTAL`. Replace the fixture
with your actual pricing system when adapting the example.

### D. The same as a spec (`bundle.spec/v1`, TOML)

```toml
spec_version = "1"
title = "Pricing: tiers x coupons x quantities"
note = "AI-authored; validate before running"
args = ["noargs"]
coverage_strength = 2
coverage_optimal = true
goals = [{ key = "total", dir = "min" }]

[[slots]]
sheet = "HEAD"
verb = "FW_Combi(1)"
raw = true
ending = "\n"
values = ['''import pricing
FW_VAR = 0''']

[[slots]]
sheet = "TIER"
alias = "choose_one"
raw = true
ending = "\n"
values = ["tier = 'basic'", "tier = 'gold'"]

[[slots]]
sheet = "COUPON"
alias = "choose_one"
raw = true
ending = "\n"
values = ["coupon = None", "coupon = 'SAVE10'", "coupon = 'FREESHIP'"]

[[slots]]
sheet = "QTY"
alias = "choose_one"
raw = true
ending = "\n"
values = ["qty = 1", "qty = 1000"]

[[slots]]
sheet = "TAIL"
verb = "FW_Combi(1)"
raw = true
values = ['''total = pricing.price(tier, coupon, qty)
if total < 0:
    FW_VAR = 2
print(f"app=pricing tier={tier} qty={qty} total={total} FW_VAR={FW_VAR}")''']

[[constraints]]
id = "gold_never_freeship"
polarity = "forbid"
desc = "The gold tier has no FREESHIP coupon."
pairs = [{ TIER = "tier = 'gold'", COUPON = "coupon = 'FREESHIP'" }]

[[custom_vars]]
code = 2
msg = "negative total"
```

Use the Python properties from template C with `--config`; substitute your actual add-on path. This works for
`validate`, `plan`, `candidates` and `run`. The `--sut python` shortcut is available on `run`, not on the inspection
commands. `validate` reports the spec's title, its slots, what each alias compiled to and which settings it chooses.

This spec adds a constraint and pairwise selection to C. With the fixture above and C's properties, the measured
counts are 12 generated, 2 excluded by the constraint, 3 left out by covering, and 7 executable: 6 `PASS` and
1 `DOMAIN_FAIL` with verdict 2. This build displays the spec's verdict name as `FWCUSTOMVAR=2 negative total`.
Explicit coverage settings in your properties can change that selection; read the effective configuration.

### E. A typed plugin (Java developers)

```java
package com.example.cfplugin;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.util.Map;

public final class ShopPlugin implements SutPlugin {
    @Override public String id() { return "shop"; }
    @Override public String version() { return "1.0.0"; }
    @Override public Map<Integer, String> verdictNames() { return Map.of(0, "PASS", 10, "WRONG_TOTAL"); }

    @Override
    public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override
            public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) throws Exception {
                ctx.cancellation().throwIfCancelled();
                String tier = first(candidate, "tier");
                String coupon = first(candidate, "coupon");
                double total = 0; /* call the real system here, for example Shop.price(tier, coupon) */
                int verdict = total < 0 ? 10 : 0;
                return new DomainObservation(verdict, Map.of("total", total), Map.of("tier", tier, "coupon", coupon));
            }

            @Override
            public void close() {
            }
        };
    }

    private static String first(SutCandidate c, String sheet) {
        return c.selection(sheet).filter(s -> !s.values().isEmpty()).map(s -> s.values().getFirst()).orElse("");
    }
}
```

Add `META-INF/services/org.ghyura.cf.spi.SutPlugin` containing `com.example.cfplugin.ShopPlugin`. Build it against
`fw-app.jar` (`javac --release 25 -cp fw-app.jar …`, then `jar`), put the jar and the system's jars on
`cf.execution.classpath`, and run with `--sut shop`. The scenario needs only `FW_Seq` and plain data sheets
(`tier`, `coupon`). Metric values must be finite. Building is gate G4.

For inspection commands, set `cf.execution.sut=shop` in the properties or with `--set`; `--sut` is a `run` option.
As written, the placeholder `total = 0` makes every case pass. Validation confirmed compilation, plugin discovery
and execution with four tier/coupon combinations; checking a real shop requires replacing that placeholder and
calibrating the oracle with a known failure.

### F. A one-case reproducer

For generated Java or Python, freeze the **complete rendered source** of a failing candidate. Obtain it from
`candidates --format xml` with a sufficient `--limit`, matching the failed candidate's identity. Put that source into
one value of a data sheet named `CASE`, with this structure (replace the placeholder with the JSON-escaped source):

```json
{
  "sheets": {
    "FW_Seq": [["CASE", "FW_Combi(1)"]],
    "FW_CUSTOM_VAR": [["0", "PASS"], ["6", "CHECK_FAILED"]],
    "CASE": [["<complete rendered source of the failing checkout_demo candidate>"]]
  }
}
```

The verdict map above belongs to A; retain the original map for another scenario. The rendered source already has
its verdict numbers substituted. Keep the original runner, class path, required bootstrap and arguments, and any
referenced files. Do not attach the old factor constraints to this single-source scenario. Validate, plan and inspect
it: expect **exactly one executable candidate**, then run with a new run id and checkpoint. A's mobile, `login+pay`,
timeout-present case was reproduced this way as one `DOMAIN_FAIL` with verdict 6.

For a typed plugin, preserve the selected values and their order in a reduced value-based scenario instead; inspect
the selection passed to the plugin. Merely deleting unused values can still leave several permutations, repeated
selections or optional branches. Confirm the count is one before calling it a one-case reproducer. Keep reproducers
in `repro/` and re-run them after a fix.

---

## 12. Standard operating procedure

1. **G0.** Present the agreement and record it in `OPERATIONS.md`.
2. **Probe the environment** (read-only): `java -version`, locate `fw-app.jar`, `cf --version`, `cf capabilities`,
   `cf doctor`. Report blockers to the human.
3. **Interview** (§7.1). **G1:** restate the goal, the system under test, the runner, the workspace, and what will
   execute.
4. **Model** (§7): factors and kinds, verbs, rules, selection, oracle, metrics. Write the scenario, its constraints
   and its properties in the workspace. Start from §11 or from a verified example (§17).
5. **Validate and count** (all read-only):
   - `cf validate --scenario S --config P`: zero errors; read every warning; labels within budget; no setting from
     the environment unless intended.
   - `cf plan --scenario S --config P`: each sheet's formula and rows match your intent (§7.3).
   - `cf candidates --scenario S --config P --limit 3`: generated, removed per rule, left out by covering, executable;
     the covering line; the source of the first test cases. Read one completely: does it compile in your head, and
     does it test what you meant?
6. **G2.** Show the design summary (§3) with these numbers. Change and re-validate until approved.
7. **Smoke run (G3).** `cf run … --set cf.run.stop.candidates=3 --run-id <name>_smoke --checkpoint …`. Required:
   `BROKEN 0, TIMEOUT 0, INFRA_FAIL 0`; verdict names resolved; the metrics line present (check with
   `cf export --input <smoke checkpoint> --output smoke.kv --kind metrics` and `head smoke.kv`). Any `BROKEN` means a
   harness or design error: fix it before the full run.
8. **Full run (G3)** with a fresh run id and a checkpoint. Prefer `workers=1` when test cases share anything global.
   For long runs, write the output to a log file and read only the summary lines.
9. **Read the results** (§13): state, outcomes, verdicts, reconciliation, identity digests.
10. **Explain failures:** `cf triage --input <checkpoint>` gives findings, a minimal witness each, and the values that
    explain them. Build one-case reproducers (§11.F) for each distinct finding.
11. **Optional:** analysis (`export --kind metrics`, then `analyze`), a campaign, coverage import, a re-run after a
    fix under a new run id.
12. **G5.** Write `REPORT.md` (§18). Give the human the HTML export path for a visual view. Ask what to do next.

Keep `OPERATIONS.md` current: every command, its exit code, the run id, and the scenario, plan and policy digests
printed on the `identity` line.

---

## 13. Reading results

### 13.1 The run summary

| Line | Meaning |
|---|---|
| `state` | `COMPLETED`, `FAILED` or `CANCELLED`, and the elapsed time |
| `candidates` | generated, excluded by rules, left out by covering selection, executable; "(counts not exact …)" if a scan was cut |
| `stopped` | why admission ended: exhausted, a stop policy, the error budget, or the time budget |
| `executed` | candidates admitted, samples, attempts, retries |
| `outcomes` | the count of each outcome (§13.2) |
| `verdicts` | `code NAME: count` for each verdict seen; `0 PASS` is the passing verdict |
| `abandoned` | samples that ignored their deadline; their threads still run in the JVM |
| `reconciled` | "every total adds up", or THE TOTALS DO NOT ADD UP: a framework defect, so report it |
| `identity` | scenario, plan and policy digests; quote them in your report |
| analysis block | when analysis is enabled: goals, front, top-K, balanced picks |

### 13.2 Outcomes

| Outcome | Is it about the system under test? | What you do |
|---|---|---|
| `PASS` | Yes: the verdict was 0 | — |
| `DOMAIN_FAIL` | **Yes: a finding**, named by its verdict | triage, reproduce, report |
| `BROKEN` | No: the test case could not be judged (compile error, no verdict, exception, forbidden call) | fix the scenario or harness; never report it as a product defect |
| `TIMEOUT` | Not by itself | raise the timeout or fix waiting; may hint at a hang worth reporting separately |
| `INFRA_FAIL` | No: environment (no compiler, interpreter missing, out of memory) | fix the environment |
| `SKIPPED` | No: deliberately not run (stop policy, plugin skip) | check the stop settings |
| `CANCELLED` | No | resume or re-run |

Only `PASS` samples with metrics can win an analysis.

### 13.3 The covering line (from `candidates`)

`coverage t=2 set-cover (greedy, not a proven minimum): kept 72 mandatory rows; 888 tuples, 884 feasible and covered,
4 infeasible`. "Infeasible" tuples are value pairs the rules make impossible; they are not gaps. Quote this line as the
coverage guarantee: "every feasible pair of values appears in at least one test case".

### 13.4 Exports

| `--kind` | File | For |
|---|---|---|
| `report` | XML summary | machine reading |
| `results` | CSV, one row per attempt | detailed audits |
| `csv` | CSV, the final samples with their metrics | spreadsheets |
| `html` | one self-contained page with triage findings | **the human supervisor** |
| `metrics` | `.kv`, one line per sample: `candidate_id repeat_idx env_id run_id`, then attributes, metrics and `FW_VAR` | `analyze`, and the desktop's step 8 |

### 13.5 Analysis

```sh
cf export  --input runs/r1.checkpoint --output runs/r1-metrics.kv --kind metrics
cf analyze --results runs/r1-metrics.kv --set cf.analysis.goals=latency_ms:min,total_eur:min --set cf.analysis.discover=false
```

You get statistics for each metric, the exact Pareto front over the goals, the top-K by score and three balanced picks
(weighted sum, Tchebycheff, distance to ideal). When several picks agree, say so: it strengthens the recommendation.
`--coverage-format jacoco|coverage-py|gcov --coverage-report PATH` imports coverage your own tools wrote (`{lineNo}` in
the path is the corpus line); the Framework does not run coverage tools itself.

### 13.6 Campaigns

Set `cf.campaign.iterations` above 1, `cf.analysis.enabled=true` and goals, then `run`. Each round is a complete run.
The next round keeps the winners' values (strategy `bias`, with an exploration floor). A campaign ends when its rounds
are done, when the front is stable, when there is no signal (`CAMPAIGN_NO_SIGNAL`) or when nothing is left
(`CAMPAIGN_EXHAUSTED`). Report every round, not only the last.

### 13.7 Triage

`cf triage --input runs/r1.checkpoint [--json triage.json]` groups failures into distinct findings by what classifies
them, picks the **minimal witness** (the failing test case with the least source), and lists the declared values whose
failure rate departs from the run's. Start every debugging conversation from the witness.

---

## 14. Problem playbooks

| Situation | Model | Selection and settings | Report |
|---|---|---|---|
| **Settings interactions** (flags, platforms, form fields) | each setting a static parameter, `FW_Combi(1)`; rules only for impossible pairs | t=2 set-cover; t=3 for risky areas | verdict counts, coverage line, findings with witnesses |
| **Workflow order** | steps as a sequence: `FW_Combi(k), FW_Permut()` or `FW_Permut`; "needs" rules for real preconditions only | keep the sequence apart (§7.5) so every allowed order runs with each kept row | which orders fail and with which parameters |
| **Interruptions and faults** | each fault a sudden action (`FW_Optional`). Its code runs where its sheet stands in sheet order, so place it before or after the sheets it should interrupt | sudden actions multiply after selection: watch the count | failure rate with and without each fault |
| **Repeated actions** (double submit, pause/resume/cancel) | `FW_PermutR(k), FW_Combi(k)` for ordered repeats | 3 actions in 3 positions give 27 orders | which orders break |
| **Tuning / best settings** | parameters as values; the metrics line carries measurements | `cf.analysis.enabled=true`, goals, `repeat.count` above 1 with `repeat.scope=metrics` for noise, `serialize=true` for timing | the Pareto front, balanced picks, noise intervals |
| **Verify a fix** | the same scenario, plus one-case reproducers | re-run under a new run id; compare verdict tables | which verdicts moved, and confirm nothing else did |
| **Flaky behavior** | the same as the failing design | `retry.maxAttempts` above 1, or `repeat.count` above 1 | stable versus flaky cases |
| **Code variants** | `cf decompose --source X.java --out X.toml`, then add alternative rewrites as extra values of the relevant slots, plus an oracle slot | count first: every slot multiplies | which combination of variants passes or performs best |
| **Log triage** | `cf score --input app.log [--rules rules.json]` | — | the lines worth a look (opt/watch); nothing executes |

---

## 15. Troubleshooting

| Symptom or code | Likely cause | Fix |
|---|---|---|
| exit 2, `USAGE_*` | wrong option, missing value, a key set twice on the command line | `cf <command> --help` |
| exit 3, `CONFIG_UNKNOWN_KEY` / `CONFIG_INVALID_VALUE` | typo in a `cf.*` key, or a value out of range | `cf validate` names the line and suggests the key |
| `INPUT_UNRECOGNIZED_FORMAT`, `INPUT_MALFORMED`, `INPUT_SCHEMA` | wrong extension, invalid JSON/TOML, top level not `{"sheets": …}` | fix the file; JSON is strict UTF-8 |
| `INPUT_DUPLICATE_SHEET` | two sheets differ only in case | rename one |
| `SPEC_UNKNOWN_DIRECTIVE` | a data cell starts with `FW_` | §7.8: do not start code with `FW_…` |
| `PLAN_NO_SEQUENCE` | missing or empty `FW_Seq` | add rows |
| `PLAN_DIRECTIVE_SYNTAX` | a malformed verb, such as `FW_Combi(two)` or `FW_Subsets_FOO(1)` | use the §7.3 forms |
| `PLAN_VIRTUAL_SHEET` (warning) | `FW_Seq` names a sheet that does not exist | usually a typo; fix the name |
| `KEY_CENSUS_OVERFLOW` | more than 127 labels under `SIGNED_BYTE` | split the scenario (§7.7) |
| `CONSTRAINT_UNKNOWN_SHEET` / `CONSTRAINT_UNMATERIALIZED_SHEET` | a rule names a sheet that does not exist or is no column of the test cases | correct the name; constrain only real columns |
| `CONSTRAINT_INERT` (warning) | a value no cell has (often whitespace or quotes differ) | copy the exact cell text |
| `CONSTRAINT_AMBIGUOUS` | a companion sidecar exists and `--constraints` names another | use one of them |
| `CONSTRAINT_EXPRESSION` | `when` uses an unknown name or unsafe syntax | names must be the rule's `sheets` plus the allowed functions |
| `ASSEMBLY_COVERAGE_REFUSED` | mandatory product above `cf.coverage.hardCap`, or nothing to cover | rules or split first; raise the cap only with the human |
| `ASSEMBLY_EMPTY_FACTOR` / `GEN_EMPTY_TABLE` | a mandatory sheet makes no rows (for example k > n) | fix the verb or the values |
| many `BROKEN` with `EXEC_COMPILE_ERROR` | invalid Java after concatenation: `//` comments, missing braces, data sheets out of order | read one test case's source (`candidates --limit 1`); fix the cells |
| `BROKEN` with `EXEC_NO_VERDICT` | no `public static int FW_VAR` (Java), or Python never set it | add it |
| `BROKEN` with `EXEC_SUT_EXCEPTION` | the system or your code threw out of `main` | catch the error in the check and decide its verdict |
| `INFRA_FAIL` with `EXEC_COMPILER_UNAVAILABLE` | running on a JRE | use a JDK 25 |
| `CONFIG_SUT_UNKNOWN` | the runner id is not found (Python add-on or plugin not on the class path) | add the jar to `cf.execution.classpath` |
| `TIMEOUT` with `EXEC_UNCOOPERATIVE`, `abandoned` above 0 | code ignores interruption | add timeouts; re-run with `workers=1`; tell the human threads were left running |
| `EXEC_PREPARE_FAILED` | the bootstrap does not compile or never sets `FW_ARGS` | fix `FW_RunMeFirstOnce` |
| `RESOURCE_LIMIT` (exit 5) | the memory budget or `cf.results.maxSamples` was exceeded | reduce the size, or raise the budget and heap with approval |
| `RESOURCE_LIMIT` from `export --kind html` | the page holds every sample's captured output | `--set cf.memory.budget.bytes=…` and a larger `-Xmx` (the command uses at most a quarter of the heap); keep test-case output small |
| `RESOURCE_LIMIT … checkpoint decoding` from `triage` | triage decodes a checkpoint within a fixed 128 MiB, whatever the settings | export the run with `--kind metrics`, copy the file as `metrics.kv` into a folder of its own, and `triage --input <that folder>` with a raised budget |
| every sample BROKEN, with no message in the exports | an exception escaped `main` (the exports carry no failure text) | reproduce one test case outside the Framework, with the same class-loader layout, to see the exception |
| `CHECKPOINT_MISMATCH` on resume | settings or files changed since the checkpoint | resume with identical inputs, or start a new run |
| `RUN_RECONCILIATION_FAILED` | a framework defect | report it to the human with the run id and digests |
| unexpected counts | a lone bracketed verb was applied twice, or `FW_Permut(k)` | §7.3 traps; confirm with `plan` |
| the code order differs from what you expected | sheet order is the code order, not `FW_Seq` order | reorder the sheets |
| `FW_EXIT_CODE` verdicts are named wrongly | data sheets were inserted or moved | regenerate `FW_CUSTOM_VAR` from the sheet order |
| settings "ignored" | `java -jar` does not read `<stem>.properties` by itself, or a `CF_*` variable overrides the file | pass `--config`; check origins in `validate` |

---

## 16. Working with the human through `guif`

The desktop is the supervisor's window into your work. You operate through the command line and files; open `guif`
only when the human asks (gate G4). The human can open your files themselves:

| Step | What the supervisor sees | How you use it |
|---|---|---|
| **0 · Wizard** | composes a first scenario in seven pages: situation, factors, how each varies, rules, how many, bootstrap, finish | the human may sketch a design here. You then take over the saved `NAME.json`, `NAME.constraints.json` and `NAME.properties` |
| **1 · Library** | verified examples, ready to open | needs a sibling `Combinatorics-Framework-QA-edition` checkout (its `generator_trunk`); otherwise it is empty |
| **2 · Authoring** | sheets, control sheets (2.1), validation (2.2), operand dependencies (2.3), dependency graph (2.4) | point the human at your scenario for review |
| **3 · Scenario** | the chosen scenario, its check, and the **runner settings** (runner, class path, Python settings, covering selection) | the desktop does not read `<stem>.properties`: tell the human which settings to enter here if they run from the GUI |
| **4 · Constraints / Sieve** | rules as a graph, bond matrix, editor and JSON, with each rule's impact; the **Face 2** browser editor (loopback only) | the human can review or draw rules; they are saved to the same companion file you use |
| **5 · Candidates** | counts, each rule's impact, and the code of each test case with the origin of every chunk | the best place for the human to check that your test cases mean what you said |
| **6 · Run** | run, pause, resume, cancel, progress; export report, results, csv, html or metrics | the human may prefer to press Run themselves after G3 |
| **7 · Campaign** | rounds of runs steered by the winners | — |
| **8 · Results analysis** | metrics corpus (`.kv`) analysis: front, picks, coverage | give the human your `.kv` export |
| **9 · Text scorer** | log line scoring | — |

"Hints on" (F1) makes every control explain itself. Every file field has an Auto-search button.

---

## 17. The SUT companion repository and the bundled examples

- **In this checkout:** `scenarios/showcase/showcase.xlsx` (a bookshop checkout with `.constraints.json` and
  `.properties`: 139,968 combinations, t=2 keeps 72 mandatory rows, which become 129 test cases). Also the
  `guif_dogfood/` and `guif_dogfood_v2/` folders: large real designs (JSON scenarios with constraints and properties)
  that tested the desktop itself, including named watchers, sudden events and one-case reproducers. Read them as
  patterns; copy them into your workspace before changing anything.
- **`../SUT` (sibling repository):** systems under test with intentional defects and reference oracles, mostly Python:
  a tutor, a telemetry catalog, a fintech stack, pricing and secure-pipeline tryouts, billing and order fulfilment.
  It also holds scenario workbooks in `FW_scenario_inputs/` (`k8s_rightsizing`, `postgres_tuning`, `rag_pipeline`,
  `cybersec_*`, `tinyml_*` and their `*_k1_ablation` variants). Its documentation is written for the QA (Python)
  edition. For the JVM edition:
  - the workbooks are fine for `validate`, `plan` and `candidates` (counting, covering, rules). Their values are
    `key=value` lines rather than Java, so look at a test case's source before choosing a runner; generated Java will
    not compile them;
  - Python systems can be driven through the add-on (§10.2). Pass `BUNDLE_SUT_ROOT` through `cf.python.env`, not as
    an exported variable;
  - running a SUT, installing its dependencies or starting its services is gate G4. Their own READMEs warn that some
    are deliberately unsafe: bind services to loopback, and never deploy them.

---

## 18. Report template (`REPORT.md`)

```markdown
# <Task>: Combinatorics Framework report
Supervisor: <name or "the user">  ·  Operator: AI  ·  Date: <date>  ·  Framework: <cf --version>

## Goal and verdict in one paragraph
<What was asked, what was found, how confident, and what you recommend.>

## System under test and runner
<What ran, how it was called, isolation, side effects.>

## Design (approved at G2)
| Factor | Kind | Values | FW_Seq | Ways |
Rules: <id: what it says: combinations removed>
Selection: <t, method, kept rows; the coverage line, quoted>
Oracle: <checks in severity order; verdict codes and names; how it was calibrated>

## Runs
| Run id | Scenario/plan/policy digests | Generated | Executable | Outcomes | Verdicts | Duration |

## Findings
| # | Verdict (code NAME) | Count | Minimal witness (values) | Reproducer file | Expected vs actual |

## Measurements (if any)
<Front, balanced picks, noise.>

## Limits and what was not tested
<Uncovered areas, infeasible tuples, harness caveats, BROKEN/TIMEOUT left, environment notes.>

## Decisions needed from you
1. …
```

---

## 19. Glossary

| Term | Meaning |
|---|---|
| Scenario | The workbook (or spec) that describes what varies, and how |
| Factor / sheet | One thing that varies; its values are the sheet's cells |
| Control sheet | A sheet whose name starts with `FW_` and which instructs rather than holds values |
| `FW_Seq` | The control sheet that says how each factor varies |
| Verb / directive | An `FW_Seq` instruction such as `FW_Combi(2)` |
| Mandatory / optional sheet | In every test case / present in one of its ways or absent (`FW_Optional`) |
| Candidate / test case | One assembled combination, rendered as code (or values, for plugins) |
| Sample | One execution of a candidate: one repeat in one environment |
| Mandatory row | One combination of the mandatory sheets; covering selects among these |
| t-wise covering | Keep rows until every feasible t-tuple of values appears at least once |
| Rule / sieve / bond | A constraint that removes combinations that cannot happen |
| Verdict | The int a test case leaves: 0 passes, any other value is a named domain failure |
| `FW_EXIT_CODE` | Replaced by the sheet's ordinal among data sheets, so the failing check names itself |
| Metrics line | `app=… key=value … FW_VAR=n`, the first such line of a test case's output |
| Checkpoint | The run's saved state and results, for export, triage and resume |
| Witness | The smallest failing test case of a finding |
| Pareto front | Results not beaten on every goal by any other |
| Label | A key number for each sheet and value; at most 127 in compact encoding |
| Runner / SUT plugin | What executes test cases: generated Java, Python, or a typed Java plugin |
| Bootstrap | `FW_RunMeFirstOnce`, run once before all test cases (Java) |

---

## 20. Operator card (compact summary)

```yaml
framework: Combinatorics Framework, JVM Edition ("the Bundle")
license: BUSL-1.1; free for an individual human (one installation); AI operation only with a real human QA engineer directing; not for AI training; AGPL-3.0-only from 2030-08-08
invoke: java -Djava.awt.headless=true -jar <abs>/fw-app.jar <command> [options]
requires: JDK 25 (with compiler); Python 3 and the fw-python add-on jar for Python test cases
read_only_commands: [--help, --version, capabilities, doctor, validate, plan, candidates, export, analyze, triage, score, decompose]
executing_commands: [run, resume, benchmark]          # gate G3
gui: [guif, desktop]                                  # gate G4, needs a display
exit_codes: {0: OK (defects are results), 1: FAILED, 2: USAGE, 3: VALIDATION_FAILED, 4: UNSUPPORTED_CAPABILITY, 5: RESOURCE_LIMIT, 6: CANCELLED, 130: INTERRUPTED}
outcomes: {finding: [DOMAIN_FAIL], pass: [PASS], not_about_sut: [BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED, CANCELLED]}
scenario_files:
  scenario: <name>.json      # {"sheets": {...}}; key order = sheet order = code order
  rules: <name>.constraints.json   # auto-loaded companion
  settings: <name>.properties      # pass with --config
must_set: [cf.keys.encoding=SIGNED_BYTE, cf.run.id=<new per run>]
smoke_test: --set cf.run.stop.candidates=3
verdict: public static int FW_VAR (0 pass); FW_VAR = FW_EXIT_CODE -> sheet ordinal; name codes in FW_CUSTOM_VAR
metrics_line: first stdout line starting "app=" and containing "FW_VAR="
label_budget: data sheets + values + virtual sheets <= 127
safe_verbs: [FW_Combi(1), FW_Combi(k), "FW_Combi(k),FW_Permut()", FW_Permut, "FW_CombiR(k),FW_Combi(k)", "FW_PermutR(k),FW_Combi(k)", "FW_Subsets_RANGE(a,b)", "FW_Subsets_RANGE(a,b),FW_Permut", "FW_Optional + any"]
traps: [lone bracketed verb applied twice, FW_Permut(k) ignores k, // comments in Java cells, cells starting with FW_, sheet order != FW_Seq order, inserting sheets renumbers FW_EXIT_CODE, CF_* env overrides the properties file, java -jar ignores <stem>.properties, workers>1 with shared global state, cf.execution.classpath is one loader per run (statics shared), context class loader is the application's (ServiceLoader libraries see nothing), exports carry no exception text, triage decodes checkpoints within a fixed 128 MiB]
design_first: [timelines: every order of the actions kept apart from covering, sudden events as FW_Optional sheets whose values are the moments, pairwise only for static context]
field_notes: section 21 (measured on 2026-10-22, showcase-cucumber/)
gates: [G0 agreement, G1 problem/plan, G2 design, G3 execute, G4 outside workspace, G5 conclusions]
```

---

## 21. Field notes from actual use

On 2026-10-22, an AI operator built and ran `showcase-cucumber/` in the Framework checkout, with the human directing
throughout. It has two scenarios whose candidates are Gherkin scenarios, which Cucumber runs inside the Framework's JVM:

- `01-gherkin-checkout`: against an in-JVM checkout engine and its payment ledger;
- `02-selenium-webshop`: against a dummy web shop, through headless Firefox and Selenium WebDriver.

Each system under test carries planted defects that hide in the order of actions and in the timing of events. The
numbers below are measured, not derived. Everything needed to repeat them is in the checkout:
- `java Showcase.java build|check|smoke|run|report|cucumber|serve`, run from `showcase-cucumber/`, gives every command
  a space of its own (item 7 of §21.3);
- the scenario workbooks, with their `.constraints.json` and `.properties`, are executed, working examples to copy
  from;
- the code comments of `CheckoutEngine` (D1 to D9) and `DummyWebShop` (W1 to W7) say what each planted defect is and
  where it hides.

A second run of each design gave the same verdict for every case: 4,128 of 4,128 and 714 of 714.

### 21.1 What the human corrected, and what it teaches

**The correction.**
- The first design was a pairwise matrix of many static parameters (customer, cart, coupons, shipping, payment …),
  with one sequence sheet and two sudden actions at fixed places.
- The human's correction, verbatim: *"I hope you understand what is inter-operability of action sequences, not
  'parameter-matrices for pair-wise'"*, then *"and sudden actions"*.
- The redesign: 4 static parameters, every order of 4 actions, and two sudden events that may strike after any action.
  That gives 7 pairwise-kept static rows × 750 timelines, so the weight sits on the timelines.

**The lesson.** The Framework's distinctive value is the **timeline**:
- actions whose order matters;
- repeated actions;
- events that may or may not strike between them, at any moment.

Give pairwise only the static context around the timeline. Present this shape, with its counts, at G2, and ask whether
the weight is where the human wants it *before* anything heavy runs.

**"Be sneaky-tricky" and "outsmart the testable system".** The human meant defects that obvious tests pass by. What
worked:
- disguised input (`save10` beside `SAVE10`);
- half-cent rounding, which shows only for one item mix;
- a request delivered twice, whatever request it was;
- a price change *after* an item was added versus *before* it;
- a stale payment quote;
- a system that behaves well while it is watched (§21.4).

### 21.2 Modeling patterns that worked

| Need | Pattern | In the showcase |
|---|---|---|
| Every order of the actions, never sampled | flag the sequence `FW_Optional` and add `{"polarity": "require", "assert": {"sheet": "steps", "present": true}}`: the sequence leaves the covering selection, and every kept row runs every order | `steps`: 24 orders with each of 7 rows |
| A sudden event that may strike at any moment | an `FW_Optional` sheet whose **values are the moments**, `"after 1"` … `"after N"`; the generated code weaves the step in after the N-th When of the timeline (`GherkinCase.strike`) | `price_rise` (5 ways with absence), `duplicate` (6), `reload` (4), `back_repeat` (5) |
| Two events never at the same moment | `{"polarity": "forbid", "assert": {"all": [{"sheet": "A", "present": true}, {"sheet": "A", "eqSheet": "B"}]}}`. The `present` guard matters: two absent sheets are "equal" | `one_sudden_event_at_a_time` removed 700 of the checkout's timelines |
| A retry of whatever came last | the sudden step repeats the previous request; each action records itself as "the last request" | one sheet found the duplicated item (D9), the duplicated coupon (D5) and the duplicated invoice order (D3) |
| Going back and doing it again | "goes back and repeats the last action" | duplicate guest orders (W1), a coupon re-applied by going back onto its GET page (W2) |
| Static context | pairwise set-cover over the static sheets only | 24 → 7 and 64 → 7 rows |
| A system that behaves when watched | make *who is watching* a factor | `channel` = test / terminal; `stealth` = hides / shows |
| Code from plain values | `FW_SheetNames` prefix and ending wrap the value into a line of code, so rules and tags name plain values (`"pay"`, `"FREESHIP"`) | every factor sheet |
| A multi-line fragment in a cell | `FW_File=fragments/x.txt`, with `cf.render.preserveWhitespace=true` and a prefix that opens a Java text block (`g.background("""` + newline) | Gherkin Backgrounds with data tables |
| A check's verdict from its own sheet | `then("…", FW_VAR = FW_EXIT_CODE);`, with a helper that hands the code on and resets `FW_VAR` | 4 + 3 check sheets |
| The case id inside the code | `FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE` in a string literal | feature file names, Scenario names |

Two traps of these patterns:
- A comment that contains the literal text `FW_VAR = FW_EXIT_CODE` turns *its* cell into a carrier, and the cell is
  rewritten too. Describe the pattern in words in comments.
- `FW_VAR = FW_EXIT_CODE` must be preceded by white space in its cell to be recognised.

### 21.3 Runtime facts that cost time

1. **One class loader for the run.**
   - `cf.execution.classpath` is loaded once per run and shared by parallel samples; only the generated classes are
     per sample.
   - Per-case state in helper or glue classes must therefore be thread-local. An `InheritableThreadLocal` worked; the
     Framework binds the threads a sample starts to it.
   - Never rebuild the helper classes while a run is in progress.
2. **The thread context class loader is the application's.**
   - Libraries that find parts through `ServiceLoader` and the context loader (Cucumber's Gherkin parser and backends)
     find nothing. Every sample came back BROKEN.
   - Fix: set the context loader to your helper's class loader around the call, and restore it afterwards.
3. **The exports carry no exception text.**
   - A BROKEN sample's exception appears in none of `export --kind results|report|html`.
   - To see it, reproduce one test case outside the Framework with the same layout:
     `new URLClassLoader("cf-execution-classpath", jars, appLoader)` for the class path, then a child loader for the
     compiled candidate, then call `main`.
   - The candidate's source comes from `candidates --limit 1 --format xml`.
4. **Keep each test case's standard output small.**
   - Captured output goes into the checkpoint and the HTML page. With Cucumber's pretty output on stdout, 864 samples
     made a 9.3 MB checkpoint, and the HTML export asked for 640 MB (`RESOURCE_LIMIT`).
   - Writing the per-case report to a file instead (`--plugin junit:<case>.junit.xml`) brought that checkpoint to
     3.6 MB.
5. **Triage's fixed decoding budget.**
   - `triage --input <checkpoint>` decodes within a fixed 128 MiB, whatever `cf.memory.budget.bytes` says. 4,128
     samples needed 442 MB.
   - Route around it: `export --kind metrics`, copy the file as `metrics.kv` into a folder of its own, then run
     `triage --input <folder>` with a raised budget. The findings stay the same, but the minimal witnesses show no
     source size.
6. **"No RAM limit" still needs explicit settings.** Each command caps its budget at
   `min(cf.memory.budget.bytes, heap / 4)`. Lift both: `-XX:MaxRAMPercentage=50` and
   `--set cf.memory.budget.bytes=<half the RAM>`.
7. **Isolation when another agent works in parallel.** Give every command its own space, through variables set per
   command and never exported:
   - a Maven repository (`-Dmaven.repo.local`, with `-Dmaven.repo.local.tail=~/.m2/repository` read only);
   - `SE_CACHE_PATH` (Selenium Manager downloaded geckodriver 0.37.1 there), with `SE_AVOID_STATS=true`;
   - `HOME`, `TMPDIR`, `XDG_CONFIG_HOME`, `XDG_CACHE_HOME`, `XDG_DATA_HOME`, and `-Djava.io.tmpdir`;
   - no `DISPLAY` (`env -u DISPLAY -u WAYLAND_DISPLAY`), `MOZ_HEADLESS=1`;
   - port 0 on loopback for servers.

   Check afterwards that `~/.m2`, `~/.cache/selenium` and the process list are unchanged. `showcase-cucumber/Showcase.java`
   does all of this for each command it starts.
8. **A run that found thousands of defects exits 0.** Read the `outcomes` and `verdicts` lines, never the exit code
   alone.

### 21.4 Outsmarting the system under test: what worked

- **Defeat device by call stack.** The engine priced correctly whenever `io.cucumber.*` frames were on its stack.
  - Placing the order from a plain `pos-terminal` thread (the `terminal` channel) made the hidden defects appear.
  - Price lock: 27/27 failures on the terminal channel, 0/145 on the test channel.
  - Rounding: 36/36 on the terminal, 0/80 on the test channel.
- **Defeat device by `navigator.webdriver`.** The web shop behaved for automated browsers. The step redefines
  `navigator.webdriver` on the page (`Object.defineProperty(navigator, 'webdriver', {get: () => false})`) just before
  paying. The `stealth` factor shows the difference: a guest who goes back and pays again ordered twice in 48 / 48
  cases when the browser hid its automation, and in 0 / 24 when it showed it.
- **Independent oracles.**
  - A reference model of the business rules. Each item is priced at its price when it was added, and each request
    counts once, retries included.
  - The payment provider's **ledger** instead of the order the engine returns. Found: a wallet charged a stale quote,
    936 of 936 cases where any action followed the shipping choice, and 0 of 312 where the shipping was chosen last.
  - The shop's own order list instead of its confirmation page.
- **Every check as a metric.**
  - The verdict names only the first failing Then. An `@After` hook evaluates every check and adds
    `chk_one_order`, `chk_charge`, `chk_free_shipping` and `chk_total` (1 holds, 0 fails) to the metrics line.
  - That showed 3,455 failed total checks behind the 1,015 `WRONG_TOTAL` verdicts.

### 21.5 Performance observed (8 cores, shared with another agent's work)

| Work | Size | Time |
|---|---|---|
| `validate` + `plan` + `candidates`, t=2 | 544,320 generated, 90,720 mandatory rows | ≈ 30 s |
| `candidates`, t=3 set-cover | 90,720 mandatory rows → 1,688 kept, 6,078 executable | 7 min 15 s |
| Cucumber in the Framework's JVM, 3 workers | 864 scenarios | 47 s |
| Cucumber in the Framework's JVM, 4 workers | 4,128 scenarios | 109 s (≈ 38 per second) |
| Selenium, headless Firefox, 4 workers | 714 scenarios, 10–12 s each (a fresh browser per case) | 27.6 min (≈ 26 per minute) |
| `export` (html, metrics, csv), `triage`, `analyze` | 4,128 samples | seconds each |

Two lessons:
- **Count before you choose where the breadth goes.** Put the combinatorial breadth into fast in-process tests. Keep
  the browser design small, because 7 rows × 102 timelines in a browser cost as much wall time as 4,000 in-process
  scenarios.
- **Covering strength 3 is expensive on large mandatory products.** Prefer moving factors into sudden events, which
  join after the selection and so are covered completely.

### 21.6 Reading the results of a timeline design

- **Expect many failures, and decompose them by timeline.**
  - "3,763 failures" means little until it is grouped by what the timeline did: what the duplicate repeated, whether
    the rise came before or after the add, whether the shipping came before the coupon.
  - Group the metrics corpus (`export --kind metrics`) that way, and filter out the other known defects to isolate one.
  - This turned the run into nine statements like "FREESHIP before the shipping choice: 2,064 / 2,064 fail; after it:
    0 / 2,064".
- **What triage does here.** It names the findings by their carrier sheet and failing step.
  - Its value enrichment (lift) stays near 1.0 for interaction defects, because no single value explains them.
  - Expect that from timeline designs, and use the decomposition above for the causes.
- **Pairwise has blind spots.** A pairwise-only design missed a three-way defect: half-cent rounding needs the bookmark,
  the coupon and the terminal at once.
  - Events made sudden (`FW_Optional`) are crossed with every kept row, so their interactions are covered completely.
  - Put every "may or may not happen" there, not into the matrix.

### 21.7 Working with the human during long runs

- **Long runs go to the background.** Run them as background commands, give a one-line status, and do not poll. Count
  progress by the output files only when it is needed, for example once for an ETA.
- **Corrections arrive while work is running.** Finish the step in flight safely: do not rebuild the classes a run is
  using. Then redesign, and show the new counts before the next heavy run.
- **Some decisions belong to the human:** "no RAM limit", running at all, and the redesign. Each one changed what the
  operator did next, which is the direction the licence asks for (§1).
