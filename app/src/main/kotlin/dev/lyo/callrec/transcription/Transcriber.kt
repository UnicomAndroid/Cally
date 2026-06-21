// SPDX-License-Identifier: GPL-3.0-or-later
package dev.lyo.callrec.transcription

import android.content.Context
import android.util.Base64
import dev.lyo.callrec.core.L
import dev.lyo.callrec.di.AppContainer
import dev.lyo.callrec.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud-based speech-to-text using any OpenAI-compatible chat-completions
 * endpoint that accepts `input_audio` content parts (Gemini, OpenAI gpt-4o-
 * audio, Groq, OpenRouter that proxies any of those, etc.).
 *
 * Default config points at OpenRouter with `gemini-3.1-flash-lite-preview` —
 * the user can override base URL / model / API key in Settings. We never
 * proxy; the request goes straight from the device to whichever endpoint the
 * user configured.
 *
 * Whisper.cpp local was tried first but `tiny` model's Ukrainian accuracy
 * was too low to be useful, and shipping `medium` (~1.4 GB) on every
 * install was a non-starter.
 */
interface Transcriber {
    suspend fun transcribe(audioFile: File): String
}

object TranscriberFactory {
    fun create(container: AppContainer): Transcriber = CloudTranscriber(container.settings)
}

private class CloudTranscriber(private val settings: AppSettings) : Transcriber {

    override suspend fun transcribe(audioFile: File): String = withContext(Dispatchers.IO) {
        val baseUrl = settings.sttBaseUrl.first().trimEnd('/')
        val key = settings.sttApiKey.first()
        val model = settings.sttModel.first()
        require(key.isNotBlank()) {
            "未设置 API 密钥。请前往 设置 → 转录 → API 密钥 进行配置。"
        }

        // Send the actual container name. Gemini's OpenAI-compat layer
        // accepts wav/mp3/m4a/aac directly; OpenAI's official Realtime API
        // is stricter (wav/pcm16 only) — users hitting that should toggle
        // RecordingFormat=WAV in Settings.
        val format = when (audioFile.extension.lowercase()) {
            "m4a", "mp4" -> "m4a"
            "aac" -> "aac"
            "wav" -> "wav"
            else -> audioFile.extension.lowercase().ifEmpty { "wav" }
        }
        val url = URL("$baseUrl/chat/completions")
        L.i("STT", "POST host=${url.host} model=$model audio=${audioFile.length()/1024}KB")

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 180_000
            // Stream the request body — earlier code did
            // `Base64.encode(file.readBytes(), …)` which doubles a multi-MB
            // file in memory before the JSON write. With chunked streaming
            // peak heap stays around ~64 KB regardless of audio length.
            setChunkedStreamingMode(0)
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { out -> writeStreamingPayload(out, model, audioFile, format) }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            // Don't log response bodies at INFO — provider errors sometimes
            // echo a fragment of the bearer token. DEBUG only.
            L.d("STT", "code=$code body.len=${body.length}")
            if (code !in 200..299) {
                L.w("STT", "HTTP $code (body length=${body.length})")
                error("HTTP $code 来自 ${url.host}")
            }
            parseTranscript(body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Build and stream the chat-completions JSON envelope around a base64
     * audio payload. The audio is read in 24 KB chunks, base64-encoded into
     * 32 KB output blocks, and written through the connection without ever
     * holding the full file in memory.
     */
    private fun writeStreamingPayload(
        out: OutputStream,
        model: String,
        audioFile: File,
        format: String,
    ) {
        // Build the JSON skeleton with a placeholder where audio bytes go,
        // then split the serialised string and stream the audio between the
        // halves. Lets us reuse JSONObject's escaping for the prompt without
        // hand-rolling encoder logic.
        val placeholder = "__AUDIO_PLACEHOLDER__"
        val audioPart = JSONObject()
            .put("type", "input_audio")
            .put("input_audio", JSONObject()
                .put("data", placeholder)
                .put("format", format))
        val textPart = JSONObject()
            .put("type", "text")
            .put("text", PROMPT)
        val message = JSONObject()
            .put("role", "user")
            .put("content", JSONArray().put(audioPart).put(textPart))
        val root = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(message))
            .put("response_format", JSONObject().put("type", "json_object"))

        val full = root.toString()
        val splitAt = full.indexOf(placeholder)
        check(splitAt > 0) { "JSON serialisation lost the audio placeholder" }
        val prefix = full.substring(0, splitAt).toByteArray(Charsets.UTF_8)
        val suffix = full.substring(splitAt + placeholder.length).toByteArray(Charsets.UTF_8)

        out.write(prefix)
        streamBase64(audioFile, out)
        out.write(suffix)
        out.flush()
    }

    /**
     * Read [audioFile] in 3-byte-aligned chunks and write base64-encoded
     * output to [out]. The 3-byte alignment is required for incremental
     * base64: a chunk that isn't a multiple of 3 produces padding that, when
     * concatenated with the next chunk, no longer decodes correctly. We use
     * `NO_WRAP | NO_PADDING` and only allow the FINAL chunk to introduce
     * padding bytes.
     */
    private fun streamBase64(audioFile: File, out: OutputStream) {
        val chunk = ByteArray(CHUNK_BYTES) // 24 KB → 32 KB base64
        BufferedInputStream(audioFile.inputStream(), CHUNK_BYTES).use { input ->
            var pending: ByteArray? = null
            while (true) {
                val n = input.read(chunk)
                if (n <= 0) break
                if (pending != null) {
                    // Previous read wasn't 3-aligned — emit it now (no padding)
                    // because we have more data following.
                    out.write(Base64.encode(pending, Base64.NO_WRAP or Base64.NO_PADDING))
                    pending = null
                }
                val rem = n % 3
                val aligned = n - rem
                if (aligned > 0) {
                    out.write(Base64.encode(chunk, 0, aligned, Base64.NO_WRAP or Base64.NO_PADDING))
                }
                if (rem > 0) {
                    // Defer the trailing 1-2 bytes; if it turns out to be the
                    // last read, we'll emit them with padding below.
                    pending = chunk.copyOfRange(aligned, n)
                }
            }
            // EOF — flush any tail with proper padding so the decoder closes.
            if (pending != null) {
                out.write(Base64.encode(pending, Base64.NO_WRAP))
            }
        }
    }

    companion object {
        private const val CHUNK_BYTES = 24 * 1024
        // Per-utterance schema. We DON'T pass a JSON schema spec — providers
        // implement it inconsistently — but we describe it precisely in the
        // prompt and let response_format=json_object enforce well-formedness.
        //
        // Multi-speaker diarization: model emits a `speakers` list once and
        // segments reference it by `speaker_id`. We feed a mono mix (Gemini
        // auto-downmixes anything else per their docs) with per-side levels
        // already balanced by the recorder — so diarization rests on voice
        // characteristics alone (tone / pitch / timbre / cadence). For voice
        // memos with several people present the model can lift names from
        // the conversation itself ("Привіт Олю!" → speaker B label "Оля").
        private val PROMPT = """
            转写这段音频。自动识别语言（中文 / 英文 / 混合 — 保留原文）。

            音频为单声道，双方电平已均衡。文件中无声道提示，
            请仅根据声学特征区分说话人（音高 / 音色 / 语速 / 说话习惯）。

            分别识别每一位说话人：
            • 如果是电话通话（典型的电话线路音质、两人对话）：
              恰好两位说话人。id "ME" — 声音更近更清晰的一方（麦克风侧），
              label "我"；id "THEM" — 另一方声音，label "对方"
              （如对话中提到名字则用名字）。
            • 如果是普通多人大录音：为每个不同的声音在 speakers 中
              单独创建条目。id — 简短标签 "A"、"B"、"C"…
              label — 如听到名字则用名字（"小明"、"李总"），
              否则用"发言人 1"、"发言人 2"…
            • 音调、音高、语速和说话习惯是区分的主要依据。
              不要将同一人因情绪或音量变化而误判为不同人。
              女声 vs 男声 — 几乎总是不同的 id。
            • 不要因为某一方声音略大就将整段对话合并到一个人身上。
              如果你听到两种明显不同的声音特征 — 那就是两个人。

            "title" 字段 — 录音内容的简短摘要（不超过 60 个字符），
            用中文，不加引号和表情符号。像笔记标题一样。示例：
            "和小明讨论周末计划"、"项目周会，讨论上线时间"、
            "算法课期末复习重点"。如果录音中没有语音 — "无语音内容"。

            仅返回 JSON 对象，格式如下（不要用 markdown 代码块包裹）：
            {
              "title": "简短摘要",
              "language": "zh",
              "duration_sec": 142.5,
              "speakers": [
                {"id": "A", "label": "我"},
                {"id": "B", "label": "小明"}
              ],
              "segments": [
                {
                  "start": 0.0,
                  "end": 3.2,
                  "speaker_id": "A",
                  "text": "你好，最近怎么样？",
                  "tone": "friendly",
                  "non_speech": ["laugh"]
                }
              ]
            }

            规则：
            • title — 必填，不能为空。
            • speakers 和 segments 必须对应：每个 speaker_id 必须在 speakers 中存在。
            • 按话轮拆分。同一说话人同一话题的连续短句可以合并。
            • non_speech：仅记录明显的声音（laugh, sigh, cough, pause,
              background_music, background_voice）。
            • tone：friendly|tense|neutral|excited|sad|angry|questioning 或 null。
            • 不要编造文字 — 如果听不清，标记为"[听不清]"。
            • 不要在 JSON 之外添加任何注释或标题 — 只输出 JSON 对象本身。
        """.trimIndent()
    }

    private fun parseTranscript(body: String): String {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: error("响应中缺少 choices")
        if (choices.length() == 0) error("响应中 choices 为空")
        val msg = choices.getJSONObject(0).optJSONObject("message") ?: error("choices[0] 中缺少 message")
        // Some providers return content as string, others as list of parts.
        return when (val c = msg.opt("content")) {
            is String -> c.trim()
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until c.length()) {
                    val part = c.getJSONObject(i)
                    if (part.optString("type") == "text") sb.append(part.optString("text"))
                }
                sb.toString().trim()
            }
            else -> error("未知的 content 格式")
        }
    }
}
