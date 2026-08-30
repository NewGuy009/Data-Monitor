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

### M1 · 自选股 + 行情列表

- **数据契约**
  - 定义行情基础模型：`Quote`、`QuoteSnapshot`、`WatchlistItem`。
  - 定义数据源接口：`MarketDataSource`、`WatchlistRepository`、`MarketRepository`。
  - 约定统一错误类型和返回值结构，UI 层只负责状态展示。
- **数据源接入**
  - 接入腾讯快照源 `qt.gtimg.cn/q=...`。
  - 接入新浪快照源 `hq.sinajs.cn/list=...`。
  - 统一处理 UA、超时、重试、限频和返回值校验。
  - 统一处理文本编码和字符串协议解析。
- **多源聚合**
  - Repository 实现主源与备源切换。
  - 任一数据源失败时自动降级到另一数据源。
  - 相同股票请求支持短期内存缓存，避免重复请求。
- **自选股存储**
  - 扩展自选股字段：`code`、`market`、`name`、`group`、`order`。
  - 提供新增、删除、查询、排序接口。
  - 为后续分组和筛选预留字段。
- **行情列表 UI**
  - 展示股票名称、代码、最新价、涨跌幅、涨跌额。
  - 展示涨跌颜色、状态图标和更新时间。
  - 支持加载态、空态和错误态。
  - 支持下拉刷新和前台定时刷新。
- **搜索添加**
  - 先实现本地股票代码字典。
  - 再接入远程搜索接口。
  - 支持搜索结果加入自选并去重。
- **测试**
  - 增加腾讯/新浪解析器单元测试。
  - 增加 Repository 多源降级测试。
  - 增加 Room DAO 和数据映射测试。
  - 增加行情列表 Compose UI 测试。

### M2 · 个股详情 + K 线（1~2 周）

> 目标：从 M1 的“多股票最新快照”扩展为单只股票的“可查看、可缓存、可绘制的详情数据”。详情数据链路独立于 `MarketRepository`，避免 K 线刷新影响自选列表的刷新频率与内存缓存。

#### M2.1 · 建立详情数据契约

- 新增领域模型：`StockIdentity`、`IntradayPoint`、`IntradaySeries`、`Candle`、`CandlePeriod`、`OrderBookLevel`、`OrderBook`、`TradeTick`、`StockDetailSnapshot`。
- `CandlePeriod` 首期支持 `MINUTE`、`DAY`、`WEEK`、`MONTH`；模型统一使用毫秒时间戳、`Double` 价格和 `Long?` 成交量，屏蔽外部接口字符串格式。
- 新增 `StockDetailRepository`：负责详情快照、分时、K 线、盘口与成交明细；M1 的 `MarketRepository` 保持只负责自选行情列表。
- 复用 `MarketDataResult` / `MarketError` 作为网络和解析失败的统一返回结构；UI 不接触原始响应文本。
- **验收**：纯 Kotlin 单元测试能构造完整 `StockDetailSnapshot`，且 UI 层只依赖领域模型和仓库接口。

#### M2.2 · 接入腾讯详情数据源

- 新建 `TencentDetailApi`，与现有 `TencentStockApi` 共用 Retrofit/OkHttp 配置。
- 接入分钟线接口：`web.ifzq.gtimg.cn/appstock/app/minute/query?code=...`，解析交易时间、价格、累计成交量和均价。
- 接入复权 K 线接口：`web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=代码,周期,,,数量,qfq`，首期请求日/周/月数据；分钟 K 线按接口能力单独处理。
- 对 JSON 结构、空数组、停牌和字段缺失做显式校验；解析异常返回 `MarketError.ParseFailure`，不让异常穿透到 ViewModel。
- 接口访问继续沿用现有 UA、超时、日志和限频策略；详情页首次只在用户进入或主动刷新时请求。
- **验收**：为分钟线、日 K、周 K、月 K 的典型响应和空响应分别补充解析器单元测试。

#### M2.3 · 详情 Repository 与缓存策略

- 实现 `DefaultStockDetailRepository`，把腾讯原始数据映射为领域模型，并按“分时 / K 线 / 盘口”分别返回结果，避免一个可选接口失败导致整个详情页不可用。
- 内存缓存：分时使用短 TTL；同一股票、同一 K 线周期使用按参数键控的 TTL 缓存，防止切换图表时重复请求。
- 缓存读取顺序：内存命中 → Room 本地 K 线缓存 → 网络刷新；网络失败时优先展示可用的本地旧数据并标注更新时间。
- 盘口五档和成交明细属于增强数据：腾讯当前接口有稳定字段时解析展示，没有可用数据时返回空集合，由 UI 显示占位，不阻塞图表。
- **验收**：仓库测试覆盖内存命中、Room 命中、网络刷新、网络失败回退本地缓存四种路径。

#### M2.4 · Room K 线缓存与迁移

- 新增 `KlineEntity`：主键至少包含 `market`、`code`、`period`、`timestampMillis`；字段保存 OHLC、成交量、成交额、更新时间。
- 新增 `KlineDao`：按股票和周期读取、批量 upsert、限定最近 N 根、清理超量历史数据。
- `AppDatabase` 升级版本并新增 `MIGRATION_2_3`；保留现有 `WatchlistMigrations.MIGRATION_1_2`，数据库构建时注册完整迁移链。
- 首期缓存上限：日线 320 根、周线 260 根、月线 120 根、分钟线不持久化或限制为最近一个交易日，具体常量集中管理。
- **验收**：DAO 仪器测试覆盖 upsert、时间倒序查询、不同周期隔离和超量清理；从 v2 升级后原自选股数据仍可读取。

#### M2.5 · 详情导航与 ViewModel

- 新增 `feature/detail`（当前单模块对应 `ui/detail`）中的 `StockDetailViewModel`、`StockDetailScreen` 和 README。
- 自选股行情卡片支持点击；导航 route 只携带 `market` 与 `code`，名称等展示信息由仓库加载或从导航初始状态传入。
- `StockDetailUiState` 独立维护：基础报价、图表周期、分时状态、K 线状态、盘口状态、成交明细状态、刷新状态与错误提示。
- 首次进入默认请求实时快照、分时和日 K；切换周期只刷新对应 K 线；下拉刷新重新请求当前所见数据。
- **验收**：点击任一自选股能进入详情页；返回列表后保留原滚动位置和已加载行情。

#### M2.6 · Compose 图表与详情页面

- 新增 Vico Compose 依赖，封装 `IntradayChart` 与 `CandleChart`，页面不直接拼装第三方图表数据结构。
- 页面结构：顶部股票名称/代码与最新价，分时/K 线分段切换，周期切换（日/周/月），图表区域，OHLC/成交量摘要，盘口和成交明细区域。
- 涨跌配色继续沿用 M1 的 A 股惯例（涨红跌绿），图表无数据、加载、刷新失败均有稳定占位。
- 图表横轴显示交易时间/日期，纵轴显示价格；首期先保证折线和 K 线正确性，MA 等技术指标留给 M5。
- **验收**：Compose UI 测试覆盖加载态、分时态、K 线态、空盘口态、周期切换和返回操作；真机验证长数据量下滚动与旋转不崩溃。

#### M2.7 · 盘口、成交明细与质量收尾

- 解析并展示买一至买五、卖一至卖五的价格和委托量；成交明细展示时间、价格、手数和方向（数据源可用时）。
- 若数据源未提供稳定字段，详情页保留“暂无可用盘口数据”占位，接口能力通过数据源实现升级，不在 UI 写来源判断。
- 补充数据源解析、Repository 缓存回退、Room 迁移、ViewModel 状态流和 Compose UI 测试。
- 将接口限制、复权口径、缓存上限、无盘口数据的降级行为写入 `Development Notes.md` 变更记录。
- **验收**：`assembleDebug`、`testDebugUnitTest`、`compileDebugAndroidTestKotlin` 通过；在设备上检查至少一只沪市和一只深市股票的详情数据。

#### M2 建议落地顺序

1. M2.1 数据契约。
2. M2.2 腾讯分钟线与日 K 数据源及解析测试。
3. M2.3 Repository 内存缓存和失败回退。
4. M2.4 Room K 线缓存与迁移。
5. M2.5 导航、ViewModel 和详情页面骨架。
6. M2.6 Vico 分时图/K 线图。
7. M2.7 盘口、成交明细与完整测试收尾。

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

#### M3 任务范围与实施原则

M3 的第一目标是基于单只自选股的历史日 Bar 计算可解释的技术状态和规则信号，而不是直接引入横截面选股系统。首期分析链路为：`历史 Bar -> 数据质量校验 -> 指标/形态计算 -> 规则判断 -> 信号记录`。

- 首期以日线为分析主周期；周线和月线复用同一套契约作为趋势过滤条件。
- `Time` 分时数据仅为后续盘中规则预留，不将腾讯当前交易日分钟快照误用为可回测的历史分钟线。
- 因子负责计算事实值，规则负责解释条件，提醒负责投递结果；三者不得耦合为一个不可测试的判断函数。
- Vibe-Trading 风格的横截面 Alpha、多股票排名、基本面因子和因子有效性回测属于后续扩展，不阻塞单股提醒能力。

#### M3.1 历史 Bar 契约与数据质量

- 将当前 `Candle` 演进为分析可用的历史 Bar 输入：明确 `timestamp`、`period`、OHLC、`volume`、`turnover`、`adjustment`、`currency`、`volumeUnit`、`providerId` 和市场时区等语义。
- 明确日/周/月 Bar 的周期归属和收盘语义；分析默认只使用已确认的完整 Bar，盘中未收盘日线不得作为最终历史结论。
- 建立统一 `HistoricalBarValidator`：校验空数据、最小样本数、时间升序、重复时间戳、OHLC 关系、非有限值、负成交量/成交额，以及复权、币种、数量单位混用。
- 增加数据质量状态，例如 `COMPLETE`、`PARTIAL`、`GAPPED`、`INVALID`、`INSUFFICIENT_HISTORY`；不合格序列不得进入指标和规则计算。
- Repository 与 Room 在网络写入、缓存读出、分析调用三个边界都使用同一校验器；保留失败原因，禁止静默丢弃异常 Bar 后继续给出交易结论。
- 测试覆盖正常日/周/月线、OHLC 异常、倒序/重复日期、复权混用、单位混用、缺口、空数据和预热数据不足。

#### M3.2 分析契约、指标注册与计算上下文

- 定义 `HistoricalAnalysisInput`，只接收已经通过质量校验的单股 Bar 序列，并包含股票身份、周期、复权口径、数据源和分析截止时间。
- 定义 `Indicator` / `Factor` 接口和 `IndicatorRegistry`：每个计算项声明唯一 ID、名称、所需字段、最小预热 Bar 数、适用周期和输出类型。
- 定义完整的时间序列输出 `IndicatorSeries`，预热期使用明确的不可用状态，不用零值伪造结果；单点快照由时间序列最后一个已确认 Bar 派生。
- 禁止任何计算读取分析截止时间之后的 Bar；所有滚动窗口必须仅使用当前及过去数据，防止后续历史回放和回测出现未来数据泄漏。
- 首期不接入 Python 运行时或整个 Alpha Zoo。开源因子机制只作为 Kotlin 注册、元数据、预热和质量校验设计参考。

#### M3.3 基础单股指标与技术状态

- 实现并测试 `SMA`、`EMA`、成交量均线、`MACD`、`RSI`、`BOLL`、`ATR`、`OBV` 等基础指标，参数可配置但提供稳定默认值。
- 建立趋势状态：均线多头/空头排列、快慢线交叉、价格相对均线位置、ADX 或同等趋势强度过滤条件。
- 建立震荡与量价状态：超买超卖、布林收口/扩张、放量/缩量、量比、OBV 与价格方向一致或背离。
- 每个状态结果必须给出原始数值、阈值、使用的最后 Bar 时间和可读原因，供详情页与规则触发记录复用。
- 首期只输出计算结果和状态，不把任一指标单独定义为买入或卖出结论。

#### M3.4 K 线形态与结构识别

- 实现可独立测试的基础形态：阳包阴/阴包阳、长实体、长上影/下影、连续上涨/下跌、区间横盘和突破。
- 对金叉、死叉定义“发生于最近两根已确认 Bar 的交叉事件”，区分当前持续多头/空头状态，避免每天重复触发同一事件。
- 为顶背离/底背离预留统一的摆动高低点、价格序列与指标序列契约；首期先实现可解释的局部高低点识别，再在数据样本充分后接入 MACD/RSI 背离规则。
- 形态和结构结果必须携带涉及的 Bar 时间、价格区间、置信度或前置条件，不能只输出模糊字符串。

#### M3.5 规则模型、匹配器与去重

- 扩展 `Rule`：支持目标股票、周期、指标/形态条件、参数、启用状态、冷却时间、创建/更新时间和规则版本。
- 将规则条件建模为可组合表达式：`ALL`、`ANY`、`NOT` 与原子条件；原子条件包括价格阈值、涨跌幅、均线交叉、指标区间、量价状态和 K 线形态。
- 定义 `RuleEvaluation` / `SignalResult`：记录匹配与否、分析数据截止时间、命中的条件、引用指标值、触发方向、原因和数据质量状态。
- 按 `ruleId + stock + period + signalBarTimestamp + direction` 生成幂等键；冷却时间只用于抑制重复通知，不能覆盖已记录的不同 Bar 信号。
- M3 仅把触发结果写入本地 `AlertRecord`；Android 通知投递、后台轮询和盘中频率控制由 M4 实现。

#### M3.6 分析结果存储、详情展示与配置

- 新增 Room 存储规则、规则条件和信号记录；历史 Bar 仍复用现有 provider/period/adjustment 隔离的缓存身份。
- 定义分析结果的缓存策略：指标序列可按 Bar 更新时间失效或重算，不把瞬时 UI 状态当作长期事实保存。
- 在个股详情页增加分析摘要入口：展示当前趋势、关键指标值、最近命中/未命中规则和数据截止日期；不在 M3 承诺自动交易建议。
- 提供规则创建/编辑的最小界面，优先支持少量明确模板，例如均线金叉、RSI 超卖反转、放量突破和阳包阴；复杂表达式作为后续编辑器扩展。

#### M3.7 历史回放与规则评估

- 建立按 Bar 逐根推进的历史回放器：在每个历史截止点仅暴露当时及之前的数据，调用相同的指标和规则代码。
- 输出每条历史信号的触发日期、触发依据和后续 N 日收益观察值，用于人工核验；这不是收益承诺，也不等同于完整交易回测。
- 支持规则在不同股票、不同周期和不同参数下的可重复验证，固定复权口径、数据源和数据版本。
- 后续若扩展为横截面多因子研究，再单独建设股票池、因子排名、前瞻收益、IC/IR、分组回测和样本外验证，避免与单股提醒规则混淆。

#### M3.8 测试、验收与完成判定

- 单元测试覆盖指标公式、预热期、空值传播、边界窗口、交叉事件、形态识别、背离前置条件、规则组合和冷却/幂等逻辑。
- Repository/Room 测试覆盖合格数据才能分析、数据质量失败可追溯、规则和信号持久化、重复触发不重复写入。
- UI 测试覆盖分析摘要、数据不足状态、规则模板创建和最近信号展示。
- M3 完成标准：对一只具备足够日线历史的数据股票，应用可稳定计算指标、解释趋势/形态状态、按规则生成一次可追溯的本地信号记录，并在历史回放中证明没有读取未来 Bar。

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
## M2 复评：数据兼容与质量收尾（M2 未完全结束）

当前结论：M2 的详情页架构、导航、ViewModel、缓存、Room K 线存储和图表骨架基本完成，但数据源覆盖和数据口径仍未达到 M2 的完整验收标准。暂不进入 M3，先完成以下收尾任务。

### 已发现问题

1. 股票搜索仍依赖本地少量股票字典，很多股票无法进入详情页。详情数据源虽然可以直接请求部分未收录股票，但搜索入口没有提供这些股票。
2. 腾讯 K 线接口对不同股票返回字段不一致。普通沪深股票通常返回 `qfqday`、`qfqweek`、`qfqmonth`，部分科创板股票返回 `day`、`week`、`month`，当前解析器只读取 `qfq*`，会把可用数据误判为无数据。
3. 未复权 K 线与前复权 K 线的成交量单位可能不同。当前解析器统一乘以 100，接入普通 K 线后可能造成成交量放大 100 倍。
4. 腾讯 `minute/query` 返回的是交易日内已经产生的分时数据，不是历史完整分时数据。普通股票接口实测可返回约 267 个点；图表横轴会自动减少时间标签，标签少不代表数据点少。
5. 当前分时页面没有明确显示交易日期、数据起止时间和点数，用户无法区分“当日已产生数据”“收盘后的完整数据”和“无数据”。
6. K 线解析只检查字段数量和数字格式，还没有校验 OHLC 关系、日期顺序、重复日期和异常价格。

### M2 剩余任务

#### M2.R1 股票代码与数据源覆盖（已完成）

已实现腾讯远程联想搜索、本地搜索兜底、市场能力矩阵和 `market + code` 校验。当前详情搜索范围为沪市、深市和科创板；北交所保留快照能力，但详情页会返回明确不支持状态，待后续数据源兼容任务完成后再开放。

- 扩展搜索数据源，不能继续依赖固定少量股票字典；至少支持沪市、深市和科创板股票搜索。
- 建立股票市场和数据源能力映射，明确支持的市场、周期和接口，不支持的市场返回可识别的错误状态。
- 校验 `market + code` 到腾讯 provider code 的转换，避免不同市场相同代码发生缓存或请求混淆。
- 增加 `600000`、`000001`、`300750`、`688981` 等代表性股票的详情回归测试。

#### M2.R2 K 线协议兼容与数据口径（已完成）

已实现 `qfqday/qfqweek/qfqmonth` 与 `day/week/month` 双协议字段兼容、实际复权模式记录、成交量单位转换、OHLC 和日期序列校验。Room K 线缓存已升级到 v4，并将复权模式纳入 K 线序列主键。

- 解析器支持 `qfqday/qfqweek/qfqmonth` 和 `day/week/month` 两种响应字段。
- 在领域模型中记录复权模式，例如 `QFQ`、`RAW`，避免 UI 和缓存丢失价格口径。
- 根据响应口径处理成交量单位，禁止所有 K 线无条件乘以 100。
- 对返回数据增加 OHLC 合法性校验：价格必须为正数，最高价不低于开收盘价，最低价不高于开收盘价，日期必须有序且不能重复。
- 为普通沪深、科创板、空响应、字段缺失和异常行分别补充解析器测试。

#### M2.R3 分时数据的全天语义

已完成。分时图展示腾讯 `minute/query` 返回的当前交易日全部已产生点，不补齐未来数据；横轴仅压缩标签，不裁剪 `points`。`IntradaySeries` 已提供首末时间、点数和动态覆盖状态：盘中为部分交易日，收盘点达到 15:00 后为完整交易日，日期早于当前上海日期时标记为最近可用交易日。解析器会拒绝乱序、重复、坏点和无效交易日期。当前接口不支持按指定历史日期查询分时，历史分时仍需单独接入数据源。

- 详情页展示交易日期、实际时间范围、点数和覆盖状态。
- 分时点统一使用上海时区解析和展示，累计成交量/成交额仍保持原有字段语义。
- 增加覆盖状态、乱序点、重复点和坏点回归测试。

#### M2.R4 缓存与 Room 口径升级

已完成。K 线内存缓存和 Room 主键均包含股票、周期、复权模式与时间戳；缓存过期后优先请求网络，网络失败时才按复权模式从 Room 回退，回退结果标记为 `CACHE`。Room 写入会拒绝空数据、复权模式混杂和乱序/重复日期，v1 -> v2 -> v3 -> v4 迁移链保留原自选股和已有 K 线数据。

- 前复权和未复权 K 线不会互相覆盖。
- 网络成功会覆盖旧 Room 数据；网络失败不会写入异常响应。
- 增加过期内存缓存先网络刷新、网络失败再 Room 回退的 Repository 测试。

#### M2.R5 质量验收

自动化验收已完成。数据源、Repository、分时领域模型和详情 Compose 状态均有覆盖；真机四市场数据核对仍需在实际网络和设备上执行，不能由本地单元测试替代。

- 行情 fixture 覆盖普通沪市、普通深市、创业板、科创板和无行情响应；详情数据覆盖不支持市场。
- Repository 覆盖协议字段降级、缓存口径隔离、异常数据拒绝、网络刷新优先和网络失败回退。
- 分时覆盖完整交易日、交易中部分数据、空响应、最近可用交易日、非法日期、乱序、重复和时间范围校验。
- Compose UI 覆盖分时日期/点数、K 线无数据、网络错误、盘口无数据和降级占位。
- 待真机执行：沪市、深市、创业板、科创板各选一只股票，核对价格、日/周/月 K 线、分时首末时间和成交量单位。


### M2 完成判定

完成 M2.R1 至 M2.R5 后，以下条件同时满足，M2 才算结束：

- 常见沪深股票和科创板股票可以从搜索进入详情页。
- 日、周、月 K 线能够兼容前复权和普通字段返回结构。
- 成交量单位在不同接口口径下保持正确，不出现 100 倍放大。
- 分时图展示完整的当日可用数据，并明确交易日期和数据范围。
- 异常数据、暂不支持的股票和接口空响应都有明确降级状态。
- 单元测试、AndroidTest 编译和 Debug 构建已通过；代表性真机验证仍待执行后，M2 才可完成最终验收。

## M2.S 多数据源与全球市场扩展基础

### 目标与范围

在不改变现有详情页数据契约的前提下，将腾讯财经从“固定实现”调整为“可注册、可选择的数据源 provider”。腾讯继续作为默认数据源，新浪财经先作为可选的国内行情 provider；美股、韩股等海外市场只预留身份、能力和 provider 接口，不在本阶段接入真实网络接口，也不伪造可用行情。

### M2.S.1 Provider 标识与注册机制

已完成基础落地：新增 `DataProviderId`、按市场声明能力的 `ProviderCapabilities`、统一 `MarketDataProvider`、`MarketDataProviderRegistry` 和 Hilt `Set` 注册。腾讯 provider 聚合行情/详情/搜索能力；新浪 provider 聚合现有行情能力，详情和搜索明确为空。Repository 尚未切换到 registry，留给 M2.S.2 的选择策略实现。

- 新增可扩展的 `DataProviderId`，替代业务层直接依赖不断增长的 `MarketSource` 枚举。
- 定义 `MarketDataProvider`，统一暴露 provider 标识、市场能力、行情源、详情源和搜索源。
- 通过 Hilt multibinding 或 provider registry 注册腾讯、新浪及未来 provider，Repository 不再直接构造或固定依赖腾讯实现。
- 保留现有 `MarketSource` 的兼容映射，避免一次性改动所有 UI 和测试；后续逐步迁移到 provider ID。

### M2.S.2 数据源选择与降级策略

已完成基础落地：DataStore 持久化主 provider 和备用 provider，默认 Tencent -> Sina；`DefaultMarketDataProviderSelector` 按偏好顺序和市场能力过滤 provider。行情列表已改为按注册 provider 顺序聚合，详情 Repository 已按能力遍历 provider，新浪详情能力为空时会自动回到腾讯。旧构造入口保留用于兼容现有测试，Room/provider 隔离留给后续 S.5。

- 新增 `DataSourcePreference`，至少包含主 provider 和备用 provider 列表。
- 使用 DataStore 持久化用户选择；默认主源为 Tencent，国内行情备用源为 Sina。
- 按“用户选择 -> provider 能力 -> 备用源 -> 内存/Room 缓存”的顺序选择数据源。
- provider 不支持某个市场或数据类型时，返回明确的能力错误，不发起无效请求。
- 详情数据和行情列表使用统一选择策略，但允许后续为行情、K 线、分时分别配置 provider。

### M2.S.3 证券身份与 provider symbol 映射

已完成：标准证券身份与 provider symbol 已解耦，腾讯/新浪均通过各自 mapper 完成转换；搜索结果支持从 provider symbol 还原为标准身份。美股、韩股身份格式可以表达，但当前 mapper 明确返回不支持，不发起真实请求。

- `StockIdentity` 只表达标准市场和证券代码，不再由领域层直接生成 `sh600000`、`sz000001` 等腾讯格式。
- 新增 `ProviderSymbolMapper`，由各 provider 负责把标准身份转换为自身接口需要的代码。
- 将当前腾讯市场代码校验和前缀转换迁移到 `TencentSymbolMapper`，保持现有沪市、深市、科创板和北交所能力矩阵行为不变。
- 预留全球市场身份格式，例如 `US-NASDAQ/AAPL`、`US-NYSE/IBM`、`KR-KOSPI/005930` 和 `KR-KOSDAQ/035720`。
- 市场身份校验、provider 支持能力和证券搜索结果必须分别建模，不能把“能搜索”误认为“能加载详情”。

### M2.S.4 全球化领域数据契约

已完成：领域模型已引入币种、规范数量单位、provider 原始数量单位、市场时区与交易时段契约。腾讯/新浪现有 A 股数据统一映射为 `CNY/SHARES`；腾讯明细源保留原始“手”并在数据层转换。Room K 线缓存升级至 v5，迁移后的历史数据明确标记为 `CNY/SHARES`。海外市场只完成 `USD/KRW`、纽约/首尔交易时段等契约预置，尚未接入真实网络数据或 UI 格式化。

- `Quote` 增加币种、数量单位和可选市场时区等字段，至少支持 CNY、USD、KRW。
- 增加 `TradingSession` 或交易日历契约，分时覆盖状态不再固定使用上海时区和 15:00 收盘时间。
- 明确成交量、成交额、价格精度和复权模式的 provider 口径，统一转换后再进入领域层和 Room。
- 对不适用字段使用可空能力，不因为美股/韩股没有 A 股盘口、复权或涨跌停字段而伪造默认值。
- 现有 A 股页面继续使用当前展示口径；全球化字段先完成契约和映射，暂不要求完整 UI 改版。

### M2.S.5 Repository 与缓存隔离

已完成：两条 Repository 均只依赖 provider selector；行情聚合、详情子数据和缓存回退保留实际 `providerId`。内存缓存按 provider 隔离，Room K 线升级至 v6，主键为 `market + code + period + adjustment + provider_id + timestamp`；v5 历史 K 线统一迁移为 Tencent。`MarketSource` 仍作为现有 UI/错误契约的兼容字段，后续可逐步将展示层切换到 `providerId`。

- `DefaultMarketRepository` 和 `DefaultStockDetailRepository` 改为依赖 provider selector/registry，不直接依赖 `TencentMarketDataSource`。
- 多源聚合结果保留实际 provider ID，缓存回退必须明确是哪个 provider 的数据。
- Room K 线主键增加 `provider_id`，形成 `market + code + period + adjustment + provider_id + timestamp` 的序列身份。
- 新增 Room v5 migration，已有 v4 数据统一标记为 Tencent，保留自选股和现有 K 线数据。
- provider、市场、周期、复权模式、时间范围和币种任一不一致时不得命中同一缓存序列。

### M2.S.6 内置 provider 适配范围

已完成：腾讯继续承接现有 A 股完整能力，新浪继续仅承接 A 股行情；新增 `UsMarketDataProvider` 与 `KoreanMarketDataProvider` 的 Hilt 注册、市场能力矩阵和双向 symbol mapper。海外 provider 为注册占位，未绑定任何网络、搜索或详情 source，因此不会展示在当前行情源设置中；海外股票请求会返回明确的不支持错误且不写入缓存。

- `TencentMarketDataProvider`：承接当前腾讯行情、搜索、分时、K 线和盘口能力，作为默认实现。
- `SinaMarketDataProvider`：先承接已有新浪 A 股行情解析能力，可作为行情列表主源或腾讯备用源；详情能力暂标记为不支持。
- `UsMarketDataProvider`：只建立接口、能力矩阵和 symbol mapper 占位，不发起真实请求。
- `KoreanMarketDataProvider`：只建立接口、能力矩阵和 symbol mapper 占位，不发起真实请求。
- provider 不支持的操作统一返回 `UnsupportedSymbol` 或能力错误，UI 继续沿用现有降级占位。

### M2.S.7 搜索与设置入口

已完成：搜索能力已进入 provider selector，`DefaultStockSearchRepository` 按 DataStore 中主/备 provider 顺序调用具备真实搜索 source 且声明 `SEARCH` 能力的 provider；远程结果再次按 provider 市场能力与 symbol mapper 校验。腾讯远程搜索与本地字典兜底保持不变，新浪无搜索源时自动跳过。设置页继续只展示具有真实行情 source 的 Tencent/Sina，海外占位 provider 不会显示或产生可添加搜索结果。

- 将搜索能力纳入 provider registry，支持 provider 按市场声明可搜索范围。
- 国内股票继续使用腾讯远程搜索和本地兜底；新浪作为行情源时不强制新增新浪搜索接口。
- 为数据源选择预留设置项和 ViewModel 状态，首期至少能选择 Tencent/Sina 行情源；详情仍显示实际可用能力。
- 美股/韩股 provider 在没有真实搜索和行情实现前，不展示为可添加的可用市场。

### M2.S.8 测试与完成判定

- provider 注册、选择、能力过滤和主备降级测试。
- 腾讯/新浪标准身份映射、同一股票多 provider 缓存隔离和 Room v4 -> v5 migration 测试。
- 币种、时区、交易时段、成交量单位和复权模式的领域映射测试。
- 不支持美股/韩股真实接口时，验证返回明确占位错误且不会写入缓存。
- 完成后应能在不改 Repository 核心流程的情况下新增一个 provider；腾讯默认行为和现有 A 股测试全部保持通过。

### M2.S 明确不包含

- 不在本阶段接入美股或韩股的真实第三方 API。
- 不在本阶段实现海外市场完整交易日历、汇率换算、公司行动和税费规则。
- 不在本阶段把新浪详情、盘口、分时和 K 线能力宣称为已支持。
- 不通过复制腾讯协议字段来设计海外市场契约。

### M2 UI Optimize
 主流Kotlin/Android图表库对比
库名称	主要特点	适用场景
MPAndroidChart	Android 最老牌、最流行的图表库。功能强大，支持多种图表，社区庞大，资料丰富。注意：官方对动态与实时数据没有正式支持，但社区有大量实现方案。	对实时性要求不极致的绝大多数Android应用。
AAChartCore-Kotlin	基于前端知名库 Highcharts，Kotlin 编写，图表类型极其丰富精美。支持链式调用，API 简洁。	追求图表精美样式和丰富类型，且可接受一定性能开销的项目。
Androidplot	专注于动态数据展示。轻量级，兼容性好，支持 Kotlin 和 Jetpack Compose。	核心需求就是绘制动态、实时更新的图表。
Lets-Plot	JetBrains 出品，基于“图形语法”。跨平台（Compose Multiplatform），适合统计分析，可制作精美统计图表。	跨平台项目，或需要在应用中进行复杂数据分析与可视化的场景。
ComposeCharts	专为 Jetpack Compose 和 Compose Multiplatform 设计。API 声明式，简洁现代，支持多平台。	使用了 Jetpack Compose 或 Compose Multiplatform 的新项目。
AAY-chart	同样基于 Kotlin Multiplatform 和 Compose 的图表库。支持多平台，组件可组合，易于定制。	需要跨平台支持，并且项目基于 Compose 的开发者。
