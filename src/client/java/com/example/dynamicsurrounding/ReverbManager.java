package com.example.dynamicsurrounding;

import org.lwjgl.openal.*;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns a single OpenAL EFX "cave" reverb effect + auxiliary effect slot and
 * knows how to attach/detach that slot from individual OpenAL sources
 * (Minecraft's {@code com.mojang.blaze3d.audio.Channel} sources).
 *
 * All OpenAL calls in this class must happen on the sound engine's thread
 * (the same thread that owns the current AL context) - in vanilla that is
 * whichever thread creates/plays the Channel, which is what our mixin hooks.
 */
public final class ReverbManager {
    private static boolean efxSupported = false;
    private static boolean checked = false;

    private static int effectSlot = 0;
    private static int reverbEffect = 0;

    private static volatile boolean enabled = false;

    // Sources we've attached the reverb slot to, so we can detach them again.
    private static final Set<Integer> activeSources = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ReverbManager() {}

    public static void init() {
        // Actual EFX object creation happens lazily on first use, since it
        // must occur on the thread holding the current AL context, which
        // isn't guaranteed to be this one.
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            // Detach reverb from everything currently playing.
            for (int source : activeSources) {
                detachFromSource(source);
            }
            activeSources.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Called from the mixin right before/after a Channel starts playing.
     * Attaches the cave reverb aux slot to the given raw OpenAL source id
     * if reverb is currently enabled.
     */
    public static void onSourcePlay(int sourceId) {
        if (!enabled) {
            return;
        }
        if (!ensureEfx()) {
            return;
        }
        attachToSource(sourceId);
    }

    /** Called from the mixin when a Channel is closed/destroyed. */
    public static void onSourceClose(int sourceId) {
        activeSources.remove(sourceId);
        // No need to explicitly clear the aux send - the source is being
        // deleted anyway.
    }

    private static boolean ensureEfx() {
        if (checked) {
            return efxSupported;
        }
        checked = true;
        try {
            long device = getCurrentDevice();
            if (device == 0L) {
                efxSupported = false;
                return false;
            }

            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            if (!alcCaps.ALC_EXT_EFX) {
                DynamicSurroundingClient.LOGGER.warn("Dynamic Surrounding: OpenAL device has no EFX support, reverb disabled.");
                efxSupported = false;
                return false;
            }

            effectSlot = EXTEfx.alGenAuxiliaryEffectSlots();
            reverbEffect = EXTEfx.alGenEffects();

            EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
            // Tuned for a fairly "roomy stone cave" feel.
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DENSITY, 1.0f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DIFFUSION, 1.0f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_GAIN, 0.32f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_GAINHF, 0.89f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DECAY_TIME, 3.5f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_DECAY_HFRATIO, 0.6f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, 0.5f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, 0.02f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_LATE_REVERB_GAIN, 1.2f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, 0.03f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_AIR_ABSORPTION_GAINHF, 0.99f);
            EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_REVERB_ROOM_ROLLOFF_FACTOR, 0.0f);

            EXTEfx.alAuxiliaryEffectSloti(effectSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);

            efxSupported = true;
            DynamicSurroundingClient.LOGGER.info("Dynamic Surrounding: EFX reverb ready (slot={}, effect={})", effectSlot, reverbEffect);
        } catch (Throwable t) {
            DynamicSurroundingClient.LOGGER.error("Dynamic Surrounding: failed to initialize OpenAL EFX reverb", t);
            efxSupported = false;
        }
        return efxSupported;
    }

    private static void attachToSource(int sourceId) {
        try {
            // Route the source's send 0 into our auxiliary reverb slot.
            // Filter = AL_FILTER_NULL (0): no extra dry filtering on the send.
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, effectSlot, 0, 0);
            activeSources.add(sourceId);
        } catch (Throwable t) {
            DynamicSurroundingClient.LOGGER.debug("Dynamic Surrounding: could not attach reverb to source {}", sourceId, t);
        }
    }

    private static void detachFromSource(int sourceId) {
        try {
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, 0, 0, 0);
        } catch (Throwable t) {
            // Source may already be gone - fine to ignore.
        }
    }

    private static long getCurrentDevice() {
        long context = ALC10.alcGetCurrentContext();
        if (context == 0L) {
            return 0L;
        }
        return ALC10.alcGetContextsDevice(context);
    }
}
