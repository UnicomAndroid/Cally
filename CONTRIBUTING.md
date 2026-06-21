# 为 Cally 做贡献

感谢你的贡献意愿。本文档说明如何参与，避免不必要的周折。

> **乌克兰社区：** 所有内容可用母语——issues、PRs、提交。英语也可以，如果你更习惯的话。

## 目前最有价值的贡献

最大的缺口是**设备覆盖率**。我们只在有限的 Pixel/三星设备上测试过；绕过方案对厂商 HAL 敏感。如果你有 `docs/device-matrix.md` 之外的手机——`./gradlew installDebug`，打一个测试电话，并在 issue 中报告：

- `Build.MANUFACTURER`、`Build.MODEL`、`Build.FINGERPRINT`
- Android 版本、补丁级别
- 5 种策略中哪种成功了（在设置 → 调试中可见，或 logcat `RecorderController`）
- 是否有来电铃声、蓝牙耳机、SIM 切换
- 上行/下行 WAV 样本（10-20 秒，可来自打给自己的测试电话）

将结果填入 `docs/device-matrix.md` 并开启 PR。

## 其他受欢迎的贡献方向

- **i18n** — 英文语言包（`values-en/strings.xml`）。欢迎更多语言。
- **SAF 集成** — `OpenDocumentTree` + `MediaStore` 镜像。
- **加密保险库** — 通过 PIN/生物识别对录音进行 AES-GCM 加密。
- **设计** — 应用目前功能可用；UX 批评和 Material 3 Expressive 打磨很有价值。

## 工作流程

1. Fork → branch（`feat/xyz`、`fix/xyz`、`docs/xyz`）。
2. 小而有逻辑的提交（鼓励 Conventional Commits：`feat(playback):`、`fix:`、`docs:`）。
3. 针对 `main` 的 PR。描述为什么，而非是什么（diff 本身已显示）。
4. 如有相关 issue，请链接。
5. **如果 PR 更改了用户可见行为——更新 `CHANGELOG.md` 的 `## [Unreleased]` 部分。** 这是强制性的。详细的版本控制和发布规则——见 [`RELEASING.md`](RELEASING.md)。
6. CI 必须通过（lint + unit tests + assembleDebug）。

## 发布与版本管理

这些规则适用于维护者，但对贡献者也很重要（避免 PR 在准备发布时被阻止）：

- **每个发布构建 → `versionCode` +1**（Android 拒绝升级相同 versionCode 的 APK）。
- **`IRecorderService.aidl`、`AudioRecorderJob` pump 逻辑、`verifyCaller()`、`WrappedShellContext`、`HiddenApiBootstrap` 的变更 → `userServiceVersion` +1。** 否则带 `daemon=true` 的 Shizuku 守护进程不会重新生成，AIDL 事务不匹配 → 用户端静默失败。
- 完整矩阵和起飞前检查清单 — [`RELEASING.md`](RELEASING.md)。

## 本地构建

```bash
./gradlew :app:assembleDebug                  # APK 在 app/build/outputs/apk/debug/
./gradlew :app:lintDebug                      # AGP lint
./gradlew test                                # 单元测试
./gradlew :app:connectedAndroidTest           # 仪器化测试（需要设备）
```

发布签名对开发是可选的——`app/build.gradle.kts` 中的 `signingConfigs.release` 块仅在项目根目录存在 `keystore.properties` 时激活。说明见 README。

## 代码风格

- Kotlin official code style（`gradle.properties` 中的 `kotlin.code.style=official`）。
- Lint 警告不阻止构建（`warningsAsErrors=false`），但不要引入新的。
- 不要写自我描述的注释；注释应解释**为什么**，而非**是什么**。如果注释重复函数名——删除。
- Public API → KDoc，internal → 可以不加。

## 社区行为准则

[行为准则](CODE_OF_CONDUCT.md)（Contributor Covenant 2.1）— 在此。

## 安全报告

不要在公开 issue 中报告——见 [SECURITY.md](SECURITY.md)。

## 更多文档

- [`PRIVACY.md`](PRIVACY.md) — 精确的数据处理边界。
- [`docs/architecture.md`](docs/architecture.md) — 公开架构总览。
- [`docs/threat-model.md`](docs/threat-model.md) — 防御什么、不防御什么。
- [`docs/device-matrix.md`](docs/device-matrix.md) — 具体设备上的实际测试结果。
- [`RELEASING.md`](RELEASING.md) — 发布流程和版本管理的强制性规则。
- [`CLAUDE.md`](CLAUDE.md) — AI 代理的代码操作说明。

## 许可证

所有贡献 — 遵循 GPL-3.0-or-later（与其余代码相同）。提交 PR = 同意此许可证，无需额外 CLA。
