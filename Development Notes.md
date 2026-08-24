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
