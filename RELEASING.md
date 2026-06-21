# 发布流程 — 强制性规则

本文档是 **load-bearing**。违反版本管理规则会导致用户升级失败（Shizuku 守护进程不匹配、AccessibilityService 激活丢失、签名锁定不匹配）。

## 非可选规则

### 1. 每个用户可见的 PR 都必须更新 CHANGELOG.md

每个更改用户可见行为（UI、功能、修复、安全、有运行时影响的依赖）的 PR **必须**在 `CHANGELOG.md` 的 `## [Unreleased]` 部分相应分类下添加条目：

- `### 新增` — 新功能
- `### 变更` — 现有功能的行为变更
- `### 修复` — bug 修复
- `### 安全` — 安全相关变更
- `### 移除` — 移除的功能 / API
- `### 已弃用 (Deprecated)` — 将在未来版本中移除

**不需要更新 CHANGELOG 的情况：**
- 纯内部重构，无可见变更
- 测试变更
- CI/build 基础设施（不影响 APK）
- 文档（CONTRIBUTING / SECURITY / README — 仅限非政策变更）

如有疑问——更新。留空比多一行更糟糕。

### 2. 版本 bump 矩阵

两个独立的版本号，按不同规则 bump：

| 变更内容 | `versionCode`（应用） | `versionName`（语义化） | `userServiceVersion` |
|---|---|---|---|
| 任何面向用户的发布构建 | **+1** 强制 | 按语义化版本 | 不变 |
| Bug 修复（不破坏任何东西） | +1 | **patch**（0.1.0 → 0.1.1） | 不变 |
| 新的用户可见功能，向后兼容 | +1 | **minor**（0.1.0 → 0.2.0） | 不变 |
| 公共 UI / 数据格式的破坏性变更 | +1 | **major**（0.1.0 → 1.0.0） | 不变 |
| `IRecorderService.aidl` 变更（方法、签名、ParcelFileDescriptor 语义） | +1 | 按语义化版本 | **+1 强制** |
| `AudioRecorderJob` pump 语义变更（采样率处理、FD 生命周期、线程） | +1 | 按语义化版本 | **+1 强制** |
| `RecorderService.verifyCaller()` 逻辑变更 | +1 | 按语义化版本 | **+1 强制** |
| `WrappedShellContext` 身份或 `HiddenApiBootstrap` 豁免变更 | +1 | 按语义化版本 | **+1 强制** |
| 仅 `:userservice` 内部重构，未更改 AIDL/pump/verify/wrap | +1 | 按语义化版本 | 可选 |
| `:app` 中的 UI 增强/修复，不涉及 `:userservice` / `:aidl` | +1 | 按语义化版本 | 不变 |

**为什么需要 `userServiceVersion`**：带 `daemon=true` 的 Shizuku 守护进程在我们的 APK 退出后仍然存活。旧守护进程（v=10）看到新 APK（v=11）会重新生成。如果不 bump——守护进程继续使用其旧版本，AIDL 事务不匹配，用户遭遇静默失败。

**为什么 `versionCode` 总是 +1**：Android Package Manager 拒绝升级相同 versionCode 的 APK（降级保护）。如果你构建发布版而不 bump → 用户无法在之前版本的基础上升级。

### 3. 签名身份在不同发布之间不得更改，除非是明确的破坏性发布

问自己：所有用户是否准备好**从头重新安装**应用（如果 backup-rules 禁止了备份，将丢失设置和录音）？

如果答案是否定的——你**不能**更改：

- `keyAlias`
- Release keystore（新 store = 新 SHA-256）
- `applicationId`
- 整体签名配置

更改签名 → 新 APK 不被视为升级 → 需要卸载旧版本。如果你的用例需要这样做（例如 rebrand `dev.lyo.callrec` → `dev.lyo.cally`）——这是一个**独立的 major-release**，需提前 4-6 周公告并提供迁移工具（导出 → 重新导入）。

### 4. Tag 格式

```
v<MAJOR>.<MINOR>.<PATCH>          # 标准发布
v<MAJOR>.<MINOR>.<PATCH>-<pre>    # 预发布：-alpha.1, -beta.2, -rc.1
```

Tag 在 CHANGELOG 的 `[Unreleased]` 部分重命名为带日期的 `[X.Y.Z]` 后从 `main` 创建。

```bash
git tag -a v0.2.0 -m "cally v0.2.0 — 简要描述"
git push origin v0.2.0
```

## 发布检查清单（起飞前）

在 `git tag` 之前逐项检查：

- [ ] 自上个 tag 以来的所有用户可见变更已描述在 `CHANGELOG.md` 的 `## [Unreleased]` 中
- [ ] `## [Unreleased]` 已重命名为 `## [X.Y.Z] — YYYY-MM-DD`
- [ ] `CHANGELOG.md` 中 `## [Unreleased]` 下已创建新的空白标题及子分类
- [ ] `CHANGELOG.md` 底部的 Compare-link 已更新
- [ ] `app/build.gradle.kts` 中的 `versionCode` 已 bump
- [ ] `app/build.gradle.kts` 中的 `versionName` 与新 tag 匹配
- [ ] 如需要，`userservice/build.gradle.kts` 中的 `userServiceVersion` 已 bump（见上方矩阵）
- [ ] `./gradlew test lintRelease assembleRelease` 无错误通过
- [ ] Release APK 已在真机上验证（至少一台 Pixel + 一台三星）
- [ ] Release APK 已验证从上一发布版本升级（非全新安装）
- [ ] 如果更改了 AIDL——**强制** soak 测试：升级后连续 5 次以上通话，不重启设备（验证守护进程重新生成）
- [ ] 已签名 APK 已上传至 GitHub Release，release notes 中包含 SHA-256
- [ ] CHANGELOG 条目已复制到 release notes
- [ ] Tag 已创建并推送

## 回滚

如果发布版本有问题：

1. **不要对已推送的 tag 执行 `git tag -d`**——用户可能已经克隆。
2. 发布**补丁版本**（X.Y.Z+1）修复问题。永远不要"替换" tag。
3. 在 CHANGELOG 中 X.Y.Z 下标注：`> ⚠️ 此版本存在严重缺陷 — 请使用 X.Y.Z+1。`
4. 在 GitHub Release 中点击"Mark as pre-release"或如果严重则从发布列表中删除。
5. 不要从 release assets 中删除已签名 APK——用户已经拥有它。

## 谁可以打 tag

只有具有 `main` 提交权限的维护者。Tag 是关于兼容性和安全性的公开承诺。不要从 feature-branch 打 tag。
