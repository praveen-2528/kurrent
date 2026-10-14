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

# Allow importing parser from parent directory or local directory
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
try:
    from parser import parse_markdown_email
except ImportError:
    from backend.parser import parse_markdown_email

from database import save_briefing, init_db

# Credentials (read from environment or .env file)
IMAP_SERVER = os.environ.get("IMAP_SERVER", "imap.gmail.com")
EMAIL_ACCOUNT = os.environ.get("EMAIL_ACCOUNT", "your-email@gmail.com")
EMAIL_APP_PASSWORD = os.environ.get("EMAIL_APP_PASSWORD", "your-16-char-app-password")


def fetch_and_ingest():
    print(f"[{datetime.now().isoformat()}] Starting email fetch from {EMAIL_ACCOUNT}...")

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

        email_ids = messages[0].split()
        if not email_ids:
            print("No Kurrent briefing email found in inbox.")
            mail.logout()
            return False

        # Pick the latest email
        latest_id = email_ids[-1]
        res, msg_data = mail.fetch(latest_id, "(RFC822)")

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
            print("Email body was empty.")
            mail.logout()
            return False

        # 3. Parse with Kurrent Parser
        parsed = parse_markdown_email(body)
        print(f"Successfully parsed briefing for date: {parsed['date']} (Edition #{parsed['edition']})")
        print(f"Stats: {len(parsed['articles'])} articles, {len(parsed['recalls'])} recalls, {len(parsed['quizzes'])} quizzes.")

        # 4. Save to SQLite database
        save_briefing(parsed["date"], parsed["edition"], parsed)
        print("Saved briefing to SQLite database!")

        # Mark as read
        mail.store(latest_id, "+FLAGS", "\\Seen")
        mail.logout()
        return True

    except Exception as e:
        print(f"Error during ingestion: {str(e)}")
        return False


if __name__ == "__main__":
    fetch_and_ingest()
