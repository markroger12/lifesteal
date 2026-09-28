package com.example.lifecore.item;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * HMAC-SHA256 signatures for plugin-owned items.
 * <p>
 * Clients in creative mode (or NBT editors) can craft items with arbitrary data components,
 * including PersistentDataContainer values. Without the server secret they cannot produce a
 * valid signature, so forged heart notes, heart items and beacons are rejected.
 */
public final class ItemSigner {

    private final byte[] secret;

    public ItemSigner(byte[] secret) {
        if (secret == null || secret.length < 16) {
            throw new IllegalArgumentException("Item signing secret must be at least 16 bytes");
        }
        this.secret = secret.clone();
    }

    /**
     * Loads the secret from the given file, generating a new random one if it does not exist.
     */
    public static ItemSigner loadOrCreate(File file) throws IOException {
        if (file.exists()) {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            try {
                byte[] decoded = Base64.getDecoder().decode(text);
                if (decoded.length >= 16) {
                    return new ItemSigner(decoded);
                }
            } catch (IllegalArgumentException ignored) {
                // fall through and report
            }
            throw new IOException("Corrupted item secret in " + file + " - restore it from a backup or delete it "
                    + "(deleting invalidates all existing LifeCore items).");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent);
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        Files.writeString(file.toPath(), Base64.getEncoder().encodeToString(secret), StandardCharsets.UTF_8);
        try {
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        } catch (SecurityException ignored) {
            // best effort
        }
        return new ItemSigner(secret);
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(java.util.Arrays.copyOf(digest, 18));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    /** Constant-time signature comparison. */
    public boolean verify(String payload, String signature) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        byte[] expected = sign(payload).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    /** Canonical payload for an item. */
    public static String payload(int version, String type, String id, double value, String uid, String owner, long created) {
        return version + "|" + type + "|" + id + "|" + String.format(java.util.Locale.ROOT, "%.2f", value) + "|"
                + (uid == null ? "-" : uid) + "|" + (owner == null ? "-" : owner) + "|" + created;
    }
}
