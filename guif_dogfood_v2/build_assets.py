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

"""Compile the v2 design into actual Framework sheets and constraint sidecars."""
from pathlib import Path
import json, argparse

ROOT=Path(__file__).resolve().parents[1]
HERE=Path(__file__).resolve().parent
OUT=ROOT/'target/guif-dogfood-v2'

# id, what the button needs, steps that leave it missing ('-': nothing can, 'family': tested by its own family), steps that meet it
NEEDS={row[0]:row for row in (line.split('\t') for line in (HERE/'probe/preconditions.tsv').read_text().splitlines() if line.strip())}

# the author's notice, as this file carries it, heads every settings file the suite writes (the license gate asks for it)
_own=Path(__file__).read_text().splitlines()
NOTICE='\n'.join(_own[_own.index('# SPDX-License-Identifier: LicenseRef-BUSL-1.1'):_own.index('# See LICENSE and NOTICE.md for the binding terms.')+1])+'\n\n'

def cell(key,value):return 'o.put('+json.dumps(key)+','+json.dumps(value)+');'
def condition(key,value):return dict(sheet=key,eq=cell(key,value))

SPECS=[
 ('library',2,dict(fixture=['valid','empty'],selection=['first','none'],action=['open','check']),['clear']),
 ('authoring',2,dict(format=['json','yaml','toml','xlsx'],selection=['first','none']),['new']),
 ('scenario_runner',2,dict(runner=['python','generated-java','inline-metrics'],input=['valid','invalid']),[]),
 ('candidates',3,dict(page=['small','all'],change=['keep','replace']),['cancel','clear']),
 ('formatting',3,dict(language=['JAVA','JSON','XML','PYTHON','TEXT'],input=['full','blank','truncated']),['original','select']),
 ('run',3,dict(timing=['early','later'],sequence=['pause','resume','cancel']),['cancel','refresh']),
 ('campaign',3,dict(first=['pause','resume','cancel'],second=['pause','resume','cancel'],third=['pause','resume','cancel']),['cancel','refresh']),
 ('analysis',2,dict(input=['valid','invalid'],coverage=['jacoco','gcov','coverage-py']),[]),
 ('scorer',2,dict(rules=['valid','invalid']),['clear']),
 ('wizard',2,dict(situation=['SETTINGS','WORKFLOW','OPTIONS','REPEATS','EVENTS','OWN'],selection=['first','last'],format=['json','yaml','toml','xlsx']),[]),
 ('sieve_graph',3,dict(first=['Undo','Redo'],second=['Undo','Redo']),['undo','validate']),
 ('sieve_data',3,dict(selection=['first','last'],resolution=['apply','discard']),['undo']),
 ('sieve_rules',2,dict(resolution=['apply','discard']),['switch']),
 ('sieve_formula',2,dict(mode=['blocks','text'],slot=['root','operand']),[]),
 ('search',2,dict(selection=['first','none']),['stop']),
 ('dialog_buttons',2,dict(chooser=['all_purposes']),[]),
]

# One candidate each: the smallest Framework run that shows one finding cluster (name, probe family, option values).
REPROS=[
 ('repro_library_open_without_example','sweep_repro',dict(button='t01_open_in_the_scenario_tab_1',setup='missing')),
 ('repro_authoring_remove_row_without_row','sweep_repro',dict(button='t02_remove_row_1',setup='missing')),
 ('repro_authoring_copy_dot_without_graph','sweep_repro',dict(button='t02_2_4_dependency_graph_copy_dot_1',setup='missing')),
 ('repro_scenario_validate_empty_box','sweep_repro',dict(button='t03_validate_and_plan_1',setup='missing')),
 ('repro_sieve_validate_before_open','sweep_repro',dict(button='t04_validate_1',setup='missing')),
 ('repro_sieve_undo_without_history','sweep_repro',dict(button='t04_undo_1',setup='missing')),
 ('repro_sieve_discard_without_draft','sweep_repro',dict(button='t04_discard_draft_1',setup='missing')),
 ('repro_sieve_palette_replaces_filled_slot','sweep_repro',dict(button='t04_rule_body_compare_1',setup='ready',plan='sieve_open mixed rule0 formula formula_root')),
 ('repro_sieve_number_replaces_filled_operand','sweep_repro',dict(button='t04_rule_body_number_1',setup='ready',plan='sieve_open mixed rule0 formula formula_operand')),
 ('repro_sieve_gate_without_rule_blocks_validate','sweep_repro',dict(button='t04_adjacent_1',setup='missing',then='press:t04_validate_1 note_sieve')),
 ('repro_sieve_data_draft_before_open_locks_views','sweep_repro',dict(button='t04_parameters_orders_parameters_add_attribute_1',setup='missing',then='matrix note_sieve')),
 ('repro_candidates_preview_without_scenario','sweep_repro',dict(button='t05_preview_1',setup='missing')),
 ('repro_candidates_next_page_after_step3_change','sweep_repro',dict(button='t05_next_page_1',setup='missing',plan='scenario preview step3_changed')),
 ('repro_run_without_scenario','sweep_repro',dict(button='t06_run_1',setup='missing')),
 ('repro_campaign_without_scenario','sweep_repro',dict(button='t07_run_campaign_1',setup='missing')),
 ('repro_analysis_without_metrics','sweep_repro',dict(button='t08_analyze_1',setup='missing')),
 ('repro_scorer_without_text','sweep_repro',dict(button='t09_score_1',setup='missing')),
 ('repro_scorer_export_after_failed_scoring','sweep_repro',dict(button='t09_export_csv_1',setup='missing',plan='scored score_fails')),
 ('repro_wizard_move_up_at_first_factor','sweep_repro',dict(button='t10_0_2_factors_move_up_1',setup='missing')),
 ('repro_wizard_check_empty_design','sweep_repro',dict(button='t10_0_7_finish_check_with_the_framework_1',setup='missing')),
]

def inventory():
    buttons={};elements={}
    for p in sorted((OUT/'fixtures').glob('*/evidence.json'),key=lambda p:p.stat().st_mtime):
        d=json.loads(p.read_text())
        for b in d.get('buttons',[]):
            buttons[b['id']]={k:b[k] for k in ['id','tab','path','text','type','hint']}
        for e in d.get('elements',[]):
            elements[e['id']]={k:e[k] for k in ['id','tab','path','type','name']}
    (HERE/'inventory.json').write_text(json.dumps(list(buttons.values()),ensure_ascii=False,indent=2)+'\n')
    (HERE/'element_inventory.json').write_text(json.dumps(list(elements.values()),ensure_ascii=False,indent=2)+'\n')
    codefile=HERE/'probe/codes.tsv'
    codes={a:int(b) for a,b in (line.split('\t') for line in codefile.read_text().splitlines())} if codefile.exists() else {}
    for ident in sorted([*buttons,*elements]):
        if ident not in codes:codes[ident]=max(codes.values(),default=99)+1
    (HERE/'probe/codes.tsv').write_text(''.join(f'{key}\t{value}\n' for key,value in codes.items()))
    return buttons,elements,codes

def make(name,strength,factors,optional,codes,folder='scenarios',family=None):
    sheets={'HEAD':[['import java.util.*; import org.ghyura.cf.desktop.ButtonProbe; public class GuifButtonCase { public static int FW_VAR=0; public static void main(String[] args) throws Exception { Map<String,String> o=new LinkedHashMap<>(); int position=0;']]}
    for k,levels in factors.items():
        if k=='sequence':
            sheets[k]=[['o.put(List.of("first","second","third").get(position++),'+json.dumps(v)+');'] for v in levels]
        else:sheets[k]=[[cell(k,v)] for v in levels]
    if optional:sheets['sudden']=[[cell('sudden',v)] for v in optional]
    sheets['act']=[[f'ButtonProbe p=ButtonProbe.execute("{family or name}",o);']]
    sheets['watch']=[['FW_VAR=p.verdict();']]
    sheets['TAIL']=[['p.emit(FW_VAR); } }']]
    seq=[[k,*(['FW_PermutR(3)','FW_Combi(3)'] if k=='sequence' else ['FW_Combi(1)']),*(['FW_Optional'] if k=='sudden' else [])] for k in sheets]
    custom=[['0','PASS'],['-1','GUIF_V2_HARNESS_ERROR'],['-7','GUIF_V2_UNREGISTERED_WATCHER']]
    custom.extend([str(v),'GUIF_V2_'+k.upper()] for k,v in codes.items())
    data={'sheets':{'FW_Seq':seq,**sheets,'FW_CUSTOM_VAR':custom}}
    constraints=[]
    if name=='library':
        constraints.append(dict(id='selected_row_needs_loaded_library',polarity='require',assertion=''))
        constraints[-1].pop('assertion')
        constraints[-1]['assert']={'any':[{'not':condition('selection','first')},condition('fixture','valid')]}
        constraints.append(dict(id='clear_is_an_interruption_of_existing_selection',polarity='forbid',pairs=[{'selection':cell('selection','none'),'sudden':cell('sudden','clear')}]))
    if name=='formatting':
        for value in ['blank','truncated']:
            constraints.append(dict(id='interrupt_requires_started_formatter_'+value,polarity='forbid',pairs=[{'input':cell('input',value),'sudden':cell('sudden',event)} for event in optional]))
    if name=='sieve_data':
        constraints.append({'id':'apply_requires_a_constructed_data_draft','polarity':'require','assert':{'any':[{'not':condition('resolution','apply')},{'sheet':'selection','present':True}]}})
    if name.startswith('sweep_'):
        constraints.append({'id':'probe_needs_an_explicit_setup_node','polarity':'require','assert':{'sheet':'setup','present':True}})
        # a button that needs nothing has no state in which its need is missing: the sieve removes those rows
        free=[b for b in factors['button'] if NEEDS[b][2]=='-']
        if free:constraints.append(dict(id='nothing_to_miss_for_buttons_that_need_nothing',polarity='forbid',pairs=[{'button':cell('button',b),'setup':cell('setup','missing')} for b in free]))
    budget=sum(len(v) for v in sheets.values())+len(sheets)
    assert budget<127,(name,budget)
    stem=HERE/folder/name;stem.parent.mkdir(parents=True,exist_ok=True)
    stem.with_suffix('.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
    stem.with_suffix('.constraints.json').write_text(json.dumps(dict(version=1,constraints=constraints),indent=2)+'\n')
    stem.with_suffix('.properties').write_text(NOTICE+f'''cf.execution.sut=generated-java
cf.execution.workers=1
cf.execution.timeout.millis=120000
cf.execution.classpath=target/guif-dogfood-v2/probe.jar
cf.keys.encoding=SIGNED_BYTE
cf.coverage.strength={strength}
cf.coverage.method=set-cover
cf.coverage.budget=0
cf.coverage.hardCap=100000
cf.analysis.goals=elapsed_ms:min,failures:min
cf.analysis.discover=false
cf.plan.autoPromoteSingleVerb=false
''')
    return dict(name=name,strength=strength,factors=factors,optional=optional,constraints=constraints,label_budget=budget,path=str(stem.with_suffix('.json').relative_to(ROOT)))

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--sweeps',action='store_true');ap.add_argument('--repro',action='store_true');args=ap.parse_args()
    buttons,elements,codes=inventory()
    specs=[make(*spec,codes) for spec in SPECS]
    if args.sweeps:
        # every button the precondition table can set up; search and chooser buttons belong to their own families
        ids=[b for b,row in NEEDS.items() if row[2]!='family']
        missing=[b for b in ids if b not in buttons]
        assert not missing,('preconditions.tsv names buttons the inventory lacks',missing)
        absent=[b['id'] for b in buttons.values() if (b['tab'].startswith('t') or b['tab']=='shell') and b['id'] not in NEEDS]
        assert not absent,('buttons without a precondition row',absent)
        for n in range(0,len(ids),40):
            specs.append(make(f'sweep_{n//40+1:02}',2,dict(button=ids[n:n+40],setup=['missing','ready']),['away'],codes))
    if args.repro:
        specs+=[make(name,0,{k:[v] for k,v in options.items()},[],codes,folder='repro',family=family) for name,family,options in REPROS]
    (HERE/'manifest.json').write_text(json.dumps(specs,indent=2)+'\n')
    print(f'{len(buttons)} buttons, {len(elements)} non-button elements, {len(specs)} scenarios; largest label budget {max(s["label_budget"] for s in specs)}/127')

if __name__=='__main__':main()
