"""Static Drunsk resource/config smoke tests; never launches Minecraft."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src" / "main" / "resources"
JAVA = ROOT / "src" / "main" / "java"


class DrunskStaticSmokeTests(unittest.TestCase):
    def test_fabric_and_mixin_json_are_valid_and_linked(self):
        fabric = json.loads((RES / "fabric.mod.json").read_text(encoding="utf-8"))
        mixins = json.loads((RES / "drunsk.mixins.json").read_text(encoding="utf-8"))
        self.assertIn(fabric["id"], {"drunsk", "drunsk_full", "drunsk_visual"})
        self.assertIn("drunsk.mixins.json", fabric["mixins"])
        self.assertEqual(mixins["package"], "com.drunsk.mixin")
        self.assertTrue(mixins["required"])
        self.assertEqual(mixins["compatibilityLevel"], "JAVA_25")

    def test_every_mixin_entry_has_a_source_class(self):
        mixins = json.loads((RES / "drunsk.mixins.json").read_text(encoding="utf-8"))
        package = mixins["package"].replace(".", "/")
        for entry in mixins["client"]:
            relative = entry.replace(".", "/") + ".java"
            self.assertTrue((JAVA / package / relative).is_file(), entry)

    def test_localizations_are_valid_and_have_identical_keys(self):
        en = json.loads((RES / "assets/drunsk/lang/en_us.json").read_text(encoding="utf-8"))
        ru = json.loads((RES / "assets/drunsk/lang/ru_ru.json").read_text(encoding="utf-8"))
        self.assertTrue(en)
        self.assertEqual(set(en), set(ru), "EN/RU localization keys diverged")
        self.assertTrue(all(isinstance(v, str) and v.strip() for v in en.values()))
        self.assertTrue(all(isinstance(v, str) and v.strip() for v in ru.values()))

    def test_visual_only_profile_contract(self):
        config = (JAVA / "com/drunsk/DrunskConfig.java").read_text(encoding="utf-8")
        client = (JAVA / "com/drunsk/DrunskClient.java").read_text(encoding="utf-8")
        self.assertIn("visualOnly", config)
        self.assertIn("automationAllowed", config)
        self.assertGreaterEqual(client.count("notifyVisualOnly(client)"), 7)
        self.assertIn("freecam = false", config)
        for lang in ("en_us.json", "ru_ru.json"):
            text = (RES / "assets/drunsk/lang" / lang).read_text(encoding="utf-8")
            self.assertIn("drunsk.profile.visual_only", text)

    def test_config_json_contract_matches_save_and_defaults(self):
        source = (JAVA / "com/drunsk/DrunskConfig.java").read_text(encoding="utf-8")
        # The persisted top-level contract must remain stable.
        self.assertIn('root.addProperty("relayUrl", relayUrl)', source)
        self.assertIn('root.add("tokens", t)', source)
        self.assertIn('root.add("utils", u)', source)
        util_keys = set(re.findall(r'u\.addProperty\("([A-Za-z0-9]+)"', source))
        read_keys = set(re.findall(r'opt(?:Bool|Double|Int)\(u, "([A-Za-z0-9]+)"', source))
        self.assertEqual(util_keys, read_keys)
        self.assertGreaterEqual(len(util_keys), 25)
        self.assertIn('relayUrl = "wss://xn--d1amilgk.online/drunsk"', source)

    def test_updater_uses_pinned_https_github_api_url(self):
        source = (JAVA / "com/drunsk/update/DrunskUpdateChecker.java").read_text(encoding="utf-8")
        match = re.search(r'URI\.create\("([^\"]+)"\)', source)
        self.assertIsNotNone(match)
        self.assertEqual(match.group(1), "https://api.github.com/repos/kwent-theking/Drunsk/releases/latest")
        self.assertNotIn("http://", match.group(1))
        self.assertIn('header("Accept", "application/vnd.github+json")', source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
