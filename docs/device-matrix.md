# 设备兼容性矩阵

具体设备上的实际录音测试结果。通过 PR 提交（参见 [CONTRIBUTING.md](../CONTRIBUTING.md) → "目前最有价值的贡献"）。

## 结果等级

- ✅ — 双方声音均达到足够水平（uplink RMS > -40 dBFS, downlink RMS > -40 dBFS），无崩溃
- ⚠️ — 正常录制，但一方静音或有杂音
- 🟥 — 回退至 MIC-only，无下行音频
- ❌ — `STATE_UNINITIALIZED` / SecurityException / 崩溃

## Pixel

| 设备 | Android | 补丁 | 策略 | 结果 | 备注 |
|---|---|---|---|---|---|
| _添加你的设备_ | | | | | |

## 三星

| 设备 | Android / One UI | 补丁 | 策略 | 结果 | 备注 |
|---|---|---|---|---|---|
| _添加你的设备_ | | | | | |

## 其他（小米、Nothing、OnePlus、Honor……）

| 设备 | Android | 补丁 | 策略 | 结果 | 备注 |
|---|---|---|---|---|---|
| _添加你的设备_ | | | | | |

## 如何填写

1. 安装 debug 构建：`./gradlew :app:installDebug`。
2. 激活 Shizuku，授予权限。
3. 给自己打一个测试电话（另一个 SIM/SIP 号码）。
4. 在应用 → 设置 → 调试中查看使用的策略（或在 logcat 中：`adb logcat -s RecorderController`）。
5. 在播放器中试听两个轨道——评估音量水平。
6. 在对应表格中添加一行 + 可选地在备注中提供 `Build.FINGERPRINT`。

完整复现格式：`Build.MANUFACTURER` / `Build.MODEL`（Android `Build.VERSION.RELEASE`，补丁 `Build.VERSION.SECURITY_PATCH`），来自 `RecorderController` 的策略（如 `DualUplinkDownlink`），按上述等级标注结果。
