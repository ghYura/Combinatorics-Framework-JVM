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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Admission for one complete loading operation: the scenario, constraints, bonds and every FW_File input share a
 * budget. Leases remain live until all readers and interpretation finish, then the key dictionary admits the retained
 * scenario. These measured parser factors are estimates, not a universal heap bound. The caller owns unsaved text and
 * edited documents; this scope admits their additional encoding/parsing work.
 */
final class ScenarioInputs implements InputSource.Reader, AutoCloseable {
    private final Path scenario;
    private final MemoryBudget budget;
    private final List<MemoryBudget.Lease> leases = new ArrayList<>();

    ScenarioInputs(Path scenario, RunConfig config) {
        this.scenario = scenario.toAbsolutePath().normalize();
        budget = MemoryBudget.of(config);
    }

    @Override
    public InputSource read(Path file, long maxBytes) {
        long size = InputSource.checkedSize(file, maxBytes);
        long estimate = scenario.equals(file.toAbsolutePath().normalize())
                ? ScenarioLoader.readingBytes(file) : MemorySizes.multiply(ScenarioLoader.TEXT_READING_FACTOR, size);
        admit(file.toString(), estimate);
        try {
            // A growing file must not use memory that was not admitted by the size check above.
            return InputSource.read(file, Math.min(size, maxBytes));
        } catch (FrameworkException e) {
            if (size < maxBytes && e.primary().code() == ErrorCode.INPUT_LIMIT) {
                throw MemorySizes.limit("input " + file + " grew beyond its admitted size of " + size + " bytes; retry loading it");
            }
            throw e;
        }
    }

    InputSource text(String origin, String text) {
        // Three UTF-8 bytes per UTF-16 code unit also bounds supplementary characters and replacement characters.
        admit(origin, MemorySizes.multiply(ScenarioLoader.TEXT_READING_FACTOR, MemorySizes.multiply(3, text.length())));
        return InputSource.ofText(origin, text);
    }

    private void admit(String origin, long estimate) {
        long bookkeeping = MemorySizes.add(256, MemorySizes.multiply(2, origin.length()));
        leases.add(budget.reserve(MemorySizes.add(estimate, bookkeeping), "reading the scenario input " + origin));
    }

    @Override
    public void close() {
        leases.forEach(MemoryBudget.Lease::close);
        leases.clear();
    }
}
