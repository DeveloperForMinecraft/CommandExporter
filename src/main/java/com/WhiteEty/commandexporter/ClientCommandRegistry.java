package com.WhiteEty.commandexporter;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.Collection;
import java.util.stream.Collectors;

public class ClientCommandRegistry {

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage().trim();
        
        // Проверяем, начинается ли сообщение с нашей базовой команды
        if (message.toLowerCase().startsWith("/exportcmds")) {
            // Отменяем отправку сообщения на сервер, чтобы он не выдавал ошибку в чат
            event.setCanceled(true);
            
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() == null || mc.player == null) return;

            // ПРИНУДИТЕЛЬНОЕ ДОБАВЛЕНИЕ В ИСТОРИЮ (СТРЕЛОЧКИ ЧАТА)
            // Так как мы отменили отправку, добавляем команду в историю чата клиента вручную
            try {
                if (mc.gui != null && mc.gui.getChat().getRecentChat() != null) {
                    mc.gui.getChat().getRecentChat().add(message);
                }
            } catch (Throwable ignored) {
                // Подстраховка на случай других названий полей в старых маппингах
            }

            // Вариант 1: Запуск сканирования через /exportcmds start
            if (message.equalsIgnoreCase("/exportcmds start")) {
                
                // Автоматически вытаскиваем из сетевого соединения ВСЕ доступные корневые команды сервера
                Collection<String> rootCommands = mc.getConnection().getCommands().getRoot().getChildren()
                        .stream()
                        .map(node -> node.getName())
                        .collect(Collectors.toList());
                
                // Запускаем наш оптимизированный асинхронный сканер
                AutoScanner.startScan(rootCommands);
                
            } 
            // Вариант 2: Экстренная остановка через /exportcmds stop
            else if (message.equalsIgnoreCase("/exportcmds stop")) {
                
                // Даем команду сканеру немедленно завершить работу и сохранить лог
                AutoScanner.stopScan();
                
            } 
            // Вариант 3: Если ввели просто /exportcmds или что-то не то — выводим красивую справку
            else {
                mc.player.displayClientMessage(new StringTextComponent("§6[Scanner] Использование мода:"), false);
                mc.player.displayClientMessage(new StringTextComponent("§e> /exportcmds start §7- Запустить сканирование веток"), false);
                mc.player.displayClientMessage(new StringTextComponent("§e> /exportcmds stop  §7- Экстренно остановить и сохранить данные"), false);
            }
        }
    }
}
