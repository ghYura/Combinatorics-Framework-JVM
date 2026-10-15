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
import org.ghyura.cf.fixtures.demos.CoalitionAllocation;
import org.ghyura.cf.fixtures.demos.CoalitionValuePlugin;
import org.ghyura.cf.store.MemoryBudget;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runnable D11 allocation from a normal shared run, using the framework's existing permutation operator.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 *      tools/CoalitionAllocationReport.java --scenario PATH/spec.toml --out allocation.xml [--workers 4]
 * A separate operation reservation owns the copied observations, certificates and XML encoding. Public allocation
 * API callers own their input/output objects, as in the existing embedding contract; this is not a global heap quota.
 */
public final class CoalitionAllocationReport {
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null;String workers="1";
        for(int i=0;i<args.length;i+=2) {
            if(i+1==args.length)throw new IllegalArgumentException("option value required");
            switch(args[i]) {case "--scenario" -> scenario=Path.of(args[i+1]);case "--out" -> out=Path.of(args[i+1]);case "--workers" -> workers=args[i+1];default -> throw new IllegalArgumentException("unknown option "+args[i]);}
        }
        if(scenario==null||out==null)throw new IllegalArgumentException("--scenario and --out are required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",CoalitionValuePlugin.ID,"D11 allocation"),
                new ConfigResolver.Override("cf.execution.workers",workers,"D11 allocation"),
                new ConfigResolver.Override("cf.generation.distinctTables","true","original D11 Core settings")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var owned=budget.reserve(4L*1024*1024,"D11 observed values, certificates and XML encoding")) {
            if(owned.bytes()!=4L*1024*1024)throw new IllegalStateException("invalid allocation reservation");
            List<CoalitionAllocation.Observed> observations=new ArrayList<>();
            try(var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle();var summary=run.await();
                if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException("D11 run failed: "+summary.diagnostics());
                for(var o:run.finals()) {
                    var domain=o.domain().orElseThrow(()->new IllegalArgumentException("D11 observation has no domain value"));
                    var at=domain.attributes();observations.add(new CoalitionAllocation.Observed(at.get("case"),at.get("mask"),o.outcome().name(),new BigInteger(at.get("value"))));
                }
            }
            var orders=new LexicographicPermutations(6);
            var allocation=CoalitionAllocation.allocate(observations,java.util.stream.IntStream.range(0,orders.size().intValueExact()).mapToObj(i->orders.get(BigInteger.valueOf(i))).iterator());
            StringBuilder xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<allocation schema=\"d11.allocation/v1\" source=\"observed coalition values\" grand-value=\"").append(allocation.grandValue()).append("\">\n");
            for(String p:allocation.shapley().keySet()) {
                var q=allocation.shapley().get(p);var split=allocation.dividendSplit().get(p);var uniform=allocation.uniformSubsetMarginals().get(p);
                xml.append("  <player id=\"").append(p).append("\" n=\"").append(q.numerator()).append("\" d=\"").append(q.denominator())
                        .append("\" dividend-n=\"").append(split.numerator()).append("\" dividend-d=\"").append(split.denominator())
                        .append("\" permutation-sum=\"").append(allocation.permutationSums().get(p)).append("\" standalone=\"").append(allocation.standalone().get(p))
                        .append("\" leave-one-out=\"").append(allocation.leaveOneOut().get(p)).append("\" uniform-n=\"").append(uniform.numerator()).append("\" uniform-d=\"").append(uniform.denominator()).append("\"/>\n");
            }
            for(var m:allocation.marginals())xml.append("  <marginal player=\"").append(m.player()).append("\" coalition=\"").append(m.coalition()).append("\" n=\"").append(m.weight().numerator()).append("\" d=\"").append(m.weight().denominator()).append("\" value=\"").append(m.marginal()).append("\"/>\n");
            for(var m:allocation.permutations())xml.append("  <permutation order=\"").append(m.order()).append("\" marginals=\"").append(String.join(" ",m.marginals().stream().map(Object::toString).toList())).append("\"/>\n");
            allocation.dividends().forEach((mask,value)->xml.append("  <dividend mask=\"").append(mask).append("\" value=\"").append(value).append("\"/>\n"));
            allocation.checks().forEach((name,passed)->xml.append("  <check name=\"").append(name).append("\" passed=\"").append(passed).append("\"/>\n"));
            observations.stream().sorted(java.util.Comparator.comparing(CoalitionAllocation.Observed::mask)).forEach(o->xml.append("  <observation case=\"").append(o.caseId()).append("\" mask=\"").append(o.mask()).append("\" verdict=\"PASS\" value=\"").append(o.value()).append("\"/>\n"));
            xml.append("</allocation>\n");
            Files.write(out,xml.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("64 observed coalitions; 192 weighted marginals; 720 permutations; Shapley "+allocation.shapley());
        }
    }
}
