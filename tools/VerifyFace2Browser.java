// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
import org.ghyura.cf.model.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Optional real browser acceptance test. Requires Firefox and a local geckodriver, no Selenium/Python/npm.
 * Run after mvn verify from the repository root:
 * java -cp fw-app/target/fw-app.jar tools/VerifyFace2Browser.java /path/to/geckodriver
 * This launches a temporary Firefox profile and the real workbench browser connection. */
public final class VerifyFace2Browser {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String webdriver;
    private String browserSession;

    private VerifyFace2Browser() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Pass the path to a locally installed geckodriver.");
        new VerifyFace2Browser().run(Path.of(args[0]));
    }

    private void run(Path driver) throws Exception {
        Path output = Path.of("fw-desktop/target/face2-browser").toAbsolutePath(); Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("result.json"));
        Files.deleteIfExists(output.resolve("face2.png"));
        Files.deleteIfExists(output.resolve("failure.png"));
        Files.deleteIfExists(output.resolve("failure-page.html"));
        String appHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("fw-app/target/fw-app.jar"))));
        Path temporary = Files.createTempDirectory("cf-face2-browser-");
        String classpath = Path.of("fw-desktop/target/test-classes").toAbsolutePath() + java.io.File.pathSeparator
                + Path.of("fw-app/target/fw-app.jar").toAbsolutePath();
        Process jvm = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", classpath, "org.ghyura.cf.desktop.Face2BrowserHarness", temporary.toString())
                .redirectError(output.resolve("jvm.log").toFile()).start();
        Process gecko = null;
        try (var lines = new BufferedReader(new InputStreamReader(jvm.getInputStream(), StandardCharsets.UTF_8));
             var commands = new PrintWriter(jvm.getOutputStream(), true, StandardCharsets.UTF_8)) {
            String url = line(lines, "URL ", jvm);
            int port;
            try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = socket.getLocalPort(); }
            webdriver = "http://127.0.0.1:" + port;
            gecko = new ProcessBuilder(driver.toAbsolutePath().toString(), "--host", "127.0.0.1", "--port", Integer.toString(port))
                    .redirectErrorStream(true).redirectOutput(output.resolve("geckodriver.log").toFile()).start();
            boolean ready = false;
            for (int i = 0; i < 100 && gecko.isAlive(); i++) {
                try { call("GET", "/status", null); ready = true; break; }
                catch (java.io.IOException waiting) { Thread.sleep(100); }
            }
            require(ready, "geckodriver did not start; see " + output);
            var session = object(call("POST", "/session", Map.of("capabilities", Map.of("alwaysMatch", Map.of(
                    "browserName", "firefox", "moz:firefoxOptions", Map.of("args", List.of("-headless")))))));
            browserSession = (String) session.get("sessionId");
            call("POST", sessionPath("/window/rect"), Map.of("width", 1440, "height", 1000));
            call("POST", sessionPath("/url"), Map.of("url", url));
            // Firefox's WebDriver context does not expose the page's top-level lexical bindings.
            // Check the rendered controls and persisted workbench state, as a browser user would.
            waitFor("document.querySelectorAll('#quick-s1 option').length === 3");
            require(Boolean.TRUE.equals(script("return ['multi','addformula','fdlg','mdlg'].every(id => document.getElementById(id) !== null)")), "Original group and formula editors missing");
            script("for (const [id,value] of [['quick-s1','A'],['quick-s2','B'],['quick-v1','a'],['quick-v2','1']]) { const e=document.getElementById(id); e.value=value; e.dispatchEvent(new Event('change',{bubbles:true})); } return true;");
            click("#quick-add");
            waitFor("document.querySelectorAll('#links [data-del]').length === 1 && document.getElementById('impact').textContent.includes('6 kept')");
            require(Boolean.TRUE.equals(script("return document.querySelectorAll('#edges [data-bi]').length > 0")), "The original graph did not draw the link");
            Files.write(output.resolve("face2.png"), Base64.getDecoder().decode((String) call("GET", sessionPath("/screenshot"), null)));
            click("#submit"); waitFor("document.getElementById('overlay').classList.contains('show')");
            var first = state(lines, commands, jvm, "state"); checkState(first, 1, 1, 6, false);
            click("#t-edit"); waitFor("!document.getElementById('overlay').classList.contains('show') && document.querySelectorAll('#links [data-del]').length === 1 && document.getElementById('impact').textContent.includes('6 kept')");
            click("#links [data-del]"); click("#submit"); waitFor("document.getElementById('overlay').classList.contains('show')");
            var cleared = state(lines, commands, jvm, "state"); checkState(cleared, 0, 0, 8, false);
            click("#t-edit"); waitFor("!document.getElementById('overlay').classList.contains('show') && document.querySelectorAll('#links [data-del]').length === 0 && document.getElementById('impact').textContent.includes('8 kept')");
            state(lines, commands, jvm, "edit");
            click("#submit"); waitFor("document.getElementById('warn').textContent.includes('changed')");
            var conflict = state(lines, commands, jvm, "state"); checkState(conflict, 0, 1, 8, true);
            String report = Json.write(Map.of("status", "PASS", "appSha256", appHash, "firstSubmit", first, "editAgainDelete", cleared, "staleSubmission", conflict));
            Files.writeString(output.resolve("result.json"), report); System.out.println(report);
        } catch (Exception failure) {
            if (browserSession != null) try {
                Files.write(output.resolve("failure.png"), Base64.getDecoder().decode((String) call("GET", sessionPath("/screenshot"), null)));
                Files.writeString(output.resolve("failure-page.html"), (String) call("GET", sessionPath("/source"), null));
            } catch (Exception diagnosticFailure) { failure.addSuppressed(diagnosticFailure); }
            Files.writeString(output.resolve("result.json"), Json.write(Map.of("status", "FAIL", "appSha256", appHash, "error", failure.toString())));
            throw failure;
        } finally {
            if (browserSession != null) try { call("DELETE", sessionPath(""), null); } catch (Exception ignored) { }
            if (gecko != null) { gecko.destroy(); if (!gecko.waitFor(10, TimeUnit.SECONDS)) gecko.destroyForcibly(); }
            try (var commands = new PrintWriter(jvm.getOutputStream(), true, StandardCharsets.UTF_8)) { commands.println("exit"); }
            if (!jvm.waitFor(15, TimeUnit.SECONDS)) jvm.destroyForcibly();
            client.close();
        }
    }

    private Object call(String method, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(webdriver + path)).timeout(Duration.ofSeconds(45));
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body)));
        if (body != null) request.header("Content-Type", "application/json");
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) throw new IllegalStateException("WebDriver " + response.statusCode() + ": " + response.body());
        return object(Json.parse(response.body(), "WebDriver response")).get("value");
    }

    private String sessionPath(String suffix) { return "/session/" + browserSession + suffix; }
    private Object script(String code) throws Exception { return call("POST", sessionPath("/execute/sync"), Map.of("script", code, "args", List.of())); }
    private void click(String selector) throws Exception {
        var element = object(call("POST", sessionPath("/element"), Map.of("using", "css selector", "value", selector)));
        call("POST", sessionPath("/element/" + element.get("element-6066-11e4-a52e-4f735466cecf") + "/click"), Map.of());
    }
    private void waitFor(String expression) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (Boolean.TRUE.equals(script("return !!(" + expression + ")"))) return;
            Thread.sleep(100);
        }
        throw new IllegalStateException("Browser condition timed out: " + expression);
    }
    private static String line(BufferedReader lines, String prefix, Process process) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            if (lines.ready()) {
                String line = lines.readLine(); if (line == null) break;
                if (line.startsWith(prefix)) return line.substring(prefix.length());
            } else if (!process.isAlive()) break;
            else Thread.sleep(100);
        }
        throw new IllegalStateException("JVM did not return " + prefix + "; inspect fw-desktop/target/face2-browser/jvm.log");
    }
    private static Map<?, ?> state(BufferedReader lines, PrintWriter commands, Process process, String command) throws Exception {
        commands.println(command); return object(Json.parse(line(lines, "STATE ", process), "workbench state"));
    }
    private static Map<?, ?> object(Object value) { return (Map<?, ?>) value; }
    private static void checkState(Map<?, ?> state, int saved, int workbench, int kept, boolean dirty) {
        require(((Number) state.get("savedRules")).intValue() == saved && ((Number) state.get("workbenchRules")).intValue() == workbench
                && ((Number) state.get("kept")).intValue() == kept && Boolean.valueOf(dirty).equals(state.get("dirty")), "Unexpected workbench state: " + state);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
