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

"""Materialize the BEFORE design; the Framework, not this script, selects cases."""
from pathlib import Path
from collections import Counter
import json, re

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'guif_dogfood'
INITIAL = BASE / 'initial'

def inventory():
    text = (INITIAL/'gui_object_inventory.md').read_text()   # the inventory of the brief's Appendix A, kept as data
    rows, counts, selector_counts = [], Counter(), Counter()
    for line in text.splitlines():
        if not re.match(r'\| [0-9] ·', line): continue
        tab, kind, name, hint = [v.strip() for v in line.strip('|').split('|')]
        tab = int(tab.split()[0])
        slug = re.sub('[^a-z0-9]+', '_', name.lower()).strip('_')[:72] or 'symbol'
        # Symbols must not collapse to the same human identity.
        slug = {'+':'plus','−':'minus','↑':'up','↓':'down'}.get(name, slug)
        key = f't{tab}_{kind.replace(" ","_").replace("-","_")}_{slug}'
        counts[key] += 1
        selector_key=(tab,kind,name)
        selector_counts[selector_key] += 1
        ident = key + (f'_{counts[key]}' if counts[key] > 1 else '')
        rows.append(dict(id=ident, tab=tab, kind=kind, name=name, hint=hint, occurrence=selector_counts[selector_key]-1))
    return rows

INV = inventory()
def ids(tab, start=0, end=None):
    return [r['id'] for r in [r for r in INV if r['tab']==tab][start:end]]

SPECS = [
 ('t1_library',2,dict(source=['valid','empty','malformed'],selection=['none','first'],action=['check','open']),ids(1)),
 ('t2_authoring',2,dict(format=['json','yaml','toml','xlsx'],edit=['normal','duplicate','boundary'],controls=['empty','named']),ids(2)),
 ('t3_runner',2,dict(runner=['generated-java','python'],settings=['valid','badenv','badsandbox'],input=['valid','constraints','settings','results','folder']),ids(3)),
 ('t4_graph',2,dict(rules=['empty','pair','invalid'],operation=['save','undo','import'],scan=['1','100']),ids(4,0,30)),
 ('t4_data',3,dict(draft=['json','data','rule'],switch=['graph','data','json'],resolution=['discard','apply'],order=['before','after']),ids(4,30,48)),
 ('t4_rules',3,dict(body=['pair','formula'],edit=['valid','invalid'],resolution=['apply','discard','undo']),ids(4,48)),
 ('t5_candidates',2,dict(scan=['1','100','bad'],page=['1','5'],executable=['true','false'],control=['finish','cancel']),ids(5)),
 ('t6_run',3,dict(control=['finish','pause','cancel'],timing=['first','later'],export=['report','results','csv','metrics','checkpoint']),ids(6)+['x_run_roundtrip']),
 ('t6_sequences',3,dict(first=['pause','resume','cancel'],second=['pause','resume','cancel'],third=['pause','resume','cancel'],export=['metrics','checkpoint']),ids(6)+['x_run_sequence']),
 ('t7_campaign',2,dict(strategy=['bias','noop'],iterations=['1','2'],control=['finish','pause','cancel']),ids(7)),
 ('t8_analysis',2,dict(corpus=['valid','unverdicted','csv','checkpoint'],coverage=['none','jacoco','coverage-py','gcov'],timing=['single','double']),ids(8)),
 ('t9_scorer',2,dict(text=['normal','empty'],rules=['valid','jsonbad','regexbad'],filter=['all','ERROR','absent'],threshold=['normal','boundary']),ids(9)),
 ('x_dialogs',2,dict(group=['scenario','run','python','other'],place=['empty','example','remembered'],current=['none','missing']),['x_dialog_'+p.lower() for p in ['SCENARIO_OPEN','DOCUMENT_OPEN','DOCUMENT_SAVE','CONSTRAINTS_OPEN','CONSTRAINTS_SAVE','GRAPH_SAVE','EXPORT_REPORT','EXPORT_RESULTS','EXPORT_SAMPLES','EXPORT_METRICS','EXPORT_CHECKPOINT','RESULTS_OPEN','COVERAGE_OPEN','TEXT_OPEN','RULES_OPEN','SCORES_SAVE','LIBRARY_OPEN','ADDON_OPEN','PROGRAM_OPEN','IMPORTS_OPEN','WORKDIR_OPEN']]+['x_trail']),
 ('x_search_data',3,dict(kind=['SCENARIO','LIBRARY','CONSTRAINTS','METRICS','JACOCO','COVERAGE_PY','GCOV'],roots=['likely','duplicate','deep'],content=['positive','misleading','hidden'],budget=['full','cap','stop']),['x_search_match','x_search_roots','x_search_budget','x_search_filter','x_search_selection']),
 ('x_search_tools',3,dict(kind=['TEXT','RULES','ADDON','PYTHON','PYTHON_MODULES','PYTHON_WORKDIR'],roots=['likely','duplicate','deep'],content=['positive','misleading','hidden'],budget=['full','cap','stop']),['x_search_match','x_search_roots','x_search_budget','x_search_filter','x_search_selection']),
 ('x_shell',2,dict(hints=['on','off','f1'],cells=['text','number','boolean'],visits=['forward','reverse']),ids(0)+['x_tabs','x_hints','x_zebra','x_lifecycle']),
]

OPTIONAL = {'t4_data':['undo','validate'], 't5_candidates':['cancel'], 't6_run':['cancel'], 't6_sequences':['cancel','refresh'], 't7_campaign':['cancel']}
def cell(k,v): return f'o.put("{k}","{v}");'

def scenario(name,strength,factors,watchers):
    sheets={
      'HEAD':[['import java.util.*; import org.ghyura.cf.desktop.GuifProbe; public class GuifCase { public static int FW_VAR=0; public static void main(String[] args) throws Exception { Map<String,String> o=new LinkedHashMap<>();']],
    }
    for k,levels in factors.items(): sheets[k]=[[cell(k,v)] for v in levels]
    if name in OPTIONAL: sheets['sudden']=[[cell('sudden',v)] for v in OPTIONAL[name]]
    sheets['act']=[[f'GuifProbe p=GuifProbe.execute("{name}",o); if(p.infrastructureCode()!=0) {{ FW_VAR=p.infrastructureCode(); }}']]
    for ident in watchers: sheets['w_'+ident]=[[f'if (FW_VAR == 0 && p.violated("{ident}")) {{ FW_VAR = FW_EXIT_CODE; }}']]
    sheets['TAIL']=[['p.emit(FW_VAR); } }']]
    seq=[[k,'FW_Combi(1)']+(['FW_Optional'] if k=='sudden' else []) for k in sheets]
    codes=[['0','PASS'],['-1','GUIF_HARNESS_ERROR'],['-2','GUIF_EDT_TIMEOUT'],['-3','GUIF_CLOSE_TIMEOUT'],['-4','GUIF_THREAD_LEAK']]
    for n,k in enumerate(sheets,1):
        if k.startswith('w_'): codes.append([str(n),'GUIF_'+k[2:].upper()+'_INVARIANT'])
    sheets={'FW_Seq':seq,**sheets,'FW_CUSTOM_VAR':codes}
    constraints=[]
    if name=='t3_runner':
        for level in ['badenv','badsandbox']:
            constraints.append(dict(id='python_only_'+level,polarity='forbid',pairs=[{'runner':cell('runner','generated-java'),'settings':cell('settings',level)}]))
    if name=='t1_library':
        for level in ['empty','malformed']:
            constraints.append(dict(id='selection_requires_library_'+level,polarity='forbid',pairs=[{'source':cell('source',level),'selection':cell('selection','first')}]))
    budget=sum(len(v) for k,v in sheets.items() if k not in ('FW_Seq','FW_CUSTOM_VAR'))+len(seq)
    assert budget <= 108, (name,budget)
    for k,values in sheets.items():
        if k in ('FW_Seq','FW_CUSTOM_VAR'): continue
        assert not k.startswith('FW_')
        for row in values:
            assert not row[0].startswith('FW_') and '//' not in row[0]
    return {'sheets':sheets},dict(version=1,constraints=constraints),codes,budget

def main():
    INITIAL.mkdir(exist_ok=True)
    (INITIAL/'inventory.json').write_text(json.dumps(INV,ensure_ascii=False,indent=2)+'\n')
    manifest=[]
    for name,t,factors,watchers in SPECS:
        data,rules,codes,budget=scenario(name,t,factors,watchers)
        stem=INITIAL/'scenarios'/name; stem.parent.mkdir(exist_ok=True)
        stem.with_suffix('.json').write_text(json.dumps(data,indent=2)+'\n')
        stem.with_suffix('.constraints.json').write_text(json.dumps(rules,indent=2)+'\n')
        stem.with_suffix('.properties').write_text(f'''cf.execution.sut=generated-java
cf.execution.workers=1
cf.execution.timeout.millis=120000
cf.execution.classpath=target/guif-dogfood/probe.jar
cf.keys.encoding=SIGNED_BYTE
cf.coverage.strength={t}
cf.coverage.method=set-cover
cf.coverage.budget=0
cf.coverage.hardCap=100000
cf.analysis.goals=elapsed_ms:min,violations:min
cf.analysis.discover=false
cf.plan.autoPromoteSingleVerb=false
''')
        manifest.append(dict(name=name,strength=t,factors=factors,optional=OPTIONAL.get(name,[]),watchers=watchers,codes=codes,label_budget=budget))
    (INITIAL/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    catalog={r['id']:r for r in INV}
    for spec in manifest:
        for wid in spec['watchers']:
            catalog.setdefault(wid,dict(id=wid,tab=-1,kind='logic',name=wid,hint='',occurrence=0))
    (INITIAL/'probe'/'objects.tsv').write_text('\n'.join('\t'.join(str(r[k]) for k in ('id','tab','kind','name','occurrence')) for r in catalog.values())+'\n')
    (INITIAL/'probe'/'groups.tsv').write_text('\n'.join(s['name']+'\t'+','.join(s['watchers']) for s in manifest)+'\n')
    write_design(manifest,catalog)
    print(f'{len(INV)} appendix objects; {len(manifest)} scenarios; max label budget {max(s["label_budget"] for s in manifest)}/127')

def write_design(specs,catalog):
    memberships={k:[] for k in catalog}
    for s in specs:
        for wid in s['watchers']: memberships[wid].append(s['name'])
    lines=['# Object model before execution','',
      'The 206 Appendix A identities are immutable. Duplicate controls are scoped by tab and occurrence in the component tree; unnamed Sieve controls use structural kind/occurrence selectors. Dynamic library and authoring table names are matched by their stable prefix. The catalog also includes cross-cutting logical objects.',
      '', 'P0: cancellation, draft ownership, export integrity and teardown. P1: search classification, runner settings, file guidance, persistence, authoring. P2: selection, descriptions and presentation. A dialog button is exercised through its backing chooser/API in headless mode; showing a native modal is excluded and never claimed as tested.',
      '', '| Object | Tab | Kind / shown name | Risk | Scenarios |','|---|---|---|---|---|']
    for wid,r in catalog.items():
        risk='P0' if r['tab'] in (4,6,7) or wid in ('x_lifecycle','x_run_roundtrip','x_run_sequence') else 'P1' if r['tab'] in (1,2,3,8) or r['tab']==-1 else 'P2'
        lines.append(f'| `{wid}` | {r["tab"]} | {r["kind"]}: {r["name"]} | {risk} | {", ".join(memberships[wid])} |')
    (INITIAL/'00_object_model.md').write_text('\n'.join(lines)+'\n')
    lines=['# Watchers and byte codes before execution','',
      'Each catalog object owns a virtual thread named `guif-watch-<id>` and an unbounded snapshot queue. Listeners register on the EDT. Document, action/item, model/selection, spinner/tab change, hierarchy and property events become immutable snapshots. Only the consumer checks assertions and updates violations. Semantic API results are immutable assertion snapshots routed to the responsible object, not assertions in the generated candidate.',
      '', 'Text watchers check editable-value/selection round trips; button watchers check the enabled action and its completion; choice and spinner watchers check selected values and bounds; table/list/tree watchers check selection and populated results; sub-tab watchers check selection or draft refusal. Scenario-specific checks below strengthen these smoke contracts. Listeners do no blocking work. After detach, poison pills and bounded joins drain all assertions before verdict aggregation. Desktop.close is awaited off the EDT. New cf-desktop and guif-watch threads are checked at teardown.',
      '', 'Code 0 is PASS. Negative codes -1/-2/-3/-4 identify harness exception/EDT timeout/close timeout/thread leak. Positive codes are data-sheet ordinals regenerated with FW_CUSTOM_VAR on every build. Names are scenario-qualified by their artifact, allowing the same number to mean different objects in different scenarios. The first failing watcher decides the verdict; every watcher still emits events, violations and exercise mode, so masking is visible.',
      '', '| Scenario | Code | Name / watcher | Oracle |','|---|---:|---|---|']
    oracle={
      't1_library':'empty-selection guidance; manifest counts; details; sending selected scenario',
      't2_authoring':'sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export',
      't3_runner':'Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning',
      't4_graph':'pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout',
      't4_data':'draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft',
      't4_rules':'rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery',
      't5_candidates':'scan/page boundaries; cancellation; selected source; page progression and executable filtering',
      't6_run':'state transitions; cancellation terminality; all export formats; metrics export → analysis sample count',
      't6_sequences':'ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption',
      't7_campaign':'iterations/lineage; pause/resume/cancel; optional sudden cancellation',
      't8_analysis':'wrong-content refusal; unverdicted count; coverage enablement; repeated analyze',
      't9_scorer':'malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts',
      'x_dialogs':'purpose-specific title/accessory/example/selection and isolated trail',
      'x_search_data':'content classification; duplicate roots; hidden skips; budget and stop; filter/select',
      'x_search_tools':'content classification; duplicate roots; hidden skips; budget and stop; filter/select',
      'x_shell':'nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks'}
    for s in specs:
        for code,name in s['codes']:
            if int(code)>0: lines.append(f'| {s["name"]} | {code} | `{name}` | {oracle[s["name"]]} |')
    (INITIAL/'01_watchers_and_codes.md').write_text('\n'.join(lines)+'\n')
    lines=['# Factors, constraints and sequences before execution','',
      'The Framework generates, constrains and greedily selects rows. No Python covering-array generator is used. Main factors are independent only where that makes semantic sense. Invalid values are deliberate negative inputs; an invalid action is not filtered away when refusal/idempotence is the contract.',
      '', 'Ordered actions are part of the input space. `t6_sequences` has first/second/third action-position factors and t=3, preserving distinctions that an unordered permutation label set could erase. All 27 pause/resume/cancel triples are obligations, including repeated actions and actions after cancellation. Sieve contrasts draft→switch→resolve→switch with draft→resolve→switch, and graph edits cover edit→save→undo→redo. Traces record actual dispatched actions and results. Ordered pair/triple coverage is reported separately from Framework t-wise coverage.',
      '', '`sudden` is a real FW_Optional sheet, not a boolean approximation: omission selects the uninterrupted path. Its values inject cancellation at a committed-sample hook, undo/validate after a draft before the next user action, preview cancellation after dispatch, or campaign cancellation during an iteration. The harness records whether the optional action fired. This distinguishes selection from execution and prevents a race from silently counting as coverage.',
      '', 'Constraints: empty/malformed libraries cannot have a selected row; Python-only invalid settings are excluded with the Java runner. All other negative and repeated actions are meaningful. Constraints compare exact generated cell text. Each scenario has its own companion and explicit properties.',
      '', '| Scenario | t | Main factors / levels | Optional sudden actions | Labels (limit 127) |','|---|---:|---|---|---:|']
    for s in specs:
        factors='; '.join(k+'='+','.join(v) for k,v in s['factors'].items())
        lines.append(f'| {s["name"]} | {s["strength"]} | {factors} | {", ".join(s["optional"]) or "none"} | {s["label_budget"]} |')
    lines += ['', 'Self-review: maximum allocation is kept below 109 labels (at least 15% headroom); no virtual sheets; signed-byte encoding enforced. Control sheets are excluded from code ordinals. Codes never use -128. Every map is regenerated, and validate/plan output is retained. Own names never use FW_ prefixes; Java cells contain no trailing // comments. Metric names are lower-case ASCII and factor values contain no delimiter characters. No mutation of product source, real desktop, real trail, or user Maven repository is authorized. All file fixtures and evidence live under target/guif-dogfood.',
      '', 'Execution gates: build; exactly one starting JUnit baseline; compile harness; one-candidate calibration per partition; count/validate then one batched Framework run per initial scenario; minimal reproducer per confirmed defect; refined scenarios/campaign; exactly one ending JUnit baseline. Harness defects are corrected and labeled, never reported as product failures. Reports distinguish directly activated controls, backing-API coverage, read-only selection, and unexercised exclusions.']
    (INITIAL/'02_factors_constraints.md').write_text('\n'.join(lines)+'\n')

if __name__=='__main__': main()
