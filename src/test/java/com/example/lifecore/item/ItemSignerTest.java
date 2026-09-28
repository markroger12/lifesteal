package com.example.lifecore.item;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSignerTest {

    @TempDir
    Path temp;

    @Test
    void signatureVerifiesOnlyUnchangedPayloads() throws Exception {
        ItemSigner signer = ItemSigner.loadOrCreate(temp.resolve("secret.key").toFile());
        String payload = ItemSigner.payload(1, "note", "note", 5.0, "uid", "owner", 42L);
        String signature = signer.sign(payload);
        assertTrue(signer.verify(payload, signature));
        String tampered = ItemSigner.payload(1, "note", "note", 50.0, "uid", "owner", 42L);
        assertFalse(signer.verify(tampered, signature), "changing the stored hearts must invalidate the note");
        assertFalse(signer.verify(payload, ""));
        assertFalse(signer.verify(payload, null));
    }

    @Test
    void secretPersistsAcrossRestarts() throws Exception {
        File file = temp.resolve("secret.key").toFile();
        String payload = ItemSigner.payload(1, "heart", "small_heart", 0, null, null, 0);
        String first = ItemSigner.loadOrCreate(file).sign(payload);
        String second = ItemSigner.loadOrCreate(file).sign(payload);
        assertEquals(first, second);
    }

    @Test
    void differentSecretsProduceDifferentSignatures() throws Exception {
        String payload = ItemSigner.payload(1, "heart", "small_heart", 0, null, null, 0);
        String a = ItemSigner.loadOrCreate(temp.resolve("a.key").toFile()).sign(payload);
        ItemSigner other = ItemSigner.loadOrCreate(temp.resolve("b.key").toFile());
        assertFalse(other.verify(payload, a), "items forged with another secret must be rejected");
    }

    @Test
    void corruptedSecretIsReportedInsteadOfSilentlyReplaced() throws Exception {
        File file = temp.resolve("secret.key").toFile();
        Files.writeString(file.toPath(), "not base64 !!!");
        assertThrows(java.io.IOException.class, () -> ItemSigner.loadOrCreate(file));
    }
}
