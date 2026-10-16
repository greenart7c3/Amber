import os
import pathlib
import subprocess
import tempfile
import unittest


class ReleaseTagTests(unittest.TestCase):
    def test_valid_git_tag_is_data_not_shell_code(self):
        tag = "v$(poc)"
        subprocess.run(["git", "check-ref-format", "refs/tags/" + tag], check=True)
        workflow = pathlib.Path(__file__).parents[1] / ".github/workflows/create-release.yml"
        step = workflow.read_text().split("- name: Rename APK for release asset", 1)[1].split("- name:", 1)[0]
        run = next(line.strip().removeprefix("run: ") for line in step.splitlines() if line.strip().startswith("run: "))
        # Model GitHub's expression substitution only where the workflow puts it in run.
        run = run.replace("${{ github.ref_name }}", tag)
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            source = root / "app/build/outputs/apk/free/release/app-free-universal-release-unsigned-signed.apk"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"synthetic-apk")
            command = root / "poc"
            command.write_text('#!/bin/sh\n: > "$AMBER_MARKER"\nprintf injected\n')
            command.chmod(0o755)
            marker = root / "shell-executed"
            env = dict(os.environ, RELEASE_TAG=tag, AMBER_MARKER=str(marker), PATH=str(root) + os.pathsep + os.environ["PATH"])
            subprocess.run(["bash", "-c", run], cwd=root, env=env, check=True)
            self.assertFalse(marker.exists(), "release tag executed a command")
            self.assertTrue((root / ("amber-fdroid-universal-" + tag + ".apk")).exists())


if __name__ == "__main__":
    unittest.main()
