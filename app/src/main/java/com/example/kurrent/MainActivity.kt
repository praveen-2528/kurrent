package com.example.kurrent

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.example.kurrent.adapter.ArticleAdapter
import com.example.kurrent.adapter.QuickCardAdapter
import com.example.kurrent.adapter.QuizAdapter
import com.example.kurrent.adapter.RecallAdapter
import com.example.kurrent.data.SampleDataRepository
import com.example.kurrent.databinding.ActivityMainBinding
import com.example.kurrent.ui.CardStackPageTransformer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure any unexpected runtime errors are cleanly logged
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("KurrentCrash", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupAdapters()
        setupExamFilters()
        setupBottomNavigation()
        loadData()

        // Sync with live EC2 backend asynchronously in background
        lifecycleScope.launch {
            try {
                SampleDataRepository.syncWithBackend { liveBriefing ->
                    setupHeader()
                    loadData()
                }
            } catch (e: Exception) {
                android.util.Log.e("KurrentMain", "Error updating UI after sync: ${e.message}", e)
            }
        }
    }

    private fun setupHeader() {
        val briefing = SampleDataRepository.getDailyBriefing()
        binding.tvHeaderDate.text = "TODAY, ${briefing.date} • EDITION #${briefing.edition}"
        binding.tvStreak.text = "7 Days"
        updateProgressRing(0, briefing.articles.size)
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
            // Attach tactile spring-physics card stack transformer
            setPageTransformer(CardStackPageTransformer())
            // Listen to page swipes to update the daily reading progress ring
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
        if (totalCount == 0) return
        val percent = ((readCount.toFloat() / totalCount.toFloat()) * 100).toInt().coerceIn(0, 100)
        binding.progressDailyRing.setProgressCompat(percent, true)
        binding.tvDailyProgress.text = "$readCount/$totalCount Read"

        // When 100% read is reached, trigger subtle micro-celebration on the streak flame without interrupting modal
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
        // Fast, near-instant switches (<100ms) with zero heavy transition lag
        binding.bottomNav.setOnItemSelectedListener { item ->
            currentTabId = item.itemId
            when (item.itemId) {
                R.id.nav_digest -> {
                    showDigestTab()
                    true
                }
                R.id.nav_cards -> {
                    showCardsTab()
                    true
                }
                R.id.nav_recalls -> {
                    showRecallsTab()
                    true
                }
                R.id.nav_quiz -> {
                    showQuizTab()
                    true
                }
                R.id.nav_saved -> {
                    showSavedTab()
                    true
                }
                else -> false
            }
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
