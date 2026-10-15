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

package org.ghyura.cf.input;

import java.util.Locale;
import java.util.Optional;

/**
 * The scenario file formats, chosen by file extension as the legacy ScheduleParser.forFile did. Unlike the
 * legacy code, an unrecognized extension is an error: the legacy fallback treated any unknown file as XLSX.
 */
public enum InputFormat {
    JSON("json", ".json"),
    YAML("yaml", ".yaml", ".yml"),
    TOML("toml", ".toml"),
    XLSX("xlsx", ".xlsx", ".xlsm");

    private final String token;
    private final String[] extensions;

    InputFormat(String token, String... extensions) {
        this.token = token;
        this.extensions = extensions;
    }

    public String token() {
        return token;
    }

    public static Optional<InputFormat> fromFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (InputFormat format : values()) {
            for (String extension : format.extensions) {
                if (lower.endsWith(extension)) {
                    return Optional.of(format);
                }
            }
        }
        return Optional.empty();
    }

    /** Matches the format token ({@code json}, {@code yaml}, {@code toml}, {@code xlsx}); also {@code yml} and {@code xlsm}. */
    public static Optional<InputFormat> fromToken(String text) {
        String lower = text.trim().toLowerCase(Locale.ROOT);
        for (InputFormat format : values()) {
            if (format.token.equals(lower)) {
                return Optional.of(format);
            }
            for (String extension : format.extensions) {
                if (extension.substring(1).equals(lower)) {
                    return Optional.of(format);
                }
            }
        }
        return Optional.empty();
    }
}
