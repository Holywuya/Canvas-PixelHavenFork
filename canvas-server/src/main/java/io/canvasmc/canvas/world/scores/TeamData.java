package io.canvasmc.canvas.world.scores;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class TeamData {
    private boolean allowFriendlyFire = true;
    private boolean seeFriendlyInvisibles = true;
    private Component displayName;
    private Component playerPrefix = CommonComponents.EMPTY;
    private Component playerSuffix = CommonComponents.EMPTY;
    private Team.Visibility nameTagVisibility = Team.Visibility.ALWAYS;
    private Team.Visibility deathMessageVisibility = Team.Visibility.ALWAYS;
    private ChatFormatting color = ChatFormatting.RESET;
    private Team.CollisionRule collisionRule = Team.CollisionRule.ALWAYS;

    @Contract("_ -> new")
    public static @NonNull TeamData copyOf(final @NonNull TeamData toCopy) {
        return new TeamData(
            toCopy.isAllowFriendlyFire(),
            toCopy.isSeeFriendlyInvisibles(),
            toCopy.getDisplayName(),
            toCopy.getPlayerPrefix(),
            toCopy.getPlayerSuffix(),
            toCopy.getNameTagVisibility(),
            toCopy.getDeathMessageVisibility(),
            toCopy.getColor(),
            toCopy.getCollisionRule()
        );
    }

    public TeamData(
        final boolean allowFriendlyFire,
        final boolean seeFriendlyInvisibles,
        final Component displayName,
        final Component playerPrefix,
        final Component playerSuffix,
        final Team.Visibility nameTagVisibility,
        final Team.Visibility deathMessageVisibility,
        final ChatFormatting color,
        final Team.CollisionRule collisionRule
    ) {
        this.allowFriendlyFire = allowFriendlyFire;
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        this.displayName = displayName;
        this.playerPrefix = playerPrefix;
        this.playerSuffix = playerSuffix;
        this.nameTagVisibility = nameTagVisibility;
        this.deathMessageVisibility = deathMessageVisibility;
        this.color = color;
        this.collisionRule = collisionRule;
    }

    public TeamData() {
        // no-op
    }

    public boolean isAllowFriendlyFire() {
        return allowFriendlyFire;
    }

    public TeamData setAllowFriendlyFire(final boolean allowFriendlyFire) {
        this.allowFriendlyFire = allowFriendlyFire;
        return this;
    }

    public boolean isSeeFriendlyInvisibles() {
        return seeFriendlyInvisibles;
    }

    public TeamData setSeeFriendlyInvisibles(final boolean seeFriendlyInvisibles) {
        this.seeFriendlyInvisibles = seeFriendlyInvisibles;
        return this;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public TeamData setDisplayName(final Component displayName) {
        this.displayName = displayName;
        return this;
    }

    public Component getPlayerPrefix() {
        return playerPrefix;
    }

    public TeamData setPlayerPrefix(final Component playerPrefix) {
        this.playerPrefix = playerPrefix;
        return this;
    }

    public Component getPlayerSuffix() {
        return playerSuffix;
    }

    public TeamData setPlayerSuffix(final Component playerSuffix) {
        this.playerSuffix = playerSuffix;
        return this;
    }

    public Team.Visibility getNameTagVisibility() {
        return nameTagVisibility;
    }

    public TeamData setNameTagVisibility(final Team.Visibility nameTagVisibility) {
        this.nameTagVisibility = nameTagVisibility;
        return this;
    }

    public Team.Visibility getDeathMessageVisibility() {
        return deathMessageVisibility;
    }

    public TeamData setDeathMessageVisibility(final Team.Visibility deathMessageVisibility) {
        this.deathMessageVisibility = deathMessageVisibility;
        return this;
    }

    public ChatFormatting getColor() {
        return color;
    }

    public TeamData setColor(final ChatFormatting color) {
        this.color = color;
        return this;
    }

    public Team.CollisionRule getCollisionRule() {
        return collisionRule;
    }

    public TeamData setCollisionRule(final Team.CollisionRule collisionRule) {
        this.collisionRule = collisionRule;
        return this;
    }

    public static TeamData fromTeam(final @NonNull PlayerTeam team) {
        return new TeamData(
            team.isAllowFriendlyFire(),
            team.canSeeFriendlyInvisibles(),
            team.getDisplayName(),
            team.getPlayerPrefix(),
            team.getPlayerSuffix(),
            team.getNameTagVisibility(),
            team.getDeathMessageVisibility(),
            team.getColor(),
            team.getCollisionRule()
        );
    }

    public void applyTo(final @NonNull PlayerTeam team) {
        team.setAllowFriendlyFire(this.allowFriendlyFire);
        team.setSeeFriendlyInvisibles(this.seeFriendlyInvisibles);
        team.setDisplayName(this.displayName);
        team.setPlayerPrefix(this.playerPrefix);
        team.setPlayerSuffix(this.playerSuffix);
        team.setNameTagVisibility(this.nameTagVisibility);
        team.setDeathMessageVisibility(this.deathMessageVisibility);
        team.setColor(this.color);
        team.setCollisionRule(this.collisionRule);
    }
}
