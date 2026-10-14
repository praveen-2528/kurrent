import sys
import os

sys.path.extend([
    os.path.abspath("backend"),
    os.path.abspath("backend/ec2")
])

from parser import parse_markdown_email
from database import save_briefing, get_latest_briefing, init_db

init_db()

with open("docs/daily_email_format.md", "r", encoding="utf-8") as f:
    text = f.read()

parts = text.split("## Fully Populated Sample for Testing")
sample = parts[1].split("```text")[1].split("```")[0].strip()

parsed = parse_markdown_email(sample)
save_briefing(parsed["date"], parsed["edition"], parsed)

res = get_latest_briefing()
print("=== SQLite & EC2 Flow Test Successful ===")
print("Date:", res["date"], "Edition:", res["edition"])
print("Articles:", len(res["articles"]))
print("Recalls:", len(res["recalls"]))
print("Quizzes:", len(res["quizzes"]))
print("First Article Headline:", res["articles"][0]["headline_fact"])
print("First Article Mnemonic:", res["articles"][0]["mnemonic"])
