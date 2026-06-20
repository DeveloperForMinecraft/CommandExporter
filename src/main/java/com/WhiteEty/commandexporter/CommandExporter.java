package com.WhiteEty.commandexporter;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("commandexporter")
public class CommandExporter {
    public CommandExporter() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // Регистрируем обработчик событий на клиенте
        MinecraftForge.EVENT_BUS.register(new ClientCommandRegistry());
    }
}
