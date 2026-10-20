#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-BUSL-1.1
# Test assets for the human QA engineer. See LICENSE and NOTICE.md.
"""Read checkpoint-selected evidence only; never execute tests. Writes the ledgers report.md cites."""
from pathlib import Path
from collections import Counter,defaultdict
import hashlib,json,re,sys
from evidence import said,kind
ROOT=Path(__file__).resolve().parents[1];HERE=Path(__file__).resolve().parent;OUT=ROOT/'target/guif-dogfood-v2'
CODES={int(c):k for k,c in (l.split('\t') for l in (HERE/'probe/codes.tsv').read_text().splitlines() if l.strip())}
NEEDS={r[0]:r for r in (l.split('\t') for l in (HERE/'probe/preconditions.tsv').read_text().splitlines() if l.strip())}
def esc(s):return str(s).replace('|','/').replace('\n',' ')
def link(e,text='evidence'):return f'[{text}](../target/guif-dogfood-v2/fixtures/{e}/evidence.json)'
rows=json.loads((OUT/'evidence-index.json').read_text());runs=json.loads((OUT/'runs.json').read_text())
inventory={b['id']:b for b in json.loads((HERE/'inventory.json').read_text())}
def ledger(meta):return dict(meta=meta,ready=0,ready_bad=[],missing=0,missing_bad=[],actions=0,hard=False,families=set(),outcomes=Counter(),said={},stated='')
buttons={k:ledger(v) for k,v in inventory.items()}
elements={};outcomes=Counter();families=Counter();verdicts=Counter();sequence=defaultdict(set);optional=Counter();hard=[]
repros=[]
for row in rows:
    ident=row['evidence_id'];d=json.loads((OUT/'fixtures'/ident/'evidence.json').read_text());o=d['options']
    family=row['run_id'].removeprefix('v2_main_');families[family]+=1;verdicts[(family,row['verdict'])]+=1
    # a reproducer repeats one row of a family or of the sweep: listed on its own, not counted twice
    if family.startswith('repro_'):repros.append((family,row,d));continue
    outcomes[row['outcome']]+=1
    target=o.get('button') if family.startswith('sweep_') else None
    for b in d['buttons']:
        a=buttons.setdefault(b['id'],ledger(b));a['actions']+=b['actions']
        if b.get('hard'):a['hard']=True;hard.append((b['id'],ident,[f for f in b['failures']]))
        for s in b['samples']:
            if 'expected' not in s:continue
            a['families'].add(family.split('_')[0] if family.startswith('sweep_') else family)
            # the need as the check stated it, so a later edit of the table cannot rewrite what was checked
            if b['id']==target and not a['stated'] and ': ' in s['reason']:a['stated']=s['reason'].split(': ',1)[1]
            if s['expected']:
                a['ready']+=1
                if not s['ok']:a['ready_bad'].append((ident,s))
            else:
                a['missing']+=1
                if not s['ok']:a['missing_bad'].append((ident,s))
        if b['id']==target and o.get('setup')=='missing' and any('expected' in s and not s['ok'] for s in b['samples']):
            what=said(d,target);a['outcomes'][kind(what) if what!='not pressed' else what]+=1
            if what and what!='not pressed':a['said'].setdefault(what,ident)
    for e in d.get('elements',[]):
        x=elements.setdefault(e['id'],dict(meta=e,events=0,changed=0,omitted=0,failures=[]))
        x['events']+=len(e['events']);x['changed']+=sum(v['kind']!='attached' for v in e['events']);x['omitted']+=e.get('omitted_events',0);x['failures']+=e['failures']
    if family in ('run','campaign'):
        sequence[family].add(tuple(e['action'].lower() for e in d['trace'] if e['action'] in ('Pause','Resume','Cancel') and str(e.get('before','')).startswith('before '))[:3])
    for e in d['trace']:
        if e['action'].startswith('FW_Optional'):optional[(family,e['action'])]+=1
def verdict(a):
    if a['ready_bad'] or a['hard']:return 'HARD'
    if a['missing_bad']:return 'SOFT'
    if not a['ready']:return 'UNTESTED'
    return 'PASS' if a['missing'] or NEEDS.get(a['meta']['id'],['','','-'])[2] in ('-','family') else 'READY ONLY'
grade=Counter(verdict(a) for a in buttons.values())
before=json.loads((OUT/'protected-before.json').read_text())
changed=[p for p,h in before.items() if not Path(p).is_file() or hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
manifest=[s['name'] for s in json.loads((HERE/'manifest.json').read_text())]
summary=dict(status='completed' if all(n in families for n in manifest) else 'in_progress',selected=len(rows),families=len(families),outcomes=dict(outcomes),
    buttons=len(buttons),buttons_by_verdict=dict(grade),buttons_with_ready_checks=sum(a['ready']>0 for a in buttons.values()),
    buttons_with_missing_checks=sum(a['missing']>0 for a in buttons.values()),buttons_pressed=sum(a['actions']>0 for a in buttons.values()),
    hard_findings=len({h[0] for h in hard}),soft_findings=sum(1 for a in buttons.values() if verdict(a)=='SOFT'),
    listened_nonbutton_elements=len(elements),listener_failures=sum(len(a['failures']) for a in elements.values()),
    protected=len(before),changed=changed,pending_families=[n for n in manifest if n not in families])
(HERE/'checkpoint-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
# ---- per-button ledger
L=['# Per-button availability ledger','',
   'Every button of the ten tabs, the search window and the chooser accessories. **Needs** is what the button needs before pressing it means anything '
   '([probe/preconditions.tsv](probe/preconditions.tsv)). **Missing** counts checks made while that need was missing (the button should not be pressable), '
   '**Ready** those made while it was met (the button must be pressable). Pressable means enabled and shown. Checks come from every family and from the per-button sweep.','',
   'Verdict: PASS both halves hold (a button that needs nothing passes on its ready half) · SOFT pressable although its need is missing (see what a press does) · HARD ready but not pressable, or a press that failed · READY ONLY its missing half was not checked · UNTESTED no check.','',
   '| Button | Tab / section / text | Needs | Missing: wrong / checks | Ready: wrong / checks | Presses | When pressed without its need | Verdict |','|---|---|---|---:|---:|---:|---|---|']
for k,a in sorted(buttons.items()):
    b=a['meta'];need=a['stated'] or NEEDS.get(k,['','?'])[1];out=', '.join(f'{n} {w}' for w,n in a['outcomes'].most_common()) or ('—' if not a['missing_bad'] else 'family check')
    ev=(a['ready_bad'] or a['missing_bad'] or [('',None)])[0][0]
    L.append(f"| {k} | {esc(b['tab']+' / '+(b['path'] or '—')+' / '+b['text'])} | {esc(need)} | {len(a['missing_bad'])} / {a['missing']} | {len(a['ready_bad'])} / {a['ready']} | {a['actions']} | {esc(out)} | {verdict(a)}{' · '+link(ev) if ev else ''} |")
(HERE/'button_states.md').write_text('\n'.join(L)+'\n')
# ---- findings: hard first, then soft, grouped by tab and by what a press does
L=['# Findings with evidence','',f"Hard (ready but not pressable, a disabled button's action, a failing press): {summary['hard_findings']} buttons. "
   f"Soft (pressable although what it needs is missing): {summary['soft_findings']} buttons. Each line links one execution that shows it; [button_states.md](button_states.md) has every button.",'',
   '## Hard','']
for k in sorted({h[0] for h in hard}|{k for k,a in buttons.items() if a['ready_bad']}):
    a=buttons[k];L.append(f"- **{esc(a['meta']['text'])}** ({k}): "+'; '.join(esc(s['event']+': '+s['reason']) for _,s in a['ready_bad'][:3])+' '+(link(a['ready_bad'][0][0]) if a['ready_bad'] else ''))
    for ident,why in [(i,f) for h,i,f in hard if h==k][:2]:L.append(f"  - {esc('; '.join(why)[:300])} {link(ident)}")
if L[-1]=='':L.append('None.')
L+=['','## Soft, by tab','']
tabs=defaultdict(list)
for k,a in sorted(buttons.items()):
    if verdict(a)=='SOFT':tabs[a['meta']['tab']].append(k)
for t,ks in sorted(tabs.items()):
    L+=[f'### {t} — {len(ks)} buttons','','| Button | Needs | While missing it was | Press outcome | Example |','|---|---|---|---|---|']
    for k in ks:
        a=buttons[k];ident,s=a['missing_bad'][0];first=next(iter(a['said'].items()),None)
        L.append(f"| {esc(a['meta']['text'])} | {esc(a['stated'] or NEEDS.get(k,['','?'])[1])} | {esc(s['event'])} | {esc(', '.join(f'{n} {w}' for w,n in a['outcomes'].most_common()) or 'family check')}{(' — “'+esc(first[0])+'”') if first else ''} | {link(ident)} |")
    L.append('')
L+=['## Reproducers','','One candidate each, in [repro/](repro/): the Framework verdict names the button, the evidence shows what the press did. [RESUME.md](RESUME.md) has the command.','',
    '| Scenario | Verdict | What the window said or did | Evidence |','|---|---|---|---|']
for name,row,d in sorted(repros,key=lambda x:x[0]):
    o=d['options'];b=o.get('button');shown=[n for n in d['notes'] if 'says:' in n or 'showed:' in n]
    what=shown[0] if shown else (said(d,b) if b else None)
    L.append(f"| {name} | {row['verdict']} {CODES.get(row['verdict'],'PASS' if row['verdict']==0 else '?')} | {esc(what or 'nothing changed')[:260]} | {link(row['evidence_id'])} |")
(HERE/'findings.md').write_text('\n'.join(L)+'\n')
# ---- Framework execution
L=['# Framework execution and coverage','','Current checkpoints only; calibration and superseded executions are excluded. Each family is one Framework run: generated-java, one worker, SIGNED_BYTE labels, set-cover at the stated strength.','',
   '| Family | Selected cases | Outcomes | Verdicts (code: cases) | Framework coverage |','|---|---:|---|---|---|']
for r in sorted(runs,key=lambda r:manifest.index(r['name']) if r['name'] in manifest else len(manifest)):
    v=', '.join(f'{c}: {n}' for (f,c),n in sorted(verdicts.items(),key=lambda x:(x[0][1] is None,x[0][1])) if f==r['name'])
    L.append(f"| {r['name']} | {families[r['name']]} | {esc(' '.join(l for l in r['summary'] if l.startswith('outcomes')).replace('outcomes',''))} | {esc(v)} | {esc(' / '.join(c.replace('coverage','').strip() for c in r['coverage']))} |")
L+=['','## Ordered actions and sudden events','','Run uses FW_PermutR(3) with FW_Combi(3); campaign uses three positional factors. Disabled attempts are kept on purpose: these are attempted triples, not a claim that every action was enabled and delivered.','']
L+=[f'- {f}: {len(s)} distinct attempted pause/resume/cancel triples.' for f,s in sorted(sequence.items())]
L+=['']+[f'- {f}: {a}, {n} dispatches.' for (f,a),n in sorted(optional.items())]
(HERE/'execution.md').write_text('\n'.join(L)+'\n')
# ---- listeners
L=['# Independent non-button listeners','','Attached-only rows are monitored elements, not interaction coverage. Fields, outputs, labels, choices, spinners, lists, tables, trees and tabs have their own listeners; dynamic instances may have separate IDs.','',
   '| Element | Type / name | Events | After attachment | Omitted tail | Violations |','|---|---|---:|---:|---:|---:|']
for k,a in sorted(elements.items()):
    e=a['meta'];L.append(f"| {k} | {esc(e['type']+' / '+e['name'])} | {a['events']} | {a['changed']} | {a['omitted']} | {len(a['failures'])} |")
(HERE/'listeners.md').write_text('\n'.join(L)+'\n')
print(json.dumps(summary))
