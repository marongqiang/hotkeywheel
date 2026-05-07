# Hotkey Wheel — 快捷键轮盘

**版本**: 0.5.2 | **Minecraft**: 1.20.1 Fabric | **Java**: 17

当多个功能被绑定到同一个物理按键时，按下该键会弹出一个圆形轮盘菜单。用户通过鼠标选择扇区，精确触发被选中的那一个功能，彻底解决快捷键冲突问题。

---

## 目录

- [核心功能](#核心功能)
- [轮盘 UI](#轮盘-ui)
- [组合键扫描](#组合键扫描)
- [快捷键触发](#快捷键触发)
- [图标系统](#图标系统)
- [配置系统](#配置系统)
- [MaLiLib 兼容](#malilib-兼容)
- [输入拦截机制](#输入拦截机制)
- [使用方法](#使用方法)
- [编译与安装](#编译与安装)
- [配置详解](#配置详解)
- [已知限制](#已知限制)
- [MRQ 分支改进](#mrq-分支改进)
- [与原版的区别](#与原版的区别)
- [许可证](#许可证)

---

## 核心功能

### 解决的问题

在 Minecraft 中，多个功能可能被绑定到同一物理键。例如：
- 背包、Xaero 路径点、小地图都绑在 `B` 键上
- 多个快捷栏槽位都绑在 `1` 键上

普通情况下，按 `B` 会同时触发所有绑在该键上的功能，导致冲突。Hotkey Wheel 在检测到冲突时：
1. **按下键时不触发任何功能** — 进入"待打开"状态（Tap-Arm）
2. **松开键后弹出轮盘** — 圆形菜单显示所有冲突的选项
3. **鼠标选择扇区 → 左键确认** — 只触发被选中的那一个功能
4. **点击中心取消区** — 不触发任何功能

### 触发逻辑细节

1. 用户按下某个键 / 组合键（如 `B`、`Ctrl+1`）
2. 模组扫描发现该组合键下存在 2 个及以上可显示条目
3. 该次按下被拦截，不会触发原版或其他模组的功能
4. 用户松开该键 → 屏幕中央出现轮盘
5. 光标解锁，用户移动鼠标高亮目标扇区
6. 左键点击扇区 → 触发被选中的功能 → 轮盘关闭
7. 左键点击中心取消区 → 不触发 → 轮盘关闭
8. 按 Esc 或再次按下开启键 → 轮盘关闭

### 精确触发机制

确认选择后，模组**只触发被选中的那一个 `KeyBinding`**：

- **原版绑定**: 通过反射递增目标 KeyBinding 的 `timesPressed` 计数器，不调用 `KeyBinding.onKeyPressed()`，避免广播到同键上的其他绑定
- **快捷栏 `key.hotbar.X`**: 直接设置 `player.getInventory().selectedSlot`，不经过 KeyBinding 系统
- **MaLiLib 热键**: 通过反射调用 `toggleBooleanValue()` 或 `IHotkeyCallback.onKeyAction()`
- **与开启键同键的条目**: 推迟到主线程下一帧执行，避免与轮盘开关逻辑冲突。阻止该键在 550ms 内再次触发轮盘开启

---

## 轮盘 UI

### 双环布局

- 当同一组合键下有 **≤ 8 个**条目时使用**单环**
- 当 **> 8 个**条目时自动切换为**双环**
  - 内环容纳约 40% 的条目
  - 外环容纳约 60% 的条目
  - 外环相对于内环错开半个扇区角度
- 内外环之间有视觉间隙（`ringGapPx` 可配置）

### 层叠渲染

轮盘使用自定义顶点缓冲渲染，分为多层：
1. **底板层**: 深色半透明填充（透明度可配置）
2. **高亮覆盖层**: 鼠标悬停扇区变为洋红色/玫瑰色高亮
3. **分割线层**: 金色/白色扇区分割线（可开关）
4. **图标层**: 物品图标或自定义 PNG 纹理
5. **文字层**: 扇区标签和工具提示

### 动画效果

| 动画 | 时长 | 说明 |
|------|------|------|
| 扇区悬停高亮 | 150ms | 鼠标移动到新扇区时的缓动过渡 |
| 扇区确认闪烁 | 130ms | 点击扇区后的白色闪烁反馈 |
| 图标选中放大 | 即时 | 选中状态下图标额外放大 8% |

### 中心取消区

轮盘正中心为圆形死区。鼠标移入中心区域时所有高亮取消，点击关闭轮盘且不触发任何功能。

---

## 组合键扫描

### 自动扫描

- 遍历 `GameOptions.allKeys` 中所有已注册的 KeyBinding
- 根据每个绑定的物理按键和修饰键自动计算**组合键 ID**（如 `CTRL,SHIFT,B`）
- 将相同组合键 ID 的绑定归到一组
- 结果按"有冲突优先"（≥2 条目的排前面）、条目数降序、键名升序排列

### MaLiLib 扫描（可选）

如果安装了 MaLiLib，通过反射调用其 `IKeybindManager` 接口扫描所有 MaLiLib 热键，合并到分组中。

### 排除规则

- 自动排除模组自身打开配置的键（默认 `O`）
- 用户可通过配置按组合键排除特定条目

---

## 快捷键触发

确认选择后，仅触发被选中的功能：

| 绑定类型 | 触发方式 |
|---------|---------|
| 原版 KeyBinding | 反射递增 `timesPressed`，不广播 |
| `key.hotbar.X` | 直接 `selectedSlot = X` |
| MaLiLib `IHotkeyTogglable` | 调用 `toggleBooleanValue()` |
| MaLiLib 非标准热键 | 反射查找 `toggleBooleanValue()` 或递归查找嵌套目标 |
| 以上都不适用 | 调用 `IHotkeyCallback.onKeyAction(PRESS)` |

触发后下一 tick 自动将绑定状态设为 `false`，防止卡键。

---

## 图标系统

### 物品图标

为每个动作指定 `minecraft:...` 格式的物品 ID，轮盘扇区渲染物品图标。内置 22 个原版快捷栏默认图标映射（如 `key.attack` → 金剑，`key.inventory` → 箱子）。

### 自定义 PNG 图标

- 将 PNG 放入 `config/hotkeywheel/icons/`
- 在图标选择器中选择 → 以 `custom:<文件名.png>` 格式写入配置
- PNG 被加载为 `NativeImageBackedTexture`，在扇区中渲染

### 图标尺寸

- 内环和外环图标缩放倍率分别可配置（`iconScaleInner`/`iconScaleOuter`，范围 0.5–2.0）
- 选中状态下图标额外放大 8%
- 图标支持投影阴影和高亮边框

---

## 配置系统

配置文件: `config/hotkeywheel.json`

### 配置键说明

| 配置键 | 类型 | 说明 |
|--------|------|------|
| `hotkeyWheelEnabledCombos` | 列表 | 启用轮盘的组合键白名单 |
| `hotkeyWheelDisabledPerCombo` | 列表 | 每个组合键下排除的特定条目 |
| `labelOverrides` | 映射 | 自定义标签覆盖 `actionId → 文本` |
| `iconItemIds` | 映射 | 图标映射 `actionId → "minecraft:xxx"` 或 `"custom:xxx.png"` |
| `hiddenFromWheel` | 列表 | 全局隐藏，不在任何轮盘显示 |
| `wheelActionSortOrder` | 列表 | UI 预览拖拽保存的自定义排列顺序 |
| `shortLabelMaxChars` | 整数 | 标签最大字符数（默认 20） |

### UI 参数 (`Generic.ui`)

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `showDividers` | bool | true | 扇区分割线开关 |
| `innerRingAlpha` | float | 0.35 | 内环底板透明度 |
| `outerRingAlpha` | float | 0.30 | 外环底板透明度 |
| `iconScaleInner` | float | 1.10 | 内环图标缩放 |
| `iconScaleOuter` | float | 1.18 | 外环图标缩放 |
| `ringGapPx` | float | 0–48 | 双环间隙 |
| `theme` | string | "tactical" | 主题名称 |
| `debugLogging` | bool | false | 调试日志 |

### MaLiLib 配置迁移

首次运行时自动检测 `malilib.json` 中的旧配置，迁移到 `hotkeywheel.json` 后删除旧条目。

---

## MaLiLib 兼容

- **可选依赖**: 不强制要求，通过 FabricLoader 运行时检测
- **扫描集成**: MaLiLib 热键纳入组合键分组
- **触发集成**: MaLiLib 热键可通过 `ReflectedMalilibWheelAction` 在轮盘中触发
- **ID 格式**: `mli:<ModName>:<HotkeyName>`
- 全部通过反射访问，编译时无 MaLiLib 类型依赖

---

## 输入拦截机制

通过 Mixin 注入实现输入拦截：

| Mixin | 注入点 | 行为 |
|-------|--------|------|
| `KeyboardMixin` | `Keyboard.onKey()` HEAD | 先交给轮盘系统处理，若被消费则取消原版事件 |
| `MouseMixin` | `Mouse.onMouseButton()` HEAD | 轮盘打开时吞没左键事件，防止其他模组抢先 |

轮盘确认使用 GLFW 直接轮询左键状态（上升沿检测），绕过可能的模组拦截。

---

## 使用方法

1. **触发轮盘**: 将多个功能绑定到同一个键，按该键松手即弹出轮盘
2. **选择功能**: 移动鼠标到目标扇区
3. **确认**: 左键点击扇区
4. **取消**: 左键点击中心区域 / 按 Esc / 再次按开启键
5. **配置**: 按 `O` 键打开配置界面，管理组合键和条目

---

## 编译与安装

```bash
git clone https://github.com/marongqiang/hotkeywheel.git
cd hotkeywheel
./gradlew build
# 编译产物位于 build/libs/
```

**依赖**: Fabric API 0.83.1+, Fabric Loader 0.14.21+（MaLiLib 可选）

---

## 配置详解

### 配置界面（`O` 键）

- **主界面**: 所有组合键列表，可启用/禁用、查看详情
- **详情界面**: 每个组合键下的条目管理，可排除、自定义标签和图标
- **UI 预览**: 全尺寸轮盘预览，可拖拽扇区调整顺序
- **图标选择器**: 物品/自定义 PNG 选择，支持搜索过滤

---

## 已知限制

- **修饰键组合**: 不支持两个以上非修饰键组合（如 Ctrl+Shift 组合OK，A+B 组合OK，但 A+B+C 不支持）
- **轮盘条目数**: 过多条目（>24）会导致扇区过小难以点击
- **自定义图标**: 大尺寸 PNG（>256×256）浪费内存且视觉效果无提升
- **资源重载（F3+T）**: 自定义图标可能暂时消失，需要重新打开轮盘恢复
- **与其他拦截鼠标的模组**: 可能有交互问题（如某些 Map 模组）

---

## MRQ 分支改进

### bug 修复
| 问题 | 位置 | 修复内容 |
|------|------|---------|
| **勾选标记重复渲染** | `HotkeyWheelConfigScreen.java` + `ComboFunctionDetailScreen.java` | 删除重复 `drawTextWithShadow` 调用 |
| **配置保存日志** | `HotkeyWheelConfigStore.java` | 5 处 `printStackTrace()` 改为 `LOGGER.warn` |
| **纹理缓存泄漏** | `HotkeyWheelCustomIconUtil.java` | 添加 `clearTextures()` 方法 |

### 性能优化
| 优化 | 位置 | 效果 |
|------|------|------|
| 空格正则缓存 | `LabelShortener.java` | `replaceAll` → `PATTERN_WHITESPACE.matcher().replaceAll()` |

### 构建修复
- 添加 `gradlew` wrapper 支持

---

## 与原版的区别

| 方面 | 原版 | MRQ |
|------|------|-----|
| 勾选渲染 | 重复绘制 | ✅ 单次绘制 |
| 异常日志 | println 到 stderr | ✅ logger.warn |
| 正则 | 每次编译 | ✅ 静态缓存 |
| 纹理缓存 | 无清理机制 | ✅ clearTextures() |
| 构建 | 无 gradlew | ✅ gradlew 支持 |

---

## 许可证

LGPL-3.0

---

## 相关链接

- [malilib MRQ](https://github.com/marongqiang/malilib_MRQ)（可选依赖，支持 MaLiLib 热键）
- [Tweakeroo MRQ](https://github.com/marongqiang/tweakeroo_MRQ)
- [MiniHUD MRQ](https://github.com/marongqiang/minihud_MRQ)
