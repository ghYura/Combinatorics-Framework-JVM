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

"""One process per Framework scenario, with logs/evidence under target only."""
from pathlib import Path
from contextlib import contextmanager
import argparse, fcntl, hashlib, json, os, signal, subprocess, time, xml.etree.ElementTree as ET, shutil, re

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'guif_dogfood'
OUT=ROOT/'target/guif-dogfood'
ENV=dict(os.environ, XDG_CONFIG_HOME=str(OUT/'config'), MAVEN_OPTS='-Djava.awt.headless=true')
MANIFEST=ASSETS/'initial/manifest.json'

def command(args, log, timeout=900):
    log.parent.mkdir(parents=True,exist_ok=True)
    started=time.monotonic()
    with log.open('w') as stream:
        p=subprocess.Popen([str(a) for a in args],cwd=ROOT,env=ENV,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
        try:p.wait(timeout=timeout)
        except BaseException:
            # Interrupt only this command's process group, including its owned CLI reproducer.
            if p.poll() is None:
                os.killpg(p.pid,signal.SIGTERM)
                try:p.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(p.pid,signal.SIGKILL);p.wait()
            raise
    return p.returncode,round(time.monotonic()-started,3)

@contextmanager
def suite_lock():
    OUT.mkdir(parents=True,exist_ok=True)
    with (OUT/'suite.lock').open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise RuntimeError('another guif dog-food batch is running') from None
        try:yield
        finally:fcntl.flock(lock,fcntl.LOCK_UN)

def protect_state():
    paths=sorted(ROOT.glob('fw-*/src/**/*'))
    trail=Path.home()/'.config/combinatorics-framework/desktop-places.properties'
    if trail.exists():paths.append(trail)
    hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file()}
    (OUT/'protected-before.json').write_text(json.dumps(hashes,indent=2)+'\n')

def repository_args():
    return [f'-Dmaven.repo.local={OUT}/m2',f'-Dmaven.repo.local.tail={Path.home()}/.m2/repository']

def java(phase):
    return ['java','-Xmx1600m','-Djava.awt.headless=true','-Dflatlaf.animation=false',f'-Dguif.phase={phase}','-cp','fw-app/target/fw-app.jar:target/guif-dogfood/probe.jar','org.ghyura.cf.app.Main']

def stage_m2():
    ns={'m':'http://maven.apache.org/POM/4.0.0'}
    for p in [ROOT/'pom.xml',*ROOT.glob('fw-*/pom.xml')]:
        t=ET.parse(p).getroot()
        def get(k): return t.findtext('m:'+k,namespaces=ns) or t.findtext('m:parent/m:'+k,namespaces=ns)
        g,a,v=map(get,['groupId','artifactId','version'])
        dest=OUT/'m2'/g.replace('.','/')/a/v;dest.mkdir(parents=True,exist_ok=True)
        shutil.copy2(p,dest/f'{a}-{v}.pom')
        jar=p.parent/'target'/f'{a}-{v}.jar'
        if jar.exists():shutil.copy2(jar,dest/jar.name)

def baseline(which):
    rc,_=command(['mvn','-o','-B',*repository_args(),'-DargLine=-Djava.awt.headless=true','test','-pl','fw-desktop'],OUT/'logs'/f'baseline-{which}.log')
    summary=[l for l in (OUT/'logs'/f'baseline-{which}.log').read_text().splitlines() if re.search(r'Tests run: \d+, Failures', l) or 'BUILD ' in l]
    print(which,summary,flush=True)
    if rc: raise RuntimeError('baseline failed: '+which)

def run_file(phase,scenario,config=None):
    config=config or scenario.with_suffix('.properties');name=scenario.stem
    log=OUT/'logs'/f'{phase}-{name}.log';ckpt=OUT/'checkpoints'/f'{phase}-{name}.checkpoint'
    ckpt.parent.mkdir(parents=True,exist_ok=True)
    started=time.time()
    rc,sec=command(java(phase)+['run','--scenario',scenario,'--config',config,'--set',f'cf.run.id={phase}_{name}','--checkpoint',ckpt],log)
    summary=[l.strip() for l in log.read_text(errors='replace').splitlines() if re.search(r'^\s*(state |outcomes |verdicts |coverage\s+t=|candidates\s+generated)',l)]
    record=dict(name=name,phase=phase,exit=rc,seconds=sec,started=started,log=str(log.relative_to(ROOT)),checkpoint=str(ckpt.relative_to(ROOT)),summary=summary)
    print(f'{phase} {name}: exit={rc} seconds={sec} '+' | '.join(summary),flush=True)
    return record

def require_completed(record):
    summary='\n'.join(record['summary'])
    if record['exit'] or not re.search(r'^state\s+COMPLETED',summary,re.M):
        raise RuntimeError('run did not complete: '+record['name'])
    if re.search(r'\b(?:BROKEN|TIMEOUT|INFRA_FAIL|CANCELLED|SKIPPED) [1-9]',summary) or re.search(r'(?:verdicts\s+|; )-[1-9]',summary):
        raise RuntimeError('harness or execution failure: '+record['name'])

def collect_coverage(phase='initial',scenarios=None):
    scenarios=scenarios or [ASSETS/'initial/scenarios'/f'{s["name"]}.json' for s in json.loads(MANIFEST.read_text())]
    filename='coverage-summary.json' if phase=='initial' else f'coverage-{phase}-summary.json'
    prior=OUT/filename;chosen={p.stem for p in scenarios}
    records=[r for r in json.loads(prior.read_text()) if r['name'] not in chosen] if prior.exists() else []
    for p in scenarios:
        log=OUT/'logs'/f'coverage-{phase}-{p.stem}.log'
        rc,_=command(java(phase)+['candidates','--scenario',p,'--config',p.with_suffix('.properties'),'--limit','0'],log)
        summary=[l.strip() for l in log.read_text().splitlines() if re.match(r'\s*coverage\s+t=',l)]
        records.append(dict(name=p.stem,exit=rc,summary=summary))
        if rc or not summary:raise RuntimeError('coverage report failed: '+p.stem)
    (OUT/filename).write_text(json.dumps(records,indent=2)+'\n')
    return records

def index_evidence():
    paths=[]
    for phase in ('initial','regression','refined'):
        p=OUT/f'{phase}-runs.json'
        for r in json.loads(p.read_text()) if p.exists() else []:
            ckpt=ROOT/r.get('checkpoint',f'target/guif-dogfood/checkpoints/{phase}-{r["name"]}.checkpoint')
            if ckpt.exists():paths.append(ckpt)
    rc,_=command(java('report')[:-1]+['org.ghyura.cf.desktop.EvidenceIndex',OUT/'evidence-index.json',*paths],OUT/'logs/evidence-index.log')
    if rc:raise RuntimeError('checkpoint identity extraction failed')

def run_scenarios(phase, names=None):
    specs=json.loads(MANIFEST.read_text());records=[]
    if not names:(OUT/f'{phase}-started.time').write_text(str(time.time()))
    prior=OUT/f'{phase}-runs.json'
    if names and prior.exists(): records=[r for r in json.loads(prior.read_text()) if r['name'] not in names]
    for spec in specs:
        name=spec['name']
        if names and name not in names:continue
        scenario=ASSETS/'initial/scenarios'/f'{name}.json';config=scenario.with_suffix('.properties')
        if phase=='pilot':
            data=json.loads(scenario.read_text())
            for sheet,rows in data['sheets'].items():
                if sheet not in ('FW_Seq','FW_CUSTOM_VAR'):data['sheets'][sheet]=rows[:1]
            if 'sudden' in data['sheets']:
                data['sheets']['sudden']=[['/* optional interruption omitted during calibration */']]
                for row in data['sheets']['FW_Seq']:
                    if row[0]=='sudden':row[:]=['sudden','FW_Combi(1)']
            scenario=OUT/'pilots'/f'{name}.json';scenario.parent.mkdir(exist_ok=True);scenario.write_text(json.dumps(data))
        if phase!='pilot':
            for cmd,extra in [('validate',[]),('plan',['--scan','100000'])]:
                rc,sec=command(java(phase)+[cmd,'--scenario',scenario,'--config',config]+extra,OUT/'logs'/f'{phase}-{name}-{cmd}.log')
                if rc:raise RuntimeError(f'{phase} {name} {cmd} exit={rc}')
        record=run_file(phase,scenario,config)
        records.append(record);prior.write_text(json.dumps(records,indent=2)+'\n')
        require_completed(record)
    return records

def main():
    ap=argparse.ArgumentParser();ap.add_argument('phase',choices=['all','pilot','initial','end','coverage','index']);ap.add_argument('names',nargs='*');args=ap.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    with suite_lock():execute(args)

def execute(args):
    if args.phase=='all':
        protect_state()
        rc,_=command(['mvn','-o','-B','-q',*repository_args(),'package','-DskipTests','-Dmaven.jar.forceCreation=true','-pl','fw-app','-am'],OUT/'logs/build-rerun.log')
        if rc:raise RuntimeError('build failed')
        stage_m2();baseline('start');
        rc,_=command(['bash','guif_dogfood/initial/probe/build.sh'],OUT/'logs/probe-build.log')
        if rc:raise RuntimeError('probe compile failed')
        run_scenarios('initial');collect_coverage()
        from postprocess import additional_runs, report
        additional_runs()
        baseline('end')
        index_evidence();report()
    elif args.phase=='end':baseline('end')
    elif args.phase=='coverage':collect_coverage()
    elif args.phase=='index':index_evidence()
    else:run_scenarios(args.phase,args.names or None)

if __name__=='__main__':main()
