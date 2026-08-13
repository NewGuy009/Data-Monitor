# 落地计划（Implementation Plan）

> 配套 `ProjectTarget.md`。目标：Android 端股票行情监控与交易提醒 App。
> 技术基线见 `Development Notes.md`（Kotlin 2.2.10 + Compose BOM 2026.02.01 + AGP 9.3.0，minSdk 24 / target 36）。

---

## 1. 技术选型

| 领域 | 选型 | 说明 |
| --- | --- | --- |
| UI | Jetpack Compose + Material 3 | 已有基础模板，直接沿用 |
| 架构 | MVVM + Clean Architecture（`ui` / `domain` / `data` 三层） | Compose + ViewModel + Repository |
| 异步 | Kotlin Coroutines + Flow | 行情推流、规则事件流 |
| 依赖注入 | Hilt | ViewModel、Repository、WorkManager 注入 |
| 网络 | Retrofit + OkHttp + kotlinx-serialization | 财经接口 JSON 解析；OkHttp 拦截器处理 UA/限频 |
| 本地存储 | Room（结构化数据） + DataStore（偏好/设置） | 自选股、规则、K 线缓存 |
| 后台任务 | WorkManager（周期抓取） + ForegroundService（盘中高频轮询） | 应对 Android 后台限制 |
| 通知 | NotificationManager + NotificationChannel | 支持分组、免打扰 |
| 图表 | MPAndroidChart 或 Vico（Compose 原生） | 优先 Vico，Compose 友好 |
| 日志 | Timber | 统一日志入口 |
| 测试 | JUnit4 + MockK + Turbine + Compose UI Test | Flow 与 Composable 都要覆盖 |

## 2. 模块划分

采用单工程多模块结构，`app` 作为壳工程，业务按 feature 拆分。

```
:app                    # 壳工程，负责 DI 装配、导航
:core:common            # 通用工具、扩展、Result 类型
:core:designsystem      # Compose 主题、组件库
:core:database          # Room DAO/Entity
:core:datastore         # 偏好设置
:core:network           # Retrofit + OkHttp + 数据源接口
:core:model             # Domain 层数据模型（Stock、Quote、Rule、Alert…）
:data:market            # 行情数据仓库（多源聚合，含缓存策略）
:data:rule              # 规则存储与匹配
:data:watchlist         # 自选股仓库
:feature:watchlist      # 自选股列表 UI
:feature:detail         # 个股详情（K 线、盘口）
:feature:rule           # 规则编辑器 UI
:feature:alert          # 提醒历史 UI
:feature:settings       # 设置页
:service:monitor        # 后台监控 Service + WorkManager
```

> MVP 阶段可先不拆得这么细，用 `app` 单模块 + 包分层实现；等代码量上来后再抽离。

## 3. 分阶段里程碑

### M0 · 项目基础设施（1 周）

> 面向新手的分步做法：把 M0 拆成 5 个小步骤，每一步都能编译通过、能跑起来看到效果，出问题也容易定位。

#### M0.1 · 建立包骨架 + Application 类 + 导航（先不引入 DI/网络）

- **目标**：让 App 能跑出两个占位页面，用底部导航切换；建立分层目录。
- **步骤**：
  1. 在 `com.example.mysecondapp` 下新建包：`ui/`、`ui/navigation/`、`ui/watchlist/`、`ui/detail/`、`domain/`、`data/`、`di/`。
  2. 新建 `MySecondApp : Application` 类（暂时空实现），在 `AndroidManifest.xml` 的 `<application>` 加 `android:name`。
  3. 在 `libs.versions.toml` 加 `androidx-navigation-compose` 依赖别名。
  4. 写 `AppNavHost`（`NavHost` + 2 个占位 Composable：`WatchlistScreen`、`SettingsScreen`）。
  5. `MainActivity` 里用 `Scaffold` + `NavigationBar` 承载 `AppNavHost`。
- **验收**：Run 之后能看到底部两个 Tab，可切换，页面各显示一行文字（如"自选股（占位）"、"设置（占位）"）。

#### M0.2 · 引入 Hilt（依赖注入）

- **目标**：能用 `@HiltAndroidApp` / `@AndroidEntryPoint` / `@HiltViewModel` 走通一次注入。
- **步骤**：
  1. `libs.versions.toml` 加 `hilt-android`、`hilt-compiler`、`androidx-hilt-navigation-compose`；插件加 `com.google.dagger.hilt.android` 和 `com.google.devtools.ksp`。
  2. 顶层 `build.gradle.kts` 声明这两个插件为 `apply false`。
  3. `app/build.gradle.kts` 应用 `hilt` + `ksp` 插件，添加对应依赖（`hilt-compiler` 用 `ksp(...)`）。
  4. `MySecondApp` 加 `@HiltAndroidApp`；`MainActivity` 加 `@AndroidEntryPoint`。
  5. 写一个 `WatchlistViewModel : ViewModel`，用 `@HiltViewModel` + `@Inject constructor()`，在 `WatchlistScreen` 里用 `hiltViewModel()` 取到，把 ViewModel 里的一个字符串显示出来。
- **验收**：能编译，页面显示的字符串来自 ViewModel，说明 DI 生效。

#### M0.3 · 引入网络栈（Retrofit + OkHttp + kotlinx-serialization）

- **目标**：能通过 Retrofit 调一个真实公开接口拿到数据。
- **步骤**：
  1. `libs.versions.toml` 加 `retrofit`、`okhttp`、`okhttp-logging-interceptor`、`kotlinx-serialization-json`、`retrofit-kotlinx-serialization-converter`；插件加 `org.jetbrains.kotlin.plugin.serialization`。
  2. `app/build.gradle.kts` 应用 serialization 插件；加依赖。
  3. `di/NetworkModule.kt` 提供 `OkHttpClient` / `Json` / `Retrofit`。
  4. 写一个最小接口 `TencentStockApi`（例如 `GET https://qt.gtimg.cn/q=sh600000`，返回 `String`——腾讯是文本协议，先不解析）。
  5. `WatchlistViewModel` 注入这个 API，在 `init { viewModelScope.launch { ... } }` 里调一次，把响应前 100 个字符显示出来。
- **验收**：Run 后页面出现真实响应文本，如 `v_sh600000="1~浦发银行~600000~...`；说明网络栈通了。

#### M0.4 · 引入本地存储（Room + DataStore）

- **目标**：能把一条自选股数据存进 Room，读出来展示；能用 DataStore 存一个偏好设置。
- **步骤**：
  1. `libs.versions.toml` 加 `androidx-room-runtime`、`androidx-room-ktx`、`androidx-room-compiler`（KSP）、`androidx-datastore-preferences`。
  2. 在 `data/local/` 新建 `WatchlistEntity`、`WatchlistDao`、`AppDatabase`（`@Database(entities=[WatchlistEntity::class], version=1)`）。
  3. `di/DatabaseModule.kt` 用 `Room.databaseBuilder(...)` 提供单例。
  4. 写 `SettingsRepository`，包装一个 `DataStore<Preferences>`，示例：`refreshIntervalSeconds`（默认 15）。
  5. ViewModel 里插一条测试数据、`Flow` 收集展示。
- **验收**：卸载重装 App，数据还在（说明 Room 落地）；改一次偏好、重启，值保持（说明 DataStore 落地）。

#### M0.5 · 补齐支撑库（Timber + WorkManager + 协程）

- **目标**：日志、后台任务能跑；协程作用域约定统一。
- **步骤**：
  1. `libs.versions.toml` 加 `timber`、`androidx-work-runtime-ktx`、`androidx-hilt-work`、`androidx-hilt-common`。
  2. `MySecondApp.onCreate()` 里 `Timber.plant(DebugTree())`。
  3. 用 `HiltWorkerFactory` 让 WorkManager 支持 Hilt 注入（`Configuration.Provider`）。
  4. 写一个 `PingWorker`，每 15 分钟打一条 `Timber.i("ping")`，`MainActivity` 里注册一次周期任务。
- **验收**：Logcat 里能看到 `Timber` 打的日志；`adb shell dumpsys jobscheduler | findstr mysecondapp` 能看到调度记录。

> 每完成一个小步骤就 **commit 一次**，方便回滚。
> Sync 报错时优先查：AGP/Kotlin/KSP/Compose 版本对齐、`libs.versions.toml` alias 拼写、`build.gradle.kts` 里是否漏加插件。

- 配置 CI（可选，GitHub Actions/本地脚本），至少能跑 `./gradlew build test`。

### M1 · 自选股 + 行情列表（1~2 周）

- 数据源调研：优先接入**新浪财经**（`hq.sinajs.cn`）和**腾讯财经**（`qt.gtimg.cn`），字段少、免鉴权、协议简单。
- Repository 层做多源容错：任一源失败自动降级。
- Room 存自选股（`code`、`market`、`name`、`group`、`order`）。
- Compose 列表页：显示最新价、涨跌幅、涨跌额，下拉刷新 + 定时刷新。
- 搜索添加：先做本地股票代码字典，再接搜索接口。

### M2 · 个股详情 + K 线（1~2 周）

- 分时图：分钟线数据源（腾讯 `web.ifzq.gtimg.cn` 可用）。
- 日/周/月 K 线：同源接入，Room 缓存最近 N 根。
- 图表用 Vico（Compose 原生，主题一致性好）。
- 盘口五档、成交明细（有则展示，无则占位）。

### M3 · 规则引擎（2 周，最核心）

- 规则模型：
  ```
  Rule(
    id, stockCode, type, params, cooldown, enabled, createdAt
  )
  ```
  `type` 枚举：`PRICE_ABOVE` / `PRICE_BELOW` / `CHANGE_PCT` / `VOLUME_SURGE` / `MA_CROSS` …
- 表达式化设计：`params` 用 JSON，方便后续扩展组合条件（AND/OR）。
- 匹配器：`interface RuleMatcher { fun match(quote: Quote, history: List<Kline>): Boolean }`
- 触发后写入 `AlertRecord` 表，同时触发通知；`cooldown` 防抖。

### M4 · 后台监控与通知（1~2 周）

- 盘中（09:15–15:00 A 股 / 09:15–16:00 港股 / 21:30–04:00 美股）用 **ForegroundService** + 短轮询（5–15 秒可配置）。
- 盘后/非交易时段用 **WorkManager** 周期任务，减少功耗。
- 通知分渠道：价格提醒、涨跌幅提醒、系统消息。支持免打扰时段（DataStore 存）。
- 电量优化：Doze/App Standby 场景要在文档里标注对策。

### M5 · 分析能力（2 周+）

- 内置指标计算：MA/EMA/MACD/KDJ/RSI/BOLL（纯 Kotlin，写在 `core:analytics`）。
- 持仓页：手动录入成本，展示盈亏、市值分布饼图。
- 简单复盘：把 `AlertRecord` 和后续价格走势做对照，评估规则有效性。

### M6 · 打磨与发布（1 周）

- 深色模式、动态取色（已内建）适配检查。
- 无障碍：contentDescription、字号缩放、TalkBack。
- Play/国内应用商店合规文案与截图。
- Crash 上报（可选 Firebase Crashlytics 或 Sentry）。

## 4. 数据源梳理

| 源 | 用途 | 备注 |
| --- | --- | --- |
| 新浪财经 `hq.sinajs.cn/list=sh600000` | 实时/延时快照 | GBK 编码，字符串协议，需 UA |
| 腾讯财经 `qt.gtimg.cn/q=sh600000` | 实时/延时快照 + 分时 | 字符串协议，字段丰富 |
| 腾讯 K 线 `web.ifzq.gtimg.cn/appstock/app/*` | 日/周/月/分钟 K 线 | JSON，注意频率 |
| 东方财富 `push2.eastmoney.com` | 备选源、板块与资金流 | JSON，接口较多 |
| 财经资讯 RSS | 消息面（后置） | 数据清洗成本较高 |

> 所有源都要在网络层加：UA 伪装、限频（令牌桶）、失败重试、返回值校验。**只做行情展示与提醒，不做交易委托**。

## 5. 关键风险与对策

| 风险 | 对策 |
| --- | --- |
| Android 后台限制导致提醒漏发 | 盘中用 ForegroundService + 常驻通知；文档指导用户加白名单 |
| 数据源接口变动或被封 | Repository 多源冗余；接口层抽象成 `MarketDataSource`，可插拔 |
| GBK/编码坑（新浪源） | OkHttp 拦截器统一转码 |
| 电量与流量 | 频率可配置；非交易时段自动降频；WiFi/移动网络策略区分 |
| 合规 | 全局免责声明；不做荐股；数据仅本地存储 |
| K 线数据体量 | Room 只缓存近 N 根，历史按需拉取 |

## 6. 代码约定

- 目录：`ui/` `domain/` `data/` 严格分层；`domain` 不依赖 Android 框架。
- 命名：Composable 用大驼峰名词短语（`WatchlistScreen`）；ViewModel 用 `XxxViewModel`；Repository 用 `XxxRepository`。
- 错误处理：Repository 层返回 `Result<T>` 或 `Flow<Result<T>>`，UI 只做展示。
- 提交信息：约定式提交（`feat:` / `fix:` / `refactor:` / `docs:`）。
- 每个 feature 目录必须包含 README（本目录职责、依赖、扩展点）。

## 7. 近期动作（下一步做什么）

1. 在 `libs.versions.toml` 中登记 M0 阶段所需依赖别名（Hilt、Retrofit、Room、Navigation、Coroutines、Timber、Vico）。
2. 在 `app` 模块下建立包骨架：`ui/`、`domain/`、`data/`、`di/`。
3. 建 `MarketDataSource` 接口 + 一个新浪源的 `SinaMarketDataSource` 试跑通 "输入股票代码 → 拿到快照"。
4. 用 Compose 写一个最简单的行情列表页承载调试结果。

> 每完成一个里程碑，就把总结与偏差写回 `Development Notes.md` 或本文件末尾的 "变更记录" 区。
