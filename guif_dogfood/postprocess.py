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

"""Save reviewable summaries without dumping logs/checkpoints/exports."""
from pathlib import Path
import json, re, argparse
from run_suite import ROOT, ASSETS, OUT, command, java, run_file, collect_coverage, index_evidence, suite_lock, require_completed

from reporting import read_json, report

POST=ASSETS/'post-results'

def additional_runs(phases=('regression','refined','campaign'),names=None):
    for phase in phases:
        if phase=='campaign':
            p=POST/'refined/D002_sieve_spinner_t3.json';log=OUT/'logs/campaign-spinner.log'
            rc,sec=command(java('campaign')+['run','--scenario',p,'--config',POST/'refined/spinner-campaign.properties','--checkpoint',OUT/'checkpoints/spinner-campaign.checkpoint','--set','cf.run.id=spinner_campaign'],log)
            lines=[l.strip() for l in log.read_text().splitlines() if re.match(r'\s*(campaign|strategy|iterations|iteration|state|candidates|outcomes|verdicts|seed|winners|round)\s',l)]
            (OUT/'campaign-runs.json').write_text(json.dumps([dict(name='spinner_campaign',exit=rc,seconds=sec,summary=lines)],indent=2)+'\n')
            print('campaign spinner: '+' | '.join(lines),flush=True)
            if rc:raise RuntimeError('spinner campaign failed')
            continue
        scenarios=[p for p in sorted((POST/phase).glob('*.json')) if p.name!='manifest.json' and not p.name.endswith('.constraints.json')]
        if names:scenarios=[p for p in scenarios if p.stem in names]
        chosen={p.stem for p in scenarios};prior=OUT/f'{phase}-runs.json'
        records=[r for r in read_json(prior,[]) if r['name'] not in chosen]
        for p in scenarios:
            rc,_=command(java(phase)+['validate','--scenario',p,'--config',p.with_suffix('.properties')],OUT/'logs'/f'{phase}-{p.stem}-validate.log')
            if rc:raise RuntimeError('scenario validation failed: '+p.stem)
            record=run_file(phase,p);records.append(record)
            prior.write_text(json.dumps(records,indent=2)+'\n')
            require_completed(record)
        if phase=='refined':collect_coverage('refined',scenarios)

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--rerun',action='store_true')
    parser.add_argument('--phase',choices=['regression','refined','campaign'])
    parser.add_argument('--names',nargs='*')
    args=parser.parse_args()
    with suite_lock():
        if args.rerun:
            additional_runs((args.phase,) if args.phase else ('regression','refined','campaign'),args.names)
        index_evidence()
        report()
