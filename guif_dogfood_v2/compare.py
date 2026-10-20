#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-BUSL-1.1
# Test assets for the human QA engineer. See LICENSE and NOTICE.md.
"""Before and after a product fix: every button the verify phase checked, in the investigation's evidence and in the phase's.
Reads evidence only; writes <phase>.md. Usage: compare.py <phase> <title> <fixed button id>..."""
from pathlib import Path
from collections import Counter
import json,sys
from evidence import said,kind
ROOT=Path(__file__).resolve().parents[1];HERE=Path(__file__).resolve().parent;OUT=ROOT/'target/guif-dogfood-v2'
CODES={int(c):k for k,c in (l.split('\t') for l in (HERE/'probe/codes.tsv').read_text().splitlines() if l.strip())}
def link(e):return f'[evidence](../target/guif-dogfood-v2/fixtures/{e}/evidence.json)'

def checks(index,prefix,families=None):
    """Per button: checks while its need was missing (and how many found it pressable), checks while it was met (and how many
    found it not pressable), and what presses without the need did. Only the given families when named."""
    got={};repros=[]
    for row in json.loads((OUT/index).read_text()):
        name=row['run_id'].removeprefix(prefix);d=json.loads((OUT/'fixtures'/row['evidence_id']/'evidence.json').read_text())
        if name.startswith('repro_'):repros.append((name,row,d));continue
        if families is not None and name not in families:continue
        target=d['options'].get('button')
        for b in d['buttons']:
            a=got.setdefault(b['id'],dict(text=b['text'],missing=0,missing_bad=0,ready=0,ready_bad=0,outcomes=Counter(),need='',example=''))
            for s in b['samples']:
                if 'expected' not in s:continue
                if s['expected']:a['ready']+=1;a['ready_bad']+=not s['ok']
                else:a['missing']+=1;a['missing_bad']+=not s['ok']
                if not s['ok'] and not a['example']:a['example']=row['evidence_id']
                if b['id']==target and not a['need'] and ': ' in s['reason']:a['need']=s['reason'].split(': ',1)[1]
            if b['id']==target and d['options'].get('setup')=='missing' and any('expected' in s and not s['ok'] for s in b['samples']):
                a['outcomes'][kind(said(d,target))]+=1
    return got,repros
def verdict(a):return 'HARD' if a['ready_bad'] else 'SOFT' if a['missing_bad'] else 'PASS' if a['ready'] else 'UNTESTED'
def cell(a):return f"{verdict(a)} · missing {a['missing_bad']}/{a['missing']} · ready {a['ready_bad']}/{a['ready']}"

phase,title,fixed=sys.argv[1],sys.argv[2],sys.argv[3:]
runs=[r['name'] for r in json.loads((OUT/f'runs-{phase}.json').read_text())]
after,repros=checks(f'evidence-{phase}.json',f'v2_{phase}_')
before,_=checks('evidence-index.json','v2_main_',set(runs))
note=HERE/f'{phase}.note.md'
L=[f'# After the fix: {title}','',*([note.read_text().strip(),''] if note.exists() else []),f'The verify phase `{phase}` reran {len([r for r in runs if not r.startswith("repro_")])} scenarios ('
   +', '.join(r for r in runs if not r.startswith('repro_'))+f') and {len(repros)} reproducers on the fixed product, with the same probe, '
   'oracle and selection as the investigation; the investigation\'s own evidence is unchanged. Counts are wrong / checks.','',
   '## The buttons the fix touches','','| Button | Needs (as checked after the fix) | Before | After | Before: a press without the need |','|---|---|---|---|---|']
for k in fixed:
    b,a=before.get(k),after.get(k)
    if a is None:L.append(f'| {k} | — | {cell(b) if b else "—"} | not checked | |');continue
    L.append(f"| {a['text']} | {a['need'] or '—'} | {cell(b) if b else '—'} | {cell(a)}{' · '+link(a['example']) if a['example'] else ''} | {', '.join(f'{n} {w}' for w,n in b['outcomes'].most_common()) if b else ''} |")
moved=[(k,before[k],a) for k,a in sorted(after.items()) if k not in fixed and k in before and verdict(before[k])!=verdict(a)]
L+=['','## Every other button these scenarios checked','',f"{len([k for k in after if k not in fixed])} buttons; verdict changed for {len(moved)}"+(':' if moved else '.'),'']
L+=[f"- {a['text']} ({k}): {cell(b)} → {cell(a)}" for k,b,a in moved]
L+=['','## Reproducers after the fix','','| Scenario | Verdict | What the window said or did | Evidence |','|---|---|---|---|']
for name,row,d in sorted(repros,key=lambda x:x[0]):
    o=d['options'];shown=[n for n in d['notes'] if 'says:' in n]
    what=shown[0] if shown else (said(d,o['button']) if o.get('button') else None)
    L.append(f"| {name} | {row['verdict']} {CODES.get(row['verdict'],'PASS' if row['verdict']==0 else '?')} | {' '.join(str(what or 'nothing changed').replace('|','/').split())[:260]} | {link(row['evidence_id'])} |")
protected=json.loads((OUT/f'protected-{phase}-after.json').read_text())
L+=['',f"Protected files during the phase: {protected['protected']} checked against the snapshot taken after the fix; changed {len(protected['changed'])}, added {len(protected['added'])}."]
(HERE/f'{phase}.md').write_text('\n'.join(L)+'\n')
print(f'{phase}.md:',len(fixed),'fixed buttons,',len(moved),'other verdicts moved')
