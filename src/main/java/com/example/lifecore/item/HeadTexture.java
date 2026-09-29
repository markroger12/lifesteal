package com.example.lifecore.item;

import com.example.lifecore.util.compat.Compat;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A custom player-head skin, identified by its texture hash on {@code textures.minecraft.net}.
 *
 * <p>Accepts whatever head websites hand out: the base64 texture value, the texture URL, the bare
 * hash, or a whole give command containing one of those.
 *
 * <p>Every head built from the same texture carries an identical profile (fixed id, name and
 * texture property), so LifeCore items made at different times still stack.
 */
public record HeadTexture(String hash) {

    private static final String URL_PREFIX = "http://textures.minecraft.net/texture/";
    private static final String PROFILE_NAME = "LifeCore";
    private static final Pattern URL_PATTERN =
            Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,128})");
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{16,128}");
    private static final Pattern BASE64_PATTERN = Pattern.compile("[A-Za-z0-9+/_-]{20,}={0,2}");
    private static final boolean PAPER_PROFILES = paperProfiles();

    /**
     * Extracts a texture from user input.
     *
     * @return empty if the input is blank or contains no recognisable texture
     */
    public static Optional<HeadTexture> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String text = input.trim();
        Optional<HeadTexture> fromUrl = fromUrl(text);
        if (fromUrl.isPresent()) {
            return fromUrl;
        }
        if (HASH_PATTERN.matcher(text).matches()) {
            return Optional.of(new HeadTexture(text.toLowerCase(Locale.ROOT)));
        }
        Matcher candidates = BASE64_PATTERN.matcher(text);
        while (candidates.find()) {
            Optional<String> decoded = decode(candidates.group());
            if (decoded.isPresent()) {
                Optional<HeadTexture> texture = fromUrl(decoded.get());
                if (texture.isPresent()) {
                    return texture;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<HeadTexture> fromUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(new HeadTexture(matcher.group(1).toLowerCase(Locale.ROOT))) : Optional.empty();
    }

    private static Optional<String> decode(String base64) {
        for (Base64.Decoder decoder : new Base64.Decoder[]{Base64.getDecoder(), Base64.getUrlDecoder()}) {
            try {
                return Optional.of(new String(decoder.decode(base64), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException ignored) {
                // try the next alphabet
            }
        }
        return Optional.empty();
    }

    public String url() {
        return URL_PREFIX + hash;
    }

    /** The base64 {@code textures} profile property for this skin. */
    public String value() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url() + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Stable profile id, so heads with the same texture are identical. */
    public UUID profileId() {
        return UUID.nameUUIDFromBytes(("LifeCore:head:" + hash).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Applies the skin to a head.
     *
     * @return false if the server rejected the profile (the meta is then left unchanged)
     */
    public boolean apply(SkullMeta meta) {
        try {
            if (PAPER_PROFILES) {
                PaperProfiles.apply(meta, profileId(), value());
            } else {
                meta.setOwnerProfile(spigotProfile());
            }
            return true;
        } catch (RuntimeException | LinkageError ex) {
            return false;
        }
    }

    /** Spigot encodes the skin into the same property as {@link #value()}, so heads stay identical. */
    private PlayerProfile spigotProfile() {
        PlayerProfile profile = Bukkit.createPlayerProfile(profileId(), PROFILE_NAME);
        PlayerTextures textures = profile.getTextures();
        try {
            textures.setSkin(URI.create(url()).toURL());
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("invalid texture URL " + url(), ex);
        }
        profile.setTextures(textures);
        return profile;
    }

    private static boolean paperProfiles() {
        if (!Compat.classExists("com.destroystokyo.paper.profile.ProfileProperty")) {
            return false;
        }
        try {
            SkullMeta.class.getMethod("setPlayerProfile", Class.forName("com.destroystokyo.paper.profile.PlayerProfile"));
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    /** Paper-only API, kept in its own class so Spigot never loads it. */
    private static final class PaperProfiles {
        private PaperProfiles() {
        }

        static void apply(SkullMeta meta, UUID id, String value) {
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(id, PROFILE_NAME);
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", value));
            meta.setPlayerProfile(profile);
        }
    }
}
