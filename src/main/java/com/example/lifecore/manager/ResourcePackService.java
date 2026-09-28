package com.example.lifecore.manager;

import com.example.lifecore.LifeCorePlugin;
import com.example.lifecore.configuration.settings.LifeCoreSettings;
import com.example.lifecore.util.text.Placeholders;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exports the bundled resource pack, calculates its SHA-1, optionally hosts it with a tiny
 * read-only HTTP server and sends it to players.
 */
public final class ResourcePackService {

    private final LifeCorePlugin plugin;
    private final String fileName;
    private volatile String url = "";
    private volatile byte[] sha1;
    private HttpServer server;
    private ExecutorService serverExecutor;

    public ResourcePackService(LifeCorePlugin plugin) {
        this.plugin = plugin;
        this.fileName = plugin.getName() + "-ResourcePack.zip";
    }

    public File packFile() {
        return new File(new File(plugin.getDataFolder(), "resourcepack"), fileName);
    }

    public void start() {
        stop();
        LifeCoreSettings.ResourcePack config = plugin.settings().resourcePack;
        File file = packFile();
        if (config.exportOnStartup()) {
            export(file);
        }
        String configuredSha = config.sha1();
        if (!configuredSha.isEmpty()) {
            sha1 = HexFormat.of().parseHex(configuredSha.toLowerCase(java.util.Locale.ROOT));
        } else if (file.exists()) {
            sha1 = digest(file);
        } else {
            sha1 = null;
        }
        if (!config.url().isEmpty()) {
            url = config.url();
        } else if (config.hostEnabled() && file.exists()) {
            startServer(file, config);
        } else {
            url = "";
        }
        if (sha1 != null && file.exists()) {
            plugin.log().info("[resource-pack] " + fileName + " SHA-1: " + HexFormat.of().formatHex(sha1));
        }
    }

    private void export(File target) {
        try (InputStream in = plugin.getResource("resourcepack/" + fileName)) {
            if (in == null) {
                return;
            }
            File parent = target.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create " + parent);
            }
            File temp = new File(parent, fileName + ".tmp");
            Files.copy(in, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
            byte[] bundled = digest(temp);
            if (target.exists() && java.util.Arrays.equals(bundled, digest(target))) {
                Files.deleteIfExists(temp.toPath());
                return;
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.log().info("[resource-pack] Exported " + target.getPath());
        } catch (IOException ex) {
            plugin.log().warn("[resource-pack] Could not export the resource pack: " + ex.getMessage());
        }
    }

    private void startServer(File file, LifeCoreSettings.ResourcePack config) {
        try {
            server = HttpServer.create(new InetSocketAddress(config.hostPort()), 16);
            serverExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "LifeCore-PackHost");
                thread.setDaemon(true);
                return thread;
            });
            String path = "/" + fileName;
            server.createContext("/", exchange -> {
                try (exchange) {
                    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod()) || !path.equals(exchange.getRequestURI().getPath())) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    byte[] body = Files.readAllBytes(file.toPath());
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                }
            });
            server.setExecutor(serverExecutor);
            server.start();
            url = "http://" + config.publicAddress() + ":" + config.hostPort() + path;
            plugin.log().info("[resource-pack] Hosting the resource pack at " + url);
        } catch (IOException ex) {
            plugin.log().warn("[resource-pack] Could not start the pack host on port " + config.hostPort() + ": " + ex.getMessage());
            url = "";
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }
    }

    /** Sends the pack. @return false if no URL is configured. */
    public boolean send(Player player) {
        if (url.isEmpty()) {
            return false;
        }
        String prompt = plugin.messages().text(player, "resource-pack.prompt", Placeholders.EMPTY);
        player.setResourcePack(url, sha1, prompt, plugin.settings().resourcePack.required());
        return true;
    }

    private static byte[] digest(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return digest.digest();
        } catch (IOException | NoSuchAlgorithmException ex) {
            return null;
        }
    }
}
