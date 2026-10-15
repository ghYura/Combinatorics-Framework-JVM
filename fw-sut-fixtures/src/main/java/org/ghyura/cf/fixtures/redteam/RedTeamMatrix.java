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
import org.ghyura.cf.model.Canonical;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Collections;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Source labels and index attribution; generation itself stays in the native framework. */
public final class RedTeamMatrix {
    public record Atom(String label,String fragment){ }
    public record Slot(String sheet,String verb,List<Atom> atoms){public Slot{atoms=List.copyOf(atoms);}}
    public record Coordinate(int comboId,String candidateId,Map<String,String> labels,String prompt){
        public Coordinate{labels=Collections.unmodifiableMap(new TreeMap<>(labels));}
    }
    private RedTeamMatrix(){ }
    private static final List<Slot> SLOTS=read();
    public static List<Slot> slots(){return SLOTS;}
    private static List<Slot> read() {
        try(var in=RedTeamMatrix.class.getResourceAsStream("matrix.json")){
            if(in==null)throw new IllegalStateException("missing local redteam matrix");byte[] bytes=in.readNBytes(65537);
            if(bytes.length>65536)throw new IllegalArgumentException("local matrix exceeds64KiB");
            var root=(Map<?,?>)Json.parse(new String(bytes,StandardCharsets.UTF_8),"local redteam matrix");
            var slots=new ArrayList<Slot>();for(Object row:(List<?>)root.get("matrix")){
                var columns=(List<?>)row;var atoms=new ArrayList<Atom>();for(Object item:(List<?>)columns.get(2)){var pair=(List<?>)item;atoms.add(new Atom((String)pair.get(0),(String)pair.get(1)));}
                slots.add(new Slot((String)columns.get(0),(String)columns.get(1),atoms));
            }
            return List.copyOf(slots);
        }catch(IOException error){throw new IllegalStateException("local redteam matrix unreadable",error);}
    }
    private static int atom(Slot slot,String fragment){
        for(int i=0;i<slot.atoms().size();i++)if(slot.atoms().get(i).fragment().equals(fragment))return i;
        throw new IllegalArgumentException("unknown original "+slot.sheet()+" fragment");
    }
    /** Validate selected values and recover the original Cartesian combo ID without enumerating attacks a second time. */
    public static Coordinate coordinate(List<List<String>> values) {
        if(values.size()!=SLOTS.size())throw new IllegalArgumentException("original six columns required");
        int index=0;var labels=new TreeMap<String,String>();var prompt=new StringBuilder();
        for(int c=0;c<SLOTS.size();c++) {
            var slot=SLOTS.get(c);var selected=List.copyOf(values.get(c));var positions=selected.stream().map(v->atom(slot,v)).toList();int rank,radix;String label;
            if(slot.verb().equals("FW_Combi(1)")){
                if(positions.size()!=1)throw new IllegalArgumentException("one present original "+slot.sheet()+" atom required");
                rank=positions.getFirst();radix=slot.atoms().size();label=slot.atoms().get(rank).label();
            }else if(slot.verb().equals("FW_Subsets")){
                if(positions.equals(List.of()))rank=0;else if(positions.equals(List.of(0)))rank=1;else if(positions.equals(List.of(1)))rank=2;else if(positions.equals(List.of(0,1)))rank=3;else throw new IllegalArgumentException("original two-atom subset required");
                radix=4;label=positions.isEmpty()?"none":String.join("+",positions.stream().map(i->slot.atoms().get(i).label()).toList());
            }else {
                if(!slot.verb().equals("FW_Permut")||positions.size()!=3||new java.util.HashSet<>(positions).size()!=3)throw new IllegalArgumentException("original three-atom permutation required");
                // Lehmer rank in the original itertools order; there is no second campaign/generator here.
                rank=0;for(int i=0;i<3;i++){int smaller=0;for(int k=i+1;k<3;k++)if(positions.get(k)<positions.get(i))smaller++;rank+=smaller*(i==0?2:1);}
                radix=6;label=String.join("+",positions.stream().map(i->slot.atoms().get(i).label()).toList());
            }
            index=Math.addExact(Math.multiplyExact(index,radix),rank);labels.put(slot.sheet().toLowerCase(java.util.Locale.ROOT),label);selected.forEach(prompt::append);
        }
        return new Coordinate(index,"redteam:"+String.format(java.util.Locale.ROOT,"%012d",index),labels,prompt.toString());
    }
    public static String json(Object value){return Json.write(value);}
    public static String promptDigest(String prompt){return Canonical.sha256Hex(prompt);}
}
