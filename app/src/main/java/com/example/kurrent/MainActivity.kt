package com.example.kurrent

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.kurrent.adapter.ArticleAdapter
import com.example.kurrent.adapter.QuickCardAdapter
import com.example.kurrent.adapter.QuizAdapter
import com.example.kurrent.adapter.RecallAdapter
import com.example.kurrent.audio.AudioNewsManager
import com.example.kurrent.data.SampleDataRepository
import com.example.kurrent.databinding.ActivityMainBinding
import com.example.kurrent.ui.CardStackPageTransformer
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var quickCardAdapter: QuickCardAdapter
    private lateinit var quizAdapter: QuizAdapter
    private lateinit var recallAdapter: RecallAdapter

    // Audio News Engine for Hands-Free Digest
    private lateinit var audioNewsManager: AudioNewsManager

    private var currentExamFilter: String = "ALL"
    private var currentSearchQuery: String = ""
    private var currentTabId: Int = R.id.nav_digest

    // Track cards read by the user today for the circular progress ring
    private val readCardIndices = mutableSetOf<Int>()

    // Selected date tracking and nil state
    private var selectedDate: String? = null
    private var isDateNil: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure any unexpected runtime errors are cleanly logged
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("KurrentCrash", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
        }

        // Initialize local disk cache and authentication manager
        SampleDataRepository.init(this)
        com.example.kurrent.data.AuthManager.init(this)

        // Mandatory Auth Check: Block access unless signed in
        if (!com.example.kurrent.data.AuthManager.isLoggedIn()) {
            val intent = android.content.Intent(this, com.example.kurrent.ui.AuthActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupAudioNewsManager()
        setupSwipeRefresh()
        setupSearch()
        setupAccountActions()
        setupHeader()
        setupAdapters()
        setupExamFilters()
        setupBottomNavigation()
        setupCalendarActions()
        loadData()

        // Sync with live EC2 backend asynchronously in background
        lifecycleScope.launch {
            try {
                SampleDataRepository.syncWithBackend { liveBriefing ->
                    if (!isDateNil && selectedDate == null) {
                        setupHeader()
                        loadData()
                    }
                }
                syncCloudProgress()
            } catch (e: Exception) {
                android.util.Log.e("KurrentMain", "Error updating UI after sync: ${e.message}", e)
            }
        }
    }

    /**
     * Prevents the top bar from going under the notification panel/status bar,
     * ensures the bottom bar accounts for system navigation bars, and adjusts the
     * floating mini audio player to remain cleanly positioned above the bottom nav.
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, windowInsets ->
            val statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInset.top)
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, windowInsets ->
            val navBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInset.bottom)

            // Adjust floating mini audio player to remain above bottomNav
            val miniParams = binding.layoutMiniAudioPlayer.layoutParams as? ViewGroup.MarginLayoutParams
            miniParams?.bottomMargin = (80 * resources.displayMetrics.density).toInt() + navBarInset.bottom
            binding.layoutMiniAudioPlayer.layoutParams = miniParams

            windowInsets
        }
    }

    private fun setupAccountActions() {
        updateProfileHeader()

        binding.btnProfile.setOnClickListener {
            showAccountSheet()
        }

        binding.layoutStreakBadge.setOnClickListener {
            showAccountSheet()
        }
    }

    private fun showAccountSheet() {
        val sheet = com.example.kurrent.ui.AccountBottomSheetDialog { isLoggedIn ->
            updateProfileHeader()
            if (isLoggedIn) {
                syncCloudProgress()
            }
        }
        sheet.show(supportFragmentManager, "ACCOUNT_SHEET")
    }

    private fun updateProfileHeader() {
        if (com.example.kurrent.data.AuthManager.isLoggedIn()) {
            val user = com.example.kurrent.data.AuthManager.getUserProfile()
            val firstName = user?.displayName?.split(" ")?.firstOrNull() ?: "Profile"
            binding.tvProfileStatus.text = firstName
            val stats = com.example.kurrent.data.AuthManager.getUserStats()
            if (stats != null) {
                binding.tvStreak.text = "${stats.currentStreak} Days"
            }
        } else {
            binding.tvProfileStatus.text = "Account"
        }
    }

    private fun syncCloudProgress() {
        if (com.example.kurrent.data.AuthManager.isLoggedIn()) {
            lifecycleScope.launch {
                val articles = SampleDataRepository.getDailyBriefing().articles
                val readIds = readCardIndices.mapNotNull { idx -> articles.getOrNull(idx)?.id }
                val stats = SampleDataRepository.syncUserCloudProgress(readIds)
                if (stats != null) {
                    binding.tvStreak.text = "${stats.currentStreak} Days"
                    articleAdapter.notifyDataSetChanged()
                    quickCardAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupHeader() {
        val briefing = SampleDataRepository.getDailyBriefing()
        val dateLabel = if (selectedDate == null || selectedDate == briefing.date) {
            "TODAY, ${briefing.date} • #${briefing.edition}"
        } else {
            "${briefing.date} • #${briefing.edition}"
        }
        binding.tvHeaderDate.text = dateLabel
        val stats = com.example.kurrent.data.AuthManager.getUserStats()
        if (stats != null) {
            binding.tvStreak.text = "${stats.currentStreak} Days"
        } else {
            binding.tvStreak.text = "7 Days"
        }
        updateProfileHeader()
        updateProgressRing(readCardIndices.size, briefing.articles.size)
    }

    private fun setupCalendarActions() {
        // Tap on interactive date chip opens the Material DatePicker
        binding.btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        // Return to today from nil empty state
        binding.btnNilReturnToday.setOnClickListener {
            restoreTodayBriefing()
        }

        // Pick another date from nil empty state
        binding.btnNilPickDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Briefing Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selectionMillis ->
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selectionMillis
            }
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val dateStr = sdf.format(calendar.time)
            loadBriefingForDate(dateStr)
        }

        datePicker.show(supportFragmentManager, "BRIEFING_DATE_PICKER")
    }

    private fun loadBriefingForDate(dateStr: String) {
        binding.layoutDateLoading.visibility = View.VISIBLE
        binding.tvLoadingMessage.text = "Loading briefing for $dateStr..."
        selectedDate = dateStr

        lifecycleScope.launch {
            try {
                val briefing = SampleDataRepository.fetchBriefingForDate(dateStr)
                binding.layoutDateLoading.visibility = View.GONE
                if (briefing != null && briefing.articles.isNotEmpty()) {
                    isDateNil = false
                    binding.layoutDateNil.visibility = View.GONE
                    readCardIndices.clear()
                    setupHeader()
                    loadData()
                    showCurrentTab()
                } else {
                    // Nil state: No briefing on this date
                    isDateNil = true
                    showDateNilState(dateStr)
                }
            } catch (e: Exception) {
                binding.layoutDateLoading.visibility = View.GONE
                isDateNil = true
                showDateNilState(dateStr)
            }
        }
    }

    private fun showDateNilState(dateStr: String) {
        binding.tvHeaderDate.text = "$dateStr • Nil"
        binding.tvNilTitle.text = "No Briefing on $dateStr"
        binding.tvNilMessage.text = "There is no current affairs content published for this date (nil)."
        binding.layoutDateNil.visibility = View.VISIBLE
        binding.layoutSearchAndAudio.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE
        binding.layoutSearchEmpty.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        updateProgressRing(0, 0)
    }

    private fun restoreTodayBriefing() {
        isDateNil = false
        selectedDate = null
        binding.layoutDateNil.visibility = View.GONE
        binding.layoutSearchAndAudio.visibility = View.VISIBLE
        SampleDataRepository.restoreTodayBriefing()
        readCardIndices.clear()
        setupHeader()
        loadData()
        showCurrentTab()
    }

    private fun setupAudioNewsManager() {
        audioNewsManager = AudioNewsManager(this)

        // Track updates when moving through news items
        audioNewsManager.onTrackChangedListener = { article, currentTrack, totalTracks ->
            runOnUiThread {
                binding.tvPlayerTitle.text = article.title
                binding.tvPlayerSubtitle.text = "Track $currentTrack of $totalTracks • ${article.category}"
                articleAdapter.setCurrentlySpeaking(article.id)
            }
        }

        // Playback state updates
        audioNewsManager.onPlaybackStateChanged = { isPlaying ->
            runOnUiThread {
                if (isPlaying) {
                    binding.layoutMiniAudioPlayer.visibility = View.VISIBLE
                    binding.btnPlayerPlayPause.setImageResource(R.drawable.ic_pause)
                    binding.tvAudioDigestText.text = "Playing"
                    binding.ivAudioDigestIcon.setImageResource(R.drawable.ic_pause)
                    binding.waveformPlayer.setPlaying(true)
                } else {
                    binding.layoutMiniAudioPlayer.visibility = View.GONE
                    binding.tvAudioDigestText.text = "Listen"
                    binding.ivAudioDigestIcon.setImageResource(R.drawable.ic_headphones)
                    binding.waveformPlayer.setPlaying(false)
                    articleAdapter.setCurrentlySpeaking(null)
                }
            }
        }

        // Top bar "Listen" button -> Read full daily digest
        binding.btnAudioDigest.setOnClickListener {
            if (audioNewsManager.isSpeaking()) {
                audioNewsManager.stop()
            } else {
                val articlesToPlay = SampleDataRepository.searchArticles(currentSearchQuery, currentExamFilter)
                if (articlesToPlay.isNotEmpty()) {
                    audioNewsManager.playAll(articlesToPlay)
                } else {
                    Toast.makeText(this, "No articles to play", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Mini player controls
        binding.btnPlayerPlayPause.setOnClickListener {
            if (audioNewsManager.isSpeaking()) {
                audioNewsManager.stop()
            } else {
                val articlesToPlay = SampleDataRepository.searchArticles(currentSearchQuery, currentExamFilter)
                if (articlesToPlay.isNotEmpty()) {
                    audioNewsManager.playAll(articlesToPlay)
                }
            }
        }

        binding.btnPlayerClose.setOnClickListener {
            audioNewsManager.stop()
            binding.layoutMiniAudioPlayer.visibility = View.GONE
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.indigo_primary, R.color.teal_accent)
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                try {
                    var isUpdated = false
                    SampleDataRepository.syncWithBackend {
                        isUpdated = true
                    }
                    setupHeader()
                    loadData()
                    if (isUpdated) {
                        Toast.makeText(this@MainActivity, "Daily briefing refreshed!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Briefing is up to date (offline cache)", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Offline - Showing cached briefing", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { text ->
            currentSearchQuery = text?.toString()?.trim() ?: ""
            binding.btnClearSearch.visibility = if (currentSearchQuery.isNotBlank()) View.VISIBLE else View.GONE
            applyCurrentFilter()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            currentSearchQuery = ""
            binding.btnClearSearch.visibility = View.GONE
            applyCurrentFilter()
        }
    }

    private fun setupAdapters() {
        // 1. Digest Feed Adapter with Bookmark & Audio Listen Handlers
        articleAdapter = ArticleAdapter(
            articles = emptyList(),
            onBookmarkClicked = { article ->
                SampleDataRepository.toggleBookmark(article.id)
                if (currentTabId == R.id.nav_saved) {
                    refreshSavedTab()
                }
                syncCloudProgress()
            },
            onListenClicked = { article ->
                audioNewsManager.togglePlayArticle(article)
            }
        )
        binding.rvArticles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = articleAdapter
        }

        // 2. Quick Cards Adapter with Physics-Based Stack Transformer
        quickCardAdapter = QuickCardAdapter(emptyList()) { article ->
            SampleDataRepository.toggleBookmark(article.id)
            articleAdapter.notifyDataSetChanged()
            syncCloudProgress()
        }
        binding.vpQuickCards.apply {
            adapter = quickCardAdapter
            setPageTransformer(CardStackPageTransformer())
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    readCardIndices.add(position)
                    val total = quickCardAdapter.itemCount
                    updateProgressRing(readCardIndices.size, total)
                    syncCloudProgress()
                }
            })
        }

        // 3. Spaced Repetition Recalls Adapter
        recallAdapter = RecallAdapter(emptyList())
        binding.rvRecalls.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = recallAdapter
        }

        // 4. Quiz Adapter
        quizAdapter = QuizAdapter(emptyList())
        binding.rvQuiz.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = quizAdapter
        }
    }

    private fun updateProgressRing(readCount: Int, totalCount: Int) {
        if (totalCount == 0) {
            binding.progressDailyRing.setProgressCompat(0, false)
            binding.tvDailyProgress.text = "0/0 Read"
            return
        }
        val percent = ((readCount.toFloat() / totalCount.toFloat()) * 100).toInt().coerceIn(0, 100)
        binding.progressDailyRing.setProgressCompat(percent, true)
        binding.tvDailyProgress.text = "$readCount/$totalCount Read"

        if (percent == 100 && readCount > 0) {
            triggerStreakIgnitionCelebration()
        }
    }

    /**
     * Streak Ignition Celebration:
     * 1. Haptic confirmation pulse.
     * 2. 60ms anticipation shrink (0.85x).
     * 3. Explosive 1.38x flame expansion with an ambient amber dissipating glow.
     * 4. Decelerating settle back to 1.0x.
     */
    private fun triggerStreakIgnitionCelebration() {
        // 1. Haptic pulse
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            binding.layoutStreakBadge.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        } else {
            binding.layoutStreakBadge.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }

        // 2. 60ms anticipation shrink (0.85x)
        binding.ivStreakFlame.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(60)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                // 3. Explosive 1.38x flame expansion with Overshoot
                binding.ivStreakFlame.animate()
                    .scaleX(1.38f)
                    .scaleY(1.38f)
                    .setDuration(140)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.4f))
                    .withEndAction {
                        // Settle back to 1.0x
                        binding.ivStreakFlame.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(110)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                    .start()

                // Ambient amber dissipating glow ring expansion
                binding.viewStreakGlow.alpha = 0.85f
                binding.viewStreakGlow.scaleX = 1.0f
                binding.viewStreakGlow.scaleY = 1.0f
                binding.viewStreakGlow.animate()
                    .scaleX(2.5f)
                    .scaleY(2.5f)
                    .alpha(0f)
                    .setDuration(320)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun setupExamFilters() {
        binding.chipGroupExams.setOnCheckedStateChangeListener { _, checkedIds ->
            currentExamFilter = when {
                checkedIds.contains(R.id.chipUpsc) -> "UPSC"
                checkedIds.contains(R.id.chipSsc) -> "SSC"
                checkedIds.contains(R.id.chipIbps) -> "IBPS"
                else -> "ALL"
            }
            applyCurrentFilter()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            currentTabId = item.itemId
            showCurrentTab()
            true
        }
    }

    private fun showCurrentTab() {
        if (isDateNil) {
            binding.layoutDateNil.visibility = View.VISIBLE
            binding.layoutSearchAndAudio.visibility = View.GONE
            binding.filterScrollView.visibility = View.GONE
            binding.swipeRefresh.visibility = View.GONE
            binding.layoutSearchEmpty.visibility = View.GONE
            binding.vpQuickCards.visibility = View.GONE
            binding.rvRecalls.visibility = View.GONE
            binding.rvQuiz.visibility = View.GONE
            binding.layoutSavedEmpty.visibility = View.GONE
            return
        }

        binding.layoutDateNil.visibility = View.GONE
        binding.swipeRefresh.isEnabled = (currentTabId == R.id.nav_digest)
        when (currentTabId) {
            R.id.nav_digest -> showDigestTab()
            R.id.nav_cards -> showCardsTab()
            R.id.nav_recalls -> showRecallsTab()
            R.id.nav_quiz -> showQuizTab()
            R.id.nav_saved -> showSavedTab()
        }
    }

    private fun loadData() {
        applyCurrentFilter()
        val briefing = SampleDataRepository.getDailyBriefing()
        quizAdapter.updateQuizzes(briefing.quizzes)
        recallAdapter.updateRecalls(SampleDataRepository.getRecalls())
    }

    private fun applyCurrentFilter() {
        val filtered = SampleDataRepository.searchArticles(currentSearchQuery, currentExamFilter)
        articleAdapter.updateArticles(filtered)
        quickCardAdapter.updateArticles(filtered)
        val total = filtered.size
        updateProgressRing(readCardIndices.size, total)

        // Empty search state feedback
        if (filtered.isEmpty() && currentSearchQuery.isNotBlank() && currentTabId == R.id.nav_digest) {
            binding.layoutSearchEmpty.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
            binding.tvSearchEmptyMessage.text = "No articles matching \"$currentSearchQuery\". Try searching by entity or topic."
        } else {
            binding.layoutSearchEmpty.visibility = View.GONE
            if (currentTabId == R.id.nav_digest && !isDateNil) {
                binding.swipeRefresh.visibility = View.VISIBLE
            }
        }
    }

    private fun showDigestTab() {
        binding.layoutSearchAndAudio.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.VISIBLE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        applyCurrentFilter()
    }

    private fun showCardsTab() {
        binding.layoutSearchAndAudio.visibility = View.VISIBLE
        binding.filterScrollView.visibility = View.VISIBLE
        binding.swipeRefresh.visibility = View.GONE
        binding.layoutSearchEmpty.visibility = View.GONE
        binding.vpQuickCards.visibility = View.VISIBLE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        applyCurrentFilter()
    }

    private fun showRecallsTab() {
        binding.layoutSearchAndAudio.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE
        binding.layoutSearchEmpty.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.VISIBLE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        recallAdapter.updateRecalls(SampleDataRepository.getRecalls())
    }

    private fun showQuizTab() {
        binding.layoutSearchAndAudio.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        binding.swipeRefresh.visibility = View.GONE
        binding.layoutSearchEmpty.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.VISIBLE
        binding.layoutSavedEmpty.visibility = View.GONE
    }

    private fun showSavedTab() {
        binding.layoutSearchAndAudio.visibility = View.GONE
        binding.filterScrollView.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSearchEmpty.visibility = View.GONE
        refreshSavedTab()
    }

    private fun refreshSavedTab() {
        val allArticles = SampleDataRepository.getDailyBriefing().articles
        val bookmarked = allArticles.filter { it.isBookmarked }
        if (bookmarked.isEmpty()) {
            binding.swipeRefresh.visibility = View.GONE
            binding.layoutSavedEmpty.visibility = View.VISIBLE
        } else {
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.layoutSavedEmpty.visibility = View.GONE
            articleAdapter.updateArticles(bookmarked)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!com.example.kurrent.data.AuthManager.isLoggedIn()) {
            val intent = android.content.Intent(this, com.example.kurrent.ui.AuthActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        updateProfileHeader()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::audioNewsManager.isInitialized) {
            audioNewsManager.release()
        }
    }
}
