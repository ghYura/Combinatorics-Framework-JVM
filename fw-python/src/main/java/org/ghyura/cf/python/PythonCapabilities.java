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

package org.ghyura.cf.python;

import org.ghyura.cf.spi.AddonCapabilities;

import java.util.List;

/** What the Python add-on states about the capability mapping while it is installed. */
public final class PythonCapabilities implements AddonCapabilities {

    static final String C07_EVIDENCE = "cf.execution.sut=python runs every candidate's rendered Python with the configured "
            + "interpreter, as the legacy executor's trusted-local mode did: the legacy runpy runner and FW_VAR/FW_CUSTOM_VAR verdict, "
            + "FW_Arguments, a Python FW_RunMeFirstOnce, cf.python.path and cf.python.workdir, bounded output and the app= metrics "
            + "line; a deadline or cancellation kills the process tree (PythonPluginTest). Every original demo but D14b (21 of the 22 "
            + "SmallUseCasesDemo folders, 29 variants) runs its own unchanged Python with every case's outcome and verdict equal to its "
            + "Java port (OriginalDemosPythonTest), D10 also from the packaged application (PackagedApplicationIT); D14b equals its Java "
            + "port too once its legacy Docker PostgreSQL fixture and sandbox are provided (0122 records, not an automated test here). "
            + "Legacy generator_trunk examples run as the legacy bundle ran them (the tree as working directory and on PYTHONPATH, "
            + "BUNDLE_REPO_ROOT, BUNDLE_SUT_ROOT, a line break after every sheet, cf.python.env): 113 of the 160 surveyed give verdicts "
            + "from their own Python with the libraries and servers they name; the rest are Java, data or prompt specs, templates, a "
            + "superseded legacy GUI self-test or AI examples not run on this machine (0123 dependency matrix). Trusted and unsandboxed "
            + "unless cf.python.sandbox runs every candidate in bubblewrap or a container (PythonPluginTest; D14b's legacy networked "
            + "sandbox through it, 0126 records): the interpreter and the libraries the candidates import come from the environment.";

    static final String C14_EVIDENCE = "no Java port (author decision 2026-10-11: SUT code is not ported); the original SUT Python runs "
            + "unchanged through this add-on instead: the automation-scheme-studio constructor drives the legacy scenarios' circuits "
            + "(11 scenarios give verdicts; 00_smoke from the packaged application in PackagedApplicationIT) and the 3D sieve "
            + "examples give verdicts with the shapely library installed (0122 records). Not covered: the shape_vs_sieve GUI viewers, "
            + "which are interactive and have no scenario.";

    static final String C15_EVIDENCE = "no Java port (author decision 2026-10-11: SUT code is not ported); the original finance stack runs "
            + "unchanged instead: the 12 legacy fintech_oot batches give verdicts from their own Python through this add-on against "
            + "the live two-node cluster that fintech_oot/up_instances.sh starts, with the httpx library installed (0122 records). "
            + "The user starts and stops the cluster; nothing here starts servers, so no automated test covers it.";

    @Override
    public String addon() {
        return "fw-python";
    }

    @Override
    public List<Statement> statements() {
        return List.of(new Statement("C07", "implemented", C07_EVIDENCE), new Statement("C14", "partial", C14_EVIDENCE),
                new Statement("C15", "partial", C15_EVIDENCE));
    }
}
