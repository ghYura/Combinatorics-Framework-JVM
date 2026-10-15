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
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.model.*;
import org.ghyura.cf.fixtures.redteam.AttackDefenseMatrix;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Observed source matrix/frontier report through the existing native run service. */
public final class AttackDefenseReport {
    private static String element(String text){return Xml.escape(text).replace("&quot;","\"").replace("&apos;","'");}
    public static void main(String[] args)throws Exception{
        Path scenario=null,output=null,addon=null;String workers="1";for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->output=Path.of(args[i+1]);case "--addon"->addon=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option");}}if(scenario==null||output==null||addon==null)throw new IllegalArgumentException("scenario,out,addon required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-attack-defense","source matrix"),new ConfigResolver.Override("cf.execution.classpath",addon.toAbsolutePath().toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",workers,"native workers"),new ConfigResolver.Override("cf.generation.distinctTables","true","source profile"),new ConfigResolver.Override("cf.assembly.distinct","true","source profile")));
        try(var prepared=session.prepareRun(scenario)){var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED||!summary.reconciliation().consistent())throw new IllegalStateException(summary.diagnostics().toString());var scores=new ArrayList<AttackDefenseMatrix.Scored>();
            try(var out=Files.newBufferedWriter(output,StandardCharsets.UTF_8)){out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<attack-defense-report samples=\""+summary.samples()+"\">\n");for(var o:run.finals()){if(!o.outcome().isDomainVerdict())throw new IllegalStateException(o.failureMessage());var d=o.domain().orElseThrow();var labels=new TreeMap<String,String>();((Map<?,?>)Json.parse(d.attributes().get("labels"),"observed labels")).forEach((k,v)->labels.put((String)k,(String)v));var c=AttackDefenseMatrix.fromLabels(labels);scores.add(new AttackDefenseMatrix.Scored(c,(int)d.metrics().get("severity").doubleValue(),(int)d.metrics().get("cost_tokens").doubleValue()));out.write("<observation candidate=\""+o.key().candidateId().hex()+"\" outcome=\""+o.outcome()+"\"><metrics>"+element(Json.write(d.metrics()))+"</metrics><fields>"+element(Json.write(d.attributes()))+"</fields></observation>\n");}out.write("<summary>"+element(Json.write(AttackDefenseMatrix.summarize(scores)))+"</summary>\n</attack-defense-report>\n");}System.out.println(summary.samples()+" samples;"+summary.outcomes());
        }
    }
}
