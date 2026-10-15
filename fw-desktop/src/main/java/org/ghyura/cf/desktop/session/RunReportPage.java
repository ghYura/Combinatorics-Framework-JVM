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

import org.ghyura.cf.analysis.Triage;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A finished run as a page a non-author can read (GEN:diagnostics-reporting-release; the legacy {@code bundle report}, its
 * "face 3"): one self-contained HTML file (no external style, script, font or image) rendered from the run's checkpoint
 * only, so it can be opened from a file, copied or attached. Like the legacy page it shows what is absent as plainly as
 * what is present: a checkpoint keeps observations, not stage timings or an analysis, and those sections say so. Its
 * sections: the run, its verdicts, the triage findings ({@link Triage}), stages, the analyzer front and the kept
 * attachments. The page is deterministic: the same checkpoint renders the same bytes (no render time is written).
 */
public final class RunReportPage {

    public static final String SCHEMA = "cf.report/v1";

    private RunReportPage() {
    }

    private static final Map<Outcome, String> OUTCOME_CLASS = new EnumMap<>(Map.of(Outcome.PASS, "ok", Outcome.DOMAIN_FAIL, "fail",
            Outcome.INFRA_FAIL, "bad", Outcome.BROKEN, "bad", Outcome.TIMEOUT, "bad", Outcome.CANCELLED, "warn", Outcome.SKIPPED, "muted"));

    private static String stateClass(String state) {
        return switch (state) {
            case "COMPLETED" -> "ok";
            case "FAILED" -> "bad";
            case "CANCELLED", "PAUSED" -> "warn";
            default -> "muted";
        };
    }

    /** {@code html.escape}: the five characters HTML gives meaning. */
    static String e(Object v) {
        String s = v == null ? "" : v.toString();
        StringBuilder out = new StringBuilder(s.length());
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#x27;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String fixed(double x, int digits) {
        return new BigDecimal(x).setScale(digits, RoundingMode.HALF_EVEN).toPlainString();
    }

    private static String kv(List<String[]> rows) {
        StringBuilder body = new StringBuilder("<table class='kv'>");
        for (String[] r : rows) {
            if (r[1] != null) {
                body.append("<tr><th>").append(e(r[0])).append("</th><td>").append(r[1]).append("</td></tr>");
            }
        }
        return body.append("</table>").toString();
    }

    private static String section(String title, String body, String note) {
        return "<section><h2>" + e(title) + "</h2>" + (note == null ? "" : "<p class='note'>" + note + "</p>") + body + "</section>";
    }

    private static String absent(String text) {
        return "<p class='absent'>" + e(text) + "</p>";
    }

    /**
     * The page of a run.
     *
     * @param source where the checkpoint was read from, shown under the title
     * @param budget admits rendering/escaping buffers and the triage of the checkpoint; the caller owns the returned text
     */
    @SuppressWarnings("try")
    public static String html(Checkpoint c, String source, MemoryBudget budget) {
        try (MemoryBudget.Lease output = budget.reserve(renderingBytes(c, source), "HTML report buffers")) {
            return body(c, source, budget);
        }
    }

    /** The same page with UTF-8 encoding covered by the operation's reservation; returned bytes belong to the caller. */
    @SuppressWarnings("try")
    public static byte[] encode(Checkpoint c, String source, MemoryBudget budget) {
        try (MemoryBudget.Lease output = budget.reserve(renderingBytes(c, source), "HTML report and UTF-8 buffers")) {
            return body(c, source, budget).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static long renderingBytes(Checkpoint c, String source) {
        return MemorySizes.add(131_072, MemorySizes.multiply(24,
                MemorySizes.add(MemorySizes.retained(c), MemorySizes.retained(source))));
    }

    private static String body(Checkpoint c, String source, MemoryBudget budget) {
        Map<SampleSlot, Observation> latest = new LinkedHashMap<>();
        for (Observation o : c.observations()) {
            latest.merge(o.key().slot(), o, (a, b) -> b.key().attempt() > a.key().attempt() ? b : a);
        }
        List<Observation> finals = new ArrayList<>(latest.values());
        String body = run(c) + verdicts(c, finals) + findings(Triage.ofCheckpoint(c, source, budget)) + stages() + front() + attachments(c);
        return "<!doctype html>\n<html lang='en'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Run " + e(c.runId()) + "</title><style>" + CSS + "</style></head><body><main>"
                + "<h1>Run <span class='mono'>" + e(c.runId()) + "</span></h1>"
                + "<p class='sub'>" + e(source) + "</p>"
                + body
                + "<footer>" + e(SCHEMA) + " — rendered from this run's own checkpoint; no candidate was executed again.</footer>"
                + "</main></body></html>\n";
    }

    private static String run(Checkpoint c) {
        StringBuilder settings = new StringBuilder("<details><summary>configuration (" + c.configuration().size()
                + " settings, secrets left out)</summary><table class='kv'>");
        c.configuration().forEach((k, v) -> settings.append("<tr><th class='mono'>").append(e(k)).append("</th><td class='mono'>").append(e(v))
                .append("</td></tr>"));
        settings.append("</table></details>");
        List<String[]> rows = List.of(
                new String[]{"state", "<span class='pill " + stateClass(c.state()) + "'>" + e(c.state()) + "</span>"},
                new String[]{"scenario", "<span class='mono'>" + e(c.scenarioPath()) + "</span>"},
                new String[]{"scenario sha256", "<span class='mono muted'>" + e(head(c.scenarioDigest(), 16)) + "</span>"},
                new String[]{"plan digest", "<span class='mono muted'>" + e(head(c.planDigest(), 16)) + "</span>"},
                new String[]{"key dictionary", "<span class='mono muted'>" + e(head(c.dictionaryDigest(), 16)) + "</span>"},
                new String[]{"system under test", e(c.plugin()) + " <span class='muted'>" + e(c.pluginVersion()) + "</span>"},
                new String[]{"execution policy", "<span class='mono muted'>" + e(head(c.policyDigest(), 16)) + "</span>"},
                new String[]{"isolation", "candidates ran in this JVM, each in its own class loader; no OS sandbox"},
                new String[]{"configuration", "<span class='mono muted'>" + e(head(c.configDigest(), 16)) + "</span>"});
        return section("Run", kv(rows) + settings, null);
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String verdicts(Checkpoint c, List<Observation> finals) {
        if (finals.isEmpty()) {
            return section("Verdicts", absent("No sample was committed: this run produced no verdicts."), null);
        }
        Map<Outcome, Long> outcomes = new EnumMap<>(Outcome.class);
        Map<Integer, Long> codes = new TreeMap<>();
        long nanos = 0;
        for (Observation o : finals) {
            outcomes.merge(o.outcome(), 1L, Long::sum);
            o.verdictCode().ifPresent(v -> codes.merge(v, 1L, Long::sum));
        }
        for (Observation o : c.observations()) {
            nanos += o.elapsedNanos();
        }
        long samples = finals.size();
        long passed = outcomes.getOrDefault(Outcome.PASS, 0L);
        StringBuilder chips = new StringBuilder("<div class='chips'>");
        StringBuilder bar = new StringBuilder("<div class='bar'>");
        outcomes.forEach((k, v) -> {
            String cls = OUTCOME_CLASS.get(k);
            chips.append("<span class='chip ").append(cls).append("'>").append(e(k)).append(" <b>").append(v).append("</b></span>");
            bar.append("<span class='seg ").append(cls).append("' style='width:").append(fixed(100.0 * v / samples, 4)).append("%' title='")
                    .append(e(k)).append(": ").append(v).append("'></span>");
        });
        chips.append("</div>");
        bar.append("</div>");
        StringBuilder verdictCodes = new StringBuilder();
        codes.forEach((code, n) -> verdictCodes.append(verdictCodes.isEmpty() ? "" : ", ").append(code).append(" × ").append(n));
        List<String[]> rows = List.of(
                new String[]{"samples", e(samples)},
                new String[]{"attempts", e(c.observations().size()) + (c.observations().size() > samples ? " <span class='muted'>("
                        + (c.observations().size() - samples) + " retried)</span>" : "")},
                new String[]{"pass rate", fixed(100.0 * passed / samples, 1) + "%  (" + passed + "/" + samples + ")"},
                new String[]{"verdict codes", verdictCodes.isEmpty() ? null : e(verdictCodes)},
                new String[]{"attempt time", fixed(nanos / 1e9, 3) + " s, summed over attempts"});
        return section("Verdicts", chips.toString() + bar + kv(rows), "Over the final attempt of every sample.");
    }

    private static String findings(Triage.Report t) {
        if (t.failing() == 0) {
            return section("Findings", absent(t.records() == 0 ? "No sample ended with a domain verdict, so there is nothing to group."
                    : "No sample failed: nothing to triage."), null);
        }
        StringBuilder rows = new StringBuilder();
        int rank = 0;
        for (Triage.Finding f : t.findings()) {
            Triage.Record w = f.witness();
            rows.append("<tr><td class='num'>").append(++rank).append("</td><td class='mono'>").append(e(f.label())).append("</td><td class='num'>")
                    .append(f.count()).append("</td><td class='num'>").append(fixed(100.0 * f.count() / t.failing(), 1)).append("%</td><td class='mono'>")
                    .append(e(w == null ? "" : w.sourceRef().isEmpty() ? w.candidateId() : w.sourceRef())).append("</td><td class='num muted'>")
                    .append(f.witnessBytes() == null ? "?" : f.witnessBytes() + " B").append("</td></tr>");
        }
        StringBuilder lift = new StringBuilder();
        for (Triage.Enrichment r : t.enrichment().subList(0, Math.min(12, t.enrichment().size()))) {
            lift.append("<tr><td class='mono'>").append(e(r.key())).append("</td><td class='mono'>").append(e(r.value())).append("</td><td class='num'>")
                    .append(r.failing()).append('/').append(r.total()).append("</td><td class='num'>").append(fixed(100.0 * r.rate(), 1))
                    .append("%</td><td class='num'>").append(fixed(r.lift(), 2)).append("</td><td class='num muted'>").append(fixed(100.0 * r.coverage(), 1))
                    .append("%</td></tr>");
        }
        StringBuilder notes = new StringBuilder();
        t.notes().forEach(n -> notes.append("<p class='note'>").append(e(n)).append("</p>"));
        String body = notes + "<table class='grid'><thead><tr><th>#</th><th>finding</th><th>samples</th><th>share</th><th>minimal witness</th>"
                + "<th>bytes</th></tr></thead><tbody>" + rows + "</tbody></table>"
                + (lift.isEmpty() ? "" : "<table class='grid'><thead><tr><th>key</th><th>value</th><th>failing</th><th>rate</th><th>lift</th>"
                + "<th>covers</th></tr></thead><tbody>" + lift + "</tbody></table>");
        return section("Findings", body, e(t.failing() + " of " + t.records() + " samples failed in " + t.findings().size()
                + " distinct way(s); lift is how much a value changes the odds of failing, 1.0 is noise."));
    }

    private static String stages() {
        return section("Stages", absent("A checkpoint keeps observations, not stage timings: the run report written by run --out "
                + "records them."), null);
    }

    private static String front() {
        return section("Analyzer front", absent("A checkpoint keeps no analysis: export the corpus and run analyze --results to see the "
                + "non-dominated candidates."), null);
    }

    private static String attachments(Checkpoint c) {
        if (c.attachments().isEmpty()) {
            return section("Attachments", absent("No attachment was kept (cf.results.attach)."), null);
        }
        Map<org.ghyura.cf.model.SampleKey, String> displays = new HashMap<>();
        c.observations().forEach(o -> displays.put(o.key(), o.display()));
        StringBuilder rows = new StringBuilder();
        for (ResultRepository.Attachment a : c.attachments()) {
            rows.append("<tr><td class='mono'>").append(e(displays.getOrDefault(a.key(), a.key().candidateId().hex()))).append("</td><td class='mono'>")
                    .append(e(a.name())).append("</td><td class='num muted'>").append(a.bytes()).append("</td><td class='mono muted'>")
                    .append(head(sha256(a.text()), 12)).append("</td></tr>");
        }
        return section("Attachments", "<table class='grid'><thead><tr><th>sample</th><th>name</th><th>bytes</th><th>sha256</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>", null);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }

    /** The legacy page's style, unchanged: light and dark schemes, a narrow-screen layout, no external resource. */
    static final String CSS = """
            :root{--bg:#fbfbfd;--fg:#1c1c22;--mut:#6b7280;--line:#e3e3ea;--card:#fff;
            --ok:#1a7f4b;--fail:#b45309;--bad:#b42318;--warn:#8a6d1f;--accent:#3b4cca}
            @media (prefers-color-scheme:dark){:root{--bg:#0f1115;--fg:#e6e6ec;--mut:#9aa0ab;
            --line:#272a33;--card:#161922;--ok:#4ade80;--fail:#fbbf24;--bad:#f87171;--warn:#facc15;--accent:#8b9dff}}
            *{box-sizing:border-box}
            body{margin:0;padding:2rem 1.25rem 4rem;background:var(--bg);color:var(--fg);
            font:15px/1.55 ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
            main{max-width:1080px;margin:0 auto}
            h1{font-size:1.5rem;margin:0 0 .25rem}
            h2{font-size:1.05rem;margin:0 0 .75rem;letter-spacing:.01em}
            .sub{color:var(--mut);margin:0 0 2rem;font-size:.875rem}
            section{background:var(--card);border:1px solid var(--line);border-radius:10px;
            padding:1.1rem 1.25rem;margin:0 0 1.1rem}
            .note{color:var(--mut);font-size:.83rem;margin:-.4rem 0 .8rem}
            table{border-collapse:collapse;width:100%;font-size:.875rem}
            .kv th{text-align:left;font-weight:500;color:var(--mut);padding:.28rem 1rem .28rem 0;
            white-space:nowrap;vertical-align:top;width:1%}
            .kv td{padding:.28rem 0}
            .grid{margin-top:.5rem}
            .grid th{text-align:left;font-weight:600;color:var(--mut);font-size:.78rem;
            text-transform:uppercase;letter-spacing:.04em;border-bottom:1px solid var(--line);padding:.4rem .6rem .4rem 0}
            .grid td{padding:.4rem .6rem .4rem 0;border-bottom:1px solid var(--line);vertical-align:top}
            .mono{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.85em}
            .num{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
            .muted{color:var(--mut)}
            .path{font-family:ui-monospace,monospace;font-size:.76em;color:var(--mut);word-break:break-all}
            .reason{color:var(--mut);font-size:.83em;max-width:32rem}
            .pill{display:inline-block;padding:.08rem .5rem;border-radius:99px;font-size:.76rem;
            font-weight:600;border:1px solid currentColor}
            .chips{display:flex;flex-wrap:wrap;gap:.4rem;margin-bottom:.7rem}
            .chip{padding:.18rem .6rem;border-radius:6px;font-size:.8rem;border:1px solid var(--line)}
            .chip b{font-variant-numeric:tabular-nums}
            .ok{color:var(--ok)} .fail{color:var(--fail)} .bad{color:var(--bad)} .warn{color:var(--warn)}
            .absent{color:var(--mut);font-style:italic;margin:.2rem 0}
            .bar{display:flex;height:9px;border-radius:99px;overflow:hidden;background:var(--line);margin:.2rem 0 .9rem}
            .seg{display:block;height:100%}
            .seg.ok{background:var(--ok)} .seg.fail{background:var(--fail)}
            .seg.bad{background:var(--bad)} .seg.warn{background:var(--warn)} .seg.muted{background:var(--mut)}
            .track{width:34%;min-width:6rem}
            .track span{display:block;height:7px;border-radius:99px;background:var(--accent);opacity:.65}
            details summary{cursor:pointer;color:var(--mut);font-size:.83rem;margin:.6rem 0 .3rem}
            footer{color:var(--mut);font-size:.78rem;margin-top:1.5rem;text-align:center}
            @media(max-width:720px){body{padding:1rem .75rem 3rem}.track{display:none}}
            """;
}
