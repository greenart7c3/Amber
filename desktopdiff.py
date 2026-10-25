#! /usr/bin/env python3

"""Compares two builds of the Amber desktop app.

Each argument is a Linux release (Amber-X.Y.Z-linux-x86_64.tar.xz or
Amber-X.Y.Z-x86_64.AppImage) or a directory: an unpacked app image (the folder
with bin/ and lib/), or the AppDir an AppImage is made from. An AppImage is
unpacked with unsquashfs (squashfs-tools), never run. Every file, directory and symlink must match: same path,
same type, same symlink target, same executable bit and the same bytes.
Timestamps, owners and the other permission bits are not compared, since they
depend on the machine and umask, not on the build. Nothing else is ignored.
"""

import hashlib
import os
import stat
import struct
import subprocess
import sys
import tarfile
import tempfile


class Entry:
    def __init__(self, kind, executable=False, target=None, digest=None):
        self.kind = kind
        self.executable = executable
        self.target = target
        self.digest = digest

    def describe(self):
        if self.kind == "symlink":
            return "symlink to %r" % self.target
        if self.kind == "file":
            return "file%s sha256=%s" % (" (executable)" if self.executable else "", self.digest)
        return self.kind

    def __eq__(self, other):
        return (self.kind, self.executable, self.target, self.digest) == \
            (other.kind, other.executable, other.target, other.digest)


def sha256(stream):
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1 << 20), b""):
        digest.update(chunk)
    return digest.hexdigest()


def isExecutable(mode):
    return bool(mode & stat.S_IXUSR)


class DesktopDiff:
    def entries(self, path):
        """Maps each path relative to the app image root to its Entry, or
        returns None when the build can't be verified."""
        if os.path.isdir(path):
            return self.directoryEntries(path)
        if path.endswith(".tar.xz"):
            return self.tarEntries(path)
        if path.endswith(".AppImage"):
            return self.appImageEntries(path)
        print("%s is not a directory, a .tar.xz or an .AppImage" % path)
        return None

    @staticmethod
    def appImageOffset(path):
        """An AppImage is an ELF runtime followed by a squashfs image, which
        starts where the ELF file ends: after its section header table."""
        with open(path, "rb") as stream:
            header = stream.read(64)
        if header[:4] != b"\x7fELF" or header[4] != 2 or header[5] != 1:
            return None
        sectionHeaders, = struct.unpack_from("<Q", header, 0x28)
        entrySize, count = struct.unpack_from("<HH", header, 0x3A)
        return sectionHeaders + entrySize * count

    def appImageEntries(self, path):
        offset = self.appImageOffset(path)
        if offset is None:
            print("%s is not a 64-bit little-endian ELF AppImage" % path)
            return None
        with tempfile.TemporaryDirectory() as tmp:
            root = os.path.join(tmp, "root")
            result = subprocess.run(
                ["unsquashfs", "-no-progress", "-no-xattrs", "-offset", str(offset), "-dest", root, path],
                stdout=subprocess.DEVNULL,
            )
            if result.returncode != 0:
                print("unsquashfs could not unpack %s" % path)
                return None
            return self.directoryEntries(root)

    def directoryEntries(self, root):
        entries = {}
        for parent, dirs, files in os.walk(root):
            for name in dirs + files:
                full = os.path.join(parent, name)
                rel = os.path.relpath(full, root).replace(os.sep, "/")
                info = os.lstat(full)
                if stat.S_ISLNK(info.st_mode):
                    entries[rel] = Entry("symlink", target=os.readlink(full))
                elif stat.S_ISDIR(info.st_mode):
                    entries[rel] = Entry("directory")
                elif stat.S_ISREG(info.st_mode):
                    with open(full, "rb") as stream:
                        entries[rel] = Entry("file", isExecutable(info.st_mode), digest=sha256(stream))
                else:
                    print("Unexpected special file %r in %s!" % (rel, root))
                    return None
        return entries

    def tarEntries(self, path):
        # The release tarball holds a single top-level Amber/ directory; paths
        # are compared below it so a directory input lines up with it.
        entries = {}
        with tarfile.open(path, "r:xz") as tar:
            for member in tar:
                name = member.name.rstrip("/")
                top, _, rel = name.partition("/")
                if top != "Amber" or name.startswith("/") or ".." in name.split("/"):
                    print("Unexpected path %r in %s!" % (member.name, path))
                    return None
                if not rel:
                    continue
                # tar keeps every copy of a repeated name and extracting
                # leaves the last one, so such an archive can't be verified.
                if rel in entries:
                    print("Duplicate entry %r in %s!" % (rel, path))
                    return None
                if member.issym():
                    entries[rel] = Entry("symlink", target=member.linkname)
                elif member.isdir():
                    entries[rel] = Entry("directory")
                elif member.isreg():
                    with tar.extractfile(member) as stream:
                        entries[rel] = Entry("file", isExecutable(member.mode), digest=sha256(stream))
                else:
                    print("Unexpected entry type for %r in %s!" % (rel, path))
                    return None
        return entries

    def compare(self, first, second):
        firstEntries = self.entries(first)
        secondEntries = self.entries(second)
        if firstEntries is None or secondEntries is None:
            return False
        if not firstEntries:
            print("%s is empty!" % first)
            return False

        success = True
        for name in sorted(firstEntries.keys() - secondEntries.keys()):
            print("Only in the first build: %r" % name)
            success = False
        for name in sorted(secondEntries.keys() - firstEntries.keys()):
            print("Only in the second build: %r" % name)
            success = False
        for name in sorted(firstEntries.keys() & secondEntries.keys()):
            a, b = firstEntries[name], secondEntries[name]
            if a != b:
                print("Builds differ on %r: %s vs %s" % (name, a.describe(), b.describe()))
                success = False
        return success


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: desktopdiff <first build> <second build>  (each a .tar.xz, .AppImage or directory)")
        sys.exit(1)

    if DesktopDiff().compare(sys.argv[1], sys.argv[2]):
        print("Builds match!")
        sys.exit(0)
    else:
        print("Builds don't match!")
        sys.exit(1)
