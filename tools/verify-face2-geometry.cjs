// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
// Execute the actual presentation adapter with deterministic element measurements.
// This verifies routing/state invariants, not browser CSS layout or visual acceptance.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = fs.readFileSync(path.join(root, 'fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2/visibility.js'), 'utf8');
const escape = s => String(s).replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const decode = s => s.replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');

function fixture(sheets, initial) {
  const ids = new Map(), nodes = [], headers = [], edges = [];
  class Element {
    constructor(tag = 'div') {
      this.tagName = tag; this.style = {}; this.dataset = {}; this.attributes = {};
      this.className = ''; this.textContent = ''; this._html = ''; this.children = [];
      this.classList = {
        contains: c => this.className.split(' ').includes(c),
        add: (...cs) => { this.className = [...new Set([...this.className.split(' '), ...cs])].filter(Boolean).join(' '); },
        remove: (...cs) => { this.className = this.className.split(' ').filter(c => !cs.includes(c)).join(' '); },
        toggle: (c, force) => { const on = force ?? !this.classList.contains(c); this.classList[on ? 'add' : 'remove'](c); return on; }
      };
    }
    set id(value) { this._id = value; ids.set(value, this); }
    get id() { return this._id; }
    set innerHTML(value) { this._html = value; this.textContent = decode(value.replace(/<[^>]*>/g, '')); }
    get innerHTML() { return this._html; }
    setAttribute(key, value) { this.attributes[key] = String(value); }
    getAttribute(key) { return this.attributes[key]; }
    append(...elements) { this.children.push(...elements); }
    before() {}
    prepend() {}
    remove() {}
    insertBefore() {}
    observe() {}
    querySelector() { return null; }
    querySelectorAll(selector) {
      if (this.id === 'canvas') {
        if (selector === '.node') return nodes;
        if (selector === '.col-h') return headers;
        if (selector === '.node,.col-h') return [...nodes, ...headers];
        if (selector.includes('[data-bi]')) return edges;
      }
      return [];
    }
    get offsetWidth() { return parseFloat(this.style.width) || Math.max(70, this.textContent.length * 7 + 24); }
    get offsetHeight() {
      return Math.max(parseFloat(this.style.minHeight) || 0,
        16 + 19 * Math.max(1, Math.ceil(this.textContent.length * 7 / (this.offsetWidth - 22))));
    }
    get offsetTop() { return parseFloat(this.style.top) || 0; }
    getBoundingClientRect() { return {left:0,top:0,width:1000,height:700,right:1000,bottom:700}; }
    getContext() { return {font:'13px sans-serif', measureText:s => ({width:String(s).length * 7})}; }
  }
  const $ = id => { if (!ids.has(id)) { const el = new Element(); el.id = id; } return ids.get(id); };
  const body = new Element(), header = new Element(), main = new Element();
  const nid = (s,v) => JSON.stringify([s,v]), NI = {};
  sheets.forEach(([sheet,values],si) => values.forEach((value,vi) => { NI[nid(sheet,value)] = {si,vi}; }));
  const context = {
    console, Set, Map, JSON, Object, Math, Number, String, Infinity,
    SHEETS:sheets, NI, PADY:66, PADX:130, ROWH:52, COLW:210,
    bonds:JSON.parse(JSON.stringify(initial)), params:{keep:{flag:true,big:'9007199254740993'}},
    nodeY:{}, view:{scale:1,tx:0,ty:0}, lang:'en', sel:null, selBond:null, selArm:null, drag:null, pan:null,
    armDraft:null, didAutoFit:true, conflictBonds:new Set(), OPTIONAL:new Set(sheets.length ? [sheets.at(-1)[0]] : []),
    $, nid, esc:escape, attr:s => escape(s).replace(/'/g, '&#39;'),
    document:{body, createElement:tag => new Element(tag), querySelector:selector => selector === 'header' ? header : main, querySelectorAll:() => []},
    getComputedStyle:() => ({font:'13px sans-serif'}),
    ResizeObserver:class { observe() {} },
    requestAnimationFrame:() => 1, cancelAnimationFrame() {},
    T:() => ({optionalW:'optional',armEdit:'Edit branch'}),
    nodeDots:() => '', inspectNode:() => null,
    bondNodes:b => Object.entries(b.sides).flatMap(([s,values]) => values.map(v => [s,v])),
    plainBondText:b => JSON.stringify(b.sides),
    bondText:b => escape(JSON.stringify(b.sides)),
    sameArm:(a,bi,s,v) => !!a && a.bi === bi && a.s === s && a.v === v,
    pos(si,vi) { return {x:130+210*si,y:66+52*vi}; },
    nodeXY(s,v) { const i = NI[nid(s,v)]; return i ? {x:130+210*i.si,y:context.nodeY[nid(s,v)] ?? 66+52*i.vi} : null; },
    edgesInner:() => '',
    bindEdges() {}, applyView() {}, applyLang() {}, renderPanel() {}, spotlight() {}, fitView() {}, zoomCenter() {},
    startNodeDrag(e,el) { const key = el.dataset.id; context.drag = {id:key,y0:context.nodeXY(...JSON.parse(key)).y}; },
    clampY:y => y, armWorldPoint:() => ({x:0,y:0}), placeArmPane() {},
    openArmDialog(...args) { context.openedArm = args; }, openBondDialog(...args) { context.openedBond = args; },
    renderCanvas() {
      nodes.length = 0; headers.length = 0; edges.length = 0;
      $('edges').innerHTML = context.edgesInner();
      for (const m of $('edges').innerHTML.matchAll(/<(path|circle|rect|g)\b([^>]*data-bi="(\d+)"[^>]*)>/g)) {
        const el = new Element(m[1]);
        el.dataset.bi = m[3];
        for (const a of m[2].matchAll(/([a-z-]+)="([^"]*)"/g)) {
          el.setAttribute(a[1], decode(a[2]));
          if (a[1] === 'class') el.className = a[2];
          if (a[1].startsWith('data-')) el.dataset[a[1].slice(5)] = decode(a[2]);
        }
        edges.push(el);
      }
      sheets.forEach(([s,values],si) => {
        const h = new Element(); h.className = 'col-h'; h.innerHTML = escape(s);
        h.style.left = context.pos(si,0).x + 'px'; headers.push(h);
        values.forEach(v => {
          const n = new Element(); n.className = 'node'; n.dataset.id = nid(s,v); n.innerHTML = escape(v);
          const p = context.nodeXY(s,v); n.style.left = p.x + 'px'; n.style.top = p.y + 'px'; nodes.push(n);
        });
      });
      context.bindEdges($('canvas')); context.spotlight(); context.applyView();
    }
  };
  vm.createContext(context); vm.runInContext(source, context);
  const snapshot = () => JSON.stringify({bonds:context.bonds,params:context.params,nodeY:context.nodeY});
  return {context, $, nodes, headers, edges, snapshot};
}

function segments(f) {
  return f.edges.filter(el => el.tagName === 'path' && el.classList.contains('edge')).flatMap(el => {
    const points = [...el.getAttribute('d').matchAll(/[ML](-?[\d.]+),(-?[\d.]+)/g)].map(m => ({x:+m[1],y:+m[2]}));
    return points.slice(1).map((p,i) => ({bi:+el.dataset.bi,a:points[i],b:p}));
  }).filter(s => Math.hypot(s.a.x-s.b.x,s.a.y-s.b.y) > .1);
}
function verify(f) {
  const boxes = f.nodes.map(n => ({id:n.dataset.id, l:parseFloat(n.style.left)-n.offsetWidth/2,
    r:parseFloat(n.style.left)+n.offsetWidth/2,t:parseFloat(n.style.top)-n.offsetHeight/2,b:parseFloat(n.style.top)+n.offsetHeight/2}));
  for (let i=0;i<boxes.length;i++) for (let j=i+1;j<boxes.length;j++) {
    const a=boxes[i],b=boxes[j];
    assert(!(a.l<b.r&&a.r>b.l&&a.t<b.b&&a.b>b.t), 'Overlapping cards: '+a.id+' / '+b.id);
  }
  const lines = segments(f);
  for (const s of lines) for (const r of boxes) {
    const hit = s.a.x === s.b.x
      ? s.a.x > r.l+.2 && s.a.x < r.r-.2 && Math.max(s.a.y,s.b.y) > r.t+.2 && Math.min(s.a.y,s.b.y) < r.b-.2
      : s.a.y > r.t+.2 && s.a.y < r.b-.2 && Math.max(s.a.x,s.b.x) > r.l+.2 && Math.min(s.a.x,s.b.x) < r.r-.2;
    assert(!hit, 'Connection '+s.bi+' crosses card '+r.id);
  }
  for (let i=0;i<lines.length;i++) for (let j=i+1;j<lines.length;j++) {
    const a=lines[i],b=lines[j]; if(a.bi===b.bi) continue;
    const overlap = a.a.x===a.b.x && b.a.x===b.b.x && Math.abs(a.a.x-b.a.x)<.1
      && Math.min(Math.max(a.a.y,a.b.y),Math.max(b.a.y,b.b.y))-Math.max(Math.min(a.a.y,a.b.y),Math.min(b.a.y,b.b.y))>.2
      || a.a.y===a.b.y && b.a.y===b.b.y && Math.abs(a.a.y-b.a.y)<.1
      && Math.min(Math.max(a.a.x,a.b.x),Math.max(b.a.x,b.b.x))-Math.max(Math.min(a.a.x,a.b.x),Math.min(b.a.x,b.b.x))>.2;
    assert(!overlap, 'Coincident independent connections '+a.bi+' / '+b.bi+' '+JSON.stringify({a,b}));
  }
  for(const el of f.edges) {
    if(el.classList.contains('edge-halo')) {
      assert.equal(el.getAttribute('aria-hidden'),'true');
      assert.equal(el.getAttribute('tabindex'),undefined);
      continue;
    }
    assert.equal(el.getAttribute('role'),'button');
    assert.equal(el.getAttribute('tabindex'),'0');
    assert(el.getAttribute('aria-label').includes('Connection'));
    let prevented = false;
    el.onkeydown({key:'Enter',preventDefault(){prevented=true;},stopPropagation(){}});
    assert(prevented);
    const arm = el.classList.contains('armctl') || el.classList.contains('armseg');
    assert.equal((arm ? f.context.openedArm : f.context.openedBond)[0],+el.dataset.bi);
    let shortcutBlocked = false;
    el.onkeydown({key:'Enter',ctrlKey:true,preventDefault(){shortcutBlocked=true;},stopPropagation(){shortcutBlocked=true;}});
    assert.equal(shortcutBlocked,false,'Existing Ctrl+Enter submission shortcut must still reach the document');
  }
  return {nodes:boxes.length,segments:lines.length,connections:new Set(lines.map(s=>s.bi)).size};
}

const cases = [];
for (const columns of [1,2,3,5,8]) for (const rows of [1,3,8]) for (const long of [false,true]) for (const dense of [false,true]) {
  const sheets = Array.from({length:columns},(_,s)=>['Sheet '+s,Array.from({length:rows},(_,v)=>
    long && v===0 ? 'A complete long value in sheet '+s+' with no hidden suffix '+('x'.repeat(50)) : 'value '+v)]);
  const bonds = [];
  const add = (members, extra={}) => {
    const sides = {};
    for(const [s,v] of members) (sides[sheets[s][0]] ||= []).push(sheets[s][1][v%rows]);
    bonds.push({polarity:bonds.length%2?'require':'forbid',sides,gate:{},...extra});
  };
  for(let s=0;s<columns-1;s++) for(let v=0;v<(dense?rows:1);v++) add([[s,v],[s+1,(v+1)%rows]]);
  if(columns>1) add([[0,0],[1,0]],{condition:{sheet:sheets[0][0],present:true}});
  if(columns>2) {
    add([[0,0],[columns-1,0]]);
    add([[0,0],[1,0],[columns-1,rows-1]]);
    if(rows>1) add([[0,0],[0,1],[2,rows-1]]);
  }
  if(columns===1&&rows>1) add([[0,0],[0,1]]);
  const f=fixture(sheets,bonds),before=f.snapshot();
  f.$('compact-view').onclick();
  let summary;
  try { summary=verify(f); } catch(error) { console.error('case', {columns,rows,long,dense}); throw error; }
  assert.equal(f.snapshot(),before,'Presentation changed document state');
  const beforeView=JSON.stringify(f.context.view);
  f.$('compact-view').onclick();
  f.$('compact-view').onclick();
  assert.equal(JSON.stringify(f.context.view),beforeView,'Repeated toggle changed fit');
  assert.equal(f.snapshot(),before,'Repeated toggle changed document state');
  cases.push({columns,rows,long,dense,...summary});
}
const advanced=fixture([['A',['a','b']],['B',['1','2']],['C',['x','y']]],[
  {polarity:'require',sides:{A:['a'],B:['1']},when:'A.value == B.value'},
  {polarity:'forbid',sides:{A:['b'],C:['y']},mapping:{source:'A',target:'C',allow:{b:['y']}}},
  {polarity:'require',sides:{A:['a'],B:['1']},assert:{sheet:'A',present:true}}
]);
advanced.$('compact-view').onclick();
assert.equal(segments(advanced).length,0,'Sheet-level conditions must not become value links');
const empty=fixture([],[]);empty.$('compact-view').onclick();verify(empty);
const mutable=fixture([['A',['a','b']],['B',['1','2']],['C',['x','y']]],[
  {polarity:'forbid',sides:{A:['a'],B:['1']},gate:{}}
]);
mutable.$('compact-view').onclick();
mutable.context.bonds.push({polarity:'require',sides:{A:['b'],B:['2'],C:['x']},gate:{}});
mutable.context.renderCanvas();verify(mutable);
mutable.context.bonds.splice(0,1);mutable.context.renderCanvas();verify(mutable);
const key=JSON.stringify(['A','a']);
mutable.context.sel=key;
const limit=mutable.context.clampY(100000);
assert(Number.isFinite(limit),'Compact dragging must preserve node separation');
mutable.context.nodeY[key]=limit;mutable.context.renderCanvas();verify(mutable);
// Prior free-layout drags may put nodes above one another. Compact mode must
// separate them without changing the saved free layout on a simple toggle.
mutable.$('compact-view').onclick();
mutable.context.nodeY[JSON.stringify(['A','a'])]=-100;
mutable.context.nodeY[JSON.stringify(['A','b'])]=500;
const draggedBefore=mutable.snapshot();
mutable.$('compact-view').onclick();verify(mutable);
assert.equal(mutable.snapshot(),draggedBefore);
const last=mutable.nodes.find(n=>n.dataset.id===JSON.stringify(['A','b']));
mutable.context.startNodeDrag({button:0},last);
mutable.context.nodeY[last.dataset.id]=mutable.context.clampY(mutable.context.drag.y0+40);
const liveY=mutable.context.nodeXY('A','b').y;
mutable.context.drag=null;mutable.context.renderCanvas();verify(mutable);
assert.equal(mutable.context.nodeXY('A','b').y,liveY,'Drag release must not move the node again');
const out=path.join(root,'target/face2-visibility');fs.mkdirSync(out,{recursive:true});
fs.writeFileSync(path.join(out,'geometry-result.json'),JSON.stringify({
  status:'PASS',measurement:'Deterministic DOM metrics; browser CSS/rendering NOT verified',cases,advanced:true,empty:true,mutationAndDrag:true
},null,2));
console.log('PASS: '+cases.length+' layouts; no card collisions, card-crossing wires or coincident independent wires; keyboard routing, state-preserving toggles, mutation, drag bounds and advanced-rule separation.');
