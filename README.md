![title](./canvas_title.png)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/Holywuya/Canvas-PixelHavenFork)](https://github.com/Holywuya/Canvas-PixelHavenFork)
[![GitHub forks](https://img.shields.io/github/forks/Holywuya/Canvas-PixelHavenFork)](https://github.com/Holywuya/Canvas-PixelHavenFork)

# Canvas - PixelHaven Fork

基于 [CanvasMC](https://github.com/CraftCanvasMC/Canvas) 的社区维护分支，运行在 Minecraft 1.21.11 上。

Canvas 是一个高性能的 Folia 分支，旨在为大规模服务器提供稳定、高效的区域化多线程环境。

---

## Fork 特色

本分支在 Canvas 原版基础上，从多个上游项目移植了大量优化和修复，并自研了多项独创功能：

### 配置系统重写

- 从 JSON5（jankson）迁移到 **YAML**（snakeyaml）配置格式
- **全局配置**（`config/canvas-server.yml`）：服务器级设置，不可被维度覆盖
- **世界配置**（`config/canvas-worlds.yml`）：支持 per-world 覆盖（每个维度可创建 `canvas-patch.yml`）
- 自动 kebab-case 字段命名，支持运行时重载

### 来自 Canvas 26.1.2 的更新

| 功能 | 说明 |
|------|------|
| **Purpur Alternative Keepalive** | 基于时间戳的 keepalive 机制，解决高延迟环境误踢问题 |
| **Region Tick Guards** | 替代 Spigot AsyncCatcher，提供 region 感知的线程安全检查，支持 SILENT/LOG/THROW 三级严重度 |
| **Remove MinecraftServer tickables** | 清理废弃代码，修复服务器 GUI 不更新的 bug |
| **TickGuard 工具类** | 可配置的区域线程安全检查框架 |

### 来自 Spring-for-LeavesMC 的优化（14 个补丁）

**性能优化：**
- AI 目标选择器节流（19/20 跳过）
- 减少实体对象分配（缓存 lambda）
- 移除热路径 lambda 分配
- 优化太阳灼烧检测（提前退出）
- 跳过零移动实体的 move() 处理
- 跳过微小平面移动乘法运算
- 更快的区块序列化（Lithium 移植）

**Bug 修复：**
- 更新抑制崩溃捕获（技术服必备）
- 漏斗矿车无玩家时正常工作
- 下落方块实体重复修复
- 传送门退出事件逻辑修复
- 区块重载检测器修复
- `preventMovingIntoUnloadedChunks` 配置修复

### 来自 Kitin 的优化（6 个补丁）

| 补丁 | 说明 |
|------|------|
| **Gale AI Collections** | AttributeMap 使用 Reference-based HashMap，减少内存开销 |
| **Reduce Entity Packets** | 跳过 TNT/高速实体的位置同步包，世吞场景大幅减少网络流量 |
| **Villager Smart Hibernation** | 被围住的村民冻结 AI，减少无用计算 |
| **Particle Throttling** | 粒子包节流，可配置每 tick 上限 |
| **Dropper Transfer** | 投掷器零拷贝物品转移，跳过事件系统开销 |
| **ItemEntity Water Fix** | 修复水中物品异常运动 |

### 来自 Luminol 的功能

| 功能 | 说明 |
|------|------|
| **Linear 区域格式** | LZ4 压缩 + ZSTD bucket 的自定义区域文件格式，磁盘空间减少约 50%，I/O 更快 |
| **区域压缩等级配置** | `regionCompressionLevel` 选项，ZSTD 压缩等级 1-22 可调，默认 3 |
| **Folia Bug 修复** | 8 项 Folia 区域线程相关修复：volatile 引用、POI 扫描、实体 AI/记忆安全、栓绳、驯服动物传送、末影龙部件同步、移动事件竞态保护 |

### 来自 LeafMC 的优化（第一批，10+ 补丁）

| 补丁 | 说明 |
|------|------|
| **无 listener 跳过事件** | BlockPhysicsEvent / PreCreatureSpawnEvent / VehicleEntityCollisionEvent 无监听器时直接跳过 |
| **CombatTracker 内存泄漏修复** | MC-301114，使用 EvictingRingList 限制战斗记录上限（10240 条） |
| **MobEffectUtil 挖掘加速优化** | 避免 `hasEffect` 后重复 `getEffect`，一次查找 |
| **Inventory iterator 移除** | 热路径从 iterator 改为 indexed loop，减少对象分配 |
| **NamspacedKey 缓存** | 缓存 `toString()` 和 `hashCode()`，降低热路径开销 |
| **SkeletonHorse trap NPE 修复** | 避免在 goal 迭代期间修改 goal 集合 |
| **运算优化** | `CubePointRange` 除法转乘法、`Vec3i` 哈希优化、平台数学函数（floor/ceil） |
| **哈希缓存** | `FluidOcclusionCacheKey` 和 `ShapePairKey` 哈希结果缓存 |
| **BlockEntity 地图缓存** | `ChunkAccess` 中缓存 BlockEntity 查找表 |

### 自研功能（PixelHaven 独创）

**日志过滤与清理：**
| 功能 | 说明 |
|------|------|
| **8 类日志过滤** | 独立开关压制：无效统计、空消息、被忽略进度、远距离 setBlock、无法识别配方、过期消息、Not Secure 标记、null ID 断连 |
| **自动日志清理** | 可配置保留天数的日志自动清理 |

**插件兼容性：**
| 功能 | 说明 |
|------|------|
| **FAWE 兼容 Shim** | 恢复 `MinecraftServer.currentTick` 字段，兼容 FastAsyncWorldEdit |
| **MythicMobs 兼容** | `allowLegacyScheduler` 选项允许传统 Bukkit 调度器操作 |
| **`canvas-supported` 旗帜** | plugin.yml 标记，无需声明完整 Folia 支持即可兼容 Canvas |

**村民行为优化：**
| 功能 | 说明 |
|------|------|
| **POI 搜索范围配置** | 可分别限制工作站点、住所、集合点的 POI 搜索半径 |
| **村民智能休眠增强** | 全封闭且无交易的村民跳过全部 AI 运算 |

**其他优化：**
| 功能 | 说明 |
|------|------|
| **平台数学函数** | 使用 `Math.floorDiv` / `Math.floorMod` 替换手写 floor 逻辑 |
| **EntityType 转换缓存** | 缓存 `minecraftToBukkit` 转换结果 |
| **快速随机源** | `Xoroshiro128PlusPlus` 替代 `SimpleThreadUnsafeRandom` |

### 原版 Canvas 功能

- **AFFINITY 调度器**：可配置的区域调度器，支持 CPU 亲和性、工作窃取
- **优化的区块系统**：重写的区块加载和生成管线
- **完善的区域 Profiling**：完全兼容区域线程的 Spark profiler
- **大量 Folia Bug 修复**：端末珍珠、路点系统、传送事件等
- **Purpur 容器扩展**：6 行末影箱、可配置桶行数
- **丰富的战斗配置**：攻击延迟、暴击、扫击、剑格挡等
- **MC Bug 修复开关**：20+ 个可配置的 Mojang bug 修复

---

## 构建方法

### 环境要求

- Java 21+
- Git

### 构建服务器 JAR

```bash
# 构建完整的服务器 JAR
./gradlew createMojmapPublisherJar
```

产物位于 `canvas-server/build/libs/`。

### 开发流程

```bash
# 1. 应用所有补丁
./gradlew applyAllPatches

# 2. 修改源码...
#    - Canvas 自有代码：直接编辑 canvas-server/src/
#    - Minecraft 源码：编辑 canvas-server/src/minecraft/java/

# 3. 在 worktree 中提交更改（Minecraft 源码必须走这个流程）
cd canvas-server/src/minecraft/java
git add -A .
git commit --fixup=file
git rebase --autosquash HEAD~2

# 4. 重建补丁
cd ../../..
./gradlew rebuildMinecraftSourcePatches

# 5. 提交补丁
git add canvas-server/minecraft-patches/
git commit -m "描述你的更改"

# 6. 构建验证
./gradlew createMojmapPublisherJar
```

### 启动服务器

```bash
java -Xmx2G -jar canvas-server/build/libs/canvas-paperclip-*.jar --nogui
```

---

## 配置示例

### 全局配置（`config/canvas-server.yml`）

```yaml
region-scheduler:
  affinity-scheduler:
    enable-work-stealing: true
    enable-mid-tick-tasks: true
  default-tick-rate: 20.0
  guard-severity: THROW

chunk-system:
  thread-priority: 5
  optimize-aquifer: false

networking:
  filter-velocity-packet: false
  particle-throttling: false
  purpur-alternative-keepalive: false

region-compression-level: 3
region-format: MCA

performance:
  throttle-inactive-goal-selector-tick: false
  faster-chunk-serialization: false
  skip-entity-move-if-movement-is-zero: false
```

### 世界配置（`config/canvas-worlds.yml`）

```yaml
entities:
  fast-orbs: false
  entity-collision-mode: VANILLA
  villagers:
    villager-smart-hibernation: false

combat:
  restore-old-attack-delay-mechanics: false
  critical-hit-multiplier: 1.5

visuals:
  particles:
    disable-sprint-particles: false
```

---

## 兼容性说明

- 本项目基于 **Folia**，不是 Paper/Purpur 的直接替代品
- 适用于已使用 Folia 或 Folia 分支的服务器环境
- 严格遵守 Folia 的线程安全规则
- 中国大陆构建已配置阿里云 Maven 镜像

---

## 致谢

- [CanvasMC](https://github.com/CraftCanvasMC/Canvas) — 上游项目
- [Folia](https://github.com/PaperMC/Folia) — 区域化多线程基础
- [Paper](https://github.com/PaperMC/Paper) — 服务端基础
- [Spring-for-LeavesMC](https://github.com/XingZiNina/Spring-for-LeavesMC) — 性能优化和 Bug 修复
- [Kitin](https://github.com/SucIXR/Kitin) — 网络和实体优化
- [Lithium](https://github.com/CaffeineMC/lithium-fabric) — 区块序列化和装备追踪优化
- [Gale](https://github.com/GaleMC/Gale) — AI 属性集合优化、可变实体唤醒时长
- [Purpur](https://github.com/PurpurMC/Purpur) — Alternative Keepalive 和容器扩展
- [LeafMC](https://github.com/Winds-Studio/Leaf) — 事件跳过、内存修复、热路径优化
- [Luminol](https://github.com/LuminolMC/Luminol) — Linear 区域格式、Folia 线程安全修复、Gale 可变唤醒时长移植

---

## 许可证

本项目基于 **GNU General Public License v3.0 (GPL-3.0)** 许可证。
