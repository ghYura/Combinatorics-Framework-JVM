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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs inside the isolated root of {@code PackagedApplicationIT} and reports what that root really denies.
 * It is the control that gives the application's own smoke run meaning: if the network were reachable here,
 * "the application works without network" would prove nothing. It tries the forbidden things on purpose, so
 * the architecture gate allow-lists exactly this class for socket use.
 *
 * <p>The connect target is 192.0.2.1 (TEST-NET-1, RFC 5737), which is never routable, so even a failed
 * isolation sends nothing anywhere useful.</p>
 */
public final class IsolationProbe {

    private IsolationProbe() {
    }

    /** @param args args[0] is the working directory to attempt a write in */
    public static void main(String[] args) throws IOException {
        System.out.println("usr.bin=" + Files.exists(Path.of("/usr/bin")));
        System.out.println("bin=" + Files.exists(Path.of("/bin")));
        System.out.println("home=" + Files.exists(Path.of("/home")));

        List<String> interfaces = new ArrayList<>();
        var found = NetworkInterface.getNetworkInterfaces();
        if (found != null) {
            for (NetworkInterface i : Collections.list(found)) {
                interfaces.add(i.getName());
            }
        }
        Collections.sort(interfaces);
        System.out.println("interfaces=" + String.join(",", interfaces));

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("192.0.2.1", 9), 1500);
            System.out.println("connect=connected");
        } catch (IOException e) {
            System.out.println("connect=" + e.getClass().getSimpleName());
        }

        try {
            Files.writeString(Path.of(args[0], "probe.txt"), "x");
            System.out.println("write=succeeded");
        } catch (IOException e) {
            System.out.println("write=" + e.getClass().getSimpleName());
        }
    }
}
