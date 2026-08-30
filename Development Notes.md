# 开发需知

## 工具链版本

| 名称 | 版本 |
| --- | --- |
| Android Studio | 2026.1.2（稳定版） |
| Android Gradle Plugin (AGP) | 9.3.0 |
| Kotlin | 2.2.10 |
| Gradle | 见 `gradle/wrapper/gradle-wrapper.properties` |
| Compose BOM | 2026.02.01 |
| compileSdk | 36（minorApiLevel = 1） |
| targetSdk | 36 |
| minSdk | 24 |
| Java 兼容级别 | 11（source & target） |

## 关键依赖

- `androidx.activity:activity-compose` 1.8.0
- `androidx.core:core-ktx` 1.10.1
- `androidx.lifecycle:lifecycle-runtime-ktx` 2.6.1
- `androidx.compose.material3:material3`（由 Compose BOM 统一管理）
- `androidx.navigation:navigation-compose` 2.9.0
- `com.google.dagger:hilt-android` **2.60.1**（AGP 9 需 ≥ 2.59.2，2.60.1 最新稳定）
- `com.google.devtools.ksp` **2.2.10-2.0.2**（KSP2，与 Kotlin 2.2.10 匹配）
- `androidx.hilt:hilt-navigation-compose` 1.2.0
- 测试：JUnit 4.13.2、androidx.test.ext:junit 1.1.5、Espresso 3.5.1

版本集中在 `gradle/libs.versions.toml` 维护，新增依赖请优先在该文件登记 alias。

## 已知兼容性说明

| 问题 | 版本 / 配置 |
| --- | --- |
| Hilt + AGP 9 报 `Android BaseExtension not found` | Hilt < 2.59 不支持 AGP 9，升级到 **2.60.1** 解决 |
| KSP 生成目录与 AGP 内置 Kotlin 冲突 | `gradle.properties` 加 `android.disallowKotlinSourceSets=false` |

## 依赖仓库

`settings.gradle.kts` 中 `pluginManagement` 与 `dependencyResolutionManagement` 均已配置阿里云镜像，顺序为：

1. `https://maven.aliyun.com/repository/public`
2. `https://maven.aliyun.com/repository/google`
3. `https://maven.aliyun.com/repository/central`（或 gradle-plugin，视上下文）
4. `google()`（仅 `com.android.*` / `com.google.*` / `androidx.*`）
5. `mavenCentral()`

修改镜像后请执行一次 **Sync Project with Gradle Files** 或 `./gradlew --refresh-dependencies help` 以验证可达性。

## 变更记录

### 2026-08-18 · M0.4 完成：Room + DataStore 落地

- 新增 Room 依赖与 KSP 配置，建立 `WatchlistEntity`、`WatchlistDao`、`AppDatabase` 和 `DatabaseModule`。
- 新增 `WatchlistRepository`，首次启动时若本地表为空，会写入 3 条示例自选股数据。
- 新增 `SettingsRepository`，使用 `DataStore<Preferences>` 持久化 `refreshIntervalSeconds`，默认值为 `15` 秒。
- `WatchlistViewModel` 改为同时展示：
  - 腾讯行情探针返回的原始文本片段。
  - 从 Room 读取出的本地自选股列表。
- `SettingsScreen` / `SettingsViewModel` 已接入 DataStore，可在页面上调整刷新间隔并在重启后保留。
- 验证结果：`.\gradlew.bat :app:assembleDebug` 通过。

### 2026-08-18 · M0.5 完成：Timber + WorkManager + Hilt Worker

- 新增 `Timber`、`WorkManager`、`androidx.hilt:hilt-work` 相关依赖。
- `MySecondApp` 现在实现 `Configuration.Provider`，通过 `HiltWorkerFactory` 接管 WorkManager 初始化。
- `MySecondApp.onCreate()` 中已启用 `Timber.DebugTree()`。
- Manifest 中已移除默认 `WorkManagerInitializer`，避免与自定义配置冲突。
- 新增 `service/monitor/PingWorker`，每次执行打印一条 `Timber.i("ping")` 日志。
- `MainActivity` 启动时会注册唯一的 15 分钟周期任务：`ping-work`。
- 验证结果：`.\gradlew.bat :app:assembleDebug` 通过。

### 当前 M0 状态

- `M0.1`：完成，底部导航与占位页面已建立。
- `M0.2`：完成，Hilt 注入链路已打通。
- `M0.3`：完成，Retrofit + OkHttp 已请求腾讯公开行情接口。
- `M0.4`：完成，Room 与 DataStore 已接入并有页面演示。
- `M0.5`：完成，Timber、WorkManager、Hilt Worker 已接入。

### 下一步建议

- 进入 `M1`，把当前“探针式文本回显”替换为正式的 `Quote` 模型与列表 UI。
- 在 `data` 层引入 `MarketDataSource` / `Repository` 抽象，为新浪源和腾讯源双数据源做准备。

### 2026-08-19 路 M1 第七步继续完成

- 新增 `TencentMarketDataSourceTest`、`SinaMarketDataSourceTest`、`DefaultMarketRepositoryTest`、`DefaultStockSearchRepositoryTest`，覆盖行情解析、多源聚合、内存缓存和本地搜索。
- 新增 `WatchlistDaoTest`，覆盖自选分组排序、存在性判断、分组查询和最大排序值。
- 新增 `WatchlistContentTest`，覆盖搜索结果渲染、加入自选按钮和行情卡片关键字段展示。
- `WatchlistScreen` 拆出 `WatchlistContent`，并补充搜索框与添加按钮的测试标识，便于后续 Compose UI 回归测试。
- 验证结果：`.\gradlew.bat :app:testDebugUnitTest` 通过，`.\gradlew.bat :app:compileDebugAndroidTestKotlin` 通过。
### 2026-08-19 路 M1 阶段评估

- M1 按当前实现可以视为结束。
- 已完成数据契约、双源行情接入、多源聚合、自选存储、搜索添加、行情列表 UI 和核心测试。
- 当前保留项是远程股票搜索扩展和更多 UI / 仓储边界测试，属于后续增强，不影响 M1 主流程验收。

### 2026-08-19 · M2 架构分析与任务拆分

- M2 采用独立的详情数据链路：新增 `StockDetailRepository`，不向 M1 的 `MarketRepository` 混入分时、K 线和盘口职责。
- 实施顺序确定为：数据契约 → 腾讯详情源 → Repository 缓存策略 → Room K 线缓存与迁移 → 导航/ViewModel → Vico 图表 → 盘口与测试收尾。
- K 线缓存将使用独立 `KlineEntity` 和 `MIGRATION_2_3`，确保升级时现有自选股数据不受影响；详情页网络失败时可显示本地旧 K 线。
- 腾讯分钟线和 K 线作为首个详情源；盘口、成交明细作为可降级增强区域，数据源没有稳定返回时显示明确占位。

### 2026-08-19 · M2.1 完成：详情数据契约

- 新增 `StockIdentity`、分时模型、K 线模型、盘口模型、成交明细模型和 `StockDetailSnapshot`。
- 新增 `StockDetailRepository`，统一定义详情聚合、分时、K 线、盘口和成交明细的获取入口。
- 详情模型全部位于 `domain` 层，使用统一时间戳、价格、成交量字段；UI 和数据源不需要依赖外部字符串协议。
- 详情子数据允许独立为空，支持盘口或成交明细不可用时保留报价与图表内容。
- 新增 `StockDetailModelTest`，验证完整详情快照、股票身份缓存键和腾讯供应商代码转换。

### 2026-08-19 · M2.2 完成：腾讯详情数据源

- 已确认腾讯分钟线接口返回交易日与“时间、价格、累计成交量、累计成交额”字符串数组；领域模型明确区分累计字段，均价按成交额与手数换算。
- 已确认复权 K 线接口通过 `qfqday`、`qfqweek`、`qfqmonth` 返回日期、开收高低和成交量数组。
- 新增 `TencentDetailApi`、`StockDetailDataSource` 与 `TencentDetailDataSource`；腾讯详情网络端点复用现有 OkHttp 的 UA、超时与日志配置。
- 分时和 K 线响应在 data 层完成 JSON 协议校验和时间戳映射；K 线成交量统一转换为“股”，与 M1 `Quote.volume` 一致。
- 新增 `TencentDetailDataSourceTest`，覆盖分钟线正常/空响应、日周月 K 线解析及异常 K 线行。
- 验证结果：`./gradlew.bat :app:testDebugUnitTest` 通过。

### 2026-08-20 · M2.3 完成：详情 Repository 与内存缓存

- 新增 `DefaultStockDetailRepository` 并绑定到 Hilt 的 `StockDetailRepository`。
- 详情刷新会并行请求最新报价、分时和当前 K 线周期；任意一个区域失败时，其他成功数据仍可组成详情快照。
- 报价、分时和 K 线按股票/周期/数量分别使用内存缓存；报价和分时 TTL 为 15 秒，日/周/月 K 线 TTL 分别为 15 分钟、6 小时和 24 小时。
- 网络失败时优先返回进程内旧数据并标记 `MarketSource.CACHE`；Room 持久化回退留给 M2.4。
- 分时可以转换为一分钟 K 线，累计成交量和成交额会计算为分钟增量。
- 盘口和成交明细当前按契约返回空数据，待 M2.7 接入稳定端点。
- 新增 `DefaultStockDetailRepositoryTest`，覆盖独立缓存命中、过期缓存回退和详情局部失败。

### 2026-08-20 · M2.4 完成：Room K 线缓存与迁移

- 新增 `KlineEntity`、`KlineDao` 和 `RoomKlineCache`；K 线主键为市场、代码、周期和时间戳。
- `AppDatabase` 升级到 v3，新增 `MIGRATION_2_3`；数据库构建注册完整的 v1→v2→v3 迁移链，原有自选股表不被改动。
- 日/周/月 K 线联网成功后写入 Room，并按请求的最近 N 根清理历史数据；分时及分钟线不持久化。
- 内存 K 线缓存过期后先查询 Room，网络失败时返回 Room 最近数据；网络成功仍会覆盖本地缓存。
- 新增 `KlineDaoTest`，覆盖时间倒序查询、周期隔离和最近 N 根清理。

### 2026-08-20 · M2.5 完成：详情导航、ViewModel 与页面骨架

- 新增 `DetailDestination`，详情路由只传递 `market` 和 `code`；自选行情卡片可进入详情，返回后保留原列表导航状态。
- 新增 `StockDetailViewModel`，从 `SavedStateHandle` 构造股票身份，维护图表周期、刷新状态和错误状态，并通过 `StockDetailRepository` 刷新详情。
- 新增 `StockDetailScreen` 与 `StockDetailContent`：已具备顶部返回、最新报价、OHLC 摘要、分时/日周月周期切换、下拉刷新、图表占位和盘口降级占位。
- 详情页不显示底部导航，避免详情阅读时误触 Tab；返回顶层页面后恢复底部导航。
- 分时周期刷新复用同次分时请求转换分钟 K 线，避免重复网络调用。
- 新增 `StockDetailContentTest`，覆盖报价渲染、周期切换和返回操作。

### 2026-08-20 · M2.6 完成：Vico 分时图与 K 线图

- 新增 Vico Compose 2.1.0 依赖，并封装 `IntradayPriceChart` 和 `CandleStickChart`，详情页面不直接依赖 Vico 模型构建 API。
- 分时图将领域 `IntradayPoint` 映射为价格折线；日/周/月图将 `Candle` 映射为 OHLC K 线，均支持横向浏览并默认定位最新数据。
- 图表 X 轴采用连续索引以避免交易日缺口造成大面积空白，标签仍由时间戳显示为 `HH:mm` 或 `MM-dd`。
- 无详情数据、无图表数据及刷新失败仍沿用稳定占位/错误状态，不创建空 Vico 模型。
- 扩展 `StockDetailContentTest`，覆盖分时与 K 线图表节点渲染。
### 2026-08-20 - M2.7 completed: order book and trade detail enhancement

- Added Tencent order book parsing from the `qt` snapshot, including five bid levels and five ask levels.
- Tencent order book quantities are converted from lots to shares in the data layer.
- Added independent 15-second memory caches for order book and trade tick results. Failed refreshes can fall back to the previous value and report `MarketSource.CACHE`.
- The detail screen now renders loaded bid/ask levels and keeps a clear empty state when trade tick data is unavailable.
- Tencent `minute/query` currently does not provide stable tick-level data (`mx_price` is empty), so trade details intentionally remain an empty, explicitly labeled state instead of treating minute aggregates as trades.
- Added parser, repository cache/degradation, and Compose order book coverage. `compileDebugKotlin` and the focused detail unit tests pass.
### 2026-08-20 - M2.R1 completed: symbol coverage and market capabilities

- Added `StockMarket` and `StockMarketCapabilities` to validate market/code pairs, normalize detail cache keys, and centralize Tencent provider-code conversion.
- SH, SZ, and STAR Market symbols are searchable and supported by the current detail capability. BJ remains quote-only until reliable intraday and K-line support is implemented.
- Added Tencent Smartbox remote symbol search. Remote suggestions are filtered to detail-supported A shares, while the local dictionary remains a pinyin and network-failure fallback.
- Search input now debounces remote requests and cancels stale work so older responses cannot overwrite newer queries.
- Detail requests now return `MarketError.UnsupportedSymbol` for invalid or unsupported market/code combinations instead of falling into a parse or empty-data error.
- Added parser, aggregation, market-capability, and unsupported-detail tests. Representative mappings cover `600000`, `000001`, `300750`, and `688981`.
### 2026-08-20 - M2.R1 fix: Tencent search name encoding

- Tencent Smartbox returns Chinese symbol names as literal `\\uXXXX` escapes. The search parser now decodes these values before they reach the UI or Room.
- Existing watchlist records containing escaped names are decoded when mapped out of Room, and refreshed quote names take precedence in the watchlist card display.
- Added regression coverage for escaped remote-search names and previously persisted escaped watchlist names.
### 2026-08-20 - M2.R2 completed: K-line protocol compatibility and data units

- Added `CandleAdjustment` to record the actual K-line price口径: `QFQ`, `RAW`, or `NONE` for intraday-derived candles.
- Tencent K-line parsing now prefers `qfqday/qfqweek/qfqmonth` and falls back to `day/week/month`, including the case where the adjusted array is present but empty.
- QFQ K-line volumes are converted from lots to shares; ordinary RAW K-line volumes are kept as shares and are no longer multiplied by 100.
- Added OHLC validity checks, positive finite price checks, chronological ordering checks, and duplicate-date rejection before data reaches the repository.
- Upgraded Room from v3 to v4. K-line identity now includes adjustment mode, and the migration preserves existing rows as QFQ without allowing RAW and QFQ rows to overwrite each other.
- Added regression coverage for raw STAR-style responses, volume conversion, invalid OHLC rows, adjustment-isolated DAO data, and existing repository behavior.

### 2026-08-21 - M2.R3 completed: intraday full-day semantics

- `IntradaySeries` now exposes the first timestamp, last timestamp, point count, and a time-dependent coverage status.
- Coverage is evaluated in Asia/Shanghai time: data before the 15:00 close is `PARTIAL_TRADING_DAY`, data reaching the close is `COMPLETE_TRADING_DAY`, and an earlier trading date is `LATEST_AVAILABLE_TRADING_DAY`.
- The detail page now shows the trading date, actual time range, complete point count, and coverage label. Vico axis label compression does not reduce the underlying point list.
- Tencent minute responses are rejected as a whole when they contain malformed, out-of-order, duplicate, or invalid-date points. The current `minute/query` endpoint remains current-day-only; historical intraday lookup is not inferred from it.
- Added unit coverage for partial/complete/latest-available states and parser rejection of malformed, out-of-order, and duplicate points.

### 2026-08-21 - M2.R4 completed: cache and Room data-contract upgrade

- K-line memory and Room cache identities include stock, period, adjustment mode, and timestamp. QFQ and RAW series remain isolated through the v4 schema and migration chain.
- Expired memory K-line data now attempts a network refresh first. Room is used only after the network fails and no in-memory fallback is available, with the result marked as `MarketSource.CACHE`.
- Room writes reject empty, mixed-adjustment, out-of-order, or duplicate candle batches so unverified protocol data cannot become persistent fallback data.
- Added Repository regression coverage for network refresh taking precedence over stale Room data and for Room fallback after a failed refresh.

### 2026-08-21 - M2.R5 automated quality acceptance completed

- Added the representative quote fixture matrix for SH, SZ, growth-board, and STAR symbols, plus empty-provider response coverage.
- Added invalid intraday-date coverage and Compose coverage for missing chart data, network error, and order-book degradation states.
- Serial verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:assembleDebug`.
- Real-device acceptance remains pending: verify one SH, SZ, growth-board, and STAR stock against live price, day/week/month K-lines, intraday range, and volume units.

### 2026-08-21 - M2.S.1 provider registration foundation completed

- Added `DataProviderId`, built-in Tencent/Sina IDs, legacy `MarketSource` compatibility mapping, and market-scoped `ProviderCapabilities`.
- Added the `MarketDataProvider` contract and `MarketDataProviderRegistry`, including duplicate-ID validation and deterministic provider ordering.
- Registered Tencent and Sina providers through Hilt multibinding. Tencent exposes quote/detail/search sources; Sina currently exposes quote only.
- Repository source selection was intentionally unchanged in S.1 and was implemented in the following S.2 step.
- Verification passed: `:app:testDebugUnitTest` and `:app:compileDebugAndroidTestKotlin`.

### 2026-08-21 - M2.S.2 provider selection and fallback completed

- Added `DataSourcePreference` and persisted primary/fallback provider IDs in the existing Settings DataStore. The default order is Tencent -> Sina.
- Added `MarketDataProviderSelector`; provider selection is filtered by market and capability before any network request.
- Market list aggregation now follows the selected provider order and fills missing symbols from later providers. Detail requests iterate providers with the requested capability and fall back when the selected provider has no detail implementation or returns a failure.
- Existing Repository constructors remain as compatibility adapters for tests; production Hilt injection uses the selector implementation.
- Verification passed: `:app:testDebugUnitTest` and `:app:compileDebugAndroidTestKotlin`.

### 2026-08-21 - M2.S.3 security identity and provider symbol mapping completed

- Added `ProviderSymbolMapper` with provider-owned conversion in both directions: standard `StockIdentity` to provider symbol, and provider symbol back to standard identity.
- Added `TencentSymbolMapper` and `SinaSymbolMapper`. Tencent mapping preserves SH, SZ, STAR, and BJ validation; Sina currently reuses the mainland quote symbol shape.
- `MarketDataProvider` now exposes its symbol mapper. Market list aggregation and detail quote requests use the selected provider mapper instead of hardcoded Tencent prefixes.
- Tencent detail requests and Tencent Smartbox search parsing now use `TencentSymbolMapper`; search results are normalized into standard market/code identities before entering the domain/UI.
- Global identity examples such as `US-NASDAQ/AAPL` and `KR-KOSPI/005930` remain representable, while current providers return unsupported without making a malformed request.
- Existing `StockIdentity` Tencent helper methods remain as deprecated-compatible behavior for older tests and stored callers; production provider paths no longer depend on them.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-24 - M2 Tencent intraday time semantics corrected

- Confirmed Tencent `app/minute/query` does not return epoch timestamps directly. Its payload provides a trading date such as `20260824` and ordered minute snapshot strings such as `0930 9.01 4337 3907637.00`; the fields are minute, price, cumulative volume, and cumulative turnover.
- The data boundary combines the trading date and `HHmm` value in `Asia/Shanghai` and exposes each point as an `IntradayPoint.timestampMillis`. The series therefore represents an ordered set of available minute snapshots, not a fixed one-point-per-second stream and not necessarily a complete day while trading is in progress.
- Tencent `fqkline/get` is separate: day/week/month responses contain date plus OHLC fields per period. These are `Candle` records and are not interchangeable with the minute snapshot series.
- Fixed the detail chart to format intraday labels with the series' market timezone instead of the device timezone. The screenshot's `09:30-14:25` data can no longer be displayed as `01:30-06:25` on a UTC device.
- Reduced intraday point spacing and removed end-scroll behavior so the available trading-day minute series is presented as a whole-day chart instead of showing only the final few minutes.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-21 - M2.S.4 global market data contract completed

- Added `CurrencyCode`, `QuantityUnit`, `TradingWindow`, `TradingSession`, and `MarketDataContract`. Contracts are available for A shares, US equities, Korean equities, and an explicit unknown fallback.
- `Quote`, `Candle`, and `IntradaySeries` now carry normalized currency and quantity-unit semantics. Quote/intraday records also expose market timezone and trading-session context.
- Intraday coverage no longer assumes Asia/Shanghai or a 15:00 close. It evaluates the series using its market session, including the New York 16:00 close for future US equities.
- Added a separate raw provider volume unit. Tencent intraday payloads are declared as lots and normalized to shares at the data boundary; existing Tencent/Sina A-share values remain `CNY/SHARES` for UI and repository consumers.
- Upgraded Room to v5. K-line cache rows now store currency and volume unit; `MIGRATION_4_5` marks existing v4 data as the established A-share `CNY/SHARES` contract.
- Added regression coverage for US trading-session completion and Tencent intraday unit normalization. No overseas network API or currency-display UI is enabled by this step.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-21 - M2.S.5 repository and cache isolation completed

- `MarketDataResult.Success`, `Quote`, `QuoteSnapshot`, and `StockDetailSnapshot` now retain optional actual `providerId` while keeping `MarketSource` as the existing UI/error compatibility field.
- Market-list aggregation stamps each accepted quote with the provider that returned it. Detail data records the provider selected for each successful capability; a detail snapshot can therefore represent multiple provider IDs when regions fall back independently.
- Detail memory caches for quote, intraday, candles, order book, and trade ticks are keyed by provider. Cache reads follow the currently selected provider order, so values from Tencent and future providers never overwrite each other.
- Upgraded Room to v6. K-line identity now includes `provider_id`, and the v5 -> v6 migration recreates the table while assigning all existing cache rows to Tencent.
- Room K-line reads, writes, pruning, and fallback retrieval include provider ID. Quote-list regression coverage asserts the actual Tencent/Sina provider for merged rows; DAO coverage verifies identical timestamp series remain isolated by provider.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-21 - M2.S.6 built-in provider adapter scope completed

- Added stable `us` and `korea` provider IDs and registered `UsMarketDataProvider` and `KoreanMarketDataProvider` through the existing Hilt provider set.
- Added bidirectional reserved symbol mappers: US identities use `US-NASDAQ:AAPL` / `US-NYSE:IBM`; Korean identities use `KR-KOSPI:005930` / `KR-KOSDAQ:035720`. Tickers are normalized to uppercase and Korean six-digit codes are preserved.
- The new providers declare their intended quote/search market capability but deliberately bind no market, search, or detail data source. The selector excludes them from executable requests, and Settings continues to expose only Tencent/Sina because those are the only quote providers with a real source.
- Market-list refresh now returns `UnsupportedSymbol` when selected providers have no executable quote source, instead of treating a reserved overseas provider as an unknown transport result. No request or cache write occurs in this path.
- Added mapper and registration-only provider coverage for US/Korean markets, malformed symbols, and absent network sources.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-21 - M2.S.7 search and settings entry completed

- Added `searchProviders(markets)` to the shared provider selector. Selection respects persisted provider order, requires a real search data source, and filters by market-scoped `SEARCH` capability.
- `DefaultStockSearchRepository` now depends on the provider selector rather than directly on Tencent search. It merges selected provider results with the existing local dictionary, then validates each remote item against the provider capability and symbol mapper before it can reach the add-to-watchlist UI.
- Tencent remains the active remote A-share search implementation. Sina remains quote-only and is skipped for search; local code/pinyin results remain the deterministic fallback when Tencent is unavailable.
- The existing Settings screen already limits source choices to registered providers with an executable quote data source, so Tencent/Sina remain selectable while US/Korean registration-only providers stay hidden.
- Added regression coverage that excludes remote results outside the selected provider's market/search contract.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-24 - M2 chart horizontal-axis test fix completed

- Fixed the detail-page intraday and candle charts' horizontal-axis labeling by restoring Vico's default adaptive tick placement. The chart still uses continuous point indexes for uniform trading-point spacing.
- Axis label lookup now rounds interpolated Vico values and clamps them to the available label range, preventing invalid values from falling back to the last timestamp while keeping the default axis labels visible.
- Added unit coverage for horizontal-axis index boundary behavior in `StockChartsTest`.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-26 - M2 chart price-axis auto range completed

- Vico's default positive-value range starts the vertical axis at zero, which compresses low-priced stocks whose actual fluctuation is only a few cents.
- Added a shared adaptive price range provider for intraday and candlestick charts. It uses the actual data high/low range with 8% padding, keeps positive-only prices above zero, and adds a minimum margin when all values are equal.
- Added unit coverage for low-priced small-range data and equal-price data in `StockChartsTest`.
- Verification passed: `:app:testDebugUnitTest` and `git diff --check`.

### 2026-08-26 - M2 chart extrema labels completed

- Added persistent Vico markers to the detail charts. The `Time` chart marks the highest and lowest price points in the loaded intraday snapshot series; day/week/month candlestick charts mark the selected loaded range's highest `high` and lowest `low`.
- High and low labels are drawn next to the actual chart coordinates, with `H price` above the high point and `L price` below the low point. The markers follow chart scrolling, zooming, and adaptive price-axis changes instead of being fixed Compose overlays.
- Empty data does not create markers. When one point is both the high and low, the chart uses a combined marker to avoid duplicate rendering.
- The current extrema scope is the complete data list loaded for the chart, not only the currently visible viewport. A marker outside the initial viewport becomes visible after scrolling to its candle or point.
- Verification passed: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.

### 2026-08-26 - Detail chart default period restored to Time

- Changed `StockDetailViewModel` initial chart period from `CandlePeriod.DAY` to `CandlePeriod.MINUTE`.
- Opening a stock detail page now requests and selects the `Time` chart by default; users can still switch to day/week/month candles manually.
- Verification passed: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, and `git diff --check`.

### 2026-08-27 - Android emulator freeze troubleshooting

- Symptom: the Android Studio embedded emulator displayed the Android launcher, but all touch/click input was unresponsive. App deployment and launch therefore appeared to have no effect.
- Diagnosis: this was an emulator/AVD runtime failure, not an application startup failure and not caused by the detail-chart changes. ADB connectivity became unstable while the emulator UI was frozen.
- Resolution: deleted the old `Medium_Phone` emulator and created a new AVD. The new emulator is the required target for subsequent app deployment and verification.

### 2026-08-28 - M3.0.1 completed: current-state assessment (G0)

- Confirmed the existing historical data path: `MarketDataProvider` / Tencent parser normalizes day, week, and month payloads into chronological `Candle` lists; `DefaultStockDetailRepository` selects providers, owns in-memory fallback, and writes non-minute K-lines through `KlineCache`; `RoomKlineCache` persists them in `kline`; `StockDetailViewModel` only requests and exposes the selected period to the chart UI.
- Reuse decision: retain `Candle` as the immutable OHLCV value object. `CandlePeriod`, `CandleAdjustment`, currency, and volume unit are already explicit. Provider ID, stock identity, market time zone, fetch time, and analysis cutoff must be introduced as historical-series metadata rather than copied into every Bar.
- Confirmed cache isolation: memory and Room identities include stock, period, adjustment, and provider. The Room v6 primary key also includes timestamp, so QFQ/RAW and provider series cannot overwrite each other. M3 must preserve this key boundary when it creates analysis inputs and signal identities.
- Confirmed availability: Tencent `fqkline/get` supplies date plus OHLC and volume for day/week/month, capped by the repository request limit (currently 320 by default). `app/minute/query` supplies only current-trading-day minute snapshots and is explicitly excluded from M3 historical replay.
- Identified M3 gaps: parser and Room write paths reject malformed/unsorted candles, but may discard malformed rows before an analysis caller can see a structured reason; no `HistoricalBarSeries`, quality state, market-aware gap evaluation, analysis cutoff, or final-daily-Bar confirmation contract exists; Room stores fetch time per Bar but not series-level provenance or validation outcome.
- G0 decision: M3 is approved to start with one stock and confirmed daily Bars. M3.1 must create the pure validation boundary before any indicator or rule implementation; day/week/month remain reusable contracts but only daily event rules are in the first release.
- Verification evidence: source-to-analysis path, required fields, and unavailable fields were traced from the provider parser through repository, cache, Room, ViewModel, and chart. No historical-minute or unsupported-provider capability is assumed by the M3 scope.

### 2026-08-30 - M3.0.2 completed: first-release requirements baseline (G1)

- First-release input boundary is one stock's validated, confirmed daily Bar series. Day/week/month share the future domain contract, but only daily Bars may trigger M3 event rules. A series may use QFQ or RAW data, provided its adjustment, provider, currency, and volume unit stay internally consistent and are retained in the result identity.
- An analysis result is a factual state or a rule event with a data cutoff. It is not a prediction, investment recommendation, order, or notification. Trend state such as `EMA12 > EMA26` is not itself an EMA golden-cross event; an event requires the previous confirmed Bar to be on the opposite/equal side and the cutoff Bar to cross it.
- Frozen template contracts: EMA cross uses close prices with fast/slow defaults `12/26`, requires at least 27 daily Bars, and records both EMA values plus previous-side evidence; RSI oversold recovery uses RSI(14), threshold 30, requires at least 16 daily Bars, and requires RSI to cross from `<= 30` to `> 30`; volume breakout uses the prior 20-Bar high and prior 20-Bar average volume, requires at least 21 daily Bars, and requires the cutoff close to exceed the prior high with volume at least 1.5 times the prior average; bullish/bearish engulfing requires two daily Bars and records both OHLC pairs.
- Frozen directions: golden cross, RSI recovery, volume breakout, and bullish engulfing produce `BULLISH`; death cross and bearish engulfing produce `BEARISH`. A template does not emit a neutral event. Missing volume makes a volume breakout unavailable rather than zero-volume or false.
- Frozen evidence: each signal must retain template/rule ID and version, stock identity, period, adjustment, provider, cutoff Bar timestamp, direction, source indicator/pattern values, and a machine-readable matched-condition list. Re-running the same rule for the same identity, series contract, cutoff Bar, and direction must create no second durable signal.
- Deferred from M3 first release: MACD/RSI divergence, multi-period conditions, visual expression editing, historical minute replay, automatic trading, ranking, profitability claims, Android notifications, and background polling.
- G1 decision: requirements are frozen. Later additions enter the backlog unless they correct data integrity, no-lookahead behavior, or safety.
- Verification evidence: every template has a single daily input period, minimum history, event condition, direction, evidence shape, and idempotency policy; the EMA acceptance example explicitly separates an ongoing bullish trend from a new cross event.

### 2026-08-30 - M3.1-M3.3 architecture baseline approved (G2)

- Historical input design: add a domain-only `HistoricalBarSeries` that owns stock identity, Bars, period, adjustment, currency, volume unit, provider ID, market timezone, fetch time, selected cutoff Bar, and cutoff-Bar completion state. `Candle` remains the shared normalized OHLCV value object. All analysis operates on the explicit prefix ending at the cutoff, so a replay cannot observe later Bars.
- Quality design: `HistoricalBarValidator` returns a `HistoricalBarValidationResult` containing the usable Bar prefix, a single precedence-based quality state, and all structured issue codes. Invalid structural data wins over insufficient history, gaps, and partial/unconfirmed status. The validator does not mutate or silently discard a malformed Bar. Calendar gaps are reported only when a caller supplies an expected trading-bar calendar; without one, weekends, holidays, suspensions, and listing dates remain conservative and do not create a gap failure.
- Analysis boundary design: indicators and patterns will consume validated `HistoricalAnalysisInput` objects in a later M3.2 step. Indicators calculate values; pattern detectors identify timestamped structures; rules interpret those typed outputs. Each calculation declares a cutoff and explicit unavailable/warmup state. UI calls use cases only and never recalculates market data.
- Persistence design: M3.8 will introduce separate Rule and Signal Room entities. Signal identity is `ruleId + stock market/code + period + adjustment + providerId + signalBarTimestamp + direction`; evidence is persisted as a versioned serialized payload together with source contract metadata. Transient indicator series and unmatched evaluations remain in memory, not Room. The Room migration is planned from v6 only when these entities are actually introduced.
- UI design: M3.9 will map analysis into loading, insufficient, partial, invalid, ready/no-match, and matched-signal states. The first detail-page surface is read-only and shows quality, cutoff, provider/adjustment, and evidence. Template editing comes after the domain and persistence paths, and it will not label a state as a guaranteed buy or sell.
- G2 decision: contracts are approved for implementation. The first code increment is the isolated `HistoricalBarSeries` validator plus JUnit fixtures; Repository adaptation, indicators, Room, and Compose remain separate following increments.

### 2026-08-30 - M3.4 validation boundary increment completed

- Added pure Kotlin `HistoricalBarSeries`, `HistoricalBarCompletion`, `HistoricalBarQuality`, structured issue codes, validation options, and `HistoricalBarValidationResult` under `domain.analysis.history`.
- `HistoricalBarValidator` explicitly limits analysis to Bars at or before the declared cutoff. It reports empty/insufficient history, cutoff mismatch, ordering and duplicate errors, invalid/non-finite OHLC, negative volume/turnover, mixed series metadata, optional calendar gaps, and unconfirmed cutoff Bars.
- Quality precedence is deterministic: structural invalidity -> `INVALID`; too few usable Bars -> `INSUFFICIENT_HISTORY`; explicit calendar gap -> `GAPPED`; unknown/unconfirmed cutoff -> `PARTIAL`; otherwise `COMPLETE`. Only `COMPLETE` is eligible for final event rules.
- Added JVM fixtures for valid future-Bar exclusion, empty/one-Bar history, malformed values, ordering, metadata mixing, conservative calendar gaps, and unconfirmed cutoff behavior.
- Verification passed: `:app:testDebugUnitTest` (`65 tests completed`) and `git diff --check`.
- Repository integration: added `StockDetailRepository.fetchHistoricalBarSeries()`. The existing `fetchCandles()` remains the M2 chart/cache API; the new method adds series metadata, derives the market-local cutoff completion state, and validates before returning an analysis input.
- Stage status: M3.4/G3 passed. Invalid data is returned as a structured validation result and is not sent to an analysis consumer; no separate analysis-ready cache exists yet, while the existing chart cache remains intentionally independent.

### 2026-08-30 - M3.5 completed: indicator registry and baseline calculators (G4)

- Added the pure Kotlin `Indicator`, `IndicatorDefinition`, `IndicatorValue`, `IndicatorSeries`, and duplicate-safe `IndicatorRegistry` contracts. Indicators expose parameter definitions, supported periods, required fields, warmup length, timestamp-aligned values, and explicit unavailable reasons.
- Added baseline calculators for SMA, EMA, volume SMA, MACD, RSI, Bollinger Bands, ATR, and OBV. MACD exposes `macd`, `signal`, and `histogram` as typed outputs instead of packing values into a display string.
- Calculation design: warmup Bars are `WARMUP`; null volume is `MISSING_INPUT`; non-finite or broken recursive input is `INVALID_VALUE`; unsupported period is `UNSUPPORTED_PERIOD`. No indicator substitutes zero for unavailable input.
- Added hand-calculated fixtures for SMA/EMA, RSI, Bollinger Bands, MACD, volume SMA, ATR, and OBV, plus registry duplicate rejection, missing-volume handling, non-finite handling, and a cross-indicator cutoff invariance test proving future Bars do not change the value at an earlier timestamp.
- Verification passed: `:app:testDebugUnitTest` and `git diff --check`.
- G4 decision: indicator results are ready for the pattern and rule layers. Wider reference fixtures and the complete test matrix remain scheduled for M3.11.

### 2026-08-30 - M3.6 completed: technical states and pattern detectors

- Added a typed `TechnicalResult` contract with match status, stable reason code, source Bar timestamps, numeric evidence, and parameters. Rules will consume these objects directly; UI has no parsing role in analysis decisions.
- Implemented EMA trend state and EMA golden/death cross event detection. The cross detector compares the final two confirmed indicator points: an ongoing bullish/bearish EMA relationship returns `NOT_MATCHED`, so it cannot repeatedly emit a cross event.
- Implemented RSI state, volume-versus-average state, Bollinger-band position, and OBV direction state. Also added candle body/range/shadow geometry, bullish/bearish engulfing, and range breakout with configurable lookback and volume multiplier.
- Added an explicit `SwingPointDetector`/`SwingPoint` contract only. MACD/RSI divergence remains deferred until a reviewed swing-point algorithm and deterministic fixtures are available.
- Added tests for positive, near-miss, insufficient-history, one-time EMA cross, structured evidence, and candle geometry behavior.
- Verification passed: `:app:testDebugUnitTest` and `git diff --check`.
- Stage status: outputs are ready for M3.7 typed rules and signal idempotency; they remain factual analysis results rather than buy/sell instructions.

### 2026-08-30 - M3.7 completed: rule evaluation and signal idempotency (G5)

- Added typed `AnalysisRule`, `RuleCondition`, `RuleAnalysisContext`, `RuleEvaluation`, `RuleEvidence`, and `SignalRecord` contracts. Atomic conditions support technical events and indicator thresholds; condition groups support `ALL`, `ANY`, and `NOT` with evidence propagation.
- Rule evaluation order is deterministic: disabled/period mismatch/data-quality block is handled before condition evaluation; incomplete, partial, gapped, or invalid historical input cannot produce a final matched event.
- Added six first-release templates: EMA golden cross, EMA death cross, RSI oversold recovery, volume breakout, bullish engulfing, and bearish engulfing. RSI recovery is explicitly a two-point crossing event, separate from the persistent RSI oversold state.
- Signal identity includes rule ID/version, stock identity, period, adjustment, provider, signal Bar timestamp, and direction. `SignalDeduplicator` rejects a second record with the same key; rule version, provider, period, or direction changes produce a distinct key.
- Kept cooldown/notification behavior out of M3.7. Idempotency records historical facts now; M4 will decide whether and when a matched fact is delivered as a reminder.
- Added tests for template evidence, `ALL/ANY/NOT`, indicator thresholds, data-quality blocking, duplicate prevention, and versioned signal identity.
- Verification passed: `:app:testDebugUnitTest` (`87 tests completed`) and `git diff --check`.
- G5 decision: domain rules are ready for Room persistence and use-case integration. No UI or automatic trading behavior has been introduced.

### 2026-08-30 - M3.8 completed: Room repositories and evaluation use case

- Added Room v7 `analysis_rule` and `analysis_signal` tables. Migration `6 -> 7` creates only these independent tables, preserving all existing watchlist and K-line rows.
- `analysis_rule` stores the current versioned rule and typed condition tree JSON. `analysis_signal` uses the complete rule/version/stock/period/adjustment/provider/signal-Bar/direction composite key, so SQLite enforces historical signal idempotency rather than relying only on an in-memory set.
- Added `AnalysisRuleDao`, `SignalDao`, `RoomAnalysisRuleRepository`, and `RoomSignalRepository`. Mapping tests prove nested condition trees and evidence-bearing signal records survive entity round trips.
- Added `EvaluateEnabledRules`, which reads enabled rules, delegates all decisions to the pure `RuleEvaluator`, and persists matched records through `SignalRepository`; it has no SQL or UI logic.
- Added Android DAO tests for duplicate signal insertion and same-rule replacement. These are compiled but intentionally not run in this turn because no emulator/device execution was requested.
- Verification passed: `:app:testDebugUnitTest` and `:app:compileDebugAndroidTestKotlin`; `git diff --check` passed.
- Stage status: M3.8 code and compile gate passed. Device/in-memory Room execution remains part of M3.11/M3.12 acceptance evidence.

### 2026-08-30 - M3.9 completed: detail analysis summary and rule template UI

- Extended `StockDetailUiState` with an independent `StockAnalysisUiState` and enabled rule IDs. The detail ViewModel requests daily historical analysis separately from the selected chart period, so the default Time chart does not accidentally become the rule-analysis input.
- The ViewModel maps validated daily data into read-only trend, RSI, volume, Bollinger position, and OBV facts. Non-`COMPLETE` quality only shows quality/issues and does not calculate or display analysis facts as valid.
- Added the detail-page Historical analysis section showing quality, Provider, adjustment, cutoff, issue codes, and numeric evidence. Existing quote, chart, order-book, and trade sections remain independently degradable.
- Added the six first-release rule-template actions. Enabling a template calls `AnalysisRuleRepository`; Compose does not calculate conditions or write Room directly. Enabled templates are disabled in the list after the repository Flow reflects them.
- Added Compose coverage for analysis summary rendering, evidence rendering, and template callback behavior.
- Verification passed: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `git diff --check`.
- Stage status: M3.9 compile/UI-contract gate passed. Device rendering and real Room interaction remain M3.12 acceptance work.

### 2026-08-30 - M3.10 completed: historical rule replay

- Added `HistoricalRuleReplayer`, `ReplayPoint`, `HistoricalReplayResult`, and the `ReplayRuleHistory` use-case boundary. Replay evaluates a rule independently at each source Bar cutoff and retains a point for every source Bar.
- Replay creates a fresh validated prefix for each timestamp. It marks early points as insufficient history, quality failures as blocked, and an unconfirmed final Bar as partial; none are silently removed or considered matched.
- Replay builds the same typed technical results used by the detail analysis and first-release rules. It does not use minute snapshots, network calls, UI state, or future Bars.
- Added deterministic replay tests for volume-breakout matching, repeated replay equality, appended-future-Bar invariance of statuses/evidence, and unconfirmed-final-Bar handling.
- Verification passed: `:app:testDebugUnitTest` and `git diff --check`.
- Stage status: M3.10 replay gate passed. M3.11 must now run the complete automated matrix and compile regression suite.

### 2026-08-30 - M3.11 completed: automated verification matrix

- Scope delivered: reran the M3 JVM unit-test suite, Android-test Kotlin compilation, Debug APK assembly, and whitespace validation after the M3.10 implementation.
- Verification: `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug` -> passed; `git diff --check` -> passed.
- Execution note: Gradle reused the configuration cache and all requested tasks were `UP-TO-DATE`; this confirms the current outputs but is not a clean-build result.
- Stage status: M3.11 automated gate passed. Room DAO tests and Compose analysis UI tests are compiled but not executed; M3.12 must run them on the replacement emulator or a physical device.

### M3.12 manual acceptance checklist - pending device execution

- [ ] Open an A-share detail page with sufficient daily history; verify the analysis cutoff is the final confirmed daily Bar.
- [ ] Open a stock with insufficient or invalid history; verify no event signal is shown as valid.
- [ ] Enable an EMA cross template and evaluate the same closing Bar twice; verify only one durable signal exists.
- [ ] Switch day/week/month chart views; verify period, provider, and adjustment metadata do not mix.
- [ ] Inspect a stored signal; verify rule version, source Bar time, source values, direction, and reason are visible.
- [ ] Run the same fixed historical replay twice; verify signal dates and evidence are identical.

- Current status: not executed in this turn. Room DAO Android tests and Compose analysis UI tests also require execution on the replacement emulator or a physical device.
- Acceptance owner: local tester. Record pass/fail evidence here before approving the M3 G6 close-out.
