# 安全政策

## 报告漏洞

**请勿为安全漏洞开放公开 issue。**

Cally 涉及敏感领域（电话音频、特权 Shizuku-binder、带 shell-UID 进程的 AIDL）。`verifyCaller()` 中的错误、生命周期竞态或转录中的后门可能给用户带来实际后果。

### 如何报告

- **Email:** `ua.lyo.su@gmail.com`（主题行：`[cally-security] 简要描述`）
- **GitHub 私有漏洞报告:** 仓库的 Security tab → Report a vulnerability
- 加密（PGP）——根据请求提供，key fingerprint 将在回复中发送

### 应描述的内容

- 应用版本（`versionName` + `versionCode`）
- Android 版本、设备、Shizuku 是否激活
- 复现步骤
- 预期行为 vs 实际行为
- 影响：机密性 / 完整性 / 可用性
- 可能的话——PoC 或崩溃堆栈跟踪

### SLA

- **确认收到：** 72 小时
- **分类 + 严重性分级：** 7 天
- **修复：** Critical — 14 天，High — 30 天，Medium — 90 天，Low — 无硬性期限
- **协调披露：** 报告后 90 天或修复日（以较早者为准）。如果漏洞已在野外被利用，可协商更长的 embargo。

### 名人堂

以负责任方式报告的研究人员将被列入 `docs/security-credits.md`（经你许可）。目前没有金钱漏洞赏金——项目非商业性质；对于活跃的研究贡献，可邀请共同申请 grants。

## 支持的范围

范围内：

- AIDL 合约 `:aidl/IRecorderService` — 任何绕过 `verifyCaller()` 的调用者攻击
- `WrappedShellContext` / `HiddenApiBootstrap` — 超出"使用 shell 身份创建 AudioRecord"意图的权限提升
- `RecorderService` 生命周期 — 竞态条件、FD/PID 泄漏、混淆代理攻击
- 转录 — API 密钥泄漏、用户端点的 MITM、通过转录文本注入日志
- DataStore / `AppSettings` — 在应用沙箱外未经授权访问密钥

范围外：

- 长时间测试中的过度电池消耗（这是性能 bug，非安全问题）
- 针对 Shizuku 服务器的攻击（请报告至 [thedjchi/Shizuku/issues](https://github.com/thedjchi/Shizuku/issues) — 活跃社区 fork；上游 RikkaApps 长期未更新）
- 针对用户配置的 STT 端点的攻击（这是他们的责任）
- 通过本地存储填满的 DoS（用户可控）
- `apk_extracted/` 或其他本地 artifacts — 这些是我们的本地研究，未发布

## 什么**不是**漏洞

- INTERNET 权限存在——这已记录在案（云端 STT 可选）。不是漏洞。
- API 密钥以明文存储在 DataStore 中——已在 README → 安全中记录。在非 root 设备上，这是应用私有存储。如果你找到在不 root 的情况下从其他应用沙箱提取它的方法——**这才是漏洞**。
- 端点 URL 由用户配置——即使输入 HTTP 也会被 `network_security_config` 拒绝。如果用户自己输入 `http://...` 并被拒绝——这是预期设计，不是漏洞。
