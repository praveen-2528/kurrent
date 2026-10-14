package com.example.kurrent.model

import java.io.Serializable

data class DailyBriefing(
    val date: String,
    val edition: Int,
    val articles: List<Article>,
    val recalls: List<RecallItem>,
    val quizzes: List<QuizQuestion>
) : Serializable

data class Article(
    val id: String,
    val title: String,
    val category: String,
    val exams: List<String>,
    val importance: String,
    val readTime: String,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val headlineFact: String = "",
    val background: String = "",
    val longTermImpact: String = "",
    val keyFactsFigures: List<FactItem> = emptyList(),
    val staticGkLinkage: List<FactItem> = emptyList(),
    val relatedStaticRevision: List<FactItem> = emptyList(),
    val distractorTraps: List<String> = emptyList(),
    val mnemonic: String = "",
    val upscData: UpscData? = null,
    val sscIbpsFacts: List<FactItem> = emptyList(),
    val revisionLine: String = "",
    var isBookmarked: Boolean = false
) : Serializable

data class UpscData(
    val gsPaper: String,
    val prelimsFacts: List<String>,
    val mainsAnalysis: String,
    val mainsQuestion: String
) : Serializable

data class FactItem(
    val key: String,
    val value: String
) : Serializable

data class RecallItem(
    val id: String,
    val date: String,
    val category: String,
    val title: String,
    val fact: String,
    val sourceUrl: String = ""
) : Serializable

data class QuizQuestion(
    val id: String,
    val articleRef: String = "",
    val exam: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    var selectedOptionIndex: Int = -1
) : Serializable
