import contextlib
import importlib.util
import io
import os
import pathlib
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("desktopdiff", pathlib.Path(__file__).parents[1] / "desktopdiff.py")
desktopdiff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(desktopdiff)


def compare(first, second):
    with contextlib.redirect_stdout(io.StringIO()):
        return desktopdiff.DesktopDiff().compare(str(first), str(second))


class DesktopDiffTests(unittest.TestCase):
    def image(self, root, files, executable=(), links=None):
        root = pathlib.Path(root)
        for name, data in files.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            os.chmod(path, 0o755 if name in executable else 0o644)
        for name, target in (links or {}).items():
            os.symlink(target, root / name)
        return root

    def tarball(self, path, members, top="Amber"):
        """members: (name, data) pairs, written in order, so names may repeat."""
        with tarfile.open(path, "w:xz") as tar:
            tar.addfile(self.dirInfo(top))
            dirs = sorted({str(parent) for name, _ in members for parent in pathlib.PurePosixPath(name).parents} - {"."})
            for name in dirs:
                tar.addfile(self.dirInfo("%s/%s" % (top, name)))
            for name, data in members:
                info = tarfile.TarInfo("%s/%s" % (top, name))
                info.size = len(data)
                info.mode = 0o644
                tar.addfile(info, io.BytesIO(data))
        return path

    @staticmethod
    def dirInfo(name):
        info = tarfile.TarInfo(name)
        info.type = tarfile.DIRTYPE
        info.mode = 0o755
        return info

    def test_identical_images_match(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            files = {"bin/Amber": b"launcher", "lib/app/app.jar": b"code"}
            self.image(a, files, executable={"bin/Amber"})
            self.image(b, files, executable={"bin/Amber"})
            self.assertTrue(compare(a, b))

    def test_timestamps_and_other_mode_bits_are_ignored(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"lib/app/app.jar": b"code"})
            self.image(b, {"lib/app/app.jar": b"code"})
            os.chmod(pathlib.Path(b) / "lib/app/app.jar", 0o600)
            os.utime(pathlib.Path(b) / "lib/app/app.jar", (0, 0))
            self.assertTrue(compare(a, b))

    def test_changed_content_fails(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"lib/app/app.jar": b"code"})
            self.image(b, {"lib/app/app.jar": b"changed"})
            self.assertFalse(compare(a, b))

    def test_extra_file_fails_either_way(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"lib/app/app.jar": b"code"})
            self.image(b, {"lib/app/app.jar": b"code", "lib/app/extra.jar": b"payload"})
            self.assertFalse(compare(a, b))
            self.assertFalse(compare(b, a))

    def test_executable_bit_is_compared(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"lib/app/Amber.cfg": b"cfg"})
            self.image(b, {"lib/app/Amber.cfg": b"cfg"}, executable={"lib/app/Amber.cfg"})
            self.assertFalse(compare(a, b))

    def test_symlink_target_is_compared(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"amber.png": b"png"}, links={".DirIcon": "amber.png"})
            self.image(b, {"amber.png": b"png", "other.png": b"png"}, links={".DirIcon": "other.png"})
            (pathlib.Path(a) / "other.png").write_bytes(b"png")
            self.assertFalse(compare(a, b))

    def test_symlink_does_not_match_the_file_it_points_to(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"amber.png": b"png", ".DirIcon": b"png"})
            self.image(b, {"amber.png": b"png"}, links={".DirIcon": "amber.png"})
            self.assertFalse(compare(a, b))

    def test_tarball_matches_its_unpacked_image(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            tarball = self.tarball(pathlib.Path(directory) / "Amber.tar.xz", [("lib/app/app.jar", b"code")])
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertTrue(compare(tarball, image))

    def test_tarball_with_a_changed_file_fails(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            tarball = self.tarball(pathlib.Path(directory) / "Amber.tar.xz", [("lib/app/app.jar", b"payload")])
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(tarball, image))

    def test_duplicate_tar_member_cannot_pass_comparison(self):
        # Extracting keeps the last copy, so the first one must not be what is checked.
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            tarball = self.tarball(pathlib.Path(directory) / "Amber.tar.xz", [
                ("lib/app/app.jar", b"code"),
                ("lib/app/app.jar", b"payload"),
            ])
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(tarball, image))

    def test_tar_member_outside_the_app_folder_fails(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            tarball = self.tarball(pathlib.Path(directory) / "Amber.tar.xz", [("lib/app/app.jar", b"code")], top="Other")
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(tarball, image))

    def test_empty_build_does_not_match(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.assertFalse(compare(a, b))

    def test_appimage_offset_is_the_end_of_the_elf_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "Amber.AppImage"
            header = bytearray(64)
            header[:6] = b"\x7fELF\x02\x01"
            header[0x28:0x30] = (1000).to_bytes(8, "little")
            header[0x3A:0x3C] = (64).to_bytes(2, "little")
            header[0x3C:0x3E] = (30).to_bytes(2, "little")
            path.write_bytes(bytes(header))
            self.assertEqual(1000 + 64 * 30, desktopdiff.DesktopDiff.appImageOffset(str(path)))

    def test_non_elf_appimage_is_refused(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            path = pathlib.Path(directory) / "Amber.AppImage"
            path.write_bytes(b"#!/bin/sh\n" + bytes(64))
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(path, image))


if __name__ == "__main__":
    unittest.main()
