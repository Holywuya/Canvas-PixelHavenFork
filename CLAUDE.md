# Canvas 1.21.11 (PixelHaven Fork)

Canvas 是一个基于 Folia（Paper 的区域化多线程分支）的 Minecraft 服务端分支，提供性能优化、配置扩展和 API 增强。

本分支 (`ver/1.21.11`) 是 PixelHaven 维护的 1.21.11 版本 fork，上游 Canvas 主线已迁移到 `ver/26.1.2`。

## 项目架构

### 补丁系统

Canvas 使用 paperweight-weaver 补丁系统，分层修改上游代码：

```
Minecraft 原版 → Paper 补丁 → Folia 补丁 → Canvas 补丁
```

**补丁目录结构：**

| 目录 | 作用 | 数量 |
|------|------|------|
| `canvas-server/minecraft-patches/base/` | 修改 Minecraft 源码（NMS） | 22 个 |
| `canvas-server/paper-patches/base/` | 修改 Paper/Folia 服务端代码 | 13 个 |
| `canvas-api/paper-patches/base/` | 修改 API 层代码 | 4 个 |

补丁按编号顺序应用。修改补丁后需运行 `rebuildAllServerPatches` 重新生成。

### 源码目录

**Canvas 自有代码（`canvas-server/src/main/java/io/canvasmc/canvas/`）：**

| 目录 | 说明 |
|------|------|
| `GlobalConfiguration.java` | 全局配置（YAML，`config/canvas-server.yml`） |
| `WorldConfig.java` | Per-world 配置（YAML，`config/canvas-worlds.yml` + 每个维度的 `canvas-patch.yml`） |
| `configuration/` | YAML 配置框架（ConfigurationProvider, Part, Style, Resolver, Validator, NodeDiff, Token） |
| `command/sub/` | 命令实现（reload, tpsbar, world-distance, set-max-players） |
| `tick/` | 调度器相关（AffinitySchedulerThreadPool, SchedulerUtil, ScheduledHandleTickState） |
| `util/` | 工具类（TickGuard, CanonicalReference, Util, FasterRandomSource） |
| `world/entity/` | EnderPearls 管理 |
| `world/waypoints/` | 路点系统 |
| `world/chunk/` | BalancedChunkSystem |
| `spark/` | Spark profiler 集成 |

**Canvas API（`canvas-api/src/main/java/io/canvasmc/canvas/`）：**

| 目录 | 说明 |
|------|------|
| `event/` | 自定义事件（PlayerPostRespawnAsyncEvent 等） |
| `region/` | 区域化 API |
| `simd/` | SIMD 检测 |

### 生成的目录（不直接编辑）

| 目录 | 说明 |
|------|------|
| `paper-server/` | Paper 补丁应用后的服务端代码 |
| `paper-api/` | Paper 补丁应用后的 API 代码 |
| `folia-server/` | Folia 补丁应用后的服务端代码 |
| `folia-api/` | Folia 补丁应用后的 API 代码 |

### 配置系统

配置从 JSON5（旧 `Config.java`）迁移到 YAML（`GlobalConfiguration` + `WorldConfig`）。

**GlobalConfiguration**（全局，不可 per-world 覆盖）：
- `regionScheduler.*` — 调度器配置（affinity, tick rate, guard severity）
- `chunkSystem.*` — 区块系统（线程优先级, fluid 处理, 结构优化）
- `networking.*` — 网络（包过滤, keepalive, 协议切换）
- `vanillaFixes.*` — MC bug 修复开关
- `chat.*` — 聊天报告禁用
- `purpurContainers.*` — 容器行数配置
- `combat.*` — 战斗配置（已被部分迁移到 WorldConfig）

**WorldConfig**（per-world，可在每个维度的 `canvas-patch.yml` 中覆盖）：
- `regionBars.*` — TPS/RAM bar
- `visuals.*` — 粒子、火焰显示
- `entities.*` — 碰撞模式、投射物、经验球、骷髅精准度
- `combat.*` — 攻击延迟、暴击、扫击、剑格挡
- `blocks.spawner.*` — 刷怪箱配置
- `farming.*` — 农业（耕地、作物、树叶）
- `sleeping.*` — 睡觉配置

## 构建方法

### 环境要求

- Java 21+
- Git

### 构建服务器 JAR

```bash
# 构建完整的服务器 JAR（用于部署）
./gradlew createMojmapPublisherJar
```

产物位于 `canvas-server/build/libs/`。

### 开发流程

#### 修改 Canvas 自有代码（`canvas-server/src/`、`canvas-api/src/`）

直接编辑文件，然后构建验证即可。这些文件是 git 跟踪的，直接 `git add` + `git commit`。

#### 修改 Minecraft/Paper 源码（`canvas-server/src/minecraft/java/`、`paper-server/src/main/java/`）

这些目录是 `.gitignore` 的，由补丁系统生成。修改后需要通过特殊流程保存到补丁文件中：

```bash
# 1. 应用补丁（生成源码文件）
./gradlew applyAllPatches

# 2. 在生成的源码目录中编辑文件
#    - Minecraft 源码：canvas-server/src/minecraft/java/
#    - Paper/Folia 代码：paper-server/src/main/java/

# 3. 在 worktree 中提交更改（关键步骤！）
cd canvas-server/src/minecraft/java
git add -A .
git commit --fixup=file

# 4. rebase 合并 fixup 到 "file" 提交
git rebase --autosquash HEAD~2

# 5. 回到项目根目录，重建补丁
cd ../../..
./gradlew rebuildMinecraftSourcePatches

# 6. 提交补丁文件
git add canvas-server/minecraft-patches/
git commit -m "描述你的更改"

# 7. 构建验证
./gradlew createMojmapPublisherJar
```

**重要：** 直接运行 `rebuildAllServerPatches` 不会捕获未提交到 worktree 的更改。必须先在 worktree 中 `git commit --fixup=file` + `rebuild --autosquash`，然后才能重建补丁。

### 常用 Gradle 任务

| 任务 | 说明 |
|------|------|
| `applyAllPatches` | 应用所有补丁到源码 |
| `rebuildAllServerPatches` | 重建所有服务端补丁 |
| `rebuildMinecraftBasePatches` | 仅重建 Minecraft base 补丁 |
| `rebuildMinecraftSourcePatches` | 仅重建 Minecraft source 补丁 |
| `rebuildServerBasePatches` | 重建 Paper/Folia base 补丁 |
| `createMojmapPublisherJar` | 构建完整服务器 JAR |
| `:canvas-server:compileJava` | 仅编译服务端（快速验证） |

### 修改补丁的正确方式

1. `./gradlew applyAllPatches` — 应用补丁到工作目录
2. 在 `canvas-server/src/minecraft/java/` 或 `paper-server/src/` 中编辑文件
3. `./gradlew rebuildAllServerPatches` — 从工作目录重新生成 `.patch` 文件
4. 提交 `.patch` 文件的变更

**不要**直接编辑 `.patch` 文件，应通过修改源码 + rebuild 的方式。

## 关键配置引用映射

从旧 `Config.INSTANCE` 迁移到新系统的参考：

```
Config.INSTANCE.scheduler.*           → GlobalConfiguration.getInstance().regionScheduler.affinityScheduler.*
Config.INSTANCE.chunks.*              → GlobalConfiguration.getInstance().chunkSystem.*
Config.INSTANCE.networking.*          → GlobalConfiguration.getInstance().networking.*
Config.INSTANCE.fixes.*               → GlobalConfiguration.getInstance().vanillaFixes.*
Config.INSTANCE.enableNoChatReports   → GlobalConfiguration.getInstance().chat.disableChatReporting
Config.INSTANCE.containers.*          → GlobalConfiguration.getInstance().purpurContainers.*
Config.INSTANCE.fetchRespawnDimensionKey() → GlobalConfiguration.fetchRespawnDimensionKey()

# 以下已迁移到 per-world 配置：
Config.INSTANCE.particles.*           → WorldConfig.getDefaults().visuals.particles.*
Config.INSTANCE.combat.*              → WorldConfig.getDefaults().combat.*
Config.INSTANCE.spawner.*             → WorldConfig.getDefaults().blocks.spawner.*
Config.INSTANCE.entityCollisionMode   → WorldConfig.getDefaults().entities.entityCollisionMode
Config.INSTANCE.fastOrbs              → WorldConfig.getDefaults().entities.fastOrbs
Config.INSTANCE.projectiles.*         → WorldConfig.getDefaults().entities.projectiles.*
```

## Git 工作流

- **主分支**：`ver/1.21.11`
- **上游**：`origin` → `https://github.com/Holywuya/Canvas-PixelHavenFork.git`
- **上游 Canvas**：`upstream` → `https://github.com/CraftCanvasMC/Canvas.git`（`ver/26.1.2`）
- **Folia 基础**：`foliaCommit = 3ef0ba66b20599d24f235ac795865047c29c5eb4`

### 提交规则（强制）

**每完成一项任务后，必须立即提交 git。** 不要等到所有工作完成再提交。

- 完成一个功能/修复 → 立即 `git add` + `git commit`
- 完成一个补丁移植 → 立即提交
- 完成一次构建验证 → 提交
- 不要积累大量未提交的更改

原因：项目经常遇到网络中断、缓存损坏、补丁冲突等问题。未提交的工作一旦丢失就无法恢复。

## 注意事项

- 中国大陆构建需要配置阿里云 Maven 镜像（已在 `build.gradle.kts` 和 `settings.gradle.kts` 中配置）
- `canvas-server/src/minecraft/java/` 中的文件由补丁生成，修改后必须 rebuild patches
- `paper-server/`、`folia-server/` 等目录是生成的，不要直接编辑
- 配置系统使用 YAML（snakeyaml），字段名自动转换为 kebab-case
- `WorldConfig` 使用延迟初始化，`getDefaults()` 在服务器启动前返回默认实例
