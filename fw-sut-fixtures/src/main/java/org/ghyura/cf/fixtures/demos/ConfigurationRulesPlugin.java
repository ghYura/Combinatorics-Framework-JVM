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

package org.ghyura.cf.fixtures.demos;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Exact D4 atoms are configuration data; the separate sheet ending is not part of a raw selection cell. */
public final class ConfigurationRulesPlugin implements SutPlugin {
    public static final String ID="demo-d4-configuration-rules";
    private static final Pattern ATOM=Pattern.compile("(env|mode|transport|feature)\\(\"([^\"]*)\"\\);|workers\\(([12])\\);|debug\\(\\);");
    @Override public String id(){return ID;}
    @Override public String version(){return "04-d4@0bc3e5a/java-1";}
    @Override public Map<Integer,String> verdictNames(){return Map.of(0,"PASS",2,"DOMAIN_FAIL");}
    private static String one(SutCandidate c,String name){var s=c.selection(name).orElseThrow(()->new IllegalArgumentException("missing "+name));if(!s.inCandidate()||s.values().size()!=1)throw new IllegalArgumentException("one "+name+" required");return s.values().getFirst();}
    public static ConfigurationRules.Config parseAtoms(List<String> atoms) {
        Map<String,String> fields=new TreeMap<>();List<String> features=new ArrayList<>();boolean debug=false;
        for(String atom:atoms){var m=ATOM.matcher(atom);int end=0;while(m.find()){
            if(m.start()!=end)throw new IllegalArgumentException("malformed D4 atom");end=m.end();
            if(m.group(1)!=null){String fn=m.group(1),arg=m.group(2);if(fn.equals("feature"))features.add(arg);else if(fields.putIfAbsent(fn,arg)!=null)throw new IllegalArgumentException("repeated "+fn);}
            else if(m.group(3)!=null){if(fields.putIfAbsent("workers",m.group(3))!=null)throw new IllegalArgumentException("repeated workers");}
            else{if(debug)throw new IllegalArgumentException("repeated DEBUG");debug=true;}
        }if(end!=atom.length()||end==0)throw new IllegalArgumentException("malformed D4 atom");}
        return new ConfigurationRules.Config(fields.get("env"),fields.get("mode"),fields.get("transport"),features,fields.containsKey("workers")?Integer.parseInt(fields.get("workers")):0,debug);
    }
    @Override public SutSession open(ExecutionContext context){return new SutSession(){
        @Override public DomainObservation evaluate(SutCandidate c,ExecutionContext ctx){
            ctx.cancellation().throwIfCancelled();try(var memory=ctx.reserve(131072,"D4 gzip envelope and independent oracle")){
                if(memory.bytes()!=131072)throw new IllegalStateException("invalid D4 reservation");one(c,"HEAD");
                String tail=one(c,"TAIL"),phase;
                if(tail.equals("_verdict = finish(\"main\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))phase="main";
                else if(tail.equals("_verdict = finish(\"controls\")\nFW_VAR = _verdict\nFW_CUSTOM_VAR = FW_VAR\n"))phase="controls";
                else throw new IllegalArgumentException("unknown D4 finish atom");
                String policyAtom=one(c,"IMPL");var policyMatch=Pattern.compile("impl\\(\"([^\"]*)\"\\);").matcher(policyAtom);
                if(!policyMatch.matches())throw new IllegalArgumentException("malformed D4 policy atom");String policy=policyMatch.group(1);
                List<String> atoms=new ArrayList<>();
                if(phase.equals("controls"))atoms.add(one(c,"CONFIG"));else{
                    for(String sheet:List.of("ENV","MODE","TRANSPORT","WORKERS"))atoms.add(one(c,sheet));
                    var f=c.selection("FEATURES").orElseThrow();if(!f.inCandidate()||f.values().size()!=2)throw new IllegalArgumentException("two FEATURES required");atoms.addAll(f.values());
                    var d=c.selection("DEBUG").orElseThrow();if(d.inCandidate()){if(!d.values().equals(List.of("debug();")))throw new IllegalArgumentException("one DEBUG atom required");atoms.addAll(d.values());}
                }
                var cfg=parseAtoms(atoms);var r=ConfigurationRules.evaluate(phase,policy,cfg);var judgment=r.judgment();Map<String,String> a=new TreeMap<>();
                a.put("case",r.caseId());a.put("phase",phase);a.put("policy",policy);a.put("env",cfg.env());a.put("mode",cfg.mode());a.put("transport",cfg.transport());a.put("features",String.join("+",cfg.features()));a.put("workers",Integer.toString(cfg.workers()));a.put("debug",cfg.debug()?"1":"0");
                a.put("accepted",Boolean.toString(r.observed().accepted()));a.put("violations",String.join(" ",r.observed().violations()));a.put("expected_violations",String.join(" ",judgment.expectedViolations()));a.put("acceptance_ok",Boolean.toString(judgment.acceptanceOk()));a.put("transformation",judgment.transformation().isEmpty()?"not_applicable":judgment.transformation().toString());a.put("failure_classes",String.join(" ",judgment.failureClasses()));
                if(r.observed().envelope()!=null){var e=r.observed().envelope();a.put("url",e.url());a.put("headers",e.headers().toString());a.put("body_b64",e.bodyBase64());}
                return new DomainObservation(judgment.passed()?0:2,Map.of("accepted",r.observed().accepted()?1d:0d,"acceptance_ok",judgment.acceptanceOk()?1d:0d,"failure_count",(double)judgment.failureClasses().size()),a);
            }
        }
        @Override public void close(){}
    };}
}
