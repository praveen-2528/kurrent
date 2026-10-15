package com.example.kurrent.adapter

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kurrent.R
import com.example.kurrent.databinding.ItemArticleCardBinding
import com.example.kurrent.model.Article

class ArticleAdapter(
    private var articles: List<Article>,
    private val onBookmarkClicked: (Article) -> Unit
) : RecyclerView.Adapter<ArticleAdapter.ArticleViewHolder>() {

    fun updateArticles(newArticles: List<Article>) {
        this.articles = newArticles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArticleViewHolder {
        val binding = ItemArticleCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ArticleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArticleViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    inner class ArticleViewHolder(private val binding: ItemArticleCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: Article) {
            val context = binding.root.context

            binding.tvCategory.text = article.category.ifBlank { "General Awareness" }
            binding.tvReadTime.text = article.readTime.ifBlank { "2 mins" }
            binding.tvTitle.text = article.title.ifBlank { "Current Affair" }

            // 1. Exam Badges
            binding.layoutExamBadges.removeAllViews()
            for (exam in article.exams) {
                val badge = createBadge(context, exam)
                binding.layoutExamBadges.addView(badge)
            }

            // 2. Headline Fact
            if (!article.headlineFact.isNullOrBlank()) {
                binding.tvHeadlineFact.visibility = View.VISIBLE
                binding.tvHeadlineFact.text = article.headlineFact
            } else {
                binding.tvHeadlineFact.visibility = View.GONE
            }

            // 3. Background & Impact
            val contextText = StringBuilder()
            if (!article.background.isNullOrBlank()) {
                contextText.append(article.background)
            }
            if (!article.longTermImpact.isNullOrBlank()) {
                if (contextText.isNotEmpty()) contextText.append("\n\n")
                contextText.append("Impact: ").append(article.longTermImpact)
            }
            binding.tvBackground.text = contextText.toString()

            // 4. Key Facts & Figures Table
            binding.layoutKeyFacts.removeAllViews()
            if (!article.keyFactsFigures.isNullOrEmpty()) {
                binding.layoutKeyFacts.visibility = View.VISIBLE
                val header = TextView(context).apply {
                    text = "Key Verified Figures:"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setPadding(0, 0, 0, 6)
                }
                binding.layoutKeyFacts.addView(header)

                for (item in article.keyFactsFigures) {
                    val row = TextView(context).apply {
                        text = "• ${item.key}: ${item.value}"
                        textSize = 12.5f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, 2, 0, 2)
                    }
                    binding.layoutKeyFacts.addView(row)
                }
            } else {
                binding.layoutKeyFacts.visibility = View.GONE
            }

            // 5. Mnemonic Memory Hook
            if (!article.mnemonic.isNullOrBlank()) {
                binding.layoutMnemonic.visibility = View.VISIBLE
                binding.tvMnemonic.text = "Memory Hook: ${article.mnemonic}"
            } else {
                binding.layoutMnemonic.visibility = View.GONE
            }

            // 6. Distractor Traps (Trap Alert!)
            if (!article.distractorTraps.isNullOrEmpty()) {
                binding.layoutTraps.visibility = View.VISIBLE
                binding.tvTraps.text = article.distractorTraps.joinToString("\n") { "• $it" }
            } else {
                binding.layoutTraps.visibility = View.GONE
            }

            // 7. Static GK Linkage & Revision
            binding.layoutStaticGk.removeAllViews()
            val hasStatic = !article.staticGkLinkage.isNullOrEmpty() || !article.relatedStaticRevision.isNullOrEmpty()
            if (hasStatic) {
                binding.layoutStaticGk.visibility = View.VISIBLE
                val staticHeader = TextView(context).apply {
                    text = "Static GK Connection & Revision:"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(context, R.color.indigo_dark))
                    setPadding(0, 8, 0, 4)
                }
                binding.layoutStaticGk.addView(staticHeader)

                for (gk in article.staticGkLinkage) {
                    val row = TextView(context).apply {
                        text = "• ${gk.key}: ${gk.value}"
                        textSize = 12.5f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, 2, 0, 2)
                    }
                    binding.layoutStaticGk.addView(row)
                }

                for (rev in article.relatedStaticRevision) {
                    val row = TextView(context).apply {
                        text = "• Revision (${rev.key}): ${rev.value}"
                        textSize = 12.5f
                        setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                        setPadding(0, 2, 0, 2)
                    }
                    binding.layoutStaticGk.addView(row)
                }
            } else {
                binding.layoutStaticGk.visibility = View.GONE
            }

            // 8. UPSC Section
            val upsc = article.upscData
            if (upsc != null) {
                binding.layoutUpscSection.visibility = View.VISIBLE
                binding.tvGsPaper.text = "Syllabus: ${upsc.gsPaper}"
                binding.tvPrelimsPointers.text =
                    upsc.prelimsFacts.joinToString("\n") { "• $it" }

                if (!upsc.mainsQuestion.isNullOrBlank()) {
                    binding.tvMainsQuestion.visibility = View.VISIBLE
                    binding.tvMainsQuestion.text = "Mains Practice: ${upsc.mainsQuestion}"
                } else {
                    binding.tvMainsQuestion.visibility = View.GONE
                }
            } else {
                binding.layoutUpscSection.visibility = View.GONE
            }

            // 9. 5-Second Revision Line
            if (!article.revisionLine.isNullOrBlank()) {
                binding.tvRevisionLine.visibility = View.VISIBLE
                binding.tvRevisionLine.text = "⚡ 5-Sec Revision: ${article.revisionLine}"
            } else {
                binding.tvRevisionLine.visibility = View.GONE
            }

            // 10. Bookmark State
            updateBookmarkIcon(article.isBookmarked)
            binding.btnBookmark.setOnClickListener {
                onBookmarkClicked(article)
                updateBookmarkIcon(article.isBookmarked)
            }

            // 11. Share Action
            binding.btnShare.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, article.title)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "📚 *${article.title}*\n\n${article.headlineFact}\n\n💡 Mnemonic: ${article.mnemonic}\n\nShared via Kurrent"
                    )
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Current Affair"))
            }

            // 12. Official Source Link
            if (!article.sourceUrl.isNullOrBlank()) {
                binding.btnSource.visibility = View.VISIBLE
                binding.btnSource.text = if (!article.sourceName.isNullOrBlank()) "${article.sourceName} ↗" else "Official Source ↗"
                binding.btnSource.setOnClickListener {
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(article.sourceUrl))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        // ignore if invalid url
                    }
                }
            } else {
                binding.btnSource.visibility = View.GONE
            }
        }

        private fun updateBookmarkIcon(isBookmarked: Boolean) {
            val context = binding.root.context
            if (isBookmarked) {
                binding.btnBookmark.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.indigo_primary))
            } else {
                binding.btnBookmark.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_muted))
            }
        }

        private fun createBadge(context: Context, exam: String): TextView {
            return TextView(context).apply {
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params
                setPadding(16, 6, 16, 6)
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)

                val upper = exam.uppercase()
                when {
                    upper.contains("UPSC") -> {
                        text = "UPSC"
                        setBackgroundResource(R.drawable.badge_upsc)
                        setTextColor(ContextCompat.getColor(context, R.color.upsc_badge_text))
                    }
                    upper.contains("SSC") -> {
                        text = "SSC"
                        setBackgroundResource(R.drawable.badge_ssc)
                        setTextColor(ContextCompat.getColor(context, R.color.ssc_badge_text))
                    }
                    upper.contains("IBPS") -> {
                        text = "IBPS"
                        setBackgroundResource(R.drawable.badge_ibps)
                        setTextColor(ContextCompat.getColor(context, R.color.ibps_badge_text))
                    }
                    else -> {
                        text = exam
                        setBackgroundColor(Color.LTGRAY)
                        setTextColor(Color.DKGRAY)
                    }
                }
            }
        }
    }
}
