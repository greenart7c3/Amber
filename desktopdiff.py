#! /usr/bin/env python3

"""Compares two builds of the Amber desktop app.

Each argument is a release download or a directory:

  .tar.xz     read directly
  .AppImage   unpacked with unsquashfs (squashfs-tools)
  .deb        unpacked with dpkg-deb; its maintainer scripts and control file too
  .rpm        unpacked with rpm2cpio and cpio; its scripts and dependencies
              (read with rpm) too
  .msi        unpacked with msiextract, and its tables and streams (the custom
              action DLLs, icons) exported with msiinfo: msitools, on Linux or
              WSL. msiexec /a is not used: it runs the package's own actions.
  .exe        the Windows installer: the .msi it carries is checked like one,
              and the rest of the file must match byte for byte
  .dmg        mounted read-only with hdiutil (macOS only)
  directory   an unpacked app image: the folder with bin/ and lib/ (Linux),
              Amber.exe and app/ (Windows), Amber.app (macOS), or an AppDir

Packages are unpacked, never run or installed. Every file, directory and
symlink must match: same path, same type, same symlink target, same executable
bit and the same bytes. Timestamps, owners and the other permission bits are
not compared, since they depend on the machine and umask, not on the build.
Nothing else is ignored. Compare like with like: an installer adds a few files
of its own (a .desktop entry, a copyright file) that the app image lacks.

The app bundles the runtime of the JDK it was built with, so a rebuild must use
the same Temurin release; the JDK each build bundles is printed.
"""

import hashlib
import os
import re
import stat
import struct
import subprocess
import sys
import tarfile
import tempfile

JVM_LIBRARIES = {"libjvm.so", "jvm.dll", "libjvm.dylib"}
JVM_VERSION = re.compile(rb"Server VM \(([0-9][^)]{0,40})\)")


class Entry:
    def __init__(self, kind, executable=False, target=None, digest=None, jdk=None, note=False):
        self.kind = kind
        self.executable = executable
        self.target = target
        self.digest = digest
        self.jdk = jdk
        # Reported when it differs, but doesn't fail the comparison.
        self.note = note

    def describe(self):
        if self.kind == "symlink":
            return "symlink to %r" % self.target
        if self.kind == "file":
            return "file%s sha256=%s" % (" (executable)" if self.executable else "", self.digest)
        return self.kind

    def __eq__(self, other):
        return (self.kind, self.executable, self.target, self.digest) == \
            (other.kind, other.executable, other.target, other.digest)


def fileEntry(name, stream, executable):
    digest = hashlib.sha256()
    jdk = None
    if os.path.basename(name) in JVM_LIBRARIES:
        data = stream.read()
        digest.update(data)
        match = JVM_VERSION.search(data)
        jdk = match.group(1).decode("ascii", "replace") if match else None
    else:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return Entry("file", executable, digest=digest.hexdigest(), jdk=jdk)


def isExecutable(mode):
    return bool(mode & stat.S_IXUSR)


def run(command, **kwargs):
    try:
        return subprocess.run(command, stdout=subprocess.DEVNULL, **kwargs).returncode == 0
    except FileNotFoundError:
        print("%s is not installed" % command[0])
        return False


def bytesEntry(data, note=False):
    return Entry("file", digest=hashlib.sha256(data).hexdigest(), note=note)


def splitDebDepends(control):
    """The control file without its Depends field, and the field. jpackage
    derives Depends from the packages that provide the app's libraries on the
    build machine, so it moves with the distro's updates while the app stays
    the same; it only names distro packages."""
    kept, depends, inDepends = [], [], False
    for line in control.splitlines(keepends=True):
        if line[:1] in (b" ", b"\t") and inDepends:
            depends.append(line)
            continue
        inDepends = line.startswith(b"Depends:")
        (depends if inDepends else kept).append(line)
    return b"".join(kept), b"".join(depends)


def output(command, **kwargs):
    """The command's stdout, or None if it is missing or fails."""
    try:
        result = subprocess.run(command, stdout=subprocess.PIPE, **kwargs)
    except FileNotFoundError:
        print("%s is not installed" % command[0])
        return None
    return result.stdout if result.returncode == 0 else None


def peResources(data):
    """Lists (type, name, offset, size) for the resources of a PE file, with
    each type and name an int id or a string, or returns None if it isn't one."""
    if data[:2] != b"MZ":
        return None
    pe, = struct.unpack_from("<I", data, 0x3C)
    if data[pe:pe + 4] != b"PE\0\0":
        return None
    sections, optionalSize = struct.unpack_from("<H12xH", data, pe + 6)
    optional = pe + 24
    magic, = struct.unpack_from("<H", data, optional)
    directories = optional + (112 if magic == 0x20B else 96)
    resourceRva, _ = struct.unpack_from("<II", data, directories + 2 * 8)
    table = optional + optionalSize
    layout = [struct.unpack_from("<8xIIII", data, table + 40 * i) for i in range(sections)]

    def offset(rva):
        for size, address, rawSize, raw in layout:
            if address <= rva < address + max(size, rawSize):
                return raw + rva - address
        raise ValueError("RVA %#x is outside every section" % rva)

    base = offset(resourceRva)

    def name(value):
        if not value & 0x80000000:
            return value
        at = base + (value & 0x7FFFFFFF)
        length, = struct.unpack_from("<H", data, at)
        return data[at + 2:at + 2 + 2 * length].decode("utf-16-le")

    def directory(at):
        named, ids = struct.unpack_from("<HH", data, base + at + 12)
        for i in range(named + ids):
            key, child = struct.unpack_from("<II", data, base + at + 16 + 8 * i)
            yield name(key), child

    found = []
    for kind, types in directory(0):
        for resource, names in directory(types & 0x7FFFFFFF):
            for _, language in directory(names & 0x7FFFFFFF):
                rva, size = struct.unpack_from("<II", data, base + language)
                found.append((kind, resource, offset(rva), size))
    return found


class DesktopDiff:
    def entries(self, path):
        """Maps each path relative to the build's root to its Entry, or
        returns None when the build can't be verified."""
        if os.path.isdir(path):
            return self.directoryEntries(path)
        unpack = {
            ".tar.xz": None,
            ".AppImage": self.unpackAppImage,
            ".deb": self.unpackDeb,
            ".rpm": self.unpackRpm,
            ".msi": self.unpackMsi,
            ".exe": self.unpackExe,
            ".dmg": self.mountDmg,
        }
        for suffix, unpacker in unpack.items():
            if path.endswith(suffix):
                if unpacker is None:
                    return self.tarEntries(path)
                with tempfile.TemporaryDirectory() as tmp:
                    root = os.path.join(tmp, "root")
                    return unpacker(os.path.abspath(path), root)
        print("%s is not a directory or a %s file" % (path, ", ".join(unpack)))
        return None

    @staticmethod
    def appImageOffset(path):
        """An AppImage is an ELF runtime followed by a squashfs image, which
        starts where the ELF file ends: after its section header table."""
        with open(path, "rb") as stream:
            header = stream.read(64)
        if len(header) < 64 or header[:4] != b"\x7fELF" or header[4] != 2 or header[5] != 1:
            return None
        sectionHeaders, = struct.unpack_from("<Q", header, 0x28)
        entrySize, count = struct.unpack_from("<HH", header, 0x3A)
        return sectionHeaders + entrySize * count

    def unpackAppImage(self, path, root):
        offset = self.appImageOffset(path)
        if offset is None:
            print("%s is not a 64-bit little-endian ELF AppImage" % path)
            return None
        if not run(["unsquashfs", "-no-progress", "-no-xattrs", "-offset", str(offset), "-dest", root, path]):
            print("unsquashfs could not unpack %s" % path)
            return None
        return self.directoryEntries(root)

    def unpackDeb(self, path, root):
        control = root + "-control"
        if not run(["dpkg-deb", "-x", path, root]) or not run(["dpkg-deb", "-e", path, control]):
            print("dpkg-deb could not unpack %s" % path)
            return None
        entries = self.directoryEntries(root)
        scripts = self.directoryEntries(control)
        if entries is None or scripts is None:
            return None
        # postinst, prerm, ... run as root on install: they count as much as the files.
        entries.update({"[deb control]/" + name: entry for name, entry in scripts.items() if name != "control"})
        with open(os.path.join(control, "control"), "rb") as stream:
            fields, depends = splitDebDepends(stream.read())
        entries["[deb control]/control (without Depends)"] = bytesEntry(fields)
        entries["[deb control]/Depends"] = Entry("file", digest=depends.decode("utf-8", "replace").strip(), note=True)
        return entries

    def unpackRpm(self, path, root):
        os.makedirs(root)
        with open(path, "rb") as rpm:
            payload = subprocess.Popen(["rpm2cpio", "-"], stdin=rpm, stdout=subprocess.PIPE)
            unpacked = run(["cpio", "-idm", "--quiet", "--no-absolute-filenames"], stdin=payload.stdout, cwd=root)
            payload.stdout.close()
            if payload.wait() != 0 or not unpacked:
                print("rpm2cpio/cpio could not unpack %s" % path)
                return None
        entries = self.directoryEntries(root)
        if entries is None:
            return None
        # The scriptlets run as root on install; the build host and time in
        # the header are left out.
        # Requires, like a .deb's Depends, follows the build machine's packages.
        for query in ["--scripts", "--triggers", "--requires", "--provides", "--conflicts", "--obsoletes"]:
            metadata = output(["rpm", "-qp", query, path], stderr=subprocess.DEVNULL)
            if metadata is None:
                print("rpm could not read %s %s" % (query, path))
                return None
            if query == "--requires":
                text = ", ".join(sorted(metadata.decode("utf-8", "replace").split("\n")[:-1]))
                entries["[rpm]/requires"] = Entry("file", digest=text, note=True)
            else:
                entries["[rpm]/" + query.lstrip("-")] = bytesEntry(metadata)
        return entries

    def unpackMsi(self, path, root):
        os.makedirs(root)
        if not run(["msiextract", "-C", root, path]):
            print("msiextract could not unpack %s" % path)
            return None
        entries = self.directoryEntries(root)
        tables = output(["msiinfo", "tables", path])
        streams = output(["msiinfo", "streams", path])
        if entries is None or tables is None or streams is None:
            print("msiinfo could not read %s" % path)
            return None
        # The tables hold the custom actions, registry keys and shortcuts the
        # installer sets up; the Binary streams are the custom action DLLs.
        # The cabinets are left out (their files are compared above, and the
        # cabinet itself stamps them with the build time), as is the summary
        # information (build time and package revision).
        for table in sorted(set(tables.decode().split()) - {"_SummaryInformation"}):
            # Run where it can't litter: exporting Binary or Icon also writes
            # their streams into a folder of that name.
            data = output(["msiinfo", "export", path, table], cwd=root)
            if data is None:
                print("msiinfo could not export table %s of %s" % (table, path))
                return None
            # Three header lines (columns, types, keys), then the rows, whose
            # stored order means nothing to Windows Installer.
            lines = data.splitlines()
            entries["[msi table]/" + table] = bytesEntry(b"\n".join(lines[:3] + sorted(lines[3:])))
        for stream in sorted(set(streams.decode().splitlines())):
            if stream.endswith(".cab") or stream.endswith("SummaryInformation"):
                continue
            data = output(["msiinfo", "extract", path, stream])
            if data is None:
                print("msiinfo could not extract stream %s of %s" % (stream, path))
                return None
            entries["[msi stream]/" + stream] = bytesEntry(data)
        return entries

    def unpackExe(self, path, root):
        """jpackage's .exe is a launcher with the .msi as its RCDATA "MSI"
        resource. The .msi is checked like one; the rest of the .exe, with that
        resource blanked out, must match byte for byte."""
        with open(path, "rb") as stream:
            data = bytearray(stream.read())
        try:
            resources = peResources(bytes(data))
        except (struct.error, ValueError):
            resources = None
        msis = [(at, size) for kind, name, at, size in (resources or []) if kind == 10 and name == "MSI"]
        if len(msis) != 1:
            print("%s is not a jpackage installer: no single MSI resource" % path)
            return None
        at, size = msis[0]
        os.makedirs(root)
        msi = os.path.join(root, "embedded.msi")
        with open(msi, "wb") as stream:
            stream.write(data[at:at + size])
        entries = self.unpackMsi(msi, os.path.join(root, "msi"))
        if entries is None:
            return None
        data[at:at + size] = bytes(size)
        entries["[exe]/launcher (MSI resource blanked out)"] = bytesEntry(bytes(data))
        return entries

    def mountDmg(self, path, root):
        if sys.platform != "darwin":
            print("A .dmg can only be checked on macOS")
            return None
        if not run(["hdiutil", "attach", "-readonly", "-nobrowse", "-noautoopen", "-mountpoint", root, path]):
            print("hdiutil could not mount %s" % path)
            return None
        try:
            entries = self.directoryEntries(root)
        finally:
            run(["hdiutil", "detach", root, "-force"])
        # The volume's file system event log, written while the image was
        # being built; not part of the app.
        return entries and {name: entry for name, entry in entries.items()
                            if name != ".fseventsd" and not name.startswith(".fseventsd/")}

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
                        entries[rel] = fileEntry(rel, stream, isExecutable(info.st_mode))
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
                        entries[rel] = fileEntry(rel, stream, isExecutable(member.mode))
                else:
                    print("Unexpected entry type for %r in %s!" % (rel, path))
                    return None
        return entries

    @staticmethod
    def jdk(entries):
        versions = {entry.jdk for entry in entries.values() if entry.jdk}
        return ", ".join(sorted(versions)) if versions else None

    def compare(self, first, second):
        firstEntries = self.entries(first)
        secondEntries = self.entries(second)
        if firstEntries is None or secondEntries is None:
            return False
        if not firstEntries:
            print("%s is empty!" % first)
            return False

        firstJdk, secondJdk = self.jdk(firstEntries), self.jdk(secondEntries)
        print("The first build bundles JDK %s, the second JDK %s" % (firstJdk or "(none found)", secondJdk or "(none found)"))
        if firstJdk != secondJdk:
            print("They can't match: rebuild with the Temurin release of the build you are checking.")

        success = True
        for name in sorted(firstEntries.keys() - secondEntries.keys()):
            print("Only in the first build: %r" % name)
            success = False
        for name in sorted(secondEntries.keys() - firstEntries.keys()):
            print("Only in the second build: %r" % name)
            success = False
        for name in sorted(firstEntries.keys() & secondEntries.keys()):
            a, b = firstEntries[name], secondEntries[name]
            if a == b:
                continue
            if a.note and b.note:
                print("Note: %s differs (it follows the packages installed where each was built):" % name)
                print("  first:  %s" % a.digest)
                print("  second: %s" % b.digest)
                continue
            print("Builds differ on %r: %s vs %s" % (name, a.describe(), b.describe()))
            success = False
        return success


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == "--jdk":
        entries = DesktopDiff().entries(sys.argv[2])
        jdk = DesktopDiff.jdk(entries) if entries else None
        print(jdk or "No bundled JDK found in %s" % sys.argv[2])
        sys.exit(0 if jdk else 1)

    if len(sys.argv) != 3:
        print("Usage: desktopdiff <first build> <second build>")
        print("       desktopdiff --jdk <build>   prints the JDK a build bundles")
        print("Each build is a directory or a .tar.xz, .AppImage, .deb, .rpm, .msi, .exe or .dmg file.")
        sys.exit(1)

    if DesktopDiff().compare(sys.argv[1], sys.argv[2]):
        print("Builds match!")
        sys.exit(0)
    else:
        print("Builds don't match!")
        sys.exit(1)
