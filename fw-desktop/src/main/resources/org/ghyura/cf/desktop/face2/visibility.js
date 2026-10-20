// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
// Presentation adapter. Never writes bonds, params, ORDERS, undo history or bridge payloads.
(() => {
  "use strict";
  const original = {pos, nodeXY, edgesInner, renderCanvas, bindEdges, applyView, applyLang,
    startNodeDrag, clampY, armWorldPoint, placeArmPane, renderPanel, spotlight};
  let compact = false, layout = null, normalView = null, hoverConnection = null;
  const GAP = 12, TRACK = 12, MARGIN = 24;
  const button = document.createElement("button");
  button.id = "compact-view"; button.type = "button"; button.className = "btn";
  button.setAttribute("aria-pressed", "false"); button.setAttribute("aria-controls", "canvas");
  document.querySelector("header").insertBefore(button, $("lang"));
  const tools = document.createElement("div");
  tools.id = "graph-tools";
  tools.innerHTML = '<span class="key"><i class="sample forbid" aria-hidden="true"></i><span id="key-forbid"></span></span>'
    + '<span class="key"><i class="sample" aria-hidden="true"></i><span id="key-require"></span></span>'
    + '<span id="graph-help"></span><span id="graph-status" role="status" aria-live="polite"></span>'
    + '<div id="connection-summary" aria-live="polite"></div>';
  document.querySelector("main").before(tools);
  const actual = document.createElement("button");
  actual.id = "zactual"; actual.type = "button"; actual.textContent = "100%";
  $("zoomctl").append(actual);

  function words() {
    return lang === "ru" ? {
      compact:"Компактный вид", title:"Читаемые отступы и раздельные линии. Меняет только отображение.",
      forbid:"Запрещать", require:"Требовать",
      help:"Нажмите линию: её значения подсветятся. Двойной щелчок — правка. ◆ объединяет группу, ○ редактирует ветку; пересечение не соединяет линии.",
      normal:"Компактный вид разделяет линии и показывает полные названия. Нажмите линию, чтобы выбрать связь.",
      overview:"Обзор · 100% для чтения", actual:"Масштаб 100% — читать названия", fit:"Вместить весь граф",
      link:"Связь", edit:"Enter — свойства связи", arm:"Enter — изменить ветку", layout:"Только отображение"
    } : {
      compact:"Compact view", title:"Readable spacing and separated connections. Changes the view only.",
      forbid:"Forbidden", require:"Required",
      help:"Click a line to highlight its values. Double-click to edit. ◆ joins a group, ○ edits a branch; crossings do not join lines.",
      normal:"Compact view separates connections and shows full labels. Click a line to select its connection.",
      overview:"Overview · use 100% to read", actual:"Actual size — read labels at 100%", fit:"Fit the entire graph",
      link:"Connection", edit:"Enter to edit this connection", arm:"Enter to edit this branch", layout:"View only"
    };
  }
  function labels() {
    const w = words();
    button.textContent = w.compact; button.title = w.title;
    $("key-forbid").textContent = w.forbid; $("key-require").textContent = w.require;
    $("graph-help").textContent = compact ? w.help : w.normal;
    $("zfit").title = w.fit; $("zfit").setAttribute("aria-label", w.fit);
    actual.title = w.actual; actual.setAttribute("aria-label", w.actual);
    $("zin").setAttribute("aria-label", lang === "ru" ? "Приблизить" : "Zoom in");
    $("zout").setAttribute("aria-label", lang === "ru" ? "Отдалить" : "Zoom out");
    $("graph-status").textContent = view.scale < .7 ? w.overview : w.layout;
  }
  function delta(key) {
    const index = NI[key];
    return Object.hasOwn(nodeY, key) ? nodeY[key] - (PADY + index.vi * ROWH) : 0;
  }
  function point(key) {
    const n = layout.nodes.get(key);
    return {x:n.x, y:Math.max(n.floor, n.base + delta(key))};
  }
  function makeLayout() {
    const nodes = new Map(), columns = [], routes = [], slots = SHEETS.map(() => 0);
    const ports = new Map();
    SHEETS.forEach(([sheet, values], si) => values.forEach((value, vi) => {
      const key = nid(sheet, value);
      nodes.set(key, {key, sheet, value, si, vi, delta:delta(key)});
    }));
    bonds.forEach((bond, bi) => {
      if (bond.when || bond.mapping || bond.assert) return;
      const members = bondNodes(bond).map(([s, v]) => nodes.get(nid(s, v))).filter(Boolean)
        .sort((a, b) => a.si - b.si || a.vi - b.vi);
      if (members.length < 2) return;
      const local = members.length === 2 && members[1].si - members[0].si <= 1;
      const route = {bi, bond, local, ends:[], min:members[0].si, max:members.at(-1).si};
      if (local) route.slot = slots[route.min]++;
      members.forEach((node, index) => {
        const side = local && index === 1 && route.min !== route.max ? -1 : 1;
        const end = {node, side, slot:local ? route.slot : slots[node.si]++};
        route.ends.push(end);
        const key = node.key + ":" + side;
        if (!ports.has(key)) ports.set(key, []);
        ports.get(key).push(end);
      });
      routes.push(route);
    });
    // Independent tracks reuse free intervals. The sheet/value order is never changed.
    const tracks = [];
    routes.filter(r => !r.local).forEach(route => {
      let track = tracks.findIndex(spans => spans.every(r => route.max < r.min || route.min > r.max));
      if (track < 0) { track = tracks.length; tracks.push([]); }
      tracks[track].push(route); route.track = track;
    });
    ports.forEach(ends => ends.forEach((end, i) => { end.offset = (i - (ends.length - 1) / 2) * 12; }));
    const probe = document.createElement("div");
    probe.className = "node"; probe.style.cssText = "visibility:hidden;position:fixed;left:-10000px;top:0;transform:none";
    probe.setAttribute("aria-hidden", "true"); document.body.append(probe);
    const measure = document.createElement("canvas").getContext("2d");
    measure.font = getComputedStyle(probe).font;
    const top = 24 + tracks.length * 26;
    let left = MARGIN, headerHeight = 30;
    SHEETS.forEach(([sheet, values], si) => {
      const width = Math.ceil(Math.min(224, Math.max(104,
        measure.measureText(sheet).width + (OPTIONAL.has(sheet) ? 62 : 22),
        ...values.map(v => measure.measureText(String(v)).width + 42))));
      probe.style.width = width + "px";
      probe.className = "col-h";
      probe.innerHTML = esc(sheet) + (OPTIONAL.has(sheet) ? '<span class="opt-badge">' + esc(T().optionalW) + "</span>" : "");
      headerHeight = Math.max(headerHeight, probe.offsetHeight);
      columns.push({x:left + width / 2, width, left, right:left + width});
      probe.className = "node";
      values.forEach(value => {
        const n = nodes.get(nid(sheet, value));
        probe.innerHTML = esc(value) + nodeDots(n.key);
        const count = Math.max((ports.get(n.key + ":1") || []).length, (ports.get(n.key + ":-1") || []).length);
        n.width = width; n.height = Math.max(probe.offsetHeight, 26 + Math.max(0, count - 1) * 12);
      });
      left += width + Math.max(48, 28 + slots[si] * TRACK);
    });
    probe.remove();
    let height = top + headerHeight + 44;
    columns.forEach((column, si) => {
      let bottom = top + headerHeight + GAP, baseline = bottom;
      SHEETS[si][1].forEach(value => {
        const n = nodes.get(nid(SHEETS[si][0], value));
        n.x = column.x;
        n.base = baseline + n.height / 2;
        n.floor = bottom + n.height / 2;
        n.y = Math.max(n.floor, n.base + n.delta);
        bottom = n.y + n.height / 2 + GAP;
        baseline += n.height + GAP;
      });
      height = Math.max(height, bottom + MARGIN);
    });
    return {nodes, columns, routes, top, headerHeight, width:Math.max(240, left + MARGIN), height, armPoints:new Map()};
  }

  pos = function (si, vi) {
    if (!compact || !layout) return original.pos(si, vi);
    return {x:layout.columns[si].x, y:layout.top};
  };
  nodeXY = function (sheet, value) {
    if (!compact || !layout) return original.nodeXY(sheet, value);
    const key = nid(sheet, value);
    return layout.nodes.has(key) ? point(key) : null;
  };
  function pathData(points) {
    return points.map((p, i) => (i ? "L" : "M") + p.x.toFixed(1) + "," + p.y.toFixed(1)).join(" ");
  }
  function endpoint(end) {
    const p = point(end.node.key);
    // Opposite sides use staggered port rows: unrelated wires across one gutter
    // must not merge when two different value cards happen to share a baseline.
    const phase = end.side < 0 ? 6 : 0;
    return {x:p.x + end.side * (end.node.width / 2 + 2), y:Math.round((p.y + end.offset - phase) / 12) * 12 + phase};
  }
  function rail(end) { return layout.columns[end.node.si].right + 16 + end.slot * TRACK; }
  function intersects(a, b) { return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y; }
  edgesInner = function () {
    if (!compact || !layout) return original.edgesInner();
    const paths = [], decorations = [], badges = [];
    layout.armPoints.clear();
    const occupied = [...layout.nodes.values()].map(n => {
      const p = point(n.key); return {x:p.x - n.width / 2 - 4, y:p.y - n.height / 2 - 4, w:n.width + 8, h:n.height + 8};
    });
    function addPath(route, points, end) {
      const {bi, bond} = route, polarity = bond.polarity === "forbid" ? "f" : "r";
      const cls = "edge " + polarity + (bi === selBond ? " hot" : selBond !== null ? " dim" : "")
        + (conflictBonds.has(bi) ? " bad" : "") + (end ? " armseg" : "")
        + (end && sameArm(selArm, bi, end.node.sheet, end.node.value) ? " arm-hot" : "");
      const data = ' data-bi="' + bi + '"' + (end ? ' data-s="' + attr(end.node.sheet) + '" data-v="' + attr(end.node.value) + '"' : "");
      const d = pathData(points);
      const title = words().link + " " + (bi + 1) + ": " + plainBondText(bond);
      paths.push({points, bi, html:'<path class="edge-halo' + (end ? ' armseg' : '') + '"' + data
        + ' d="' + d + '"></path><path class="' + cls + '"' + data
        + ' d="' + d + '"><title>' + esc(title) + "</title></path>"});
    }
    layout.routes.forEach(route => {
      const ends = route.ends, first = endpoint(ends[0]), last = endpoint(ends.at(-1));
      if (route.local) {
        const x = rail(ends[0]);
        addPath(route, [first, {x,y:first.y}, {x,y:last.y}, last]);
      } else {
        const y = 20 + route.track * 26;
        const minX = Math.min(...ends.map(rail)), maxX = Math.max(...ends.map(rail));
        const hub = {x:(minX + maxX) / 2, y};
        ends.forEach(end => {
          const p = endpoint(end), x = rail(end);
          const points = [p, {x,y:p.y}, {x,y}, hub];
          addPath(route, points, ends.length > 2 ? end : null);
          if (ends.length > 2) {
            // The branch handle sits outside its value card, on this branch's own port.
            const handle = {x:(p.x + x) / 2, y:p.y};
            layout.armPoints.set(JSON.stringify([route.bi, end.node.sheet, end.node.value]), handle);
            const active = sameArm(selArm, route.bi, end.node.sheet, end.node.value) ? " arm-hot" : "";
            decorations.push('<circle class="armctl' + active + '" data-bi="' + route.bi + '" data-s="' + attr(end.node.sheet)
              + '" data-v="' + attr(end.node.value) + '" cx="' + handle.x + '" cy="' + handle.y
              + '" r="4.5"><title>' + esc(T().armEdit + ": " + end.node.sheet + "=" + end.node.value) + "</title></circle>");
          }
        });
        if (ends.length > 2) decorations.push('<rect class="hub" data-bi="' + route.bi + '" x="' + (hub.x - 7)
          + '" y="' + (hub.y - 7) + '" width="14" height="14" transform="rotate(45 ' + hub.x + " " + hub.y
          + ')" fill="var(--' + (route.bond.polarity === "forbid" ? "forbid" : "require") + ')"><title>'
          + esc(words().link + " " + (route.bi + 1) + ": " + plainBondText(route.bond)) + "</title></rect>");
      }
    });
    // Label a free segment; never cover a value card or a different connection.
    layout.routes.forEach(route => {
      const candidates = [];
      paths.filter(p => p.bi === route.bi).forEach(path => path.points.slice(1).forEach((p, i) => {
        const a = path.points[i];
        if (Math.hypot(p.x - a.x, p.y - a.y) >= 30)
          for (const t of [.5, .25, .75]) candidates.push({x:a.x + (p.x - a.x) * t - 13, y:a.y + (p.y - a.y) * t - 10, w:26, h:20});
      }));
      const place = candidates.find(rect => !occupied.some(other => intersects(rect, other))
        && paths.filter(p => p.bi !== route.bi).every(p => p.points.slice(1).every((end, i) => {
          const start = p.points[i];
          return !intersects(rect, {x:Math.min(start.x,end.x)-3, y:Math.min(start.y,end.y)-3,
            w:Math.abs(end.x-start.x)+6, h:Math.abs(end.y-start.y)+6});
        })));
      if (!place) return;
      occupied.push(place);
      badges.push('<g class="connection-number" data-bi="' + route.bi + '"><title>'
        + esc(words().link + " " + (route.bi + 1) + ": " + plainBondText(route.bond))
        + '</title><rect x="' + place.x + '" y="' + place.y + '" width="26" height="20" rx="5"></rect><text x="'
        + (place.x + 13) + '" y="' + (place.y + 14) + '">' + (route.bi + 1) + "</text></g>");
    });
    return paths.map(p => p.html).join("") + decorations.join("") + badges.join("");
  };

  bindEdges = function (canvas) {
    original.bindEdges(canvas);
    canvas.querySelectorAll("#edges [data-bi]").forEach(el => {
      const bi = +el.dataset.bi, isArm = el.classList.contains("armseg") || el.classList.contains("armctl");
      const full = bondNodes(bonds[bi]).map(([s,v]) => s + ": " + v).join(" · ");
      const description = words().link + " " + (bi + 1) + ", "
        + (bonds[bi].polarity === "forbid" ? words().forbid : words().require) + ": " + full;
      el.onmouseenter = el.onfocus = () => { hoverConnection = bi; spotlight(); };
      el.onmouseleave = el.onblur = () => { hoverConnection = null; spotlight(); };
      if (el.classList.contains("edge-halo")) {
        el.setAttribute("aria-hidden", "true"); // Larger mouse target, without a duplicate keyboard stop.
        return;
      }
      el.setAttribute("tabindex", "0"); el.setAttribute("role", "button");
      el.setAttribute("aria-label", description + ". " + (isArm ? words().arm : words().edit));
      el.onkeydown = event => {
        if (event.ctrlKey || event.metaKey || event.altKey || event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault(); event.stopPropagation();
        if (isArm) openArmDialog(bi, el.dataset.s, el.dataset.v);
        else openBondDialog(bi);
      };
    });
  };
  renderCanvas = function () {
    hoverConnection = null;
    if (compact) layout = makeLayout();
    original.renderCanvas();
    if (compact && SHEETS.length) {
      const canvas = $("canvas"), svg = $("edges");
      canvas.style.width = layout.width + "px"; canvas.style.height = layout.height + "px";
      svg.setAttribute("width", layout.width); svg.setAttribute("height", layout.height);
      canvas.querySelectorAll(".node").forEach(el => {
        const n = layout.nodes.get(el.dataset.id);
        el.style.width = n.width + "px"; el.style.minHeight = n.height + "px";
      });
      canvas.querySelectorAll(".col-h").forEach((el, si) => {
        el.style.top = layout.top + "px"; el.style.width = layout.columns[si].width + "px";
      });
    }
  };
  // A selected connection has a visible endpoint highlight even when the pointer leaves it.
  spotlight = function () {
    original.spotlight();
    const selected = hoverConnection === null ? selBond : hoverConnection, summary = $("connection-summary");
    summary.innerHTML = selected !== null && bonds[selected]
      ? "<b>" + esc(words().link + " " + (selected + 1)) + "</b> · " + bondText(bonds[selected]) : "";
    if (!compact) return;
    if (selected === null || !bonds[selected]) {
      if (inspectNode() === null) $("canvas").querySelectorAll("#edges [data-bi]").forEach(el => el.classList.remove("hot", "dim"));
      return;
    }
    const members = new Set(bondNodes(bonds[selected]).map(([s,v]) => nid(s,v)));
    $("canvas").classList.add("spotlight");
    $("canvas").querySelectorAll(".node").forEach(el => {
      el.classList.remove("hl-self", "hl-f", "hl-r", "hl-ctx");
      if (members.has(el.dataset.id)) el.classList.add(bonds[selected].polarity === "forbid" ? "hl-f" : "hl-r");
    });
    $("canvas").querySelectorAll("#edges [data-bi]").forEach(el => {
      el.classList.toggle("hot", +el.dataset.bi === selected);
      el.classList.toggle("dim", +el.dataset.bi !== selected);
    });
  };
  function numberRows() {
    document.querySelectorAll("#links .row[data-bi], #formulas .row[data-bi]").forEach(row => {
      if (row.querySelector(".connection-ref")) return;
      const ref = document.createElement("span");
      ref.className = "connection-ref"; ref.textContent = String(+row.dataset.bi + 1);
      ref.title = words().link + " " + ref.textContent; row.prepend(ref);
    });
  }
  renderPanel = function () { original.renderPanel(); numberRows(); };
  applyLang = function () { original.applyLang(); labels(); };
  applyView = function () { original.applyView(); labels(); };
  startNodeDrag = function (event, element) {
    original.startNodeDrag(event, element);
    if (compact && drag && drag.id === element.dataset.id) {
      const n = layout.nodes.get(drag.id);
      drag.y0 = PADY + n.vi * ROWH + point(drag.id).y - n.base;
    }
  };
  clampY = function (y) {
    if (!compact || !layout) return original.clampY(y);
    const key = drag ? drag.id : sel, n = layout.nodes.get(key);
    if (!n) return y;
    const base = PADY + n.vi * ROWH;
    const top = layout.top + layout.headerHeight + GAP + n.height / 2;
    const current = Object.hasOwn(nodeY, key) ? nodeY[key] : point(key).y;
    const wanted = drag ? n.base + y - base : point(key).y + y - current;
    const previous = SHEETS[n.si][1][n.vi - 1], next = SHEETS[n.si][1][n.vi + 1];
    const before = n.vi ? layout.nodes.get(nid(n.sheet, previous)) : null;
    const after = n.vi + 1 < SHEETS[n.si][1].length ? layout.nodes.get(nid(n.sheet, next)) : null;
    const low = before ? point(before.key).y + (before.height + n.height) / 2 + GAP : top;
    const high = after ? point(after.key).y - (after.height + n.height) / 2 - GAP : Infinity;
    return base + Math.max(low, Math.min(high, wanted)) - n.base;
  };
  armWorldPoint = function (bi, sheet, value) {
    return compact && layout ? layout.armPoints.get(JSON.stringify([bi,sheet,value])) || nodeXY(sheet,value)
      : original.armWorldPoint(bi,sheet,value);
  };
  placeArmPane = function (pt) {
    const pane = $("arm-popover");
    if (pane) pane.style.transform = ""; // Restore the template's anchor before measuring a new position.
    original.placeArmPane(pt);
    if (!pane || !pane.classList.contains("show")) return;
    const wrap = $("stagewrap").getBoundingClientRect(), rect = pane.getBoundingClientRect();
    // Keep the whole branch editor reachable when its node is near a viewport edge.
    pane.style.transform = "none";
    pane.style.left = Math.max(8, Math.min(wrap.width - rect.width - 8, rect.left - wrap.left)) + "px";
    pane.style.top = Math.max(8, Math.min(wrap.height - rect.height - 8, rect.top - wrap.top)) + "px";
  };
  fitView = function () {
    const wrap = $("stagewrap").getBoundingClientRect();
    let minX = 0, minY = 0, maxX = compact && layout ? layout.width : 240, maxY = compact && layout ? layout.height : 120;
    $("canvas").querySelectorAll(".node,.col-h").forEach(el => {
      const x = parseFloat(el.style.left) || 0, y = parseFloat(el.style.top) || el.offsetTop;
      const top = el.classList.contains("node") ? y - el.offsetHeight / 2 : y;
      minX = Math.min(minX, x - el.offsetWidth / 2 - 12); minY = Math.min(minY, top - 12);
      maxX = Math.max(maxX, x + el.offsetWidth / 2 + 12); maxY = Math.max(maxY, top + el.offsetHeight + 12);
    });
    const width = maxX - minX, height = maxY - minY;
    // Reserve the bottom-right zoom control; Fit must not hide the last value beneath it.
    view.scale = Math.max(.02, Math.min((wrap.width - 80) / width, (wrap.height - 32) / height, 1.25));
    view.tx = 16 - minX * view.scale + Math.max(0, (wrap.width - 80 - width * view.scale) / 2);
    view.ty = 16 - minY * view.scale + Math.max(0, (wrap.height - 32 - height * view.scale) / 2);
    applyView();
  };
  actual.onclick = () => { zoomCenter(1 / view.scale); };
  button.onclick = () => {
    if (!compact) normalView = {...view};
    compact = !compact;
    document.body.classList.toggle("compact-graph", compact);
    button.setAttribute("aria-pressed", String(compact));
    renderCanvas(); // Never rebuild the editor panel: unapplied text and dialog drafts must survive.
    if (compact) fitView(); else if (normalView) { view = {...normalView}; applyView(); }
    if (armDraft && selArm) {
      const p = armWorldPoint(selArm.bi,selArm.s,selArm.v);
      if (p) placeArmPane({x:view.tx + p.x * view.scale, y:view.ty + p.y * view.scale});
    }
    labels();
  };
  let resizeFrame;
  new ResizeObserver(() => {
    cancelAnimationFrame(resizeFrame);
    resizeFrame = requestAnimationFrame(() => { if (didAutoFit && !drag && !pan) fitView(); });
  }).observe($("stagewrap"));
  labels(); numberRows(); renderCanvas();
})();
