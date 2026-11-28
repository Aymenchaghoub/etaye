import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest


VERIFIER = Path(__file__).resolve().with_name("verify_demo_migration_receipt.sh")
PIN = "ca-etaye-demo--git-baseline"
MIGRATOR = "ghcr.io/aymenchaghoub/etaye-migrate@sha256:" + "a" * 64


class DemoMigrationReceiptTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.run_command("git", "init", "--quiet")
        self.run_command("git", "config", "user.name", "Fixture")
        self.run_command("git", "config", "user.email", "fixture@example.test")
        self.schema = self.root / "apps/api/src/main/resources/db/migration"
        self.schema.mkdir(parents=True)
        self.active = self.commit_migration("11")
        self.candidate = self.commit_migration("15")
        self.run_command("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", "key")
        self.public_key = " ".join((self.root / "key.pub").read_text().split()[:2])
        (self.root / "manifest.json").write_text(json.dumps({"images": {"migrate": {"reference": MIGRATOR}}}))
        self.proof = {
            "version": 1,
            "database": "etaye_demo_cloud",
            "activeSha": self.active,
            "candidateSha": self.candidate,
            "activeRevision": PIN,
            "migrator": MIGRATOR,
            "schemaBefore": ["11"],
            "schemaAfter": ["11", "15"],
            "backupRestored": True,
            "flywayValidated": True,
            "activeCompatible": True,
            "verifiedAt": int(time.time()),
        }

    def run_command(self, *args, **settings):
        return subprocess.run(args, cwd=self.root, capture_output=True, text=True, check=True, **settings).stdout

    def commit_migration(self, version):
        (self.schema / f"V{version}__fixture.sql").write_text(f"SELECT {version};\n")
        self.run_command("git", "add", ".")
        self.run_command("git", "commit", "--quiet", "--message", f"Fixture version {version}")
        return self.run_command("git", "rev-parse", "HEAD").strip()

    def sign(self, **changes):
        document = json.dumps({**self.proof, **changes}).encode()
        path = self.root / "receipt.json"
        path.write_bytes(document)
        signature = path.with_suffix(".json.sig")
        signature.unlink(missing_ok=True)
        self.run_command("ssh-keygen", "-Y", "sign", "-f", "key", "-n", "etaye-demo-migration", path.name)
        return ".".join(base64.b64encode(part).decode() for part in (document, signature.read_bytes()))

    def verify(self, receipt, public_key=None):
        env = {
            **os.environ,
            "DEMO_MIGRATION_RECEIPT": receipt,
            "DEMO_MIGRATION_PUBLIC_KEY": self.public_key if public_key is None else public_key,
        }
        return subprocess.run(
            ["bash", str(VERIFIER), "manifest.json", self.candidate, self.active, PIN],
            cwd=self.root, env=env, capture_output=True, text=True, check=False,
        )

    def test_exact_signed_receipt_produces_an_evidence_digest(self):
        result = self.verify(self.sign())
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertRegex(result.stdout, r"^[a-f0-9]{64}\n$")

    def test_wrong_release_revision_database_schema_review_or_time_is_rejected(self):
        cases = {
            "candidateSha": self.active,
            "activeSha": self.candidate,
            "activeRevision": "ca-etaye-demo--other",
            "database": "another_database",
            "migrator": MIGRATOR.replace("a" * 64, "b" * 64),
            "schemaAfter": ["11"],
            "schemaBefore": ["15"],
            "backupRestored": False,
            "flywayValidated": False,
            "activeCompatible": False,
            "verifiedAt": 0,
        }
        for name, value in cases.items():
            with self.subTest(field=name):
                self.assertNotEqual(self.verify(self.sign(**{name: value})).returncode, 0)

    def test_missing_receipt_tampered_document_and_untrusted_key_are_rejected(self):
        receipt = self.sign()
        document, signature = receipt.split(".", 1)
        altered = base64.b64encode(base64.b64decode(document) + b" ").decode()
        self.run_command("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-f", "wrong-key")
        wrong_key = " ".join((self.root / "wrong-key.pub").read_text().split()[:2])
        self.assertNotEqual(self.verify("").returncode, 0)
        self.assertNotEqual(self.verify(altered + "." + signature).returncode, 0)
        self.assertNotEqual(self.verify(receipt, wrong_key).returncode, 0)
