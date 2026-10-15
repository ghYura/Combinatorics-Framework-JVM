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

import org.ghyura.cf.model.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D6 fresh four-step counter and queue machines; all scheduling and effects are local and deterministic. */
public final class ConcurrencySchedules {
    private ConcurrencySchedules() { }
    public static List<String> policies(String campaign) { return switch (campaign) { case "counter" -> List.of("atomic_commit","split_rw"); case "queue" -> List.of("actual_queue","stale_empty"); default -> throw new IllegalArgumentException("unknown D6 campaign"); }; }
    public record Config(String campaign,String policy,String schedule,Byte cap) {
        public Config {
            if (policy==null||!policies(campaign).contains(policy)) throw new IllegalArgumentException("policy does not belong to campaign");
            String threads=campaign.equals("counter")?"AB":"PC";
            if (schedule==null||schedule.length()!=4||schedule.chars().anyMatch(t -> threads.indexOf(t)<0)||schedule.chars().filter(t -> t==threads.charAt(0)).count()!=2) throw new IllegalArgumentException("four steps, two per thread required");
            if (campaign.equals("counter")) { if (cap==null||cap<0||cap>2||ConcurrencySchedulesOracle.preemptions(schedule)>cap) throw new IllegalArgumentException("missing/invalid/exceeded preemption cap"); }
            else if (cap!=null) throw new IllegalArgumentException("queue cannot have a cap");
        }
        public String caseId() { return campaign+"|"+policy+(cap==null?"":"|CAP="+cap)+"|SEQ="+schedule; }
    }
    public sealed interface Step permits CounterStep,QueueStep { Map<String,Object> document(); }
    public record CounterStep(String thread,String op,byte counter,Map<String,Byte> locals) implements Step {
        public CounterStep { locals=Map.copyOf(locals); }
        @Override public Map<String,Object> document() { return fields("thread",thread,"op",op,"counter",counter,"locals",new TreeMap<>(locals)); }
    }
    public record QueueStep(String thread,String op,List<String> items,boolean publishedNonempty,String popResult) implements Step {
        public QueueStep { items=List.copyOf(items); }
        @Override public Map<String,Object> document() { return fields("thread",thread,"op",op,"items",items,"published_nonempty",publishedNonempty,"pop_result",popResult); }
    }
    public record Result(Config config,List<Step> observed,List<Byte> referenceCounters,List<ConcurrencySchedulesOracle.QueueCheckpoint> referenceQueue,List<Byte> failingCheckpoints) {
        public Result { observed=List.copyOf(observed); referenceCounters=List.copyOf(referenceCounters); referenceQueue=List.copyOf(referenceQueue); failingCheckpoints=List.copyOf(failingCheckpoints); }
        public boolean passed() { return failingCheckpoints.isEmpty(); }
        public Map<String,Object> document() {
            var c=config; var m=new TreeMap<>(fields("schema","d6.observation/v1","contract","v1","campaign",c.campaign(),"case_id",c.caseId(),"policy",c.policy(),"schedule",c.schedule(),"local_steps",ConcurrencySchedulesOracle.localSteps(c.schedule()),"context_switches",ConcurrencySchedulesOracle.contextSwitches(c.schedule()),"preemptions",ConcurrencySchedulesOracle.preemptions(c.schedule()),"observed_trace",observed.stream().map(Step::document).toList(),"failing_checkpoints",failingCheckpoints,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier","IMPL position 2 (legacy positional, not causal)"));
            if (c.campaign().equals("counter")) { m.put("cap",c.cap()); m.put("reference_counters",referenceCounters); }
            else { m.put("reference_trace",referenceQueue.stream().map(ConcurrencySchedulesOracle.QueueCheckpoint::document).toList()); m.put("enabled_on_reference",List.of(true,true,true,true)); }
            return Collections.unmodifiableMap(m);
        }
    }
    /** Only the policy and actual machine state decide writes/pops. No expected value or oracle participates. */
    public static List<Step> run(Config c) {
        var done=new TreeMap<String,Byte>(); var locals=new TreeMap<String,Byte>(); byte value=0;
        var items=new ArrayList<String>(); boolean published=false; String result=null; var out=new ArrayList<Step>();
        for (char t : c.schedule().toCharArray()) {
            String thread=Character.toString(t); byte n=(byte)(done.getOrDefault(thread,(byte)0)+1); done.put(thread,n); String op;
            if (c.campaign().equals("counter")) {
                if (n==1) { locals.put(thread,value); op="read"; }
                else { value=(byte)((c.policy().equals("atomic_commit")?value:locals.get(thread))+1); op="write"; }
                out.add(new CounterStep(thread,op,value,locals));
            } else {
                if (t=='P'&&n==1) { items.add("item"); op="enqueue"; }
                else if (t=='P') { published=!items.isEmpty(); op="publish"; }
                else if (n==1) op="wait_readable";
                else { op="try_pop"; boolean stale=c.policy().equals("stale_empty")&&!published; result=items.isEmpty()||stale?"EMPTY":items.removeFirst(); }
                out.add(new QueueStep(thread,op,items,published,result));
            }
        }
        return List.copyOf(out);
    }
    public static Result judge(Config c,List<Step> observed) {
        if (observed.size()!=4) throw new IllegalArgumentException("all four checkpoints required"); var bad=new ArrayList<Byte>();
        if (c.campaign().equals("counter")) {
            var ref=ConcurrencySchedulesOracle.counterExpected(c.schedule()); for (byte i=0;i<4;i++) { if (!(observed.get(i) instanceof CounterStep step)) throw new IllegalArgumentException("wrong checkpoint kind"); if (step.counter()!=ref.get(i)) bad.add((byte)(i+1)); }
            return new Result(c,observed,ref,List.of(),bad);
        }
        var ref=ConcurrencySchedulesOracle.queueReference(c.schedule()); if (ref.firstDisabledStep()!=null) throw new IllegalArgumentException("queue reference disabled at step "+ref.firstDisabledStep());
        for (byte i=0;i<4;i++) { if (!(observed.get(i) instanceof QueueStep step)) throw new IllegalArgumentException("wrong checkpoint kind"); var expected=ref.trace().get(i); if (!step.items().equals(expected.items())||!java.util.Objects.equals(step.popResult(),expected.popResult())) bad.add((byte)(i+1)); }
        return new Result(c,observed,List.of(),ref.trace(),bad);
    }
    public static Result evaluate(Config c) {
        if (c.campaign().equals("queue")) { var ref=ConcurrencySchedulesOracle.queueReference(c.schedule()); if (ref.firstDisabledStep()!=null) throw new IllegalArgumentException("queue reference disabled at step "+ref.firstDisabledStep()); }
        return judge(c,run(c));
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static Map<String,String> attributes(Result r) { var m=new TreeMap<String,String>(); for (var e : r.document().entrySet()) m.put(e.getKey(),e.getValue() instanceof String s?s:json(e.getValue())); return Map.copyOf(m); }
}
