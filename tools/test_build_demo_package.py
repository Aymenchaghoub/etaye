import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
CENTRAL = "https://repo.maven.apache.org/maven2"
TRANSFER = (
    f"[ERROR] Could not transfer artifact group:artifact:pom:1 from/to central ({CENTRAL}): "
    "status code: 429, reason phrase: Too Many Requests (429)"
)


class PackageRetryTest(unittest.TestCase):
    def run_case(self, failure, *, phase="central", mirror_fails=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "tools").mkdir()
            (root / "infra/demo").mkdir(parents=True)
            shutil.copyfile(ROOT / "tools/build_demo_package.sh", root / "tools/build_demo_package.sh")
            start = root / "infra/demo/start.sh"
            start.write_text("""#!/usr/bin/env bash
if [[ "$*" == "--build-only api" ]]; then
    phase="$MAVEN_CENTRAL_SOURCE"
else
    phase="$*"
fi
printf '%s\\n' "$phase" >> "$CALLS"
if [[ "$phase" == "$FAIL_PHASE" ]]; then
    printf '%s\\n' "$FAIL_MESSAGE" >&2
    exit 6
fi
if [[ "$phase" == mirror && "$MIRROR_FAILS" == yes ]]; then
    exit 7
fi
""")
            start.chmod(0o755)
            calls = root / "calls"
            process = subprocess.run(["bash", root / "tools/build_demo_package.sh"], capture_output=True, text=True,
                                     env={**os.environ, "CALLS": str(calls), "FAIL_PHASE": phase,
                                          "FAIL_MESSAGE": failure, "MIRROR_FAILS": "yes" if mirror_fails else ""})
            return process, calls.read_text().splitlines()

    def test_only_the_api_build_retries_for_identified_central_http_failures(self):
        distribution = f"{CENTRAL}/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
        for failure in (TRANSFER, f"java.io.IOException: Server returned HTTP response code: 429 for URL: {distribution}",
                        f"ERROR 503: Service Unavailable.\nwget: Failed to fetch {distribution}",
                        f"curl: (22) The requested URL returned error: 502\ncurl: Failed to fetch {distribution}"):
            with self.subTest(failure=failure):
                run, calls = self.run_case(failure)
                self.assertEqual(run.returncode, 0, run.stderr)
                self.assertEqual(calls, ["--build-only web postgres migrate", "central", "mirror", "--no-build"])

    def test_compilation_checksums_and_non_transient_errors_fail_at_once(self):
        for failure in ("[ERROR] COMPILATION ERROR", TRANSFER + "\n[ERROR] COMPILATION ERROR",
                        TRANSFER + "\n[ERROR] Checksum validation failed", TRANSFER.replace("429", "404"),
                        TRANSFER.replace(CENTRAL, "https://another.example/maven2"),
                        "failed to solve: failed to fetch oauth token: 429 Too Many Requests"):
            with self.subTest(failure=failure):
                run, calls = self.run_case(failure)
                self.assertNotEqual(run.returncode, 0)
                self.assertEqual(calls, ["--build-only web postgres migrate", "central"])

    def test_failed_mirror_stops_and_a_failed_startup_does_not_rebuild(self):
        run, calls = self.run_case(TRANSFER, mirror_fails=True)
        self.assertEqual(run.returncode, 7)
        self.assertEqual(calls, ["--build-only web postgres migrate", "central", "mirror"])
        run, calls = self.run_case(TRANSFER, phase="--no-build")
        self.assertEqual(run.returncode, 6)
        self.assertEqual(calls, ["--build-only web postgres migrate", "central", "--no-build"])
        run, calls = self.run_case(TRANSFER, phase="--build-only web postgres migrate")
        self.assertEqual(run.returncode, 6)
        self.assertEqual(calls, ["--build-only web postgres migrate"])


if __name__ == "__main__":
    unittest.main()
