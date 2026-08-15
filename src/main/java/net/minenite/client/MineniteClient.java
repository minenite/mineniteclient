package net.minenite.client;

import net.minenite.client.foliage.FoliageBlocks;
import net.minenite.client.gun.GunBackSlingConfig;
import net.minenite.client.gun.LaserNet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "mineniteclient", dist = Dist.CLIENT)
public class MineniteClient {
    public MineniteClient(IEventBus modBus) {
        GunBackSlingConfig.INSTANCE.load();
        modBus.addListener(LaserNet::register);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(LaserNet.class);
        NeoForge.EVENT_BUS.register(net.minenite.client.gun.LaserBeamRenderer.class);
        NeoForge.EVENT_BUS.register(net.minenite.client.gun.GlassCrackRenderer.class);
    }

    @SubscribeEvent
    public void onBlockOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (FoliageBlocks.isFoliage(event.getBlockState())) {
            event.setCanceled(true);
        }
    }
}
