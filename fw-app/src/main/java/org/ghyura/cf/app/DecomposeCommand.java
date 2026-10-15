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

package org.ghyura.cf.app;

import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.compiler.SourceDecomposition;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code decompose} (GEN:source-decomposition; the legacy code_decompose.py CLI): splits a source file into byte-preserving
 * pieces and writes the fwgen spec the legacy tool wrote, one raw slot per piece holding its original text, so rewrites
 * added to the slots are combined and reassemble into programs. Without {@code --out} the spec goes to standard output;
 * with it, the spec is written atomically and the pieces are listed as the legacy tool listed them. Nothing is run.
 */
final class DecomposeCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = Path.of(command.value("--source").orElseThrow());
        SourceDecomposition.Language language = SourceDecomposition.Language.ofToken(command.value("--lang").orElse("auto"));
        long maxCell = CandidatesCommand.number(command, "--max-cell", 0);
        String name = command.value("--name").orElse(stem(file));
        String text = decode(InputSource.read(file).copyOfBytes(), file);
        List<SourceDecomposition.Piece> pieces = SourceDecomposition.decompose(text, language);
        if (maxCell > 0) {
            pieces = SourceDecomposition.subsplitOversize(pieces, (int) Math.min(maxCell, Integer.MAX_VALUE));
        }
        String spec = SourceDecomposition.toml(pieces, name);
        Path out = command.value("--out").map(Path::of).orElse(null);
        if (out != null) {
            try {
                AtomicFiles.write(out, spec.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the decomposition spec cannot be written: "
                        + e.getMessage(), SourceLocation.of(out.toString()), "path", out.toString()));
            }
        }
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"decompose\" status=\"OK\" exitCode=\"").append(ExitCode.OK.code()).append("\" exitName=\"")
                    .append(ExitCode.OK.name()).append("\">\n");
            xml.append("  <decomposition source=\"").append(Xml.escape(file.toString())).append("\" name=\"").append(Xml.escape(name))
                    .append("\" pieces=\"").append(pieces.size()).append('"');
            if (out != null) {
                xml.append(" spec=\"").append(Xml.escape(out.toString())).append('"');
            }
            xml.append(">\n");
            for (int i = 0; i < pieces.size(); i++) {
                SourceDecomposition.Piece p = pieces.get(i);
                Map.Entry<Integer, Integer> at = SourceDecomposition.lineAndColumn(text, p.start());
                xml.append("    <piece index=\"").append(i + 1).append("\" kind=\"").append(Xml.escape(p.kind())).append("\" name=\"")
                        .append(Xml.escape(p.name())).append("\" line=\"").append(at.getKey()).append("\" column=\"").append(at.getValue())
                        .append("\" chars=\"").append(p.code().length()).append("\" interacts=\"").append(p.note().contains("interacts"))
                        .append("\"/>\n");
            }
            if (out == null) {
                xml.append("    <spec>").append(Xml.escape(spec)).append("</spec>\n");
            }
            xml.append("  </decomposition>\n</cf-report>\n");
            context.out().print(xml);
        } else if (out == null) {
            context.out().print(spec);
        } else {
            context.out().println(pieces.size() + " pieces → " + out);
            for (int i = 0; i < pieces.size(); i++) {
                SourceDecomposition.Piece p = pieces.get(i);
                context.out().println(String.format(Locale.ROOT, "  P%02d %-9s %-24s %5dB%s", i + 1, p.kind(), p.name().isEmpty() ? "(anon)"
                        : p.name(), p.code().length(), p.note().contains("interacts") ? " ⇄" : ""));
            }
        }
        return ExitCode.OK;
    }

    /** Strict UTF-8: a decomposition must give back the source's bytes, so malformed input is refused, not replaced. */
    private static String decode(byte[] bytes, Path file) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "the source is not valid UTF-8, so its pieces could "
                    + "not give its bytes back", SourceLocation.of(file.toString())));
        }
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
