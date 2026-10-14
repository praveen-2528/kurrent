package com.example.kurrent.data

import com.example.kurrent.model.Article
import com.example.kurrent.model.DailyBriefing
import com.example.kurrent.model.FactItem
import com.example.kurrent.model.QuizQuestion
import com.example.kurrent.model.RecallItem
import com.example.kurrent.model.UpscData

object SampleDataRepository {

    private val cachedBriefing: DailyBriefing by lazy {
        DailyBriefing(
            date = "2026-10-15",
            edition = 143,
            articles = listOf(
                Article(
                    id = "art_01",
                    title = "RBI Releases Revised Framework for Project Finance Lending",
                    category = "Economy & Banking",
                    exams = listOf("SSC_CGL", "IBPS_MAINS", "UPSC"),
                    importance = "HIGH",
                    readTime = "3 mins",
                    sourceName = "Reserve Bank of India Press Release",
                    sourceUrl = "https://rbi.org.in/press/project-finance-2026",
                    headlineFact = "RBI eases proposed project finance provisioning to a phased 2.5% from 5%, exempting Regional Rural Banks (RRBs).",
                    background = "In May 2024, RBI proposed a stringent 5% general provisioning for infrastructure projects under construction. Following banking sector representations on credit slowdown concerns, RBI calibrated the framework.",
                    longTermImpact = "Lowers borrowing costs for greenfield infrastructure, mitigates sudden NPA spikes, while ensuring banks maintain adequate capital buffers against gestation delays.",
                    keyFactsFigures = listOf(
                        FactItem("Final Provisioning Rate", "2.5% phased during construction"),
                        FactItem("Draft Proposed Rate", "5.0% flat"),
                        FactItem("Delinquency Threshold", "Projects delayed > 2 years require mandatory re-rating"),
                        FactItem("Applicability", "Commercial Banks, NBFCs (Excludes RRBs)")
                    ),
                    staticGkLinkage = listOf(
                        FactItem("RBI Establishment", "Set up on April 1, 1935 under the Reserve Bank of India Act, 1934 on the recommendations of the Hilton Young Commission."),
                        FactItem("Nationalisation of RBI", "Nationalised on January 1, 1949.")
                    ),
                    relatedStaticRevision = listOf(
                        FactItem("Basel III Norms", "Capital Adequacy Ratio (CAR) mandate for Indian scheduled commercial banks is 11.5% (including Capital Conservation Buffer).")
                    ),
                    distractorTraps = listOf(
                        "Confusing RRBs as covered under this rule; RRBs are explicitly EXEMPTED.",
                        "Assuming the 2.5% provision is upfront; it is phased (1% in yr 1, 1.5% in yr 2, up to 2.5% at completion)."
                    ),
                    mnemonic = "\"2.5 to Thrive, Not 5 to Strive\" — Remember RBI cut project finance provisioning from 5% to 2.5%.",
                    upscData = UpscData(
                        gsPaper = "GS-3 (Indian Economy & Infrastructure)",
                        prelimsFacts = listOf(
                            "Phased general provisioning: 1% during year 1, 1.5% during year 2, up to 2.5% at completion.",
                            "Applicable to all Scheduled Commercial Banks (excluding RRBs) and NBFCs.",
                            "Infrastructure projects facing delays over 2 years require mandatory credit re-rating."
                        ),
                        mainsAnalysis = "Striking a prudent equilibrium between infrastructure financing and financial system stability. Prevention of asset-liability mismatches in long-gestation assets.",
                        mainsQuestion = "Examine how regulatory provisioning norms by the central bank influence India's private capital expenditure in infrastructure."
                    ),
                    sscIbpsFacts = listOf(
                        FactItem("Regulatory Authority", "Reserve Bank of India (RBI)"),
                        FactItem("Headquarters", "Mumbai, Maharashtra"),
                        FactItem("Current Governor", "Shaktikanta Das"),
                        FactItem("Exempted Lenders", "Regional Rural Banks (RRBs)")
                    ),
                    revisionLine = "RBI finalizes phased 2.5% project finance provisioning during construction while exempting RRBs."
                ),
                Article(
                    id = "art_02",
                    title = "India Successfully Flight-Tests Long-Range Hypersonic Missile off Odisha Coast",
                    category = "Science, Tech & Defence",
                    exams = listOf("SSC_CGL", "UPSC"),
                    importance = "HIGH",
                    readTime = "2 mins",
                    sourceName = "PIB Defence Wing",
                    sourceUrl = "https://pib.gov.in/drdo-hypersonic",
                    headlineFact = "DRDO executes maiden flight-test of long-range hypersonic missile with strike range > 1,500 km.",
                    background = "DRDO conducted the test from Dr APJ Abdul Kalam Island off Odisha coast, making India one of very few nations with long-range hypersonic glide/strike systems.",
                    longTermImpact = "Strengthens credible conventional and strategic deterrence across the Indo-Pacific theatre with high terminal maneuverability.",
                    keyFactsFigures = listOf(
                        FactItem("Strike Range", "> 1,500 km"),
                        FactItem("Velocity", "Hypersonic (Mach 5+)"),
                        FactItem("Launch Platform", "Dr APJ Abdul Kalam Island, Odisha")
                    ),
                    staticGkLinkage = listOf(
                        FactItem("DRDO Formation", "Established in 1958 under the Ministry of Defence."),
                        FactItem("Wheeler Island Renaming", "Renamed to Dr APJ Abdul Kalam Island in September 2015.")
                    ),
                    relatedStaticRevision = listOf(
                        FactItem("Mach Number", "Mach 1 = Speed of Sound (~343 m/s). Hypersonic indicates speeds exceeding Mach 5.")
                    ),
                    distractorTraps = listOf(
                        "Confusing launch site with Sriharikota; Sriharikota (ISRO) is for space launches, Wheeler/Kalam Island (DRDO) is for missile tests."
                    ),
                    mnemonic = "\"Kalam Island Shoots Hypersonic Beyond 1500\" — Remember Kalam Island and 1500 km range.",
                    upscData = UpscData(
                        gsPaper = "GS-3 (Indigenisation of Technology & Defence)",
                        prelimsFacts = listOf(
                            "Developed jointly by Dr APJ Abdul Kalam Missile Complex (Hyderabad) and DRDO laboratories.",
                            "Maneuverable reentry vehicle profile evades ballistic missile defense (BMD) radars."
                        ),
                        mainsAnalysis = "Elevates India's conventional counter-force capability and maritime domain awareness in the Indian Ocean.",
                        mainsQuestion = "Analyze the strategic significance of indigenous hypersonic capabilities in safeguarding India's maritime and terrestrial sovereignty."
                    ),
                    sscIbpsFacts = listOf(
                        FactItem("Developing Body", "DRDO"),
                        FactItem("DRDO Chief", "Dr Samir V Kamat"),
                        FactItem("Test Location", "Dr APJ Abdul Kalam Island, Odisha")
                    ),
                    revisionLine = "DRDO successfully test-fires maiden long-range hypersonic missile (> 1,500 km) from Kalam Island."
                )
            ),
            recalls = listOf(
                RecallItem(
                    id = "rec_01",
                    date = "2026-10-08",
                    category = "Defence & Security",
                    title = "DRDO Flight-Tests Hypersonic Missile off Odisha Coast",
                    fact = "Maiden test from Dr APJ Abdul Kalam Island achieved hypersonic velocity (> Mach 5) with a strike range exceeding 1,500 km.",
                    sourceUrl = "https://pib.gov.in/drdo-hypersonic"
                ),
                RecallItem(
                    id = "rec_02",
                    date = "2026-10-01",
                    category = "Polity & Governance",
                    title = "Supreme Court 9-Judge Ruling on Industrial Alcohol",
                    fact = "Held 8:1 that Entry 8 of State List gives states regulatory control over industrial alcohol that can be converted for human consumption.",
                    sourceUrl = "https://sci.gov.in"
                )
            ),
            quizzes = listOf(
                QuizQuestion(
                    id = "q_01",
                    articleRef = "art_01",
                    exam = "SSC_CGL",
                    question = "Under the revised RBI framework for project finance, which of the following financial institutions is exempt from the mandatory provisioning norms?",
                    options = listOf(
                        "Non-Banking Financial Companies (NBFCs)",
                        "Regional Rural Banks (RRBs)",
                        "Public Sector Commercial Banks",
                        "Private Scheduled Commercial Banks"
                    ),
                    correctIndex = 1,
                    explanation = "Regional Rural Banks (RRBs) are explicitly exempt from the RBI's revised project finance provisioning guidelines."
                ),
                QuizQuestion(
                    id = "q_02",
                    articleRef = "art_02",
                    exam = "SSC_CGL",
                    question = "The long-range hypersonic missile recently tested by DRDO was launched from which island in Odisha?",
                    options = listOf(
                        "Sriharikota Island",
                        "Dr APJ Abdul Kalam Island",
                        "Pamban Island",
                        "Netaji Subhash Chandra Bose Island"
                    ),
                    correctIndex = 1,
                    explanation = "The missile was tested off the Odisha coast from Dr APJ Abdul Kalam Island (formerly Wheeler Island)."
                ),
                QuizQuestion(
                    id = "q_03",
                    articleRef = "art_01",
                    exam = "UPSC",
                    question = "With reference to the RBI's revised guidelines on project finance, consider the following statements:\n1. The framework mandates a uniform 5% general provisioning throughout the construction period.\n2. The revised guidelines apply to Scheduled Commercial Banks and Non-Banking Financial Companies (NBFCs).\n3. Regional Rural Banks (RRBs) are exempt from these guidelines.\nWhich of the statements given above are correct?",
                    options = listOf(
                        "1 and 2 only",
                        "2 and 3 only",
                        "1 and 3 only",
                        "1, 2 and 3"
                    ),
                    correctIndex = 1,
                    explanation = "Statement 1 is incorrect because the provisioning was revised to a phased 2.5%, not 5%. Statements 2 and 3 are correct."
                )
            )
        )
    }

    fun getDailyBriefing(): DailyBriefing = cachedBriefing

    fun getArticlesByFilter(examFilter: String): List<Article> {
        val all = cachedBriefing.articles
        return if (examFilter == "ALL") {
            all
        } else {
            all.filter { article ->
                article.exams.any { examTag ->
                    when (examFilter) {
                        "UPSC" -> examTag.contains("UPSC", ignoreCase = true)
                        "SSC" -> examTag.contains("SSC", ignoreCase = true)
                        "IBPS" -> examTag.contains("IBPS", ignoreCase = true)
                        else -> true
                    }
                }
            }
        }
    }

    fun getRecalls(): List<RecallItem> = cachedBriefing.recalls

    fun toggleBookmark(articleId: String): Boolean {
        val article = cachedBriefing.articles.find { it.id == articleId }
        article?.let {
            it.isBookmarked = !it.isBookmarked
            return it.isBookmarked
        }
        return false
    }
}
