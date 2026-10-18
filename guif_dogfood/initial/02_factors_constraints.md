# Factors, constraints and sequences before execution

The Framework generates, constrains and greedily selects rows. No Python covering-array generator is used. Main factors are independent only where that makes semantic sense. Invalid values are deliberate negative inputs; an invalid action is not filtered away when refusal/idempotence is the contract.

Ordered actions are part of the input space. `t6_sequences` has first/second/third action-position factors and t=3, preserving distinctions that an unordered permutation label set could erase. All 27 pause/resume/cancel triples are obligations, including repeated actions and actions after cancellation. Sieve contrasts draft→switch→resolve→switch with draft→resolve→switch, and graph edits cover edit→save→undo→redo. Traces record actual dispatched actions and results. Ordered pair/triple coverage is reported separately from Framework t-wise coverage.

`sudden` is a real FW_Optional sheet, not a boolean approximation: omission selects the uninterrupted path. Its values inject cancellation at a committed-sample hook, undo/validate after a draft before the next user action, preview cancellation after dispatch, or campaign cancellation during an iteration. The harness records whether the optional action fired. This distinguishes selection from execution and prevents a race from silently counting as coverage.

Constraints: empty/malformed libraries cannot have a selected row; Python-only invalid settings are excluded with the Java runner. All other negative and repeated actions are meaningful. Constraints compare exact generated cell text. Each scenario has its own companion and explicit properties.

| Scenario | t | Main factors / levels | Optional sudden actions | Labels (limit 127) |
|---|---:|---|---|---:|
| t1_library | 2 | source=valid,empty,malformed; selection=none,first; action=check,open | none | 36 |
| t2_authoring | 2 | format=json,yaml,toml,xlsx; edit=normal,duplicate,boundary; controls=empty,named | none | 80 |
| t3_runner | 2 | runner=generated-java,python; settings=valid,badenv,badsandbox; input=valid,constraints,settings,results,folder | none | 67 |
| t4_graph | 2 | rules=empty,pair,invalid; operation=save,undo,import; scan=1,100 | none | 77 |
| t4_data | 3 | draft=json,data,rule; switch=graph,data,json; resolution=discard,apply; order=before,after | undo, validate | 59 |
| t4_rules | 3 | body=pair,formula; edit=valid,invalid; resolution=apply,discard,undo | none | 90 |
| t5_candidates | 2 | scan=1,100,bad; page=1,5; executable=true,false; control=finish,cancel | cancel | 41 |
| t6_run | 3 | control=finish,pause,cancel; timing=first,later; export=report,results,csv,metrics,checkpoint | cancel | 41 |
| t6_sequences | 3 | first=pause,resume,cancel; second=pause,resume,cancel; third=pause,resume,cancel; export=metrics,checkpoint | cancel, refresh | 44 |
| t7_campaign | 2 | strategy=bias,noop; iterations=1,2; control=finish,pause,cancel | cancel | 30 |
| t8_analysis | 2 | corpus=valid,unverdicted,csv,checkpoint; coverage=none,jacoco,coverage-py,gcov; timing=single,double | none | 41 |
| t9_scorer | 2 | text=normal,empty; rules=valid,jsonbad,regexbad; filter=all,ERROR,absent; threshold=normal,boundary | none | 58 |
| x_dialogs | 2 | group=scenario,run,python,other; place=empty,example,remembered; current=none,missing | none | 62 |
| x_search_data | 3 | kind=SCENARIO,LIBRARY,CONSTRAINTS,METRICS,JACOCO,COVERAGE_PY,GCOV; roots=likely,duplicate,deep; content=positive,misleading,hidden; budget=full,cap,stop | none | 36 |
| x_search_tools | 3 | kind=TEXT,RULES,ADDON,PYTHON,PYTHON_MODULES,PYTHON_WORKDIR; roots=likely,duplicate,deep; content=positive,misleading,hidden; budget=full,cap,stop | none | 35 |
| x_shell | 2 | hints=on,off,f1; cells=text,number,boolean; visits=forward,reverse | none | 27 |

Self-review: maximum allocation is kept below 109 labels (at least 15% headroom); no virtual sheets; signed-byte encoding enforced. Control sheets are excluded from code ordinals. Codes never use -128. Every map is regenerated, and validate/plan output is retained. Own names never use FW_ prefixes; Java cells contain no trailing // comments. Metric names are lower-case ASCII and factor values contain no delimiter characters. No mutation of product source, real desktop, real trail, or user Maven repository is authorized. All file fixtures and evidence live under target/guif-dogfood.

Execution gates: build; exactly one starting JUnit baseline; compile harness; one-candidate calibration per partition; count/validate then one batched Framework run per initial scenario; minimal reproducer per confirmed defect; refined scenarios/campaign; exactly one ending JUnit baseline. Harness defects are corrected and labeled, never reported as product failures. Reports distinguish directly activated controls, backing-API coverage, read-only selection, and unexercised exclusions.
