package com.example.kurrent.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.kurrent.model.Article
import java.util.Locale

class AudioNewsManager(context: Context, private val onInitListener: ((Boolean) -> Unit)? = null) {

    private val TAG = "AudioNewsManager"

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private var playlist: List<Article> = emptyList()
    private var currentIndex = -1
    private var isQueueMode = false

    var currentPlayingArticleId: String? = null
        private set

    var onTrackChangedListener: ((Article, Int, Int) -> Unit)? = null
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // Default to Indian English or General English
                val result = tts?.setLanguage(Locale("en", "IN"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(0.96f) // Clear, broadcast pace
                tts?.setPitch(1.0f)
                isInitialized = true
                setupUtteranceListener()
                onInitListener?.invoke(true)
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech engine")
                onInitListener?.invoke(false)
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onPlaybackStateChanged?.invoke(true)
            }

            override fun onDone(utteranceId: String?) {
                if (isQueueMode && currentIndex + 1 < playlist.size) {
                    currentIndex++
                    val nextArticle = playlist[currentIndex]
                    speakArticleInternal(nextArticle)
                } else {
                    currentPlayingArticleId = null
                    isQueueMode = false
                    onPlaybackStateChanged?.invoke(false)
                }
            }

            override fun onError(utteranceId: String?) {
                currentPlayingArticleId = null
                isQueueMode = false
                onPlaybackStateChanged?.invoke(false)
            }
        })
    }

    /**
     * Formats ONLY the journalistic news portions of the article:
     * 1. Category & Title
     * 2. Core Headline Fact (background context)
     * 3. 5-Second Revision Line Takeaway
     * Omits distractor traps, raw tables, URLs, and syllabus codes.
     */
    fun formatNewsScript(article: Article): String {
        val category = article.category.ifBlank { "National News" }
        val title = article.title
        val headline = if (article.headlineFact.isNotBlank()) article.headlineFact else article.background
        val revision = article.revisionLine

        // Clean any markdown formatting (asterisks, bullet hashes, dashes)
        val cleanHeadline = headline.replace(Regex("[#*`_-]"), " ").replace(Regex("\\s+"), " ").trim()
        val cleanRevision = revision.replace(Regex("[#*`_-]"), " ").replace(Regex("\\s+"), " ").trim()

        val sb = StringBuilder()
        sb.append("In $category. ")
        sb.append("$title. ")
        if (cleanHeadline.isNotBlank()) {
            sb.append("$cleanHeadline. ")
        }
        if (cleanRevision.isNotBlank()) {
            sb.append("Quick Takeaway: $cleanRevision.")
        }
        return sb.toString()
    }

    /**
     * Plays a single article's news speech.
     * If this article is already playing, stops it.
     */
    fun togglePlayArticle(article: Article) {
        if (!isInitialized) return

        if (currentPlayingArticleId == article.id && isSpeaking()) {
            stop()
            return
        }

        isQueueMode = false
        currentIndex = -1
        currentPlayingArticleId = article.id
        speakArticleInternal(article)
    }

    /**
     * Sequentially plays through all articles of the daily briefing.
     */
    fun playAll(articles: List<Article>) {
        if (!isInitialized || articles.isEmpty()) return

        stop()
        playlist = articles
        currentIndex = 0
        isQueueMode = true

        val first = playlist[0]
        speakArticleInternal(first)
    }

    private fun speakArticleInternal(article: Article) {
        currentPlayingArticleId = article.id
        val script = formatNewsScript(article)

        if (isQueueMode) {
            onTrackChangedListener?.invoke(article, currentIndex + 1, playlist.size)
        } else {
            onTrackChangedListener?.invoke(article, 1, 1)
        }

        tts?.speak(script, TextToSpeech.QUEUE_FLUSH, null, "UTTERANCE_${article.id}")
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun stop() {
        tts?.stop()
        currentPlayingArticleId = null
        isQueueMode = false
        onPlaybackStateChanged?.invoke(false)
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
