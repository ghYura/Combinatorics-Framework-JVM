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

package org.ghyura.cf.desktop;
import org.ghyura.cf.artifacts.CheckpointCodec;
import java.nio.file.*;
import java.util.*;
public final class EvidenceIndexV2 {
    public static void main(String[] args)throws Exception {
        List<Object> rows=new ArrayList<>();
        for(int i=1;i<args.length;i++){
            var cp=CheckpointCodec.read(Path.of(args[i]));
            for(var o:cp.observations()){
                Map<String,Object> r=new LinkedHashMap<>();
                r.put("checkpoint",args[i]);r.put("candidate",o.key().candidateId().toString());r.put("run_id",cp.runId());
                r.put("outcome",o.outcome().name());r.put("verdict",o.verdictCode().orElse(null));r.put("failure",o.failureMessage());
                o.domain().ifPresent(d->r.put("evidence_id",d.attributes().getOrDefault("evidence_id","")));
                rows.add(r);
            }
        }
        Files.writeString(Path.of(args[0]),ButtonProbe.json(rows));
        System.out.println("Indexed "+rows.size()+" selected observations");
    }
}
