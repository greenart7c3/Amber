import importlib.util
import os
import pathlib
import tempfile
import unittest
import warnings
from zipfile import ZipFile, ZipInfo

spec = importlib.util.spec_from_file_location("apkdiff", pathlib.Path(__file__).parents[1] / "apkdiff.py")
apkdiff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(apkdiff)


class Cp437ZipInfo(ZipInfo):
    """Stores the name as cp437 without the UTF-8 flag, as some zip tools do."""

    def _encodeFilenameFlags(self):
        return self.filename.encode("cp437"), self.flag_bits & ~0x800


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

    def test_duplicate_in_only_one_archive_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            official = self.archive(directory, "a.apk", [("classes.dex", b"code"), ("classes.dex", b"payload")])
            rebuilt = self.archive(directory, "b.apk", [("classes.dex", b"code")])
            self.assertFalse(apkdiff.ApkDiff().compare(official, rebuilt))
            self.assertFalse(apkdiff.ApkDiff().compare(rebuilt, official))

    def test_signing_files_are_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            signed = self.archive(directory, "a.apk", [
                ("classes.dex", b"code"),
                ("META-INF/MANIFEST.MF", b"manifest"),
                ("META-INF/CERT.SF", b"sf"),
                ("META-INF/CERT.RSA", b"rsa"),
                ("stamp-cert-sha256", b"stamp"),
            ])
            unsigned = self.archive(directory, "b.apk", [("classes.dex", b"code")])
            self.assertTrue(apkdiff.ApkDiff().compare(signed, unsigned))

    def test_lookalike_signing_and_license_files_are_compared(self):
        # The old Dockerfile deleted *.SF, *.RSA, *.DSA and *LICENSE* anywhere
        # in both APKs, so an extra file with such a name went unnoticed.
        for extra in ["assets/LICENSE_payload.so", "lib/arm64-v8a/x.SF", "META-INF/sub/CERT.RSA"]:
            with self.subTest(extra=extra), tempfile.TemporaryDirectory() as directory:
                official = self.archive(directory, "a.apk", [("classes.dex", b"code"), (extra, b"payload")])
                rebuilt = self.archive(directory, "b.apk", [("classes.dex", b"code")])
                self.assertFalse(apkdiff.ApkDiff().compare(official, rebuilt))

    def test_names_are_compared_as_raw_bytes(self):
        # Both entries decode to "res/\u00e9.xml", but the stored bytes differ
        # (UTF-8 with the flag set vs cp437), and Android compares bytes.
        with tempfile.TemporaryDirectory() as directory:
            utf8 = self.archive(directory, "a.apk", [("res/\u00e9.xml", b"x")])
            cp437 = pathlib.Path(directory) / "b.apk"
            with ZipFile(cp437, "w") as archive:
                archive.writestr(Cp437ZipInfo("res/\u00e9.xml"), b"x")
            with ZipFile(utf8) as first, ZipFile(cp437) as second:
                self.assertEqual(first.namelist(), second.namelist())
            self.assertFalse(apkdiff.ApkDiff().compare(utf8, cp437))

    def test_changed_content_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self.archive(directory, "a.apk", [("classes.dex", b"code")])
            second = self.archive(directory, "b.apk", [("classes.dex", b"other")])
            cwd = pathlib.Path.cwd()
            try:
                # Mismatches are extracted relative to the working directory.
                os.chdir(directory)
                self.assertFalse(apkdiff.ApkDiff().compare(first, second))
            finally:
                os.chdir(cwd)


if __name__ == "__main__":
    unittest.main()
