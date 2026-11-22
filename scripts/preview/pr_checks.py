"""Select additional Gradle and emulator checks for a Preview PR merge commit."""
import os
import re
import subprocess
from pathlib import PurePosixPath

# core and custom_keyboard already run on every PR.
LIBRARY_SUITES = {"gojuon_keyboard", "qwerty_keyboard", "symbol_keyboard", "tenkey", "zenz"}
SHARED_BUILD = {
    ".github/workflows/preview-ci.yml", "scripts/preview/pr_checks.py",
    ".gitmodules", "build.gradle", "settings.gradle", "gradle.properties", "gradlew",
}


def git_paths(*args):
    data = subprocess.check_output(["git", *args])
    return [os.fsdecode(path) for path in data.split(b"\0") if path]


def select_checks(changed, app_tests):
    device_input = emoji_rendering = apk = False
    modules, tests = set(), set()
    for name in changed:
        path = PurePosixPath(name)
        module = path.parts[0]
        shared = name in SHARED_BUILD or name.startswith("gradle/")
        if module in LIBRARY_SUITES:
            modules.add(module)
        if shared:
            modules.update(LIBRARY_SUITES)

        source = name.lower()
        android_source = module in {"app", "core"} and "/src/" in source
        device_input |= (
            shared or module in {"custom_keyboard", "qwerty_keyboard", "tenkey", "gojuon_keyboard"}
            or name in {"app/build.gradle", "core/build.gradle", "scripts/preview/device_tests.sh",
                        ".github/scripts/ime-emulator-common.sh", ".github/scripts/run-multi-pointer-regression.sh"}
            or android_source and any(word in source for word in (
                "flick", "touch", "pointer", "smalltsu", "small_tsu", "haptic", "keyboardlayout",
                "keyboard_layout", "imeservice", "apppreference", "floating_keyboard",
            ))
        )
        emoji_rendering |= (
            shared or module == "symbol_keyboard"
            or name in {"app/build.gradle", "core/build.gradle", "scripts/preview/emoji_device_tests.sh"}
            or android_source and any(word in source for word in ("emoji", "font", "apppreference"))
        )

        apk |= (
            shared or name in {"scripts/preview/verify_apk.py", "zenz/scripts/prepare_zenz_model.sh"}
            or name.endswith((".gradle", ".gradle.kts", ".pro")) or path.name == "AndroidManifest.xml"
            or any(part in path.parts for part in ("assets", "cpp", "jniLibs"))
            or name.startswith("app/src/preview/") or name == "app/src/main/res/xml/method.xml"
            or path.suffix.lower() in {".aar", ".so", ".ttf", ".otf"}
        )

        # Include edited app test classes and conventionally named tests for edited app code.
        if module == "app" and path.suffix in {".java", ".kt"}:
            candidate = path.stem if name.startswith("app/src/test/") else path.stem + "Test"
            if candidate in app_tests and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", candidate):
                tests.add(candidate)

    return {
        "apk": str(apk).lower(),
        "device_input": str(device_input).lower(),
        "emoji_rendering": str(emoji_rendering).lower(),
        "library_tasks": " ".join(f":{module}:testDebugUnitTest" for module in sorted(modules)),
        "app_tests": " ".join(sorted(tests)),
    }


if __name__ == "__main__":
    # HEAD is GitHub's synthetic merge; its first parent is the exact tested base.
    # Both sides of a rename matter when a file moves between modules.
    changed = git_paths("diff", "--name-only", "--no-renames", "-z", "HEAD^1", "HEAD")
    app_tests = {
        PurePosixPath(name).stem
        for name in git_paths("ls-tree", "-rz", "--name-only", "HEAD", "--", "app/src/test")
        if name.endswith(("Test.kt", "Test.java"))
    }
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key, value in select_checks(changed, app_tests).items():
            print(f"{key}={value}", file=output)
