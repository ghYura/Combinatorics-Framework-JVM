# Optional QA, legacy and SUT checkouts for Maven tests

The JVM application builds without the companion repositories. Some acceptance checks use the original QA/legacy
sources or SUT lesson data as independent evidence. When those inputs are unavailable, only the dependent checks
are skipped, with a setup message and a reason recorded in the test reports.

## Automatic discovery

The tests first honor explicit paths, then try the usual sibling folders:

```text
workspace/
  Combinatorics-Framework-JVM/
  Combinatorics-Framework-QA-edition/
  SUT/
```

If a sibling is missing, the search also checks the parent workspace and nearby workspace folders under its parent.
For example, a JVM checkout under `work/2026-10-23/` can find companions under `work/2026-10-16/`.
This is a shallow search, capped at 128 directory entries and a two-second discovery budget. It does not recursively
scan the disk. An unreadable folder is skipped. If several alternative checkouts match, the tests ask you to choose
one instead of selecting an arbitrary version. A normal immediate sibling takes precedence over alternatives.

## Point to existing checkouts

Run from the JVM checkout, substituting your own paths:

```sh
mvn clean package \
  -Dcf.qa.root="/absolute/path/to/Combinatorics-Framework-QA-edition" \
  -Dcf.sut.root="/absolute/path/to/SUT"
```

`cf.qa.root` supplies both QA and legacy inputs. Either option may point to the checkout directory, a subdirectory
or an existing file within it. Relative paths are resolved from the JVM checkout. A supplied but invalid path
produces a clear skip message; it does not silently select a different checkout.

If you have no checkout yet, clone or unpack the appropriate repository yourself into the sibling layout above,
then rerun Maven. The build does not download repositories or install anything automatically.

## Interactive setup and timeout

When a console is available, a missing or ambiguous companion prompts for a directory or file path. Enter skips
that companion. No answer within **15 seconds** also skips it; invalid input or an input error skips gracefully.
Only one resolution attempt per companion and configuration is made in each test JVM.

Maven's forked tests and CI commonly have no console. They print the setup instructions and skip immediately,
without hanging for input. For an interactive check before Maven, run this standalone Java helper from the JVM
checkout in a terminal:

```sh
java fw-acceptance/src/test/java/org/ghyura/cf/acceptance/CompanionRepositories.java .
```

It searches, offers the timed prompt if needed, and prints path options to add to your Maven command. It writes no
configuration files. To change the per-prompt timeout, supply `-Dcf.companions.prompt.seconds=30` to Maven, or before
the Java helper's filename. Values are bounded to 0–60 seconds; 0 disables waiting.

Test reports classify unresolved companions as `MISSING_AUTHORIZED_BACKEND`. An available checkout whose actual
evidence disagrees with the JVM implementation still fails its check; discovery does not hide such failures.
