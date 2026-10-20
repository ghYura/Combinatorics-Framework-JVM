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

import java.util.*;

/**
 * Calibration only, never evidence: runs sweep rows one after another in one JVM, one compact line each, so a wrong setup
 * is found before the Framework runs the suite. Arguments: family, then rows as button:setup[:sudden].
 */
public final class CalibrateV2 {
    public static void main(String[] args) throws Exception {
        for (int i = 1; i < args.length; i++) {
            String[] row = args[i].split(":");
            Map<String, String> options = new LinkedHashMap<>();
            options.put("button", row[0]);
            options.put("setup", row[1]);
            if (row.length > 2) options.put("sudden", row[2]);
            ButtonProbe p = ButtonProbe.execute(args[0], options);
            int verdict = p.verdict();
            ButtonProbe.Watch w = p.watches.get(row[0]);
            StringBuilder line = new StringBuilder(args[i]).append(" | V=").append(verdict);
            if (w != null) {
                for (var s : w.samples) if (s.containsKey("expected")) line.append(" | exp=").append(s.get("expected")).append(" got=").append(s.get("pressable")).append(Boolean.TRUE.equals(s.get("ok")) ? " ok" : " MISMATCH");
                line.append(" | actions=").append(w.actions);
            }
            for (var x : p.watches.values()) for (String f : x.failures) if (x != w || x.hard) line.append(" | ").append(x == w ? "" : x.id + ": ").append(f.length() > 160 ? f.substring(0, 160) : f);
            for (String n : p.notes) line.append(" | note: ").append(n.lines().findFirst().orElse("").substring(0, Math.min(200, n.lines().findFirst().orElse("").length())));
            System.out.println(line);
        }
        System.exit(0);
    }
}
