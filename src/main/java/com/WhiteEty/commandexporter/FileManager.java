package com.WhiteEty.commandexporter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FileManager {

    public static Path resolveOutputPath(String serverAddress, String timestamp) throws IOException {
        Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
        // Меняем корневую папку на exported_commands по вашему ТЗ
        Path root = gameDir.resolve("exported_commands");
        Path serverDir = root.resolve(sanitize(serverAddress));
        Files.createDirectories(serverDir);
        // Меняем расширение на .txt
        return serverDir.resolve(timestamp + ".txt");
    }

    private static String sanitize(String address) {
        return address.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // Изменили String на List<String> для красивой построчной записи команд
    public static void write(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    public static String getServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        ServerData serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            return serverData.ip;
        }
        return "singleplayer";
    }
}
