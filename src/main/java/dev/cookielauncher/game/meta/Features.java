package dev.cookielauncher.game.meta;

import dev.cookielauncher.game.launch.LaunchRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Condition flags used when evaluating conditional arguments and rules.
 * Keys match the names Mojang uses in {@code arguments} entries.
 */
public final class Features {

    private final Map<String, Boolean> flags = new LinkedHashMap<>();

    public Features set(String name, boolean value) {
        flags.put(name, value);
        return this;
    }

    public boolean get(String name) {
        return flags.getOrDefault(name, false);
    }

    public static Features defaults() {
        return new Features()
                .set("is_demo_user", false)
                .set("has_custom_resolution", false)
                .set("has_quick_plays_support", false)
                .set("is_quick_play_singleplayer", false)
                .set("is_quick_play_multiplayer", false)
                .set("is_quick_play_realms", false);
    }

    /** Flags derived from a concrete launch request. */
    public static Features forRequest(LaunchRequest request) {
        boolean online = request.server != null && !request.server.isBlank();
        return new Features()
                .set("is_demo_user", request.demo)
                .set("has_custom_resolution", request.windowWidth > 0 && request.windowHeight > 0)
                .set("has_quick_plays_support", online)
                .set("is_quick_play_multiplayer", online)
                .set("is_quick_play_singleplayer", false)
                .set("is_quick_play_realms", false);
    }
}
