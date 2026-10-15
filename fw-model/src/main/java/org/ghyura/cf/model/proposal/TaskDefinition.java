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

package org.ghyura.cf.model.proposal;

import org.ghyura.cf.model.SourceLocation;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

/** Typed local task; native verbs, flags and ordered directives are lowered by the existing spec reader. */
public record TaskDefinition(String id, String version, String title, String context, long seed,
                             List<Slot> slots, List<String> arguments, List<String> goals,
                             List<CustomVerdict> customVerdicts, SourceLocation location) {
    public TaskDefinition {
        requireName(id,"task id"); requireName(version,"task version");
        Objects.requireNonNull(title); Objects.requireNonNull(context); Objects.requireNonNull(location);
        slots=List.copyOf(slots); arguments=List.copyOf(arguments); goals=List.copyOf(goals);
        customVerdicts=List.copyOf(customVerdicts);
        if(slots.isEmpty())throw new IllegalArgumentException("a task needs slots");
        var names=new HashSet<String>();
        for(var slot:slots)if(!names.add(slot.sheet().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("duplicate task sheet: "+slot.sheet());
    }
    public sealed interface Values permits Fixed, Proposed { }
    public record Fixed(List<String> values) implements Values {
        public Fixed { values=List.copyOf(values); }
    }
    /** Prefix/suffix render proposals without executing a provider response as an authoring script. */
    public record Proposed(String role,String context,int count,String prefix,String suffix) implements Values {
        public Proposed {
            Objects.requireNonNull(role); Objects.requireNonNull(context);
            Objects.requireNonNull(prefix); Objects.requireNonNull(suffix);
            if(count<0)throw new IllegalArgumentException("negative proposal count");
        }
    }
    public record Slot(String sheet,String key,String verb,List<String> flags,List<String> directives,
                       boolean raw,Values values,SourceLocation location) {
        public Slot {
            requireName(sheet,"sheet"); requireName(key,"slot key"); requireName(verb,"verb");
            if(sheet.startsWith("FW_"))throw new IllegalArgumentException("reserved data sheet: "+sheet);
            flags=List.copyOf(flags); directives=List.copyOf(directives);
            Objects.requireNonNull(values); Objects.requireNonNull(location);
        }
    }
    public record CustomVerdict(int code,String message) {
        public CustomVerdict { Objects.requireNonNull(message); }
    }
    private static void requireName(String value,String name) {
        Objects.requireNonNull(value,name);
        if(value.isBlank())throw new IllegalArgumentException("blank "+name);
    }
}
