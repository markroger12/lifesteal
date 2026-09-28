package com.example.lifecore.configuration.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects what a migration changed so it can be logged for administrators.
 */
public final class MigrationReport {

    private final String fileName;
    private final List<String> changes = new ArrayList<>();
    private int addedKeys;
    private int fromVersion;
    private int toVersion;
    private String backupFile = "";

    public MigrationReport(String fileName) {
        this.fileName = fileName;
    }

    public void change(String description) {
        changes.add(description);
    }

    public void keyAdded() {
        addedKeys++;
    }

    public void versions(int from, int to) {
        this.fromVersion = from;
        this.toVersion = to;
    }

    public void backup(String backupFile) {
        this.backupFile = backupFile;
    }

    public String fileName() {
        return fileName;
    }

    public List<String> changes() {
        return Collections.unmodifiableList(changes);
    }

    public int addedKeys() {
        return addedKeys;
    }

    public int fromVersion() {
        return fromVersion;
    }

    public int toVersion() {
        return toVersion;
    }

    public String backupFile() {
        return backupFile;
    }

    public String summary() {
        return fileName + ": v" + fromVersion + " -> v" + toVersion + ", " + addedKeys + " new key(s), "
                + changes.size() + " structural change(s)" + (backupFile.isEmpty() ? "" : ", backup: " + backupFile);
    }
}
