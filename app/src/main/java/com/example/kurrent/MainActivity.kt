package com.example.kurrent

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.kurrent.adapter.ArticleAdapter
import com.example.kurrent.adapter.QuickCardAdapter
import com.example.kurrent.adapter.QuizAdapter
import com.example.kurrent.adapter.RecallAdapter
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

    private var currentExamFilter: String = "ALL"
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
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
            } catch (e: Exception) {
                android.util.Log.e("KurrentMain", "Error updating UI after sync: ${e.message}", e)
            }
        }
    }

    /**
     * Prevents the top bar from going under the notification panel/status bar
     * and ensures the bottom bar accounts for system navigation bars.
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
            windowInsets
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
        binding.tvStreak.text = "7 Days"
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
        binding.filterScrollView.visibility = View.GONE
        binding.rvArticles.visibility = View.GONE
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
        SampleDataRepository.restoreTodayBriefing()
        readCardIndices.clear()
        setupHeader()
        loadData()
        showCurrentTab()
    }

    private fun setupAdapters() {
        // 1. Digest Feed Adapter
        articleAdapter = ArticleAdapter(emptyList()) { article ->
            SampleDataRepository.toggleBookmark(article.id)
            if (currentTabId == R.id.nav_saved) {
                refreshSavedTab()
            }
        }
        binding.rvArticles.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = articleAdapter
        }

        // 2. Quick Cards Adapter with Physics-Based Stack Transformer
        quickCardAdapter = QuickCardAdapter(emptyList()) { article ->
            SampleDataRepository.toggleBookmark(article.id)
            articleAdapter.notifyDataSetChanged()
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

        if (percent == 100) {
            binding.ivStreakFlame.animate()
                .scaleX(1.35f)
                .scaleY(1.35f)
                .setDuration(220)
                .withEndAction {
                    binding.ivStreakFlame.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                }
                .start()
        }
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
            binding.filterScrollView.visibility = View.GONE
            binding.rvArticles.visibility = View.GONE
            binding.vpQuickCards.visibility = View.GONE
            binding.rvRecalls.visibility = View.GONE
            binding.rvQuiz.visibility = View.GONE
            binding.layoutSavedEmpty.visibility = View.GONE
            return
        }

        binding.layoutDateNil.visibility = View.GONE
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
        val filtered = SampleDataRepository.getArticlesByFilter(currentExamFilter)
        articleAdapter.updateArticles(filtered)
        quickCardAdapter.updateArticles(filtered)
        val total = filtered.size
        updateProgressRing(readCardIndices.size, total)
    }

    private fun showDigestTab() {
        binding.filterScrollView.visibility = View.VISIBLE
        binding.rvArticles.visibility = View.VISIBLE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        applyCurrentFilter()
    }

    private fun showCardsTab() {
        binding.filterScrollView.visibility = View.VISIBLE
        binding.rvArticles.visibility = View.GONE
        binding.vpQuickCards.visibility = View.VISIBLE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        applyCurrentFilter()
    }

    private fun showRecallsTab() {
        binding.filterScrollView.visibility = View.GONE
        binding.rvArticles.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.VISIBLE
        binding.rvQuiz.visibility = View.GONE
        binding.layoutSavedEmpty.visibility = View.GONE
        recallAdapter.updateRecalls(SampleDataRepository.getRecalls())
    }

    private fun showQuizTab() {
        binding.filterScrollView.visibility = View.GONE
        binding.rvArticles.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.VISIBLE
        binding.layoutSavedEmpty.visibility = View.GONE
    }

    private fun showSavedTab() {
        binding.filterScrollView.visibility = View.GONE
        binding.vpQuickCards.visibility = View.GONE
        binding.rvRecalls.visibility = View.GONE
        binding.rvQuiz.visibility = View.GONE
        refreshSavedTab()
    }

    private fun refreshSavedTab() {
        val allArticles = SampleDataRepository.getDailyBriefing().articles
        val bookmarked = allArticles.filter { it.isBookmarked }
        if (bookmarked.isEmpty()) {
            binding.rvArticles.visibility = View.GONE
            binding.layoutSavedEmpty.visibility = View.VISIBLE
        } else {
            binding.rvArticles.visibility = View.VISIBLE
            binding.layoutSavedEmpty.visibility = View.GONE
            articleAdapter.updateArticles(bookmarked)
        }
    }
}
