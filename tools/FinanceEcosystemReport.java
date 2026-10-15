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

/** Finance evolving-world observed records; shared native run, with no external service. */
public final class FinanceEcosystemReport {
    private static String xmlText(String value) {
        return Xml.escape(value).replace("&quot;", "\"").replace("&apos;", "'");
    }
    public static void main(String[] args)throws Exception {
        Path scenario=null,output=null,addon=null;String workers="1";
        for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){
            case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->output=Path.of(args[i+1]);case "--addon"->addon=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option");}}
        if(scenario==null||output==null||addon==null)throw new IllegalArgumentException("scenario,out,addon required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","finance-stack-ecosystem","local source ecosystem"),new ConfigResolver.Override("cf.execution.classpath",addon.toAbsolutePath().toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",workers,"source worker invariant"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core profile"),new ConfigResolver.Override("cf.assembly.distinct","true","original Core profile")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var lease=budget.reserve(16L*1024*1024,"local source ecosystem observed XML report")){
            if(lease.bytes()!=16L*1024*1024)throw new IllegalStateException("invalid report reservation");
            try(var prepared=session.prepareRun(scenario)){
                var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException(summary.diagnostics().toString());
                try(var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8)){
                    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<finance-stack-ecosystem-report observations=\""+run.finals().size()+"\" samples=\""+summary.samples()+"\">\n");
                    for(var observation:run.finals()){
                        if(!observation.outcome().isDomainVerdict())throw new IllegalStateException("non-domain observed support: "+observation.failureMessage());
                        var domain=observation.domain().orElseThrow();out.write("<observation candidate=\""+observation.key().candidateId().hex()+"\" outcome=\""+observation.outcome()+"\"><metrics>"+xmlText(Json.write(domain.metrics()))+"</metrics><fields>"+xmlText(Json.write(domain.attributes()))+"</fields></observation>\n");
                    }
                    out.write("</finance-stack-ecosystem-report>\n");
                }
                System.out.println(summary.samples()+" samples;"+summary.outcomes());
            }
        }
        if(budget.reservedBytes()!=0)throw new IllegalStateException("unreleased report reservation");
    }
}
