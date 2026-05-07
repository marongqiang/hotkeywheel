# Hotkey Wheel - 快捷键轮盘

一个 Minecraft Fabric 模组，当多个快捷键绑定到同一个组合键时，显示一个圆形轮盘菜单，让玩家通过鼠标选择并触发对应的快捷键动作。

## 功能

- **自动扫描**：自动检测所有已注册的快捷键（包括原版和其他模组），按组合键分组。
- **轮盘菜单**：按下组合键时，在屏幕中央显示圆形轮盘，每个扇区对应一个快捷键。
- **鼠标确认**：松开打开键后轮盘保持打开；移动鼠标高亮扇区，**鼠标左键单击**确认并触发对应动作；点在中心取消区则关闭且不触发。
- **双环布局**：当同一组合键有超过 8 个快捷键时，自动分为内外两环显示，并提供内外圈的清晰边界与分割线。
- **图标支持**：每个扇区可显示物品图标或自定义 PNG 纹理，帮助快速识别。
- **配置界面**：通过模组配置界面（按 `O` 键打开）管理：
  - 启用/禁用特定组合键的轮盘
  - 排除不需要显示的快捷键
  - 自定义标签和图标
  - 轮盘 UI 参数（分割线开关、内外圈透明度、图标缩放倍率等）
- **UI 预览与拖拽排序**：在“此按键绑定的功能”界面点击“UI预览”，打开轮盘预览界面，可直接拖拽扇区调整顺序（保存到配置）。
- **MaLiLib 兼容**：支持 MaLiLib 模组的快捷键（可选依赖）。

## MRQ 修复

本分支基于上游代码进行了以下 bug 修复：

- **勾选标记重复渲染**: `HotkeyWheelConfigScreen` 和 `ComboFunctionDetailScreen` 中 `drawTextWithShadow` 被重复调用两次，已删除冗余调用
- **日志规范化**: `HotkeyWheelConfigStore` 中 5 处 `printStackTrace()` 改为 `HotkeyWheelClient.LOGGER.warn`
- **Pattern 缓存**: `LabelShortener` 中 `replaceAll("\\s+")` 提取为 `PATTERN_WHITESPACE` 静态编译
- **纹理缓存清理**: `HotkeyWheelCustomIconUtil` 添加 `clearTextures()` 方法，支持资源重载后清理过期纹理

## 当前版本

- **Minecraft**：1.20.1（Fabric）
- **模组版本**：**0.5.2**（与 `gradle.properties` 中 `mod_version` 一致）
- **Fabric Loader**：见 `gradle.properties` 中 `fabric_loader_version`（当前约 0.19.x）

## 重要说明（触发逻辑）

当你把多个功能“都绑定到同一个物理按键/组合键”时，单纯用“物理按键事件”去触发会发生冲突（例如多个快捷栏键都绑在 `1` 上，或背包与 Xaero 路径点都绑在 `B` 上）。

本模组在确认选择后**只触发被选中的那一个 `KeyBinding`**，不会对同一物理键上的其它绑定调用 `KeyBinding.onKeyPressed` 之类的“全键广播”。对原版绑定，通过反射递增**当前绑定**的 `timesPressed`（兼容 Yarn 名与运行时常用的 intermediary 字段名），避免误触发同键上的其它模组功能。

另外：

- **快捷栏 `key.hotbar.X`**：会直接设置 `selectedSlot`，保证轮盘选择一定能切到指定栏位。
- **打开聊天栏等 GUI 的功能**：轮盘关闭时不会错误地长期锁定鼠标，避免出现“闪一下又关不掉”的问题。
- **与打开键同键的条目**：若选中的动作与打开轮盘的键相同，会在主线程延迟触发按键逻辑，并在短时间内吞掉该键的重复按下，减少与轮盘输入冲突。

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/)
2. 安装 [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
3. 将模组 jar 文件放入 `.minecraft/mods` 文件夹
4. （可选）安装 [MaLiLib](https://www.curseforge.com/minecraft/mc-mods/malilib) 以支持 MaLiLib 快捷键

## 使用

1. **按下**你配置了多个绑定的那个键或组合键（例如仅 `B`、或 `Ctrl+1` 等，取决于你在控制里怎么绑）。
2. 若该组合下存在 **2 个及以上** 可显示条目，模组会进入“待打开”状态；此时该次按下通常不会交给原版处理（避免先误触其中一个功能）。
3. **松开**该键后，屏幕中央出现轮盘；光标会解锁，便于移动鼠标。
4. 移动鼠标，**高亮**你要用的扇区。
5. **鼠标左键单击**该扇区：确认并触发对应快捷键；单击中心取消区则关闭轮盘且不触发。
6. 轮盘打开时，可按 **Esc** 或 **再次按下打开时用的那个键** 关闭轮盘。

## 配置

按 `O` 键打开模组配置界面，可以：

- **组合键管理**：查看所有已检测到的组合键，启用/禁用轮盘。
- **快捷键管理**：查看每个组合键下的所有快捷键，排除不需要的，设置图标。
- **UI 预览**：在“此按键绑定的功能”界面点击“UI预览”，进入轮盘预览界面并拖拽扇区调整位置顺序。
- **外观设置**：通过 `config/hotkeywheel.json` 的 `Generic.ui` 节点调整轮盘 UI 参数。
- **标签设置**：自定义快捷键的显示名称。
- **图标设置**：
  - 物品图标：在功能列表中为每个动作指定 `minecraft:...` 或其他模组物品 id。
  - 自定义图标：将 PNG 放入 `config/hotkeywheel/icons/`，并在图标选择界面选择（会写入 `custom:<文件名.png>`）。

### hotkeywheel.json（关键字段）

- **`Generic.iconItemIds`**：`actionId -> iconId`
  - 物品图标示例：`"key.attack": "minecraft:netherite_sword"`
  - 自定义 PNG 示例：`"key.attack": "custom:sword.png"`
- **`Generic.ui`**：轮盘 UI 参数（预留给玩家自定义）
  - `showDividers`：是否显示分割线
  - `innerRingAlpha` / `outerRingAlpha`：内外圈底板透明度
  - `iconScaleInner` / `iconScaleOuter`：内外圈图标缩放倍率
  - `debugLogging`：调试日志/调试标记开关（用于对齐与拾取问题排查）

> 提示：如果你之前的配置里保存了旧的 `iconScaleInner`（例如 0.88/0.96），它会覆盖新默认值。
> 新版本会在加载配置时自动迁移旧默认值到当前默认值，避免出现“更新后看起来没变化”。

## 依赖

- **必需**：Fabric API（版本 0.83.1+）
- **可选**：MaLiLib（用于支持 MaLiLib 快捷键）

## 兼容性

- Minecraft 1.20.x
- Fabric Loader 0.14.21+
- 支持任何注册了 `KeyBinding` 的模组

## 技术细节

- 使用 **Mixin** 注入 `Keyboard`（键盘）与 **Mouse**（鼠标），在轮盘打开时拦截输入，避免其它模组抢先处理左键。
- 轮盘打开期间用 **GLFW** 轮询鼠标左键边沿，与键盘逻辑一致、时序可控。
- 通过 `KeyBinding` 系统自动扫描所有已注册的快捷键。
- 配置存储在 `config/hotkeywheel.json` 文件中。
- 轮盘渲染使用自定义顶点缓冲，支持渐变、分层底板、分割线与基础动画效果。

## 许可证

LGPL-3.0

## 贡献

欢迎提交 Issue 和 Pull Request。

## 致谢

- 灵感来源于 Masa 的 MaLiLib 模组系列。
- 感谢 Fabric 社区提供的工具和 API。
