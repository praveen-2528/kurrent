"""
Kurrent Daily Email Parser Engine (Updated for Official Kurrent Template)
Parses structured incoming current affairs emails containing:
- Articles with Mnemonics, Traps, Context, Static GK Linkages, and GS Mapping
- Spaced Repetition Recalls (Past Story Reviews)
- Article-Referenced Quizzes with Explanations
"""

import re
import json
from datetime import datetime
from typing import Dict, Any, List


def parse_markdown_email(email_body: str) -> Dict[str, Any]:
    """
    Parses the official Kurrent formatted email into typed article objects,
    past story recalls, and daily quizzes.
    """
    date_match = re.search(r"DATE:\s*([\d-]+)", email_body)
    edition_match = re.search(r"EDITION:\s*(\d+)", email_body)

    date_str = date_match.group(1).strip() if date_match else datetime.utcnow().strftime("%Y-%m-%d")
    edition_num = int(edition_match.group(1).strip()) if edition_match else 1

    articles: List[Dict[str, Any]] = []
    recalls: List[Dict[str, Any]] = []
    quizzes: List[Dict[str, Any]] = []

    # 1. Parse Articles
    article_blocks = re.findall(r"\[ARTICLE_START\](.*?)\[ARTICLE_END\]", email_body, re.DOTALL)
    for block in article_blocks:
        art_id = _extract_field(r"ID:\s*(.*)", block, f"art_{len(articles)+1}")
        title = _extract_field(r"TITLE:\s*(.*)", block, "Untitled Current Affair")
        category = _extract_field(r"CATEGORY:\s*(.*)", block, "General Awareness")
        exams_str = _extract_field(r"EXAMS:\s*(.*)", block, "UPSC, SSC_CGL, IBPS_MAINS")
        exams = [e.strip() for e in exams_str.split(",") if e.strip()]
        importance = _extract_field(r"IMPORTANCE:\s*(.*)", block, "HIGH")
        read_time = _extract_field(r"READ_TIME:\s*(.*)", block, "3 mins")
        source_name = _extract_field(r"SOURCE_NAME:\s*(.*)", block, "")
        source_url = _extract_field(r"SOURCE_URL:\s*(.*)", block, "")

        # Headline Fact
        headline_fact = _extract_section(block, "HEADLINE_FACT")

        # Context and Analysis
        context_block = _extract_section(block, "CONTEXT_AND_ANALYSIS")
        background = _extract_field(r"BACKGROUND:\s*(.*?)(?=\nLONG_TERM_IMPACT:|\Z)", context_block, "")
        long_term_impact = _extract_field(r"LONG_TERM_IMPACT:\s*(.*)", context_block, "")

        # Key Facts & Figures (pipe-delimited)
        key_facts_figures = []
        facts_block = _extract_section(block, "KEY_FACTS_FIGURES")
        for line in facts_block.splitlines():
            line = line.strip()
            if "|" in line:
                parts = line.split("|", 1)
                key_facts_figures.append({"key": parts[0].strip(), "value": parts[1].strip()})

        # Static GK Linkage
        static_gk = []
        gk_block = _extract_section(block, "STATIC_GK_LINKAGE")
        for line in gk_block.splitlines():
            line = line.strip()
            if ":" in line:
                k, v = line.split(":", 1)
                static_gk.append({"key": k.strip(), "value": v.strip()})

        # Related Static Revision
        static_revision = []
        rev_block = _extract_section(block, "RELATED_STATIC_REVISION")
        for line in rev_block.splitlines():
            line = line.strip()
            if ":" in line:
                k, v = line.split(":", 1)
                static_revision.append({"key": k.strip(), "value": v.strip()})

        # Distractor Traps (Common exam confusion points)
        distractor_traps = []
        traps_block = _extract_section(block, "DISTRACTOR_TRAPS")
        for line in traps_block.splitlines():
            line = line.strip()
            if ":" in line:
                k, v = line.split(":", 1)
                distractor_traps.append(v.strip())
            elif line:
                distractor_traps.append(line)

        # Mnemonic (Memory hook)
        mnemonic = _extract_section(block, "MNEMONIC")

        # UPSC Section
        upsc_block = _extract_section(block, "UPSC_SECTION")
        upsc_data = None
        if upsc_block:
            gs_paper = _extract_field(r"GS_PAPER:\s*(.*)", upsc_block, "")
            mains_q = _extract_field(r"MAINS_PRACTICE_QUESTION:\s*(.*)", upsc_block, "")

            prelims_facts = []
            pf_match = re.search(r"PRELIMS_FACTS:\s*(.*?)(?=\nMAINS_ANALYSIS:|\Z)", upsc_block, re.DOTALL)
            if pf_match:
                prelims_facts = [line.lstrip("- ").strip() for line in pf_match.group(1).strip().splitlines() if line.strip()]

            mains_analysis = ""
            ma_match = re.search(r"MAINS_ANALYSIS:\s*(.*?)(?=\nMAINS_PRACTICE_QUESTION:|\Z)", upsc_block, re.DOTALL)
            if ma_match:
                mains_analysis = ma_match.group(1).strip()

            upsc_data = {
                "gs_paper": gs_paper,
                "prelims_facts": prelims_facts,
                "mains_analysis": mains_analysis,
                "mains_question": mains_q
            }

        # SSC / IBPS Facts
        ssc_facts = []
        ssc_block = _extract_section(block, "SSC_IBPS_FACTS")
        for line in ssc_block.splitlines():
            line = line.lstrip("- ").strip()
            if ":" in line:
                k, v = line.split(":", 1)
                ssc_facts.append({"key": k.strip(), "value": v.strip()})

        # Revision Line
        revision_line = _extract_section(block, "REVISION_LINE")

        articles.append({
            "id": art_id,
            "title": title,
            "category": category,
            "exams": exams,
            "importance": importance,
            "read_time": read_time,
            "source_name": source_name,
            "source_url": source_url,
            "headline_fact": headline_fact,
            "background": background,
            "long_term_impact": long_term_impact,
            "key_facts_figures": key_facts_figures,
            "static_gk_linkage": static_gk,
            "related_static_revision": static_revision,
            "distractor_traps": distractor_traps,
            "mnemonic": mnemonic,
            "upsc": upsc_data,
            "ssc_ibps_facts": ssc_facts,
            "revision_line": revision_line
        })

    # 2. Parse Spaced Repetition Recalls
    recall_blocks = re.findall(r"\[RECALL_START\](.*?)\[RECALL_END\]", email_body, re.DOTALL)
    for r_block in recall_blocks:
        r_id = _extract_field(r"R_ID:\s*(.*)", r_block, f"rec_{len(recalls)+1}")
        r_date = _extract_field(r"DATE:\s*(.*)", r_block, "")
        r_category = _extract_field(r"CATEGORY:\s*(.*)", r_block, "")
        r_title = _extract_field(r"TITLE:\s*(.*)", r_block, "")
        r_fact = _extract_field(r"FACT:\s*(.*)", r_block, "")
        r_url = _extract_field(r"SOURCE_URL:\s*(.*)", r_block, "")

        recalls.append({
            "id": r_id,
            "date": r_date,
            "category": r_category,
            "title": r_title,
            "fact": r_fact,
            "source_url": r_url
        })

    # 3. Parse Daily Quizzes
    quiz_blocks = re.findall(r"\[QUIZ_START\](.*?)\[QUIZ_END\]", email_body, re.DOTALL)
    for qb in quiz_blocks:
        qid = _extract_field(r"Q_ID:\s*(.*)", qb, f"q_{len(quizzes)+1}")
        art_ref = _extract_field(r"ARTICLE_REF:\s*(.*)", qb, "")
        exam = _extract_field(r"EXAM:\s*(.*)", qb, "ALL")

        q_text_match = re.search(r"QUESTION:\s*(.*?)(?=\nOPTION_A:|\Z)", qb, re.DOTALL)
        q_text = q_text_match.group(1).strip() if q_text_match else ""

        opt_a = _extract_field(r"OPTION_A:\s*(.*)", qb, "")
        opt_b = _extract_field(r"OPTION_B:\s*(.*)", qb, "")
        opt_c = _extract_field(r"OPTION_C:\s*(.*)", qb, "")
        opt_d = _extract_field(r"OPTION_D:\s*(.*)", qb, "")

        correct_raw = _extract_field(r"CORRECT:\s*(.*)", qb, "OPTION_A").upper()
        mapping = {"OPTION_A": 0, "OPTION_B": 1, "OPTION_C": 2, "OPTION_D": 3, "A": 0, "B": 1, "C": 2, "D": 3}
        correct_index = mapping.get(correct_raw, 0)

        explanation = _extract_field(r"EXPLANATION:\s*(.*)", qb, "")

        quizzes.append({
            "id": qid,
            "article_ref": art_ref,
            "exam": exam,
            "question": q_text,
            "options": [opt_a, opt_b, opt_c, opt_d],
            "correct_index": correct_index,
            "explanation": explanation
        })

    return {
        "date": date_str,
        "edition": edition_num,
        "articles": articles,
        "recalls": recalls,
        "quizzes": quizzes
    }


def _extract_section(block: str, section_name: str) -> str:
    pattern = rf"\[{section_name}\]\s*(.*?)(?=\n\[|\Z)"
    match = re.search(pattern, block, re.DOTALL)
    return match.group(1).strip() if match else ""


def _extract_field(pattern: str, text: str, default: str = "") -> str:
    m = re.search(pattern, text)
    return m.group(1).strip() if m else default


if __name__ == "__main__":
    with open("docs/daily_email_format.md", "r", encoding="utf-8") as f:
        content = f.read()

    # Extract sample from doc
    sample_match = re.search(r"## Fully Populated Sample for Testing\s+```text(.*?)```", content, re.DOTALL)
    if sample_match:
        sample_email = sample_match.group(1).strip()
        parsed = parse_markdown_email(sample_email)
        print("=== Successfully parsed official format ===")
        print(f"Date: {parsed['date']}, Edition: {parsed['edition']}")
        print(f"Articles: {len(parsed['articles'])}, Recalls: {len(parsed['recalls'])}, Quizzes: {len(parsed['quizzes'])}")
        print("\n--- Sample Article (with Mnemonic & Traps) ---")
        print(json.dumps(parsed['articles'][0], indent=2))
        print("\n--- Sample Recall ---")
        print(json.dumps(parsed['recalls'][0], indent=2))
        print("\n--- Sample Quiz ---")
        print(json.dumps(parsed['quizzes'][0], indent=2))
