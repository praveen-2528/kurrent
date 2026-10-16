package com.example.kurrent.data

import android.content.Context
import android.util.Log
import com.example.kurrent.data.network.ApiClient
import com.example.kurrent.model.Article
import com.example.kurrent.model.DailyBriefing
import com.example.kurrent.model.FactItem
import com.example.kurrent.model.QuizQuestion
import com.example.kurrent.model.RecallItem
import com.example.kurrent.model.UpscData
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SampleDataRepository {

    private const val TAG = "SampleDataRepository"
    private val gson = Gson()
    private var cacheDir: File? = null

    private var activeBriefing: DailyBriefing = createDefaultBriefing()
    private var todayBriefing: DailyBriefing = activeBriefing

    /**
     * Initializes the offline cache directory and loads the most recently cached briefing if available.
     */
    fun init(context: Context) {
        try {
            val dir = File(context.filesDir, "briefing_cache")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            cacheDir = dir

            val cachedToday = loadFromDisk("today")
            if (cachedToday != null && cachedToday.articles.isNotEmpty()) {
                val merged = cachedToday.copy(
                    articles = (getDefaultStaticArticles() + cachedToday.articles).distinctBy { it.id },
                    recalls = (getDefaultStaticRecalls() + cachedToday.recalls).distinctBy { it.id },
                    quizzes = (getDefaultStaticQuizzes() + cachedToday.quizzes).distinctBy { it.id }
                )
                activeBriefing = merged
                todayBriefing = merged
                saveToDisk("today", merged)
                Log.d(TAG, "Restored and enriched cached briefing for date: ${merged.date} (${merged.articles.size} articles)")
            } else {
                activeBriefing = createDefaultBriefing()
                todayBriefing = activeBriefing
                saveToDisk("today", activeBriefing)
                saveToDisk(activeBriefing.date, activeBriefing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing cache: ${e.message}", e)
        }
    }

    private fun saveToDisk(key: String, briefing: DailyBriefing) {
        val dir = cacheDir ?: return
        try {
            val file = File(dir, "briefing_$key.json")
            file.writeText(gson.toJson(briefing))
            Log.d(TAG, "Cached briefing to disk: ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache briefing $key: ${e.message}", e)
        }
    }

    private fun loadFromDisk(key: String): DailyBriefing? {
        val dir = cacheDir ?: return null
        try {
            val file = File(dir, "briefing_$key.json")
            if (file.exists() && file.length() > 0) {
                return gson.fromJson(file.readText(), DailyBriefing::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached briefing $key: ${e.message}", e)
        }
        return null
    }

    private fun createDefaultBriefing(): DailyBriefing {
        return DailyBriefing(
            date = "2026-10-16",
            edition = 144,
            articles = getDefaultStaticArticles(),
            recalls = getDefaultStaticRecalls(),
            quizzes = getDefaultStaticQuizzes()
        )
    }

    /**
     * Dedicated high-yield static syllabus cards matching real exam priority topics:
     * 1. Crops & Cropping Patterns in India (Geography & Agriculture)
     * 2. Causes & Catalysts of the 1857 Revolt (Modern Indian History)
     * 3. Ganga River Drainage System & Panch Prayag (Physical Geography)
     * 4. 37th National Games in Goa (Sports & Current Affairs)
     * 5. Landmark Supreme Court Cases & Constitutional Doctrines (Polity & Governance)
     */
    fun getDefaultStaticArticles(): List<Article> {
        return listOf(
            Article(
                id = "art_crops",
                title = "Cropping Patterns, Millets & Agro-Climatic Zones in India",
                category = "Geography & Agriculture",
                exams = listOf("UPSC", "SSC_CGL", "STATE_PSC"),
                importance = "HIGH",
                readTime = "3 mins",
                sourceName = "ICAR & Ministry of Agriculture and Farmers Welfare",
                sourceUrl = "https://agricoop.gov.in",
                headlineFact = "India classifies agriculture into Kharif, Rabi, and Zaid seasons, with Millets (Shree Anna) requiring less than 40% of the water consumed by paddy and wheat.",
                background = "India's diverse agro-climatic zones support three distinct cropping cycles. Under the National Food Security Mission and International Year of Millets, transitioning water-stressed regions from water-guzzling crops to coarse cereals is a national priority.",
                longTermImpact = "Mitigates groundwater depletion in the north-western aquifer belt, enhances climate resilience against El Niño droughts, and strengthens nutritional security.",
                keyFactsFigures = listOf(
                    FactItem("Kharif Season (Monsoon)", "Sown June-July, harvested Sept-Oct. Crops: Rice, Maize, Jowar, Bajra, Cotton, Jute, Groundnut, Soybean."),
                    FactItem("Rabi Season (Winter)", "Sown Oct-Dec, harvested April-June. Crops: Wheat, Barley, Gram, Mustard, Peas, Linseed. Benefits from Western Disturbances."),
                    FactItem("Zaid Season (Summer)", "Short gap crop grown March-June between Rabi and Kharif. Crops: Watermelon, Cucumber, Vegetables, Fodder."),
                    FactItem("Shree Anna (Millets)", "C4 photosynthetic crops (Bajra, Jowar, Ragi) having high carbon-fixation, low water footprint (~400mm vs 2000mm for paddy), and high iron/calcium content.")
                ),
                staticGkLinkage = listOf(
                    FactItem("Green Revolution Pioneer", "Dr. M.S. Swaminathan (Father of Green Revolution in India) and Dr. Norman Borlaug (Global). Introduced High-Yielding Variety (HYV) dwarf wheat and rice seeds in the mid-1960s."),
                    FactItem("Operation Greens", "Launched on the lines of Operation Flood to stabilize supply of TOP (Tomato, Onion, Potato) and 22 perishable crops without price volatility.")
                ),
                relatedStaticRevision = listOf(
                    FactItem("Soil Requirements", "Paddy requires heavy clayey/alluvial soil with standing water; Cotton requires Regur (Black) soil and 210 frost-free days; Tea requires well-drained acidic soil on hill slopes.")
                ),
                distractorTraps = listOf(
                    "Assuming Mustard and Groundnut are both Kharif: Groundnut is Kharif (rainy season), while Mustard is strictly Rabi (winter).",
                    "Confusing Zaid as an autumn harvest: Zaid is the hot dry summer gap between March and June."
                ),
                mnemonic = "\"Rabi Loves Winter Gold\" — Rabi = Wheat, Gram, Mustard, Barley. Remember Groundnut = Kharif, Mustard = Rabi.",
                upscData = UpscData(
                    gsPaper = "GS-3 (Major Crops, Cropping Patterns, Irrigation & Food Security)",
                    prelimsFacts = listOf(
                        "Millets are C4 photosynthetic plants with superior water-use efficiency and drought resistance.",
                        "Western Disturbances bring winter rainfall crucial for Rabi wheat yield in North-West India.",
                        "Sugarcane is a long-duration crop taking 10-15 months to mature, spanning across seasons."
                    ),
                    mainsAnalysis = "Crop diversification from water-intensive rice-wheat monoculture towards climate-resilient coarse cereals is imperative for groundwater sustainability and farmer income stabilization in the Indo-Gangetic plains.",
                    mainsQuestion = "Analyze the economic and ecological rationale for promoting millets (Shree Anna) in drought-prone agro-climatic regions of India."
                ),
                sscIbpsFacts = listOf(
                    FactItem("Major Kharif Crop", "Rice / Paddy"),
                    FactItem("Major Rabi Oilseed", "Mustard"),
                    FactItem("Leading Millet State", "Rajasthan (Bajra), Karnataka (Ragi), Maharashtra (Jowar)"),
                    FactItem("MSP Determination Body", "Commission for Agricultural Costs and Prices (CACP)")
                ),
                revisionLine = "Kharif crops (Rice, Cotton, Groundnut) rely on monsoon rains; Rabi crops (Wheat, Mustard, Gram) mature with winter showers and Western Disturbances."
            ),
            Article(
                id = "art_1857",
                title = "Causes and Catalysts of the 1857 Revolt: The First War of Independence",
                category = "Modern Indian History",
                exams = listOf("UPSC", "SSC_CGL", "STATE_PSC"),
                importance = "HIGH",
                readTime = "3 mins",
                sourceName = "NCERT Modern India & Bipan Chandra",
                sourceUrl = "https://ncert.nic.in",
                headlineFact = "The 1857 revolt ignited on May 10, 1857 at Meerut, triggered by greased cartridges but rooted in deep political, economic, socio-religious, and military grievances under Lord Dalhousie and Lord Canning.",
                background = "A century of British East India Company rule culminated in widespread discontent. Lord Dalhousie's aggressive annexation policy through the Doctrine of Lapse and the annexation of Awadh in 1856 transformed simmering regional resentments into a coordinated uprising.",
                longTermImpact = "Ended the British East India Company rule; Queen Victoria's Proclamation of 1858 transferred direct governance to the British Crown, replaced Governor-General with Viceroy (Lord Canning), and reshuffled army racial composition under the Peel Commission.",
                keyFactsFigures = listOf(
                    FactItem("Political Discontent", "Dalhousie's Doctrine of Lapse annexed Satara (1848), Sambalpur (1849), Jhansi (1853), and Nagpur (1854). Nana Sahib (adopted son of Peshwa Baji Rao II) was denied pension."),
                    FactItem("Annexation of Awadh (1856)", "Annexed on pretext of 'maladministration' by Dalhousie, deeply outraging the Bengal Army sepoys since 75,000 sepoys hailed from Awadh (known as the 'Nursery of Bengal Army')."),
                    FactItem("Socio-Religious & Military Acts", "General Service Enlistment Act (1856) required sepoys to serve overseas across the 'Kala Pani'. Religious Disabilities Act (1856) protected property rights of Christian converts."),
                    FactItem("Immediate Trigger", "Introduction of Enfield P-53 rifle cartridges greased with cow and pig fat, requiring biting before loading. On March 29, 1857, Mangal Pandey mutinied at Barrackpore.")
                ),
                staticGkLinkage = listOf(
                    FactItem("Governor-General during 1857", "Lord Canning was Governor-General (1856–1858) and became the first Viceroy of India (1858–1862)."),
                    FactItem("Key Centers & Leaders", "Delhi: General Bakht Khan & Bahadur Shah Zafar; Kanpur: Nana Sahib & Tantia Tope; Lucknow: Begum Hazrat Mahal; Jhansi: Rani Lakshmibai; Bihar (Arrah): Kunwar Singh.")
                ),
                relatedStaticRevision = listOf(
                    FactItem("Peel Commission (1858)", "Recommended army reorganization based on 'divide and rule'—martial vs non-martial races theory, deliberately reducing Indian proportion in the military.")
                ),
                distractorTraps = listOf(
                    "Assuming Lord Dalhousie was Governor-General during the revolt: Dalhousie introduced the Doctrine of Lapse, but Lord Canning was the Governor-General when the revolt erupted in 1857.",
                    "Thinking Awadh was annexed under Doctrine of Lapse: Awadh was annexed in 1856 on charges of 'misgovernance' (maladministration), NOT the Doctrine of Lapse."
                ),
                mnemonic = "\"Can Canning Contain the Meerut Spark?\" — Remember Lord Canning was Governor-General during 1857.",
                upscData = UpscData(
                    gsPaper = "GS-1 (Modern Indian History: Freedom Struggle & Its Stages)",
                    prelimsFacts = listOf(
                        "Lord Canning was Governor-General during the 1857 revolt, not Dalhousie.",
                        "Awadh was annexed in 1856 by Dalhousie on grounds of misgovernance, not under the Doctrine of Lapse.",
                        "General Service Enlistment Act 1856 mandated overseas military service for recruits."
                    ),
                    mainsAnalysis = "The 1857 Revolt was neither purely a sepoy mutiny nor a fully unified national war of independence; it was a watershed anti-colonial rebellion of disparate feudal, agrarian, and military elements sharing common grievances against Company raj.",
                    mainsQuestion = "Examine the socio-religious and military factors that transformed a garrison mutiny at Meerut into a widespread agrarian and civilian rebellion in 1857."
                ),
                sscIbpsFacts = listOf(
                    FactItem("Date of Outbreak", "May 10, 1857 (Meerut)"),
                    FactItem("Barrackpore Hero", "Mangal Pandey (34th Bengal Native Infantry, March 29, 1857)"),
                    FactItem("First Viceroy of India", "Lord Canning (Government of India Act 1858)"),
                    FactItem("Bihar Leader", "Kunwar Singh (Jagdishpur/Arrah)")
                ),
                revisionLine = "Lord Canning governed during 1857; greased Enfield cartridges triggered the spark, but Awadh's 1856 annexation and Dalhousie's Doctrine of Lapse fueled the fire."
            ),
            Article(
                id = "art_ganga",
                title = "Ganga River Drainage System, Panch Prayag & Tributaries",
                category = "Physical Geography & Rivers",
                exams = listOf("UPSC", "SSC_CGL", "STATE_PSC"),
                importance = "HIGH",
                readTime = "3 mins",
                sourceName = "Survey of India & Ministry of Jal Shakti (NMCG)",
                sourceUrl = "https://nmcg.nic.in",
                headlineFact = "River Ganga stretches 2,525 km, formed at Devprayag by the confluence of Alaknanda and Bhagirathi, with the Gomti being its only major tributary originating entirely in the plains.",
                background = "The Ganga basin is the largest river basin in India, covering 26% of the country's landmass. The Alaknanda river acts as the central spine along which the sacred Panch Prayag (five sacred confluences) are arranged in precise descending elevation order in Garhwal Himalayas.",
                longTermImpact = "Provides water security to over 500 million people, supports the agricultural breadbasket of northern India, and houses the world's largest mangrove delta (Sundarbans).",
                keyFactsFigures = listOf(
                    FactItem("Panch Prayag Order (Descending)", "1. Vishnuprayag (Alaknanda + Dhauliganga) → 2. Nandprayag (Alaknanda + Nandakini) → 3. Karnaprayag (Alaknanda + Pindar) → 4. Rudraprayag (Alaknanda + Mandakini) → 5. Devprayag (Alaknanda + Bhagirathi)."),
                    FactItem("Origin Streams", "Bhagirathi rises from Gangotri Glacier at Gaumukh (3,900m); Alaknanda rises from Satopanth Glacier above Badrinath."),
                    FactItem("Left-Bank Tributaries", "Ramganga, Gomti (originates at Fulhar Lake / Gomat Taal, Pilibhit, UP), Ghaghara (largest tributary by volume, enters from Nepal), Gandak, Kosi ('Sorrow of Bihar'), Mahananda."),
                    FactItem("Right-Bank Tributaries", "Yamuna (longest tributary, 1,376 km, joins at Prayagraj), Son (originates at Amarkantak plateau in MP, flows north to join near Patna), Punpun, and Damodar (joins Hooghly distributary).")
                ),
                staticGkLinkage = listOf(
                    FactItem("Sundarbans Delta", "World's largest delta formed by Ganga-Brahmaputra-Meghna, home to the Royal Bengal Tiger and Halophyte Mangrove vegetation (Sundari trees)."),
                    FactItem("National River of India", "Ganga was declared the National River of India in November 2008; Gangetic Dolphin declared National Aquatic Animal in 2009.")
                ),
                relatedStaticRevision = listOf(
                    FactItem("Farakka Barrage", "Built across Ganga in West Bengal to divert 40,000 cusecs of water into Hooghly River to flush silt and keep Kolkata Port navigable.")
                ),
                distractorTraps = listOf(
                    "Believing Gomti originates in the Himalayas: Gomti is the ONLY major tributary originating in the plains (Fulhar Lake, UP).",
                    "Confusing Mandakini with Nandakini: Nandakini meets at Nandprayag; Mandakini (flowing from Kedarnath) meets at Rudraprayag."
                ),
                mnemonic = "\"Very Nice Kids Read Devotionally\" — Vishnu (Dhauli), Nand (Nandakini), Karna (Pindar), Rudra (Mandakini), Dev (Bhagirathi).",
                upscData = UpscData(
                    gsPaper = "GS-1 (Indian Physical Geography: River Systems and Drainage Basins)",
                    prelimsFacts = listOf(
                        "River Gomti originates from Gomat Taal (Fulhar Lake) in the plains of Uttar Pradesh, not the Himalayas.",
                        "Son river flows northwards from Amarkantak plateau as a major right-bank tributary.",
                        "At Devprayag, Bhagirathi and Alaknanda unite to formally assume the name Ganga."
                    ),
                    mainsAnalysis = "The perennial nature of Himalayan tributaries combined with seasonal peninsular flows requires integrated river basin management to mitigate recurring flash floods in eastern Uttar Pradesh and Bihar.",
                    mainsQuestion = "Describe the drainage characteristics of the Ganga river basin and discuss why river Kosi undergoes frequent course avulsions."
                ),
                sscIbpsFacts = listOf(
                    FactItem("Total Length", "2,525 km (Longest river within Indian territory)"),
                    FactItem("National Aquatic Animal", "Gangetic River Dolphin (Platanista gangetica)"),
                    FactItem("Only Plains-Origin Tributary", "Gomti (originates in Pilibhit, UP)"),
                    FactItem("Longest Tributary", "Yamuna (1,376 km)")
                ),
                revisionLine = "Panch Prayag descending confluences: Vishnuprayag, Nandprayag, Karnaprayag, Rudraprayag, and Devprayag (where Alaknanda meets Bhagirathi to become Ganga)."
            ),
            Article(
                id = "art_games",
                title = "37th National Games Goa: Champions, Mascot & High-Yield Sports GK",
                category = "Sports & Current Affairs",
                exams = listOf("UPSC", "SSC_CGL", "IBPS_MAINS"),
                importance = "HIGH",
                readTime = "2 mins",
                sourceName = "Indian Olympic Association (IOA) & PIB Sports",
                sourceUrl = "https://pib.gov.in/sports",
                headlineFact = "Maharashtra topped the medal tally with 228 medals to lift the Raja Bhalindra Singh Trophy at the 37th National Games in Goa, featuring mascot 'Moga'.",
                background = "The 37th edition was hosted across 5 Goan cities (Panaji, Margao, Mapusa, Ponda, and Vasco) alongside cycling and golf events in New Delhi. Over 10,000 athletes competed across 43 sports disciplines.",
                longTermImpact = "Catalyzed world-class sports infrastructure along India's coastal belt and served as the primary qualification and talent-identification ground for the Asian and Olympic cycles.",
                keyFactsFigures = listOf(
                    FactItem("Mascot & Motto", "Mascot was 'Moga' (meaning 'Love' in Konkani, depicted as an Indian Bison / Gaur). Official Motto: 'Get Set Goa'."),
                    FactItem("Overall Champion", "Maharashtra won the Raja Bhalindra Singh Trophy for the first time since 1994, amassing 228 medals (80 Gold, 69 Silver, 79 Bronze)."),
                    FactItem("Runner-Up", "Services Sports Control Board (SSCB) finished second with 66 Gold medals (126 total); Haryana secured third place with 62 Gold medals."),
                    FactItem("New & Indigenous Disciplines", "Included Sqay (Kashmiri martial art), Rollball, Sepaktakraw, Kalaripayattu, Pencak Silat, Mini Golf, and Beach Football.")
                ),
                staticGkLinkage = listOf(
                    FactItem("Origin of National Games", "Started as the 'Indian Olympic Games' in Lahore in 1924 to select athletes for the Paris Olympics. Renamed National Games at the 1940 Bombay edition."),
                    FactItem("Raja Bhalindra Singh Trophy", "Instituted in honor of IOC member and IOA President Raja Bhalindra Singh, awarded to the overall champion state team.")
                ),
                relatedStaticRevision = listOf(
                    FactItem("Next Host (38th National Games)", "Awarded to Uttarakhand.")
                ),
                distractorTraps = listOf(
                    "Believing Services (SSCB) won the overall trophy: SSCB won the previous 4 editions, but Maharashtra ended their streak in Goa.",
                    "Confusing the mascot: Mascot is 'Moga' (Indian Bison/Gaur), NOT Veera or Perry."
                ),
                mnemonic = "\"MOGA in GOA made MAHA-Champion\" — Mascot Moga, Host Goa, Champion Maharashtra.",
                upscData = UpscData(
                    gsPaper = "Prelims General Studies & Current Events",
                    prelimsFacts = listOf(
                        "Mascot 'Moga' represents the Indian Bison (Gaur), which is also the State Animal of Goa.",
                        "Maharashtra captured the Raja Bhalindra Singh Trophy for the first time in 29 years.",
                        "Traditional martial arts like Sqay and Kalaripayattu were incorporated into official medal events."
                    ),
                    mainsAnalysis = "Promotion of traditional and indigenous martial arts within national games strengthens cultural heritage preservation and grassroots sports participation.",
                    mainsQuestion = "Evaluate the role of National Games in decentralizing sports infrastructure and talent scouting across tier-2 and coastal regions in India."
                ),
                sscIbpsFacts = listOf(
                    FactItem("Host State", "Goa (37th edition)"),
                    FactItem("Mascot", "Moga (Indian Bison)"),
                    FactItem("Trophy Winner", "Maharashtra (80 Gold, 228 Total)"),
                    FactItem("Upcoming 38th Host", "Uttarakhand")
                ),
                revisionLine = "Goa hosted the 37th National Games featuring mascot Moga; Maharashtra won the Raja Bhalindra Singh Trophy with 228 medals."
            ),
            Article(
                id = "art_sc_cases",
                title = "Landmark Supreme Court Cases: Basic Structure, Privacy & Article 21",
                category = "Indian Polity & Constitution",
                exams = listOf("UPSC", "SSC_CGL", "STATE_PSC"),
                importance = "HIGH",
                readTime = "3 mins",
                sourceName = "Supreme Court Reports (SCR) & Constitution of India",
                sourceUrl = "https://sci.gov.in",
                headlineFact = "Kesavananda Bharati (1973) established the Basic Structure Doctrine, Maneka Gandhi (1978) created the Golden Triangle of Fundamental Rights, and Puttaswamy (2017) recognized Privacy under Article 21.",
                background = "Over seven decades, the Supreme Court of India transformed from a strict positivist court under A.K. Gopalan into an activist guardian of civil liberties through landmark constitutional interpretations.",
                longTermImpact = "Checked legislative omnipotence, preserved judicial review and secularism as unamendable constitutional values, and established that Fundamental Rights are interdependent and co-equal.",
                keyFactsFigures = listOf(
                    FactItem("Kesavananda Bharati (1973)", "Largest 13-judge constitutional bench ruled 7:6 that Parliament can amend any part of the Constitution under Article 368, but cannot alter or destroy its 'Basic Structure'."),
                    FactItem("Maneka Gandhi v. UOI (1978)", "Overruled A.K. Gopalan; held that 'procedure established by law' under Article 21 must be just, fair, and reasonable (substantive due process). Formulated the Golden Triangle of Articles 14, 19, and 21."),
                    FactItem("Minerva Mills v. UOI (1980)", "Struck down Section 55 of 42nd Amendment; held that the harmony and balance between Fundamental Rights (Part III) and Directive Principles (Part IV) is part of basic structure."),
                    FactItem("Justice K.S. Puttaswamy (2017)", "Unanimous 9-judge bench ruled that the Right to Privacy is an intrinsic fundamental right guaranteed under Article 21 and Part III.")
                ),
                staticGkLinkage = listOf(
                    FactItem("First Constitutional Amendment (1951)", "Challenged in Shankari Prasad (1951), where SC initially held Parliament had unlimited power to amend Fundamental Rights."),
                    FactItem("Golaknath Case (1967)", "11-judge bench ruled 6:5 that Fundamental Rights are transcendental and immutable, leading to the 24th Amendment and ultimately Kesavananda Bharati.")
                ),
                relatedStaticRevision = listOf(
                    FactItem("Navtej Singh Johar (2018)", "Decriminalized consensual same-sex relations by striking down part of Section 377 IPC on grounds of constitutional morality."),
                    FactItem("Shayara Bano (2017)", "Declared the practice of Talaq-e-Biddat (instant triple talaq) unconstitutional under Article 14.")
                ),
                distractorTraps = listOf(
                    "Assuming 'Basic Structure' is written anywhere in the Constitution: The term 'Basic Structure' is NOT mentioned anywhere in the Constitution text; it is an entirely judicial doctrine.",
                    "Thinking due process was part of original Article 21: The drafting committee explicitly chose 'procedure established by law', which the SC judicially broadened to 'due process' in Maneka Gandhi (1978)."
                ),
                mnemonic = "\"K-M-P: Kerala built Structure, Maneka gave Liberty, Puttaswamy locked Privacy\" — Kesavananda, Maneka, Puttaswamy.",
                upscData = UpscData(
                    gsPaper = "GS-2 (Indian Constitution: Significant Provisions, Basic Structure & Judiciary)",
                    prelimsFacts = listOf(
                        "The phrase 'Basic Structure' is not defined or mentioned in the text of the Constitution of India.",
                        "Maneka Gandhi (1978) established that Articles 14, 19, and 21 form a mutually inclusive Golden Triangle.",
                        "A 13-judge bench (largest in Indian judicial history) decided Kesavananda Bharati on April 24, 1973."
                    ),
                    mainsAnalysis = "The Basic Structure doctrine acts as an institutional brake against electoral authoritarianism while preserving constitutional identity and democratic equilibrium.",
                    mainsQuestion = "Discuss how the doctrine of Basic Structure evolved and examine whether it has altered the balance of power between Parliament and the Judiciary."
                ),
                sscIbpsFacts = listOf(
                    FactItem("Basic Structure Case", "Kesavananda Bharati (1973, 13 Judges)"),
                    FactItem("Right to Privacy Case", "K.S. Puttaswamy (2017, 9 Judges)"),
                    FactItem("Golden Triangle Articles", "Articles 14, 19, and 21"),
                    FactItem("Abolition of Untouchability", "Article 17")
                ),
                revisionLine = "Kesavananda Bharati founded the Basic Structure doctrine; Maneka Gandhi established the Golden Triangle (Arts 14, 19, 21); Puttaswamy affirmed Privacy as a fundamental right."
            ),
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
        )
    }

    fun getDefaultStaticRecalls(): List<RecallItem> {
        return listOf(
            RecallItem(
                id = "rec_crops",
                date = "2026-10-16",
                category = "Geography & Agriculture",
                title = "Cropping Patterns & Millets (Shree Anna) Dynamics",
                fact = "Kharif crops (Paddy, Cotton, Groundnut) rely on monsoon rains; Rabi crops (Wheat, Mustard, Gram) depend on winter showers; Millets consume <40% water compared to paddy.",
                sourceUrl = "https://agricoop.gov.in"
            ),
            RecallItem(
                id = "rec_1857",
                date = "2026-10-16",
                category = "Modern Indian History",
                title = "1857 Revolt Outbreak & Lord Canning's Administration",
                fact = "Lord Canning was Governor-General during 1857 (not Dalhousie); spark ignited at Meerut on May 10, 1857, following Awadh's 1856 annexation on grounds of maladministration.",
                sourceUrl = "https://ncert.nic.in"
            ),
            RecallItem(
                id = "rec_ganga",
                date = "2026-10-16",
                category = "Physical Geography & Rivers",
                title = "Panch Prayag Order & Ganga Plains-Origin Tributary",
                fact = "Panch Prayag descending order: Vishnuprayag, Nandprayag, Karnaprayag, Rudraprayag, Devprayag (Alaknanda meets Bhagirathi); Gomti is the only major tributary originating in the plains (Fulhar Lake).",
                sourceUrl = "https://nmcg.nic.in"
            ),
            RecallItem(
                id = "rec_games",
                date = "2026-10-16",
                category = "Sports & Current Affairs",
                title = "37th National Games Goa & Raja Bhalindra Trophy",
                fact = "Goa hosted the 37th National Games with mascot 'Moga' (Indian Bison); Maharashtra won the Raja Bhalindra Singh Trophy with 228 medals (80 Gold).",
                sourceUrl = "https://pib.gov.in/sports"
            ),
            RecallItem(
                id = "rec_sc_cases",
                date = "2026-10-16",
                category = "Indian Polity & Constitution",
                title = "Basic Structure Doctrine & Golden Triangle of Rights",
                fact = "Kesavananda Bharati (1973, 13 judges) ruled Parliament cannot alter the Basic Structure; Maneka Gandhi (1978) forged the Golden Triangle (Arts 14, 19, 21); 'Basic Structure' is not in constitutional text.",
                sourceUrl = "https://sci.gov.in"
            ),
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
        )
    }

    fun getDefaultStaticQuizzes(): List<QuizQuestion> {
        return listOf(
            QuizQuestion(
                id = "q_crops",
                articleRef = "art_crops",
                exam = "UPSC",
                question = "With reference to cropping seasons and patterns in India, consider the following statements:\n1. Mustard and Linseed are Kharif crops sown with the onset of the southwest monsoon.\n2. Millets are C4 plants that exhibit higher water-use efficiency compared to rice and wheat.\n3. The Zaid cropping season falls between the Rabi and Kharif seasons during the summer months.\nWhich of the statements given above are correct?",
                options = listOf(
                    "1 and 2 only",
                    "2 and 3 only",
                    "1 and 3 only",
                    "1, 2 and 3"
                ),
                correctIndex = 1,
                explanation = "Statement 1 is incorrect because Mustard and Linseed are Rabi (winter) crops, not Kharif. Statements 2 and 3 are correct: Millets are high-efficiency C4 crops and Zaid is the summer crop season."
            ),
            QuizQuestion(
                id = "q_1857",
                articleRef = "art_1857",
                exam = "UPSC",
                question = "With reference to the 1857 Revolt in India, consider the following statements:\n1. Lord Dalhousie was the Governor-General of India when the rebellion erupted in May 1857.\n2. The kingdom of Awadh was annexed in 1856 under the provisions of the Doctrine of Lapse.\n3. The General Service Enlistment Act of 1856 mandated that recruits must serve overseas if ordered.\nWhich of the statements given above is/are correct?",
                options = listOf(
                    "1 and 2 only",
                    "3 only",
                    "2 and 3 only",
                    "1, 2 and 3"
                ),
                correctIndex = 1,
                explanation = "Statements 1 and 2 are classic exam traps. Statement 1 is incorrect because Lord Canning was Governor-General during 1857 (Dalhousie departed in 1856). Statement 2 is incorrect because Awadh was annexed on the pretext of 'misgovernance/maladministration', not under the Doctrine of Lapse. Statement 3 is correct."
            ),
            QuizQuestion(
                id = "q_ganga",
                articleRef = "art_ganga",
                exam = "UPSC",
                question = "Consider the following statements regarding the Ganga river drainage system:\n1. At Devprayag, river Alaknanda joins river Bhagirathi to formally form the Ganga.\n2. River Gomti is the only major tributary of Ganga that originates in the plains rather than the Himalayas.\n3. River Son is a major left-bank tributary originating from the Satpura range.\nWhich of the statements given above are correct?",
                options = listOf(
                    "1 and 2 only",
                    "2 and 3 only",
                    "1 and 3 only",
                    "1, 2 and 3"
                ),
                correctIndex = 0,
                explanation = "Statement 1 is correct (Devprayag forms Ganga). Statement 2 is correct (Gomti originates from Fulhar Lake in Pilibhit, UP). Statement 3 is incorrect because River Son is a RIGHT-BANK tributary (flowing northwards from Amarkantak plateau in the Maikala range)."
            ),
            QuizQuestion(
                id = "q_games",
                articleRef = "art_games",
                exam = "SSC_CGL",
                question = "Which state won the prestigious Raja Bhalindra Singh Trophy for overall championship at the 37th National Games held in Goa?",
                options = listOf(
                    "Haryana",
                    "Services Sports Control Board (SSCB)",
                    "Maharashtra",
                    "Kerala"
                ),
                correctIndex = 2,
                explanation = "Maharashtra topped the medal tally with 228 medals (80 Gold, 69 Silver, 79 Bronze) to capture the Raja Bhalindra Singh Trophy, breaking SSCB's winning streak."
            ),
            QuizQuestion(
                id = "q_sc_cases",
                articleRef = "art_sc_cases",
                exam = "UPSC",
                question = "With reference to constitutional jurisprudence in India, consider the following statements:\n1. The phrase 'Basic Structure' is explicitly defined in Article 368 of the Constitution of India.\n2. In Maneka Gandhi (1978), the Supreme Court ruled that Articles 14, 19, and 21 form an interdependent 'Golden Triangle'.\n3. In Justice K.S. Puttaswamy (2017), a 9-judge bench declared the Right to Privacy as an intrinsic part of Article 21.\nWhich of the statements given above are correct?",
                options = listOf(
                    "1 and 2 only",
                    "2 and 3 only",
                    "1 and 3 only",
                    "1, 2 and 3"
                ),
                correctIndex = 1,
                explanation = "Statement 1 is incorrect because the term 'Basic Structure' is nowhere mentioned or defined in the text of the Constitution; it was judicially created in Kesavananda Bharati (1973). Statements 2 and 3 are correct."
            ),
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
    }

    fun getDailyBriefing(): DailyBriefing = activeBriefing

    /**
     * Attempts to fetch live briefing from the EC2 backend.
     * Enriches live briefings with default static syllabus cards so key high-yield topics are always accessible.
     */
    suspend fun syncWithBackend(onSuccess: (DailyBriefing) -> Unit) {
        val liveBriefing = ApiClient.getTodayBriefing()
        if (liveBriefing != null && liveBriefing.articles.isNotEmpty()) {
            val mergedBriefing = liveBriefing.copy(
                articles = (getDefaultStaticArticles() + liveBriefing.articles).distinctBy { it.id },
                recalls = (getDefaultStaticRecalls() + liveBriefing.recalls).distinctBy { it.id },
                quizzes = (getDefaultStaticQuizzes() + liveBriefing.quizzes).distinctBy { it.id }
            )
            activeBriefing = mergedBriefing
            todayBriefing = mergedBriefing
            saveToDisk("today", mergedBriefing)
            saveToDisk(mergedBriefing.date, mergedBriefing)
            onSuccess(mergedBriefing)
        }
    }

    /**
     * Fetches briefing for a specific date from EC2.
     * If found, saves to cache and returns the briefing.
     * If network fails or is offline, checks local disk cache before returning null.
     */
    suspend fun fetchBriefingForDate(date: String): DailyBriefing? {
        val cached = loadFromDisk(date)

        val liveBriefing = ApiClient.getBriefingByDate(date)
        if (liveBriefing != null && liveBriefing.articles.isNotEmpty()) {
            activeBriefing = liveBriefing
            saveToDisk(date, liveBriefing)
            return liveBriefing
        }

        // Offline / network failure fallback
        if (cached != null && cached.articles.isNotEmpty()) {
            activeBriefing = cached
            return cached
        }
        return null
    }

    /**
     * Restores the active briefing back to today's briefing.
     */
    fun restoreTodayBriefing(): DailyBriefing {
        activeBriefing = todayBriefing
        return activeBriefing
    }

    /**
     * Searches articles within the current active briefing across title, category,
     * headline facts, background, takeaways, mnemonics, static GK, and syllabus mappings.
     */
    fun searchArticles(query: String, examFilter: String = "ALL"): List<Article> {
        val baseArticles = getArticlesByFilter(examFilter)
        val q = query.trim().lowercase()
        if (q.isBlank()) return baseArticles

        return baseArticles.filter { article ->
            article.title.lowercase().contains(q) ||
            article.category.lowercase().contains(q) ||
            article.headlineFact.lowercase().contains(q) ||
            article.background.lowercase().contains(q) ||
            article.longTermImpact.lowercase().contains(q) ||
            article.revisionLine.lowercase().contains(q) ||
            article.mnemonic.lowercase().contains(q) ||
            article.keyFactsFigures.any { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) } ||
            article.staticGkLinkage.any { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) } ||
            article.relatedStaticRevision.any { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) } ||
            article.upscData?.gsPaper?.lowercase()?.contains(q) == true ||
            article.upscData?.prelimsFacts?.any { it.lowercase().contains(q) } == true ||
            article.upscData?.mainsAnalysis?.lowercase()?.contains(q) == true ||
            article.sscIbpsFacts.any { it.key.lowercase().contains(q) || it.value.lowercase().contains(q) }
        }
    }

    fun getArticlesByFilter(examFilter: String): List<Article> {
        val all = activeBriefing.articles
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

    fun getRecalls(): List<RecallItem> = activeBriefing.recalls

    fun toggleBookmark(articleId: String): Boolean {
        val article = activeBriefing.articles.find { it.id == articleId }
        article?.let {
            it.isBookmarked = !it.isBookmarked
            return it.isBookmarked
        }
        return false
    }

    /**
     * Syncs current bookmarks and read articles with the cloud backend if authenticated.
     */
    suspend fun syncUserCloudProgress(readArticleIds: List<String> = emptyList()): com.example.kurrent.model.UserStats? {
        val token = AuthManager.getAuthToken() ?: return null
        val bookmarkedIds = activeBriefing.articles.filter { it.isBookmarked }.map { it.id }
        val req = com.example.kurrent.model.SyncProgressRequest(
            currentDate = activeBriefing.date,
            readArticleIds = readArticleIds,
            bookmarkedArticleIds = bookmarkedIds
        )
        val res = ApiClient.syncProgress(req, token)
        if (res.isSuccess) {
            val stats = res.getOrNull()?.stats
            if (stats != null) {
                AuthManager.saveStats(stats)
                // Apply cloud bookmarks if any
                val bmSet = stats.cloudBookmarks.toSet()
                for (a in activeBriefing.articles) {
                    if (bmSet.contains(a.id)) {
                        a.isBookmarked = true
                    }
                }
            }
            return stats
        }
        return null
    }

    /**
     * Returns total number of daily briefings cached locally on device disk for offline commute.
     */
    fun getOfflineCachedDaysCount(): Int {
        val dir = cacheDir ?: return 1
        val files = dir.listFiles { f -> f.name.startsWith("briefing_") && f.name.endsWith(".json") }
        return (files?.size ?: 1).coerceAtLeast(1)
    }

    /**
     * Aggregates all articles across cached briefings for monthly PDF compilation.
     */
    fun getAllCachedArticles(): List<Article> {
        val dir = cacheDir ?: return activeBriefing.articles
        val files = dir.listFiles { f -> f.name.startsWith("briefing_") && f.name.endsWith(".json") } ?: return activeBriefing.articles
        val list = mutableListOf<Article>()
        val seenIds = mutableSetOf<String>()

        for (a in activeBriefing.articles) {
            if (seenIds.add(a.id)) list.add(a)
        }

        for (file in files) {
            try {
                val briefing = gson.fromJson(file.readText(), DailyBriefing::class.java)
                for (a in briefing.articles) {
                    if (seenIds.add(a.id)) {
                        list.add(a)
                    }
                }
            } catch (ignored: Exception) {}
        }
        return if (list.isNotEmpty()) list else activeBriefing.articles
    }

    /**
     * Gets today's active briefing articles.
     */
    fun getArticles(): List<Article> = activeBriefing.articles

    /**
     * Downloads recent briefings into local storage for seamless subway/airplane commute mode.
     */
    suspend fun preFetchRecentBriefings(daysCount: Int = 14, onComplete: ((Int) -> Unit)? = null): Int {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        for (i in 0 until daysCount) {
            val dateStr = sdf.format(cal.time)
            try {
                val b = ApiClient.getBriefingByDate(dateStr)
                if (b != null && b.articles.isNotEmpty()) {
                    saveToDisk(dateStr, b)
                }
            } catch (ignored: Exception) {}
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val count = getOfflineCachedDaysCount()
        onComplete?.invoke(count)
        return count
    }
}
