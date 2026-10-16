// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/** Extract/check QA's raw HTML literal as text; never runs or imports Python. Run from the repository root. */
public final class SyncFace2Template {
    private SyncFace2Template() { }
    public static void main(String[] args) throws Exception {
        boolean check = Arrays.asList(args).contains("--check");
        Path source = Path.of("../Combinatorics-Framework-QA-edition/generator_trunk/constraints/editor.py");
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--source")) source = Path.of(args[i + 1]);
        String python = Files.readString(source);
        String marker = "_HTML = r\"\"\"";
        int start = python.indexOf(marker);
        if (start < 0) throw new IllegalArgumentException("Expected the QA editor's raw _HTML literal.");
        start += marker.length();
        int end = python.indexOf("\"\"\"", start);
        if (end < 0) throw new IllegalArgumentException("The QA _HTML literal has no closing delimiter.");
        byte[] data = python.substring(start, end).getBytes(StandardCharsets.UTF_8);
        Path target = Path.of("fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2/editor.html");
        if (check) {
            if (!Arrays.equals(data, Files.readAllBytes(target))) throw new IllegalStateException("Face 2 template differs from QA source.");
        } else { Files.createDirectories(target.getParent()); Files.write(target, data); }
        System.out.println("Face 2 template identical: " + data.length + " bytes; SHA-256 "
                + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
    }
}
