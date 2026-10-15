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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.SutPlugin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.TreeSet;

/**
 * Finds the typed SUT plugin {@code cf.execution.sut} names with {@link ServiceLoader}, on the application class path
 * and on {@code cf.execution.classpath}. The loader over the extra class path belongs to the run and is closed with it.
 */
final class SutResolver implements AutoCloseable {

    private final URLClassLoader loader;

    SutResolver(List<String> classpath) {
        if (classpath.isEmpty()) {
            loader = null;
            return;
        }
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath) {
            Path p = Path.of(entry).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "cf.execution.classpath names '" + entry
                        + "', which does not exist", SourceLocation.NONE, "key", "cf.execution.classpath", "entry", entry));
            }
            try {
                urls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "cf.execution.classpath entry '" + entry
                        + "' is not a usable path", SourceLocation.NONE, "key", "cf.execution.classpath"));
            }
        }
        loader = new URLClassLoader("cf-plugins", urls.toArray(URL[]::new), SutResolver.class.getClassLoader());
    }

    ClassLoader loader() {
        return loader != null ? loader : SutResolver.class.getClassLoader();
    }

    /** The plugin with the given id, exactly one. */
    SutPlugin plugin(String id) {
        List<SutPlugin> matches = new ArrayList<>();
        TreeSet<String> available = new TreeSet<>();
        for (SutPlugin p : ServiceLoader.load(SutPlugin.class, loader())) {
            available.add(p.id());
            if (p.id().equals(id)) {
                matches.add(p);
            }
        }
        if (matches.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_SUT_UNKNOWN, "cf.execution.sut = " + id + " names no SUT plugin; "
                    + (available.isEmpty() ? "none is on the class path" : "available: " + String.join(", ", available)) + "; '"
                    + RunConfig.GENERATED_JAVA + "' compiles the generated candidates instead", SourceLocation.NONE, "sut", id));
        }
        if (matches.size() > 1) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_SUT_AMBIGUOUS, matches.size() + " SUT plugins have the id " + id
                    + ": " + matches.stream().map(p -> p.getClass().getName()).toList(), SourceLocation.NONE, "sut", id));
        }
        return matches.getFirst();
    }

    @Override
    public void close() {
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                // a jar that cannot be closed leaks one file handle until exit; nothing else depends on it
            }
        }
    }
}
