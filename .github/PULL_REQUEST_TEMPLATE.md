<!--
感谢你的 PR。以下检查清单是为了避免我们在评论中追问基本问题。
如果有不相关的内容——直接划掉或删除即可。
-->

## 变更内容

<!-- 简要描述：为什么，而非是什么（diff 本身已显示） -->

## 变更类型

- [ ] Bug 修复
- [ ] 新功能
- [ ] 破坏性变更（公共 UI / 行为 / 数据格式的变更）
- [ ] `IRecorderService.aidl` / `AudioRecorderJob` / `verifyCaller()` / `WrappedShellContext` 变更（需要 bump `userServiceVersion`）
- [ ] 文档 / 测试 / CI / 构建基础设施
- [ ] 无行为变更的重构

## 检查清单

- [ ] 我已阅读 [`CONTRIBUTING.md`](../CONTRIBUTING.md)
- [ ] 测试在本地通过（`./gradlew test`）
- [ ] Lint 通过（`./gradlew lintDebug`）
- [ ] 我已更新 [`CHANGELOG.md`](../CHANGELOG.md) 的 `## [Unreleased]` 部分（如果变更对用户可见）
- [ ] 如果更改了 AIDL / pump / verify / WrappedShellContext — 已在 `userservice/build.gradle.kts` 中 bump `userServiceVersion`（见 [`RELEASING.md`](../RELEASING.md)）
- [ ] 如果涉及设备覆盖率变更 — 已更新 `docs/device-matrix.md`
- [ ] 代码中无 emoji，无自我描述性注释（见 CONTRIBUTING）

## 测试设备

<!-- Pixel 7 / 三星 S22 / 等 — `:userservice` 变更必须填写 -->

## 相关 issue / discussions

<!-- Closes #123 / Refs #456 -->
