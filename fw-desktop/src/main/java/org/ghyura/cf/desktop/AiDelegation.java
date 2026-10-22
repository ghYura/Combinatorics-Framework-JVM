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

import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.model.AtomicFiles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * What the AI_delegation button knows: where the manual for an AI assistant is (AI_DELEGATION.md at the top of this
 * checkout or installation, else the copy inside the application, written out beside the desktop's settings), the agreement a human accepts before handing work to one (the manual's rules and its
 * delegation agreement), whether the human asked not to be shown that agreement again (kept with a fingerprint of its
 * text, so a changed agreement is shown again), and the manual's absolute path in the forms the clipboard can take. It
 * shows nothing, so tests drive it headless.
 */
final class AiDelegation {

    /** The manual's file name, at the top of a checkout or an installation. */
    static final String MANUAL = "AI_DELEGATION.md";
    /** The file that remembers the choice, beside the desktop's remembered places. */
    static final String STORE = "ai-delegation.properties";
    /** What the window says above the agreement. */
    static final String INTRO = "You are a busy human handing work to an AI assistant, and you stay in charge of it. Below are the "
            + "rules the assistant follows and the agreement it will ask you to accept. After OK, the path of the assistant's "
            + "manual is on the clipboard, ready to paste into its chat.";
    /** What the human states before OK can be pressed, in the author's words. */
    static final List<String> PLEDGES = List.of("I respect human work/job", "I respect long work on this latest version from very "
            + "beginning as a hand-made pet-project by the author of this Combinatorics Framework");

    /** The forms of the manual's path; the first is how the operating system in use writes it. */
    enum PathFormat {
        NATIVE, URI, SLASHES;

        @Override
        public String toString() {
            return switch (this) {
                case NATIVE -> "As " + system() + " writes it";
                case URI -> "As a file URI (file:///…)";
                case SLASHES -> "With forward slashes";
            };
        }

        /** The manual's absolute path in this form. */
        String of(Path manual) {
            Path path = manual.toAbsolutePath().normalize();
            return switch (this) {
                case NATIVE -> path.toString();
                case URI -> path.toUri().toString();
                case SLASHES -> path.toString().replace('\\', '/');
            };
        }

        private static String system() {
            String name = System.getProperty("os.name", "");
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.startsWith("windows") ? "Windows" : lower.startsWith("mac") ? "macOS" : name.isBlank() ? "this system" : name;
        }
    }

    private final Path store;
    private final List<Path> folders;
    private final Supplier<byte[]> carried;
    private final Properties choice = new Properties();

    /**
     * @param store   where the choice is remembered, or null to remember it for this session only
     * @param folders where the manual is looked for, the first that has it winning; else the application's own copy is used
     */
    AiDelegation(Path store, List<Path> folders) {
        this(store, folders, AiDelegation::bundled);
    }

    /** @param bundled the manual the application carries, or null when it carries none */
    AiDelegation(Path store, List<Path> folders, Supplier<byte[]> bundled) {
        this.store = store;
        this.folders = List.copyOf(folders);
        this.carried = bundled;
        if (store != null) {
            load();
        }
    }

    /** The desktop's: the choice beside the trail's remembered places, the manual near this checkout or installation. */
    static AiDelegation of(FileTrail trail, Examples examples) {
        Path places = trail.store();
        return new AiDelegation(places == null ? null : places.resolveSibling(STORE), folders(examples));
    }

    /** This checkout or installation, the working folder, the application's folder and the one above it. */
    static List<Path> folders(Examples examples) {
        Set<Path> folders = new LinkedHashSet<>();
        folders.add(examples.checkout().toAbsolutePath().normalize());
        folders.add(Path.of("").toAbsolutePath());
        Path application = Examples.applicationFolder();
        if (application != null) {
            folders.add(application);
            if (application.getParent() != null) {
                folders.add(application.getParent());
            }
        }
        return new ArrayList<>(folders);
    }

    // ---- the manual -------------------------------------------------------------------------------------------------

    /**
     * The manual: the first of the folders' copies, else the application's own copy written out beside the desktop's
     * settings (rewritten only when the application's copy differs), so there is always a file whose path can be pasted;
     * null when neither is there or the copy cannot be written.
     */
    Path manual() {
        Path found = locate(folders);
        return found != null ? found : unpacked();
    }

    /** The manual inside the application (the jar or the classes folder), or null. */
    static byte[] bundled() {
        try (InputStream in = AiDelegation.class.getResourceAsStream(MANUAL)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private Path unpacked() {
        byte[] text = carried.get();
        if (text == null) {
            return null;
        }
        Path folder = store != null && store.getParent() != null ? store.getParent()
                : Path.of(System.getProperty("java.io.tmpdir"), "combinatorics-framework");
        Path file = folder.resolve(MANUAL).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(file) || !Arrays.equals(Files.readAllBytes(file), text)) {
                Files.createDirectories(folder);
                AtomicFiles.write(file, text);
            }
            return file;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Where the manual is looked for. */
    List<Path> folders() {
        return folders;
    }

    /** What the window says when the manual is not there. */
    String missing() {
        return MANUAL + ", the manual for an AI assistant, was not found inside this application, nor at the top of the "
                + "Framework's checkout, beside LICENSE. Looked in:\n" + folders.stream().map(f -> "  " + f).collect(Collectors.joining("\n"));
    }

    /** The first of the folders' AI_DELEGATION.md whose title names it (another file of that name is passed over), or null. */
    static Path locate(List<Path> folders) {
        for (Path folder : folders) {
            Path file = folder.resolve(MANUAL);
            if (Files.isRegularFile(file) && titled(file)) {
                return file.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    private static boolean titled(Path file) {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = in.readLine();
            while (line != null && line.isBlank()) {
                line = in.readLine();
            }
            return line != null && line.startsWith("#") && line.contains("AI_DELEGATION");
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The agreement the window shows: the manual's rules (from "### 0.2" to the next heading) and its delegation agreement
     * (from "## 1." to the next "## " heading), without the rule that closes a section; the whole manual when it has
     * neither.
     */
    static String agreement(String manual) {
        List<String> lines = manual.lines().toList();
        String rules = section(lines, "### 0.2", "#");
        String agreement = section(lines, "## 1.", "## ");
        if (rules.isEmpty() && agreement.isEmpty()) {
            return manual.strip();
        }
        return (rules + "\n\n" + agreement).strip();
    }

    private static String section(List<String> lines, String start, String end) {
        List<String> taken = new ArrayList<>();
        boolean inside = false;
        boolean fenced = false;
        for (String line : lines) {
            if (!inside) {
                inside = line.startsWith(start);
                if (inside) {
                    taken.add(line);
                }
                continue;
            }
            if (line.strip().startsWith("```")) {
                fenced = !fenced;
            }
            if (!fenced && line.startsWith(end)) {
                break;
            }
            taken.add(line);
        }
        while (!taken.isEmpty() && (taken.getLast().isBlank() || taken.getLast().strip().equals("---"))) {
            taken.removeLast();
        }
        return String.join("\n", taken);
    }

    /** The SHA-256 of the agreement's text and the statements before OK, which a remembered choice is bound to. */
    static String fingerprint(String agreement) {
        String signed = agreement + "\n\n" + String.join("\n", PLEDGES);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java has SHA-256", e);
        }
    }

    /** The manual's path in every form that differs from the ones before it, the operating system's own first. */
    static Map<PathFormat, String> forms(Path manual) {
        Map<PathFormat, String> forms = new LinkedHashMap<>();
        for (PathFormat format : PathFormat.values()) {
            String text = format.of(manual);
            if (!forms.containsValue(text)) {
                forms.put(format, text);
            }
        }
        return forms;
    }

    // ---- the choice ---------------------------------------------------------------------------------------------------

    /** Whether the human asked not to be shown the agreement again. */
    synchronized boolean remembered() {
        return "true".equals(choice.getProperty("remember"));
    }

    /** Whether the agreement must be shown: nothing remembered, or the remembered agreement's text has changed since. */
    synchronized boolean mustAsk(String agreement) {
        return !(remembered() && fingerprint(agreement).equals(choice.getProperty("agreement.sha256")));
    }

    /** OK on the agreement: with remember, it is not shown again until its text changes; without, nothing is written. */
    synchronized void chose(String agreement, boolean remember) {
        if (remember) {
            choice.setProperty("remember", "true");
            choice.setProperty("agreement.sha256", fingerprint(agreement));
            choice.setProperty("accepted", Instant.now().toString());
            save();
        } else if (remembered()) {
            forget();
        }
    }

    /** Shows the agreement again at the next press. */
    synchronized void forget() {
        choice.setProperty("remember", "false");
        save();
    }

    /** Takes back {@link #forget}: the agreement accepted last is remembered again (nothing, when none was). */
    synchronized void remember() {
        if (choice.getProperty("agreement.sha256") != null) {
            choice.setProperty("remember", "true");
            save();
        }
    }

    /** The form of the path chosen last; the operating system's own at first. */
    synchronized PathFormat format() {
        try {
            return PathFormat.valueOf(choice.getProperty("format", PathFormat.NATIVE.name()));
        } catch (IllegalArgumentException e) {
            return PathFormat.NATIVE;
        }
    }

    /** Keeps the form of the path for the next press (in the store only when the choice is remembered there). */
    synchronized void format(PathFormat format) {
        choice.setProperty("format", format.name());
        if (remembered()) {
            save();
        }
    }

    private void load() {
        try (var in = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            choice.load(in);
        } catch (IOException | RuntimeException e) {
            // nothing remembered yet
        }
    }

    private void save() {
        if (store == null) {
            return;
        }
        try {
            if (store.getParent() != null) {
                Files.createDirectories(store.getParent());
            }
            StringWriter text = new StringWriter();
            choice.store(text, "Combinatorics Framework desktop: the AI_delegation choice (written by the desktop; safe to delete)");
            AtomicFiles.write(store, text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // a choice that cannot be remembered still holds for this session
        }
    }
}
