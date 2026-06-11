package io.canvasmc.canvas;

import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.util.CanonicalReference;
import io.papermc.paper.adventure.PaperAdventure;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldConfig extends Part {

    public static final String DEFAULT_TPSBAR_FORMAT =
        "<gradient:blue:aqua><b>TPS:</b></gradient> <tps>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>MSPT:</b></gradient> <mspt>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>Util:</b></gradient> <util>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>Players:</b></gradient> <players>";
    public static final String DEFAULT_RAMBAR_FORMAT =
        "<gradient:green:dark_green><b>RAM:</b></gradient> <used>/<xmx> <dark_gray>(</dark_gray><percent><dark_gray>%)</dark_gray>";

    private static final Logger LOGGER = LoggerFactory.getLogger("CanvasWorlds");

    private static final Path BASE_FILE = Path.of("config/canvas-worlds.yml").toAbsolutePath().normalize();

    private static final Object2ObjectOpenHashMap<ResourceKey<Level>, WorldConfig> WORLD_CONFIGS = new Object2ObjectOpenHashMap<>();
    private static WorldConfig DEFAULT;
    private static boolean initialized = false;

    public static @NonNull WorldConfig getDefaults() {
        if (DEFAULT == null) {
            // During early bootstrap (Blocks.<clinit>), we can't load the full config
            // because MinecraftServer isn't initialized yet. Return a bare default.
            DEFAULT = new WorldConfig();
        }
        return DEFAULT;
    }

    public static @NonNull WorldConfig forWorld(final @NonNull Level level) {
        if (level instanceof ServerLevel serverLevel) {
            final ResourceKey<Level> key = serverLevel.dimension();
            final WorldConfig config = WORLD_CONFIGS.get(key);
            if (config != null) return config;
        }
        // fall back to defaults
        return getDefaults();
    }

    public static void init() {
        if (!initialized) {
            initialized = true;
            GlobalConfiguration.getInstance(); // preload global
            reload();
            // fallback: if reload didn't set DEFAULT, create a bare instance
            if (DEFAULT == null) {
                DEFAULT = new WorldConfig();
            }
        }
    }

    public static void reload() {
        ConfigurationProvider.buildSolidConfiguration(
            BASE_FILE,
            WorldConfig::new,
            GlobalConfiguration.CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new world configuration option, \"{}\"", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("World configuration option \"{}\" no longer exists and is now removed.", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final WorldConfig instance) {
                    Validator.validateObject(instance);
                    DEFAULT = instance;
                }
            },
            Style.create()
                .literal("CanvasMC 世界默认配置文件").endLine()
                .blank()
                .wordWrap(
                    "这是 CanvasMC 按世界配置文件的默认值。",
                    "每个选项都可以在各维度文件夹中的 patch 变体中被覆盖。你可以",
                    "自由修改、添加或删除注释。"
                ).endLine()
                .blank()
                .wordWrap(
                    "你可以使用 \"/canvas reload\" 命令在运行时刷新此配置，但不建议在正式运行",
                    "期间执行此操作，因为这可能导致意外崩溃或非预期行为。"
                ).endLine()
                .blank()
                .wordWrap(
                    "此配置中所有选项的默认值都是为了上游兼容性而非性能优化而设定的。",
                    "你需要进行一些手动配置才能获得 Canvas 提供的部分性能提升。"
                ).endLine()
                .blank()
                .wordWrap(
                    "如果你对某些配置选项有疑问，请在我们的 Discord 中联系"
                ).endLine()
                .literal("https://canvasmc.io/discord")
                .compile(60)
        );

        // on reload, if the server started, we need to swap out the configs
        try {
            if (MinecraftServer.getServer() != null) {
                for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                    level.reloadCanvasConfig();
                }
            }
        } catch (Throwable ignored) {
            // server not started yet
        }
    }

    public static WorldConfig buildForLevel(final @NonNull ServerLevel level, final ResourceKey<Level> dimension) {
        // ensure base config file exists before building patch
        if (!java.nio.file.Files.exists(BASE_FILE)) {
            // First time: create the base config file
            init();
        } else if (!initialized) {
            init();
        }

        final WorldConfig[] result = new WorldConfig[1];

        ConfigurationProvider.buildPatchableConfiguration(
            MinecraftServer.getServer().storageSource.getDimensionPath(dimension)
                .resolve("canvas-patch.yml"),
            BASE_FILE,
            WorldConfig::new,
            instance -> {
                LOGGER.info("Loaded Canvas config patch for level {}", dimension.identifier());
                result[0] = instance;
                instance.onLoad(level);
                WORLD_CONFIGS.put(dimension, instance);
            },
            Style.create()
                .literal("世界 " + dimension.identifier() + " 的配置补丁文件").endLine()
                .blank()
                .wordWrap(
                    "此配置文件可用于覆盖 \"/config/canvas-worlds.yml\" 中",
                    "定义的默认配置值"
                ).endLine()
                .blank()
                .wordWrap(
                    "要覆盖其中的值，只需复制相同的选项路径并覆盖其值。将此文件中的值",
                    "视为专属于当前世界的默认值替换即可"
                )
                .compile(60)
        );

        return result[0];
    }

    private void onLoad(final @NonNull ServerLevel level) {
        Validator.validateObject(this);

        final EntityType<?>[] entityTypes = entities.projectiles.loadChunks.stream()
            .map(Identifier::parse)
            .map(BuiltInRegistries.ENTITY_TYPE::getValue)
            .toList().toArray(new EntityType<?>[0]);

        if (entityTypes.length > 0) {
            LOGGER.info("Set {} projectile types to load chunks in {}", entityTypes.length, level.dimension().identifier().toDebugFileName());
        }

        entities.projectiles.compiledPredicate.setValue((projectile) -> {
            for (final EntityType<?> entityType : entityTypes) {
                if (projectile.getType() == entityType) return true;
            }
            return false;
        });

        if (blocks.spawner.minSpawnDelay > blocks.spawner.maxSpawnDelay) {
            throw new IllegalArgumentException("min-spawn-delay must be less than or equal to max spawn delay");
        }
    }

    {
        option("regionBars").docs("区域资源条配置。你可以使用 \"/regionbar\" 命令为玩家切换这些资源条");
    }

    public RegionBars regionBars = new RegionBars();
    public static class RegionBars extends Part {

        {
            option("enableTpsBar").docs("启用 Canvas 的区域化 TPS 条实现。");
            option("tpsBarFormat")
                .docs(
                    "TPS 条的 MiniMessage 格式行。占位符为 <tps>、<mspt>、<util> 和 <players>。",
                    "旧版标记（%tps%、%mspt%、%util%、%players%）也可使用，会自动转换。"
                ).greedyString();
            option("enableRamBar").docs("启用 Canvas 的区域化 RAM 条实现。");
            option("ramBarFormat")
                .docs(
                    "RAM 条的 MiniMessage 格式行。占位符为 <used>、<xmx>、<percent>。",
                    "旧版标记（%used%、%xmx%、%percent%）也可使用，会自动转换。"
                ).greedyString();
        }

        public boolean enableTpsBar = true;
        public String tpsBarFormat = DEFAULT_TPSBAR_FORMAT;

        public boolean enableRamBar = true;
        public String ramBarFormat = DEFAULT_RAMBAR_FORMAT;
    }

    public Visuals visuals = new Visuals();
    public static class Visuals extends Part {

        {
            option("particles")
                .docs(
                    "除非另有明确说明，所有选项均为发送给客户端的不必要数据包，",
                    "可以安全禁用而不会偏离原版行为"
                );
        }

        public boolean hideFlamesOnEntitiesWithFireResistance = false;
        public boolean hideFlamesOnEntitiesWithInvisibility = false;

        public Particles particles = new Particles();
        public static class Particles extends Part {

            {
                option("disableFallParticles").docs("注意：启用此选项会破坏原版视觉兼容性");
                option("disableNewCombatParticles").docs("注意：启用此选项会破坏原版视觉兼容性");
            }

            public boolean disableSprintParticles = false;
            public boolean disableFallParticles = false;
            public boolean disableDeathParticles = false;
            public boolean disableEffectParticles = false;
            public boolean disableWaterSplashParticles = false;
            public boolean disableBubbleColumnParticles = false;
            public boolean disableNewCombatParticles = false;
        }

        {
            option("dontTrackPlayersInEntityTracking").docs("启用后，玩家将无法在此世界中看到其他玩家");
        }

        public boolean dontTrackPlayersInEntityTracking = false;
    }

    {
        option("chainEndCrystalExplosions").docs("启用后，末地水晶爆炸将被串联执行，而非在同一 tick 内全部执行");
        option("disableSnowLightChecks").docs("禁用雪层光照检查，使雪层永不融化");
        option("disableGrassLightChecks").docs("禁用草方块光照检查，使草方块即使在黑暗中也会蔓延");
    }

    public boolean chainEndCrystalExplosions = false;
    public boolean disableSnowLightChecks = false;
    public boolean disableGrassLightChecks = false;

    public Farming farming = new Farming();
    public static class Farming extends Part {

        {
            option("disableFarmlandTrampling").docs("使掉落不会将耕地踩回泥土");
            option("cropsIgnoreLightCheck").docs("使作物种植时忽略阳光需求");
        }

        public boolean farmlandAlwaysMoist = false;
        public boolean disableLeafDecay = false;
        public boolean cropsIgnoreLightCheck = false;
        public boolean disableFarmlandTrampling = false;
        public boolean sugarCaneBonemealable = false;
        public boolean netherWartBonemealable = false;
    }

    public Entities entities = new Entities();
    public static class Entities extends Part {

        {
            option("fastOrbs")
                .docs(
                    "移除经验球拾取延迟并使用更快的合并系统。对密集经验农场非常有用。",
                    "此选项改变了经验球的合并方式，允许单个经验球包含无限量的合并经验",
                    "并立即被收集，比原版快得多。此选项还修复了 \"幽灵经验球\" 问题，",
                    "因为系统改为增加经验球的价值而非增加数量"
                );

            option("entityCollisionMode")
                .docs(
                    Style.wrap("服务器的实体碰撞模式")
                        .defineEnum(EntityCollisionMode.class, (mode) -> {
                            return switch (mode) {
                                case VANILLA -> "默认，所有实体都有碰撞";
                                case ONLY_PUSHABLE_PLAYERS_SMALL ->
                                    "仅玩家可被实体推动，搜索范围较小";
                                case ONLY_PUSHABLE_PLAYERS_LARGE ->
                                    "仅玩家可被实体推动，搜索范围为正常半径";
                                case NO_COLLISIONS -> "完全禁用实体碰撞";
                            };
                        })
                );
        }

        public boolean fastOrbs = false;

        public ItemEntities itemEntities = new ItemEntities();
        public static class ItemEntities extends Part {

            {
                option("itemEntityVelocityOnDeathFactor")
                    .docs(
                        "物品实体死亡掉落时速度的倍增系数。值越小，扩散范围越小；",
                        "值越大，扩散范围越大"
                    ).greaterThanOrEqualTo(0.0F);
                option("itemEntitiesWaitTwoSecondsForMergeCheckAlways")
                    .docs(
                        "物品实体在 tick 期间的合并检查间隔通常为 2 秒（除非物品正在移动，",
                        "此时间隔为 2 tick）。此选项强制将间隔始终设为 2 tick，",
                        "减少物品实体检查合并的次数"
                    );
            }

            public boolean itemEntitiesImmuneToExplosions = false;
            public boolean itemEntitiesImmuneToLightning = false;
            public double itemEntityVelocityOnDeathFactor = 1.0D;
            public boolean itemEntitiesWaitTwoSecondsForMergeCheckAlways = false;
        }

        public EntityCollisionMode entityCollisionMode = EntityCollisionMode.VANILLA;
        public enum EntityCollisionMode {
            VANILLA,
            ONLY_PUSHABLE_PLAYERS_LARGE,
            ONLY_PUSHABLE_PLAYERS_SMALL,
            NO_COLLISIONS;

            private static final EntityCollisionMode[] VALUES = values();
            private final int id;

            EntityCollisionMode() {
                this.id = ordinal();
            }

            public static EntityCollisionMode fromOrdinal(int ordinal) {
                if (ordinal < 0 || ordinal >= VALUES.length) {
                    return VANILLA;
                }
                return VALUES[ordinal];
            }

            public int getId() {
                return this.id;
            }

            public boolean onlyPlayersPushable() {
                return id == ONLY_PUSHABLE_PLAYERS_LARGE.id || id == ONLY_PUSHABLE_PLAYERS_SMALL.id;
            }

            public boolean allEntitiesCanBePushed() {
                return id == VANILLA.id;
            }

            public boolean noCollisions() {
                return id == NO_COLLISIONS.id;
            }

            public boolean isLargePushRange() {
                return id == ONLY_PUSHABLE_PLAYERS_LARGE.id;
            }
        }

        public Projectiles projectiles = new Projectiles();
        public static class Projectiles extends Part {

            {
                option("loadChunks").docs("指定哪些投射物在移动时应加载区块。仅在玩家投掷时有效");
                option("crossRegionRedirectableProjectileDeflection")
                    .docs(
                        Style.wrap(
                            "恢复原版中箭矢命中可重定向投射物（如风弹和火球）时的重定向行为，",
                            "支持跨区域线程生效。"
                        )
                        .blank()
                        .wordWrap(
                            "建议在 paper-world-defaults.yml 中设置 \"max-arrow-despawn-invulnerability: disabled\"",
                            "以防止箭矢消失"
                        )
                    );
            }

            public int maxProjectileChunkLoadsPerTick = 10;
            public int maxProjectileChunkLoadsPerProjectileBeforeRemoval = 10;
            public List<String> loadChunks = new ArrayList<>();
            public boolean crossRegionRedirectableProjectileDeflection = false;

            private final CanonicalReference<Predicate<Projectile>> compiledPredicate = new CanonicalReference<>();

            public Predicate<Projectile> getDoesProjectileLoadChunksOverridePredicate() {
                return compiledPredicate.value();
            }
        }

        {
            option("skeletonAimAccuracy").docs("定义骷髅弓箭射击的不精准度。14 为原版值，值越高越不精准，值越低越精准");
            option("villagers")
                .docs(
                    "村民相关选项。减少 POI 搜索范围的选项将搜索半径（以方块为单位）",
                    "从 48 缩小到 16，可以在几乎不偏离原版行为的情况下改善 tick 耗时，",
                    "但这会阻止村民获取 17-48 方块范围外的 POI"
                );
        }

        public double skeletonAimAccuracy = 14.0D;

        public Villagers villagers = new Villagers();
        public static class Villagers extends Part {

            {
                option("villagerAcquirePoiTasksLoadChunks")
                    .docs("是否允许村民为定位 POI 而加载未加载的区块");
            }

            {
                option("villagerSmartHibernation")
                    .docs(
                        "启用后，被固体方块完全包围且未在交易的村民",
                        "将跳过大脑 tick 和其他 AI 处理。",
                        "可显著降低拥有大量封闭村民的村民农场的 tick 耗时。"
                    );
            }

            public boolean villagerAcquirePoiTasksLoadChunks = true;
            public boolean reduceJobSitePoiSearchRange = false;
            public boolean reduceHomePoiSearchRange = false;
            public boolean reduceMeetingPointPoiSearchRange = false;
            public boolean villagerSmartHibernation = false;
        }

        public boolean experienceOrbsAreFireResistant = false; // Canvas - fire res orbs
    }

    public Combat combat = new Combat();
    public static class Combat extends Part {

        {
            option("restoreOldAttackDelayMechanics").docs("恢复 1.8 攻击延迟机制");
            option("imitateSwordBlocking").docs("恢复 1.8 剑格挡机制。可能不适用于 <1.21.4 的客户端");
        }

        public boolean restoreOldAttackDelayMechanics = false;
        public boolean imitateSwordBlocking = false;

        public Mace mace = new Mace();
        public static class Mace extends Part {

            {
                option("ignoreFallDistance").docs("移除锤的坠落距离增幅");
                option("fallDistanceLimit").docs("锤伤害加成中坠落距离缩放停止生效的阈值");
            }

            public boolean ignoreFallDistance = false;
            public double fallDistanceLimit = -1.0D;
        }

        {
            option("criticalHitMultiplier").docs("配置每次暴击的伤害倍率");
            option("removeRedDeathAnimation").docs("移除实体被击杀时的红色死亡动画");
            option("useLegacyBlastProtection").docs("恢复 1.21 之前的爆炸保护逻辑");
        }

        public boolean disableSweepingEdge = false;
        public boolean disableCritsWhileSprinting = false;
        public boolean allowFishingRodsToPullEntities = true;
        public float criticalHitMultiplier = 1.5F;
        public boolean removeRedDeathAnimation = false;
        public boolean useLegacyBlastProtection = false;
        public boolean snowballCanKnockbackPlayers = false;
        public boolean eggCanKnockbackPlayers = false;
    }

    public Blocks blocks = new Blocks();
    public static class Blocks extends Part {

        {
            option("spawner")
                .docs(
                    Style.create().wordWrap(
                        "此配置部分中的所有整数选项仅在创建相关刷怪笼实例时生效。",
                        "这是因为插件也能通过 Paper API 在运行时修改这些值。",
                        "已生成的刷怪笼不会应用此配置，仅对新生成的刷怪笼生效"
                    ).blank()
                    .literal("相关选项包括：")
                    .wordWrap(
                        "\"min-spawn-delay\"、\"max-spawn-delay\"、\"spawn-count\"、\"max-nearby-entities\"、",
                        "\"required-player-range\"、\"spawn-range\""
                    )
                );

            option("optimizeDropperTransfer")
                .docs(
                    "当发射器将物品推入容器时，预检查是否有可用槽位。",
                    "如果没有可用槽位，则提前返回（断路器模式），并使用零拷贝物品移动",
                    "来避免事件系统开销。可提升漏斗密集型建筑的性能。"
                );
        }

        public boolean chestsCanOpenWithFullBlockAbove = false;
        public boolean fullChiseledBookShelvesCountAsValidEnchantPowerSources = false;
        public boolean optimizeDropperTransfer = false;

        public Spawner spawner = new Spawner();
        public static class Spawner extends Part {

            {
                option("minSpawnDelay").docs("刷怪笼两次生成之间的最小延迟")
                    .greaterThanOrEqualTo(0.0F);
                option("maxSpawnDelay").docs("刷怪笼两次生成之间的最大延迟")
                    .greaterThanOrEqualTo(0.0F);
                option("spawnCount").docs("刷怪笼每轮生成的实体数量")
                    .greaterThanOrEqualTo(0.0F);
                option("maxNearbyEntities").docs("刷怪笼停止 tick 前的最大附近实体数量")
                    .greaterThanOrEqualTo(0.0F);
                option("requiredPlayerRange").docs("刷怪笼激活所需的玩家范围")
                    .greaterThanOrEqualTo(0.0F);
                option("spawnRange").docs("生成实体的最大位置范围")
                    .greaterThanOrEqualTo(0.0F);
                option("disableMaxNearbyEntitiesCheck").docs("禁用刷怪笼最大附近实体数量检查");
                option("spawnedEntitiesHaveNoCollision").docs("禁用刷怪笼生成的实体的碰撞");
            }

            public int minSpawnDelay = 200;
            public int maxSpawnDelay = 800;
            public int spawnCount = 4;
            public int maxNearbyEntities = 6;
            public int requiredPlayerRange = 16;
            public int spawnRange = 4;
            public boolean disableMaxNearbyEntitiesCheck = false;
            public boolean spawnedEntitiesHaveNoCollision = false;
        }
    }

    {
        option("waypointUpdateScale")
            .docs(
                "控制 Canvas 路点系统随玩家间距离的衰减速度。",
                "你可以在此了解新系统的工作原理并尝试调整此配置：",
                "https://docs.canvasmc.io/canvas/info/waypoints/"
            );
        option("disableCriterionTrigger").docs("禁用所有准则触发器。进度将无法正常工作！");
        option("cactusCheckSurvivalBeforeGrowth").docs("仙人掌生长前检查是否能存活。可大幅优化仙人掌农场");
        option("enableSuffocationOptimization")
            .docs(
                "通过选择性跳过窒息检查来优化窒息判定，同时仍保持原版外观行为"
            );
    }

    public double waypointUpdateScale = 4000.0D;
    public boolean disableCriterionTrigger = false;
    public boolean cactusCheckSurvivalBeforeGrowth = false;
    public boolean enableSuffocationOptimization = false;

    public Sleeping sleeping = new Sleeping();
    public static class Sleeping extends Part {

        {
            option("sleepSkippingNight")
                .docs(
                    "跳过夜晚时显示的动作栏消息。",
                    "使用 \"default\" 为原版消息，留空则禁用"
                );
            option("sleepingPlayersPercent")
                .docs(
                    "玩家入睡时显示的动作栏消息。",
                    "使用 \"default\" 为原版消息，留空则禁用。你可以使用 \"<count>\"",
                    "作为当前入睡玩家数量的占位符，使用 \"<total>\"",
                    "作为需要入睡的玩家总数的占位符"
                );
            option("sleepNotPossible")
                .docs(
                    "当玩家尝试睡觉但 \"players_sleeping_percentage\"",
                    "游戏规则设置为大于 100 的值时显示的动作栏消息。使用 \"default\" 为原版消息，留空则禁用"
                );
        }

        private String sleepSkippingNight = "default";
        private String sleepingPlayersPercent = "default";
        private String sleepNotPossible = "default";

        public boolean sleepSkippingNightDisabled() {
            return sleepSkippingNight.isBlank();
        }

        public boolean sleepingPlayersPercentDisabled() {
            return sleepingPlayersPercent.isBlank();
        }

        public boolean sleepNotPossibleDisabled() {
            return sleepNotPossible.isBlank();
        }

        public Component getSleepSkippingNight() {
            if (sleepSkippingNightDisabled()) {
                return null;
            }

            final Component message;
            if (sleepSkippingNight.equalsIgnoreCase("default")) {
                message = Component.translatable("sleep.skipping_night");
            }
            else {
                message = PaperAdventure.asVanilla(MiniMessage.miniMessage().deserialize(sleepSkippingNight));
            }

            return message;
        }

        public Component getSleepingPlayersPercent(int amountSleeping, int sleepersNeeded) {
            if (sleepingPlayersPercentDisabled()) {
                return null;
            }

            final Component message;
            if (sleepingPlayersPercent.equalsIgnoreCase("default")) {
                message = Component.translatable("sleep.players_sleeping", amountSleeping, sleepersNeeded);
            }
            else {
                message = PaperAdventure.asVanilla(MiniMessage.miniMessage().deserialize(sleepingPlayersPercent,
                    Placeholder.parsed("count", Integer.toString(amountSleeping)),
                    Placeholder.parsed("total", Integer.toString(sleepersNeeded))));
            }

            return message;
        }

        public Component getSleepNotPossible() {
            if (sleepNotPossibleDisabled()) {
               return null;
            }

            final Component message;
            if (sleepNotPossible.equalsIgnoreCase("default")) {
                message = Component.translatable("sleep.not_possible");
            }
            else {
                message = PaperAdventure.asVanilla(MiniMessage.miniMessage().deserialize(sleepNotPossible));
            }

            return message;
        }

        public boolean sleepIgnoresNearbyMobs = false;
        public boolean rainStopsAfterSleep = true;
        public boolean thunderStopsAfterSleep = true;
    }

}
