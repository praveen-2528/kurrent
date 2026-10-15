package com.example.kurrent.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DailyBriefing(
    @SerializedName("date") val date: String = "",
    @SerializedName("edition") val edition: Int = 1,
    @SerializedName("articles") val articles: List<Article> = emptyList(),
    @SerializedName("recalls") val recalls: List<RecallItem> = emptyList(),
    @SerializedName("quizzes") val quizzes: List<QuizQuestion> = emptyList()
) : Serializable

data class Article(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("exams") val exams: List<String> = emptyList(),
    @SerializedName("importance") val importance: String = "HIGH",
    @SerializedName(value = "read_time", alternate = ["readTime"]) val readTime: String = "2 mins",
    @SerializedName(value = "source_name", alternate = ["sourceName"]) val sourceName: String = "",
    @SerializedName(value = "source_url", alternate = ["sourceUrl"]) val sourceUrl: String = "",
    @SerializedName(value = "headline_fact", alternate = ["headlineFact"]) val headlineFact: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName(value = "long_term_impact", alternate = ["longTermImpact"]) val longTermImpact: String = "",
    @SerializedName(value = "key_facts_figures", alternate = ["keyFactsFigures"]) val keyFactsFigures: List<FactItem> = emptyList(),
    @SerializedName(value = "static_gk_linkage", alternate = ["staticGkLinkage"]) val staticGkLinkage: List<FactItem> = emptyList(),
    @SerializedName(value = "related_static_revision", alternate = ["relatedStaticRevision"]) val relatedStaticRevision: List<FactItem> = emptyList(),
    @SerializedName(value = "distractor_traps", alternate = ["distractorTraps"]) val distractorTraps: List<String> = emptyList(),
    @SerializedName("mnemonic") val mnemonic: String = "",
    @SerializedName(value = "upsc", alternate = ["upscData"]) val upscData: UpscData? = null,
    @SerializedName(value = "ssc_ibps_facts", alternate = ["sscIbpsFacts"]) val sscIbpsFacts: List<FactItem> = emptyList(),
    @SerializedName(value = "revision_line", alternate = ["revisionLine"]) val revisionLine: String = "",
    var isBookmarked: Boolean = false
) : Serializable

data class UpscData(
    @SerializedName(value = "gs_paper", alternate = ["gsPaper"]) val gsPaper: String = "",
    @SerializedName(value = "prelims_facts", alternate = ["prelimsFacts"]) val prelimsFacts: List<String> = emptyList(),
    @SerializedName(value = "mains_analysis", alternate = ["mainsAnalysis"]) val mainsAnalysis: String = "",
    @SerializedName(value = "mains_question", alternate = ["mainsQuestion"]) val mainsQuestion: String = ""
) : Serializable

data class FactItem(
    @SerializedName("key") val key: String = "",
    @SerializedName("value") val value: String = ""
) : Serializable

data class RecallItem(
    @SerializedName("id") val id: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("fact") val fact: String = "",
    @SerializedName(value = "source_url", alternate = ["sourceUrl"]) val sourceUrl: String = ""
) : Serializable

data class QuizQuestion(
    @SerializedName("id") val id: String = "",
    @SerializedName(value = "article_ref", alternate = ["articleRef"]) val articleRef: String = "",
    @SerializedName("exam") val exam: String = "",
    @SerializedName("question") val question: String = "",
    @SerializedName("options") val options: List<String> = emptyList(),
    @SerializedName(value = "correct_index", alternate = ["correctIndex"]) val correctIndex: Int = 0,
    @SerializedName("explanation") val explanation: String = "",
    var selectedOptionIndex: Int = -1
) : Serializable
