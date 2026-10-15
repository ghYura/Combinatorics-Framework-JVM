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

package org.ghyura.cf.acceptance;

import java.util.List;

/**
 * Artifacts the architecture rules out of the distribution (R02, R03, D10): SQL drivers and ORMs, embedded
 * databases and key-value stores, network servers and RPC stacks, and scripting runtimes. An entry without a
 * colon bans a whole group (and every group below it, so {@code org.hibernate.orm} falls under
 * {@code org.hibernate}); an entry {@code group:artifact} bans one artifact, for groups that also hold
 * harmless libraries ({@code com.zaxxer} publishes HikariCP, a connection pool, and SparseBitSet, a bitset).
 */
final class BannedDependencies {

    static final List<String> ENTRIES = List.of(
            // databases, drivers, ORMs, embedded stores
            "org.postgresql", "org.hibernate", "jakarta.persistence", "javax.persistence", "com.h2database",
            "org.hsqldb", "org.apache.derby", "org.xerial", "com.mysql", "mysql", "org.mariadb", "com.oracle.database",
            "com.microsoft.sqlserver", "org.mongodb", "redis.clients", "io.lettuce", "org.rocksdb", "org.mapdb",
            "com.zaxxer:HikariCP", "com.mchange", "org.apache.commons.dbcp", "org.apache.commons.dbcp2",
            // network servers, clients and RPC
            "io.grpc", "io.netty", "org.eclipse.jetty", "org.apache.tomcat", "io.undertow", "org.apache.httpcomponents",
            "com.squareup.okhttp3", "org.springframework", "io.vertx",
            // scripting and foreign-language runtimes
            "org.python", "org.graalvm", "org.codehaus.groovy", "org.apache.groovy", "org.mozilla", "org.jruby",
            "net.java.dev.jna", "org.bytedeco");

    private BannedDependencies() {
    }

    static boolean isBanned(String groupId, String artifactId) {
        for (String banned : ENTRIES) {
            int colon = banned.indexOf(':');
            if (colon >= 0) {
                if (groupId.equals(banned.substring(0, colon)) && artifactId.equals(banned.substring(colon + 1))) {
                    return true;
                }
            } else if (groupId.equals(banned) || groupId.startsWith(banned + ".")) {
                return true;
            }
        }
        return false;
    }
}
