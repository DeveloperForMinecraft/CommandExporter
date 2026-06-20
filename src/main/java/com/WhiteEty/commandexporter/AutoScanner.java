package com.WhiteEty.commandexporter;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.play.NetworkPlayerInfo;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.StringTextComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AutoScanner {
    private static final Logger LOGGER = LogManager.getLogger("CommandExporter-Scanner");
    private static volatile boolean isRunning = false;
    private static Thread scannerThread = null;
    
    private static final Queue<String> scanQueue = new ConcurrentLinkedQueue<>();
    public static final Set<String> resultLog = new ConcurrentSkipListSet<>(); 
    private static final Set<String> submittedTasks = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger activeRequests = new AtomicInteger(0);
    
    private static final Set<String> onlinePlayersCache = ConcurrentHashMap.newKeySet();
    private static long lastPlayerCacheUpdate = 0;

    private static String currentQuery = "";

    public static void startScan(Collection<String> rootCommands) {
        if (isRunning) {
            printToChat("§c[Scanner] Сканер уже запущен! Используйте /exportcmds stop для остановки.");
            return;
        }
        
        if (scannerThread != null && scannerThread.isAlive()) {
            try {
                scannerThread.join(); 
            } catch (InterruptedException ignored) {}
        }
        
        ScannerConfig.load();
        
        isRunning = true;
        scanQueue.clear();
        resultLog.clear();
        submittedTasks.clear();
        activeRequests.set(0);
        onlinePlayersCache.clear();
        lastPlayerCacheUpdate = 0;

        for (String cmd : rootCommands) {
            String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
            cleanCmd = cleanCmd.trim();

            String[] split = cleanCmd.split(" ");
            String baseRoot = split.length > 0 ? split[0].toLowerCase() : "";
            
            if (ScannerConfig.ignoredCommands.contains(baseRoot)) {
                continue; 
            }
            
            scanQueue.add(cleanCmd);
            submittedTasks.add(cleanCmd.toLowerCase());
        }

        Minecraft mc = Minecraft.getInstance();
        printToChat("§6[Scanner] Старт сканера. Сборка логов полностью потокобезопасна.");

        scannerThread = new Thread(() -> {
            try {
                while (isRunning) {
                    currentQuery = scanQueue.poll();
                    if (currentQuery == null) break; 

                    if (mc.getConnection() == null || mc.player == null) break;

                    final String queryToSend = currentQuery + " "; 
                    
                    if (ScannerConfig.logVisible) {
                        printToChat("§7[TAB] Сканирование ветки: /" + queryToSend);
                    }

                    try {
                        @SuppressWarnings("unchecked")
                        CommandDispatcher<ISuggestionProvider> dispatcher = (CommandDispatcher<ISuggestionProvider>) (Object) mc.getConnection().getCommands();
                        ISuggestionProvider provider = mc.getConnection().getSuggestionsProvider();
                        
                        if (dispatcher != null && provider != null) {
                            activeRequests.incrementAndGet();
                            final String querySnapshot = currentQuery;

                            dispatcher.getCompletionSuggestions(dispatcher.parse(queryToSend, provider))
                                    .thenAccept(suggestions -> {
                                        try {
                                            if (suggestions != null && suggestions.getList() != null && !suggestions.getList().isEmpty()) {
                                                List<String> list = suggestions.getList().stream()
                                                        .map(s -> s.getText())
                                                        .collect(Collectors.toList());
                                                onSuggestionsReceived(querySnapshot, list);
                                            }
                                        } catch (Exception e) {
                                            LOGGER.error("Ошибка при обработке полученных подсказок Brigadier", e);
                                        } finally {
                                            activeRequests.decrementAndGet();
                                        }
                                    }).exceptionally(ex -> {
                                        activeRequests.decrementAndGet();
                                        return null;
                                    });
                        }
                    } catch (Exception ignored) {}

                    Thread.sleep(ScannerConfig.delayMs);
                }
            } catch (InterruptedException e) {
                printToChat("§e[Scanner] Поток сканирования прерван.");
            } catch (Exception e) {
                LOGGER.error("Критический сбой в фоновом потоке сканера веток", e);
            } finally {
                int timeout = 0;
                while (activeRequests.get() > 0 && timeout < 50) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    timeout++;
                }
                stopAndSave();
            }
        });
        
        scannerThread.start();
    }

    public static void stopScan() {
        if (!isRunning) {
            printToChat("§c[Scanner] Сканер сейчас не запущен.");
            return;
        }
        printToChat("§e[Scanner] Запрос на остановку. Завершаю активные пакеты...");
        isRunning = false;
        if (scannerThread != null && scannerThread.isAlive()) {
            scannerThread.interrupt(); 
        }
    }

    public static void onSuggestionsReceived(String checkedQuery, List<String> suggestions) {
        if (!isRunning) return;

        Minecraft mc = Minecraft.getInstance();
        int originalSize = suggestions.size();
        List<String> processedSuggestions = suggestions;

        if (originalSize > ScannerConfig.maxSuggestionsPerCommand) {
            processedSuggestions = suggestions.subList(0, ScannerConfig.maxSuggestionsPerCommand);
            printToChat("§c[Внимание] Вариантов: " + originalSize + " (Обрезано до " + ScannerConfig.maxSuggestionsPerCommand + ") для /" + checkedQuery.trim());
        } else {
            if (ScannerConfig.logVisible) {
                printToChat("§b[Ответ] Найдено вариантов: " + originalSize + " для /" + checkedQuery.trim());
            }
        }

        long now = System.currentTimeMillis();
        if (ScannerConfig.ignoreOnlinePlayers && mc.getConnection() != null && (now - lastPlayerCacheUpdate > 5000)) {
            onlinePlayersCache.clear();
            for (NetworkPlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getName() != null) {
                    onlinePlayersCache.add(info.getProfile().getName().toLowerCase());
                }
            }
            lastPlayerCacheUpdate = now;
        }

        for (String suggestion : processedSuggestions) {
            String trimmedSug = suggestion.trim();
            
            if (trimmedSug.isEmpty() || trimmedSug.startsWith("<") || trimmedSug.startsWith("[")) {
                resultLog.add("/" + checkedQuery.trim());
                continue;
            }

            if (ScannerConfig.ignoreOnlinePlayers && onlinePlayersCache.contains(trimmedSug.toLowerCase())) {
                continue;
            }

            String[] words = checkedQuery.split(" ");
            boolean isDuplicate = false;
            for (String word : words) {
                if (trimmedSug.equalsIgnoreCase(word.trim())) {
                    isDuplicate = true;
                    break;
                }
            }
            
            if (isDuplicate) {
                resultLog.add(("/" + checkedQuery.trim() + " " + trimmedSug).trim());
                continue; 
            }

            String fullPath = ("/" + checkedQuery.trim() + " " + trimmedSug).trim();
            String cleanPathForCheck = fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
            
            // ОПТИМИЗАЦИЯ ( contains + add заменены на одно атомарное условие .add() )
            if (resultLog.add(fullPath)) {
                
                if (ScannerConfig.ignoredCommands.contains(cleanPathForCheck.toLowerCase().trim())) {
                    continue; 
                }

                int currentSpaces = 0;
                for (int i = 0; i < fullPath.length(); i++) {
                    if (fullPath.charAt(i) == ' ') currentSpaces++;
                }

                if (ScannerConfig.depth && currentSpaces >= ScannerConfig.maxDepth) {
                    continue;
                }

                String queueKey = cleanPathForCheck.toLowerCase();
                // ОПТИМИЗАЦИЯ ( contains + add заменены на атомарный .add() )
                if (submittedTasks.add(queueKey)) {
                    scanQueue.add(cleanPathForCheck);
                }
            }
        }
    }

    public static void stopAndSave() {
        if (!resultLog.isEmpty() || isRunning) {
            isRunning = false;
            List<String> sortedData = new ArrayList<>(resultLog);
            resultLog.clear();
            submittedTasks.clear();
            onlinePlayersCache.clear();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date());
            String serverAddress = FileManager.getServerAddress();

            try {
                Path outputPath = FileManager.resolveOutputPath(serverAddress, "infinite_audit_" + timestamp);
                FileManager.write(outputPath, sortedData);
                printToChat("§a[Scanner] Все данные сохранены! Найдено путей: " + sortedData.size());
            } catch (IOException e) {
                LOGGER.error("Не удалось сохранить итоговый .txt файл лога сканирования", e);
            }
        }
    }

    private static void printToChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.execute(() -> mc.player.displayClientMessage(new StringTextComponent(message), false));
        }
    }
}