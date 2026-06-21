# 隐私政策

**最后更新：2026-04-27**

本文档是关于 Cally 如何处理数据的真实说明。不是法律术语模板；我们尽力用通俗语言书写。如果你发现本文档与实际应用行为存在差异——这是一个 bug，请通过 [SECURITY.md](SECURITY.md) 报告。

## 简而言之

- Cally **不发送遥测、分析、崩溃报告或任何诊断数据**。
- Cally **不使用 Firebase、Crashlytics、Sentry、Mixpanel、Amplitude、Google Analytics、Facebook SDK** 或任何第三方 SDK。
- Cally **没有账户系统**，不在任何地方注册你，不要求邮箱或电话号码，不进行任何"绑定"。
- 通话录音**本地存储在你的设备上**。Cally 自身不会将其发送到任何地方。

## 处理哪些数据

### 设备本地（应用沙箱内）

- **电话通话音频** — 当你正在进行通话时。存储在 `Android/data/dev.lyo.callrec/files/recordings/`。AAC 或 WAV 格式。
- **录音元数据** — 号码、姓名（来自你的联系人）、通话时间、时长、使用的 AudioSource、成功/失败。存储在应用的 Room 数据库中。
- **转录文本** — 如果你启用了云端转录。带有分段、说话人、语气的 JSON 结构。与录音元数据一起存储。
- **设置** — 录音格式、采样率、主题、STT API 密钥（如已输入）、STT 端点 URL、STT 模型。Android DataStore Preferences。
- **导出缓存** — 当你点击分享时，立体声混音创建在 `cacheDir/export/` 中。Android 在内存压力下会自动清理 cacheDir。

### Cally 请求哪些系统权限以及原因

| 权限 | 用途 |
|---|---|
| `RECORD_AUDIO` | AudioRecord 构造函数的 API 要求（实际录音在 Shizuku 进程中进行）。 |
| `READ_PHONE_STATE` | 检测 OFFHOOK / IDLE——以便自动开始/停止录音。 |
| `READ_CALL_LOG`、`READ_CONTACTS` | 从号码解析联系人姓名用于 UI 显示。联系人数据不离开设备。 |
| `POST_NOTIFICATIONS` | 显示正在录音的通知（FGS 强制要求）和录音已保存的通知。 |
| `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` | 在通话期间保持录音服务存活。 |
| `SYSTEM_ALERT_WINDOW` | 1×1 px overlay 持续约 3 秒——绕过 Android 14+ "后台启动 FGS" 的限制。不在屏幕上显示任何内容。 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 没有豁免，Doze 会在长时间通话中杀死 FGS。 |
| `INTERNET` | **仅用于**可选的云端转录。输入 API 密钥之前——零网络请求。 |
| `READ_LOGS`、`WRITE_SECURE_SETTINGS` | 已声明（供未来功能使用），v0.1.0 中实际未使用。 |
| `moe.shizuku.manager.permission.API_V23` | 通过 Shizuku 绑定 UserService。 |
| `BIND_ACCESSIBILITY_SERVICE` | AccessibilityService——空 stub。声明用于从后台启动 FGS 的豁免。**不读取任何无障碍事件。** |

## 云端转录（可选）

这是一个**主动选择加入的功能**。默认关闭。启用条件：

1. 打开设置 → 转录。
2. 输入 API 密钥。
3. 可选地选择其他端点 URL（默认：OpenRouter）、其他模型（默认：Gemini 3.1 Flash）。
4. 在播放器中点击特定录音的"识别语音"。

**发生了什么：**

- Cally 将**此特定录音的音频文件**（作为 chat-completions JSON 负载中带有 `input_audio` content part 的 base64）发送到你指定的端点 URL。
- 仅 HTTPS。`network_security_config.xml` 禁止明文，即使你误输入 `http://`。
- Bearer token（你的 API 密钥）在 Authorization header 中。
- 端点处理后返回带有转录文本的 JSON。
- Cally 将结果本地存储，关联到该录音。

**音频去了哪里：** 你的端点 URL 指向的地方。默认是 `https://openrouter.ai/api/v1`。可以替换为：

- 其他云服务商（OpenAI、Groq、Together AI 等）
- 自托管服务器（vLLM-Omni with Qwen2.5-Omni-7B 或 vLLM with Gemma 4 multimodal——这种情况下音频完全不会离开你的网络）

**Cally 在云端转录时**不**对音频做什么：**

- 不向 Cally 开发者发送副本
- 不在我们的数据库中存储
- 不发送关于你的元数据（你的号码、联系人、通话时间）
- 不重复使用音频训练模型（这取决于端点提供商的政策，而非我们的）

**取决于端点提供商的事项：**

当你将音频发送到端点时，端点提供商的隐私政策**已**适用。我们无法控制 OpenRouter / OpenAI / Google / Anthropic 如何处理你的请求。如果这对你至关重要——请使用自托管端点。

## 谁是数据控制者（GDPR 术语）

- **Cally 作为软件**：既非控制者也非处理者。软件不在你的设备之外存储数据。
- **你作为用户**：关于你自己通话录音的数据控制者。你有责任遵守当地法律（参见 README → 法律背景）。
- **你的 STT 端点提供商**（如启用了云端转录）：在处理时刻是音频的处理者。你是控制者。

## 数据存储 / 删除

- **删除单条录音**：在录音列表中长按 → 删除。
- **删除所有应用数据**：Android 设置 → 应用 → Cally → 存储 → 清除数据。将删除数据库、设置、所有 WAV/M4A 文件。
- **备份**：我们已在 `data_extraction_rules.xml` 和 `backup_rules.xml` 中**禁用** Android 云端备份和设备迁移。录音不会复制到 Google 账号。

## 儿童数据（COPPA / GDPR 第 8 条）

Cally 不面向 16 岁以下用户，不会有意收集其数据。如果你发现未成年人使用 Cally——应用对其录音的处理方式与任何其他录音相同（本地存储，不对外发送）。

## 我们如何通知政策变更

本隐私政策的变更——记录在 `git log`（仓库公开）和 `CHANGELOG.md` 的"安全"分类下。重大变更（新的数据流、新权限）也会在相应版本的 release notes 中公告。

## 联系方式

隐私相关问题——`ua.lyo.su@gmail.com`（主题：`[cally-privacy] ...`）。漏洞披露——请参阅单独的 [SECURITY.md](SECURITY.md)。
