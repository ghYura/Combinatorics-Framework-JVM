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

package org.ghyura.cf.fixtures.assistant;
import org.ghyura.cf.spi.*;
import org.ghyura.cf.model.Json;
import java.util.*;
/** Fitted source model inference from native numeric selections; class labels remain result data. */
public final class PortableForestPlugin implements SutPlugin {
 public static final String ID="local-surrogate-forest";
 @Override public String id(){return ID;}
 @Override public String version(){return "persisted-source@8d5aa2d/java-inference-1/"+PortableForest.BUNDLED_SHA256;}
 @Override public Map<Integer,String> verdictNames(){return Map.of(0,"COMPLETED");}
 @Override public SutSession open(ExecutionContext context){return new SutSession(){
  @Override public DomainObservation evaluate(SutCandidate candidate,ExecutionContext ctx){ctx.cancellation().throwIfCancelled();try(var lease=ctx.reserve(8L*1024*1024,"bounded neutral source forest parsing,numeric arrays and immutable inference")){
   if(lease.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid forest reservation");if(!candidate.selections().stream().map(v->v.sheet()).toList().equals(List.of("F0","F1","F2","F3")))throw new IllegalArgumentException("four source fitted numeric feature sheets required");var features=new ArrayList<Double>();for(var selection:candidate.selections()){if(!selection.inCandidate()||selection.values().size()!=1)throw new IllegalArgumentException("one mandatory numeric scalar per feature required");String text=selection.values().getFirst().strip();if(text.length()>64)throw new IllegalArgumentException("numeric scalar exceeds64units");features.add(Double.valueOf(text));}
   var model=PortableForest.bundled();var prediction=model.infer(features);ctx.cancellation().throwIfCancelled();var metrics=new TreeMap<String,Double>();metrics.put("prediction",(double)prediction.prediction());metrics.put("confidence",prediction.confidence());for(int c=0;c<prediction.probabilities().size();c++)metrics.put("probability"+c,prediction.probabilities().get(c));metrics.put("tree_count",(double)model.treeCount());metrics.put("node_count",(double)model.nodeCount());metrics.put("feature_count",(double)model.featureCount());return new DomainObservation(0,metrics,Map.of("features",Json.write(features),"prediction_json",Json.write(prediction.fields()),"model_asset_sha256",model.digest(),"source_model_sha256",model.sourceDigest(),"FW_VAR","0"));
  }}@Override public void close(){ }
 };}
}
