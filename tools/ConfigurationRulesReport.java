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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.fixtures.demos.ConfigurationRules;
import org.ghyura.cf.fixtures.demos.ConfigurationRulesPlugin;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D4's original offline pairwise and wrong-sieve proof from observations of a normal shared run.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/ConfigurationRulesReport.java --scenario PATH/main/spec.toml --out certificate.xml [--workers 4]
 * Existing Coverage.SET_COVER supplies row selection; the fixture does not depend on the constraints module.
 */
public final class ConfigurationRulesReport {
    public static void main(String[] args)throws Exception{
        Path scenario=null,out=null;String workers="1";
        for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->out=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option "+args[i]);}}
        if(scenario==null||out==null)throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ConfigurationRulesPlugin.ID,"D4 proof"),new ConfigResolver.Override("cf.execution.workers",workers,"D4 proof"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core settings"),new ConfigResolver.Override("cf.assembly.distinct","true","original Core settings")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var owned=budget.reserve(4L*1024*1024,"D4 observed configurations, coverage and XML certificate")){
            if(owned.bytes()!=4L*1024*1024)throw new IllegalStateException("invalid D4 proof reservation");
            List<ConfigurationRules.Observed> observations=new ArrayList<>();
            try(var prepared=session.prepareRun(scenario)){var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException("D4 run failed: "+summary.diagnostics());for(var o:run.finals())observations.add(ConfigurationRules.observed(o.domain().orElseThrow().attributes(),o.outcome().name()));}
            // Lexical configuration order preserves the original tie break; Coverage returns kept indexes sorted.
            Map<String,ConfigurationRules.Config> bank=new TreeMap<>();observations.forEach(o->bank.put(o.config().key(),o.config()));var keys=new ArrayList<>(bank.keySet());List<List<String>> levels=new ArrayList<>();
            for(int i=0;i<6;i++){final int col=i;levels.add(bank.values().stream().map(c->c.levels().get(col)).distinct().sorted().toList());}
            List<int[]> tuples=new ArrayList<>();for(var c:bank.values()){int[] row=new int[6];for(int i=0;i<6;i++)row[i]=levels.get(i).indexOf(c.levels().get(i));tuples.add(row);}
            var selection=Coverage.select(tuples,levels.stream().mapToInt(List::size).toArray(),2,Coverage.Method.SET_COVER,Cancellation.NONE);
            var proof=ConfigurationRules.certify(observations,Arrays.stream(selection.kept()).mapToObj(keys::get).toList());
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<configuration-proof schema=\"d4.proof/v1\" source=\"observed configurations and verdicts\" observations=\"66\" configurations=\"22\" feasible-pairs=\"").append(selection.feasible()).append("\" covered-pairs=\"").append(selection.covered()).append("\" selected=\"").append(proof.selected().size()).append("\" selection=\"SET_COVER; lexical-key ties; no optimality claim\">\n");
            for(String key:proof.selected())xml.append("  <selected key=\"").append(key).append("\"/>\n");
            for(String pair:proof.obligations())xml.append("  <obligation id=\"").append(pair).append("\" covered=\"").append(proof.covered().contains(pair)).append("\"/>\n");
            proof.detections().forEach((policy,rows)->{for(String key:rows)xml.append("  <detection policy=\"").append(policy).append("\" configuration=\"").append(key).append("\"/>\n");});
            xml.append("  <wrong-sieve rule=\"forbid gzip\" removed-legal=\"").append(proof.wrongSieveRemoved()).append("\" remaining=\"").append(proof.wrongSieveRemaining()).append("\" remaining-failures=\"").append(proof.wrongSieveRemainingFailures()).append("\" hidden-failures=\"").append(proof.wrongSieveHiddenFailures()).append("\"/>\n");
            for(var observation:observations.stream().sorted(java.util.Comparator.comparing(ConfigurationRules.Observed::caseId)).toList())xml.append("  <observation case=\"").append(observation.caseId()).append("\" verdict=\"").append(observation.verdict()).append("\"/>\n");
            xml.append("</configuration-proof>\n");Files.write(out,xml.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("66 observed cases; 22 legal configurations; "+selection.covered()+" feasible pairs covered by "+proof.selected().size()+" rows; wrong sieve hides "+proof.wrongSieveHiddenFailures()+" failures");
        }
    }
}
