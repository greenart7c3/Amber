import contextlib
import struct
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


def peWithResources(resources):
    """A minimal PE32+ file whose .rsrc section holds the given
    {(type, name): data} resources, with ids as ints and names as strings."""
    sectionRva, sectionRaw = 0x1000, 0x200
    types = {}
    for (kind, name), data in resources.items():
        types.setdefault(kind, {})[name] = data

    def entries(keys):
        named = sorted(k for k in keys if isinstance(k, str))
        ids = sorted(k for k in keys if isinstance(k, int))
        return named, ids

    # Layout: directories first, then name strings, data entries and data.
    strings, dataEntries, blobs = [], [], []
    tree = bytearray()

    def directory(children):
        named, ids = entries(children)
        at = len(tree)
        tree.extend(struct.pack("<IIHHHH", 0, 0, 0, 0, len(named), len(ids)))
        slots = []
        for key in named + ids:
            slots.append((len(tree), key))
            tree.extend(bytes(8))
        return at, slots

    _, typeSlots = directory(types)
    fixups = []
    for slot, kind in typeSlots:
        names = types[kind]
        at, nameSlots = directory(names)
        fixups.append((slot, kind, at | 0x80000000))
        for nameSlot, name in nameSlots:
            languageAt, languageSlots = directory({0: None})
            fixups.append((nameSlot, name, languageAt | 0x80000000))
            dataEntries.append((languageSlots[0][0], names[name]))
    for slot, key, child in fixups:
        if isinstance(key, str):
            strings.append((slot, key, child))
        else:
            struct.pack_into("<II", tree, slot, key, child)
    for slot, key, child in strings:
        at = len(tree)
        encoded = key.encode("utf-16-le")
        tree.extend(struct.pack("<H", len(key)) + encoded)
        struct.pack_into("<II", tree, slot, at | 0x80000000, child)
    while len(tree) % 4:
        tree.append(0)
    for slot, data in dataEntries:
        entryAt = len(tree)
        tree.extend(bytes(16))
        struct.pack_into("<II", tree, slot, 0, entryAt)
        blobs.append((entryAt, data))
    for entryAt, data in blobs:
        at = len(tree)
        tree.extend(data)
        struct.pack_into("<II", tree, entryAt, sectionRva + at, len(data))

    pe = 0x80
    image = bytearray(sectionRaw)
    image[:2] = b"MZ"
    struct.pack_into("<I", image, 0x3C, pe)
    image[pe:pe + 4] = b"PE\0\0"
    optionalSize = 112 + 16 * 8
    struct.pack_into("<HH12xH", image, pe + 4, 0x8664, 1, optionalSize)
    optional = pe + 24
    struct.pack_into("<H", image, optional, 0x20B)
    struct.pack_into("<II", image, optional + 112 + 2 * 8, sectionRva, len(tree))
    struct.pack_into("<8sIIII", image, optional + optionalSize, b".rsrc", len(tree), sectionRva, len(tree), sectionRaw)
    return bytes(image) + bytes(tree)


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

    def test_bundled_jdk_is_read_from_the_jvm_library(self):
        with tempfile.TemporaryDirectory() as image:
            self.image(image, {"lib/runtime/lib/server/libjvm.so": b"\0OpenJDK 64-Bit Server VM (21.0.12.1+1-LTS) for linux-amd64\0"})
            entries = desktopdiff.DesktopDiff().entries(image)
            self.assertEqual("21.0.12.1+1-LTS", desktopdiff.DesktopDiff.jdk(entries))

    def test_builds_with_different_jdks_do_not_match(self):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            self.image(a, {"runtime/bin/server/jvm.dll": b"Server VM (21.0.12.1+1-LTS)"})
            self.image(b, {"runtime/bin/server/jvm.dll": b"Server VM (21.0.13+7-LTS)"})
            self.assertFalse(compare(a, b))

    def test_unknown_file_type_is_refused(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            path = pathlib.Path(directory) / "Amber.zip"
            path.write_bytes(b"PK")
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(path, image))

    def test_deb_depends_is_split_off_the_control_file(self):
        control = b"Package: amber\nDepends: libc6,\n libx11-6\nInstalled-Size: 1\n"
        fields, depends = desktopdiff.splitDebDepends(control)
        self.assertEqual(b"Package: amber\nInstalled-Size: 1\n", fields)
        self.assertEqual(b"Depends: libc6,\n libx11-6\n", depends)

    def test_differing_notes_do_not_fail_the_comparison(self):
        diff = desktopdiff.DesktopDiff()
        first = {"app.jar": desktopdiff.bytesEntry(b"code"), "[deb control]/Depends": desktopdiff.Entry("file", digest="libc6", note=True)}
        second = {"app.jar": desktopdiff.bytesEntry(b"code"), "[deb control]/Depends": desktopdiff.Entry("file", digest="libc6, libbsd0", note=True)}
        diff.entries = lambda path: first if path == "a" else second
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertTrue(diff.compare("a", "b"))
        self.assertIn("Note:", out.getvalue())

    def test_pe_resources_are_found(self):
        data = peWithResources({(10, "MSI"): b"msi-bytes", (10, "PRODUCT_CODE"): b"{code}", (3, 1): b"icon"})
        found = {(kind, name): data[at:at + size] for kind, name, at, size in desktopdiff.peResources(data)}
        self.assertEqual({(10, "MSI"): b"msi-bytes", (10, "PRODUCT_CODE"): b"{code}", (3, 1): b"icon"}, found)

    def test_exe_without_an_msi_resource_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            exe = pathlib.Path(directory) / "Amber.exe"
            exe.write_bytes(peWithResources({(10, "OTHER"): b"data"}))
            self.assertFalse(compare(exe, exe))

    def test_non_pe_exe_is_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            exe = pathlib.Path(directory) / "Amber.exe"
            exe.write_bytes(b"#!/bin/sh\n")
            self.assertFalse(compare(exe, exe))

    def test_non_elf_appimage_is_refused(self):
        with tempfile.TemporaryDirectory() as directory, tempfile.TemporaryDirectory() as image:
            path = pathlib.Path(directory) / "Amber.AppImage"
            path.write_bytes(b"#!/bin/sh\n" + bytes(64))
            self.image(image, {"lib/app/app.jar": b"code"})
            self.assertFalse(compare(path, image))


if __name__ == "__main__":
    unittest.main()
