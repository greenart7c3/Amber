#! /usr/bin/env python3

import re
import sys
from zipfile import ZipFile


class ApkDiff:
    # Only signing output is ignored. JAR signature files always sit directly
    # under META-INF/, so a file that merely looks like one anywhere else in
    # the APK (or in a META-INF subdirectory) is still compared.
    IGNORE_FILES = {
        b"META-INF/MANIFEST.MF",
        b"META-INF/code_transparency_signed.jwt",
        b"stamp-cert-sha256",
    }
    IGNORE_PATTERN = re.compile(rb"META-INF/[^/]+\.(SF|RSA|DSA|EC)")

    def compare(self, firstApk, secondApk):
        with ZipFile(firstApk, "r") as firstZip, ZipFile(secondApk, "r") as secondZip:
            firstEntries = self.entries(firstZip, firstApk)
            secondEntries = self.entries(secondZip, secondApk)
            if firstEntries is None or secondEntries is None:
                return False
            return self.compareEntryNames(firstEntries, secondEntries) and \
                self.compareEntryContents(firstZip, secondZip, firstEntries, secondEntries)

    @staticmethod
    def rawName(info):
        # zipfile decodes names as UTF-8 only when flag bit 11 is set and as
        # cp437 otherwise; re-encoding the same way gives back the exact bytes
        # stored in the archive, which is what Android compares.
        return info.orig_filename.encode("utf-8" if info.flag_bits & 0x800 else "cp437")

    def ignored(self, rawName):
        return rawName in self.IGNORE_FILES or self.IGNORE_PATTERN.fullmatch(rawName) is not None

    def entries(self, apk, path):
        """Maps each raw entry name to its ZipInfo, or returns None when the
        archive holds the same name twice: Android and zipfile may each pick a
        different copy, so such an APK can't be verified."""
        entries = {}
        for info in apk.infolist():
            name = self.rawName(info)
            if name in entries:
                print("Duplicate ZIP entry %r in %s!" % (name, path))
                return None
            entries[name] = info
        return {name: info for name, info in entries.items() if not self.ignored(name)}

    def compareEntryNames(self, firstEntries, secondEntries):
        onlyFirst = sorted(firstEntries.keys() - secondEntries.keys())
        onlySecond = sorted(secondEntries.keys() - firstEntries.keys())
        for name in onlyFirst:
            print("Only in the first APK: %r" % name)
        for name in onlySecond:
            print("Only in the second APK: %r" % name)
        return not onlyFirst and not onlySecond

    def compareEntryContents(self, firstZip, secondZip, firstEntries, secondEntries):
        success = True
        for name, firstInfo in sorted(firstEntries.items()):
            secondInfo = secondEntries[name]
            if firstZip.read(firstInfo) != secondZip.read(secondInfo):
                firstZip.extract(firstInfo, "mismatches/first")
                secondZip.extract(secondInfo, "mismatches/second")
                print("APKs differ on file %r! Files extracted to the mismatches/ directory." % name)
                success = False
        return success


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: apkdiff <pathToFirstApk> <pathToSecondApk>")
        sys.exit(1)

    if ApkDiff().compare(sys.argv[1], sys.argv[2]):
        print("APKs match!")
        sys.exit(0)
    else:
        print("APKs don't match!")
        sys.exit(1)
