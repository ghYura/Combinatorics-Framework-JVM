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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** D6 policy-blind prefix completion and enabled actual-queue reference. No SUT state is read. */
public final class ConcurrencySchedulesOracle {
    private ConcurrencySchedulesOracle() { }
    public static List<String> localSteps(String schedule) {
        var seen=new java.util.HashMap<Character,Byte>(); var out=new ArrayList<String>();
        for (char t : schedule.toCharArray()) { byte n=(byte)(seen.getOrDefault(t,(byte)0)+1); seen.put(t,n); out.add(""+t+n); } return List.copyOf(out);
    }
    public static byte contextSwitches(String schedule) { byte n=0; for (int i=0;i<schedule.length()-1;i++) if (schedule.charAt(i)!=schedule.charAt(i+1)) n++; return n; }
    public static byte preemptions(String schedule) {
        var seen=new java.util.HashMap<Character,Byte>(); byte n=0;
        for (int i=0;i<schedule.length();i++) { char t=schedule.charAt(i); byte count=(byte)(seen.getOrDefault(t,(byte)0)+1); seen.put(t,count); if (i+1<schedule.length()&&t!=schedule.charAt(i+1)&&count<2) n++; } return n;
    }
    public static List<Byte> counterExpected(String schedule) {
        var out=new ArrayList<Byte>(); byte writes=0; for (var step : localSteps(schedule)) { if (step.charAt(1)=='2') writes++; out.add(writes); } return List.copyOf(out);
    }
    public record QueueCheckpoint(List<String> items,String popResult) {
        public QueueCheckpoint { items=List.copyOf(items); }
        public Map<String,Object> document() { return ConcurrencySchedules.fields("items",items,"pop_result",popResult); }
    }
    public record QueueReference(List<QueueCheckpoint> trace,Byte firstDisabledStep) { public QueueReference { trace=List.copyOf(trace); } }
    public static QueueReference queueReference(String schedule) {
        var items=new ArrayList<String>(); String result=null; var trace=new ArrayList<QueueCheckpoint>(); byte index=0;
        for (String step : localSteps(schedule)) {
            index++;
            switch (step) {
                case "P1" -> items.add("item");
                case "P2" -> { }
                case "C1" -> { if (items.isEmpty()) return new QueueReference(trace,index); }
                case "C2" -> result=items.isEmpty()?"EMPTY":items.removeFirst();
                default -> throw new IllegalArgumentException("local step outside queue contract");
            }
            trace.add(new QueueCheckpoint(items,result));
        }
        return new QueueReference(trace,null);
    }
}
