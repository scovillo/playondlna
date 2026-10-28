"""Validate Android Fastlane text metadata without external dependencies."""

from pathlib import Path
import sys


METADATA_ROOT = Path(__file__).resolve().parents[1] / "fastlane/metadata/android"
TEXT_LIMITS = {
    "title.txt": 30,
    "short_description.txt": 80,
    "full_description.txt": 4000,
    "changelogs/*.txt": 500,
}


def validate(root: Path) -> list[str]:
    errors = []
    files = []
    for name, limit in TEXT_LIMITS.items():
        files.extend((path, limit) for path in root.glob(f"*/{name}"))
    if not files:
        return [f"{root}: no Fastlane text metadata found"]

    for path, limit in sorted(files):
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeError, OSError) as error:
            errors.append(f"{path}: cannot read UTF-8 text: {error}")
            continue
        if not text.strip():
            errors.append(f"{path}: text must not be empty")
        if len(text) > limit:
            errors.append(f"{path}: {len(text)} characters exceeds limit of {limit}")
    return errors


def main() -> int:
    errors = validate(METADATA_ROOT)
    if errors:
        print("Fastlane metadata validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print("Fastlane metadata validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
