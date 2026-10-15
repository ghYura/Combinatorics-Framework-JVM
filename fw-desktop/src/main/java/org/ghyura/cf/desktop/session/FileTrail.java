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

import org.ghyura.cf.model.AtomicFiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Where the framework's files are, all the time: a tracker that knows the newest file of every kind a desktop session
 * meets (scenarios, constraint sidecars, settings, run reports, results, checkpoints, run pages, dependency graphs,
 * scoring rules, scores, texts, libraries), so that every file button opens where those files already are or are about
 * to be written. It learns a file the moment the session reads or writes one ({@link #record}), and a thread of its own
 * watches every folder it has met (the folder of each recorded file and those given to {@link #watch}) for files that
 * appear there, whoever writes them: a run in another window, a command line. A folder is scanned once when it is first
 * watched, which finds what was written before. At most {@link #MOST_FOLDERS} folders are watched, the least recently
 * met one going first. With a store file the trail is remembered from one session to the next. Thread-safe; listeners
 * are told of every change on the tracker's thread.
 */
public final class FileTrail implements AutoCloseable {

    /** The kinds of files the framework reads and writes. */
    public enum Kind {
        SCENARIO("scenario"), CONSTRAINTS("constraint sidecar"), SETTINGS("settings"), REPORT("run report"), RESULTS("results"),
        CHECKPOINT("checkpoint"), PAGE("run page"), GRAPH("dependency graph"), RULES("scoring rules"), SCORES("scores"),
        TEXT("text"), LIBRARY("library");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A file met: its kind and when it was last written. */
    public record Seen(Path file, Kind kind, long modified) {
    }

    /** The most folders watched at once. */
    public static final int MOST_FOLDERS = 64;

    /** The most entries of a folder scanned when it is first watched. */
    public static final int MOST_SCANNED = 2_000;

    private static final int SNIFF = 1_024;
    private static final long SAVE_EVERY_MILLIS = 2_000;

    private final Map<Kind, Seen> newest = new EnumMap<>(Kind.class);
    private final Map<Path, WatchKey> folders = new LinkedHashMap<>(16, 0.75f, true);
    private final ConcurrentLinkedQueue<Path> requests = new ConcurrentLinkedQueue<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Path store;
    private final WatchService watcher;
    private final Thread thread;
    private volatile boolean closed;
    private boolean dirty;
    private long savedAt;

    private FileTrail(Path store) {
        this.store = store;
        WatchService w;
        try {
            w = Path.of(".").getFileSystem().newWatchService();
        } catch (IOException | UnsupportedOperationException e) {
            w = null;
        }
        watcher = w;
        if (store != null) {
            load();
        }
        thread = Thread.ofPlatform().daemon().name("cf-file-trail").start(this::loop);
    }

    /** A trail of this session only. */
    public static FileTrail inMemory() {
        return new FileTrail(null);
    }

    /** A trail remembered in a store file from one session to the next (created when first written). */
    public static FileTrail remembered(Path store) {
        return new FileTrail(store);
    }

    /** The default store of the desktop: desktop-places.properties under $XDG_CONFIG_HOME (else ~/.config)/combinatorics-framework. */
    public static Path defaultStore(Map<String, String> environment) {
        String config = environment.getOrDefault("XDG_CONFIG_HOME", "");
        Path base = config.isBlank() ? Path.of(System.getProperty("user.home"), ".config") : Path.of(config);
        return base.resolve("combinatorics-framework").resolve("desktop-places.properties");
    }

    // ---- what the session tells -----------------------------------------------------------------------------------

    /** Learns a file the session read, chose or wrote (its kind from its name and first bytes), and watches its folder. */
    public void record(Path file) {
        if (file == null) {
            return;
        }
        Path f = file.toAbsolutePath().normalize();
        if (Files.isDirectory(f)) {
            watch(f);
            if (Library.looksLikeOne(f)) {
                learn(new Seen(f, Kind.LIBRARY, modified(f)));
            }
            return;
        }
        Kind kind = classify(f);
        if (kind != null && Files.isRegularFile(f)) {
            learn(new Seen(f, kind, modified(f)));
        }
        Path folder = f.getParent();
        if (folder != null) {
            watch(folder);
        }
    }

    /** Watches a folder for files that appear in it, after scanning it once (on the tracker's thread). */
    public void watch(Path folder) {
        if (folder != null && !closed) {
            requests.add(folder.toAbsolutePath().normalize());
        }
    }

    /** The newest file met of one of the kinds, or null. */
    public synchronized Seen newest(Collection<Kind> kinds) {
        Seen best = null;
        for (Kind k : kinds) {
            Seen s = newest.get(k);
            if (s != null && (best == null || s.modified() > best.modified())) {
                best = s;
            }
        }
        return best;
    }

    /** The folders watched now, the most recently met last. */
    public synchronized List<Path> folders() {
        return List.copyOf(folders.keySet());
    }

    /** Called on the tracker's thread after every change of what it knows. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /** Waits until the requests made so far are handled (for tests and for a button that must see the latest). */
    public void settle(long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (!requests.isEmpty() && System.currentTimeMillis() < end && !closed) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        thread.interrupt();
        try {
            thread.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (watcher != null) {
            try {
                watcher.close();
            } catch (IOException e) {
                // closing anyway
            }
        }
        save(true);
    }

    // ---- classification ---------------------------------------------------------------------------------------------

    /**
     * The kind of a file from its name and, for JSON, XML, CSV and the like, its first bytes; null for a file the
     * framework does not read or write.
     */
    public static Kind classify(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.startsWith(".") || name.endsWith("~") || name.endsWith(".tmp")) {
            return null;
        }
        if (name.matches(".*\\.constraints\\.(json|toml|ya?ml)")) {
            return Kind.CONSTRAINTS;
        }
        if (name.equals("automation_scenarios.json") || name.equals("verified_examples.json")) {
            return Kind.LIBRARY;
        }
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        String head = switch (extension) {
            case "json", "xml", "csv", "checkpoint", "ckpt", "toml", "yaml", "yml" -> head(file);
            default -> "";
        };
        if (head.startsWith("<cf-checkpoint") || head.contains("\n<cf-checkpoint") || extension.equals("checkpoint")
                || extension.equals("ckpt")) {
            return Kind.CHECKPOINT;
        }
        return switch (extension) {
            case "xlsx", "toml", "yaml", "yml" -> Kind.SCENARIO;
            case "properties" -> Kind.SETTINGS;
            case "html", "htm" -> Kind.PAGE;
            case "dot" -> Kind.GRAPH;
            case "txt", "log", "out", "sh", "cmd", "bash", "zsh", "ps1" -> Kind.TEXT;
            case "xml" -> head.contains("<cf-export kind=\"report\"") || name.contains("report") ? Kind.REPORT : null;
            case "kv" -> Kind.RESULTS;
            case "csv" -> head.startsWith("candidate_id,repeat_idx") ? Kind.RESULTS
                    : head.toLowerCase(Locale.ROOT).contains("score") || name.contains("score") ? Kind.SCORES : null;
            case "json" -> json(head, name);
            default -> null;
        };
    }

    private static Kind json(String head, String name) {
        if (head.contains("\"sheets\"") || head.contains("\"slots\"")) {
            return Kind.SCENARIO;
        }
        if (head.contains("\"constraints\"") || head.contains("\"params\"")) {
            return Kind.CONSTRAINTS;
        }
        if (head.contains("\"required_tokens\"") || head.contains("\"forbidden_tokens\"") || head.contains("\"token_weights\"")
                || name.contains("rules")) {
            return Kind.RULES;
        }
        if (head.contains("\"nodes\"") && head.contains("\"edges\"") || name.contains("graph")) {
            return Kind.GRAPH;
        }
        return null;
    }

    private static String head(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readNBytes(SNIFF), StandardCharsets.UTF_8).stripLeading();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** What makes a folder a scenario library: an intake folder, or a manifest of one. */
    static final class Library {
        private Library() {
        }

        static boolean looksLikeOne(Path folder) {
            return Files.isDirectory(folder.resolve("intake")) || Files.isRegularFile(folder.resolve("verified_examples.json"))
                    || Files.isRegularFile(folder.resolve("automation_scenarios.json"));
        }
    }

    // ---- the tracker's thread ---------------------------------------------------------------------------------------

    private void loop() {
        while (!closed) {
            Path folder;
            while ((folder = requests.peek()) != null) {
                register(folder);
                requests.poll();
            }
            if (watcher == null) {
                pause(250);
            } else {
                try {
                    WatchKey key = watcher.poll(250, TimeUnit.MILLISECONDS);
                    if (key != null) {
                        events(key);
                    }
                } catch (InterruptedException | ClosedWatchServiceException e) {
                    if (closed) {
                        return;
                    }
                }
            }
            save(false);
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void register(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        synchronized (this) {
            if (folders.containsKey(folder)) {
                folders.get(folder);
                return;
            }
        }
        WatchKey key = null;
        if (watcher != null) {
            try {
                key = folder.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            } catch (IOException | RuntimeException e) {
                key = null;
            }
        }
        synchronized (this) {
            folders.put(folder, key);
            dirty = true;
            while (folders.size() > MOST_FOLDERS) {
                Map.Entry<Path, WatchKey> eldest = folders.entrySet().iterator().next();
                if (eldest.getValue() != null) {
                    eldest.getValue().cancel();
                }
                folders.remove(eldest.getKey());
            }
        }
        scan(folder);
    }

    private void scan(Path folder) {
        if (Library.looksLikeOne(folder)) {
            learn(new Seen(folder, Kind.LIBRARY, modified(folder)));
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            int n = 0;
            for (Path entry : entries) {
                if (++n > MOST_SCANNED) {
                    break;
                }
                consider(entry);
            }
        } catch (IOException | RuntimeException e) {
            // an unreadable folder holds nothing to point at
        }
    }

    private void events(WatchKey key) {
        Path folder = (Path) key.watchable();
        for (WatchEvent<?> e : key.pollEvents()) {
            if (e.kind() == StandardWatchEventKinds.OVERFLOW) {
                scan(folder);
                continue;
            }
            Path file = folder.resolve((Path) e.context());
            if (e.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                forget(file);
            } else {
                consider(file);
            }
        }
        if (!key.reset()) {
            synchronized (this) {
                folders.remove(folder);
            }
        }
    }

    private void consider(Path file) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Kind kind = classify(file);
        if (kind != null) {
            learn(new Seen(file.toAbsolutePath().normalize(), kind, modified(file)));
        }
    }

    private void learn(Seen seen) {
        boolean changed;
        synchronized (this) {
            Seen now = newest.get(seen.kind());
            changed = now == null || now.file().equals(seen.file()) ? !seen.equals(now) : seen.modified() >= now.modified();
            if (changed) {
                newest.put(seen.kind(), seen);
                dirty = true;
            }
        }
        if (changed) {
            listeners.forEach(Runnable::run);
        }
    }

    /** A deleted file stops being the newest of its kind; the next newest in any watched folder takes its place. */
    private void forget(Path file) {
        Path f = file.toAbsolutePath().normalize();
        Kind gone = null;
        synchronized (this) {
            for (Map.Entry<Kind, Seen> e : newest.entrySet()) {
                if (e.getValue().file().equals(f)) {
                    gone = e.getKey();
                }
            }
            if (gone != null) {
                newest.remove(gone);
                dirty = true;
            }
        }
        if (gone != null) {
            for (Path folder : folders()) {
                scan(folder);
            }
            listeners.forEach(Runnable::run);
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    // ---- the store ----------------------------------------------------------------------------------------------------

    private void load() {
        Properties p = new Properties();
        try (var in = Files.newBufferedReader(store, StandardCharsets.UTF_8)) {
            p.load(in);
        } catch (IOException | RuntimeException e) {
            return;
        }
        for (String key : p.stringPropertyNames().stream().sorted().toList()) {
            String value = p.getProperty(key);
            try {
                if (key.startsWith("folder.")) {
                    watch(Path.of(value));
                } else if (key.startsWith("newest.")) {
                    Path file = Path.of(value);
                    Kind kind = Kind.valueOf(key.substring("newest.".length()));
                    if (Files.exists(file)) {
                        learn(new Seen(file, kind, modified(file)));
                    }
                }
            } catch (IllegalArgumentException e) {
                // One stale kind or malformed path must not prevent the desktop from opening.
            }
        }
    }

    private void save(boolean now) {
        if (store == null) {
            return;
        }
        Properties p = new Properties();
        synchronized (this) {
            long time = System.currentTimeMillis();
            if (!dirty || !now && time - savedAt < SAVE_EVERY_MILLIS) {
                return;
            }
            dirty = false;
            savedAt = time;
            newest.forEach((kind, seen) -> p.setProperty("newest." + kind.name(), seen.file().toString()));
            List<Path> list = new ArrayList<>(folders.keySet());
            for (int i = 0; i < list.size(); i++) {
                p.setProperty(String.format("folder.%03d", i), list.get(i).toString());
            }
        }
        try {
            Files.createDirectories(store.getParent());
            java.io.StringWriter text = new java.io.StringWriter();
            p.store(text, "Combinatorics Framework desktop: where its files are (written by the desktop; safe to delete)");
            AtomicFiles.write(store, text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // a trail that cannot be remembered still works for this session
        }
    }
}
