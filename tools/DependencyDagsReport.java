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

import org.ghyura.cf.algebra.LexicographicPermutations;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.fixtures.demos.DependencyDags;
import org.ghyura.cf.fixtures.demos.DependencyDagsPlugin;
import org.ghyura.cf.store.MemoryBudget;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** D8b full observations and offline topological-order proof through shared runs and existing algebra.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/DependencyDagsReport.java --scenario PATH/spec.toml --out proof.xml [--workers 4]
 */
public final class DependencyDagsReport {
    private static String escape(String s){return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
    public static void main(String[] args)throws Exception{
        Path scenario=null,out=null;String workers="1";
        for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->out=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option "+args[i]);}}
        if(scenario==null||out==null)throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",DependencyDagsPlugin.ID,"D8b report"),new ConfigResolver.Override("cf.execution.workers",workers,"D8b report")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var owned=budget.reserve(16L*1024*1024,"D8b observed trace bank, topology and XML certificate")){
            if(owned.bytes()!=16L*1024*1024)throw new IllegalStateException("invalid D8b report reservation");
            Map<String,Map<String,String>> observations=new TreeMap<>();TreeSet<String> graphs=new TreeSet<>();
            try(var prepared=session.prepareRun(scenario)){var run=prepared.handle();var s=run.await();if(s.state()!=RunState.COMPLETED)throw new IllegalStateException("D8b run failed: "+s.diagnostics());for(var o:run.finals()){
                var a=o.domain().orElseThrow().attributes();String id=a.get("case");String[] parts=id.split("\\|");if(parts.length!=3||!DependencyDags.POLICIES.contains(parts[0])||!parts[1].matches("G=[01]{6}")||!parts[2].matches("U=[ABCD]")||!parts[0].equals(a.get("policy"))||!parts[2].substring(2).equals(a.get("edit")))throw new IllegalArgumentException("invalid observed D8b identity");
                if(!List.of("PASS","DOMAIN_FAIL").contains(o.outcome().name())||!o.outcome().name().equals(a.get("verdict")))throw new IllegalArgumentException("invalid observed D8b verdict");
                if(observations.putIfAbsent(id,a)!=null)throw new IllegalArgumentException("duplicate observed D8b case");graphs.add(parts[1].substring(2));
            }}
            if(observations.size()!=768)throw new IllegalArgumentException("all 768 observed D8b cases required");
            var permutations=new LexicographicPermutations(4);var proof=DependencyDags.topology(graphs,java.util.stream.IntStream.range(0,24).mapToObj(i->permutations.get(BigInteger.valueOf(i))).iterator());
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dependency-dag-report schema=\"d8b.report/v1\" source=\"observed traces and graph identities; existing algebra permutations\" observations=\"768\" graphs=\"").append(proof.graphs().size()).append("\" graph-order-pairs=\"").append(proof.graphOrderPairs()).append("\" eligible-additions=\"").append(proof.additions().size()).append("\" scope=\"order validity only; redundant edges change additive values\">\n");
            for(var g:proof.graphs())xml.append("  <graph bits=\"").append(g.bits()).append("\" orders=\"").append(String.join(" ",g.orders())).append("\"/>\n");
            for(var a:proof.additions())xml.append("  <addition before=\"").append(a.before()).append("\" after=\"").append(a.after()).append("\" edge=\"").append(List.of(a.edge().from(),a.edge().to())).append("\" orders-before=\"").append(String.join(" ",a.beforeOrders())).append("\" orders-after=\"").append(String.join(" ",a.afterOrders())).append("\" equal-sets=\"").append(a.equalSets()).append("\" minimum-before=\"").append(a.beforeOrders().getFirst()).append("\" minimum-after=\"").append(a.afterOrders().getFirst()).append("\"/>\n");
            observations.forEach((id,a)->{xml.append("  <observation case=\"").append(escape(id)).append("\" verdict=\"").append(a.get("verdict")).append("\">\n");new TreeMap<>(a).forEach((name,value)->xml.append("    <field name=\"").append(escape(name)).append("\">").append(escape(value)).append("</field>\n"));xml.append("  </observation>\n");});
            xml.append("</dependency-dag-report>\n");Files.write(out,xml.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("768 observed cache traces; "+proof.graphs().size()+" graphs; "+proof.graphOrderPairs()+" graph/order pairs; "+proof.additions().size()+" redundant-edge certificates");
        }
    }
}
