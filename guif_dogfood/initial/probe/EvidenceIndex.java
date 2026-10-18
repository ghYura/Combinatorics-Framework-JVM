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

/** Extract only sample identities; never dump a checkpoint or its captured output. */
public final class EvidenceIndex {
    public static void main(String[] args) throws Exception {
        List<Object> rows=new ArrayList<>();
        for(int i=1;i<args.length;i++) {
            Path path=Path.of(args[i]);
            var checkpoint=CheckpointCodec.read(path);
            for(var observation:checkpoint.observations()) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("checkpoint",path.toString());
                row.put("run_id",checkpoint.runId());
                row.put("candidate",observation.key().candidateId().toString());
                row.put("outcome",observation.outcome().name());
                row.put("verdict",observation.verdictCode().orElse(null));
                row.put("failure",observation.failure().map(Object::toString).orElse(""));
                String message=observation.failureMessage();
                row.put("failure_message",message.substring(0,Math.min(1200,message.length())));
                Map<String,String> attributes=new LinkedHashMap<>();
                Map<String,Map<String,Long>> objects=new LinkedHashMap<>();
                observation.domain().ifPresent(domain->{
                    domain.attributes().forEach((key,value)->{
                        if(key.equals("family")||key.equals("evidence_id")||key.startsWith("factor_"))attributes.put(key,value);
                    });
                    // Numeric-looking factor levels are metrics; recover their exact spelling.
                    for(String token:domain.attributes().getOrDefault("metrics.line","").split("\\s+")) {
                        int equals=token.indexOf('=');
                        if(equals>0&&token.startsWith("factor_"))attributes.put(token.substring(0,equals),token.substring(equals+1));
                    }
                    domain.metrics().forEach((key,value)->{
                        if(key.startsWith("w_"))for(String suffix:List.of("_events","_exercised","_viol")) {
                            if(key.endsWith(suffix)) {
                                String id=key.substring(2,key.length()-suffix.length());
                                String field=suffix.equals("_viol")?"violations":suffix.substring(1);
                                objects.computeIfAbsent(id,ignored->new LinkedHashMap<>()).put(field,value.longValue());
                            }
                        }
                    });
                });
                row.put("attributes",attributes);row.put("objects",objects);
                rows.add(row);
            }
        }
        Files.writeString(Path.of(args[0]),GuifProbe.json(rows));
        System.out.println("Indexed "+rows.size()+" observations from "+(args.length-1)+" checkpoints");
    }
}
