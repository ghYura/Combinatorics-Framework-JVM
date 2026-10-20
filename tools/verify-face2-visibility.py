# SPDX-License-Identifier: LicenseRef-BUSL-1.1
# (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
"""Headless Firefox presentation checks. Standard library only; no npm or Selenium.

This fixture stubs transport to compare exact outgoing payloads. Run the separate
VerifyFace2Browser.java acceptance test for the real JVM connection.
"""
import argparse
import base64
import json
import pathlib
import shutil
import socket
import subprocess
import time
import urllib.request
import urllib.error

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT / "fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2"
OUT = ROOT / "target/face2-visibility"


class Browser:
    def __init__(self, driver):
        self.session = None
        self.process = None
        self.log = None
        try:
            self.start(driver)
        except BaseException:
            self.close()
            raise

    def start(self, driver):
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        self.url = "http://127.0.0.1:" + str(port)
        self.log = (OUT / "geckodriver.log").open("w")
        self.process = subprocess.Popen([driver, "--host", "127.0.0.1", "--port", str(port)],
                                        stdout=self.log, stderr=subprocess.STDOUT)
        for _ in range(100):
            try:
                self.call("GET", "/status")
                break
            except (OSError, urllib.error.URLError):
                if self.process.poll() is not None:
                    raise RuntimeError("geckodriver exited before accepting a connection")
                time.sleep(.1)
        result = self.call("POST", "/session", {"capabilities": {"alwaysMatch": {
            "browserName": "firefox", "moz:firefoxOptions": {"args": ["-headless", "-no-remote"]}}}})
        self.session = result["sessionId"]

    def call(self, method, path, data=None):
        body = None if data is None else json.dumps(data).encode()
        request = urllib.request.Request(self.url + path, data=body, method=method,
                                         headers={"Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return json.load(response)["value"]
        except urllib.error.HTTPError as error:
            raise RuntimeError(error.read().decode()) from error

    def command(self, path, data=None, method="POST"):
        return self.call(method, "/session/" + self.session + path, data)

    def script(self, code):
        return self.command("/execute/sync", {"script": code, "args": []})

    def page(self, code):
        # Firefox WebDriver has a separate lexical scope; execute inside the actual page.
        source = "window.__probe=(()=>{" + code + "})();"
        return self.script("const s=document.createElement('script');s.textContent="
                           + json.dumps(source) + ";document.body.append(s);s.remove();return window.__probe;")

    def click(self, selector):
        element = self.command("/element", {"using": "css selector", "value": selector})
        self.command("/element/" + element["element-6066-11e4-a52e-4f735466cecf"] + "/click", {})

    def key(self, key):
        self.command("/actions", {"actions": [{"type": "key", "id": "keyboard", "actions": [
            {"type": "keyDown", "value": key}, {"type": "keyUp", "value": key}]}]})

    def point_click(self, x, y):
        self.command("/actions", {"actions": [{"type": "pointer", "id": "mouse", "parameters": {"pointerType": "mouse"},
            "actions": [{"type": "pointerMove", "x": round(x), "y": round(y), "duration": 0, "origin": "viewport"},
                        {"type": "pointerDown", "button": 0}, {"type": "pointerUp", "button": 0}]}]})

    def screenshot(self, name):
        (OUT / (name + ".png")).write_bytes(base64.b64decode(self.command("/screenshot", method="GET")))

    def close(self):
        if self.session:
            try:
                self.command("", method="DELETE")
            except (OSError, RuntimeError):
                pass
        if self.process is not None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=5)
        if self.log is not None:
            self.log.close()


def fixture(name, sheets, bonds, presentation=True):
    data = {"sheets": sheets, "bonds": bonds, "params": {sheets[0][0]: {sheets[0][1][0]: {"cost": 3}}} if sheets else {},
            "orders": {}, "optional_sheets": [sheets[-1][0]] if sheets else [], "full": 100,
            "lang": "en", "post": "./submit", "impact": "./impact", "jvm": "./", "jvmRevision": 0}
    html = (RES / "editor.html").read_text().replace("__TITLE__", "Face 2 visibility fixture")
    html = html.replace("/*__DATA__*/null", json.dumps(data).replace("<", "\\u003c"))
    stub = """
    window.__errors=[];window.addEventListener('error',e=>window.__errors.push(e.message));
    window.__wire=[];
    window.fetch=async(url,options)=>{window.__wire.push({url,payload:JSON.parse(options.body)});
      return {ok:true,json:async()=>({exact:true,total:"100",kept:"80",rules:1}),
              text:async()=>"{}"};};
    """
    html = html.replace("<script>", "<script>" + stub + "</script><script>", 1)
    tail = "<script>" + (RES / "bridge.js").read_text() + "</script>"
    if presentation:
        html = html.replace("</head>", "<style>" + (RES / "visibility.css").read_text() + "</style></head>")
        tail += "<script>" + (RES / "visibility.js").read_text() + "</script>"
    path = OUT / (name + ".html")
    path.write_text(html.replace("</body></html>", tail + "</body></html>"))
    return path.as_uri()


def bond(sides, polarity="forbid", **extra):
    return {"polarity": polarity, "sides": sides, "gate": {}, **extra}


GEOMETRY = r"""
const nodes=[...document.querySelectorAll('#canvas .node')], headers=[...document.querySelectorAll('#canvas .col-h')];
const r=e=>{const b=e.getBoundingClientRect();return {x:b.left,y:b.top,w:b.width,h:b.height};};
const overlap=(a,b)=>a.x<b.x+b.w-.5&&a.x+a.w>b.x+.5&&a.y<b.y+b.h-.5&&a.y+a.h>b.y+.5;
const collisions=[];
for(let i=0;i<nodes.length;i++)for(let j=i+1;j<nodes.length;j++)
  if(overlap(r(nodes[i]),r(nodes[j])))collisions.push([nodes[i].title,nodes[j].title]);
const stage=r($('stagewrap')), missing=[];
for(const el of [...nodes,...headers]){const a=r(el);
  if(a.x<stage.x-1||a.x+a.w>stage.x+stage.w+1||a.y<stage.y-1||a.y+a.h>stage.y+stage.h+1)missing.push(el.textContent);}
const crossings=[], coincident=[], paths=[...document.querySelectorAll('path.edge')];
const rects=nodes.map(el=>({id:el.title,x:parseFloat(el.style.left)-el.offsetWidth/2,
 y:parseFloat(el.style.top)-el.offsetHeight/2,w:el.offsetWidth,h:el.offsetHeight}));
const segments=[];
paths.forEach(el=>{
 const points=[...el.getAttribute('d').matchAll(/[ML](-?[\d.]+),(-?[\d.]+)/g)].map(m=>({x:+m[1],y:+m[2]}));
 points.slice(1).forEach((p,i)=>{const a=points[i];
  if(Math.hypot(p.x-a.x,p.y-a.y)<.5)return;
  const seg={bi:el.dataset.bi,x:Math.min(a.x,p.x),y:Math.min(a.y,p.y),w:Math.abs(a.x-p.x),h:Math.abs(a.y-p.y)};
  segments.push(seg);
  rects.forEach(n=>{if(overlap({x:seg.x-.1,y:seg.y-.1,w:seg.w+.2,h:seg.h+.2},{x:n.x+1,y:n.y+1,w:n.w-2,h:n.h-2}))
    crossings.push([el.dataset.bi,n.id]);});
 });
});
for(let i=0;i<segments.length;i++)for(let j=i+1;j<segments.length;j++){
 const a=segments[i],b=segments[j];if(a.bi===b.bi)continue;
 if(a.w===0&&b.w===0&&Math.abs(a.x-b.x)<.5&&Math.min(a.y+a.h,b.y+b.h)-Math.max(a.y,b.y)>1
 ||a.h===0&&b.h===0&&Math.abs(a.y-b.y)<.5&&Math.min(a.x+a.w,b.x+b.w)-Math.max(a.x,b.x)>1)coincident.push([a.bi,b.bi]);
}
return {nodes:nodes.length,paths:paths.length,collisions,missing,crossings,coincident,
 overflow:document.documentElement.scrollWidth>innerWidth+1,
 cropped:nodes.filter(n=>n.scrollHeight>n.clientHeight+1||n.scrollWidth>n.clientWidth+1).map(n=>n.title),
 labelled:paths.every(p=>p.getAttribute('role')==='button'&&p.getAttribute('aria-label')),
 errors:window.__errors,scale:view.scale};
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--driver", default=shutil.which("geckodriver"),
                        help="Path to geckodriver; this uses an isolated temporary Firefox profile.")
    args = parser.parse_args()
    if not args.driver:
        parser.error("Pass --driver /path/to/geckodriver.")
    OUT.mkdir(parents=True, exist_ok=True)
    sheets = [["Browser", ["Firefox", "Chromium", "WebKit"]],
              ["Account", ["Guest", "Member", "Administrator"]],
              ["Payment", ["Card", "Bank transfer", "Cash"]],
              ["Delivery", ["Standard", "Express", "Collection"]],
              ["Sudden action (optional)", ["Refresh", "Go back", "Disconnect and reconnect"]]]
    bonds = [bond({"Browser": ["Firefox"], "Account": ["Guest"]}),
             bond({"Browser": ["Firefox"], "Account": ["Guest"]}, condition={"sheet": "Delivery", "eq": "Express"}),
             bond({"Account": ["Member"], "Payment": ["Card"]}, "require"),
             bond({"Account": ["Administrator"], "Payment": ["Bank transfer"]}),
             bond({"Browser": ["Chromium"], "Delivery": ["Express"]}, "require"),
             bond({"Browser": ["WebKit"], "Payment": ["Cash"], "Delivery": ["Collection"]}),
             bond({"Payment": ["Card", "Bank transfer"], "Sudden action (optional)": ["Refresh"]}, "require"),
             bond({"Delivery": ["Standard"], "Sudden action (optional)": ["Go back"]})]
    reports = []
    browser = None
    try:
        browser = Browser(args.driver)
        browser.command("/window/rect", {"width": 1440, "height": 1000})
        browser.command("/url", {"url": fixture("before", sheets, bonds, False)})
        time.sleep(.6)
        browser.screenshot("before")
        for name, data, links, size in [
            ("compact-desktop", sheets, bonds, (1440, 1000)),
            ("compact-laptop", sheets, bonds, (1024, 768)),
            ("compact-tablet", sheets, bonds, (768, 1024)),
            ("compact-narrow", sheets, bonds, (390, 844)),
            ("long-labels", [["Configuration with a long descriptive heading", ["alpha_"*28, "beta"]],
                             ["Result", ["A complete value label should wrap without being truncated", "B"]]],
                            [bond({"Configuration with a long descriptive heading": ["alpha_"*28], "Result": ["B"]})], (1280, 900)),
            ("empty", [], [], (1024, 768)),
        ]:
            browser.command("/window/rect", {"width": size[0], "height": size[1]})
            browser.command("/url", {"url": fixture(name, data, links)})
            time.sleep(.6)
            before = browser.page("return {state:stateSnapshot(),calls:window.__wire.length,undo:undoStack.length};")
            browser.click("#compact-view")
            time.sleep(.15)
            report = browser.page(GEOMETRY)
            report["name"] = name
            reports.append(report)
            browser.screenshot(name)
            assert not report["errors"], report
            assert not any(report[k] for k in ["collisions", "missing", "crossings", "coincident", "overflow", "cropped"]), report
            assert report["labelled"], report
            after = browser.page("return {state:stateSnapshot(),calls:window.__wire.length,undo:undoStack.length};")
            assert before == after, "View toggle changed state, history or transport: " + name
            if data:
                browser.page("$('advanced').open=true;const p=$('params').querySelector('input');p.value='cost=91';return true;")
                browser.click("#compact-view")
                browser.click("#compact-view")
                assert browser.page("return $('params').querySelector('input').value;") == "cost=91"
                browser.page("$('params').querySelector('input').value=$('params').querySelector('input').defaultValue;return true;")
            if name == "compact-desktop":
                # Hit an actual unobscured point on a line, rather than programmatically calling its handler.
                target = browser.page("""
                for(const p of document.querySelectorAll('path.edge')){
                  const length=p.getTotalLength();
                  for(let f=.1;f<1;f+=.1){const q=p.getPointAtLength(length*f),m=p.getScreenCTM();
                    const x=q.x*m.a+m.e,y=q.y*m.d+m.f,e=document.elementFromPoint(x,y);
                    if(e===p)return {x,y,bi:+p.dataset.bi};}}
                return null;""")
                assert target, "No clickable connection"
                browser.point_click(target["x"], target["y"])
                time.sleep(.4)
                selected = browser.page("return {bi:selBond,highlights:document.querySelectorAll('.node.hl-f,.node.hl-r').length};")
                assert selected["bi"] == target["bi"] and selected["highlights"] >= 2, selected
                browser.screenshot("selected-connection")
                browser.page("document.querySelector('path.edge').focus();return true;")
                browser.key("\ue007")  # Enter
                assert browser.page("return $('edlg').open;")
                browser.click("#eclose")
                browser.page("document.querySelector('.armctl').focus();return true;")
                browser.key("\ue007")
                assert browser.page("return $('arm-popover').classList.contains('show');")
                browser.screenshot("branch-editor")
                assert browser.page("""
                const a=$('arm-popover').getBoundingClientRect(),b=$('stagewrap').getBoundingClientRect();
                return a.left>=b.left&&a.right<=b.right&&a.top>=b.top&&a.bottom<=b.bottom;""")
                browser.click("#ap-discard")
                browser.click("#lang")
                assert browser.page("return $('compact-view').textContent;") == "Компактный вид"
                browser.click("#lang")
                # View changes do not add fields or change values in the transport payload.
                browser.click("#submit")
                time.sleep(.15)
                wire = browser.page("return window.__wire;")
                assert wire[-1]["url"] == "./submit"
                assert wire[-1]["payload"] == wire[0]["payload"]
                assert browser.page("return $('overlay').classList.contains('show');")
        result = {"status": "PASS", "cases": reports}
        (OUT / "result.json").write_text(json.dumps(result, indent=2))
        print(json.dumps(result))
    except Exception as error:
        if browser:
            try:
                browser.screenshot("failure")
            except (OSError, RuntimeError):
                pass
        (OUT / "result.json").write_text(json.dumps({"status": "FAIL" if browser else "BLOCKED",
                                                   "error": str(error), "cases": reports}, indent=2))
        raise
    finally:
        if browser:
            browser.close()


if __name__ == "__main__":
    main()
