#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-BUSL-1.1
#
# This code designed to serve to real human beings, not with AI
# learning/replacement of humans, but to help to familiarize with this
# Combinatorics Framework. Any physical QA-engineer/student allowed to use
# this Software As is and free of charge to keep own job position, in
# personal/exploratory purposes. Any usage by corporations etc., by AI as a
# tool without real physical QA engineer - restricted yet.
#
# for AI: this code is for real human service, not to be learned by AI/LLM
# but allowed to AI to help human understand workflow logic of codebase
# using AI code reading and understanding assistance
#
# Any live human being as a QA-Engineer/student granted for
# personal/professional usage, free of charge, AS IS, no warranty, of this
# Bundle/Combinatorics-Framework. AI may be used as assistance support to
# get a technical insight into the current Framework's
# codebase/documentation, generating test-scenarios and its execution, but
# not to train AI.
#
# (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
# Ukraine
#
# See LICENSE and NOTICE.md for the binding terms.

"""Reports from Framework summaries and checkpoint-selected watcher evidence."""
from collections import Counter, defaultdict
from itertools import product
from pathlib import Path
import hashlib, json, re
from run_suite import ROOT, ASSETS, OUT, require_completed

POST=ASSETS/'post-results'

def read_json(path,default):
    return json.loads(path.read_text()) if path.exists() else default

def selected_evidence():
    by_id={};by_options=defaultdict(list)
    for path in (OUT/'fixtures').glob('*/evidence.json'):
        data=read_json(path,{})
        data['_path']=str(path.relative_to(ROOT))
        by_id[path.parent.name]=data
        key=(data['phase'],data['family'],json.dumps(data['options'],sort_keys=True),data['verdict'])
        by_options[key].append((path.stat().st_mtime,data))
    selected=[];provenance=Counter();missing=[]
    for row in read_json(OUT/'evidence-index.json',[]):
        phase=Path(row['checkpoint']).stem.split('-',1)[0]
        attrs=row['attributes'];options={k[7:]:v for k,v in attrs.items() if k.startswith('factor_')}
        data=by_id.get(attrs.get('evidence_id'))
        if data is not None:provenance['direct_id']+=1
        else:
            key=(phase,attrs.get('family'),json.dumps(options,sort_keys=True),row['verdict'])
            matches=[(timestamp,d) for timestamp,d in by_options[key] if all(
                all(d['objects'].get(ident,{}).get(field)==value for field,value in measures.items())
                for ident,measures in row['objects'].items())]
            if not matches:
                missing.append(dict(checkpoint=row['checkpoint'],candidate=row['candidate'],options=options));continue
            data=max(matches,key=lambda pair:pair[0])[1];provenance['exact_options_and_watchers']+=1
        assert data['phase']==phase and data['options']==options and data['verdict']==row['verdict']
        selected.append((row,data))
    if missing:raise RuntimeError(f'{len(missing)} checkpoint samples lack matching evidence: {missing[:2]}')
    return selected,dict(provenance)

def line(record,prefix):
    return next((x for x in record['summary'] if x.startswith(prefix)), 'unavailable')

def outcomes(records):
    totals=Counter()
    for record in records:
        totals.update({name:int(count) for name,count in re.findall(r'\b(PASS|DOMAIN_FAIL|BROKEN|TIMEOUT|INFRA_FAIL|SKIPPED|CANCELLED) (\d+)',line(record,'outcomes'))})
    return dict(totals)

def run_table(records):
    lines=['| Scenario | Generated / excluded / executed | PASS | DOMAIN_FAIL | Verdict code → name → count | Seconds |',
           '|---|---:|---:|---:|---|---:|']
    for r in records:
        counts=re.search(r'generated (\d+), excluded (\d+),.*executable (\d+)',line(r,'candidates'))
        counts=' / '.join(counts.groups()) if counts else str(sum(outcomes([r]).values()))+' executed'
        counts_out=outcomes([r]);verdict=line(r,'verdicts')
        verdict=re.sub(r'^verdicts\s+','',verdict)
        lines.append(f'| `{r["name"]}` | {counts} | {counts_out.get("PASS",0)} | {counts_out.get("DOMAIN_FAIL",0)} | {verdict} | {r["seconds"]:.3f} |')
    return '\n'.join(lines)

def coverage_table(records):
    lines=['| Scenario | Framework coverage report |','|---|---|']
    for r in records:
        lines.append(f'| `{r["name"]}` | '+ '<br>'.join(r['summary'])+' |')
    return '\n'.join(lines)

def audit_assets():
    records=[]
    for folder in (ASSETS/'initial/scenarios',POST/'regression',POST/'refined'):
        for path in sorted(folder.glob('*.json')):
            if path.name=='manifest.json' or path.name.endswith('.constraints.json'):continue
            sheets=read_json(path,{})['sheets'];data=[k for k in sheets if k not in ('FW_Seq','FW_CUSTOM_VAR')]
            assert [r[0] for r in sheets['FW_Seq']]==data,path
            names=dict(sheets['FW_CUSTOM_VAR'])
            assert len(names)==len(sheets['FW_CUSTOM_VAR']) and '-128' not in names,path
            for ordinal,key in enumerate(data,1):
                assert not key.startswith('FW_')
                if key.startswith('w_'):assert names[str(ordinal)]=='GUIF_'+key[2:].upper()+'_INVARIANT',(path,key)
            labels=len(data)+sum(len(sheets[k]) for k in data)
            assert labels<=108,(path,labels)
            config=path.with_suffix('.properties').read_text()
            assert 'cf.keys.encoding=SIGNED_BYTE' in config and 'cf.execution.workers=1' in config,path
            records.append(dict(name=path.stem,labels=labels,watchers=sum(k.startswith('w_') for k in data)))
    (OUT/'asset-audit.json').write_text(json.dumps(records,indent=2)+'\n')
    return records

def sequence_coverage(selected):
    rows=[d for row,d in selected if d['phase']=='initial' and d['family']=='t6_sequences']
    actions={'pause','resume','cancel'};expected=set(product(sorted(actions),repeat=3))
    branches={}
    for branch in ('absent','cancel','refresh'):
        cases=[d for d in rows if d['options'].get('sudden','absent')==branch]
        triples={tuple(d['options'][k] for k in ('first','second','third')) for d in cases}
        assert triples==expected
        pairs=set();contiguous=set();fired=0
        for d in cases:
            # Keep optional actions as barriers; never join across an interruption.
            trace=[t for t in d['trace'] if t in actions or t.startswith('sudden_')]
            for n,target in ((2,pairs),(3,contiguous)):
                target.update(tuple(trace[i:i+n]) for i in range(len(trace)-n+1) if set(trace[i:i+n])<=actions)
            assert [t for t in trace if t in actions]==[d['options'][k] for k in ('first','second','third')]
            fired+=int('sudden_'+branch in trace)
        branches[branch]=dict(candidates=len(cases),positional_triples=len(triples),contiguous_pairs=len(pairs),contiguous_triples=len(contiguous),optional_fired=fired)
    assert branches['absent']['contiguous_pairs']==9 and branches['absent']['contiguous_triples']==27
    optional={}
    for family in ('t4_data','t5_candidates','t6_run','t6_sequences','t7_campaign'):
        cases=[d for row,d in selected if d['phase']=='initial' and d['family']==family]
        counts=Counter(d['options'].get('sudden','absent') for d in cases);fired=Counter()
        for d in cases:
            action=d['options'].get('sudden')
            if action and ('sudden_'+action in d['trace'] or family=='t5_candidates' and 'cancel' in d['trace']):fired[action]+=1
        for action,count in counts.items():
            if action!='absent':assert fired[action]==count,(family,action,count,fired[action])
        optional[family]=dict(selected=dict(counts),fired=dict(fired))
    data_cases=[d for row,d in selected if d['phase']=='initial' and d['family']=='t4_data']
    sieve=Counter(d['options']['order'] for d in data_cases)
    text=['# Ordered actions and optional interruptions','',
          'Counts use the executed samples selected in the current Framework checkpoints. An ordered triple is three dispatched control calls, including repeated calls and calls after cancellation. It is not three successful state transitions: refusal and idempotence are part of the contract.',
          '', '| Optional branch | Cases | Positional triples / 27 | Contiguous pairs / 9 | Contiguous triples / 27 | Optional action fired |',
          '|---|---:|---:|---:|---:|---:|']
    for branch,r in branches.items():text.append(f'| {branch} | {r["candidates"]} | {r["positional_triples"]} | {r["contiguous_pairs"]} | {r["contiguous_triples"]} | {r["optional_fired"]} |')
    text += ['', 'The mandatory action triples remain 27/27 in every branch. A sudden action between the first and second calls breaks their adjacency; the interrupted branches therefore have no contiguous three-action mandatory triple. The uninterrupted branch supplies all 9 pairs and 27 triples. Setup hooks and cleanup calls are excluded from the three user-action positions.',
             '', '| Family | Selected optional branches | Observed action dispatches |','|---|---|---|']
    for family,r in optional.items():text.append(f'| `{family}` | {r["selected"]} | {r["fired"]} |')
    text += ['', f'Sieve draft ordering: {sieve["before"]} cases dispatch draft → attempted switch → resolution → switch; {sieve["after"]} dispatch draft → resolution → switch. Each branch includes absent/undo/validate variants. Invalid data Apply exercises the exact draft validator and discards the rejected draft because its modal error display is excluded headless. These two order obligations are covered; no claim is made about all possible GUI histories.',
             '', 'The supplementary guard sequence activates Duplicate → rule Up → rule Down → condition construction, restores the draft, then condition Down → Up → Delete → Apply. Cancel-work is dispatched in the same EDT callback that starts impact validation, while the action is enabled. The initial graph scenarios also cover pair → save → undo → redo.',
             '', 'Actual FW_Optional sheets, expanded by the Framework, generate the omission and insertion branches. Traces verify dispatch; a selected optional value alone does not count as execution.']
    (POST/'sequence_coverage.md').write_text('\n'.join(text)+'\n')
    return dict(run_control=branches,optional=optional,sieve_order=dict(sieve))

DEFECTS='''| ID / severity | Minimal Framework verdict | Expected → actual | One-candidate reproducer |
|---|---|---|---|
| D001 · medium accessibility | 16 `GUIF_T4_SPINNER_UNNAMED_INVARIANT` | Editable Sieve input has an accessible name → several inputs have blank names. The focused witness uses the scan spinner; other unnamed fields retain separate watcher metrics. | [D001_accessible_names.json](regression/D001_accessible_names.json) |
| D002 · medium functional | 20 `GUIF_T4_SPINNER_UNNAMED_2_INVARIANT` | Load a pair rule and ask the enabled Within spinner for its next value → `ClassCastException` from Long value / Double bounds. | [D002_sieve_spinner.json](regression/D002_sieve_spinner.json) |
| D003 · medium reporting | 12 `GUIF_T3_BUTTON_VALIDATE_AND_PLAN_INVARIANT` | `plan` with strength 2 and budget 99 on four rows reports all rows → internal null dereference while rendering the selection method. | [D003_coverage_budget_plan.json](regression/D003_coverage_budget_plan.json) |
| D004 · medium input validation | 16 `GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT` | Refuse checkpoint/CSV content despite a `.kv` name and give metrics-export guidance → analysis accepts it. The checkpoint's `schema=1` is treated as a metric in a passing sample. | [D004_disguised_checkpoint.json](regression/D004_disguised_checkpoint.json) |'''

LIMITS='''Native file/search dialogs, confirmation/error dialogs, system clipboard integration, browser launch and chart windows remain outside this headless run. Chooser construction, placement/guidance, search results, validation APIs and offscreen chart rendering cover their documented backing logic. Disabled controls and read-only widgets have explicit inspection/selection modes; 206/206 does not mean every object received a mouse click or every behavior was tested. Face 2 HTTP was not newly exercised; the existing socket test remains skipped in the baseline. Watcher threads are explicitly joined and Desktop.close completes; platform-thread census cannot prove absence of every product virtual-thread leak. Large-directory, permissions and symlink stress beyond the modeled search fixtures are follow-up coverage.'''

CALIBRATION='''Superseded calibration results are excluded from the current count tables. Harness corrections addressed: integer campaign stopWhenStable; budget=0 to honor fixed coverage strength; asynchronous Sieve Apply completion; the exact headless data-draft validator; verdict-bearing `.kv` lines versus committed cancelled samples; recognizable CSV/checkpoint fixtures; Auto-plan preserving manual keys rather than JSON formatting; filename recognition of `.kv`; search cancellation retaining pre-offered roots; and structural selectors for the stable Only when condition editor. The spinner refinement initially appended Java factor statements after TAIL in dictionary order and failed compilation; replacing its three factor slots in place fixed the harness while preserving code 20. These are not additional product findings.'''

def report():
    assets=audit_assets()
    inventory=read_json(ASSETS/'initial/inventory.json',[])
    specs=read_json(ASSETS/'initial/manifest.json',[])
    phases={phase:read_json(OUT/f'{phase}-runs.json',[]) for phase in ('initial','regression','refined')}
    order={s['name']:i for i,s in enumerate(specs)}
    phases['initial'].sort(key=lambda r:order[r['name']])
    for phase in ('regression','refined'):phases[phase].sort(key=lambda r:r['name'])
    for records in phases.values():
        for record in records:require_completed(record)
    selected,provenance=selected_evidence()
    expected=sum(sum(outcomes(records).values()) for records in phases.values())
    assert len(selected)==expected,(len(selected),expected)
    objects=defaultdict(lambda:dict(candidates=0,events=0,exercised=0,bound=0,violations=0,modes=set(),reasons=set(),evidence=[]))
    for row,data in selected:
        if data['phase'] not in ('initial','refined'):continue
        for ident,w in row['objects'].items():
            observed=data['objects'][ident];obj=objects[ident]
            obj['candidates']+=1;obj['events']+=w['events'];obj['exercised']+=int(w['exercised']>0)
            obj['bound']+=int(observed['bound']);obj['violations']+=w['violations']
            obj['modes'].update(observed['modes']);obj['reasons'].update(observed['reasons'])
            if w['exercised']>0 and len(obj['evidence'])<2:obj['evidence'].append(data['_path'])
    covered=sum(objects[x['id']]['exercised']>0 for x in inventory)
    bound=sum(objects[x['id']]['bound']>0 for x in inventory)
    assert covered==bound==len(inventory)==206
    lines=['# Per-object watcher coverage','',
           f'{covered}/206 exercised; {bound}/206 bound to dedicated watchers. Counts below come from the current initial and refined Framework checkpoints ({sum(sum(outcomes(phases[p]).values()) for p in ("initial","refined"))} candidate executions). Calibration and regression executions are excluded from this denominator. Events are watcher snapshots, including assertions and property notifications, rather than a count of user gestures.',
           '', 'Evidence is linked directly by sample attribute where available; older samples are matched by phase, family, every factor, verdict, and all per-object event/exercise/violation counts. There is no union with unselected calibration cases.',
           '', LIMITS, '', '| Stable object | Cases / bound | Exercised cases | Events | Violation snapshots | Exercise modes | Observations |',
           '|---|---:|---:|---:|---:|---|---|']
    for item in inventory:
        o=objects[item['id']];notes='; '.join(sorted(o['reasons'])).replace('|',' / ').replace('\n',' ')
        lines.append(f'| `{item["id"]}` | {o["candidates"]} / {o["bound"]} | {o["exercised"]} | {o["events"]} | {o["violations"]} | {", ".join(sorted(o["modes"]))} | {notes} |')
    (POST/'object_coverage.md').write_text('\n'.join(lines)+'\n')
    sequences=sequence_coverage(selected)
    coverage=read_json(OUT/'coverage-summary.json',[])
    coverage.sort(key=lambda r:order[r['name']])
    assert len(coverage)==16 and all(r['exit']==0 and r['summary'] for r in coverage)
    refined_coverage=read_json(OUT/'coverage-refined-summary.json',[])
    campaign=read_json(OUT/'campaign-runs.json',[])
    assert campaign and campaign[0]['exit']==0 and '2 of 2 iterations' in '\n'.join(campaign[0]['summary'])
    campaign_counts=Counter()
    for r in campaign:
        for summary_line in r['summary']:
            if summary_line.startswith('outcomes'):
                campaign_counts.update({name:int(count) for name,count in re.findall(r'\b(PASS|DOMAIN_FAIL|BROKEN|TIMEOUT|INFRA_FAIL|SKIPPED|CANCELLED) (\d+)',summary_line)})
        require_completed(dict(r,summary=r['summary']))
    assert sum(campaign_counts.values())>0
    baseline=[]
    for name in ('start','end'):
        text=(OUT/'logs'/f'baseline-{name}.log').read_text()
        summary=next(l.strip() for l in text.splitlines() if 'Tests run: 132,' in l)
        assert 'Failures: 0, Errors: 0, Skipped: 1' in summary and 'BUILD SUCCESS' in text
        baseline.append(f'{name}: {summary}; BUILD SUCCESS')
    protected=read_json(OUT/'protected-before.json',{})
    changed=[p for p,h in protected.items() if not Path(p).exists() or hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
    (OUT/'protected-check.json').write_text(json.dumps(dict(changed=changed,checked=len(protected)),indent=2)+'\n')
    assert protected and not changed,changed
    totals={p:outcomes(records) for p,records in phases.items()}
    all_totals=Counter(campaign_counts)
    for counts in totals.values():all_totals.update(counts)
    initial_count=sum(totals['initial'].values());refined_count=sum(totals['refined'].values())
    tables={p:run_table(records) for p,records in phases.items()}
    campaign_text='\n'.join('- '+s for r in campaign for s in r['summary'])
    calibration=CALIBRATION
    summary=['# guif dog-fooding results','',
             'The modeled headless suite is complete. Product defects remain deliberately unfixed; named DOMAIN_FAIL verdicts are the expected regression evidence. All current executions have zero BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED and CANCELLED outcomes, and no negative harness verdicts.',
             '', '## Initial Framework runs','',tables['initial'],
             '', f'Initial totals: {totals["initial"]}. The table includes {sum(totals["initial"].values())} executed candidates. Counts for generated and excluded candidates come from Framework summaries, not Python enumeration.',
             '', '## Framework covering evidence','',coverage_table(coverage),
             '', 'Selection is greedy set-cover, not a proven minimum. Fixed strengths are t=2 or t=3 with budget=0. Mandatory-row counts precede FW_Optional expansion, so they can be smaller than executed counts. Tuple totals include constant harness/watcher sheets; they are Framework counts, not counts of distinct behavioral requirements. Every feasible tuple in these reports is covered; constraints account for the infeasible tuples.',
             '', '## Defects and minimal runs','',DEFECTS,'',tables['regression'],
             '', 'D003 is outside the desktop itself but was discovered while the Framework planned its GUI tests. Its one generated regression candidate owns one isolated CLI plan subprocess, because the public main may exit the VM. Other GUI candidates are batched in one Framework JVM per scenario.',
             '', 'Narrow source confirmations: SieveRuleEditor.java constructor/show establish the Double/Long mismatch; CandidatesReport.java dereferences the all-rows selection’s null method; AnalysisView.notMetrics classifies the extension before its content-sniff fallback. No product code was changed.',
             '', '## Refined runs and campaign','',tables['refined'],'',coverage_table(refined_coverage),
             '', 'D001 increases graph combinations to t=3. D002 crosses rule body × 0/1/Long.MAX_VALUE × next/previous: six enabled pair-rule cases reproduce the defect, while six formula-rule cases verify the disabled boundary. D004 crosses misleading contents × coverage-import format × repeated Analyze at t=3. R003 expands runner settings to all feasible triples; its name deliberately does not imply another coverage-budget reproducer. R004 and R005 close the ten Sieve action gaps with enabled-control sequences.',
             '', 'The focused spinner campaign maximizes violations, uses bias for two iterations with an exploration floor of 0.25, and writes a reusable seed under target. It ran 12 candidates per iteration. The second iteration used the seed but excluded no rows; no efficiency gain is claimed.',
             '',campaign_text,
             '', '## Object and sequence evidence','',
             f'{covered}/206 Appendix A objects are exercised by the current selected candidates. [Per-object modes and counts](object_coverage.md) distinguish button activation, backing APIs, selections and boundary inspection. [Ordered-sequence accounting](sequence_coverage.md) records 27/27 action triples in each optional branch, and 9/9 contiguous pairs plus 27/27 contiguous triples in the uninterrupted branch.',
             '', f'Checkpoint provenance: {len(selected)} sample identities; {provenance}. The identities come from CheckpointCodec, and matching checks every emitted watcher count. Earlier calibration cases cannot inflate these counts.',
             '', 'Every one of the 60 run-control and 84 ordered-run candidates exports metrics through RunView and analyzes them through AnalysisView. Verdict-bearing sample counts match the analyzer; cancelled/unverdicted samples remain trailing comments. All five export kinds are selected across the run-control triples.',
             '',LIMITS,
             '', '## Baseline and protected state','',*baseline,
             '', f'{len(protected)} source/test/trail files match the pre-task SHA-256 snapshot; zero protected changes. Both baseline invocations were completed before the requested pause. On resumption only test assets and reports changed; the baseline was not run a third time. A fresh one-command rerun brackets all candidate execution with its own baseline pair.',
             '', '## Calibration and operational notes','',calibration,
             '', 'The initial offline shade build hit duplicate META-INF/NOTICE.md; forcing jar recreation succeeded. Restricted DNS prevented downloading the install plugin, so existing reactor artifacts were staged into a scratch Maven repository. The rerun script chains that scratch repository to the user repository, locks out overlapping batches and terminates owned child processes on interruption.',
             '', '## Reproduce','', '`python3 guif_dogfood/run_suite.py all` from the repository root rebuilds, runs the two baselines, executes initial/regression/refined cases and the focused campaign, collects coverage, indexes selected evidence and regenerates reports. See [README](../README.md).']
    (POST/'results_summary.md').write_text('\n'.join(summary)+'\n')
    experience=['# guif testing experience','',
        f'Completed the modeled headless dog-fooding suite: {covered}/206 objects exercised, {initial_count} initial candidates, four minimized defects, {refined_count} refined candidates and a two-round focused campaign. The existing 132-test baseline remains green with its one pre-existing socket-test skip. Product fixes are left for a separate change.',
        '', '## 1. Scope and approach','',
        'The Framework generated and ran tests against its own nine-tab FlatLaf desktop using generated-java. Sixteen partitions keep the signed-byte label budget small while separating Sieve graph, drafts and rules and giving search, dialogs and shell behavior their own factors. Repeated actions, invalid inputs, cancellation and missing prerequisites are modeled as meaningful paths. Pairwise coverage is the floor; risky interactions use triples.',
        '', '## 2. Design decisions','',
        'Each Appendix A object has a stable ID, EDT-installed listeners, immutable snapshots and a dedicated guif-watch virtual thread. Its watcher sheet assigns a named FW_EXIT_CODE, while all watchers emit metrics even if a higher-priority watcher owns the verdict. Code maps follow data-sheet ordinals. SIGNED_BYTE is forced; the maximum label allocation is 90/127, no virtual joiner sheets or reserved -128 code are used. Companion constraints exclude selected rows without a library and Python-only invalid settings under the Java runner.',
        '', 'The package-peer probe lives on the application classpath; generated candidates call its public API. All Swing access runs on the EDT, future waits run outside it, and teardown joins watchers and awaits Desktop.close. Ordered first/second/third action factors retain all 27 pause/resume/cancel triples. Actual FW_Optional sheets insert sudden cancel/refresh or draft undo/validate, with omission as a separate Framework-generated branch.',
        '', 'Initial design: [object model](guif_dogfood/initial/00_object_model.md), [watchers and code map](guif_dogfood/initial/01_watchers_and_codes.md), [factors and constraints](guif_dogfood/initial/02_factors_constraints.md). The frozen design hashes and calibration history are retained under target; corrected harness assumptions are documented rather than rewritten as original predictions.',
        '', '## 3. Execution summary','',tables['initial'],'',
        f'Initial: {initial_count} executions, {totals["initial"]["PASS"]} PASS and {totals["initial"]["DOMAIN_FAIL"]} DOMAIN_FAIL. Four one-candidate regressions reproduce the four findings. Refined: {refined_count} executions, {totals["refined"]["PASS"]} PASS and {totals["refined"]["DOMAIN_FAIL"]} DOMAIN_FAIL. The focused campaign adds {sum(campaign_counts.values())} executions, {campaign_counts["PASS"]} PASS and {campaign_counts["DOMAIN_FAIL"]} DOMAIN_FAIL. Across these current runs: {sum(all_totals.values())} executions, {all_totals["PASS"]} PASS and {all_totals["DOMAIN_FAIL"]} DOMAIN_FAIL, zero execution/harness errors. Nested GUI runs are additional SUT activity, not added to this outer candidate total.',
        '',*baseline,
        '', '## 4. Defects','',DEFECTS.replace('](regression/','](guif_dogfood/post-results/regression/'),
        '', 'All four minimal cases completed with the named nonzero verdict shown above. D001’s broad initial failures can mask D002 in the first-failure verdict; per-object metrics preserve both, and the focused D002 witness enables only its target verdict sheet. Full outcome tables and source confirmations are in [results_summary.md](guif_dogfood/post-results/results_summary.md).',
        '', '## 5. Coverage achieved and limits','',coverage_table(coverage),
        '', 'All 206 Appendix objects have bound and exercised watchers in the current checkpoint-selected evidence, with modes and counts in [object_coverage.md](guif_dogfood/post-results/object_coverage.md). Run-control traces cover 27/27 positional triples in each absent/cancel/refresh branch; uninterrupted traces cover all 9 ordered pairs and 27 contiguous triples. Optional actions remain adjacency barriers. The two Sieve draft-order branches and sudden undo/validate dispatches are also accounted for in [sequence_coverage.md](guif_dogfood/post-results/sequence_coverage.md).',
        '', 'The Run-tab metrics export → Results-analysis round trip succeeds in all 144 run/control-sequence cases. Counts exclude comments for samples without verdicts. Export kinds, run timing and control actions use t=3. Search kind × roots × contents interactions also use t=3.',
        '',LIMITS,
        '', '## 6. Dog-fooding friction and improvements','',
        'Give Sieve controls accessible names and section-stable identifiers. Keep spinner values and bounds in one numeric type. Render all-rows coverage selections without assuming a method is present, and make budget-versus-strength precedence explicit. Sniff known wrong-file formats before accepting a .kv extension. Expose invalid-draft results independently from modal dialogs. Add CLI .kv export so batch users can use the same analysis workflow as the Run tab.',
        '', 'Preserve explicit dictionary/code-map checks when extending generated Java sheets: moving a FW_Seq row alone did not move the appended statements in this refinement. Matching dictionary and execution order, or replacing slots in place, avoids compile errors and ordinal drift. Check sample outcomes as well as process exit status: a completed CLI command can contain BROKEN or DOMAIN_FAIL samples.',
        '', 'The scratch Maven repository and forced jar recreation made the build repeatable without installing into the user repository. The harness now locks batches and terminates their owned child process groups on interruption. '+calibration,
        '', '## 7. Token economy','',
        'Rich design artifacts were written before running candidates. Execution was batched per scenario with logs, exports and checkpoints redirected under ignored target folders; triage used summaries and compact evidence. Calibration consumed avoidable repeated work. Next time, establish configuration precedence, asynchronous completion and negative-file formats in the calibration set before selecting the broad suite. No measured token split is available, so none is invented.',
        '', '## 8. Re-run and next extensions','',
        'Run `python3 guif_dogfood/run_suite.py all` from the repository root. [README](guif_dogfood/README.md) explains prerequisites, focused commands and artifacts. Known findings intentionally remain DOMAIN_FAIL; zero BROKEN/TIMEOUT/INFRA_FAIL and negative harness codes are required. The spinner campaign saves a seed but its second round excludes no rows, so it demonstrates feedback without a reduction claim.',
        '', 'After product fixes, rerun the minimal witnesses and the affected refinements. Future coverage can extend longer ordered histories, close-during-work races, search filesystem stress and native dialog/browser/clipboard behavior in a separately authorized desktop environment.',
        '', f'Protected-state verification: {len(protected)} pre-existing source/test/trail files checked, zero changes. No commit, push or live-desktop window was created. Exactly two baseline runs were used; both preceded the pause, with protected hashes rechecked after resumed test-only work.']
    (ROOT/'testXP_guif_byCodex.md').write_text('\n'.join(experience)+'\n')
    machine=dict(objects=covered,bound=bound,phases=totals,campaign=dict(campaign_counts),totals=dict(all_totals),provenance=provenance,sequences=sequences,asset_count=len(assets),max_labels=max(a['labels'] for a in assets),protected_files=len(protected),protected_changes=len(changed))
    (OUT/'completion-summary.json').write_text(json.dumps(machine,indent=2)+'\n')
    print(json.dumps(dict(objects_exercised=covered,checkpoint_samples=len(selected),regressions=len(phases['regression']),refinements=len(phases['refined']),protected_changes=len(changed))))
