package com.example.lifecore.item;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadTextureTest {

    private static final String HASH = "5a9c0f3e7b21d4c8a6e0f1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f6";
    private static final String URL = "http://textures.minecraft.net/texture/" + HASH;

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String parsedHash(String input) {
        return HeadTexture.parse(input).orElseThrow(() -> new AssertionError("not parsed: " + input)).hash();
    }

    @Test
    void acceptsTextureUrls() {
        assertEquals(HASH, parsedHash(URL));
        assertEquals(HASH, parsedHash("https://textures.minecraft.net/texture/" + HASH));
        assertEquals(HASH, parsedHash("  " + URL + "  "));
    }

    @Test
    void acceptsBareHashInAnyCase() {
        assertEquals(HASH, parsedHash(HASH));
        assertEquals(HASH, parsedHash(HASH.toUpperCase()));
    }

    @Test
    void acceptsBase64Values() {
        String simple = base64("{\"textures\":{\"SKIN\":{\"url\":\"" + URL + "\"}}}");
        assertEquals(HASH, parsedHash(simple));
        assertEquals(HASH, parsedHash(simple.replace("=", "")), "padding is optional");
        String detailed = base64("{\"timestamp\":1700000000000,\"profileId\":\"0123456789abcdef0123456789abcdef\","
                + "\"profileName\":\"Someone\",\"textures\":{\"SKIN\":{\"url\":\"" + URL + "\"}}}");
        assertEquals(HASH, parsedHash(detailed));
    }

    @Test
    void acceptsGiveCommands() {
        String value = base64("{\"textures\":{\"SKIN\":{\"url\":\"" + URL + "\"}}}");
        String modern = "/give @p minecraft:player_head[minecraft:profile={properties:[{name:\"textures\",value:\""
                + value + "\"}]}] 1";
        String legacy = "/give @p minecraft:player_head{SkullOwner:{Id:[I;1,2,3,4],Properties:{textures:[{Value:\""
                + value + "\"}]}}} 1";
        assertEquals(HASH, parsedHash(modern));
        assertEquals(HASH, parsedHash(legacy));
    }

    @Test
    void rejectsInputWithoutATexture() {
        assertTrue(HeadTexture.parse(null).isEmpty());
        assertTrue(HeadTexture.parse("").isEmpty());
        assertTrue(HeadTexture.parse("   ").isEmpty());
        assertTrue(HeadTexture.parse("a heart please").isEmpty());
        assertTrue(HeadTexture.parse("https://example.com/texture/" + HASH).isEmpty(), "only Mojang's texture host");
        assertTrue(HeadTexture.parse(base64("{\"textures\":{}}")).isEmpty());
        assertTrue(HeadTexture.parse("abc123").isEmpty(), "too short to be a hash");
    }

    @Test
    void producesAStableProfile() {
        HeadTexture head = new HeadTexture(HASH);
        assertEquals(URL, head.url());
        String decoded = new String(Base64.getDecoder().decode(head.value()), StandardCharsets.UTF_8);
        assertTrue(decoded.contains(URL));
        assertEquals(HASH, parsedHash(head.value()), "the generated value parses back");
        assertEquals(head.value(), new HeadTexture(HASH).value());
        assertEquals(head.profileId(), new HeadTexture(HASH).profileId());
        assertNotEquals(head.profileId(), new HeadTexture(HASH.replace('5', '6')).profileId());
    }
}
