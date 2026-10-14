package com.example.kurrent.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kurrent.R
import com.example.kurrent.databinding.ItemQuizCardBinding
import com.example.kurrent.model.QuizQuestion
import com.google.android.material.button.MaterialButton

class QuizAdapter(
    private var quizzes: List<QuizQuestion>
) : RecyclerView.Adapter<QuizAdapter.QuizViewHolder>() {

    fun updateQuizzes(newQuizzes: List<QuizQuestion>) {
        this.quizzes = newQuizzes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuizViewHolder {
        val binding = ItemQuizCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuizViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuizViewHolder, position: Int) {
        holder.bind(quizzes[position], position)
    }

    override fun getItemCount(): Int = quizzes.size

    inner class QuizViewHolder(private val binding: ItemQuizCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(quiz: QuizQuestion, position: Int) {
            val context = binding.root.context

            binding.tvQuizNumber.text = "QUESTION ${position + 1} OF ${quizzes.size}"
            binding.tvQuizExam.text = "${quiz.exam} TYPE"
            binding.tvQuizQuestion.text = quiz.question

            val optionButtons = listOf(
                binding.btnOption0,
                binding.btnOption1,
                binding.btnOption2,
                binding.btnOption3
            )

            val letters = listOf("A", "B", "C", "D")

            for (i in optionButtons.indices) {
                val btn = optionButtons[i]
                if (i < quiz.options.size) {
                    btn.visibility = View.VISIBLE
                    btn.text = "${letters[i]}. ${quiz.options[i]}"
                    resetButtonAppearance(btn)
                } else {
                    btn.visibility = View.GONE
                }
            }

            // If user has already answered this question
            if (quiz.selectedOptionIndex != -1) {
                applyAnsweredState(optionButtons, quiz)
            } else {
                binding.layoutExplanation.visibility = View.GONE
                for (i in optionButtons.indices) {
                    val btn = optionButtons[i]
                    btn.isEnabled = true
                    btn.setOnClickListener {
                        quiz.selectedOptionIndex = i
                        applyAnsweredState(optionButtons, quiz)
                    }
                }
            }
        }

        private fun applyAnsweredState(
            optionButtons: List<MaterialButton>,
            quiz: QuizQuestion
        ) {
            val context = binding.root.context
            val letters = listOf("A", "B", "C", "D")

            for (i in optionButtons.indices) {
                val btn = optionButtons[i]
                btn.isEnabled = false

                if (i == quiz.correctIndex) {
                    btn.strokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.correct_green)
                    )
                    btn.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.ssc_badge_bg)
                    )
                    btn.setTextColor(
                        ContextCompat.getColor(context, R.color.ssc_badge_text)
                    )
                } else if (i == quiz.selectedOptionIndex && quiz.selectedOptionIndex != quiz.correctIndex) {
                    btn.strokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.wrong_red)
                    )
                    btn.setBackgroundColor(0xFFFFEEEE.toInt())
                    btn.setTextColor(
                        ContextCompat.getColor(context, R.color.wrong_red)
                    )
                }
            }

            // Show explanation
            binding.layoutExplanation.visibility = View.VISIBLE
            val isCorrect = quiz.selectedOptionIndex == quiz.correctIndex
            if (isCorrect) {
                binding.tvExplanationStatus.text = "✓ Correct! Option ${letters[quiz.correctIndex]}"
                binding.tvExplanationStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.correct_green)
                )
            } else {
                binding.tvExplanationStatus.text = "✗ Incorrect. Correct is Option ${letters[quiz.correctIndex]}"
                binding.tvExplanationStatus.setTextColor(
                    ContextCompat.getColor(context, R.color.wrong_red)
                )
            }
            binding.tvExplanationText.text = quiz.explanation
        }

        private fun resetButtonAppearance(button: MaterialButton) {
            val context = button.context
            button.strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.border_light)
            )
            button.setBackgroundColor(Color.TRANSPARENT)
            button.setTextColor(
                ContextCompat.getColor(context, R.color.text_primary)
            )
        }
    }
}
