// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
// Runs the packaged transport code without browser automation or npm dependencies.
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2');
const html = fs.readFileSync(path.join(root, 'editor.html'), 'utf8');
for (const [, script] of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(script);
const bridge = fs.readFileSync(path.join(root, 'bridge.js'), 'utf8');

function fixture(initial = [], parameters = {}) {
  const elements = new Map(), calls = [], timers = new Map(), listeners = {};
  let timer = 0, response = {ok: true, json: async () => ({rules: 1, exact: true, total: '4', kept: '3'})};
  const element = id => {
    if (!elements.has(id)) elements.set(id, {textContent: '', innerHTML: '', style: {}, disabled: false, open: false,
      inputs: [], querySelectorAll() { return this.inputs; }, append(node) { this.textContent += node.textContent || ''; },
      classes: new Set(), classList: {add(value) { element(id).classes.add(value); }}, setAttribute() {}});
    return elements.get(id);
  };
  const context = vm.createContext({
    D: {jvm: './', jvmRevision: 0}, bonds: JSON.parse(JSON.stringify(initial)), params: JSON.parse(JSON.stringify(parameters)),
    lang: 'en', armDraft: null, _impactTimer: null, emptyResult: false, updateImpact() {}, download() {},
    $: element, T: () => ({kept: n => `kept ${n}`, dlsaved: 'downloaded'}), toast() {},
    refreshWarn() { element('warn').textContent = ''; }, pushUndo() {},
    renderParams() {
      element('params').inputs = Object.entries(context.params).flatMap(([s, values]) => Object.entries(values).map(([v, attrs]) => {
        const value = Object.entries(attrs).map(([key, val]) => key + '=' + val).join(', ');
        return {dataset: {s, v}, value, defaultValue: value};
      }));
    },
    renderPanel() { context.renderParams(); context.updateImpact(); },
    setTimeout(fn) { timers.set(++timer, fn); return timer; }, clearTimeout(id) { timers.delete(id); },
    window: {addEventListener(type, fn) { listeners[type] = fn; }},
    fetch: async (url, options) => { calls.push({url, payload: JSON.parse(options.body)}); return response; },
    document: {body: {inert: false}, querySelectorAll() { return element('params').inputs.concat(element('drafts').inputs); },
      createElement() { return {click() {}}; }, createTextNode(textContent) { return {textContent}; }},
    URL: {createObjectURL() { return 'blob:test'; }, revokeObjectURL() {}}, Blob,
  });
  vm.runInContext(bridge, context);
  return {context, calls, element, listeners, timers, respond(next) { response = next; },
    async impact() { const fn = [...timers.values()].at(-1); timers.clear(); await fn(); },
    async submit() { await element('submit').onclick(); }};
}

async function main() {
  const bond = {polarity: 'forbid', sides: {A: ['a'], B: ['1']}, gate: {}, when: null, condition: null, mapping: null, assert: null, desc: ''};
  const noOp = fixture([bond], {A: {a: {large: 9007199254740993}}});
  await noOp.impact();
  assert.deepEqual(noOp.calls[0].payload.edits, [{source: 0, patch: []}]);
  assert.deepEqual(noOp.calls[0].payload.paramsPatch, []);
  assert.match(noOp.element('impact').innerHTML, /kept 3/);

  const edit = fixture([bond], {A: {a: {large: 9007199254740993, cost: 1}}});
  edit.context.bonds[0].sides.B = ['2']; edit.context.params.A.a.cost = 3;
  await edit.submit();
  assert.deepEqual(edit.calls[0].payload.edits, [{source: 0, patch: [{path: ['sides', 'B'], value: ['2']}]}]);
  assert.deepEqual(edit.calls[0].payload.paramsPatch, [{path: ['A', 'a', 'cost'], value: 3}]);
  assert.equal(edit.calls[0].payload.revision, 0);
  assert.equal(edit.element('overlay').classes.has('show'), true);
  assert.equal(edit.element('submit').disabled, true);

  const undo = fixture([bond]);
  const snapshot = JSON.stringify(undo.context.bonds);
  undo.context.bonds[0].sides.B = ['2']; undo.context.bonds = JSON.parse(snapshot);
  await undo.submit(); assert.deepEqual(undo.calls[0].payload.edits, [{source: 0, patch: []}]);

  const added = fixture(); added.context.bonds.push(bond); await added.submit();
  assert.deepEqual(added.calls[0].payload.edits, [{bond}]);
  const removed = fixture([bond]); removed.context.bonds = []; await removed.submit();
  assert.deepEqual(removed.calls[0].payload.edits, []);

  const markerName = fixture([{...bond, sides: {A: ['a'], _jvmSource: ['old']}}], {_jvmSource: {a: {_jvmSource: 1}}});
  markerName.context.bonds[0].sides._jvmSource = ['new'];
  markerName.context.params._jvmSource.a._jvmSource = 2;
  await markerName.submit();
  assert.deepEqual(markerName.calls[0].payload.edits, [{source: 0, patch: [{path: ['sides', '_jvmSource'], value: ['new']}]}]);
  assert.deepEqual(markerName.calls[0].payload.paramsPatch, [{path: ['_jvmSource', 'a', '_jvmSource'], value: 2}]);

  for (const message of ['Invalid formula', 'The JVM document changed', 'File changed outside this editor']) {
    const failed = fixture([bond]); failed.respond({ok: false, text: async () => message});
    await failed.submit();
    assert.equal(failed.element('overlay').classes.has('show'), false);
    assert.equal(failed.element('submit').disabled, false);
    assert.equal(failed.element('warn').textContent, message);
    assert.equal(failed.calls.length, 1, 'a rejected save must not fall back to a download');
    failed.respond({ok: true, json: async () => ({exact: true, total: '4', kept: '3'})});
    await failed.impact();
    assert.equal(failed.element('warn').textContent, message, 'a late impact response must not erase a save error');
    assert.equal(failed.context.document.body.inert, false);
  }
  const draft = fixture(); draft.element('fdlg').open = true; await draft.submit();
  assert.equal(draft.calls.length, 0);
  const inline = fixture(); inline.element('drafts').inputs.push({value: 'edited but unapplied', defaultValue: ''});
  await inline.submit(); assert.equal(inline.calls.length, 0);
  const unsafe = fixture([bond]); unsafe.context.bonds[0].sides.B = [9007199254740993]; await unsafe.submit();
  assert.equal(unsafe.calls.length, 0); assert.match(unsafe.element('warn').textContent, /precision/);

  const typed = fixture([], {A: {a: {big: 9007199254740993, cost: 1, flag: true, text: '2'}}});
  const row = typed.element('params').inputs[0];
  row.value = row.value.replace('cost=1', 'cost=3'); row.onchange();
  await typed.submit();
  assert.deepEqual(typed.calls[0].payload.paramsPatch, [{path: ['A', 'a', 'cost'], value: 3}], 'editing one parameter must preserve its neighbours');
  const largeEdit = fixture([], {A: {a: {big: 9007199254740993}}});
  const bigRow = largeEdit.element('params').inputs[0]; bigRow.value = 'big=9007199254740995'; bigRow.onchange();
  assert.match(largeEdit.element('warn').textContent, /precision/);
  assert.equal(largeEdit.context.params.A.a.big, 9007199254740993);

  const inFlight = fixture(); let acknowledge;
  inFlight.respond({ok: true, json: () => new Promise(resolve => { acknowledge = resolve; })});
  const submitting = inFlight.submit();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(inFlight.context.document.body.inert, true, 'edits must not be lost while a submitted snapshot is being saved');
  acknowledge({rules: 0}); await submitting; assert.equal(inFlight.context.document.body.inert, false);

  const bounded = fixture([bond]); bounded.respond({ok: true, json: async () => ({exact: false, note: 'scan limit 1'})});
  await bounded.impact(); assert.match(bounded.element('impact').textContent, /not yet known/);
  assert.equal(bounded.element('impacthint').textContent, 'scan limit 1');
  const unavailable = fixture([bond]); unavailable.respond({ok: false, text: async () => 'validation failed'});
  await unavailable.impact(); assert.equal(unavailable.element('impact').textContent, 'Count unavailable');

  const dirty = fixture([bond]); dirty.context.bonds[0].sides.B = ['2'];
  let prevented = false; dirty.listeners.beforeunload({preventDefault() { prevented = true; }});
  assert.equal(prevented, true);
  console.log('PASS: original Face 2 JavaScript syntax; bridge deltas, add/delete/undo, typed parameters, precision, pending drafts, submit acknowledgements, persistent errors, save locking, exact/bounded impact and unsaved edits.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
