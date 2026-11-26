"""Exercise check_doc_workflow against the real repository and tampered workflow fixtures."""
from contextlib import redirect_stdout
from io import StringIO
import json
from pathlib import Path
import shutil
import tempfile
import unittest

import check_doc_workflow


ROOT = Path(__file__).resolve().parents[1]


class CheckDocWorkflowTests(unittest.TestCase):
    def test_repository_workflows_and_configs_pass(self):
        with redirect_stdout(StringIO()) as output:
            self.assertEqual(check_doc_workflow.main(), 0)
        summary = json.loads(output.getvalue())
        self.assertTrue(summary["ok"])
        self.assertFalse(summary["applicationBuild"])
        self.assertTrue(summary["architecturePages"])

    def test_rejects_secret_reference_and_unpinned_action_in_copied_repo(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for rel in (
                ".github/workflows/documentation.yml",
                ".github/workflows/codeql.yml",
                ".github/workflows/architecture-pages.yml",
                ".github/codeql-config.yml",
                ".github/codeql-api-config.yml",
                ".github/codeql-web-config.yml",
                ".coderabbit.yaml",
                ".pr_agent.toml",
            ):
                dst = root / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / rel, dst)
            self.assertTrue(check_doc_workflow.check_doc_workflows(root)["ok"])

            doc_yml = root / ".github/workflows/documentation.yml"
            original = doc_yml.read_text(encoding="utf-8")
            doc_yml.write_text(original + "\n      # ${{ secrets.SHOULD_FAIL }}\n", encoding="utf-8")
            with self.assertRaises(AssertionError):
                check_doc_workflow.check_doc_workflows(root)

            doc_yml.write_text(original.replace("@" + "3d3c42e5aac5ba805825da76410c181273ba90b1", "@v7"), encoding="utf-8")
            with self.assertRaises(AssertionError):
                check_doc_workflow.check_doc_workflows(root)
