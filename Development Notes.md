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
