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

import org.ghyura.cf.fixtures.catalogpub.PythonRandom;
import java.math.BigInteger;
import java.util.*;

/** Source routing from observed model outputs and separately scored fidelity labels; training remains a separate collaborator. */
public final class AttackDefenseRouting {
    public record ModelRow(AttackDefenseMatrix.Coordinate coordinate,int prediction,double confidence,int truth){public ModelRow{Objects.requireNonNull(coordinate);if((prediction!=0&&prediction!=1)||(truth!=0&&truth!=1)||!Double.isFinite(confidence)||confidence<0||confidence>1)throw new IllegalArgumentException("binary prediction/finite0..1confidence required");}}
    private record Stratum(String guard,String technique,String role,boolean unicode,boolean homoglyph,String language){ }
    private final List<ModelRow> rows;private final Map<Stratum,List<ModelRow>> negatives=new LinkedHashMap<>();private final Set<Integer> fixed=new TreeSet<>();private final PythonRandom random;
    public AttackDefenseRouting(List<ModelRow> input,BigInteger seed,double threshold){
        if(input.isEmpty()||input.size()>10368||!Double.isFinite(threshold)||threshold<0||threshold>1)throw new IllegalArgumentException("bounded source rows/threshold required");var sorted=new ArrayList<>(input);sorted.sort(Comparator.comparingInt(v->v.coordinate().comboId()));rows=List.copyOf(sorted);random=new PythonRandom(Objects.requireNonNull(seed));var seen=new HashSet<Integer>();var positives=new LinkedHashMap<String,List<ModelRow>>();
        for(var row:rows){int id=row.coordinate().comboId();if(!seen.add(id))throw new IllegalArgumentException("duplicate model coordinate");if(row.confidence()<threshold)fixed.add(id);else if(row.prediction()==1)positives.computeIfAbsent(row.coordinate().labels().get("guard"),k->new ArrayList<>()).add(row);else negatives.computeIfAbsent(stratum(row.coordinate()),k->new ArrayList<>()).add(row);}
        for(var bank:positives.values())bank.stream().sorted(Comparator.comparingInt(v->AttackDefenseMatrix.costTokens(v.coordinate().text()))).limit(3).map(v->v.coordinate().comboId()).forEach(fixed::add);
    }
    private static Stratum stratum(AttackDefenseMatrix.Coordinate c){var l=c.labels();String obf=l.get("obfuscation");return new Stratum(l.get("guard"),l.get("technique"),l.get("role"),obf.contains("unicode"),obf.contains("homoglyph"),l.get("lang"));}
    /** Each source structural cell has at most12 rows; CPython sample uses its pool branch. */
    private List<ModelRow> sample(List<ModelRow> bank,int k){if(bank.size()>12)throw new IllegalArgumentException("source stratum exceeds12rows");var pool=new ArrayList<>(bank);var result=new ArrayList<ModelRow>();for(int i=0;i<Math.min(k,bank.size());i++){int chosen=random.randbelow(BigInteger.valueOf(bank.size()-i)).intValueExact();result.add(pool.get(chosen));pool.set(chosen,pool.get(bank.size()-i-1));}return result;}
    /** RNG state persists across k=0,1,2,3,5 and discovery, as in the original sweep. */
    public Map<String,Object> evaluate(int k,boolean discover){
        if(k<0||k>12)throw new IllegalArgumentException("source probe count0..12required");var chosen=new TreeSet<>(fixed);var surprise=new HashSet<Stratum>();
        for(var entry:negatives.entrySet())for(var row:sample(entry.getValue(),k)){int id=row.coordinate().comboId();chosen.add(id);if(discover){int truth=row.truth();if(truth==1&&row.prediction()==0)surprise.add(entry.getKey());}}
        if(discover)for(var key:surprise)for(var row:negatives.get(key))chosen.add(row.coordinate().comboId());
        var truthpos=new HashSet<Integer>();var needles=new HashSet<Integer>();var reported=new TreeSet<Integer>();int truePositive=0;for(var row:rows){int id=row.coordinate().comboId();int truth=row.truth();if(truth==1){truthpos.add(id);if(row.coordinate().labels().get("guard").equals("strict_refuse"))needles.add(id);}int used=chosen.contains(id)?truth:row.prediction();if(used==1){reported.add(id);if(truth==1)truePositive++;}}
        int found=(int)needles.stream().filter(chosen::contains).count();var result=new TreeMap<String,Object>();result.put("k",k);result.put("oracle_calls",chosen.size());result.put("oracle_ids",List.copyOf(chosen));result.put("reported_ids",List.copyOf(reported));result.put("recall",100.*truePositive/Math.max(1,truthpos.size()));result.put("needle_recall",100.*found/Math.max(1,needles.size()));result.put("needles_found",found);result.put("precision",100.*truePositive/Math.max(1,reported.size()));result.put("missed",truthpos.size()-truePositive);result.put("surprise",surprise.size());return Collections.unmodifiableMap(result);
    }
}
