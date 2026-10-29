import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Run with JDK 27: java scripts/VerifyDatabaseTests.java [--self-test]. */
class VerifyDatabaseTests {
    private static final Pattern DATABASE_TEST = Pattern.compile(
            "@RequiresDatabase\\b|extends\\s+BaseIntegrationTest\\b");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--self-test")) {
            selfTest();
            return;
        }
        if (args.length != 0 && args.length != 2) {
            throw new IllegalArgumentException("Usage: VerifyDatabaseTests.java [source-root report-root | --self-test]");
        }
        Path sources = Path.of(args.length == 0 ? "start/src/test/java" : args[0]);
        Path reports = Path.of(args.length == 0 ? "start/target/surefire-reports" : args[1]);
        Result result = verify(sources, reports);
        if (!result.problems().isEmpty()) {
            result.problems().forEach(problem -> System.err.println("Database test gate: " + problem));
            System.exit(1);
        }
        System.out.printf("Database test gate passed: %d test classes, %d executed cases, no skips or failures.%n",
                result.classes(), result.executed());
    }

    private static Result verify(Path sources, Path reports) throws Exception {
        Map<String, Evidence> required = new LinkedHashMap<>();
        try (var paths = Files.walk(sources)) {
            for (Path source : paths.filter(path -> path.toString().endsWith("Test.java")).sorted().toList()) {
                String content = Files.readString(source);
                if (!DATABASE_TEST.matcher(content).find()) continue;
                var packageName = PACKAGE.matcher(content);
                if (!packageName.find()) throw new IOException("Missing package in " + source);
                String simpleName = source.getFileName().toString().replaceFirst("\\.java$", "");
                required.put(packageName.group(1) + "." + simpleName, new Evidence());
            }
        }
        List<String> problems = new ArrayList<>();
        if (required.isEmpty()) problems.add("No database test sources were found.");
        if (!Files.isDirectory(reports)) {
            problems.add("Surefire reports are missing: " + reports);
            return new Result(required.size(), 0, problems);
        }

        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; }
            @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; }
        });
        try (var paths = Files.list(reports)) {
            for (Path report : paths.filter(path -> path.getFileName().toString().matches("TEST-.*\\.xml"))
                    .sorted().toList()) {
                var document = builder.parse(report.toFile());
                var suites = document.getElementsByTagName("testsuite");
                for (int index = 0; index < suites.getLength(); index++) {
                    Element suite = (Element) suites.item(index);
                    Evidence evidence = suiteFamily(required, suite);
                    if (evidence != null) {
                        for (String attribute : List.of("skipped", "failures", "errors")) {
                            String value = suite.getAttribute(attribute);
                            if (!value.isEmpty() && (!value.matches("[0-9]+") || Long.parseLong(value) != 0)) {
                                evidence.incomplete = true;
                            }
                        }
                    }
                }
                var cases = document.getElementsByTagName("testcase");
                for (int index = 0; index < cases.getLength(); index++) {
                    Element test = (Element) cases.item(index);
                    String className = test.getAttribute("classname");
                    Evidence evidence = family(required, className);
                    // Surefire may put @DisplayName in classname while the suite retains its FQCN.
                    if (evidence == null) evidence = suiteFamily(required, test.getParentNode());
                    if (evidence == null) continue;
                    if (test.getElementsByTagName("skipped").getLength() > 0
                            || test.getElementsByTagName("failure").getLength() > 0
                            || test.getElementsByTagName("error").getLength() > 0) {
                        evidence.incomplete = true;
                    } else {
                        evidence.executed++;
                    }
                }
            }
        }
        required.forEach((name, evidence) -> {
            if (evidence.executed == 0) problems.add("No executed test cases for " + name);
            if (evidence.incomplete) problems.add("Skipped or failed test cases for " + name);
        });
        return new Result(required.size(), required.values().stream().mapToInt(value -> value.executed).sum(), problems);
    }

    private static Evidence family(Map<String, Evidence> required, String className) {
        // JUnit parameterized/dynamic cases retain the class; nested reports may use $ or a dot.
        for (var entry : required.entrySet()) {
            String root = entry.getKey();
            if (className.equals(root) || className.startsWith(root + "$") || className.startsWith(root + ".")) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Evidence suiteFamily(Map<String, Evidence> required, Node node) {
        for (Node parent = node; parent instanceof Element; parent = parent.getParentNode()) {
            if (parent.getNodeName().equals("testsuite")) {
                Evidence evidence = family(required, ((Element) parent).getAttribute("name"));
                if (evidence != null) return evidence;
            }
        }
        return null;
    }

    private static void selfTest() throws Exception {
        Path fixture = Files.createTempDirectory("atom-database-gate-");
        try {
            Path sources = Files.createDirectory(fixture.resolve("sources"));
            Path reports = Files.createDirectory(fixture.resolve("reports"));
            Files.writeString(sources.resolve("SampleTest.java"),
                    "package fixture;\n@RequiresDatabase class SampleTest {}\n");
            Path report = reports.resolve("TEST-fixture.SampleTest.xml");
            String one = "<testcase classname=\"fixture.SampleTest\" name=\"runs\"/>";
            check("missing report", !verify(sources, reports).problems().isEmpty());
            check("missing report directory", !verify(sources, fixture.resolve("absent")).problems().isEmpty());
            Files.writeString(report, suite(one));
            check("successful case", verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite(""));
            check("zero executed", !verify(sources, reports).problems().isEmpty());
            String skipped = "<testcase classname=\"fixture.SampleTest\" name=\"skipped\"><skipped/></testcase>";
            Files.writeString(report, suite(skipped));
            check("all skipped", !verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite(one + skipped));
            check("partially skipped", !verify(sources, reports).problems().isEmpty());
            for (String status : List.of("failure", "error")) {
                Files.writeString(report, suite(one + "<testcase classname=\"fixture.SampleTest\" name=\"bad\"><"
                        + status + "/></testcase>"));
                check(status, !verify(sources, reports).problems().isEmpty());
            }
            Files.writeString(report, "<testsuite name=\"fixture.SampleTest\" skipped=\"1\">" + one + "</testsuite>");
            check("suite skip counter", !verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite("<testcase classname=\"fixture.SampleTest\" name=\"parameter[1]\"/>"
                    + "<testcase classname=\"fixture.SampleTest\" name=\"factory()[1]\"/>"));
            check("parameterized and dynamic", verify(sources, reports).executed() == 2
                    && verify(sources, reports).problems().isEmpty());
            Files.writeString(report, "<testsuite name=\"fixture.SampleTest$Nested\">"
                    + "<testcase classname=\"fixture.SampleTest$Nested\" name=\"nested\"/>"
                    + "<testcase name=\"without-classname\"/></testsuite>");
            check("nested and suite fallback", verify(sources, reports).executed() == 2
                    && verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite("<testcase classname=\"Human-readable test class\" name=\"runs\"/>"));
            check("display name falls back to known suite", verify(sources, reports).executed() == 1
                    && verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite("<testsuite name=\"Nested display name\">"
                    + "<testcase classname=\"Human-readable nested class\" name=\"runs\"/></testsuite>"));
            check("nested display names fall back to known ancestor", verify(sources, reports).executed() == 1
                    && verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite(one + "<testsuite name=\"Nested display name\" skipped=\"1\"/>"));
            check("nested display-name skip cannot hide behind root pass", !verify(sources, reports).problems().isEmpty());
            Files.writeString(report, "<testsuite name=\"Unknown display name\">"
                    + "<testcase classname=\"Human-readable test class\" name=\"runs\"/></testsuite>");
            check("display names without known ancestor are not evidence", !verify(sources, reports).problems().isEmpty());
            Files.writeString(report, suite(one));
            Files.writeString(reports.resolve("TEST-fixture.SampleTest$Nested.xml"),
                    "<testsuite name=\"fixture.SampleTest$Nested\" skipped=\"1\"/>");
            check("nested skip cannot hide behind root pass", !verify(sources, reports).problems().isEmpty());
            Files.delete(reports.resolve("TEST-fixture.SampleTest$Nested.xml"));
            Files.writeString(sources.resolve("SecondTest.java"),
                    "package fixture;\nclass SecondTest extends BaseIntegrationTest {}\n");
            check("new inherited database class requires report", !verify(sources, reports).problems().isEmpty());
            Files.delete(sources.resolve("SecondTest.java"));
            Files.writeString(report, "<!DOCTYPE testsuite [<!ENTITY external SYSTEM \"file:///unreadable\">]>"
                    + "<testsuite name=\"fixture.SampleTest\">&external;</testsuite>");
            boolean rejected = false;
            try { verify(sources, reports); } catch (SAXParseException expected) { rejected = true; }
            check("external entities rejected", rejected);
            Files.writeString(report, "<testsuite");
            rejected = false;
            try { verify(sources, reports); } catch (SAXParseException expected) { rejected = true; }
            check("malformed report rejected", rejected);
            Files.delete(sources.resolve("SampleTest.java"));
            Files.delete(report);
            check("empty test discovery rejected", !verify(sources, reports).problems().isEmpty());
            System.out.println("Database report gate self-tests passed (20 cases).");
        } finally {
            try (var paths = Files.walk(fixture)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static String suite(String cases) {
        return "<testsuite name=\"fixture.SampleTest\">" + cases + "</testsuite>";
    }

    private static void check(String name, boolean condition) {
        if (!condition) throw new AssertionError("Database report gate self-test failed: " + name);
    }

    private static final class Evidence {
        int executed;
        boolean incomplete;
    }

    private record Result(int classes, int executed, List<String> problems) {}
}
