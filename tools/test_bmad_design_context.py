"""Every Étaye BMAD override loads the design reference and carries the development gate."""
import tomllib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CUSTOM = ROOT / "_bmad/custom"
DESIGN_FILE = "file:{project-root}/docs/design/design-system.md"
GATE = "development_authorized in _bmad/custom/config.toml is false"


def overrides():
    return sorted(CUSTOM.glob("bmad-*.toml"))


class BmadDesignContextTests(unittest.TestCase):
    def test_overrides_exist_for_the_design_and_build_workflows(self):
        names = {p.stem for p in overrides()}
        required = {"bmad-ux", "bmad-build", "bmad-build-auto", "bmad-code-review", "bmad-agent-dev", "bmad-agent-ux-designer"}
        self.assertTrue(required <= names, required - names)

    def test_each_override_matches_its_skill_surface_and_carries_the_facts(self):
        for path in overrides():
            with self.subTest(path.name):
                skill = ROOT / ".claude/skills" / path.stem
                self.assertTrue((skill / "customize.toml").exists(), "unknown skill " + path.stem)
                base = tomllib.loads((skill / "customize.toml").read_text(encoding="utf-8"))
                override = tomllib.loads(path.read_text(encoding="utf-8"))
                surface = "agent" if "agent" in base else "workflow"
                self.assertEqual(list(override), [surface])
                facts = override[surface]["persistent_facts"]
                self.assertTrue(any(GATE in fact for fact in facts), "development gate missing")
                if surface == "workflow":
                    self.assertEqual(facts[0], DESIGN_FILE)
                else:
                    self.assertNotIn(DESIGN_FILE, facts, "agents point to the file; workflows load it once")
                    self.assertTrue(any("docs/design/design-system.md" in fact for fact in facts))

    def test_every_loaded_file_exists_and_the_gate_flag_is_defined(self):
        for path in overrides():
            (table,) = tomllib.loads(path.read_text(encoding="utf-8")).values()
            for fact in table["persistent_facts"]:
                if fact.startswith("file:"):
                    target = ROOT / fact.removeprefix("file:{project-root}/")
                    self.assertTrue(target.is_file(), "%s loads a missing file %s" % (path.name, target))
        config = tomllib.loads((CUSTOM / "config.toml").read_text(encoding="utf-8"))
        self.assertIn("development_authorized", config["etaye"])
        self.assertIsInstance(config["etaye"]["authorized_stories"], list)


if __name__ == "__main__":
    unittest.main()
