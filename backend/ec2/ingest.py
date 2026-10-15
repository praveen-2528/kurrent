"""
Kurrent EC2 Email Ingestion Script
Can be run manually anytime: python ingest.py
Or automated via Linux crontab at 5:15 AM:
15 5 * * * /usr/bin/python3 /home/ubuntu/kurrent/backend/ec2/ingest.py >> /home/ubuntu/kurrent/ingest.log 2>&1
"""

import os
import sys
import imaplib
import email
from email.header import decode_header
import json
from datetime import datetime

# Ensure parent backend directories are at the top of sys.path
_current_dir = os.path.dirname(os.path.abspath(__file__))
_backend_dir = os.path.dirname(_current_dir)
_root_dir = os.path.dirname(_backend_dir)

for _p in [_backend_dir, _root_dir]:
    if _p not in sys.path:
        sys.path.insert(0, _p)

try:
    from kurrent_parser import parse_markdown_email
except ImportError:
    try:
        from parser import parse_markdown_email
    except ImportError:
        from backend.parser import parse_markdown_email

from database import save_briefing, init_db

# Automatically read .env file in the current directory if present
def _load_env_file():
    env_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if os.path.exists(env_file):
        with open(env_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    key, val = line.split("=", 1)
                    os.environ[key.strip()] = val.strip().strip("'\"")

_load_env_file()

# Credentials (read from environment or .env file)
IMAP_SERVER = os.environ.get("IMAP_SERVER", "imap.gmail.com").strip()
EMAIL_ACCOUNT = os.environ.get("EMAIL_ACCOUNT", "your-email@gmail.com").strip()
# Remove spaces if Google App Password was copied with spaces (e.g. "xxxx xxxx xxxx xxxx" -> "xxxxxxxxxxxxxxxx")
EMAIL_APP_PASSWORD = os.environ.get("EMAIL_APP_PASSWORD", "your-16-char-app-password").replace(" ", "").strip()


def fetch_and_ingest():
    print(f"[{datetime.now().isoformat()}] Starting email fetch from account: {EMAIL_ACCOUNT}...")

    init_db()

    try:
        mail = imaplib.IMAP4_SSL(IMAP_SERVER)
        mail.login(EMAIL_ACCOUNT, EMAIL_APP_PASSWORD)
        mail.select("inbox")

        # 1. Search for unread briefing emails first
        status, messages = mail.search(None, '(UNSEEN SUBJECT "[KURRENT-DAILY]")')

        if status != "OK" or not messages[0]:
            print("No unread email found. Checking latest email with [KURRENT-DAILY] in subject...")
            status, messages = mail.search(None, '(SUBJECT "[KURRENT-DAILY]")')

        is_unseen_batch = (status == "OK" and bool(messages[0]))
        targets = email_ids if is_unseen_batch else [email_ids[-1]]

        print(f"Found {len(targets)} email(s) to process (unseen batch: {is_unseen_batch}).")
        ingested_count = 0

        for email_id in targets:
            try:
                res, msg_data = mail.fetch(email_id, "(RFC822)")
                if not msg_data or not msg_data[0]:
                    continue

                raw_email = msg_data[0][1]
                msg = email.message_from_bytes(raw_email)

                # 2. Extract body
                body = ""
                if msg.is_multipart():
                    for part in msg.walk():
                        content_type = part.get_content_type()
                        content_disposition = str(part.get("Content-Disposition"))
                        if content_type == "text/plain" and "attachment" not in content_disposition:
                            body = part.get_payload(decode=True).decode("utf-8", errors="ignore")
                            break
                else:
                    body = msg.get_payload(decode=True).decode("utf-8", errors="ignore")

                if not body:
                    print(f"Email ID {email_id.decode('utf-8', errors='ignore')} body was empty.")
                    continue

                # 3. Parse with Kurrent Parser
                parsed = parse_markdown_email(body)
                print(f"Successfully parsed briefing for date: {parsed['date']} (Edition #{parsed['edition']})")
                print(f"Stats: {len(parsed['articles'])} articles, {len(parsed['recalls'])} recalls, {len(parsed['quizzes'])} quizzes.")

                # 4. Save to SQLite database under its extracted DATE tag
                save_briefing(parsed["date"], parsed["edition"], parsed)
                print(f"Saved briefing for date '{parsed['date']}' to SQLite database!")

                # Mark as read
                mail.store(email_id, "+FLAGS", "\\Seen")
                ingested_count += 1
            except Exception as item_err:
                print(f"Error parsing email ID {email_id}: {str(item_err)}")

        mail.logout()
        print(f"Completed ingestion: successfully saved {ingested_count} briefing(s).")
        return ingested_count > 0

    except Exception as e:
        print(f"Error during ingestion: {str(e)}")
        return False


if __name__ == "__main__":
    fetch_and_ingest()
