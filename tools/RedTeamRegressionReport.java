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

import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Xml;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Original redteam2 observed dual-version records and measured family rates; shared native run, with no external service. */
public final class RedTeamRegressionReport {
    public static void main(String[] args)throws Exception {
        Path scenario=null,output=null,addon=null;String workers="1";
        for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){
            case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->output=Path.of(args[i+1]);case "--addon"->addon=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option");}}
        if(scenario==null||output==null||addon==null)throw new IllegalArgumentException("scenario,out,addon required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-redteam-regression-v2","original local mock"),new ConfigResolver.Override("cf.execution.classpath",addon.toAbsolutePath().toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",workers,"source worker invariant"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core profile"),new ConfigResolver.Override("cf.assembly.distinct","true","original Core profile")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var lease=budget.reserve(16L*1024*1024,"local redteam observed XML report")){
            if(lease.bytes()!=16L*1024*1024)throw new IllegalStateException("invalid report reservation");
            try(var prepared=session.prepareRun(scenario)){
                var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException(summary.diagnostics().toString());
                try(var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8)){
                    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<local-redteam-regression-report observations=\""+run.finals().size()+"\" samples=\""+summary.samples()+"\">\n");
                    for(var observation:run.finals()){
                        if(!observation.outcome().isDomainVerdict())throw new IllegalStateException("non-domain observed support: "+observation.failureMessage());
                        var domain=observation.domain().orElseThrow();out.write("<observation candidate=\""+observation.key().candidateId().hex()+"\" outcome=\""+observation.outcome()+"\"><metrics>"+Xml.escape(Json.write(domain.metrics()))+"</metrics><fields>"+Xml.escape(Json.write(domain.attributes()))+"</fields></observation>\n");
                    }
                    var analysis=org.ghyura.cf.fixtures.redteam.RedTeamRegressionAnalysis.summarize(run.finals().stream().map(o->o.domain().orElseThrow()).toList());
                    out.write("<regression max_delta_percent=\""+analysis.maxDelta()+"\" unchanged=\""+analysis.unchanged()+"\" timing_scope=\"measured local completion; original HTTP/CPU-DoS latency not equivalent\"><app2-vulnerabilities>"+Xml.escape(Json.write(analysis.app2Vulnerabilities()))+"</app2-vulnerabilities>");
                    for(var family:analysis.families())out.write("<family name=\""+family.name()+"\" app2_breaches=\""+family.app2().breaches()+"\" app2_total=\""+family.app2().total()+"\" app2_percent=\""+family.app2().percent()+"\" app3_breaches=\""+family.app3().breaches()+"\" app3_total=\""+family.app3().total()+"\" app3_percent=\""+family.app3().percent()+"\" delta_percent=\""+family.absoluteDelta()+"\"/>");
                    out.write("</regression></local-redteam-regression-report>\n");
                }
                System.out.println(summary.samples()+" samples;"+summary.outcomes());
            }
        }
        if(budget.reservedBytes()!=0)throw new IllegalStateException("unreleased report reservation");
    }
}
