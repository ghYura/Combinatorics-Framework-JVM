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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The example inputs a first run can start from, so every file field can say "like this one": the showcase scenario of
 * this repository, the library of verified examples (the generator_trunk folder of a Combinatorics-Framework-QA-edition
 * checkout, usually a sibling of this one) and a metrics corpus inside that library. They are looked up at fixed relative
 * paths from the working folder, the application's folder and a few of their parents; nothing is scanned, and an example
 * that is not there is simply not offered.
 */
final class Examples {

    /** The folder name of the QA edition checkout that holds the library. */
    static final String QA_EDITION = "Combinatorics-Framework-QA-edition";
    /** How many parents of each starting folder are tried. */
    private static final int PARENTS = 3;

    private final List<Path> roots;

    /** Examples under these folders, the first that has one winning. */
    Examples(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    /** Examples near the working folder and the application's own folder. */
    static Examples near() {
        Set<Path> roots = new LinkedHashSet<>();
        for (Path start : new Path[]{Path.of("").toAbsolutePath(), applicationFolder()}) {
            Path p = start == null ? null : start.normalize();
            for (int i = 0; p != null && i <= PARENTS; i++, p = p.getParent()) {
                roots.add(p);
            }
        }
        return new Examples(new ArrayList<>(roots));
    }

    /** The showcase scenario (scenarios/showcase/showcase.xlsx), or null. */
    Path scenario() {
        for (Path root : roots) {
            Path file = root.resolve("scenarios").resolve("showcase").resolve("showcase.xlsx");
            if (Files.isRegularFile(file)) return file;
        }
        return null;
    }

    /** The library: a generator_trunk folder with intake/verified_examples.json, or null. */
    Path library() {
        for (Path root : roots) {
            for (Path tree : new Path[]{root.resolve(QA_EDITION).resolve("generator_trunk"), root.resolve("generator_trunk"), root}) {
                if (Files.isRegularFile(tree.resolve("intake").resolve("verified_examples.json"))) return tree;
            }
        }
        return null;
    }

    /** A metrics corpus of the library (tryout_own/pricing/pricing_metrics.kv), or null. */
    Path corpus() {
        Path library = library();
        Path file = library == null ? null : library.resolve("tryout_own").resolve("pricing").resolve("pricing_metrics.kv");
        return file != null && Files.isRegularFile(file) ? file : null;
    }

    /**
     * This checkout (the folder with the build's pom.xml and fw-app) or installation (the folder with addons or scenarios),
     * the first such of the folders examples are looked up from; else the working folder.
     */
    Path checkout() {
        for (Path root : roots) {
            if (Files.isRegularFile(root.resolve("pom.xml")) && Files.isDirectory(root.resolve("fw-app"))) return root;
        }
        for (Path root : roots) {
            if (Files.isDirectory(root.resolve("addons")) || Files.isDirectory(root.resolve("scenarios"))) return root;
        }
        return roots.isEmpty() ? Path.of("").toAbsolutePath() : roots.getFirst();
    }

    /** The folder of the jar (or classes folder) this desktop was loaded from, or null. */
    static Path applicationFolder() {
        try {
            Path source = Path.of(Examples.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(source) ? source.getParent() : source;
        } catch (java.net.URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
