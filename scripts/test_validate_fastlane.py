from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from validate_fastlane import TEXT_LIMITS, validate


class ValidateFastlaneTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)

    def write(self, name, text):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_limits_accept_boundary_and_reject_one_extra_character(self):
        limits = {name.replace("*", "16"): limit for name, limit in TEXT_LIMITS.items()}
        limits["changelogs/default.txt"] = TEXT_LIMITS["changelogs/*.txt"]
        for name, limit in limits.items():
            with self.subTest(name=name):
                path = self.write(f"uk/{name}", "ї" * limit)
                self.assertEqual([], validate(self.root))
                path.write_text("ї" * limit + "\n", encoding="utf-8")
                errors = validate(self.root)
                self.assertEqual(1, len(errors))
                self.assertIn(f"{limit + 1} characters exceeds limit of {limit}", errors[0])
                path.unlink()

    def test_reports_all_locales_and_historical_changelogs(self):
        for locale in ("en-US", "de-DE", "uk", "ru"):
            self.write(f"{locale}/changelogs/1.txt", "x" * 501)
        self.assertEqual(4, len(validate(self.root)))

    def test_rejects_empty_text(self):
        self.write("en-US/short_description.txt", " \n")
        self.assertIn("text must not be empty", validate(self.root)[0])

    def test_rejects_invalid_utf8(self):
        path = self.write("en-US/full_description.txt", "text")
        path.write_bytes(b"\xff")
        self.assertIn("cannot read UTF-8", validate(self.root)[0])

    def test_missing_metadata_does_not_pass_silently(self):
        self.assertIn("no Fastlane text metadata found", validate(self.root)[0])


if __name__ == "__main__":
    unittest.main()
