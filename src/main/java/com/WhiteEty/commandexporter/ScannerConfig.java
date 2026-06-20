package com.WhiteEty.commandexporter;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ScannerConfig {
    // Внедряем официальный логгер Forge / Log4J
    private static final Logger LOGGER = LogManager.getLogger("CommandExporter-Config");

    public static int delayMs = 50;
    public static int maxSuggestionsPerCommand = 500;
    public static boolean logVisible = true;
    public static boolean depth = true;
    public static int maxDepth = 5;
    public static boolean ignoreOnlinePlayers = false;
    public static List<String> ignoredCommands = new ArrayList<>();

    private static Path getConfigPath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("exported_commands").resolve("commandexporter_scanner.yml");
    }

    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            saveDefault();
            return;
        }

        try {
            List<String> lines = Files.readAllLines(path);
            ignoredCommands.clear();
            boolean readingIgnored = false;

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                if (line.equals("[IgnoredCommands]")) {
                    readingIgnored = true;
                    continue;
                }

                if (readingIgnored) {
                    String cleanCmd = line.startsWith("/") ? line.substring(1) : line;
                    ignoredCommands.add(cleanCmd.toLowerCase().trim());
                    continue;
                }

                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length < 2) continue;
                    
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    if (key.equalsIgnoreCase("delayMs")) {
                        delayMs = Integer.parseInt(value);
                    } else if (key.equalsIgnoreCase("maxSuggestionsPerCommand")) {
                        maxSuggestionsPerCommand = Integer.parseInt(value);
                    } else if (key.equalsIgnoreCase("logVisible")) {
                        logVisible = Boolean.parseBoolean(value);
                    } else if (key.equalsIgnoreCase("Depth")) {
                        depth = Boolean.parseBoolean(value);
                    } else if (key.equalsIgnoreCase("maxDepth")) {
                        maxDepth = Integer.parseInt(value);
                    } else if (key.equalsIgnoreCase("ignoreOnlinePlayers")) {
                        ignoreOnlinePlayers = Boolean.parseBoolean(value);
                    }
                }
            }
        } catch (Exception e) {
            // Красивый и правильный вывод ошибки в логи Forge
            LOGGER.error("Не удалось прочитать файл конфигурации сканера", e);
        }
    }

    private static void saveDefault() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = new ArrayList<>();
            lines.add("# Конфигурация асинхронного бесконечного сканера TAB-Complete");
            lines.add("delayMs=50");
            lines.add("# Ограничение по количеству аргументов. В случае не надобности выставите 9999999.");
            lines.add("maxSuggestionsPerCommand=500");
            lines.add("\n# Отображать ли процесс сканирования веток в чате (true/false)");
            lines.add("logVisible=true");
            lines.add("\n# Ограничивать ли глубину вложенности аргументов (количество пробелов в команде)");
            lines.add("Depth=true");
            lines.add("maxDepth=5");
            lines.add("\n# Игнорировать подсказки, похожие на ники игроков");
            lines.add("ignoreOnlinePlayers=false");
            lines.add("\n# Список команд или конкретных аргументов, которые НЕ нужно углублять.");
            lines.add("# Можно писать целые связки, например: clan locale");
            lines.add("[IgnoredCommands]");
            lines.add("recipe");
            lines.add("recipes");
            lines.add("dura");
            lines.add("msg");
            lines.add("tell");
            lines.add("clan locale");

            Files.write(path, lines);
            ignoredCommands.add("recipe");
            ignoredCommands.add("recipes");
            ignoredCommands.add("dura");
            ignoredCommands.add("msg");
            ignoredCommands.add("tell");
            ignoredCommands.add("clan locale");
        } catch (IOException e) {
            LOGGER.error("Не удалось сохранить дефолтный конфиг сканера", e);
        }
    }
}
