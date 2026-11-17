package com.sqlens.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.*;

class HistoryFileTest {

    @Test
    void createsOwnerOnlyFile(@TempDir Path dir) throws IOException {
        Path f = HistoryFile.secure(dir.resolve(".sqlens_history"));
        assertNotNull(f);
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
    }

    @Test
    void tightensExistingWorldReadableFile(@TempDir Path dir) throws IOException {
        Path f = Files.createFile(dir.resolve("h"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        HistoryFile.secure(f);
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(f)));
    }

    @Test
    void refusesSymlinks(@TempDir Path dir) throws IOException {
        Path target = Files.createFile(dir.resolve("target"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r--r--")));
        Path link = Files.createSymbolicLink(dir.resolve("link"), target);
        assertNull(HistoryFile.secure(link));
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)), "target untouched");
    }
}
