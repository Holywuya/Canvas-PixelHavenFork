# Leaf 第一批低风险补丁移植设计

## 背景

当前项目是 Canvas-PixelHavenFork `ver/1.21.11`，基于 Folia 的 region-threading 模型。Leaf `ver/1.21.11` 是 Paper fork，补丁数量多，且包含大量异步、并行、tracker、world tick 相关改动。

本设计只覆盖第一批低风险移植候选。目标不是完整迁移 Leaf，而是挑选无线程模型假设、改动局部、容易逐项验证的补丁，作为后续移植工作的基础。

## 目标

移植第一批低风险 Leaf 补丁，优先获得以下收益：

- 在无监听器时跳过部分 Bukkit 事件构造和派发，减少热路径分配。
- 引入局部微优化，减少重复查询、iterator 和字符串分配。
- 引入明确的 vanilla bugfix，降低内存泄漏或崩溃风险。

每个补丁必须独立实现、独立验证、独立提交。

## 非目标

本批次明确不处理以下内容：

- `Async Pathfinding`。
- `Async Mob Spawning`。
- `Async Chunk Sender`。
- `Async Playerdata Saving`。
- `Multithreaded Tracker`。
- `Parallel World Ticking`。
- Leaf、Gale 或 Purpur 配置系统整体移植。
- 任何会改变 Folia region ownership 语义的补丁。

这些补丁需要单独设计，不能直接套用 Leaf 实现。

## 方案

采用「小补丁队列」方案：每个 Leaf 补丁独立评估、移植和验证。实现时以生成源码为编辑目标，再通过项目 patch 系统重建补丁文件。

本批次包含 8 个候选：

1. `0256-Skip-BlockPhysicsEvent-if-no-listeners.patch`
2. `0264-Skip-PreCreatureSpawnEvent-if-no-listeners.patch`
3. `0311-Skip-VehicleEntityCollisionEvent-if-no-listeners.patch`
4. `0214-Optimise-MobEffectUtil-getDigSpeedAmplification.patch`
5. `0190-Remove-iterators-from-Inventory.patch`
6. `0021-Cache-namespacedKey-toString-and-hash.patch`
7. `0313-Fix-MC-301114-Combat-Tracker-memory-leak.patch`
8. `0322-fix-skeleton-horse-trap-NPE.patch`

## 文件边界

### Minecraft/NMS 补丁

以下补丁修改 `canvas-server/src/minecraft/java/` 下的生成源码，最终重建到 `canvas-server/minecraft-patches/base/`：

- `net/minecraft/world/level/redstone/NeighborUpdater.java`
- `net/minecraft/world/level/NaturalSpawner.java`
- `net/minecraft/world/entity/vehicle/boat/AbstractBoat.java`
- `net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java`
- `net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java`
- `net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java`
- `net/minecraft/world/effect/MobEffectUtil.java`
- `net/minecraft/world/entity/player/Inventory.java`
- `net/minecraft/world/damagesource/CombatTracker.java`
- `net/minecraft/world/entity/animal/equine/SkeletonHorse.java`
- `net/minecraft/world/entity/animal/equine/SkeletonTrapGoal.java`

### API 补丁

以下补丁修改 `canvas-api` 的 Paper API 生成源码或 API patch，最终重建到 `canvas-api/paper-patches/base/`：

- `org/bukkit/NamespacedKey.java`

### Canvas 自有代码

如需要为 `CombatTracker` 内存泄漏修复引入 bounded list，应优先新增 Canvas 自有工具类，而不是引用 Leaf 包名：

- `canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`

如果实现可以用现有 JDK 集合完成，则不新增该类。

## 配置策略

第一批默认不新增配置项，理由如下：

- 事件跳过类补丁只有在无监听器时生效，不改变有监听器时的行为。
- `MobEffectUtil`、`Inventory`、`NamespacedKey` 属于局部等价优化。
- `SkeletonHorse` 修复是 bugfix。

`CombatTracker` 内存泄漏修复需要限制历史条目数量。为避免引入 Leaf 配置系统，采用固定上限，并在实现计划中要求先核对 Paper PR 或 vanilla 上游是否已有合理上限。如果必须配置化，则放入 `GlobalConfiguration.vanillaFixes`，默认启用，默认上限使用 Leaf 的上限值或 Paper PR 中的上限值。

## 线程模型约束

实现必须遵守以下规则：

- 不新增异步 world/entity/chunk 访问。
- 不放宽 `TickThread.ensureTickThread` 或 Folia ownership 检查。
- 不引入 Leaf 的 world-level tick thread 假设。
- 所有事件调用仍在原调用点执行，只在无监听器时跳过事件对象创建和 `callEvent`。
- 不把 `getRegisteredListeners().length` 结果缓存到跨 tick、跨线程的全局状态。

## 测试与验证

每个补丁至少需要：

1. `./gradlew applyAllPatches` 成功。
2. 修改生成源码后，按 Canvas patch 流程提交 fixup 并重建对应 patch。
3. `./gradlew :canvas-server:compileJava` 成功。
4. 若修改 API，运行 `./gradlew :canvas-api:compileJava`。
5. 批次结束后运行 `./gradlew createMojmapPublisherJar`。

能写单元测试的 API 或 utility 类应补测试。NMS 行为补丁以编译和最小运行验证为主，因为当前仓库没有稳定的 NMS 单元测试入口。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| Leaf patch 已被当前 Canvas 或上游包含 | 每个任务先 grep 目标源码和 patch，确认是否已有等价逻辑 |
| 事件跳过改变插件语义 | 只在 `HandlerList` 无注册监听器时跳过，有监听器时保持原路径 |
| `CombatTracker` 修复引入 Leaf 包名或配置系统 | 使用 Canvas 包名或 JDK 实现，不引用 Leaf 配置类 |
| API patch 与 Paper/Canvas 现有改动冲突 | 单独处理 `NamespacedKey`，编译 API 后再继续 |
| NMS patch 重建失败 | 按 CLAUDE.md 流程修改生成源码并 rebuild，不直接编辑 `.patch` |

## 验收标准

本批次完成时应满足：

- 8 个候选补丁中，已移植的每个补丁都有独立 commit。
- 被跳过的候选必须在提交说明或后续记录中说明原因，例如「已存在」或「与 Folia 冲突」。
- 不引入 Leaf、Gale、Purpur 配置框架。
- 不引入新的异步 world/entity/chunk 访问。
- `./gradlew :canvas-server:compileJava` 通过。
- 涉及 API 时，`./gradlew :canvas-api:compileJava` 通过。
- 批次结束时，`./gradlew createMojmapPublisherJar` 通过，或如失败，失败原因明确且不是本批次代码导致。

## 自检结果

- 占位符检查：无 `TODO`、`待定`、`后续实现` 等占位内容。
- 范围检查：范围限定为第一批 8 个低风险补丁，不包含高风险异步和线程模型补丁。
- 一致性检查：配置策略、线程模型约束和验收标准一致，均要求不引入 Leaf 配置系统和异步 world 访问。
