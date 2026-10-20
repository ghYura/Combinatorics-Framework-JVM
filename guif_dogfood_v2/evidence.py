#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-BUSL-1.1
# Test assets for the human QA engineer. See LICENSE and NOTICE.md.
"""What a press did, read back from one execution's evidence (shared by report.py and compare.py)."""

def said(d,ident):
    """What the window said after the press of ident: the text that changed first, as it settled; None for silence."""
    press=next((e['seq'] for e in d['trace'] if e['action']=='press '+ident and 'seq' in e),None)
    if press is None:return 'not pressed'
    changed=[]
    for e in d.get('elements',[]):
        if e['type'] in ('JTextArea','JLabel','JTextField','JTextPane','JEditorPane'):
            after=[v for v in e['events'] if v['seq']>press and v['kind'] in ('document:insert','document:remove','property:text') and v['value'].strip()]
            if after:changed.append((after[0]['seq'],(e['name'] or e['type'])+': '+after[-1]['value'].strip()[:150]))
    # a draft the press left behind, then a replacement the editor reports, outrank the text that changed first
    draft=[c for c in changed if 'pending draft' in c[1]];replaced=[c for c in changed if ': Replaced ' in c[1]]
    return min(draft or replaced or changed)[1] if changed else None
def kind(what):
    """What a press without its need did, from the first text that changed."""
    if what is None:return 'silent'
    name,_,value=what.partition(': ')
    if 'pending draft' in value:return 'leaves a pending draft'
    if value.startswith('Replaced '):return 'replaces the formula, and says so'
    if name in ('makes',"Formula over the sheets' parameters"):return 'replaces the formula'
    if name in ('Constraints as JSON','Rule ID'):return 'reloads the shown text'
    return 'explains'
