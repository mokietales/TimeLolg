# Time Log — UI/UX 设计稿

| | |
|---|---|
| **产品** | Time Log（极简时间记录） |
| **平台** | Android（Jetpack Compose / Material 3） |
| **风格基线** | Apple Human Interface Guidelines · Linear |
| **当前版本** | v2.0（versionCode 2） |
| **最低系统** | Android 8.0（API 26） |
| **最近构建** | 2026-05-19 · `app-debug.apk` 19MB |

---

## 1. 设计原则

1. **当下优先（Now First）**。打开 App 第一眼就回答："我现在在做什么？"，所以默认 Tab 是 Now，且首屏没有任何运营、引导、分发模块。
2. **少即是多（Restraint）**。借鉴 Linear：去除装饰性边框、阴影、emoji；同一屏只允许一个强调色出现。
3. **Apple 风格的留白与字号节奏**。借鉴 iOS Settings：水平 24dp 安全边距、区块之间 32dp 留白、行内 12–14dp 行内间距，加上 iOS Large Title 的字号节奏。
4. **数字必须 tabular**。所有时间戳、时长、计数都使用 tabular monospace，避免数字宽度抖动。
5. **数据是主角**。Card 投影、ripple 高光、彩色 chip 都默认关闭，只有内容本身和它的层级带来视觉。

---

## 2. 信息架构

App 共 4 个底部 Tab。整体没有左右抽屉，没有二级导航栈，所有功能可以两次点击触达。

| Tab | 用途 | 默认 | 可操作 |
|---|---|---|---|
| **Now** | 此刻在做什么 | ✅ | 启动 / 暂停 / 停止 / 写笔记 / 新建 Task |
| **Timeline** | 按日的事件流 | | 浏览，按日聚合，看时长 |
| **Review** | 按 Task 聚合的统计 | | 切换 Today / Week / Month / All Time |
| **Events** | 原始事件列表 | | 过滤 · 编辑 Task / 笔记 · 删除 |

**为什么是 Now / Timeline / Review / Events**

- Now 是动作（do）
- Timeline 是叙事（observe）：发生的顺序
- Review 是反思（reflect）：投入分布
- Events 是档案（manage）：原始记录可改可删

这 4 件事互不重叠，覆盖了"记录类工具"用户的全部基本需求。

---

## 3. 视觉系统

### 3.1 色彩 Token

下表所有色值已经落地在 `ui/theme/Color.kt`。

| Token | Light | Dark | 用途 |
|---|---|---|---|
| background | `#FFFFFF` | `#000000` | App 大底色 |
| surface | `#FFFFFF` | `#000000` | 内容默认背景 |
| surfaceContainer | `#F7F7F8` | `#111113` | 极轻底色（inset 列表行） |
| surfaceVariant | `#F2F2F7` | `#1C1C1E` | iOS systemGroupedBackground |
| surfaceContainerHigh | `#F2F2F7` | `#1C1C1E` | 选中态高层底色 |
| onSurface | `#0A0A0A` | `#FFFFFF` | 主文 |
| onSurfaceVariant | `#6E6E73` | `#98989F` | 次文 / 标签 |
| onSurfaceMuted | `#8E8E93` | `#6E6E73` | 占位、辅助 |
| outline | `#E5E5EA` | `#2C2C2E` | 分隔线 |
| outlineVariant | `#F0F0F2` | `#1C1C1E` | 进度条 track |
| primary | `#5E6AD2` | `#8C92F0` | Linear indigo · 唯一强调色 |
| error | `#E53935` | `#FF6B6B` | 删除 / 危险 |

**使用约束：**
- 全屏默认只允许 1 处 primary，多于 1 处时自降为 onSurface
- 不使用 surface tonal palette（不让 Material3 自动给 card 染色）
- Dark mode 真黑（`#000000`）+ 系统级 OLED 友好

### 3.2 字体

系统字体 + tabular monospace。已落地在 `ui/theme/Type.kt`。

| Style | 字号 | 字重 | 字距 | 用途 |
|---|---|---|---|---|
| `displayLarge` | 72 | Light | -1.5 | Now Tab 大计时器 |
| `displayMedium` | 48 | Light | -1.0 | （备用） |
| `displaySmall` | 32 | Regular | -0.5 | （备用） |
| `headlineLarge` | 34 | SemiBold | -0.6 | iOS Large Title（备用） |
| `headlineMedium` | 28 | SemiBold | -0.4 | 各 Tab 页面标题 |
| `headlineSmall` | 22 | SemiBold | -0.2 | 区块标题 |
| `titleLarge` | 20 | SemiBold | -0.15 | 对话框标题 |
| `titleMedium` | 17 | Medium | -0.1 | 列表行主文（小） / Active Task 名 |
| `titleSmall` | 15 | Medium | 0 | Day Header |
| `bodyLarge` | 17 | Regular | -0.05 | 列表行主文 |
| `bodyMedium` | 15 | Regular | 0 | 二级文本、按钮 |
| `bodySmall` | 13 | Regular | 0 | 时间戳、note 摘要 |
| `labelLarge` | 15 | Medium | 0.1 | 分段控件、按钮 |
| `labelMedium` | 13 | Medium | 0.4 | 时间数字、计数 |
| `labelSmall` | 11 | Medium | 0.8 | Eyebrow（页头小字） |

**Tabular numbers**：所有时长 / 时间戳通过 `fontFeatureSettings = "tnum 1, zero 1"` 强制等宽数字（详见 `ui/components/Components.kt::TabularNumFeature`）。

### 3.3 间距 & 形状

```
水平内容边距     24 dp
inset list 边距   16 dp
区块之间          32 dp
行内间距          12 dp
计时器组件        72 dp（vertical breathing room）
PageTitle 顶部    12 dp
PageTitle 底部    24 dp

shape.extraSmall  6  dp
shape.small       10 dp（chips、buttons）
shape.medium      14 dp（inset list 容器）
shape.large       20 dp
shape.extraLarge  28 dp（dialog）

bottom tab bar    48 dp
hairline divider  0.5 dp
```

### 3.4 反馈 / 动效

- 仅使用 `clickable` 默认 ripple，不主动加 elevation overlay
- Tab 切换无动画（保持瞬时切换的克制感）
- Review 行展开使用 `animateContentSize()` 默认曲线
- Now 页面 Active 状态切换没有 fade，直接替换布局（强调"现在"这一时刻的存在感）

---

## 4. 屏 1 — Now

> 默认首页。回答"我此刻在做什么"。

### 4.1 状态机

```
        ┌────────────┐  pick task    ┌──────────┐  toggle    ┌──────────┐
        │   Idle     │ ─────────────►│  Active  │ ◄────────► │  Paused  │
        │ (no rec)   │               │ (timing) │            │ (frozen) │
        └────────────┘               └────┬─────┘            └────┬─────┘
              ▲                           │ stop                  │ stop
              │ stop / discard            ▼                       ▼
              └────────────────  Persist EventEntity ◄────────────┘
```

会话状态 `TrackingSession` 同时由 `mutableStateOf` 提供给 UI、由 SharedPreferences 持久化。即使应用被系统杀掉，下次启动仍能恢复一个进行中的会话。

| 字段 | 含义 |
|---|---|
| `startMs` | 会话起点（epoch ms），null 表示空闲 |
| `taskId` / `taskName` | 关联的 Task |
| `pausedAtMs` | 当前暂停起点，null 表示未暂停 |
| `pausedAccumulatedMs` | 之前累计暂停的时长 |
| `note` | 一句话笔记，实时持久化 |

**有效时长** = `(now - startMs) - pausedAccumulatedMs - currentPauseWindow`

### 4.2 空闲布局（Idle）

```
┌────────────────────────────────────────┐
│  TUESDAY, MAY 19           ← labelSmall│
│  Now                       ← headlineMd│
│                                        │
│  Nothing tracked.          ← titleMd   │
│  Choose what you’re doing. ← bodyMd 灰 │
│                                        │
│  ┌───────────────────────────────┐     │
│  │ • Coding              →      │     │
│  │ ─────────────────────         │     │
│  │ • Reading             →      │     │
│  │ ─────────────────────         │     │
│  │ • Exercise            →      │     │
│  │ ─────────────────────         │     │
│  │ + New activity        →      │     │
│  └───────────────────────────────┘     │
└────────────────────────────────────────┘
```

- iOS inset-grouped list 容器（圆角 14dp，`surfaceContainer` 底色）
- 圆点 ●（8dp）使用 primary，提示"这是可以投入的事项"
- 点一行立刻进入 Active —— 没有"开始"按钮，减少一次点击
- "+ New activity" 行展开为内联输入：`Activity name [Cancel] [Start]`

### 4.3 进行中布局（Active）

```
┌────────────────────────────────────────┐
│  TUESDAY, MAY 19                        │
│  Now                                    │
│                                         │
│                                         │
│           00:24:13                ←72sp │
│                                         │
│           Coding                  ←17sp │
│           Started at 14:03    ←13sp 灰 │
│                                         │
│                                         │
│                                         │
│              Add a note…                │
│                                         │
│                                         │
│           ⏸          ⏹                │
│         Pause       Stop          ←icon │
│                                         │
│              Discard              ←灰 link │
└────────────────────────────────────────┘
```

- 计时器：72sp、tabular monospace、Light 字重，字距 -1.5sp，上下各留 56dp
- Note 输入：无边框、居中、placeholder "Add a note…"，光标用 primary
- 圆形大图标按钮：56dp icon + label，Stop 上 primary 染色
- "Discard" 是底部低调 link，避免误操作

### 4.4 暂停状态

- 计时器停在当前秒
- "Started at 14:03" → "**Paused**"（primary 文字色）
- Pause 按钮 label/icon 切换为 Resume

---

## 5. 屏 2 — Timeline

> 按日聚合的事件流。回答"今天 / 这周 / 这个月我都做了什么"。

### 5.1 布局

```
┌────────────────────────────────────────┐
│  TIMELINE                               │
│  12 events                              │
│                                         │
│  Today                          1h 24m  │
│  ─────────────                          │
│  14:03  ●   Coding              24m     │
│  14:27  │   refactor session            │
│         │                                │
│  13:30  ●   Lunch               25m     │
│  13:55  │                                │
│                                         │
│  Yesterday                      4h 02m  │
│  ─────────────                          │
│  21:10  ●   Reading             40m     │
│  21:50  │   Atomic Habits ch 5          │
│         │                                │
│  ...                                    │
└────────────────────────────────────────┘
```

### 5.2 信息层次

每行从左到右：

```
┌── tabular HH:mm（开始）       ─┐
│   tabular HH:mm（结束 · muted） │
└── 8dp dot（primary） + 28dp │── primary task name + tabular shortDuration
                                │   note 摘要（最多 2 行 · muted 13sp）
```

### 5.3 Day Header

```
Today                                  1h 24m
```

- 日期使用 "Today" / "Yesterday" / "Friday, May 16" 自适应
- 右侧 tabular shortDuration 当日总时长

---

## 6. 屏 3 — Review

> 按 Task 聚合的统计。回答"我把时间投入到了哪里"。

### 6.1 布局

```
┌────────────────────────────────────────┐
│  REVIEW                                 │
│  18h 42m                                │
│                                         │
│ ┌────┬────┬────┬─────┐                 │
│ │Tdy │Wk  │Mth │ All │ ← segmented     │
│ └────┴────┴────┴─────┘                 │
│                                         │
│  Coding                       8h 20m   │
│  ████████████░░░░░░░░░░░░       12     │
│  ─────────────────────────              │
│                                         │
│  Reading                      4h 10m   │
│  ██████░░░░░░░░░░░░░░░░░░░       6     │
│  ─────────────────────────              │
│                                         │
│  Exercise                     1h 12m   │
│  ██░░░░░░░░░░░░░░░░░░░░░░░       3     │
│  ─────────────────────────              │
└────────────────────────────────────────┘
```

### 6.2 分段控件

- 4 个等宽分段：Today / This Week / This Month / All Time
- 选中态：底色升一层（surfaceContainer → surfaceContainerHigh）+ 文字色 onSurface
- 未选中：onSurfaceVariant 文字色
- 默认值 = This Week
- 选择跨进程恢复（`rememberSaveable`）

### 6.3 单行结构

```
┌─ task name ────────────────────  total tabular ─┐
│                                                  │
│ ───────── 4dp thin bar ────────  count tabular  │
│                                                  │
│ ─────── 0.5dp inset divider ──────────────────  │
```

- 进度条 fraction = task 时长 / 当前范围内全部时长
- 计数 = 该 Task 在范围内的事件数
- 一行总占 14dp 上下 padding

### 6.4 数据计算

- 在客户端按 `EventTaskAllocationRow` flow 实时聚合
- 每个事件按其分配的所有 Task 各计 100%（多对多，这套计法目前简化版只用单 Task 但保留扩展能力）
- 排序按总时长降序

---

## 7. 屏 4 — Events

> 原始事件列表 + 编辑/删除。回答"那条记录写错了，我要改"。

### 7.1 布局

```
┌────────────────────────────────────────┐
│  EVENTS                                 │
│  37 records                             │
│                                         │
│  [All] [Today] [Week] [With note]       │
│                                         │
│  Coding                          24m    │
│  May 19 · 14:03 – 14:27                 │
│  refactor session                       │
│  ─────────────────────────              │
│                                         │
│  Lunch                           25m    │
│  May 19 · 13:30 – 13:55                 │
│  ─────────────────────────              │
│                                         │
│  ...                                    │
└────────────────────────────────────────┘
```

### 7.2 过滤器

`FilterChip` 横排，4 选 1：All · Today · Week · With note。chip 复用 surfaceContainer 配色，无 border，圆角 10dp。

### 7.3 编辑对话框

点任意行 → 弹 AlertDialog：

```
┌────────────────────────────────────┐
│ Edit event                          │
│                                     │
│ Tuesday, May 19 · 14:03 – 14:27    │
│ 24m                                 │
│                                     │
│ Activity                            │
│ [Coding] [Reading] [Exercise] ...  │
│                                     │
│ ┌─Note──────────────────────────┐  │
│ │ refactor session              │  │
│ └────────────────────────────────┘  │
│                                     │
│ 🗑 Delete event           ← error 色 │
│                                     │
│           [Cancel]  [Save]          │
└────────────────────────────────────┘
```

- Activity 用 chip 横滑切换归属 Task
- Note 多行可改
- 删除入口走二次确认（再弹一个 AlertDialog）
- 时间段（start/end）当前**不可改**，是已知限制（见 §11 Future Work）

---

## 8. 全局组件

| 组件 | 职责 | 文件 |
|---|---|---|
| `PageTitle` | 每屏顶部 eyebrow + headlineMd 标题 | `ui/components/Components.kt` |
| `SectionHeader` | 区块小标题（labelMedium + 大写） | 同上 |
| `InsetGroup` | iOS 圆角列表容器 | 同上 |
| `ListRow` | 标准列表行（leading / primary / secondary / trailing） | 同上 |
| `RowDivider` | 0.5dp 分隔线（带可选缩进） | 同上 |
| `EmptyState` | 空状态居中 title + subtitle | 同上 |
| `ThinBar` | 4dp 圆角进度条 | 同上 |
| `BottomTabs` | 底部 4-Tab 容器 | `ui/TimeLogApp.kt` |
| `TabularNumFeature` | tabular numbers 字符串 const | `ui/components/Components.kt` |

---

## 9. 数据模型

```
Task                      Event
───────────────           ───────────────────
id           PK           id           PK
name         UNIQUE       startMs
createdAtMs               endMs
                          note
                          createdAtMs

EventTaskAllocation              TaskInheritance（暂未在 UI 暴露）
─────────────────────────        ────────────────────────────────
(eventId, taskId)        PK      (childTaskId, parentTaskId)  PK
```

- 一个 Event 当前默认 100% 归属 1 个 Task；schema 支持 N-N 多归属，未来可在 Now / Edit 页扩展
- `TaskInheritance` 表与 EventDao 中的递归 CTE 查询保留了"父 Task 自动汇总子 Task 时长"的能力，等后续 Tasks 管理界面回归时启用

详见 `data/EventDao.kt`、`data/TaskDao.kt`。

---

## 10. 关键交互流程

### 10.1 启动 → 进入 Now（Idle）→ 开始记录

```
launch app
  └─ Now Tab
       └─ tap "Coding" row
             └─ TrackingSession.start(taskId, "Coding")
                   └─ UI flips to Active
                         └─ ticker 1Hz 更新 elapsedMs
```

### 10.2 进行中 → 切到 Timeline / Review / Events

`TrackingSession` 是 Activity-scope 单例，切 Tab 不会停止计时；切回 Now 仍是 Active 状态。

### 10.3 进行中 → 暂停 → 继续 → 停止

```
[Active] tap Pause
  └─ pausedAtMs = now
[Paused] tap Resume
  └─ pausedAccumulatedMs += (now - pausedAtMs)
  └─ pausedAtMs = null
[Active] tap Stop
  └─ snapshot = stopAndConsume()
  └─ insert EventEntity + EventTaskAllocationEntity
  └─ UI flips to Idle
```

### 10.4 编辑历史事件

```
Events Tab → tap row
  └─ AlertDialog
       ├─ change Activity（chip 切换）
       ├─ change Note
       ├─ Delete → 二次确认 → deleteEventCascade
       └─ Save → updateEventAndAllocations
```

---

## 11. 已知限制 / 路线图

| 优先级 | 项 |
|---|---|
| P0 | 计时中提供 foreground service + 通知，让锁屏 / 杀进程不影响计时 |
| P0 | 编辑事件支持改 start / end 时间（datetime picker） |
| P1 | Tasks 管理页：长按重命名 / 删除；多父继承在 UI 层暴露 |
| P1 | 一个 Event 多 Task 归属在 Now 起始时可勾选 |
| P2 | 周报 / 月报导出 PDF / CSV |
| P2 | Widget：锁屏小组件 + 主屏快捷开始 |
| P3 | 云同步、多端 |

---

## 12. 文件目录速查

```
app/src/main/java/com/mokie/timelogdemo/
├── MainActivity.kt              ← edge-to-edge + theme + TimeLogApp
├── data/
│   ├── TimeLogDatabase.kt       ← Room DB v3
│   ├── TaskEntity.kt
│   ├── TaskInheritanceEntity.kt
│   ├── TaskDao.kt
│   ├── EventEntity.kt
│   ├── EventTaskAllocationEntity.kt
│   └── EventDao.kt              ← insert / update / delete cascade
└── ui/
    ├── TimeLogApp.kt            ← Scaffold + 4 Tab
    ├── TrackingSession.kt       ← 计时状态 + SharedPreferences 恢复
    ├── theme/
    │   ├── Color.kt
    │   ├── Type.kt
    │   └── Theme.kt
    ├── components/Components.kt
    ├── util/TimeFormat.kt
    ├── now/NowScreen.kt
    ├── timeline/TimelineScreen.kt
    ├── review/ReviewScreen.kt
    └── events/EventsScreen.kt
```

---

*文档最后更新：2026-05-19*
