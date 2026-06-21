# 更新日志

所有显著变更记录于此。格式：[Keep a Changelog](https://keepachangelog.com/en/1.1.0/)，版本管理遵循 [SemVer](https://semver.org/spec/v2.0.0.html)。

## [Unreleased]

### 新增
-

### 变更
-

### 修复
-

### 安全
-

### 移除
-

### 已弃用 (Deprecated)
-

## [0.5.0] — 2026-05-01

### 变更
- **STT 混音重新设计为 RMS 归一化单声道** (`AudioMixer.mixNormalizedMonoForStt`)。此前发送 soft-pan 立体声，期望实现声道分离的说话人识别，但 Gemini 官方文档 ([ai.google.dev/gemini-api/docs/audio](https://ai.google.dev/gemini-api/docs/audio)) 明确指出：*«multi-channel audio is automatically combined into a single channel»*——pan 被忽略，且立体声额外产生 ±10% 的时间戳漂移。模型只听到用户声音的真正原因：上行（mic-direct）比下行（post-codec）在混合中大约响 12 dB。现在每侧缩放到约 −18 dBFS RMS 后再混合 → 两个说话人在单声道混音中以可比较的响度出现。提示词已更新：移除了左右声道的指令（对模型来说是虚假信息），增强了基于声音特征的说话人识别。Soft-pan 立体声混音保留用于分享/播放，它在人耳听感中表现正常。

### 修复
- **通话转录不再将所有对话归给"我"** — 上述变更的结果。症状：在双流录音中，`Transcriber` 仅返回 `speaker_id="ME"` 的分段，对方声音要么被完全忽略，要么被归入用户的发言中。

### 安全
-

### 移除
-

### 已弃用 (Deprecated)
-

## [0.4.1] — 2026-04-28

### 修复
- **`RecorderController.tryDual` / `trySingle`** — 内部 `catch (DeadObjectException)` 比外部 `openStrategy` 中的 `catch (RemoteException)` 更窄，导致所有其他 `RemoteException` 子类（TransactionTooLargeException 等）被下一个 `catch (Throwable) { 0 }` 吞掉并被归类为 `InitFailure` → 污染能力缓存。现在按层级捕获整个 `RemoteException`，dead-object 也走相同路径 → 正确的 `Transient` 分类，无缓存污染。
- **`DaemonHealth.NotInstalled` 现在真正可达** — `ShizukuClient.recompute()` 额外尝试 `PackageManager.getPackageInfo("moe.shizuku.privileged.api", 0)` 以区分"未安装"和"已安装但未运行"。此前两种情况都归类为 `NotRunning`，"cally: Shizuku not installed"通知和引导页中相应的 StepCard 是死代码。
- **引导页步骤序号** — 在 Android 13+ 上 POST_NOTIFICATIONS 和"系统权限"的 `index` 均为 4，用户看到"1, 2, 3, 4, 4, 5, 6"。改用计数器 `var stepIdx = 4; index = stepIdx++`，在 Tiramisu+ 上得到一致的 4/5/6/7，在旧版本上得到 4/5/6。
- **`AudioLevelMeter` 预热现在为 500 ms 实际音频** — 此前校准持续 50 次 `update()` 调用，在 8KB pump chunk 下 = ~25 秒。Pixel 回铃音（~1 s）落入中位数 → calibratedFloor 被高估 → 实际对话被判定为静音 → 策略在回退链上不必要地降级。现在门控条件为 `totalFrames >= sampleRate / 2`，保证 500 ms，与缓冲区大小无关。

### 安全
- **`SeedDataActivity`（仅 debug）现在验证调用者** — 此前 `exported=true` 且无运行时防护，允许安装了 debug 版本的设备上任何应用通过简单 Intent 清空录音数据库。已添加 `Activity.referrer.host == "com.android.shell" || == packageName` 检查——来自 ADB 的 `am start` 通过，其他设备内调用被阻止并显示 Toast"untrusted caller blocked"。曾尝试首先使用 `exported=false`，但三星 One UI 16 即使在 debuggable 构建上也拒绝 shell 启动非导出 activity。
- **`CompletedRecordingNotification.visibility = VISIBILITY_PRIVATE`** — 此前为 `VISIBILITY_PUBLIC`，锁屏显示完整联系人姓名（"通话已录制：Джерело — Харків · 3:42"）。对 T2（记者）构成实际 OPSEC 问题。正在录音的通知保持 `PUBLIC`，符合威胁模型"不隐藏录音事实"的决策。

### 变更
- 移除了 `RecorderController.stop()` 上方的重复 KDoc 块。

## [0.4.0] — 2026-04-28

### 新增
- **手动录音中的语音备忘录模式** — `RecorderController.start(callId, voiceMemo = true)` 跳过整个 dual/voicecall 回退链，直接使用 `Strategy.SingleMic`。没有通话音频路径时，其他策略无论如何都会降级到 MIC，但会经历 5 秒静音且横幅中错误显示"downlink silent"。会话在数据库中标记为 `mode = "VoiceMemo"`，跳过 CallLog 后处理以避免将随机联系人关联到录音机录音。
- **多说话人识别 + 转录智能标题** — `Transcriber` 提示词已更新，模型返回 `speakers: [{id, label}]` 而非固定的 me/them，外加 `title` 字段（最多 60 字符的简短描述）。`TranscriptCodec` 解析器处理新模式，对旧录音向后兼容（旧的 `speaker: "me"|"them"` 转换为合成 speakers）。录音列表中，语音备忘录终于获得有意义的名称（如"购物清单和周末计划"）而非日期，只要用户进行了转录。
- **Telegram 风格的聊天布局用于转录** — `TranscriptView` 渲染为即时通讯风格：主要说话人（id=ME 或列表中的第一个）显示在右侧 `primaryContainer` 气泡中，其他人显示在左侧中性 `surfaceContainerHigh` 中，带有首字母头像（仅限分组的第一条发言），气泡轮廓采用不对称的 `RoundedCornerShape`。时间和语气显示在气泡右下角。
- **精心设计的 8 色调色板用于说话人——仅在头像上** — 每个独特的声音获得自己的色调（azure / amber / teal / rose / indigo / olive / terracotta / cyan），有单独的浅色和深色主题变体。颜色仅存在于首字母头像和分组第一条发言上方的标题标签中；气泡保持中性（TG 模式："彩色头像，灰色气泡"）。调色板是 `PlaybackScreen.chatAccents()` 中的局部实现，因为 M3 `colorScheme` 仅提供约 3 种容器角色——不足以满足 4 个以上说话人的分类颜色需求。

### 变更
- **停止按钮现在位于状态横幅中** — 此前位于右下角浮动工具栏中，与顶部横幅中的录音状态视觉关联差。现在带有停止图标的 `FilledIconButton` 位于横幅右侧，紧邻"录制中"文本。移除了 FAB（无重复）。
- **录音列表和实时电平表为语音备忘录隐藏第二轨道** — `Levels.voiceMemo: Boolean` 从 `RecorderController` 穿透到 UI；`StatusBanner` 仅渲染上行电平表，RecordingRow 显示"语音备忘录 · 4月28日, 15:42"而非空白占位符。
- **转录直接渲染，不包裹在 Card 中** — 此前 `TranscriptionSection` 包裹在 `surfaceContainerLow` Card 中，消耗约 32 dp 内边距并重复视觉层次（Card 中有 Card）。现在聊天气泡直接放置在播放屏幕表面上，类似 Telegram 的消息流。

### 修复
- 无联系人的录音不再显示为"—"——语音备忘录正确显示"语音备忘录"+ 日期/时间，如果已转录则显示 AI 标题。

## [0.3.0] — 2026-04-28

### 新增
- **绕过健康状态 AIDL 信号** — `WrappedShellContext` 现在跟踪反射补丁覆盖率（sCurrentActivityThread / mSystemThread / mInitialApplication / mBoundApplication），作为 `BypassHealth` 枚举（Failed / Degraded / Full），通过新的 AIDL 方法 `getBypassHealth() = 4` 可访问。当实际原因是绕过降级时，`RecorderController` 中的校准回退链不再用策略失败信息污染 `Capabilities` 缓存。
- **`DaemonHealth` 密封状态机** — 替代旧的 `ShizukuState` 枚举 + 独立的 `service: StateFlow<IRecorderService?>`。六种状态：`NotInstalled / NotRunning / NoPermission / Stale / Bound(service) / Unhealthy(reason)`。所有 UI 和服务消费者获取 Shizuku/UserService 状态的单一事实来源。
- **关于守护进程健康的系统通知** — 当 `DaemonHealth != Bound` 时，新的 `callrec.status` 通道通知出现，恢复时自动消失。点击打开设置屏幕，带有深度链接的状态感知操作（`EXTRA_FROM_HEALTH_NOTIF`）。使用 VISIBILITY_PRIVATE 保护隐私。
- **`AudioLevelMeter` 中的自适应噪底** — `calibratedFloor` 学习为前约 500 ms 样本的中位数 RMS；`isAudible` 返回 `lastRms > calibratedFloor + AUDIBLE_DELTA (0.008f)`。替代固定 `AUDIBLE_THRESHOLD = 0.005`——每路流自适应阈值。
- **用于分类失败的 `OpenResult` 密封类型**，在 `RecorderController.openStrategy` 中：`Success(outcome) / InitFailure(reason) / Transient(reason)`。Transient（DeadObjectException / RemoteException / SecurityException）退出时不改变缓存。
- **引导页中的 POST_NOTIFICATIONS 选择加入步骤**（Android 13+）— 确保守护进程健康通知确实对用户可见。
- **引导页提示卡**，推荐带有自动重启看门狗的社区 Shizuku 构建版（`thedjchi/Shizuku`）。
- **仅 debug 的 `DaemonHealthDebugActivity`** 用于分类设备矩阵问题——显示 BypassHealth、DaemonHealth、Capabilities 缓存。不包含在 release 中（`app/src/debug/`）。
- **在 `Lifecycle.State.RESUMED` 时进行一次性健康验证**，通过 `ProcessLifecycleOwner`——当用户打开应用时检测僵尸守护进程。零空闲轮询（其余情况由事件驱动）。

### 变更
- `CallMonitorService.kickoff()` 现在等待 `health.filterIsInstance<DaemonHealth.Bound>().first().service` 而非分离的 `bind()` + `service.filterNotNull()`——统一的复合信号，包含"已绑定 + 版本匹配 + 权限 OK"。
- `recordingStarted` 现在在 `kickoff()` 的 `try/finally` 块中重置，在任何错误时（修复了 STICKY 重启在 kickoff 失败后看到 `recordingStarted=true` 的竞态）。
- `OverlayTrick.briefly()` 现在严格断言 `canShow=true` 前提条件；检查上移到 `CallStateReceiver`，如果缺少 overlay 权限则发送用户可见通知。
- `RecorderController` 校准写入现在门控于 `mutateCache: Boolean = (bypassHealth == Full)`——在 Degraded 绕过时缓存不被污染。
- `MainActivity` 具有 `launchMode="singleTop"` + `onNewIntent` 处理 `EXTRA_FROM_HEALTH_NOTIF` 触发健康重新检查。

### 移除
- AIDL 方法 `probeSource = 20`（从未被 app 调用——死亡接口）。事务码 20 以注释形式保留。
- AIDL 方法 `grantPermission = 30`（从未被 app 调用——死亡接口）。事务码 30 以注释形式保留。
- `RecorderService` 中的 `ALLOWED_GRANT_PERMS` 伴生字段。
- manifest 中的 `READ_LOGS` 权限（仅 `grantPermission` 使用——两者均已移除）。
- `ShizukuState` 枚举文件（被 `DaemonHealth` 替代）。
- `OverlayTrick.briefly` 中的静默 no-op 分支（现为严格模式）。
- `RecorderController` 中的 `bailedOnDeadDaemon: Boolean` 标志（被 `OpenResult.Transient` 语义替代）。

### 修复
-

### 安全
-

### 已弃用 (Deprecated)
-

## [0.2.0] — 2026-04-28

### 新增
- 首次启动法律免责声明（ModalBottomSheet），采用三级司法管辖区分类：(1) 一方同意——无需通知，(2) 多方同意 + 默示同意——通知即足够，(3) 明确同意——需要明确的知情同意（DE/AT/BE）。每个级别点击展开，显示法律引用（§ 201 StGB、18 U.S.C. § 2511、CA Penal Code § 632 等）。始终可见的技术说明：关于无法通过上行链路实现提示音通知。
- 设置 → 关于应用 → "法律声明"——以只读方式重新打开相同的表单。
- 带版本的 DataStore 标志 `disclaimer_accepted_v1`，用于将来文本重大更改时重新提示。
- WAV 编码器每约 1 MB 重写 RIFF 头——被强制终止的录音仍可播放。
- DB↔FS 对账遍历标记音频文件已在应用外被删除的录音。
- 云端 STT API 密钥通过 Android Keystore 支持的 AES/GCM 静态加密。

### 变更
- README："乌克兰以外用户"部分重写为三级结构（无需通知 / 通知即足够 / 明确同意），附有"每个级别允许什么"的摘要矩阵。
- 电话号码和联系人姓名不再出现在 release logcat 的 INFO 级别（仅 DEBUG）。
- AAC 编码器在关闭期间重试 EOS 输入槽最多 5 次——MP4 容器的 moov atom 现在总是被写入。
- 录音 pump join 延长至 5 秒；如果 pump 仍然阻塞——管道 FD 强制关闭。
- 策略回退链在 Shizuku 守护进程 binder 在尝试过程中死亡时优雅终止，而非报告"全部静音"。

### 修复
- Release 构建现在激活 verifyCaller 签名证书锁定（此前 signingSha256 为空）。
- 清理请求跳过仍在进行中的录音（ended_at IS NULL）。
- 从 UserService 授予允许列表中移除 WRITE_SECURE_SETTINGS——v0.2.0 中没有任何路径使用它。
- 策略回退链在 Shizuku 守护进程 binder 在尝试过程中死亡时，不再将正常工作的策略放入 `knownFailedInit`——选择缓存保持有效。

### UserService
- `userServiceVersion` 10 → 11。AudioRecorderJob.stop() 现在在 join 超时时强制关闭管道 FD，RecorderService 从允许列表中移除 `WRITE_SECURE_SETTINGS`。APK 更新后，带 `daemon=true` 的守护进程通过 `ShizukuClient.onServiceConnected` 中的版本不匹配自动重新生成。

### 计划中
- SAF 集成（`OpenDocumentTree` + MediaStore 镜像）
- 英文语言包（`values-en/strings.xml`）
- 加密保险库（AES-GCM，PIN/生物识别）
- GitHub Actions CI（lint + test + assembleDebug）
- Material 3 ButtonGroup 重载迁移（5 个调用点 — `overflowIndicator` 参数）

## [0.1.0] — 2026-04-27

第一个公开发布。MVP 骨架。

### 新增
- `:aidl/IRecorderService` AIDL 桥接，连接 app 进程和 Shizuku UserService。
- 带 `daemon=true` 的 Shizuku UserService：进程在应用从最近任务中划掉后仍然存活。
- 通过 `WrappedShellContext` 在 shell 进程（UID 2000）中的特权录音器（伪装 `com.android.shell` 以通过 AudioFlinger 门控）。
- 带实时可听性验证的 5 步回退链，按 `Build.FINGERPRINT` 缓存：
  1. `VOICE_UPLINK` + `VOICE_DOWNLINK`（双路并行）
  2. `MIC` + `VOICE_DOWNLINK`（三星友好）
  3. `VOICE_CALL` 立体声（L=上行/R=下行）
  4. `VOICE_CALL` 单声道
  5. 仅 `MIC`（最后手段）
- 默认 AAC-in-MP4 编码器（`MediaCodec` + `MediaMuxer`），WAV 可选。
- 前台服务 `type=specialUse` + 不可见 1×1 overlay 用于 Android 14+ 后台启动 FGS 绕过。
- Material 3 Expressive UI（主题、动效、形状变形、波浪 LoadingIndicator、FloatingToolbar）。
- 4 个屏幕：引导页、主页（录音列表）、播放器、设置。
- 通过 `CallStateReceiver` 在 `OFFHOOK` 时自动开始录音。
- 联系人和通话记录解析用于录音元数据。
- 带自定义平衡的立体声混音导出（用于播放器的分享对话框）。
- 波形视图（峰值幅度缩减器 + Canvas，点击和拖动进行 seek）。
- 通过用户配置的 OpenAI 兼容端点的转录（默认 OpenRouter+Gemini Flash）；主动选择加入，无 API 密钥时关闭。
- 点击任意转录气泡以 seek+play 该分段。
- MediaSession + MediaStyle 通知，带传输按钮和蓝牙耳机媒体键。
- 每次通话后显示已保存录音的通知。
- 每次 AIDL 调用执行 `verifyCaller()`：UID + SHA-256 release 证书锁定。
- 每应用语言配置（Android 13+）— 目前仅 uk-UA。

### 安全
- `network_security_config.xml` 中 `cleartextTrafficPermitted=false`。
- `data_extraction_rules.xml` + `backup_rules.xml` 阻止 adb 备份和云端恢复。
- Hidden API 豁免限定于 5 个 framework 前缀（纵深防御，非 `""`）。
- 无任何 Firebase / Crashlytics / Sentry / analytics。

### 已知限制
- 仅 uk-UA 语言。
- VoIP（WhatsApp/Telegram/Viber/Signal）原则上不支持——不涉及电话音频路径。
- 通话中使用蓝牙耳机可能在某些 HAL 上破坏录音。
- 三星 One UI 5.1+ 需要回退到 MIC-only 策略——shell UID 下 VOICE_* 返回静音。

[Unreleased]: https://github.com/LyoSU/cally/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/LyoSU/cally/compare/v0.4.1...v0.5.0
[0.4.1]: https://github.com/LyoSU/cally/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/LyoSU/cally/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/LyoSU/cally/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/LyoSU/cally/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/LyoSU/cally/releases/tag/v0.1.0
