package com.example.dynamicsurrounding.mixin;

import com.example.dynamicsurrounding.ReverbManager;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into every raw OpenAL source Minecraft creates so we can route it
 * through our cave reverb auxiliary effect slot while reverb is enabled.
 *
 * The private "pointer" field is the raw OpenAL source id (see Yarn mappings
 * for net.minecraft.client.sound.Source, 1.21.5).
 */
@Mixin(Source.class)
public abstract class ChannelMixin {

    @Shadow
    @Final
    private int pointer;

    @Inject(method = "play", at = @At("HEAD"))
    private void dynamicsurrounding$onPlay(CallbackInfo ci) {
        ReverbManager.onSourcePlay(this.pointer);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicsurrounding$onClose(CallbackInfo ci) {
        ReverbManager.onSourceClose(this.pointer);
    }
}
