import importlib.util
import pathlib
import tempfile
import unittest
import warnings
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("apkdiff", pathlib.Path(__file__).parents[1] / "apkdiff.py")
apkdiff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apkdiff)


class ApkDiffTests(unittest.TestCase):
    def archive(self, directory, name, entries):
        path = pathlib.Path(directory) / name
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(path, "w") as archive:
                for key, value in entries:
                    archive.writestr(key, value)
        return path

    def test_changed_duplicate_member_cannot_pass_comparison(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self.archive(directory, "a.apk", [("classes.dex", b"original"), ("classes.dex", b"original")])
            second = self.archive(directory, "b.apk", [("classes.dex", b"changed"), ("classes.dex", b"original")])
            self.assertFalse(apkdiff.ApkDiff().compare(first, second))

    def test_identical_distinct_members_match(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self.archive(directory, "a.apk", [("classes.dex", b"code"), ("assets/data", b"data")])
            second = self.archive(directory, "b.apk", [("assets/data", b"data"), ("classes.dex", b"code")])
            self.assertTrue(apkdiff.ApkDiff().compare(first, second))

    def test_extra_member_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self.archive(directory, "a.apk", [("classes.dex", b"code")])
            second = self.archive(directory, "b.apk", [("classes.dex", b"code"), ("assets/extra", b"data")])
            self.assertFalse(apkdiff.ApkDiff().compare(first, second))


if __name__ == "__main__":
    unittest.main()
