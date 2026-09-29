package com.example.dynamicsurrounding;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.LightType;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic Surrounding
 *
 * Detects whether the player is currently underground / inside a cave and
 * toggles a global OpenAL EFX reverb effect on ambient sound sources so that
 * everything the player hears while underground gets a cave-like echo.
 */
public class DynamicSurroundingClient implements ClientModInitializer {
    public static final String MOD_ID = "dynamicsurrounding";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // How many straight ticks the state must be stable before switching,
    // so reverb doesn't flicker on/off right at a cave entrance.
    private static final int SWITCH_DELAY_TICKS = 10;

    private boolean caveState = false;
    private boolean pendingState = false;
    private int pendingTicks = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Dynamic Surrounding initializing - cave reverb enabled");
        ReverbManager.init();

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        boolean inCave = isPlayerInCave(client, player);

        if (inCave == pendingState) {
            pendingTicks++;
        } else {
            pendingState = inCave;
            pendingTicks = 0;
        }

        if (pendingTicks >= SWITCH_DELAY_TICKS && caveState != pendingState) {
            caveState = pendingState;
            ReverbManager.setEnabled(caveState);
            LOGGER.debug("Cave reverb {}", caveState ? "ENABLED" : "disabled");
        }
    }

    /**
     * Heuristic cave detection:
     *  - the player must have no direct sky access (sky light == 0 at their position)
     *  - and must be below a rough "surface" threshold, OR simply have a solid
     *    ceiling above them within a short distance (covers overhangs/mesa arches
     *    that are technically above sea level but still enclosed).
     */
    private boolean isPlayerInCave(MinecraftClient client, ClientPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        var world = client.world;
        if (world == null) return false;

        int skyLight = world.getLightLevel(LightType.SKY, pos);
        if (skyLight > 0) {
            // Can still see some sky - not enclosed.
            return false;
        }

        // No sky light at all. Treat as a cave if we are below the world's
        // logical "surface" (use sea level as a generous cutoff) or if there
        // is a solid block ceiling close overhead.
        int seaLevel = world.getSeaLevel();
        if (pos.getY() < seaLevel) {
            return true;
        }

        return hasCeilingWithin(world, pos, 8);
    }

    private boolean hasCeilingWithin(net.minecraft.world.World world, BlockPos pos, int maxDistance) {
        BlockPos.Mutable mutable = pos.mutableCopy();
        for (int i = 1; i <= maxDistance; i++) {
            mutable.setY(pos.getY() + i);
            if (!world.getBlockState(mutable).isAir()) {
                return true;
            }
        }
        return false;
    }
}
