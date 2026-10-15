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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** D4's local request adapter, independent legality/envelope oracle and observed pairwise certificate. */
public final class ConfigurationRules {
    public static final List<String> POLICIES = List.of("correct", "drops_gzip", "ignores_debug_rule");
    private static final byte[] ORIGINAL = "{\"sensor\":7}\n".getBytes(StandardCharsets.US_ASCII);
    public record Config(String env, String mode, String transport, List<String> features, int workers, boolean debug) {
        public Config {
            if (env == null || mode == null || transport == null || !List.of("dev", "prod").contains(env) || !List.of("batch", "live").contains(mode)
                    || !List.of("http", "https").contains(transport) || workers < 1 || workers > 2)
                throw new IllegalArgumentException("D4 configuration outside the contract");
            if (features.size() != 2 || Set.copyOf(features).size() != 2 || !List.of("audit", "cache", "gzip").containsAll(features))
                throw new IllegalArgumentException("two distinct known D4 features required");
            features = features.stream().sorted().toList();
        }
        public List<String> levels() { return List.of(env, mode, transport, String.join("+", features), Integer.toString(workers), debug ? "1" : "0"); }
        public String key() {
            return "env=" + env + "|mode=" + mode + "|transport=" + transport + "|features=" + String.join("+", features)
                    + "|workers=" + workers + "|debug=" + (debug ? 1 : 0);
        }
    }
    public record Envelope(String url, int workers, Map<String,String> headers, String bodyBase64) {
        public Envelope { headers = Collections.unmodifiableMap(new TreeMap<>(headers)); }
    }
    public record Adapted(boolean accepted, List<String> violations, Envelope envelope) {
        public Adapted { violations = List.copyOf(violations); }
    }
    public record Judgment(List<String> expectedViolations, boolean acceptanceOk, Map<String,Boolean> transformation,
                           List<String> failureClasses, boolean passed) {
        public Judgment {
            expectedViolations = List.copyOf(expectedViolations); transformation = Collections.unmodifiableMap(new TreeMap<>(transformation));
            failureClasses = List.copyOf(failureClasses);
        }
    }
    public record Result(String phase, String policy, Config config, Adapted observed, Judgment judgment) {
        public String caseId() { return phase + "|" + policy + "|" + config.key(); }
    }
    /** An observation from the shared run, not a recomputed SUT result. */
    public record Observed(String caseId, String policy, Config config, String verdict) { }
    public record Proof(List<String> configurations, List<String> selected, Set<String> obligations, Set<String> covered,
                        Map<String,List<String>> detections, int wrongSieveRemoved, int wrongSieveRemaining,
                        int wrongSieveRemainingFailures, int wrongSieveHiddenFailures) {
        public Proof {
            configurations = List.copyOf(configurations); selected = List.copyOf(selected);
            obligations = Collections.unmodifiableSet(new TreeSet<>(obligations)); covered = Collections.unmodifiableSet(new TreeSet<>(covered));
            Map<String,List<String>> copied = new TreeMap<>(); detections.forEach((k,v)->copied.put(k,List.copyOf(v)));
            detections = Collections.unmodifiableMap(copied);
        }
    }
    private ConfigurationRules() { }
    public static byte[] originalPayload() { return ORIGINAL.clone(); }

    /** Named-rule SUT; deliberately ignores R6 for the faulty policy. */
    public static Adapted adapt(Config c, String policy) {
        if (!POLICIES.contains(policy)) throw new IllegalArgumentException("unknown D4 policy");
        List<String> bad = new ArrayList<>(); Set<String> f = Set.copyOf(c.features());
        boolean[] violated = {c.env().equals("prod") && c.transport().equals("http"),
                c.mode().equals("live") && f.contains("cache"), c.mode().equals("live") && c.workers() < 2,
                c.env().equals("prod") && !Set.of("audit","gzip").containsAll(f),
                c.mode().equals("batch") && f.contains("audit") && c.workers() < 2,
                c.env().equals("prod") && c.debug()};
        for (int i=0;i<violated.length;i++) if (violated[i] && !(i==5 && policy.equals("ignores_debug_rule"))) bad.add("R"+(i+1));
        if (!bad.isEmpty()) return new Adapted(false,bad,null);
        Map<String,String> h = new TreeMap<>();
        if (f.contains("audit")) h.put("X-Audit","1"); if (f.contains("cache")) h.put("Cache-Control","max-age=60");
        boolean zipped = f.contains("gzip") && !policy.equals("drops_gzip"); h.put("Content-Encoding",zipped ? "gzip" : "identity");
        if (c.debug()) h.put("X-Debug","1");
        byte[] body = ORIGINAL;
        if (zipped) {
            var bytes = new ByteArrayOutputStream();
            try (var zip = new GZIPOutputStream(bytes)) { zip.write(ORIGINAL); }
            catch (IOException e) { throw new IllegalStateException(e); }
            body = bytes.toByteArray();
        }
        return new Adapted(true,List.of(),new Envelope(c.transport()+"://telemetry.invalid/"+c.mode(),c.workers(),h,Base64.getEncoder().encodeToString(body)));
    }

    /** Independent legality checks; no policy or SUT result is consulted. */
    public static List<String> violations(Config c) {
        List<String> found = new ArrayList<>();
        if (c.env().equals("prod") && c.transport().equals("http")) found.add("R1");
        if (c.mode().equals("live") && c.features().contains("cache")) found.add("R2");
        if (c.mode().equals("live") && c.workers()<2) found.add("R3");
        if (c.env().equals("prod") && c.features().stream().anyMatch(f->!f.equals("audit")&&!f.equals("gzip"))) found.add("R4");
        if (c.mode().equals("batch") && c.features().contains("audit") && c.workers()<2) found.add("R5");
        if (c.env().equals("prod") && c.debug()) found.add("R6");
        return List.copyOf(found);
    }
    public static Judgment judge(Config c, Adapted result) {
        List<String> want = violations(c), classes = new ArrayList<>();
        if (!want.isEmpty() && result.accepted()) classes.add("wrong_acceptance");
        else if (want.isEmpty() && !result.accepted()) classes.add("wrong_rejection");
        else if (!want.isEmpty() && !result.violations().equals(want)) classes.add("wrong_violation_ids");
        Map<String,Boolean> transform = new TreeMap<>();
        if (result.accepted() && want.isEmpty()) {
            var e = result.envelope(); Map<String,String> h = new TreeMap<>();
            if (c.features().contains("audit")) h.put("X-Audit","1"); if (c.features().contains("cache")) h.put("Cache-Control","max-age=60");
            h.put("Content-Encoding",c.features().contains("gzip") ? "gzip" : "identity"); if (c.debug()) h.put("X-Debug","1");
            byte[] decoded = null, body = Base64.getDecoder().decode(e.bodyBase64());
            if ("identity".equals(e.headers().get("Content-Encoding"))) decoded = body;
            else if ("gzip".equals(e.headers().get("Content-Encoding"))) {
                try (var zip = new GZIPInputStream(new ByteArrayInputStream(body))) { decoded = zip.readNBytes(ORIGINAL.length+1); }
                catch (IOException ignored) { decoded = null; }
            }
            transform.put("url",e.url().equals(c.transport()+"://telemetry.invalid/"+c.mode())); transform.put("workers",e.workers()==c.workers());
            transform.put("headers",e.headers().equals(h)); transform.put("payload",java.util.Arrays.equals(decoded,ORIGINAL));
            // The original failure list uses URL, workers, headers, payload order.
            for (String k : List.of("url","workers","headers","payload")) if (!transform.get(k)) classes.add("wrong_"+k);
        }
        boolean acceptance = classes.stream().noneMatch(k->List.of("wrong_acceptance","wrong_rejection","wrong_violation_ids").contains(k));
        return new Judgment(want,acceptance,transform,classes,classes.isEmpty());
    }
    public static Result evaluate(String phase, String policy, Config config) {
        if (!List.of("main","controls").contains(phase)) throw new IllegalArgumentException("unknown D4 phase");
        var got=adapt(config,policy);return new Result(phase,policy,config,got,judge(config,got));
    }
    public static Set<String> obligations(Config c) {
        var names=List.of("env","mode","transport","features","workers","debug");var values=c.levels();Set<String> pairs=new TreeSet<>();
        for(int i=0;i<6;i++)for(int j=i+1;j<6;j++)pairs.add(names.get(i)+"="+values.get(i)+"|"+names.get(j)+"="+values.get(j));
        return Collections.unmodifiableSet(pairs);
    }
    public static Observed observed(Map<String,String> at, String verdict) {
        Config c=new Config(at.get("env"),at.get("mode"),at.get("transport"),List.of(at.get("features").split("\\+")),Integer.parseInt(at.get("workers")),switch(at.get("debug")){case "0"->false;case "1"->true;default->throw new IllegalArgumentException("invalid observed DEBUG");});
        return new Observed(at.get("case"),at.get("policy"),c,verdict);
    }
    /** Caller obtains the selected keys using the existing framework Coverage selector. */
    public static Proof certify(List<Observed> observations, List<String> selected) {
        Map<String,Observed> byCase=new TreeMap<>();Map<String,Config> bank=new TreeMap<>();
        for(var o:observations) {
            if (!POLICIES.contains(o.policy()) || !o.caseId().equals("main|"+o.policy()+"|"+o.config().key()) || !List.of("PASS","DOMAIN_FAIL").contains(o.verdict())) throw new IllegalArgumentException("invalid main observation");
            if (!violations(o.config()).isEmpty() || byCase.putIfAbsent(o.caseId(),o)!=null) throw new IllegalArgumentException("illegal or duplicate observation");
            bank.put(o.config().key(),o.config());
        }
        if(bank.size()!=22 || byCase.size()!=66)throw new IllegalArgumentException("all 66 main observations required");
        for(String k:bank.keySet())for(String policy:POLICIES)if(!byCase.containsKey("main|"+policy+"|"+k))throw new IllegalArgumentException("missing policy observation");
        if (Set.copyOf(selected).size()!=selected.size() || !bank.keySet().containsAll(selected))throw new IllegalArgumentException("unknown or duplicate selected configuration");
        Set<String> need=new TreeSet<>(),covered=new TreeSet<>();bank.values().forEach(c->need.addAll(obligations(c)));selected.forEach(k->covered.addAll(obligations(bank.get(k))));
        Map<String,List<String>> detections=new TreeMap<>();
        for(String policy:POLICIES)detections.put(policy,selected.stream().filter(k->byCase.get("main|"+policy+"|"+k).verdict().equals("DOMAIN_FAIL")).sorted().toList());
        int removed=0,remain=0,failed=0,hidden=0;
        for(var o:observations)if(o.config().features().contains("gzip")){removed++;if(o.verdict().equals("DOMAIN_FAIL"))hidden++;}else{remain++;if(o.verdict().equals("DOMAIN_FAIL"))failed++;}
        return new Proof(new ArrayList<>(bank.keySet()),selected,need,covered,detections,removed,remain,failed,hidden);
    }
}
