# Canvas 1.21.11 上游更新备份说明

## 备份内容

### 新增文件（`backup/src/`）
- `GlobalConfiguration.java` — 新全局配置系统（YAML，替代旧 Config.java）
- `WorldConfig.java` — Per-world 配置系统
- `configuration/` — YAML 配置框架（11个文件）
- `TickGuard.java` — 区域线程安全检查（替代 AsyncCatcher）
- `CanonicalReference.java` — 一次性引用工具
- `UpdateSuppressionException.java` — 更新抑制异常捕获
- `Util.java` — 更新版（添加了 gradient 功能）
- `lithium/` — Lithium 移植（HashPalette, Equipment Tracking）

### 修改的构建文件（`backup/`）
- `build.gradle.kts` — 添加 aliyun 镜像、canvasMavenPublicUrl
- `settings.gradle.kts` — 添加 aliyun 镜像
- `CLAUDE.md` — 项目文档

## 重新 clone 后的操作步骤

1. 将 `backup/src/` 中的文件复制到 `canvas-server/src/main/java/io/canvasmc/canvas/`
2. 将 `backup/build.gradle.kts` 和 `backup/settings.gradle.kts` 覆盖项目根目录的同名文件
3. 将 `backup/CLAUDE.md` 放到项目根目录
4. 运行 `./gradlew applyAllPatches`（需要代理访问 GitHub，端口 7897 SOCKS5）
5. 运行 `./gradlew rebuildAllServerPatches` 重建补丁
6. 运行 `./gradlew createMojmapPublisherJar` 构建

## 已完成的上游更新（需要重新应用）

### 来自 Canvas 26.1.2
- 配置系统重写（JSON5 → YAML）
- 全局配置 + per-world 配置分离
- TickGuard 替代 AsyncCatcher
- Purpur Alternative Keepalive
- Region Tick Guards
- Remove MinecraftServer tickables + Fix GUI

### 来自 Spring-for-LeavesMC
- 性能优化：节流目标选择器、减少实体分配、移除 lambda、缓存攀爬检测、优化太阳灼烧、跳过零移动实体、更快区块序列化、Lithium 装备追踪
- Bug 修复：更新抑制崩溃捕获、漏车修复、下落方块重复修复、传送门事件修复、区块重载检测修复

## 重要：代理配置
```bash
git config --global http.proxy socks5h://127.0.0.1:7897
git config --global https.proxy socks5h://127.0.0.1:7897
```
