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

package org.ghyura.cf.campaign;

import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.store.KeyDictionary;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * An assembled candidate as a system under test sees it. The selections are built on the admitting thread; the source is
 * rendered on first request, under the renderer's lock, and kept.
 */
final class AssembledCandidate implements SutCandidate {

    private final Candidate candidate;
    private final List<SheetSelection> selections;
    private final CandidateRenderer renderer;
    private volatile String source;

    AssembledCandidate(Candidate candidate, List<String> sheets, KeyDictionary dictionary, CandidateRenderer renderer) {
        this.candidate = candidate;
        this.renderer = renderer;
        List<SheetSelection> list = new ArrayList<>(sheets.size());
        for (int c = 0; c < sheets.size(); c++) {
            SelectionState state = candidate.states().get(c);
            Row row = candidate.rows().get(c);
            List<String> values = new ArrayList<>();
            if (state != SelectionState.ABSENT && row != null) {
                for (int label : row.keys().labels()) {
                    values.add(dictionary.value(label).text());
                }
            }
            list.add(new SheetSelection(sheets.get(c), state, values));
        }
        this.selections = List.copyOf(list);
    }

    @Override
    public CandidateId id() {
        return candidate.id();
    }

    @Override
    public String displayId() {
        return candidate.display();
    }

    @Override
    public BigInteger ordinal() {
        return candidate.ordinal();
    }

    @Override
    public List<SheetSelection> selections() {
        return selections;
    }

    @Override
    public String source() {
        String s = source;
        if (s == null) {
            synchronized (renderer) {
                s = source;
                if (s == null) {
                    s = renderer.renderText(candidate);
                    source = s;
                }
            }
        }
        return s;
    }
}
