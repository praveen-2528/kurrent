"""
AWS Lambda Function: Daily Email Ingestor & Parser
Triggered by: Amazon EventBridge Rule (Cron: 05:15 AM IST / 23:45 UTC)
Cost: 100% Free Tier (1 execution/day = ~30 executions/month out of 1,000,000 free)
"""

import os
import imaplib
import email
from email.header import decode_header
import json
import boto3
from parser import parse_markdown_email

# Environment variables configured in Lambda
IMAP_SERVER = os.environ.get("IMAP_SERVER", "imap.gmail.com")
EMAIL_ACCOUNT = os.environ.get("EMAIL_ACCOUNT", "your-email@gmail.com")
EMAIL_APP_PASSWORD = os.environ.get("EMAIL_APP_PASSWORD", "your-16-char-app-password")
DYNAMODB_TABLE = os.environ.get("DYNAMODB_TABLE", "kurrent_briefings")

dynamodb = boto3.resource("dynamodb")
table = dynamodb.Table(DYNAMODB_TABLE)


def lambda_handler(event, context):
    """
    Main Lambda entry point.
    Connects via IMAP SSL, searches for today's briefing email, parses it, and writes to DynamoDB.
    """
    print("Starting Kurrent daily email ingestion...")

    try:
        # 1. Connect to Mailbox over SSL
        mail = imaplib.IMAP4_SSL(IMAP_SERVER)
        mail.login(EMAIL_ACCOUNT, EMAIL_APP_PASSWORD)
        mail.select("inbox")

        # 2. Search for unread daily briefing emails
        status, messages = mail.search(None, '(UNSEEN SUBJECT "[KURRENT-DAILY]")')

        if status != "OK" or not messages[0]:
            # Fallback search without UNSEEN if already marked read
            status, messages = mail.search(None, '(SUBJECT "[KURRENT-DAILY]")')

        email_ids = messages[0].split()
        if not email_ids:
            print("No new Kurrent briefing email found for today.")
            return {"statusCode": 200, "body": "No new emails to process."}

        # Pick the latest email
        latest_id = email_ids[-1]
        res, msg_data = mail.fetch(latest_id, "(RFC822)")

        raw_email = msg_data[0][1]
        msg = email.message_from_bytes(raw_email)

        # 3. Extract email body
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
            return {"statusCode": 400, "body": "Empty email payload."}

        # 4. Parse using the Kurrent Parser Engine
        parsed_data = parse_markdown_email(body)
        print(f"Parsed {len(parsed_data['articles'])} articles, {len(parsed_data['recalls'])} recalls, and {len(parsed_data['quizzes'])} quizzes.")

        # 5. Store in DynamoDB
        date_str = parsed_data["date"]

        # 5a. Store master document for 10ms mobile app single-fetch
        table.put_item(
            Item={
                "PK": f"BRIEFING#{date_str}",
                "SK": "METADATA",
                "date": date_str,
                "edition": parsed_data["edition"],
                "payload": parsed_data
            }
        )

        # 5b. Batch write granular records
        with table.batch_writer() as batch:
            for article in parsed_data["articles"]:
                batch.put_item(
                    Item={
                        "PK": f"DATE#{date_str}",
                        "SK": f"ARTICLE#{article['id']}",
                        **article
                    }
                )
            for recall in parsed_data["recalls"]:
                batch.put_item(
                    Item={
                        "PK": f"DATE#{date_str}",
                        "SK": f"RECALL#{recall['id']}",
                        **recall
                    }
                )
            for quiz in parsed_data["quizzes"]:
                batch.put_item(
                    Item={
                        "PK": f"DATE#{date_str}",
                        "SK": f"QUIZ#{quiz['id']}",
                        **quiz
                    }
                )

        # Mark email as read / processed
        mail.store(latest_id, "+FLAGS", "\\Seen")
        mail.logout()

        return {
            "statusCode": 200,
            "body": json.dumps({
                "message": "Successfully ingested and stored briefing.",
                "date": date_str,
                "articles_count": len(parsed_data["articles"]),
                "recalls_count": len(parsed_data["recalls"]),
                "quizzes_count": len(parsed_data["quizzes"])
            })
        }

    except Exception as e:
        print(f"Error during ingestion: {str(e)}")
        raise e
