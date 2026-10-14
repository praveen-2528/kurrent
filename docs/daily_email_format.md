# Kurrent — Official Daily Email Format Specification

This is the exact structured format for the incoming morning email.

---

## Template Specification

```text
DATE: YYYY-MM-DD
EDITION: 143
TOTAL_TOPICS: 5
TOTAL_MCQS: 5
TOTAL_RECALLS: 4

[ARTICLE_START]
ID: art_01
TITLE: <Story Headline>
CATEGORY: <Syllabus Category>
EXAMS: SSC_CGL, IBPS_MAINS, UPSC
IMPORTANCE: HIGH
READ_TIME: 3 mins
SOURCE_NAME: <Official Source Name>
SOURCE_URL: <https://...>

[HEADLINE_FACT]
<1-2 line MCQ-ready headline statement>

[CONTEXT_AND_ANALYSIS]
BACKGROUND: <Why in news + ongoing story arc>
LONG_TERM_IMPACT: <Deep-dive economic, policy, and regulatory impact>

[KEY_FACTS_FIGURES]
<Fact Type 1> | <Verified Value 1>
<Fact Type 2> | <Verified Value 2>

[STATIC_GK_LINKAGE]
<Key>: <Static GK Backbone Fact>

[RELATED_STATIC_REVISION]
<Key>: <Adjacent Fact to Revise>

[DISTRACTOR_TRAPS]
TRAP_1: <Real Exam Confusion Point 1>
TRAP_2: <Real Exam Confusion Point 2>

[MNEMONIC]
<One-line memory hook>

[UPSC_SECTION]
GS_PAPER: <GS Paper>
PRELIMS_FACTS:
<Fact lines>
MAINS_ANALYSIS:
<Analysis lines>
MAINS_PRACTICE_QUESTION: <Question>

[SSC_IBPS_FACTS]
<Key>: <Value>

[REVISION_LINE]
<Single 5-second summary sentence>
[ARTICLE_END]

[RECALL_START]
R_ID: rec_01
DATE: YYYY-MM-DD
CATEGORY: <Category>
TITLE: <Past Story Title>
FACT: <High-yield summary>
SOURCE_URL: <https://...>
[RECALL_END]

[QUIZ_START]
Q_ID: q_01
ARTICLE_REF: art_01
EXAM: SSC_CGL
QUESTION: <Question Stem>
OPTION_A: <Option A>
OPTION_B: <Option B>
OPTION_C: <Option C>
OPTION_D: <Option D>
CORRECT: OPTION_B
EXPLANATION: <Detailed explanation>
[QUIZ_END]
```

---

## Fully Populated Sample for Testing

```text
DATE: 2026-10-15
EDITION: 143
TOTAL_TOPICS: 1
TOTAL_MCQS: 1
TOTAL_RECALLS: 1

[ARTICLE_START]
ID: art_01
TITLE: RBI Releases Revised Framework for Project Finance Lending
CATEGORY: Economy & Banking
EXAMS: SSC_CGL, IBPS_MAINS, UPSC
IMPORTANCE: HIGH
READ_TIME: 3 mins
SOURCE_NAME: Reserve Bank of India Press Release
SOURCE_URL: https://rbi.org.in/press/project-finance-2026

[HEADLINE_FACT]
RBI eases proposed project finance provisioning to a phased 2.5% from 5%, exempting Regional Rural Banks (RRBs).

[CONTEXT_AND_ANALYSIS]
BACKGROUND: In May 2024, RBI proposed a stringent 5% general provisioning for infrastructure projects under construction. Following banking sector representations on credit slowdown concerns, RBI calibrated the framework.
LONG_TERM_IMPACT: Lowers borrowing borrowing costs for greenfield infrastructure, mitigates sudden NPA spikes, while ensuring banks maintain adequate capital buffers against gestation delays.

[KEY_FACTS_FIGURES]
Final Provisioning Rate | 2.5% phased during construction
Draft Proposed Rate | 5.0% flat
Delinquency Threshold | Projects delayed > 2 years require mandatory re-rating
Applicability | Commercial Banks, NBFCs (Excludes RRBs)

[STATIC_GK_LINKAGE]
RBI Establishment: Set up on April 1, 1935 under the Reserve Bank of India Act, 1934 on the recommendations of the Hilton Young Commission.
Nationalisation of RBI: Nationalised on January 1, 1949.

[RELATED_STATIC_REVISION]
Basel III Norms: Capital Adequacy Ratio (CAR) mandate for Indian scheduled commercial banks is 11.5% (including Capital Conservation Buffer).

[DISTRACTOR_TRAPS]
TRAP_1: Confusing RRBs as covered under this rule; RRBs are explicitly EXEMPTED.
TRAP_2: Assuming the 2.5% provision is upfront; it is phased (1% in yr 1, 1.5% in yr 2, up to 2.5% at completion).

[MNEMONIC]
"2.5 to Thrive, Not 5 to Strive" - Remember RBI cut project finance provisioning from 5% to 2.5%.

[UPSC_SECTION]
GS_PAPER: GS-3 (Indian Economy & Infrastructure)
PRELIMS_FACTS:
- Phased general provisioning: 1% during year 1, 1.5% during year 2, up to 2.5% at completion.
- Applicable to all Scheduled Commercial Banks (excluding RRBs) and NBFCs.
- Infrastructure projects facing delays over 2 years require mandatory credit re-rating.
MAINS_ANALYSIS:
- Striking a prudent equilibrium between infrastructure financing and financial system stability.
- Prevention of asset-liability mismatches and early warning signals in long-gestation assets.
MAINS_PRACTICE_QUESTION: Examine how regulatory provisioning norms by the central bank influence India's private capital expenditure in infrastructure.

[SSC_IBPS_FACTS]
Regulatory Authority: Reserve Bank of India (RBI)
Headquarters: Mumbai, Maharashtra
Current Governor: Shaktikanta Das
Exempted Lenders: Regional Rural Banks (RRBs)

[REVISION_LINE]
RBI finalizes phased 2.5% project finance provisioning during construction while exempting RRBs.
[ARTICLE_END]

[RECALL_START]
R_ID: rec_01
DATE: 2026-10-08
CATEGORY: Defence & Security
TITLE: DRDO Flight-Tests Hypersonic Missile off Odisha Coast
FACT: Maiden test from Dr APJ Abdul Kalam Island achieved hypersonic velocity (> Mach 5) with a strike range exceeding 1,500 km.
SOURCE_URL: https://pib.gov.in/drdo-hypersonic
[RECALL_END]

[QUIZ_START]
Q_ID: q_01
ARTICLE_REF: art_01
EXAM: SSC_CGL
QUESTION: Under the revised RBI framework for project finance, which of the following financial institutions is exempt from the mandatory provisioning norms?
OPTION_A: Non-Banking Financial Companies (NBFCs)
OPTION_B: Regional Rural Banks (RRBs)
OPTION_C: Public Sector Commercial Banks
OPTION_D: Private Scheduled Commercial Banks
CORRECT: OPTION_B
EXPLANATION: Regional Rural Banks (RRBs) are explicitly exempt from the RBI's revised project finance provisioning guidelines.
[QUIZ_END]
```
