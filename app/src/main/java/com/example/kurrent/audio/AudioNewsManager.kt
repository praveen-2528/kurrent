package com.example.kurrent.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.kurrent.model.Article
import java.util.Locale

class AudioNewsManager private constructor(private val appContext: Context) {

    private val TAG = "AudioNewsManager"

    private var tts: TextToSpeech? = null
    var isInitialized = false
        private set

    private var playlist: MutableList<Article> = mutableListOf()
    var currentIndex: Int = -1
        private set
    var isQueueMode: Boolean = false
        private set

    var currentPlayingArticleId: String? = null
        private set

    var isPlayingState: Boolean = false
        private set

    var currentSpeed: Float = 1.0f
        private set

    private val availableSpeeds = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 0

    var onTrackChangedListener: ((Article, Int, Int) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onSpeedChanged: ((Float) -> Unit)? = null

    private var pendingPlayback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("en", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(currentSpeed)
                tts?.setPitch(1.0f)
                isInitialized = true
                setupUtteranceListener()
                pendingPlayback?.invoke()
                pendingPlayback = null
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech engine")
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != "UTTERANCE_$currentPlayingArticleId") return
                isPlayingState = true
                onPlaybackStateChanged?.invoke(true)
                KurrentAudioService.updateNotification(appContext)
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != "UTTERANCE_$currentPlayingArticleId") return
                if (isQueueMode && currentIndex + 1 < playlist.size) {
                    currentIndex++
                    val nextArticle = playlist[currentIndex]
                    speakArticleInternal(nextArticle)
                } else {
                    isPlayingState = false
                    currentPlayingArticleId = null
                    isQueueMode = false
                    onPlaybackStateChanged?.invoke(false)
                    KurrentAudioService.stopService(appContext)
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != "UTTERANCE_$currentPlayingArticleId") return
                isPlayingState = false
                currentPlayingArticleId = null
                isQueueMode = false
                onPlaybackStateChanged?.invoke(false)
                KurrentAudioService.stopService(appContext)
            }
        })
    }

    /**
     * Formats journalistic news script for high-yield listening:
     * 1. Category & Title
     * 2. Core Headline Fact (background context)
     * 3. 5-Second Revision Line Takeaway
     */
    fun formatNewsScript(article: Article): String {
        val category = article.category.ifBlank { "National News" }
        val title = article.title
        val headline = if (article.headlineFact.isNotBlank()) article.headlineFact else article.background
        val revision = article.revisionLine

        val cleanHeadline = headline.replace(Regex("[#*`_-]"), " ").replace(Regex("\\s+"), " ").trim()
        val cleanRevision = revision.replace(Regex("[#*`_-]"), " ").replace(Regex("\\s+"), " ").trim()

        val sb = StringBuilder()
        sb.append("In $category. ")
        sb.append("$title. ")
        if (cleanHeadline.isNotBlank()) {
            sb.append("$cleanHeadline. ")
        }
        if (cleanRevision.isNotBlank()) {
            sb.append("Key Takeaway: $cleanRevision.")
        }
        return sb.toString()
    }

    /**
     * Cycles through broadcast speeds: 1.0x -> 1.25x -> 1.5x -> 2.0x -> 1.0x
     */
    fun cycleSpeed(): Float {
        speedIndex = (speedIndex + 1) % availableSpeeds.size
        currentSpeed = availableSpeeds[speedIndex]
        tts?.setSpeechRate(currentSpeed)
        onSpeedChanged?.invoke(currentSpeed)
        return currentSpeed
    }

    /**
     * Sequentially plays through all articles of the daily briefing.
     */
    fun playAll(articles: List<Article>, startIndex: Int = 0) {
        if (articles.isEmpty()) return
        Log.i(TAG, "playAll called with ${articles.size} articles, start=$startIndex")

        tts?.stop()
        playlist.clear()
        playlist.addAll(articles)
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        isQueueMode = true

        KurrentAudioService.startService(appContext)
        val target = playlist[currentIndex]
        speakArticleInternal(target)
    }

    /**
     * Toggles playback for a single article.
     */
    fun togglePlayArticle(article: Article) {
        if (currentPlayingArticleId == article.id && isSpeaking()) {
            stop()
            return
        }

        isQueueMode = false
        currentIndex = -1
        currentPlayingArticleId = article.id
        KurrentAudioService.startService(appContext)
        speakArticleInternal(article)
    }

    /**
     * Pauses or stops current utterance.
     */
    fun pauseOrResume() {
        Log.i(TAG, "pauseOrResume called. Currently isSpeaking=$isPlayingState")
        if (isSpeaking()) {
            isPlayingState = false
            tts?.stop()
            onPlaybackStateChanged?.invoke(false)
            KurrentAudioService.updateNotification(appContext)
        } else if (playlist.isNotEmpty() && currentIndex in playlist.indices) {
            val article = playlist[currentIndex]
            speakArticleInternal(article)
        }
    }

    fun skipNext() {
        Log.i(TAG, "skipNext called. currentIndex=$currentIndex, size=${playlist.size}")
        if (isQueueMode && currentIndex + 1 < playlist.size) {
            currentIndex++
            speakArticleInternal(playlist[currentIndex])
        } else {
            stop()
        }
    }

    fun skipPrevious() {
        Log.i(TAG, "skipPrevious called. currentIndex=$currentIndex")
        if (isQueueMode && currentIndex - 1 >= 0) {
            currentIndex--
            speakArticleInternal(playlist[currentIndex])
        } else if (playlist.isNotEmpty() && currentIndex in playlist.indices) {
            speakArticleInternal(playlist[currentIndex])
        }
    }

    private fun speakArticleInternal(article: Article) {
        Log.i(TAG, "speakArticleInternal: [${article.id}] isInitialized=$isInitialized")
        if (!isInitialized) {
            pendingPlayback = { speakArticleInternal(article) }
            return
        }

        currentPlayingArticleId = article.id
        val script = formatNewsScript(article)

        if (isQueueMode) {
            onTrackChangedListener?.invoke(article, currentIndex + 1, playlist.size)
        } else {
            onTrackChangedListener?.invoke(article, 1, 1)
        }

        isPlayingState = true
        onPlaybackStateChanged?.invoke(true)
        KurrentAudioService.updateNotification(appContext)
        val res = tts?.speak(script, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_${article.id}")
        Log.i(TAG, "speak() returned status: $res")
    }

    fun getCurrentArticle(): Article? {
        if (currentIndex in playlist.indices) {
            return playlist[currentIndex]
        }
        val id = currentPlayingArticleId ?: return null
        return playlist.find { it.id == id }
    }

    fun getPlaylistSize(): Int = playlist.size

    fun isSpeaking(): Boolean = isPlayingState

    fun stop() {
        isPlayingState = false
        tts?.stop()
        currentPlayingArticleId = null
        isQueueMode = false
        onPlaybackStateChanged?.invoke(false)
        KurrentAudioService.stopService(appContext)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        instance = null
    }

    companion object {
        @Volatile
        private var instance: AudioNewsManager? = null

        fun getInstance(context: Context): AudioNewsManager {
            return instance ?: synchronized(this) {
                instance ?: AudioNewsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
