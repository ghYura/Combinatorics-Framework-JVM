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

"""Regenerate post-result expansions, leaving the initial design untouched."""
from pathlib import Path
import json

BASE=Path(__file__).resolve().parent
POST=BASE/'post-results'

def save(name,data,source):
    sheets=data['sheets']
    ordered=[k for k in sheets if k not in ('FW_Seq','FW_CUSTOM_VAR')]
    expected={str(i):'GUIF_'+k[2:].upper()+'_INVARIANT' for i,k in enumerate(ordered,1) if k.startswith('w_')}
    actual=dict(sheets['FW_CUSTOM_VAR'])
    assert all(actual.get(k)==v for k,v in expected.items()), name
    budget=len(ordered)+sum(len(sheets[k]) for k in ordered)
    assert budget<=108, (name,budget)
    stem=POST/'refined'/name
    stem.with_suffix('.json').write_text(json.dumps(data,indent=2)+'\n')
    stem.with_suffix('.constraints.json').write_text(source.with_suffix('.constraints.json').read_text())
    config=source.with_suffix('.properties').read_text().splitlines()
    config=[line for line in config if not line.startswith('cf.campaign.')]
    config=['cf.coverage.strength=3' if line.startswith('cf.coverage.strength=') else line for line in config]
    stem.with_suffix('.properties').write_text('\n'.join(config)+'\n')
    return dict(name=name,label_budget=budget,strength=3)

def main():
    records=[]
    for name,original in [('D001_accessible_names_t3','t4_graph'),('R003_runner_interactions_t3','t3_runner'),('D004_disguised_metrics_t3','t8_analysis')]:
        source=BASE/'initial/scenarios'/f'{original}.json'
        records.append(save(name,json.loads(source.read_text()),source))
    source=POST/'regression/D002_sieve_spinner.json'
    data=json.loads(source.read_text());s=data['sheets']
    # Replace the three singleton factor slots in place: code 20 stays stable.
    factors=dict(rule_body=['pair','formula'],spinner_value=['0','1','9223372036854775807'],spinner_direction=['next','previous'])
    rename=dict(zip(('body','edit','resolution'),factors))
    s={rename.get(key,key):value for key,value in s.items()};data['sheets']=s
    for key,values in factors.items():s[key]=[[f'o.put("{key}","{value}");'] for value in values]
    for row in s['FW_Seq']:row[0]=rename.get(row[0],row[0])
    records.append(save('D002_sieve_spinner_t3',data,source))
    # The old runner clone name incorrectly implied a coverage-budget refinement.
    for suffix in ('.json','.constraints.json','.properties'):
        (POST/'refined'/('D003_coverage_budget_plan_t3'+suffix)).unlink(missing_ok=True)
    campaign=(POST/'refined/D002_sieve_spinner_t3.properties').read_text()
    campaign=campaign.replace('cf.analysis.goals=elapsed_ms:min,violations:min','cf.analysis.goals=violations:max')
    campaign+='cf.campaign.iterations=2\ncf.campaign.stopWhenStable=99\ncf.campaign.strategy=bias\ncf.campaign.explorationFloor=0.25\ncf.campaign.seed.output=target/guif-dogfood/spinner-campaign-seed.json\n'
    (POST/'refined/spinner-campaign.properties').write_text(campaign)
    (POST/'refined/manifest.json').write_text(json.dumps(records,indent=2)+'\n')
    print('Refinements:', ', '.join(r['name'] for r in records), '; maximum labels',max(r['label_budget'] for r in records))

if __name__=='__main__':main()
