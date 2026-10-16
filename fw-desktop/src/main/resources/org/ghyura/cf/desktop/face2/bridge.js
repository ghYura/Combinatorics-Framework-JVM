// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
// Transport adapter only. editor.html is the unmodified QA Face 2 template.
(() => {
  const copy = value => JSON.parse(JSON.stringify(value));
  const baseline = copy(bonds), initialParams = copy(params);
  bonds.forEach((bond, index) => { bond._jvmSource = index; });
  let submitted = false, submitting = false, impactSequence = 0;
  let saveError = "";
  const plain = value => value !== null && typeof value === "object" && !Array.isArray(value);
  const own = (object, key) => Object.prototype.hasOwnProperty.call(object, key);
  const originalWarnings = refreshWarn;
  refreshWarn = function () {
    originalWarnings();
    if (saveError) {
      const warning = $("warn");
      if (warning.textContent) warning.append(document.createElement("br"));
      warning.append(document.createTextNode(saveError));
      warning.setAttribute("role", "alert");
    }
  };

  // Untouched JSON numbers stay in Java; only edited fields cross back from JavaScript.
  function safe(value) {
    if (typeof value === "number" && (!Number.isFinite(value) || Number.isInteger(value) && !Number.isSafeInteger(value)))
      throw new Error("Edit large integer values in the JVM Advanced JSON editor to preserve their precision.");
    if (value && typeof value === "object") Object.values(value).forEach(safe);
  }
  function changes(before, after, path = [], out = []) {
    if (JSON.stringify(before) === JSON.stringify(after)) return out;
    if (plain(before) && plain(after)) {
      for (const key of new Set([...Object.keys(before), ...Object.keys(after)])) {
        if (!own(after, key)) out.push({path: [...path, key], remove: true});
        else if (!own(before, key)) { safe(after[key]); out.push({path: [...path, key], value: after[key]}); }
        else changes(before[key], after[key], [...path, key], out);
      }
    } else { safe(after); out.push({path, value: after}); }
    return out;
  }
  function payload() {
    return {version: 1, revision: D.jvmRevision, edits: bonds.map(bond => {
      const {_jvmSource: source, ...value} = bond;
      if (Number.isInteger(source))
        return {source, patch: changes(baseline[source], value)};
      safe(value); return {bond: value};
    }), paramsPatch: changes(initialParams, params)};
  }
  async function request(action) {
    const response = await fetch(D.jvm + action, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload())});
    if (!response.ok) throw new Error(await response.text() || "The Framework did not accept these changes.");
    return response;
  }
  function showError(error) {
    saveError = error.message || String(error);
    refreshWarn();
    toast(lang === "ru" ? "Не сохранено — см. сообщение" : "Not saved — see the message");
  }
  function hasDraft() {
    return !!armDraft || ["fdlg", "mdlg", "rdlg", "adlg", "edlg"].some(id => $(id).open)
      || [...document.querySelectorAll("#le-when, #le-cond, #le-map, #le-assert, #params input")]
        .some(input => input.value !== input.defaultValue);
  }

  // The QA parameter row parses all fields on change. Preserve untouched JVM types and
  // large numbers when a neighbouring attribute is edited, instead of reparsing them.
  const originalParams = renderParams;
  renderParams = function () {
    originalParams();
    $("params").querySelectorAll("input").forEach(input => {
      const sheet = input.dataset.s, value = input.dataset.v;
      const before = copy((params[sheet] || {})[value] || {});
      input.onchange = () => {
        try {
          if (Object.entries(before).some(([key, val]) => /[,=]/.test(key) || /[,=]/.test(String(val))))
            throw new Error("Edit parameters containing commas or equals signs in the JVM Advanced JSON editor.");
          const entries = new Map();
          for (const part of input.value.split(",").filter(part => part.trim())) {
            const pieces = part.split("="), key = pieces[0].trim(), text = (pieces[1] || "").trim();
            if (pieces.length !== 2 || !key || entries.has(key)) throw new Error("Use unique name=value parameters, separated by commas.");
            let next;
            if (own(before, key) && String(before[key]) === text) next = before[key];
            else {
              if (own(before, key) && typeof before[key] === "string") next = text;
              else if (own(before, key) && typeof before[key] === "boolean") {
                if (text !== "true" && text !== "false") throw new Error("A boolean parameter must be true or false.");
                next = text === "true";
              } else next = isNaN(+text) ? text : +text;
              safe(next);
            }
            entries.set(key, next);
          }
          pushUndo(); params = {...params, [sheet]: {...(params[sheet] || {}), [value]: Object.fromEntries(entries)}};
          renderPanel();
        } catch (error) { showError(error); }
      };
    });
  };
  renderParams();

  // Override the QA transport's download-on-error fallback: a failed POST is never a saved document.
  $("submit").onclick = async () => {
    if (submitted || submitting) return;
    if (hasDraft()) { showError(new Error("Apply or discard the pending field or dialog edit before submitting.")); return; }
    saveError = ""; refreshWarn();
    submitting = true; $("submit").disabled = true;
    document.body.inert = true;
    try {
      const result = await (await request("submit")).json();
      submitted = true;
      $("t-okh").textContent = lang === "ru" ? "Сохранено в Framework" : "Saved to Framework";
      $("okmsg").textContent = (lang === "ru" ? "Правила сохранены: " : "Rules saved: ") + result.rules
        + (lang === "ru" ? ". Вернитесь в JVM для запуска или нажмите «Изменить ещё»." : ". Return to the JVM to run the scenario, or choose Edit again.");
      $("overlay").classList.add("show");
    } catch (error) { showError(error); }
    finally { submitting = false; document.body.inert = false; $("submit").disabled = submitted; }
  };
  download = async function () {
    try {
      if (hasDraft()) throw new Error("Apply or discard the pending field or dialog edit before downloading.");
      const text = await (await request("export")).text();
      const url = URL.createObjectURL(new Blob([text], {type: "application/json"}));
      const link = document.createElement("a"); link.href = url; link.download = "sidecar.json"; link.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000); toast(T().dlsaved);
    } catch (error) { showError(error); }
  };
  $("dl").onclick = () => download();

  // Use the existing JVM preview, including configured bonds, optional branches and scan bounds.
  // Never replace an unavailable JVM count with the QA co-occurrence estimate.
  updateImpact = function () {
    clearTimeout(_impactTimer);
    const sequence = ++impactSequence;
    $("impacthint").textContent = "…";
    _impactTimer = setTimeout(async () => {
      try {
        const data = await (await request("impact")).json();
        if (sequence !== impactSequence) return;
        if (data.exact) {
          const total = BigInt(data.total), kept = BigInt(data.kept), removed = total - kept;
          $("impact").innerHTML = `<b>${removed.toLocaleString()}</b> / ${total.toLocaleString()} · <span class="hint">${T().kept(kept.toLocaleString())}</span> <span class="pill r" style="font-size:10px">exact</span>`;
          $("bar").style.width = (total ? Number(removed * 10000n / total) / 100 : 0) + "%";
          $("impacthint").textContent = lang === "ru" ? "Точный подсчёт Framework; SUT не запускался." : "Exact Framework count; the SUT was not executed.";
          emptyResult = kept === 0n && total > 0n && bonds.length > 0;
        } else {
          $("impact").textContent = lang === "ru" ? "Точный результат пока неизвестен" : "Exact result not yet known";
          $("bar").style.width = "0%"; $("impacthint").textContent = data.note; emptyResult = false;
        }
        refreshWarn();
      } catch (error) {
        if (sequence !== impactSequence) return;
        $("impact").textContent = lang === "ru" ? "Подсчёт недоступен" : "Count unavailable";
        $("bar").style.width = "0%"; $("impacthint").textContent = error.message; emptyResult = false;
      }
    }, 300);
  };
  window.addEventListener("beforeunload", event => {
    const current = bonds.map(bond => { const value = {...bond}; delete value._jvmSource; return value; });
    if (!submitted && (hasDraft() || JSON.stringify(current) !== JSON.stringify(baseline) || JSON.stringify(params) !== JSON.stringify(initialParams))) {
      event.preventDefault(); event.returnValue = "";
    }
  });
  updateImpact();
})();
