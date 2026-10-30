package me.foesio.foMobToggle.api.event;

import java.util.Objects;
import java.util.UUID;
import me.foesio.foMobToggle.model.ToggleCategory;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after an in-memory saved preference changes, before its delayed database write.
 * This non-cancellable synchronous event runs on the caller's player-owning context.
 * Permission changes, startup loads, blocked requests, and no-ops do not fire it.
 */
public final class MobTogglePreferenceChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    public enum Cause {
        GUI,
        API
    }

    private final UUID playerId;
    private final ToggleCategory category;
    private final boolean previousPreference;
    private final boolean newPreference;
    private final Cause cause;

    public MobTogglePreferenceChangedEvent(UUID playerId, ToggleCategory category,
                                           boolean previousPreference, boolean newPreference, Cause cause) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.category = Objects.requireNonNull(category, "category");
        this.previousPreference = previousPreference;
        this.newPreference = newPreference;
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ToggleCategory getCategory() {
        return category;
    }

    public boolean getPreviousPreference() {
        return previousPreference;
    }

    public boolean getNewPreference() {
        return newPreference;
    }

    public Cause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
