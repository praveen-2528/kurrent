package com.example.kurrent.adapter

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kurrent.R
import com.example.kurrent.data.SampleDataRepository
import com.example.kurrent.databinding.ItemQuickCardBinding
import com.example.kurrent.model.Article
import com.example.kurrent.model.QuizQuestion
import com.example.kurrent.ui.UIInteractions
import com.google.android.material.button.MaterialButton

class QuickCardAdapter(
    private var articles: List<Article>,
    private val onBookmarkClicked: (Article) -> Unit
) : RecyclerView.Adapter<QuickCardAdapter.QuickCardViewHolder>() {

    fun updateArticles(newArticles: List<Article>) {
        this.articles = newArticles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickCardViewHolder {
        val binding = ItemQuickCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickCardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickCardViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    inner class QuickCardViewHolder(private val binding: ItemQuickCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var isStaticExpanded = false
        private var isCardFlipped = false

        fun bind(article: Article) {
            val context = binding.root.context

            // Reset initial card state
            isCardFlipped = false
            binding.cardFront.visibility = View.VISIBLE
            binding.cardFront.rotationY = 0f
            binding.cardBack.visibility = View.GONE
            binding.cardBack.rotationY = -90f

            // --- FRONT CARD BINDING ---
            binding.tvQuickCategory.text = article.category.ifBlank { "General Awareness" }
            binding.tvQuickTitle.text = article.title.ifBlank { "Current Affair" }

            val mainExam = article.exams.firstOrNull() ?: "General"
            binding.tvQuickExamTag.text = "#$mainExam"

            // Headline Fact
            if (!article.headlineFact.isNullOrBlank()) {
                binding.layoutHeadlineFact.visibility = View.VISIBLE
                binding.tvHeadlineFact.text = article.headlineFact
            } else {
                binding.layoutHeadlineFact.visibility = View.GONE
            }

            // 4-5 Crisp Bullet Points (Never walls of text)
            val pointers = StringBuilder()
            if (!article.keyFactsFigures.isNullOrEmpty()) {
                for (fact in article.keyFactsFigures.take(4)) {
                    pointers.append("• ").append(fact.key).append(": ").append(fact.value).append("\n")
                }
            } else if (article.upscData != null && !article.upscData.prelimsFacts.isNullOrEmpty()) {
                for (fact in article.upscData.prelimsFacts.take(4)) {
                    pointers.append("• ").append(fact).append("\n")
                }
            } else if (!article.background.isNullOrBlank()) {
                pointers.append("• ").append(article.background)
            }
            binding.tvQuickPointers.text = pointers.toString().trimEnd()

            // Accordion Morphing for Static GK Linkage
            val staticContent = StringBuilder()
            if (!article.staticGkLinkage.isNullOrEmpty()) {
                for (gk in article.staticGkLinkage) {
                    staticContent.append("• ").append(gk.key).append(": ").append(gk.value).append("\n")
                }
            }
            if (!article.relatedStaticRevision.isNullOrEmpty()) {
                for (rev in article.relatedStaticRevision) {
                    staticContent.append("• Revision (").append(rev.key).append("): ").append(rev.value).append("\n")
                }
            }

            if (staticContent.isNotBlank()) {
                binding.layoutAccordionStaticGk.visibility = View.VISIBLE
                binding.tvStaticGkContent.text = staticContent.toString().trimEnd()

                isStaticExpanded = false
                binding.layoutStaticGkBody.visibility = View.GONE
                binding.tvAccordionArrow.text = "▼"

                binding.btnToggleStaticAccordion.setOnClickListener {
                    isStaticExpanded = !isStaticExpanded
                    TransitionManager.beginDelayedTransition(binding.layoutAccordionStaticGk)
                    binding.layoutStaticGkBody.visibility = if (isStaticExpanded) View.VISIBLE else View.GONE
                    binding.tvAccordionArrow.text = if (isStaticExpanded) "▲" else "▼"
                }
            } else {
                binding.layoutAccordionStaticGk.visibility = View.GONE
            }

            // Mnemonic pill
            if (!article.mnemonic.isNullOrBlank()) {
                binding.tvQuickMnemonic.visibility = View.VISIBLE
                binding.tvQuickMnemonic.text = "💡 Mnemonic: ${article.mnemonic}"
            } else {
                binding.tvQuickMnemonic.visibility = View.GONE
            }

            // Bookmark icon state
            updateBookmarkIcon(context, article.isBookmarked)
            binding.btnQuickBookmark.setOnClickListener {
                onBookmarkClicked(article)
                updateBookmarkIcon(context, article.isBookmarked)
            }

            // Share action
            binding.btnQuickShare.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "⚡ *${article.title}*\n\n${article.headlineFact}\n\nShared via Kurrent"
                    )
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Flashcard"))
            }

            // 3D Flip to Active Recall Back View
            binding.btnTestCard.setOnClickListener {
                UIInteractions.flipCard(context, binding.cardFront, binding.cardBack, toBack = true)
                isCardFlipped = true
            }

            binding.btnFlipBack.setOnClickListener {
                UIInteractions.flipCard(context, binding.cardFront, binding.cardBack, toBack = false)
                isCardFlipped = false
            }

            // --- BACK CARD BINDING (MCQ Active Recall) ---
            bindBackQuiz(context, article)
        }

        private fun bindBackQuiz(context: Context, article: Article) {
            val allQuizzes = SampleDataRepository.getDailyBriefing().quizzes
            // Find quiz matching this article or default to first
            val quiz = allQuizzes.find { it.articleRef == article.id } ?: allQuizzes.firstOrNull()

            if (quiz == null) {
                binding.tvFlipQuestion.text = "Active recall challenge is being generated for this topic."
                binding.layoutFlipOptions.visibility = View.GONE
                return
            }

            binding.layoutFlipOptions.visibility = View.VISIBLE
            binding.tvFlipQuestion.text = quiz.question

            val optionButtons = listOf(
                binding.btnFlipOpt0,
                binding.btnFlipOpt1,
                binding.btnFlipOpt2,
                binding.btnFlipOpt3
            )
            val letters = listOf("A", "B", "C", "D")

            for (i in optionButtons.indices) {
                val btn = optionButtons[i]
                if (i < quiz.options.size) {
                    btn.visibility = View.VISIBLE
                    btn.text = "${letters[i]}. ${quiz.options[i]}"
                    resetButton(btn)

                    btn.setOnClickListener {
                        handleOptionSelection(context, optionButtons, i, quiz)
                    }
                } else {
                    btn.visibility = View.GONE
                }
            }

            binding.layoutFlipExplanation.visibility = View.GONE
        }

        private fun handleOptionSelection(
            context: Context,
            optionButtons: List<MaterialButton>,
            selectedIndex: Int,
            quiz: QuizQuestion
        ) {
            val isCorrect = selectedIndex == quiz.correctIndex

            for (i in optionButtons.indices) {
                val btn = optionButtons[i]
                btn.isEnabled = false

                if (i == quiz.correctIndex) {
                    // Radial green fill on correct
                    btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.correct_green))
                    btn.setBackgroundColor(ContextCompat.getColor(context, R.color.ssc_badge_bg))
                    btn.setTextColor(ContextCompat.getColor(context, R.color.ssc_badge_text))
                } else if (i == selectedIndex && !isCorrect) {
                    // Wrong selection
                    btn.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.wrong_red))
                    btn.setBackgroundColor(0xFFFFEEEE.toInt())
                    btn.setTextColor(ContextCompat.getColor(context, R.color.wrong_red))
                }
            }

            if (isCorrect) {
                // Crisp light haptic tap
                UIInteractions.hapticSuccess(optionButtons[selectedIndex])
                binding.tvFlipStatus.text = "✓ Correct Answer!"
                binding.tvFlipStatus.setTextColor(ContextCompat.getColor(context, R.color.correct_green))
            } else {
                // 200ms shake + soft double vibrate
                UIInteractions.shakeView(optionButtons[selectedIndex])
                UIInteractions.hapticError(optionButtons[selectedIndex])
                val letters = listOf("A", "B", "C", "D")
                binding.tvFlipStatus.text = "✗ Incorrect. Correct: Option ${letters[quiz.correctIndex]}"
                binding.tvFlipStatus.setTextColor(ContextCompat.getColor(context, R.color.wrong_red))
            }

            // Smooth slide-down explanation box
            TransitionManager.beginDelayedTransition(binding.cardBack)
            binding.layoutFlipExplanation.visibility = View.VISIBLE
            binding.tvFlipExplanation.text = quiz.explanation
        }

        private fun resetButton(button: MaterialButton) {
            val context = button.context
            button.isEnabled = true
            button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.border_light))
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }

        private fun updateBookmarkIcon(context: Context, isBookmarked: Boolean) {
            if (isBookmarked) {
                binding.btnQuickBookmark.setColorFilter(ContextCompat.getColor(context, R.color.indigo_primary))
            } else {
                binding.btnQuickBookmark.setColorFilter(ContextCompat.getColor(context, R.color.text_muted))
            }
        }
    }
}
