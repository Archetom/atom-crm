#!/usr/bin/env python3
"""Verify CRM check orchestration without Maven downloads, Docker or application builds."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPTS = Path(__file__).resolve().parent
GIT = shutil.which("git")
BASH = shutil.which("bash")
DOMAIN_TEST = "domain/src/test/java/example/domain/LabelTest.java"


class AgentCheckTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="crm-agent-check-test-")
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.project = self.base / "project"
        self.project.mkdir()
        self.bin = self.base / "bin"
        self.bin.mkdir()
        self.reports = self.base / 'reports "quoted" 名字'
        self.reports.mkdir()
        self.trace = self.base / "trace"
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        TMPDIR=str(self.reports), TRACE=str(self.trace), EXPECTED_VERSION="1.0.0",
                        ATOM_TEST_FORKS="2", GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull,
                        GIT_AUTHOR_NAME="Process Test", GIT_AUTHOR_EMAIL="process@example.invalid",
                        GIT_COMMITTER_NAME="Process Test", GIT_COMMITTER_EMAIL="process@example.invalid")
        for name in ("MAVEN_ARGS", "MAVEN_OPTS", "FAKE_BUILD_EXIT", "FAKE_GATE_EXIT", "FAKE_REPORTS",
                     "FAKE_DOCKER_EXIT", "FAKE_VERSION"):
            self.env.pop(name, None)
        (self.project / "scripts").mkdir()
        for name in ("agent-check.sh", "affected-scope.py", "release-verify.sh"):
            shutil.copyfile(SCRIPTS / name, self.project / "scripts" / name)
        shutil.copyfile(SCRIPTS.parent / "Makefile", self.project / "Makefile")
        for path, content in {
            "pom.xml": "<project><version>1.0.0</version></project>\n",
            "domain/pom.xml": "<project/>\n", ".gitignore": "target/\n",
            "README.md": "Release 1.0.0\n", "README.en.md": "Release 1.0.0\n",
            "llms.txt": "Release 1.0.0\n", "conf/application.yml": "version: 1.0.0\n",
            "start/src/test/java/io/github/archetom/crm/OpenApiIntegrationTest.java": "// Version 1.0.0\n",
            DOMAIN_TEST: "class LabelTest {}\n",
        }.items():
            self.write(path, content)
        self.write("mvnw", '''#!/bin/sh
printf 'maven|%s|%s\\n' "$PWD" "$*" >> "$TRACE"
case "$*" in
    *help:evaluate*) printf '%s\\n' "${FAKE_VERSION:-1.0.0}"; exit 0 ;;
esac
case " $* " in
    *' clean '*) rm -rf domain/target start/target ;;
esac
if [ "${FAKE_REPORTS:-fresh}" = fresh ]; then
    mkdir -p domain/target/surefire-reports start/target/surefire-reports
    printf '<testsuite tests="1" failures="0"/>\\n' > domain/target/surefire-reports/TEST-Unit.xml
    printf '<testsuite tests="1" failures="0"/>\\n' > start/target/surefire-reports/TEST-Database.xml
fi
printf 'BUILD SUCCESS (fixture text, not evidence)\\n'
exit "${FAKE_BUILD_EXIT:-0}"
''')
        self.executable("docker", '#!/bin/sh\nprintf "docker|%s\\n" "$*" >> "$TRACE"\nexit "${FAKE_DOCKER_EXIT:-0}"\n')
        self.executable("java", '#!/bin/sh\nprintf "gate|%s\\n" "$*" >> "$TRACE"\nexit "${FAKE_GATE_EXIT:-0}"\n')
        self.git("init", "-q")
        self.git("add", "--all")
        self.git("commit", "-q", "-m", "fixture")

    def write(self, path, text):
        file = self.project / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding="utf-8")

    def executable(self, name, content):
        file = self.bin / name
        file.write_text(content)
        file.chmod(0o755)

    def git(self, *args):
        return subprocess.check_output([GIT, "-c", "core.hooksPath=" + os.devnull, *args],
                                       cwd=self.project, env=self.env, stderr=subprocess.DEVNULL)

    def run_check(self, scope="full", make=False):
        command = (["make", "--no-print-directory", "agent-check", "SCOPE=" + scope] if make else
                   [BASH, "scripts/agent-check.sh", scope])
        process = subprocess.run(command, cwd=self.project, env=self.env, text=True,
                                 stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=20)
        result = json.loads(process.stdout)
        if not make:
            self.assertEqual(result["exitCode"], process.returncode, process.stderr)
        self.assertEqual(result["context"], "application")
        self.assertEqual(result["scope"], scope or "full")
        self.assertGreaterEqual(result["durationMs"], 0)
        for path in result["reportPaths"]:
            self.assertTrue(Path(path).is_dir(), path)
            self.assertTrue(list(Path(path).glob("TEST-*.xml")), path)
        return process, result

    def trace_lines(self):
        return self.trace.read_text().splitlines() if self.trace.exists() else []

    def builds(self):
        return [line for line in self.trace_lines() if line.startswith("maven|") and "help:evaluate" not in line]

    def test_fast_explicitly_skips_database_even_when_ci_true(self):
        self.env["CI"] = "true"
        process, result = self.run_check("fast", make=True)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["status"], "passed")
        self.assertEqual(len(self.builds()), 1)
        self.assertIn("-Datom.test.skip-database-tests=true", self.builds()[0])
        self.assertFalse(any(line.startswith(("docker|", "gate|")) for line in self.trace_lines()))

    def test_full_runs_one_clean_install_with_database_gate_and_two_forks(self):
        self.env["CI"] = "false"
        process, result = self.run_check()
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["status"], "passed")
        self.assertEqual(len(self.builds()), 1)
        build = self.builds()[0]
        self.assertIn("clean install", build)
        self.assertIn("-Datom.test.forks=2", build)
        self.assertIn("-Datom.test.skip-database-tests=false", build)
        self.assertNotIn("-Datom.test.skip-database-tests=true", build)
        self.assertIn("gate|scripts/VerifyDatabaseTests.java", self.trace_lines())
        self.assertTrue(result["reportPaths"])

    def test_default_scope_is_full(self):
        process, result = self.run_check("")
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["selectedScope"], "full")
        self.assertIn("clean install", self.builds()[0])

    def test_database_gate_failure_cannot_pass(self):
        self.env["FAKE_GATE_EXIT"] = "7"
        process, result = self.run_check()
        self.assertEqual(process.returncode, 7)
        self.assertEqual(result["status"], "failed")
        self.assertTrue(result["reportPaths"])

    def test_maven_failure_short_circuits_database_gate(self):
        self.env["FAKE_BUILD_EXIT"] = "17"
        process, result = self.run_check()
        self.assertEqual(process.returncode, 17)
        self.assertEqual(result["status"], "failed")
        self.assertNotIn("gate|scripts/VerifyDatabaseTests.java", self.trace_lines())

    def test_missing_docker_blocks_before_build(self):
        self.env["FAKE_DOCKER_EXIT"] = "1"
        process, result = self.run_check()
        self.assertEqual(process.returncode, 2)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(self.builds(), [])

    def test_stale_fast_reports_do_not_pass(self):
        self.write("domain/target/surefire-reports/TEST-Old.xml", "<testsuite/>\n")
        os.utime(self.project / "domain/target/surefire-reports/TEST-Old.xml", (1, 1))
        self.env["FAKE_REPORTS"] = "none"
        process, result = self.run_check("fast")
        self.assertEqual(process.returncode, 3)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(result["reportPaths"], [])

    def test_full_clean_and_report_check_reject_missing_fresh_results(self):
        self.write("start/target/surefire-reports/TEST-Old.xml", "<testsuite/>\n")
        self.env["FAKE_REPORTS"] = "none"
        process, result = self.run_check()
        self.assertEqual(process.returncode, 3)
        self.assertEqual(result["status"], "failed")
        self.assertFalse((self.project / "start/target/surefire-reports/TEST-Old.xml").exists())

    def test_affected_single_module_tests_use_reactor_and_skip_database(self):
        self.write(DOMAIN_TEST, "class LabelTest { int value; }\n")
        process, result = self.run_check("affected")
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["selectedScope"], "module-tests")
        self.assertEqual(result["selectedModules"], ["domain"])
        self.assertIn("-pl domain -am", self.builds()[0])
        self.assertIn("-Datom.test.skip-database-tests=true", self.builds()[0])

    def test_connector_change_retains_full_validation(self):
        self.write("domain/src/test/java/example/domain/ConnectorRegistrationTest.java", "class ConnectorRegistrationTest {}\n")
        process, result = self.run_check("affected")
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["selectedScope"], "full")
        self.assertIn("clean install", self.builds()[0])

    def test_unknown_change_retains_full_validation(self):
        self.write("unknown.file", "change\n")
        process, result = self.run_check("affected")
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["selectedScope"], "full")

    def test_docs_do_not_build(self):
        process, result = self.run_check("docs")
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertEqual(result["status"], "passed")
        self.assertEqual(self.builds(), [])

    def test_generate_is_not_an_application_operation(self):
        process, result = self.run_check("generate")
        self.assertEqual(process.returncode, 2)
        self.assertEqual(result["status"], "failed")
        self.assertEqual(self.builds(), [])


if __name__ == "__main__":
    unittest.main()
