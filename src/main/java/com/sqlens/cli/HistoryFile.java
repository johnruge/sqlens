package com.sqlens.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/** Prepares the REPL history file so only the current user can read it. */
public final class HistoryFile {

    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    private HistoryFile() {}

    /**
     * @return the path if it is (now) a regular, owner-only file; null if history should be
     *         disabled (e.g. the path is a symlink or permissions cannot be set)
     */
    public static Path secure(Path file) {
        try {
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return null;   // refuse symlinks and special files
            }
            Files.setPosixFilePermissions(file, OWNER_ONLY);
            return file;
        } catch (IOException | UnsupportedOperationException e) {
            return null;
        }
    }
}
