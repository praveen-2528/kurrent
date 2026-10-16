#!/usr/bin/env python3
"""
Seed High-Yield Static Syllabus Cards into Kurrent EC2 SQLite Database.
Adds dedicated briefing for 2026-10-16 containing:
1. Crops & Cropping Patterns in India (Geography & Agriculture)
2. Causes & Catalysts of the 1857 Revolt (Modern Indian History)
3. Ganga River Drainage System & Panch Prayag (Physical Geography)
4. 37th National Games in Goa (Sports & Current Affairs)
5. Landmark Supreme Court Cases & Constitutional Doctrines (Polity & Governance)
"""

import sqlite3
import json
import os
import sys

DB_PATH = os.environ.get("KURRENT_DB_PATH", os.path.join(os.path.dirname(os.path.abspath(__file__)), "kurrent.db"))

briefing_payload = {
    "date": "2026-10-16",
    "edition": 144,
    "articles": [
        {
            "id": "art_crops",
            "title": "Cropping Patterns, Millets & Agro-Climatic Zones in India",
            "category": "Geography & Agriculture",
            "exams": ["UPSC", "SSC_CGL", "STATE_PSC"],
            "importance": "HIGH",
            "read_time": "3 mins",
            "source_name": "ICAR & Ministry of Agriculture and Farmers Welfare",
            "source_url": "https://agricoop.gov.in",
            "headline_fact": "India classifies agriculture into Kharif, Rabi, and Zaid seasons, with Millets (Shree Anna) requiring less than 40% of the water consumed by paddy and wheat.",
            "background": "India's diverse agro-climatic zones support three distinct cropping cycles. Under the National Food Security Mission and International Year of Millets, transitioning water-stressed regions from water-guzzling crops to coarse cereals is a national priority.",
            "long_term_impact": "Mitigates groundwater depletion in the north-western aquifer belt, enhances climate resilience against El Niño droughts, and strengthens nutritional security.",
            "key_facts_figures": [
                {"key": "Kharif Season (Monsoon)", "value": "Sown June-July, harvested Sept-Oct. Crops: Rice, Maize, Jowar, Bajra, Cotton, Jute, Groundnut, Soybean."},
                {"key": "Rabi Season (Winter)", "value": "Sown Oct-Dec, harvested April-June. Crops: Wheat, Barley, Gram, Mustard, Peas, Linseed. Benefits from Western Disturbances."},
                {"key": "Zaid Season (Summer)", "value": "Short gap crop grown March-June between Rabi and Kharif. Crops: Watermelon, Cucumber, Vegetables, Fodder."},
                {"key": "Shree Anna (Millets)", "value": "C4 photosynthetic crops (Bajra, Jowar, Ragi) having high carbon-fixation, low water footprint (~400mm vs 2000mm for paddy), and high iron/calcium content."}
            ],
            "static_gk_linkage": [
                {"key": "Green Revolution Pioneer", "value": "Dr. M.S. Swaminathan (Father of Green Revolution in India) and Dr. Norman Borlaug (Global). Introduced High-Yielding Variety (HYV) dwarf wheat and rice seeds in the mid-1960s."},
                {"key": "Operation Greens", "value": "Launched on the lines of Operation Flood to stabilize supply of TOP (Tomato, Onion, Potato) and 22 perishable crops without price volatility."}
            ],
            "related_static_revision": [
                {"key": "Soil Requirements", "value": "Paddy requires heavy clayey/alluvial soil with standing water; Cotton requires Regur (Black) soil and 210 frost-free days; Tea requires well-drained acidic soil on hill slopes."}
            ],
            "distractor_traps": [
                "Assuming Mustard and Groundnut are both Kharif: Groundnut is Kharif (rainy season), while Mustard is strictly Rabi (winter).",
                "Confusing Zaid as an autumn harvest: Zaid is the hot dry summer gap between March and June."
            ],
            "mnemonic": "\"Rabi Loves Winter Gold\" — Rabi = Wheat, Gram, Mustard, Barley. Remember Groundnut = Kharif, Mustard = Rabi.",
            "upsc": {
                "gs_paper": "GS-3 (Major Crops, Cropping Patterns, Irrigation & Food Security)",
                "prelims_facts": [
                    "Millets are C4 photosynthetic plants with superior water-use efficiency and drought resistance.",
                    "Western Disturbances bring winter rainfall crucial for Rabi wheat yield in North-West India.",
                    "Sugarcane is a long-duration crop taking 10-15 months to mature, spanning across seasons."
                ],
                "mains_analysis": "Crop diversification from water-intensive rice-wheat monoculture towards climate-resilient coarse cereals is imperative for groundwater sustainability and farmer income stabilization in the Indo-Gangetic plains.",
                "mains_question": "Analyze the economic and ecological rationale for promoting millets (Shree Anna) in drought-prone agro-climatic regions of India."
            },
            "ssc_ibps_facts": [
                {"key": "Major Kharif Crop", "value": "Rice / Paddy"},
                {"key": "Major Rabi Oilseed", "value": "Mustard"},
                {"key": "Leading Millet State", "value": "Rajasthan (Bajra), Karnataka (Ragi), Maharashtra (Jowar)"},
                {"key": "MSP Determination Body", "value": "Commission for Agricultural Costs and Prices (CACP)"}
            ],
            "revision_line": "Kharif crops (Rice, Cotton, Groundnut) rely on monsoon rains; Rabi crops (Wheat, Mustard, Gram) mature with winter showers and Western Disturbances."
        },
        {
            "id": "art_1857",
            "title": "Causes and Catalysts of the 1857 Revolt: The First War of Independence",
            "category": "Modern Indian History",
            "exams": ["UPSC", "SSC_CGL", "STATE_PSC"],
            "importance": "HIGH",
            "read_time": "3 mins",
            "source_name": "NCERT Modern India & Bipan Chandra",
            "source_url": "https://ncert.nic.in",
            "headline_fact": "The 1857 revolt ignited on May 10, 1857 at Meerut, triggered by greased cartridges but rooted in deep political, economic, socio-religious, and military grievances under Lord Dalhousie and Lord Canning.",
            "background": "A century of British East India Company rule culminated in widespread discontent. Lord Dalhousie's aggressive annexation policy through the Doctrine of Lapse and the annexation of Awadh in 1856 transformed simmering regional resentments into a coordinated uprising.",
            "long_term_impact": "Ended the British East India Company rule; Queen Victoria's Proclamation of 1858 transferred direct governance to the British Crown, replaced Governor-General with Viceroy (Lord Canning), and reshuffled army racial composition under the Peel Commission.",
            "key_facts_figures": [
                {"key": "Political Discontent", "value": "Dalhousie's Doctrine of Lapse annexed Satara (1848), Sambalpur (1849), Jhansi (1853), and Nagpur (1854). Nana Sahib (adopted son of Peshwa Baji Rao II) was denied pension."},
                {"key": "Annexation of Awadh (1856)", "value": "Annexed on pretext of 'maladministration' by Dalhousie, deeply outraging the Bengal Army sepoys since 75,000 sepoys hailed from Awadh (known as the 'Nursery of Bengal Army')."},
                {"key": "Socio-Religious & Military Acts", "value": "General Service Enlistment Act (1856) required sepoys to serve overseas across the 'Kala Pani'. Religious Disabilities Act (1856) protected property rights of Christian converts."},
                {"key": "Immediate Trigger", "value": "Introduction of Enfield P-53 rifle cartridges greased with cow and pig fat, requiring biting before loading. On March 29, 1857, Mangal Pandey mutinied at Barrackpore."}
            ],
            "static_gk_linkage": [
                {"key": "Governor-General during 1857", "value": "Lord Canning was Governor-General (1856–1858) and became the first Viceroy of India (1858–1862)."},
                {"key": "Key Centers & Leaders", "value": "Delhi: General Bakht Khan & Bahadur Shah Zafar; Kanpur: Nana Sahib & Tantia Tope; Lucknow: Begum Hazrat Mahal; Jhansi: Rani Lakshmibai; Bihar (Arrah): Kunwar Singh."}
            ],
            "related_static_revision": [
                {"key": "Peel Commission (1858)", "value": "Recommended army reorganization based on 'divide and rule'—martial vs non-martial races theory, deliberately reducing Indian proportion in the military."}
            ],
            "distractor_traps": [
                "Assuming Lord Dalhousie was Governor-General during the revolt: Dalhousie introduced the Doctrine of Lapse, but Lord Canning was the Governor-General when the revolt erupted in 1857.",
                "Thinking Awadh was annexed under Doctrine of Lapse: Awadh was annexed in 1856 on charges of 'misgovernance' (maladministration), NOT the Doctrine of Lapse."
            ],
            "mnemonic": "\"Can Canning Contain the Meerut Spark?\" — Remember Lord Canning was Governor-General during 1857.",
            "upsc": {
                "gs_paper": "GS-1 (Modern Indian History: Freedom Struggle & Its Stages)",
                "prelims_facts": [
                    "Lord Canning was Governor-General during the 1857 revolt, not Dalhousie.",
                    "Awadh was annexed in 1856 by Dalhousie on grounds of misgovernance, not under the Doctrine of Lapse.",
                    "General Service Enlistment Act 1856 mandated overseas military service for recruits."
                ],
                "mains_analysis": "The 1857 Revolt was neither purely a sepoy mutiny nor a fully unified national war of independence; it was a watershed anti-colonial rebellion of disparate feudal, agrarian, and military elements sharing common grievances against Company raj.",
                "mains_question": "Examine the socio-religious and military factors that transformed a garrison mutiny at Meerut into a widespread agrarian and civilian rebellion in 1857."
            },
            "ssc_ibps_facts": [
                {"key": "Date of Outbreak", "value": "May 10, 1857 (Meerut)"},
                {"key": "Barrackpore Hero", "value": "Mangal Pandey (34th Bengal Native Infantry, March 29, 1857)"},
                {"key": "First Viceroy of India", "value": "Lord Canning (Government of India Act 1858)"},
                {"key": "Bihar Leader", "value": "Kunwar Singh (Jagdishpur/Arrah)"}
            ],
            "revision_line": "Lord Canning governed during 1857; greased Enfield cartridges triggered the spark, but Awadh's 1856 annexation and Dalhousie's Doctrine of Lapse fueled the fire."
        },
        {
            "id": "art_ganga",
            "title": "Ganga River Drainage System, Panch Prayag & Tributaries",
            "category": "Physical Geography & Rivers",
            "exams": ["UPSC", "SSC_CGL", "STATE_PSC"],
            "importance": "HIGH",
            "read_time": "3 mins",
            "source_name": "Survey of India & Ministry of Jal Shakti (NMCG)",
            "source_url": "https://nmcg.nic.in",
            "headline_fact": "River Ganga stretches 2,525 km, formed at Devprayag by the confluence of Alaknanda and Bhagirathi, with the Gomti being its only major tributary originating entirely in the plains.",
            "background": "The Ganga basin is the largest river basin in India, covering 26% of the country's landmass. The Alaknanda river acts as the central spine along which the sacred Panch Prayag (five sacred confluences) are arranged in precise descending elevation order in Garhwal Himalayas.",
            "long_term_impact": "Provides water security to over 500 million people, supports the agricultural breadbasket of northern India, and houses the world's largest mangrove delta (Sundarbans).",
            "key_facts_figures": [
                {"key": "Panch Prayag Order (Descending)", "value": "1. Vishnuprayag (Alaknanda + Dhauliganga) → 2. Nandprayag (Alaknanda + Nandakini) → 3. Karnaprayag (Alaknanda + Pindar) → 4. Rudraprayag (Alaknanda + Mandakini) → 5. Devprayag (Alaknanda + Bhagirathi)."},
                {"key": "Origin Streams", "value": "Bhagirathi rises from Gangotri Glacier at Gaumukh (3,900m); Alaknanda rises from Satopanth Glacier above Badrinath."},
                {"key": "Left-Bank Tributaries", "value": "Ramganga, Gomti (originates at Fulhar Lake / Gomat Taal, Pilibhit, UP), Ghaghara (largest tributary by volume, enters from Nepal), Gandak, Kosi ('Sorrow of Bihar'), Mahananda."},
                {"key": "Right-Bank Tributaries", "value": "Yamuna (longest tributary, 1,376 km, joins at Prayagraj), Son (originates at Amarkantak plateau in MP, flows north to join near Patna), Punpun, and Damodar (joins Hooghly distributary)."}
            ],
            "static_gk_linkage": [
                {"key": "Sundarbans Delta", "value": "World's largest delta formed by Ganga-Brahmaputra-Meghna, home to the Royal Bengal Tiger and Halophyte Mangrove vegetation (Sundari trees)."},
                {"key": "National River of India", "value": "Ganga was declared the National River of India in November 2008; Gangetic Dolphin declared National Aquatic Animal in 2009."}
            ],
            "related_static_revision": [
                {"key": "Farakka Barrage", "value": "Built across Ganga in West Bengal to divert 40,000 cusecs of water into Hooghly River to flush silt and keep Kolkata Port navigable."}
            ],
            "distractor_traps": [
                "Believing Gomti originates in the Himalayas: Gomti is the ONLY major tributary originating in the plains (Fulhar Lake, UP).",
                "Confusing Mandakini with Nandakini: Nandakini meets at Nandprayag; Mandakini (flowing from Kedarnath) meets at Rudraprayag."
            ],
            "mnemonic": "\"Very Nice Kids Read Devotionally\" — Vishnu (Dhauli), Nand (Nandakini), Karna (Pindar), Rudra (Mandakini), Dev (Bhagirathi).",
            "upsc": {
                "gs_paper": "GS-1 (Indian Physical Geography: River Systems and Drainage Basins)",
                "prelims_facts": [
                    "River Gomti originates from Gomat Taal (Fulhar Lake) in the plains of Uttar Pradesh, not the Himalayas.",
                    "Son river flows northwards from Amarkantak plateau as a major right-bank tributary.",
                    "At Devprayag, Bhagirathi and Alaknanda unite to formally assume the name Ganga."
                ],
                "mains_analysis": "The perennial nature of Himalayan tributaries combined with seasonal peninsular flows requires integrated river basin management to mitigate recurring flash floods in eastern Uttar Pradesh and Bihar.",
                "mains_question": "Describe the drainage characteristics of the Ganga river basin and discuss why river Kosi undergoes frequent course avulsions."
            },
            "ssc_ibps_facts": [
                {"key": "Total Length", "value": "2,525 km (Longest river within Indian territory)"},
                {"key": "National Aquatic Animal", "value": "Gangetic River Dolphin (Platanista gangetica)"},
                {"key": "Only Plains-Origin Tributary", "value": "Gomti (originates in Pilibhit, UP)"},
                {"key": "Longest Tributary", "value": "Yamuna (1,376 km)"}
            ],
            "revision_line": "Panch Prayag descending confluences: Vishnuprayag, Nandprayag, Karnaprayag, Rudraprayag, and Devprayag (where Alaknanda meets Bhagirathi to become Ganga)."
        },
        {
            "id": "art_games",
            "title": "37th National Games Goa: Champions, Mascot & High-Yield Sports GK",
            "category": "Sports & Current Affairs",
            "exams": ["UPSC", "SSC_CGL", "IBPS_MAINS"],
            "importance": "HIGH",
            "read_time": "2 mins",
            "source_name": "Indian Olympic Association (IOA) & PIB Sports",
            "source_url": "https://pib.gov.in/sports",
            "headline_fact": "Maharashtra topped the medal tally with 228 medals to lift the Raja Bhalindra Singh Trophy at the 37th National Games in Goa, featuring mascot 'Moga'.",
            "background": "The 37th edition was hosted across 5 Goan cities (Panaji, Margao, Mapusa, Ponda, and Vasco) alongside cycling and golf events in New Delhi. Over 10,000 athletes competed across 43 sports disciplines.",
            "long_term_impact": "Catalyzed world-class sports infrastructure along India's coastal belt and served as the primary qualification and talent-identification ground for the Asian and Olympic cycles.",
            "key_facts_figures": [
                {"key": "Mascot & Motto", "value": "Mascot was 'Moga' (meaning 'Love' in Konkani, depicted as an Indian Bison / Gaur). Official Motto: 'Get Set Goa'."},
                {"key": "Overall Champion", "value": "Maharashtra won the Raja Bhalindra Singh Trophy for the first time since 1994, amassing 228 medals (80 Gold, 69 Silver, 79 Bronze)."},
                {"key": "Runner-Up", "value": "Services Sports Control Board (SSCB) finished second with 66 Gold medals (126 total); Haryana secured third place with 62 Gold medals."},
                {"key": "New & Indigenous Disciplines", "value": "Included Sqay (Kashmiri martial art), Rollball, Sepaktakraw, Kalaripayattu, Pencak Silat, Mini Golf, and Beach Football."}
            ],
            "static_gk_linkage": [
                {"key": "Origin of National Games", "value": "Started as the 'Indian Olympic Games' in Lahore in 1924 to select athletes for the Paris Olympics. Renamed National Games at the 1940 Bombay edition."},
                {"key": "Raja Bhalindra Singh Trophy", "value": "Instituted in honor of IOC member and IOA President Raja Bhalindra Singh, awarded to the overall champion state team."}
            ],
            "related_static_revision": [
                {"key": "Next Host (38th National Games)", "value": "Awarded to Uttarakhand."}
            ],
            "distractor_traps": [
                "Believing Services (SSCB) won the overall trophy: SSCB won the previous 4 editions, but Maharashtra ended their streak in Goa.",
                "Confusing the mascot: Mascot is 'Moga' (Indian Bison/Gaur), NOT Veera or Perry."
            ],
            "mnemonic": "\"MOGA in GOA made MAHA-Champion\" — Mascot Moga, Host Goa, Champion Maharashtra.",
            "upsc": {
                "gs_paper": "Prelims General Studies & Current Events",
                "prelims_facts": [
                    "Mascot 'Moga' represents the Indian Bison (Gaur), which is also the State Animal of Goa.",
                    "Maharashtra captured the Raja Bhalindra Singh Trophy for the first time in 29 years.",
                    "Traditional martial arts like Sqay and Kalaripayattu were incorporated into official medal events."
                ],
                "mains_analysis": "Promotion of traditional and indigenous martial arts within national games strengthens cultural heritage preservation and grassroots sports participation.",
                "mains_question": "Evaluate the role of National Games in decentralizing sports infrastructure and talent scouting across tier-2 and coastal regions in India."
            },
            "ssc_ibps_facts": [
                {"key": "Host State", "value": "Goa (37th edition)"},
                {"key": "Mascot", "value": "Moga (Indian Bison)"},
                {"key": "Trophy Winner", "value": "Maharashtra (80 Gold, 228 Total)"},
                {"key": "Upcoming 38th Host", "value": "Uttarakhand"}
            ],
            "revision_line": "Goa hosted the 37th National Games featuring mascot Moga; Maharashtra won the Raja Bhalindra Singh Trophy with 228 medals."
        },
        {
            "id": "art_sc_cases",
            "title": "Landmark Supreme Court Cases: Basic Structure, Privacy & Article 21",
            "category": "Indian Polity & Constitution",
            "exams": ["UPSC", "SSC_CGL", "STATE_PSC"],
            "importance": "HIGH",
            "read_time": "3 mins",
            "source_name": "Supreme Court Reports (SCR) & Constitution of India",
            "source_url": "https://sci.gov.in",
            "headline_fact": "Kesavananda Bharati (1973) established the Basic Structure Doctrine, Maneka Gandhi (1978) created the Golden Triangle of Fundamental Rights, and Puttaswamy (2017) recognized Privacy under Article 21.",
            "background": "Over seven decades, the Supreme Court of India transformed from a strict positivist court under A.K. Gopalan into an activist guardian of civil liberties through landmark constitutional interpretations.",
            "long_term_impact": "Checked legislative omnipotence, preserved judicial review and secularism as unamendable constitutional values, and established that Fundamental Rights are interdependent and co-equal.",
            "key_facts_figures": [
                {"key": "Kesavananda Bharati (1973)", "value": "Largest 13-judge constitutional bench ruled 7:6 that Parliament can amend any part of the Constitution under Article 368, but cannot alter or destroy its 'Basic Structure'."},
                {"key": "Maneka Gandhi v. UOI (1978)", "value": "Overruled A.K. Gopalan; held that 'procedure established by law' under Article 21 must be just, fair, and reasonable (substantive due process). Formulated the Golden Triangle of Articles 14, 19, and 21."},
                {"key": "Minerva Mills v. UOI (1980)", "value": "Struck down Section 55 of 42nd Amendment; held that the harmony and balance between Fundamental Rights (Part III) and Directive Principles (Part IV) is part of basic structure."},
                {"key": "Justice K.S. Puttaswamy (2017)", "value": "Unanimous 9-judge bench ruled that the Right to Privacy is an intrinsic fundamental right guaranteed under Article 21 and Part III."}
            ],
            "static_gk_linkage": [
                {"key": "First Constitutional Amendment (1951)", "value": "Challenged in Shankari Prasad (1951), where SC initially held Parliament had unlimited power to amend Fundamental Rights."},
                {"key": "Golaknath Case (1967)", "value": "11-judge bench ruled 6:5 that Fundamental Rights are transcendental and immutable, leading to the 24th Amendment and ultimately Kesavananda Bharati."}
            ],
            "related_static_revision": [
                {"key": "Navtej Singh Johar (2018)", "value": "Decriminalized consensual same-sex relations by striking down part of Section 377 IPC on grounds of constitutional morality."},
                {"key": "Shayara Bano (2017)", "value": "Declared the practice of Talaq-e-Biddat (instant triple talaq) unconstitutional under Article 14."}
            ],
            "distractor_traps": [
                "Assuming 'Basic Structure' is written anywhere in the Constitution: The term 'Basic Structure' is NOT mentioned anywhere in the Constitution text; it is an entirely judicial doctrine.",
                "Thinking due process was part of original Article 21: The drafting committee explicitly chose 'procedure established by law', which the SC judicially broadened to 'due process' in Maneka Gandhi (1978)."
            ],
            "mnemonic": "\"K-M-P: Kerala built Structure, Maneka gave Liberty, Puttaswamy locked Privacy\" — Kesavananda, Maneka, Puttaswamy.",
            "upsc": {
                "gs_paper": "GS-2 (Indian Constitution: Significant Provisions, Basic Structure & Judiciary)",
                "prelims_facts": [
                    "The phrase 'Basic Structure' is not defined or mentioned in the text of the Constitution of India.",
                    "Maneka Gandhi (1978) established that Articles 14, 19, and 21 form a mutually inclusive Golden Triangle.",
                    "A 13-judge bench (largest in Indian judicial history) decided Kesavananda Bharati on April 24, 1973."
                ],
                "mains_analysis": "The Basic Structure doctrine acts as an institutional brake against electoral authoritarianism while preserving constitutional identity and democratic equilibrium.",
                "mains_question": "Discuss how the doctrine of Basic Structure evolved and examine whether it has altered the balance of power between Parliament and the Judiciary."
            },
            "ssc_ibps_facts": [
                {"key": "Basic Structure Case", "value": "Kesavananda Bharati (1973, 13 Judges)"},
                {"key": "Right to Privacy Case", "value": "K.S. Puttaswamy (2017, 9 Judges)"},
                {"key": "Golden Triangle Articles", "value": "Articles 14, 19, and 21"},
                {"key": "Abolition of Untouchability", "value": "Article 17"}
            ],
            "revision_line": "Kesavananda Bharati founded the Basic Structure doctrine; Maneka Gandhi established the Golden Triangle (Arts 14, 19, 21); Puttaswamy affirmed Privacy as a fundamental right."
        }
    ],
    "recalls": [
        {
            "id": "rec_crops",
            "date": "2026-10-16",
            "category": "Geography & Agriculture",
            "title": "Cropping Patterns & Millets (Shree Anna) Dynamics",
            "fact": "Kharif crops (Paddy, Cotton, Groundnut) rely on monsoon rains; Rabi crops (Wheat, Mustard, Gram) depend on winter showers; Millets consume <40% water compared to paddy.",
            "source_url": "https://agricoop.gov.in"
        },
        {
            "id": "rec_1857",
            "date": "2026-10-16",
            "category": "Modern Indian History",
            "title": "1857 Revolt Outbreak & Lord Canning's Administration",
            "fact": "Lord Canning was Governor-General during 1857 (not Dalhousie); spark ignited at Meerut on May 10, 1857, following Awadh's 1856 annexation on grounds of maladministration.",
            "source_url": "https://ncert.nic.in"
        },
        {
            "id": "rec_ganga",
            "date": "2026-10-16",
            "category": "Physical Geography & Rivers",
            "title": "Panch Prayag Order & Ganga Plains-Origin Tributary",
            "fact": "Panch Prayag descending order: Vishnuprayag, Nandprayag, Karnaprayag, Rudraprayag, Devprayag (Alaknanda meets Bhagirathi); Gomti is the only major tributary originating in the plains (Fulhar Lake).",
            "source_url": "https://nmcg.nic.in"
        },
        {
            "id": "rec_games",
            "date": "2026-10-16",
            "category": "Sports & Current Affairs",
            "title": "37th National Games Goa & Raja Bhalindra Trophy",
            "fact": "Goa hosted the 37th National Games with mascot 'Moga' (Indian Bison); Maharashtra won the Raja Bhalindra Singh Trophy with 228 medals (80 Gold).",
            "source_url": "https://pib.gov.in/sports"
        },
        {
            "id": "rec_sc_cases",
            "date": "2026-10-16",
            "category": "Indian Polity & Constitution",
            "title": "Basic Structure Doctrine & Golden Triangle of Rights",
            "fact": "Kesavananda Bharati (1973, 13 judges) ruled Parliament cannot alter the Basic Structure; Maneka Gandhi (1978) forged the Golden Triangle (Arts 14, 19, 21); 'Basic Structure' is not in constitutional text.",
            "source_url": "https://sci.gov.in"
        }
    ],
    "quizzes": [
        {
            "id": "q_crops",
            "article_ref": "art_crops",
            "exam": "UPSC",
            "question": "With reference to cropping seasons and patterns in India, consider the following statements:\n1. Mustard and Linseed are Kharif crops sown with the onset of the southwest monsoon.\n2. Millets are C4 plants that exhibit higher water-use efficiency compared to rice and wheat.\n3. The Zaid cropping season falls between the Rabi and Kharif seasons during the summer months.\nWhich of the statements given above are correct?",
            "options": ["1 and 2 only", "2 and 3 only", "1 and 3 only", "1, 2 and 3"],
            "correct_index": 1,
            "explanation": "Statement 1 is incorrect because Mustard and Linseed are Rabi (winter) crops, not Kharif. Statements 2 and 3 are correct: Millets are high-efficiency C4 crops and Zaid is the summer crop season."
        },
        {
            "id": "q_1857",
            "article_ref": "art_1857",
            "exam": "UPSC",
            "question": "With reference to the 1857 Revolt in India, consider the following statements:\n1. Lord Dalhousie was the Governor-General of India when the rebellion erupted in May 1857.\n2. The kingdom of Awadh was annexed in 1856 under the provisions of the Doctrine of Lapse.\n3. The General Service Enlistment Act of 1856 mandated that recruits must serve overseas if ordered.\nWhich of the statements given above is/are correct?",
            "options": ["1 and 2 only", "3 only", "2 and 3 only", "1, 2 and 3"],
            "correct_index": 1,
            "explanation": "Statements 1 and 2 are classic exam traps. Statement 1 is incorrect because Lord Canning was Governor-General during 1857 (Dalhousie departed in 1856). Statement 2 is incorrect because Awadh was annexed on the pretext of 'misgovernance/maladministration', not under the Doctrine of Lapse. Statement 3 is correct."
        },
        {
            "id": "q_ganga",
            "article_ref": "art_ganga",
            "exam": "UPSC",
            "question": "Consider the following statements regarding the Ganga river drainage system:\n1. At Devprayag, river Alaknanda joins river Bhagirathi to formally form the Ganga.\n2. River Gomti is the only major tributary of Ganga that originates in the plains rather than the Himalayas.\n3. River Son is a major left-bank tributary originating from the Satpura range.\nWhich of the statements given above are correct?",
            "options": ["1 and 2 only", "2 and 3 only", "1 and 3 only", "1, 2 and 3"],
            "correct_index": 0,
            "explanation": "Statement 1 is correct (Devprayag forms Ganga). Statement 2 is correct (Gomti originates from Fulhar Lake in Pilibhit, UP). Statement 3 is incorrect because River Son is a RIGHT-BANK tributary (flowing northwards from Amarkantak plateau in the Maikala range)."
        },
        {
            "id": "q_games",
            "article_ref": "art_games",
            "exam": "SSC_CGL",
            "question": "Which state won the prestigious Raja Bhalindra Singh Trophy for overall championship at the 37th National Games held in Goa?",
            "options": ["Haryana", "Services Sports Control Board (SSCB)", "Maharashtra", "Kerala"],
            "correct_index": 2,
            "explanation": "Maharashtra topped the medal tally with 228 medals (80 Gold, 69 Silver, 79 Bronze) to capture the Raja Bhalindra Singh Trophy, breaking SSCB's winning streak."
        },
        {
            "id": "q_sc_cases",
            "article_ref": "art_sc_cases",
            "exam": "UPSC",
            "question": "With reference to constitutional jurisprudence in India, consider the following statements:\n1. The phrase 'Basic Structure' is explicitly defined in Article 368 of the Constitution of India.\n2. In Maneka Gandhi (1978), the Supreme Court ruled that Articles 14, 19, and 21 form an interdependent 'Golden Triangle'.\n3. In Justice K.S. Puttaswamy (2017), a 9-judge bench declared the Right to Privacy as an intrinsic part of Article 21.\nWhich of the statements given above are correct?",
            "options": ["1 and 2 only", "2 and 3 only", "1 and 3 only", "1, 2 and 3"],
            "correct_index": 1,
            "explanation": "Statement 1 is incorrect because the term 'Basic Structure' is nowhere mentioned or defined in the text of the Constitution; it was judicially created in Kesavananda Bharati (1973). Statements 2 and 3 are correct."
        }
    ]
}


def main():
    print(f"Connecting to Kurrent database at: {DB_PATH}")
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS briefings (
            date TEXT PRIMARY KEY,
            edition INTEGER,
            payload_json TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.execute("""
        INSERT INTO briefings (date, edition, payload_json)
        VALUES (?, ?, ?)
        ON CONFLICT(date) DO UPDATE SET
            edition = excluded.edition,
            payload_json = excluded.payload_json,
            created_at = CURRENT_TIMESTAMP
    """, (briefing_payload["date"], briefing_payload["edition"], json.dumps(briefing_payload)))
    conn.commit()
    print(f"[OK] Successfully seeded Edition #{briefing_payload['edition']} ({briefing_payload['date']}) into kurrent.db!")
    print(f"  Articles seeded: {len(briefing_payload['articles'])}")
    print(f"  Recalls seeded: {len(briefing_payload['recalls'])}")
    print(f"  Quizzes seeded: {len(briefing_payload['quizzes'])}")
    conn.close()

if __name__ == "__main__":
    main()
