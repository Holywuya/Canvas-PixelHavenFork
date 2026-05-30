# Leaf 第一批低风险补丁移植实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 Leaf `ver/1.21.11` 中第一批低风险、局部、无线程模型假设的补丁移植到 Canvas-PixelHavenFork。

**架构：** 按补丁逐个移植：先应用 Canvas 生成源码，再修改对应 NMS、Paper API 或 Canvas 自有代码，最后通过 paperweight patch 流程重建补丁。每个补丁独立验证、独立提交，避免一次性引入大面积冲突。

**技术栈：** Java 21、Gradle、paperweight-weaver、Folia region-threading、Bukkit/Paper API、Canvas YAML 配置系统。

---

## 文件结构

### 文档

- 修改：`docs/superpowers/specs/2026-05-31-leaf-first-batch-port-design.md`
  - 已存在的设计规格。实现时只在发现设计错误时修改。
- 修改：`docs/superpowers/plans/2026-05-31-leaf-first-batch-port.md`
  - 本计划。执行时用复选框记录进度。

### 生成源码：Minecraft/NMS

以下文件由 patch 系统生成。不要直接提交这些源码文件到主仓库；修改后必须按 `CLAUDE.md` 中的流程重建 patch。

- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/level/redstone/NeighborUpdater.java`
  - 移植 `BlockPhysicsEvent` 无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java`
  - 移植 `PreCreatureSpawnEvent` 无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java`
  - 移植船只碰撞事件无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java`
  - 移植矿车碰撞事件无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java`
  - 移植新矿车行为中的碰撞事件无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java`
  - 移植旧矿车行为中的碰撞事件无 listener 跳过逻辑。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/effect/MobEffectUtil.java`
  - 优化 `getDigSpeedAmplification`，避免 `hasEffect` 后再次 `getEffect`。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/player/Inventory.java`
  - 移除部分热路径 iterator，改为 indexed loop 和固定装备槽数组。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/damagesource/CombatTracker.java`
  - 限制 combat entries 数量，修复 MC-301114 内存泄漏。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/SkeletonHorse.java`
  - 避免在 goal 迭代期间移除 skeleton trap goal。
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/SkeletonTrapGoal.java`
  - 增加 trap 开关，并在 `stop()` 中释放 `eligiblePlayers`。

### 生成源码：Paper API

- 修改：`canvas-api/src/main/java/org/bukkit/NamespacedKey.java`
  - 缓存 `toString()` 和 `hashCode()` 结果。

### Canvas 自有代码

- 创建：`canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`
  - 容量受限的 ring list。用于 `CombatTracker.entries`，避免无限增长。
- 创建：`canvas-server/src/test/java/io/canvasmc/canvas/util/collection/EvictingRingListTest.java`
  - 验证 ring list 的容量、淘汰顺序、`get`、`set`、`clear` 和 fail-fast iterator。

### 最终 patch 输出

- 修改：`canvas-server/minecraft-patches/base/*.patch`
  - 由 `rebuildMinecraftSourcePatches` 生成。
- 修改：`canvas-api/paper-patches/base/*.patch`
  - 由 API patch rebuild 任务生成；如果项目没有单独任务，使用现有 patch rebuild 工作流。

---

## 通用执行规则

- 每个任务开始前运行 `git status --short`，确认没有未预期改动。
- 每个 Leaf 补丁先 grep 当前源码和 patch，确认没有等价逻辑。
- 不引用 `org.dreeam.leaf.*`、`org.galemc.gale.*` 或 Leaf 配置系统。
- 不新增异步 world、entity、chunk 访问。
- 不放宽 Folia/Canvas 线程检查。
- 修改 `canvas-server/src/minecraft/java/` 后，必须按 patch worktree 流程提交 fixup 并 rebuild。
- 每完成一个补丁或一个独立工具类，立即 commit。

---

## 任务 1：准备生成源码和基线验证

**文件：**
- 读取：`CLAUDE.md`
- 生成：`canvas-server/src/minecraft/java/**`
- 生成：`paper-server/**`
- 生成：`canvas-api/src/main/java/org/bukkit/NamespacedKey.java`

- [ ] **步骤 1：确认工作区干净**

运行：

```bash
git status --short
```

预期：只允许看到本计划文档未提交。如果还有其他未说明改动，停止并让负责人确认。

- [ ] **步骤 2：应用所有补丁**

运行：

```bash
./gradlew applyAllPatches
```

预期：任务成功结束，生成 `canvas-server/src/minecraft/java/` 和 API 源码。

- [ ] **步骤 3：运行基线编译**

运行：

```bash
./gradlew :canvas-server:compileJava :canvas-api:compileJava
```

预期：两个任务都 `BUILD SUCCESSFUL`。如果基线失败，停止并记录失败输出，不开始移植。

- [ ] **步骤 4：提交准备状态（仅当 applyAllPatches 或文档产生可提交改动）**

运行：

```bash
git status --short
```

如果只有本计划文档未提交，提交它：

```bash
git add docs/superpowers/plans/2026-05-31-leaf-first-batch-port.md
git commit -m "docs: add Leaf first batch port plan"
```

预期：生成一个文档提交。若 `applyAllPatches` 没有产生可提交改动，这是正常的。

---

## 任务 2：为 CombatTracker 准备 EvictingRingList

**文件：**
- 创建：`canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`
- 创建：`canvas-server/src/test/java/io/canvasmc/canvas/util/collection/EvictingRingListTest.java`

- [ ] **步骤 1：编写失败的测试**

创建 `canvas-server/src/test/java/io/canvasmc/canvas/util/collection/EvictingRingListTest.java`：

```java
package io.canvasmc.canvas.util.collection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

class EvictingRingListTest {

    @Test
    void evictsOldestEntriesWhenCapacityIsReached() {
        EvictingRingList<Integer> list = new EvictingRingList<>(3);

        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        assertEquals(3, list.size());
        assertArrayEquals(new Object[] {2, 3, 4}, list.toArray());
    }

    @Test
    void supportsGetSetAndClear() {
        EvictingRingList<String> list = new EvictingRingList<>(2);

        list.add("first");
        list.add("second");
        String oldValue = list.set(1, "updated");

        assertEquals("second", oldValue);
        assertEquals("first", list.get(0));
        assertEquals("updated", list.get(1));

        list.clear();

        assertEquals(0, list.size());
        assertArrayEquals(new Object[0], list.toArray());
    }

    @Test
    void iteratorIsFailFast() {
        EvictingRingList<Integer> list = new EvictingRingList<>(2);
        list.add(1);
        list.add(2);
        Iterator<Integer> iterator = list.iterator();

        list.add(3);

        assertThrows(ConcurrentModificationException.class, iterator::next);
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new EvictingRingList<>(0));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
./gradlew :canvas-server:test --tests io.canvasmc.canvas.util.collection.EvictingRingListTest
```

预期：FAIL，原因是 `EvictingRingList` 类不存在或无法解析。

- [ ] **步骤 3：编写最少实现代码**

创建 `canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`：

```java
package io.canvasmc.canvas.util.collection;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;

public final class EvictingRingList<E> extends AbstractList<E> implements RandomAccess {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;

    private Object[] elements;
    private final int maxCapacity;
    private int head;
    private int size;
    private int tail;
    private int mask;

    public EvictingRingList(int requestedMaxCapacity) {
        if (requestedMaxCapacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.maxCapacity = tableSizeFor(requestedMaxCapacity);
        int initialCapacity = Math.min(DEFAULT_INITIAL_CAPACITY, this.maxCapacity);
        this.elements = new Object[initialCapacity];
        this.mask = initialCapacity - 1;
    }

    public EvictingRingList(Collection<? extends E> collection) {
        this(Math.max(1, collection.size()));
        this.addAll(collection);
    }

    public EvictingRingList(int requestedMaxCapacity, Collection<? extends E> collection) {
        this(requestedMaxCapacity);
        this.addAll(collection);
    }

    private static int tableSizeFor(int capacity) {
        int value = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        if (value < 0) {
            return 1;
        }
        return value >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : value + 1;
    }

    private void grow() {
        int oldCapacity = this.elements.length;
        int newCapacity = oldCapacity << 1;
        Object[] newElements = new Object[newCapacity];
        int firstPart = oldCapacity - this.head;
        System.arraycopy(this.elements, this.head, newElements, 0, firstPart);
        System.arraycopy(this.elements, 0, newElements, firstPart, this.head);
        this.elements = newElements;
        this.mask = newCapacity - 1;
        this.head = 0;
        this.tail = oldCapacity;
    }

    @Override
    public boolean add(E element) {
        this.modCount++;
        if (this.size < this.elements.length) {
            this.size++;
        } else if (this.elements.length < this.maxCapacity) {
            this.grow();
            this.size++;
        } else {
            this.head = (this.head + 1) & this.mask;
        }
        this.elements[this.tail] = element;
        this.tail = (this.tail + 1) & this.mask;
        return true;
    }

    @Override
    public E get(int index) {
        Objects.checkIndex(index, this.size);
        return (E) this.elements[(this.head + index) & this.mask];
    }

    @Override
    public E set(int index, E element) {
        Objects.checkIndex(index, this.size);
        int realIndex = (this.head + index) & this.mask;
        E oldValue = (E) this.elements[realIndex];
        this.elements[realIndex] = element;
        return oldValue;
    }

    @Override
    public E remove(int index) {
        Objects.checkIndex(index, this.size);
        this.modCount++;
        E oldValue = this.get(index);
        for (int i = index; i < this.size - 1; i++) {
            int current = (this.head + i) & this.mask;
            int next = (this.head + i + 1) & this.mask;
            this.elements[current] = this.elements[next];
        }
        int lastIndex = (this.head + this.size - 1) & this.mask;
        this.elements[lastIndex] = null;
        this.tail = (this.tail - 1) & this.mask;
        this.size--;
        return oldValue;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public void clear() {
        this.modCount++;
        if (this.size == 0) {
            return;
        }
        if (this.head < this.tail) {
            Arrays.fill(this.elements, this.head, this.tail, null);
        } else {
            Arrays.fill(this.elements, this.head, this.elements.length, null);
            if (this.tail > 0) {
                Arrays.fill(this.elements, 0, this.tail, null);
            }
        }
        this.head = 0;
        this.tail = 0;
        this.size = 0;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        Objects.requireNonNull(action);
        int expectedModCount = this.modCount;
        int cursor = this.head;
        for (int count = 0; count < this.size; count++) {
            action.accept((E) this.elements[cursor]);
            cursor = (cursor + 1) & this.mask;
        }
        if (this.modCount != expectedModCount) {
            throw new ConcurrentModificationException();
        }
    }

    @Override
    public Object @NotNull [] toArray() {
        Object[] result = new Object[this.size];
        this.copyInto(result);
        return result;
    }

    @Override
    public <T> T @NotNull [] toArray(T[] array) {
        T[] result = array;
        if (result.length < this.size) {
            result = (T[]) Array.newInstance(array.getClass().getComponentType(), this.size);
        }
        this.copyInto(result);
        if (result.length > this.size) {
            result[this.size] = null;
        }
        return result;
    }

    private void copyInto(Object[] target) {
        if (this.size == 0) {
            return;
        }
        if (this.head < this.tail) {
            System.arraycopy(this.elements, this.head, target, 0, this.size);
            return;
        }
        int firstPart = this.elements.length - this.head;
        System.arraycopy(this.elements, this.head, target, 0, firstPart);
        System.arraycopy(this.elements, 0, target, firstPart, this.tail);
    }

    @Override
    public @NotNull Iterator<E> iterator() {
        return new RingIterator();
    }

    private final class RingIterator implements Iterator<E> {
        private int cursor = EvictingRingList.this.head;
        private int remaining = EvictingRingList.this.size;
        private int lastReturned = -1;
        private int expectedModCount = EvictingRingList.this.modCount;

        @Override
        public boolean hasNext() {
            return this.remaining > 0;
        }

        @Override
        public E next() {
            this.checkForComodification();
            if (this.remaining <= 0) {
                throw new NoSuchElementException();
            }
            this.lastReturned = this.cursor;
            E element = (E) EvictingRingList.this.elements[this.cursor];
            this.cursor = (this.cursor + 1) & EvictingRingList.this.mask;
            this.remaining--;
            return element;
        }

        @Override
        public void remove() {
            if (this.lastReturned < 0) {
                throw new IllegalStateException();
            }
            this.checkForComodification();
            int logicalIndex = (this.lastReturned - EvictingRingList.this.head) & EvictingRingList.this.mask;
            EvictingRingList.this.remove(logicalIndex);
            this.cursor = this.lastReturned;
            this.lastReturned = -1;
            this.expectedModCount = EvictingRingList.this.modCount;
        }

        private void checkForComodification() {
            if (EvictingRingList.this.modCount != this.expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```bash
./gradlew :canvas-server:test --tests io.canvasmc.canvas.util.collection.EvictingRingListTest
```

预期：`EvictingRingListTest` 全部 PASS。

- [ ] **步骤 5：提交工具类**

运行：

```bash
git add canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java canvas-server/src/test/java/io/canvasmc/canvas/util/collection/EvictingRingListTest.java
git commit -m "feat: add evicting ring list"
```

预期：生成一个独立 commit。

---

## 任务 3：移植 BlockPhysicsEvent 无 listener 跳过

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/level/redstone/NeighborUpdater.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0256-Skip-BlockPhysicsEvent-if-no-listeners.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Skip BlockPhysicsEvent\|hasPhysicsEvent\|BlockPhysicsEvent.getHandlerList" -n canvas-server/src/minecraft/java/net/minecraft/world/level/redstone/NeighborUpdater.java canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改生成源码**

在 `NeighborUpdater.executeUpdate` 中，把 `BlockPhysicsEvent` 创建和 `callEvent` 包进 listener 检查。目标代码形态：

```java
if (org.bukkit.event.block.BlockPhysicsEvent.getHandlerList().getRegisteredListeners().length > 0) { // Canvas - Skip BlockPhysicsEvent if no listeners
    org.bukkit.event.block.BlockPhysicsEvent event = new org.bukkit.event.block.BlockPhysicsEvent(org.bukkit.craftbukkit.block.CraftBlock.at(level, pos), org.bukkit.craftbukkit.block.data.CraftBlockData.fromData(state), org.bukkit.craftbukkit.block.CraftBlock.at(level, sourcePos));
    level.getCraftServer().getPluginManager().callEvent(event);

    if (event.isCancelled()) {
        return;
    }
} // Canvas - Skip BlockPhysicsEvent if no listeners
state.handleNeighborChanged(level, pos, neighborBlock, orientation, movedByPiston);
```

说明：不要使用 Leaf 的 `ServerLevel.hasPhysicsEvent` 字段，避免额外状态同步和跨 patch 依赖。

- [ ] **步骤 3：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 4：重建 Minecraft source patch**

按项目 patch 流程运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/level/redstone/NeighborUpdater.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
```

预期：rebuild 成功，`canvas-server/minecraft-patches/base/` 中生成对应 patch 改动。

- [ ] **步骤 5：提交补丁**

运行：

```bash
git add canvas-server/minecraft-patches/base
git commit -m "perf: skip block physics event without listeners"
```

预期：生成一个独立 commit。

---

## 任务 4：移植 PreCreatureSpawnEvent 无 listener 跳过

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0264-Skip-PreCreatureSpawnEvent-if-no-listeners.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Skip PreCreatureSpawnEvent\|PreCreatureSpawnEvent.getHandlerList" -n canvas-server/src/minecraft/java/net/minecraft/world/level/NaturalSpawner.java canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改第一次 PreCreatureSpawnEvent 调用点**

在 `NaturalSpawner` 第一个 `PreCreatureSpawnEvent` 创建点外层加入：

```java
if (com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent.getHandlerList().getRegisteredListeners().length != 0) { // Canvas - Skip PreCreatureSpawnEvent if no listeners
    com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent event = new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(
        org.bukkit.craftbukkit.util.CraftLocation.toBukkit(pos, level),
        org.bukkit.craftbukkit.entity.CraftEntityType.minecraftToBukkit(entityType),
        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL
    );
    if (!event.callEvent()) {
        if (event.shouldAbortSpawn()) {
            return PreSpawnStatus.ABORT;
        }
        return PreSpawnStatus.CANCELLED;
    }
} // Canvas - Skip PreCreatureSpawnEvent if no listeners
```

保留当前源码中的参数、缩进和已有 Paper 注释；只添加外层 listener 检查。

- [ ] **步骤 3：修改第二次 PreCreatureSpawnEvent 调用点**

在同文件第二个 `PreCreatureSpawnEvent` 创建点外层加入相同的 listener 检查。保持当前源码已有参数不变。

- [ ] **步骤 4：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/level/NaturalSpawner.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "perf: skip creature pre-spawn event without listeners"
```

预期：生成一个独立 commit。

---

## 任务 5：移植 VehicleEntityCollisionEvent 无 listener 跳过

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/boat/AbstractBoat.java`
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java`
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java`
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0311-Skip-VehicleEntityCollisionEvent-if-no-listeners.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Skip VehicleEntityCollisionEvent\|VehicleEntityCollisionEvent.getHandlerList" -n canvas-server/src/minecraft/java/net/minecraft/world/entity/vehicle canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改 AbstractBoat**

把 `AbstractBoat` 中两个形如：

```java
if (!this.isPassengerOfSameVehicle(entity)) {
```

的 `VehicleEntityCollisionEvent` 外层判断改为：

```java
if (!this.isPassengerOfSameVehicle(entity) && org.bukkit.event.vehicle.VehicleEntityCollisionEvent.getHandlerList().getRegisteredListeners().length > 0) { // Canvas - Skip VehicleEntityCollisionEvent if no listeners
```

- [ ] **步骤 3：修改 AbstractMinecart**

在 `AbstractMinecart` 的 `canCollideWithBukkit` 或等价方法中，在创建 `VehicleEntityCollisionEvent` 前加入：

```java
if (org.bukkit.event.vehicle.VehicleEntityCollisionEvent.getHandlerList().getRegisteredListeners().length == 0) {
    return true; // Canvas - Skip VehicleEntityCollisionEvent if no listeners
}
```

在其他矿车碰撞处理点，把事件创建包进：

```java
if (org.bukkit.event.vehicle.VehicleEntityCollisionEvent.getHandlerList().getRegisteredListeners().length > 0) { // Canvas - Skip VehicleEntityCollisionEvent if no listeners
    org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
        (org.bukkit.entity.Vehicle) this.getBukkitEntity(),
        entity.getBukkitEntity()
    );
    if (!collisionEvent.callEvent()) return;
} // Canvas - Skip VehicleEntityCollisionEvent if no listeners
```

保留当前源码中的 `return` 或 `continue` 语义，不把 Leaf 的上下文直接复制到不匹配的位置。

- [ ] **步骤 4：修改 NewMinecartBehavior**

在每个遍历实体的局部作用域中先定义：

```java
final boolean hasVehicleEntityCollisionEvent = org.bukkit.event.vehicle.VehicleEntityCollisionEvent.getHandlerList().getRegisteredListeners().length > 0; // Canvas - Skip VehicleEntityCollisionEvent if no listeners
```

然后把对应事件创建包进：

```java
if (hasVehicleEntityCollisionEvent) { // Canvas - Skip VehicleEntityCollisionEvent if no listeners
    org.bukkit.event.vehicle.VehicleEntityCollisionEvent collisionEvent = new org.bukkit.event.vehicle.VehicleEntityCollisionEvent(
        (org.bukkit.entity.Vehicle) this.minecart.getBukkitEntity(),
        entity.getBukkitEntity()
    );
    if (!collisionEvent.callEvent()) continue;
} // Canvas - Skip VehicleEntityCollisionEvent if no listeners
```

- [ ] **步骤 5：修改 OldMinecartBehavior**

使用和 `NewMinecartBehavior` 相同的局部 boolean 方式。对 `return`、`continue` 和 `push` 的控制流保持当前源码语义。

- [ ] **步骤 6：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 7：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/entity/vehicle/boat/AbstractBoat.java net/minecraft/world/entity/vehicle/minecart/AbstractMinecart.java net/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior.java net/minecraft/world/entity/vehicle/minecart/OldMinecartBehavior.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "perf: skip vehicle collision event without listeners"
```

预期：生成一个独立 commit。

---

## 任务 6：移植 MobEffectUtil#getDigSpeedAmplification 优化

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/effect/MobEffectUtil.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0214-Optimise-MobEffectUtil-getDigSpeedAmplification.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Optimise MobEffectUtil\|digEffect\|conduitEffect" -n canvas-server/src/minecraft/java/net/minecraft/world/effect/MobEffectUtil.java canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改生成源码**

把 `getDigSpeedAmplification` 方法实现改为：

```java
public static int getDigSpeedAmplification(LivingEntity entity) {
    int digAmplifier = 0;
    int conduitAmplifier = 0;
    MobEffectInstance digEffect = entity.getEffect(MobEffects.HASTE);
    if (digEffect != null) {
        digAmplifier = digEffect.getAmplifier();
    }

    MobEffectInstance conduitEffect = entity.getEffect(MobEffects.CONDUIT_POWER);
    if (conduitEffect != null) {
        conduitAmplifier = conduitEffect.getAmplifier();
    }

    return Math.max(digAmplifier, conduitAmplifier);
}
```

- [ ] **步骤 3：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 4：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/effect/MobEffectUtil.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "perf: optimize dig speed amplification lookup"
```

预期：生成一个独立 commit。

---

## 任务 7：移植 Inventory iterator 移除优化

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/player/Inventory.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0190-Remove-iterators-from-Inventory.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Remove iterators from Inventory\|EQUIPMENT_SLOTS_SORTED_BY_INDEX" -n canvas-server/src/minecraft/java/net/minecraft/world/entity/player/Inventory.java canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：确认装备槽数组名称**

打开 `Inventory.java` 顶部，确认是否已有：

```java
private static final EquipmentSlot[] EQUIPMENT_SLOTS_SORTED_BY_INDEX
```

如果没有，新增一个按 `EquipmentSlot.getIndex()` 排序的静态数组。代码形态：

```java
private static final EquipmentSlot[] EQUIPMENT_SLOTS_SORTED_BY_INDEX = EQUIPMENT_SLOT_MAPPING.values().stream()
    .sorted(java.util.Comparator.comparingInt(EquipmentSlot::getIndex))
    .toArray(EquipmentSlot[]::new); // Canvas - Remove iterators from Inventory
```

如果当前源码已有等价数组，复用现有数组，不新增重复字段。

- [ ] **步骤 3：修改 `clearOrCountMatchingItems` 附近的装备遍历**

把：

```java
for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOT_MAPPING.values()) {
```

改为：

```java
for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) { // Canvas - Remove iterators from Inventory
```

- [ ] **步骤 4：修改 `isEmpty`**

把 `this.items` 的增强 for 改为 indexed loop：

```java
for (int i = 0; i < this.items.size(); i++) {
    ItemStack itemStack = this.items.get(i);
    if (!itemStack.isEmpty()) {
        return false;
    }
}

for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) {
    if (!this.equipment.get(equipmentSlot).isEmpty()) {
        return false;
    }
}
```

- [ ] **步骤 5：修改 `contains(ItemStack)`、`contains(TagKey<Item>)` 和 `contains(Predicate<ItemStack>)`**

目标代码形态：

```java
for (int i = 0; i < this.items.size(); i++) {
    ItemStack itemStack = this.items.get(i);
    if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
        return true;
    }
}
for (EquipmentSlot equipmentSlot : EQUIPMENT_SLOTS_SORTED_BY_INDEX) {
    ItemStack itemStack = this.equipment.get(equipmentSlot);
    if (!itemStack.isEmpty() && ItemStack.isSameItemSameComponents(itemStack, stack)) {
        return true;
    }
}
```

对 `TagKey<Item>` 和 `Predicate<ItemStack>` 版本保持当前条件，只替换遍历方式。

- [ ] **步骤 6：修改 `fillStackedContents`**

把增强 for 改为：

```java
for (int i = 0; i < this.items.size(); i++) {
    contents.accountSimpleStack(this.items.get(i));
}
```

- [ ] **步骤 7：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 8：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/entity/player/Inventory.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "perf: remove inventory iterator allocations"
```

预期：生成一个独立 commit。

---

## 任务 8：移植 NamespacedKey toString/hashCode 缓存

**文件：**
- 修改：`canvas-api/src/main/java/org/bukkit/NamespacedKey.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-api/paper-patches/features/0021-Cache-namespacedKey-toString-and-hash.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "Cache namespacedKey\|keyStr\|private int hash" -n canvas-api/src/main/java/org/bukkit/NamespacedKey.java canvas-api/paper-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：新增缓存字段**

在 `namespace` 和 `key` 字段后新增：

```java
private String keyStr; // Canvas - Cache NamespacedKey toString
private int hash; // Canvas - Cache NamespacedKey hashCode
```

说明：保持非 `final`，用于 lazy cache；不使用 `volatile`，因为结果由不可变字段计算，竞态下重复计算也等价。

- [ ] **步骤 3：修改 `hashCode()`**

把方法改为：

```java
@Override
public int hashCode() {
    int hash = this.hash;
    if (hash == 0) {
        hash = (31 * this.namespace.hashCode()) + this.key.hashCode();
        this.hash = hash;
    }
    return hash;
}
```

- [ ] **步骤 4：修改 `toString()`**

把方法改为：

```java
@Override
public String toString() {
    String keyStr = this.keyStr;
    if (keyStr == null) {
        keyStr = this.namespace + ':' + this.key;
        this.keyStr = keyStr;
    }
    return keyStr;
}
```

- [ ] **步骤 5：编译 API**

运行：

```bash
./gradlew :canvas-api:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 6：重建 API patch 并提交**

先查看可用任务：

```bash
./gradlew tasks --all | grep -i "rebuild.*api\|api.*patch"
```

若存在 `rebuildApiPatches` 或项目等价任务，运行该任务。若项目只支持统一 rebuild，则运行：

```bash
./gradlew rebuildAllServerPatches
```

然后提交：

```bash
git add canvas-api/paper-patches/base
git commit -m "perf: cache namespaced key string and hash"
```

预期：生成一个独立 commit。若 rebuild 任务名不同，在提交信息中记录实际使用的任务名。

---

## 任务 9：移植 MC-301114 CombatTracker 内存泄漏修复

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/damagesource/CombatTracker.java`
- 使用：`canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0313-Fix-MC-301114-Combat-Tracker-memory-leak.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "MC-301114\|EvictingRingList\|maxCombatEntries" -n canvas-server/src/minecraft/java/net/minecraft/world/damagesource/CombatTracker.java canvas-server/minecraft-patches/base canvas-server/src/main/java/io/canvasmc/canvas || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改 CombatTracker 字段初始化**

把 `entries` 字段从直接初始化：

```java
public final List<CombatEntry> entries = Lists.newArrayList();
```

改为字段声明：

```java
private static final int MAX_COMBAT_ENTRIES = 10240; // Canvas - Fix MC-301114 Combat Tracker memory leak
public final List<CombatEntry> entries;
```

- [ ] **步骤 3：修改构造函数**

在 `CombatTracker(LivingEntity mob)` 构造函数中初始化 entries：

```java
this.entries = new io.canvasmc.canvas.util.collection.EvictingRingList<>(MAX_COMBAT_ENTRIES); // Canvas - Fix MC-301114 Combat Tracker memory leak
```

保留已有：

```java
this.mob = mob;
this.paperCombatTracker = new io.papermc.paper.world.damagesource.PaperCombatTrackerWrapper(this);
```

- [ ] **步骤 4：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/damagesource/CombatTracker.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "fix: cap combat tracker entries"
```

预期：生成一个独立 commit。

---

## 任务 10：移植 SkeletonHorse trap NPE 修复

**文件：**
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/SkeletonHorse.java`
- 修改：`canvas-server/src/minecraft/java/net/minecraft/world/entity/animal/equine/SkeletonTrapGoal.java`
- 来源：`C:/Users/Esters/AppData/Local/Temp/leaf-research/leaf-server/minecraft-patches/features/0322-fix-skeleton-horse-trap-NPE.patch`

- [ ] **步骤 1：确认当前没有等价逻辑**

运行：

```bash
grep -R "doTrap\|fix skeleton horse trap\|eligiblePlayers = null" -n canvas-server/src/minecraft/java/net/minecraft/world/entity/animal/equine canvas-server/minecraft-patches/base || true
```

预期：如果已经存在等价逻辑，记录为「已存在」并跳过本任务的代码修改；否则继续。

- [ ] **步骤 2：修改 SkeletonTrapGoal**

在字段区新增：

```java
public boolean doTrap = true; // Canvas - Fix skeleton horse trap NPE
```

把 `canUse()` 的返回条件改为：

```java
return this.doTrap && !(this.eligiblePlayers = this.horse.level().findNearbyBukkitPlayers(this.horse.getX(), this.horse.getY(), this.horse.getZ(), 10.0, net.minecraft.world.entity.EntitySelector.PLAYER_AFFECTS_SPAWNING)).isEmpty();
```

在类末尾新增：

```java
@Override
public void stop() {
    this.eligiblePlayers = null; // Canvas - Release skeleton trap eligible players
}
```

- [ ] **步骤 3：修改 SkeletonHorse#setTrap**

把设置 trap 的逻辑改为：

```java
this.isTrap = isTrap;
if (isTrap) {
    this.goalSelector.addGoal(1, this.skeletonTrapGoal);
    this.skeletonTrapGoal.doTrap = true; // Canvas - Fix skeleton horse trap NPE
} else {
    this.skeletonTrapGoal.doTrap = false; // Canvas - Fix skeleton horse trap NPE
}
```

说明：不要在 `else` 分支调用 `goalSelector.removeGoal(this.skeletonTrapGoal)`，避免在 goal 迭代期间修改 goal 集合。

- [ ] **步骤 4：编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：重建并提交补丁**

运行：

```bash
cd canvas-server/src/minecraft/java
git add net/minecraft/world/entity/animal/equine/SkeletonHorse.java net/minecraft/world/entity/animal/equine/SkeletonTrapGoal.java
git commit --fixup=file
git rebase --autosquash HEAD~2
cd ../../../..
./gradlew rebuildMinecraftSourcePatches
git add canvas-server/minecraft-patches/base
git commit -m "fix: avoid skeleton horse trap goal mutation"
```

预期：生成一个独立 commit。

---

## 任务 11：整体验证和收尾

**文件：**
- 检查：`canvas-server/minecraft-patches/base/*.patch`
- 检查：`canvas-api/paper-patches/base/*.patch`
- 检查：`canvas-server/src/main/java/io/canvasmc/canvas/util/collection/EvictingRingList.java`
- 检查：`canvas-server/src/test/java/io/canvasmc/canvas/util/collection/EvictingRingListTest.java`

- [ ] **步骤 1：检查是否误引入 Leaf/Gale 包名**

运行：

```bash
grep -R "org.dreeam.leaf\|org.galemc.gale" -n canvas-server/src/main/java canvas-server/minecraft-patches/base canvas-api/paper-patches/base || true
```

预期：没有新增匹配。如果有匹配，必须移除或说明是既有内容。

- [ ] **步骤 2：检查是否误引入旧 Config.INSTANCE**

运行：

```bash
grep -R "Config.INSTANCE" -n canvas-server/src/main/java canvas-server/minecraft-patches/base canvas-api/paper-patches/base || true
```

预期：本批次不得新增 `Config.INSTANCE`。如果输出中有既有匹配，记录文件名，不在本批次扩大修改。

- [ ] **步骤 3：运行单元测试**

运行：

```bash
./gradlew :canvas-server:test --tests io.canvasmc.canvas.util.collection.EvictingRingListTest
```

预期：`EvictingRingListTest` PASS。

- [ ] **步骤 4：运行编译验证**

运行：

```bash
./gradlew :canvas-server:compileJava :canvas-api:compileJava
```

预期：`BUILD SUCCESSFUL`。

- [ ] **步骤 5：运行完整 jar 构建**

运行：

```bash
./gradlew createMojmapPublisherJar
```

预期：`BUILD SUCCESSFUL`，产物位于 `canvas-server/build/libs/`。如果失败，记录完整失败任务和首个错误；确认是否由本批次改动导致。

- [ ] **步骤 6：检查最终工作区**

运行：

```bash
git status --short
```

预期：工作区干净。如果有验证产物或未提交 patch，提交应提交的源码/patch；不要提交 build 输出。

- [ ] **步骤 7：收尾提交（仅当有计划勾选或记录改动）**

如果执行过程中更新了本计划复选框或补充了跳过原因，运行：

```bash
git add docs/superpowers/plans/2026-05-31-leaf-first-batch-port.md
git commit -m "docs: update Leaf first batch port progress"
```

预期：文档进度有独立 commit。若计划文档未改动，跳过此步骤。

---

## 自检

### 规格覆盖度

- 第一批 8 个候选补丁均有对应任务：任务 3 至任务 10。
- patch 系统准备和基线验证由任务 1 覆盖。
- `CombatTracker` 所需工具类和测试由任务 2 覆盖。
- 批次级验证由任务 11 覆盖。
- 高风险 async、tracker、parallel world ticking 明确不在计划范围内。

### 占位符扫描

- 未使用「待定」「TODO」「后续实现」作为执行占位。
- 每个代码任务都包含具体目标代码或明确修改形态。
- 每个验证步骤都有具体命令和预期结果。

### 类型一致性

- `EvictingRingList` 包名统一为 `io.canvasmc.canvas.util.collection`。
- `CombatTracker` 引用统一为 `io.canvasmc.canvas.util.collection.EvictingRingList`。
- `NamespacedKey` 缓存字段统一为 `keyStr` 和 `hash`。
- Skeleton horse trap 开关统一为 `doTrap`。
