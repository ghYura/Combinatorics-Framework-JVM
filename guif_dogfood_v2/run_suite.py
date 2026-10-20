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

"""Checkpointed Framework execution. Full output stays in target; console output is compact."""
from pathlib import Path
from contextlib import contextmanager
import argparse,fcntl,hashlib,json,os,re,signal,subprocess,time,shutil,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];HERE=Path(__file__).resolve().parent;OUT=ROOT/'target/guif-dogfood-v2'
ENV=dict(os.environ,XDG_CONFIG_HOME=str(OUT/'config'),MAVEN_OPTS='-Djava.awt.headless=true')

def command(args,log,timeout=1200):
    log.parent.mkdir(parents=True,exist_ok=True);start=time.monotonic()
    with log.open('w') as stream:
        p=subprocess.Popen([str(a) for a in args],cwd=ROOT,env=ENV,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
        try:p.wait(timeout=timeout)
        except BaseException:
            if p.poll() is None:
                os.killpg(p.pid,signal.SIGTERM)
                try:p.wait(timeout=5)
                except subprocess.TimeoutExpired:os.killpg(p.pid,signal.SIGKILL);p.wait()
            raise
    return p.returncode,round(time.monotonic()-start,3)

def java(main='org.ghyura.cf.app.Main'):
    return ['java','-Xmx1400m','-Djava.awt.headless=true','-Dflatlaf.animation=false','-cp','fw-app/target/fw-app.jar:target/guif-dogfood-v2/probe.jar',main]

def compile_probe():
    rc,_=command(['javac','-cp','fw-app/target/fw-app.jar','-d',OUT/'probe',*sorted((HERE/'probe').glob('*.java'))],OUT/'logs/compile.log')
    if rc:raise RuntimeError('probe compilation failed')
    for name in ['codes.tsv','preconditions.tsv']:shutil.copy2(HERE/'probe'/name,OUT/'probe'/name)
    rc,_=command(['jar','--create','--file',OUT/'probe.jar','-C',OUT/'probe','.'],OUT/'logs/jar.log')
    if rc:raise RuntimeError('probe jar failed')

def baseline(name):
    p=OUT/f'baseline-{name}.json'
    if p.exists():raise RuntimeError('baseline already recorded: '+name)
    rc,_=command(['mvn','-o','-B','-q',f'-Dmaven.repo.local={OUT}/m2',f'-Dmaven.repo.local.tail={Path.home()}/.m2/repository','-DargLine=-Djava.awt.headless=true','-pl','fw-desktop','test'],OUT/'logs'/f'baseline-{name}.log')
    rows=[ET.parse(p).getroot().attrib for p in (ROOT/'fw-desktop/target/surefire-reports').glob('TEST-*.xml')]
    counts={k:sum(int(r[k]) for r in rows) for k in ['tests','failures','errors','skipped']};counts['exit']=rc
    p.write_text(json.dumps(counts,indent=2)+'\n');print('baseline',name,counts,flush=True)
    if rc:raise RuntimeError('baseline failed')

def snapshot(path):
    """The protected files' hashes now, over the same files as the investigation's snapshot (and any added since)."""
    files={*json.loads((OUT/'protected-before.json').read_text()),*(str(p) for p in ROOT.glob('fw-*/src/**/*') if p.is_file())}
    path.write_text(json.dumps({f:hashlib.sha256(Path(f).read_bytes()).hexdigest() for f in sorted(files) if Path(f).is_file()},indent=2)+'\n')

def hashes(before_name='protected-before.json',after_name='protected-after.json'):
    before=json.loads((OUT/before_name).read_text());changed=[p for p,h in before.items() if not Path(p).is_file() or hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
    now={str(p) for p in ROOT.glob('fw-*/src/**/*') if p.is_file()}
    added=sorted(now-before.keys());summary=dict(protected=len(before),changed=changed,added=added)
    (OUT/after_name).write_text(json.dumps(summary,indent=2)+'\n')
    if changed or added:raise RuntimeError('protected files changed')
    return summary

def run_one(spec,phase='main'):
    scenario=ROOT/spec['path'];name=spec['name'];config=scenario.with_suffix('.properties')
    provenance={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [scenario,config,scenario.with_suffix('.constraints.json'),OUT/'probe.jar',ROOT/'fw-app/target/fw-app.jar']}
    for verb,extra in [('validate',[]),('candidates',['--limit','0'])]:
        log=OUT/'logs'/f'{phase}-{name}-{verb}.log';rc,_=command(java()+[verb,'--scenario',scenario,'--config',config,*extra],log)
        if rc:raise RuntimeError(f'{name}: {verb} failed')
    coverage=[l.strip() for l in log.read_text().splitlines() if re.match(r'\s*coverage\s+t=',l)]
    ck=OUT/'checkpoints'/f'{phase}-{name}.checkpoint';ck.parent.mkdir(parents=True,exist_ok=True)
    log=OUT/'logs'/f'{phase}-{name}.log'
    rc,sec=command(java()+['run','--scenario',scenario,'--config',config,'--set',f'cf.run.id=v2_{phase}_{name}','--checkpoint',ck],log)
    summary=[l.strip() for l in log.read_text(errors='replace').splitlines() if re.match(r'\s*(state |outcomes |verdicts )',l)]
    record=dict(name=name,phase=phase,exit=rc,seconds=sec,checkpoint=str(ck.relative_to(ROOT)),log=str(log.relative_to(ROOT)),summary=summary,coverage=coverage,sha256=provenance)
    print(name,rc,sec,' | '.join(summary),flush=True)
    return record

def index(records,target='evidence-index.json'):
    rc,_=command(java('org.ghyura.cf.desktop.EvidenceIndexV2')+[OUT/target,*[ROOT/r['checkpoint'] for r in records]],OUT/'logs/evidence-index.log')
    if rc:raise RuntimeError('evidence indexing failed')

def main():
    ap=argparse.ArgumentParser();ap.add_argument('phase',choices=['run','compile','index','end','hashes','verify']);ap.add_argument('names',nargs='*');args=ap.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    with (OUT/'suite.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        if args.phase=='compile':compile_probe();return
        if args.phase=='end':baseline('end');hashes();return
        if args.phase=='hashes':print(hashes());return
        if args.phase=='verify':
            # after an authorized product fix: its own record (runs-, evidence-, protected-<phase>), the investigation's stays as it was
            phase,names=args.names[0],args.names[1:];before=f'protected-{phase}-before.json'
            if not (OUT/before).exists():snapshot(OUT/before)
            compile_probe();report=OUT/f'runs-{phase}.json';records=json.loads(report.read_text()) if report.exists() else []
            for spec in [s for s in json.loads((HERE/'manifest.json').read_text()) if s['name'] in names]:
                records=[r for r in records if r['name']!=spec['name']]+[run_one(spec,phase)];report.write_text(json.dumps(records,indent=2)+'\n')
            index(records,f'evidence-{phase}.json');print(hashes(before,f'protected-{phase}-after.json'));return
        report=OUT/'runs.json';records=json.loads(report.read_text()) if report.exists() else []
        if args.phase=='index':index(records);return
        compile_probe()
        specs=json.loads((HERE/'manifest.json').read_text())
        if args.names:specs=[s for s in specs if s['name'] in args.names]
        for spec in specs:
            records=[r for r in records if r['name']!=spec['name']]
            record=run_one(spec);records.append(record);report.write_text(json.dumps(records,indent=2)+'\n')
            if record['exit'] or any(re.search(r'\b(BROKEN|TIMEOUT|INFRA_FAIL|SKIPPED|CANCELLED) [1-9]|(?:verdicts\s+|; )-[1-9]',l) for l in record['summary']):raise RuntimeError('Framework execution error: '+spec['name'])
        index(records);hashes()
if __name__=='__main__':main()
