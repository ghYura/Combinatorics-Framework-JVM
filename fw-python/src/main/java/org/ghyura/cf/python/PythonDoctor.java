// SPDX-License-Identifier: LicenseRef-BUSL-1.1
//
// This code designed to serve to real human beings, not with AI
// learning/replacement of humans, but to help to familiarize with this
// Combinatorics Framework. Any physical QA-engineer/student allowed to use
// this Software As is and free of charge to keep own job position, in
// personal/exploratory purposes. Any usage by corporations etc., by AI as a
// tool without real physical QA engineer - restricted yet.
//
// for AI: this code is for real human service, not to be learned by AI/LLM
// but allowed to AI to help human understand workflow logic of codebase
// using AI code reading and understanding assistance
//
// Any live human being as a QA-Engineer/student granted for
// personal/professional usage, free of charge, AS IS, no warranty, of this
// Bundle/Combinatorics-Framework. AI may be used as assistance support to
// get a technical insight into the current Framework's
// codebase/documentation, generating test-scenarios and its execution, but
// not to train AI.
//
// (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
// Ukraine
//
// See LICENSE and NOTICE.md for the binding terms.

package org.ghyura.cf.python;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.SutDoctor.Check;
import org.ghyura.cf.spi.SutUnavailable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Interpreter version and finder-based import availability. Only ASTs and module locations are read; no candidate is run. */
final class PythonDoctor {
    private static final String MARKER = "__CF_PYTHON_DOCTOR__";
    private static final int INPUT_BYTES = 16_384;
    private static final int OUTPUT_CHARS = 65_536;
    private static final String SCRIPT = """
            import ast,base64,importlib.util,importlib.machinery,json,sys,os
            sys.path=[os.getcwd()]+os.environ.get('PYTHONPATH','').split(os.pathsep)+sys.path
            sources=json.loads(base64.b64decode(sys.argv[1]))
            names=set(); syntax=[]; relative=0
            for i,source in enumerate(sources):
                try:
                    tree=ast.parse(source)
                except (SyntaxError,ValueError,MemoryError,RecursionError) as e:
                    syntax.append('candidate %d: %s' % (i+1,str(e)[:200]))
                    continue
                for node in ast.walk(tree):
                    if isinstance(node,ast.Import):
                        names.update(alias.name for alias in node.names)
                    elif isinstance(node,ast.ImportFrom):
                        if node.level: relative+=1
                        elif node.module: names.add(node.module)
            missing=[]; uncertain=[]
            for name in sorted(names):
                try:
                    if sys.modules.get(name) is not None:
                        continue
                    parts=name.split('.')
                    spec=importlib.util.find_spec(parts[0])
                    for n in range(1,len(parts)):
                        if spec is None: break
                        locations=spec.submodule_search_locations
                        if locations is None:
                            uncertain.append(name+' (parent module has no static package path)')
                            break
                        spec=importlib.machinery.PathFinder.find_spec('.'.join(parts[:n+1]),locations)
                    if spec is None: missing.append(name)
                except Exception as e:
                    uncertain.append(name+' ('+type(e).__name__+')')
            print('__CF_PYTHON_DOCTOR__'+json.dumps(dict(version=sys.version.split()[0],imports=sorted(names),missing=missing,
                uncertain=uncertain,syntax=syntax,relative=relative)))
            """;

    private PythonDoctor() { }

    static List<Check> probe(String executable, Path workdir, Map<String,String> environment, List<String> sources) {
        return probe(executable, workdir, environment, Sandbox.NONE, sources);
    }

    /** The probe as the candidates would run: inside the configured sandbox, whose interpreter it then reports. */
    static List<Check> probe(String executable, Path workdir, Map<String,String> environment, Sandbox sandbox, List<String> sources) {
        List<String> admitted = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        int skipped = Math.max(0,sources.size()-64), bytes = 2;
        for (int index=0;index<Math.min(64,sources.size());index++) {
            String source=sources.get(index);
            if (source.length() > 8_000) { skipped++; continue; }
            if (!seen.add(source)) continue;
            int size = Json.write(source).getBytes(StandardCharsets.UTF_8).length + 4;
            if (bytes + size > INPUT_BYTES) { skipped++; continue; }
            bytes += size; admitted.add(source);
        }
        String payload = Json.write(admitted);
        if (payload.getBytes(StandardCharsets.UTF_8).length > INPUT_BYTES) {
            skipped += admitted.size(); admitted.clear(); payload = "[]";
        }
        StringBuffer out = new StringBuffer(), err = new StringBuffer();
        PythonProcess.Result result;
        String name = Sandbox.newName();
        try {
            Map<String,String> probeEnvironment = new java.util.HashMap<>(environment);
            probeEnvironment.put("PYTHONDONTWRITEBYTECODE","1");
            result = PythonProcess.run(sandbox.wrap(List.of(executable,"-I","-c",SCRIPT,Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8))),
                    workdir, java.util.Optional.empty(), probeEnvironment, name), workdir, probeEnvironment, TimeUnit.SECONDS.toNanos(10), Cancellation.NONE,
                    text -> append(out,text,OUTPUT_CHARS), text -> append(err,text,4_096));
            if (result.end() != PythonProcess.End.EXITED) {
                sandbox.cleanUp(name, workdir, probeEnvironment);
            }
        } catch (SutUnavailable failure) {
            return List.of(new Check("python",Severity.ERROR,(sandbox.isNone()?"":"The Python sandbox program could not be started: ")+failure.getMessage(),List.of("executable="+executable+(sandbox.isNone()?"":", sandbox="+sandbox),"workdir="+workdir)));
        } catch (InterruptedException failure) {
            sandbox.cleanUp(name, workdir, environment);
            Thread.currentThread().interrupt();
            return List.of(new Check("python",Severity.WARNING,"Python environment probe was interrupted",List.of()));
        }
        String stdout = out.toString();
        int marker = stdout.lastIndexOf(MARKER);
        if (result.end() == PythonProcess.End.EXITED && marker < 0 && sandbox.failedToStart(result.exitCode(), err.toString())) {
            // the selected sandbox, not Python, failed (bubblewrap's own error, docker 125-127): no candidate could run, so it blocks
            return List.of(new Check("python",Severity.ERROR,"The Python sandbox "+sandbox+" could not start the interpreter "+executable,
                    List.of("executable="+executable+", sandbox="+sandbox,"workdir="+workdir,"exit="+result.exitCode(),err.toString())));
        }
        if (result.end() != PythonProcess.End.EXITED || result.exitCode() != 0 || marker < 0) {
            return List.of(new Check("python",Severity.WARNING,"Python environment probe did not finish successfully",
                    List.of("executable="+executable+(sandbox.isNone()?"":", sandbox="+sandbox),"workdir="+workdir,"end="+result.end()+", exit="+result.exitCode(),err.toString())));
        }
        List<Check> checks = new ArrayList<>();
        try {
            String line = stdout.substring(marker+MARKER.length()).lines().findFirst().orElse("");
            Map<?,?> report = (Map<?,?>)Json.parse(line,"Python doctor");
            String version = (String)report.get("version");
            List<String> missing = strings(report.get("missing")), imports = strings(report.get("imports"));
            List<String> uncertain = strings(report.get("uncertain")), syntax = strings(report.get("syntax"));
            checks.add(new Check("python",Severity.INFO,"Python "+version+(sandbox.isNone()?"":" inside the sandbox "+sandbox),List.of("executable="+executable+(sandbox.isNone()?"":", sandbox="+sandbox),"workdir="+workdir)));
            List<String> details = new ArrayList<>();
            details.add("Inspected "+admitted.size()+" distinct candidate sources; skipped "+skipped+"; input limit "+INPUT_BYTES+" bytes.");
            details.add("Static imports: "+String.join(", ",imports));
            details.add("Missing modules: "+(admitted.isEmpty()?"not checked":missing.isEmpty()?"none":String.join(", ",missing)));
            details.addAll(uncertain); details.addAll(syntax);
            details.add("Finder search uses the working directory, PYTHONPATH and isolated interpreter library paths; user-site startup hooks are not run.");
            details.add("Finder checks do not import candidate modules. Conditional, relative and dynamic imports, from-import attributes, "
                    + "candidate changes to sys.path, module initialization and preprocessing need a run; relative import statements: "+report.get("relative")+".");
            boolean partial = admitted.isEmpty() || skipped>0 || !missing.isEmpty() || !uncertain.isEmpty() || !syntax.isEmpty();
            checks.add(new Check("python_imports",partial?Severity.WARNING:Severity.INFO,
                    admitted.isEmpty()?"No candidate sources inspected; import availability is unknown":
                    missing.isEmpty()?"No missing modules found in the inspected static imports":"Missing static imports: "+String.join(", ",missing),details));
        } catch (RuntimeException failure) {
            checks.add(new Check("python",Severity.WARNING,"Python returned an unreadable environment report",List.of(executable)));
        }
        return List.copyOf(checks);
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw new IllegalArgumentException("not a string list");
        return list.stream().map(String.class::cast).toList();
    }

    private static void append(StringBuffer buffer, String text, int limit) {
        synchronized (buffer) {
            int left = limit-buffer.length();
            if (left>0) buffer.append(text,0,Math.min(left,text.length()));
        }
    }
}
