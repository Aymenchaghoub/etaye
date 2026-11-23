package dev.aymen.etaye.isolation;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Records the measured outcome of every adversarial isolation case and writes one report when the test run ends.
 *
 * <p>The mandatory cases, their expected outcome and the pending cases of resources without a real route live in
 * {@code isolation/matrix-cases.json}. The report holds only safe labels, outcomes and test identities: never a
 * request, a response body, a header value or an identifier. {@code tools/assert_isolation_matrix.py} fails the CI
 * job for a missing, failing or skipped mandatory case.</p>
 */
public final class IsolationMatrix {

    private static final String REPORT_DIRECTORY_PROPERTY = "etaye.isolation.report.dir";
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final Manifest MANIFEST = loadManifest();
    private static final Map<String, Entry> OBSERVED = new LinkedHashMap<>();
    private static final ThreadLocal<String> CURRENT_TEST = new ThreadLocal<>();
    private static volatile String fixtureVersion = "unknown";

    private IsolationMatrix() {}

    public record Case(String id, String group, String label, String expected) {}

    public record Pending(String id, String resource, List<String> operations, List<String> owners, String trigger) {}

    public record Manifest(int schemaVersion, String fixtureVersion, List<Case> cases, Map<String, List<String>> parity, List<Pending> pending) {}

    public record Entry(String id, String group, String label, String expected, String observed, String result,
                        String test, String fingerprint) {}

    public record Report(int schemaVersion, String story, String commit, String fixtureVersion,
                         Map<String, List<String>> parity, List<Entry> cases, List<PendingRow> pending) {}

    public record PendingRow(String id, String resource, List<String> operations, List<String> owners, String status,
                             String trigger) {}

    /** Registers the report writer for the whole test run and names the running test for each observation. */
    public static final class Extension implements BeforeEachCallback {
        @Override
        public void beforeEach(ExtensionContext context) {
            context.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).computeIfAbsent(ReportWriter.class);
            CURRENT_TEST.set(context.getRequiredTestClass().getName() + "#" + context.getRequiredTestMethod().getName());
        }
    }

    static final class ReportWriter implements AutoCloseable {
        @Override
        public void close() throws IOException {
            writeReport();
        }
    }

    public static void fixtureVersion(String version) {
        fixtureVersion = version;
    }

    private static final java.util.regex.Pattern STABLE_CODE = java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]{1,40}");
    private static final java.util.regex.Pattern UUID_TEXT = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * The compact outcome used by the report: the status and, when the problem carries one, its stable code. A code
     * that is not a stable identifier is replaced, so a regression can never copy request data into the report.
     */
    public static String outcome(int status, Object code) {
        if (code == null) {
            return Integer.toString(status);
        }
        return status + " " + (STABLE_CODE.matcher(code.toString()).matches() ? code : "NON_STANDARD_CODE");
    }

    /**
     * Records the observed outcome of a mandatory case and fails the calling test when it differs from the
     * expectation in the manifest.
     */
    public static void observe(String caseId, String observed, String fingerprint) {
        Case definition = MANIFEST.cases().stream().filter(candidate -> candidate.id().equals(caseId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown isolation case " + caseId));
        String test = CURRENT_TEST.get();
        if (test == null) {
            throw new IllegalStateException("Observe isolation cases from a test that registers the extension");
        }
        observed = UUID_TEXT.matcher(observed).replaceAll("<id>");
        boolean passed = definition.expected().equals(observed);
        Entry entry = new Entry(definition.id(), definition.group(), definition.label(), definition.expected(),
                observed, passed ? "PASS" : "FAIL", test, fingerprint);
        synchronized (OBSERVED) {
            if (OBSERVED.putIfAbsent(caseId, entry) != null) {
                throw new IllegalStateException("The isolation case " + caseId + " was observed twice");
            }
        }
        if (!passed) {
            throw new AssertionError("Isolation case " + caseId + " expected [" + definition.expected()
                    + "] but observed [" + observed + "]");
        }
    }

    public static void observe(String caseId, String observed) {
        observe(caseId, observed, null);
    }

    /**
     * Fingerprints what a refusal discloses: status, media type, caching directive, the presence of any header that
     * could carry a validator, a location, a cookie or a retry hint, and the problem body without its per-request
     * correlation identifier.
     */
    public static String fingerprint(int status, String contentType, String cacheControl, java.util.Set<String> headers,
                                     Map<String, Object> problem) {
        Map<String, Object> body = new TreeMap<>(problem == null ? Map.of() : problem);
        body.remove("correlationId");
        java.util.Set<String> disclosing = new java.util.TreeSet<>();
        for (String name : headers) {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            if (java.util.List.of("etag", "location", "content-location", "set-cookie", "retry-after", "www-authenticate",
                    "last-modified", "content-disposition").contains(lower)) {
                disclosing.add(lower);
            }
        }
        String material = status + "\n" + contentType + "\n" + cacheControl + "\n" + disclosing + "\n"
                + JSON.writeValueAsString(body);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static Manifest loadManifest() {
        try (InputStream stream = IsolationMatrix.class.getResourceAsStream("/isolation/matrix-cases.json")) {
            if (stream == null) {
                throw new IllegalStateException("The isolation case manifest is missing from the test classpath");
            }
            return JSON.readValue(stream, Manifest.class);
        } catch (IOException ex) {
            throw new IllegalStateException("The isolation case manifest cannot be read", ex);
        }
    }

    private static void writeReport() throws IOException {
        List<Entry> entries;
        synchronized (OBSERVED) {
            entries = new ArrayList<>(OBSERVED.values());
        }
        if (entries.isEmpty()) {
            return;
        }
        // Surefire may reorder test methods; the report keeps the order of the manifest.
        List<String> order = MANIFEST.cases().stream().map(Case::id).toList();
        entries.sort(java.util.Comparator.comparingInt(entry -> order.indexOf(entry.id())));
        List<PendingRow> pending = MANIFEST.pending().stream()
                .map(row -> new PendingRow(row.id(), row.resource(), row.operations(), row.owners(), "PENDING",
                        row.trigger()))
                .toList();
        Report report = new Report(1, "1.4", commit(), fixtureVersion, MANIFEST.parity(), entries, pending);
        Path directory = Path.of(System.getProperty(REPORT_DIRECTORY_PROPERTY, "target/isolation-matrix"));
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("isolation-matrix.json"), JSON.writeValueAsString(report) + "\n");
        Files.writeString(directory.resolve("isolation-matrix.md"), markdown(report));
    }

    private static String markdown(Report report) {
        StringBuilder text = new StringBuilder();
        text.append("# Adversarial isolation matrix\n\n");
        text.append("Story ").append(report.story()).append(" · commit `").append(report.commit())
                .append("` · fixture `").append(report.fixtureVersion()).append("`\n\n");
        text.append("| Case | Group | Scenario | Expected | Observed | Result | Test |\n|---|---|---|---|---|---|---|\n");
        for (Entry entry : report.cases()) {
            text.append("| ").append(entry.id()).append(" | ").append(entry.group()).append(" | ")
                    .append(entry.label()).append(" | ").append(entry.expected()).append(" | ")
                    .append(entry.observed()).append(" | ").append(entry.result()).append(" | `")
                    .append(entry.test()).append("` |\n");
        }
        text.append("\n## Not yet covered (D-55)\n\n");
        text.append("These rows prove nothing about resource isolation until the owning route ships and is tested.\n\n");
        text.append("| Row | Resource | Operations | Owning stories | Status | Resumption trigger |\n|---|---|---|---|---|---|\n");
        for (PendingRow row : report.pending()) {
            text.append("| ").append(row.id()).append(" | ").append(row.resource()).append(" | ")
                    .append(String.join(", ", row.operations())).append(" | ").append(String.join(", ", row.owners()))
                    .append(" | ").append(row.status()).append(" | ").append(row.trigger()).append(" |\n");
        }
        return text.toString();
    }

    private static String commit() {
        String fromCi = System.getenv("GITHUB_SHA");
        if (fromCi != null && !fromCi.isBlank()) {
            return fromCi;
        }
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "unknown";
            }
            String head = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return process.exitValue() == 0 && head.matches("[0-9a-f]{40}") ? head : "unknown";
        } catch (IOException ex) {
            return "unknown";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "unknown";
        }
    }
}
