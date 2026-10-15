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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Supplies the text behind an {@code FW_File=path} cell. The interpreter never touches the file system
 * itself; whoever loads a scenario decides where relative paths start (normally the scenario file's own
 * directory, so a scenario and the files it names travel together).
 */
public interface FileResolver {

    /**
     * The content of the named file as the legacy reader produced it: lines separated by line feeds, each line
     * (the last one too) followed by one.
     *
     * @param path  the text after {@code FW_File=}, exactly as written
     * @param where the cell that names the file, for diagnostics
     * @throws FrameworkException INPUT_UNREADABLE, INPUT_MALFORMED (not UTF-8) or INPUT_LIMIT
     */
    String readText(String path, SourceLocation where);

    /** A resolver for scenarios that have no directory to resolve against: every {@code FW_File=} is refused. */
    static FileResolver none() {
        return (path, where) -> {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE,
                    "FW_File=" + path + " cannot be read: this scenario was not loaded from a file, so there is no "
                            + "directory to resolve the path against", where, "file", path));
        };
    }

    /**
     * Reads files read-only; a relative path is resolved against {@code baseDirectory}, an absolute path is used
     * as it is. Content is decoded as strict UTF-8 (a byte order mark is dropped) and line endings are
     * normalised to line feeds, as the legacy reader did.
     */
    static FileResolver inDirectory(Path baseDirectory, long maxBytes) {
        return inDirectory(baseDirectory, maxBytes, InputSource::read);
    }

    /** As {@link #inDirectory(Path, long)}, with the owner's admission policy for each referenced file. */
    static FileResolver inDirectory(Path baseDirectory, long maxBytes, InputSource.Reader inputs) {
        Objects.requireNonNull(baseDirectory, "baseDirectory");
        Objects.requireNonNull(inputs, "inputs");
        return (path, where) -> {
            Path file = baseDirectory.resolve(path);
            InputSource source;
            try {
                source = inputs.read(file, maxBytes);
            } catch (FrameworkException e) {
                throw new FrameworkException(relocated(e.primary(), "FW_File=" + path + " cannot be read: "
                        + e.primary().message(), where, file), e);
            }
            String text;
            try {
                text = Utf8.decode(source);
            } catch (FrameworkException e) {
                throw new FrameworkException(relocated(e.primary(), "FW_File=" + path + ": " + e.primary().message(),
                        where, file), e);
            }
            return withLineFeeds(text);
        };
    }

    /** The same finding, reported at the cell that names the file, with its attributes (sizes, limits) and the file. */
    private static Diagnostic relocated(Diagnostic original, String message, SourceLocation where, Path file) {
        List<String> pairs = new ArrayList<>();
        original.attributes().forEach((key, value) -> {
            if (!key.equals("file")) {
                pairs.add(key);
                pairs.add(value);
            }
        });
        pairs.add("file");
        pairs.add(file.toString());
        return Diagnostic.error(original.code(), message, where, pairs.toArray(new String[0]));
    }

    /** Splits like {@link BufferedReader#readLine()} (LF, CR and CRLF end a line) and ends every line with LF. */
    static String withLineFeeds(String text) {
        StringBuilder out = new StringBuilder(text.length() + 1);
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("reading a String cannot fail", e);
        }
        return out.toString();
    }
}
