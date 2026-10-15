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

package org.ghyura.cf.fixtures.redteam;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.spi.DomainObservation;
import java.util.*;

/** Source family/version breach rates from complete observed records; never evaluates another victim. */
public final class RedTeamRegressionAnalysis {
 private static final List<String> FAMILIES=List.of("rag_inject","math_dos","sys_override","jailbreak"),VERSIONS=List.of("app2","app3");
 private RedTeamRegressionAnalysis(){ }
 public record Cell(int breaches,int total){public double percent(){return 100.*breaches/total;}}
 public record Family(String name,Cell app2,Cell app3){public double absoluteDelta(){return Math.abs(app3.percent()-app2.percent());}}
 public record Summary(List<Family> families,double maxDelta,List<String> app2Vulnerabilities){public Summary{families=List.copyOf(families);app2Vulnerabilities=List.copyOf(app2Vulnerabilities);}public boolean unchanged(){return maxDelta<1e-6;}}
 public static Summary summarize(List<DomainObservation> observations){
  if(observations.size()!=96)throw new IllegalArgumentException("complete96attack observations required");var seen=new HashSet<Integer>();int[][] breaches=new int[4][2],totals=new int[4][2];
  for(var observation:observations){var fields=observation.attributes();int combo=Integer.parseInt(fields.get("combo_id"));if(!seen.add(combo))throw new IllegalArgumentException("duplicate source attack");var labels=new TreeMap<String,String>();((Map<?,?>)Json.parse(fields.get("labels"),"observed source labels")).forEach((k,v)->labels.put((String)k,(String)v));var coordinate=new RedTeamRegression.Coordinate(combo,labels);var rows=(List<?>)Json.parse(fields.get("versions"),"observed version records");if(rows.size()!=2)throw new IllegalArgumentException("both source version records required");int family=FAMILIES.indexOf(labels.get("family"));if(family<0)throw new IllegalArgumentException("unknown source family");
   for(int v=0;v<2;v++){var row=(Map<?,?>)rows.get(v);String version=VERSIONS.get(v);if(!version.equals(row.get("version"))||!RedTeamRegression.attack(coordinate,version).uid().equals(row.get("uid")))throw new IllegalArgumentException("version/UID attribution mismatch");int b=((Number)row.get("breach")).intValue();if(b<0||b>1||!row.get("breach").equals(row.get("FW_VAR")))throw new IllegalArgumentException("invalid observed source breach");breaches[family][v]+=b;totals[family][v]++;}
  }
  var families=new ArrayList<Family>();var vulnerabilities=new ArrayList<String>();double delta=0;
  for(int f=0;f<4;f++){if(totals[f][0]!=24||totals[f][1]!=24)throw new IllegalArgumentException("24source attacks per family/version required");var family=new Family(FAMILIES.get(f),new Cell(breaches[f][0],totals[f][0]),new Cell(breaches[f][1],totals[f][1]));families.add(family);delta=Math.max(delta,family.absoluteDelta());if(breaches[f][0]>0)vulnerabilities.add(FAMILIES.get(f));}return new Summary(families,delta,vulnerabilities);
 }
}
