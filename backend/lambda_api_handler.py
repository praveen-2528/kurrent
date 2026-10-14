"""
AWS Lambda Function: Mobile App API Handler
Handles HTTP API Gateway requests from the Kurrent Android app.
Cached at edge via Amazon CloudFront (1 TB/month free tier).
"""

import os
import json
from datetime import datetime
import boto3

DYNAMODB_TABLE = os.environ.get("DYNAMODB_TABLE", "kurrent_briefings")

dynamodb = boto3.resource("dynamodb")
table = dynamodb.Table(DYNAMODB_TABLE)


def lambda_handler(event, context):
    """
    HTTP API Gateway entrypoint.
    Routes:
      GET /briefing/today
      GET /briefing/{date}
    """
    raw_path = event.get("rawPath", "")
    method = event.get("requestContext", {}).get("http", {}).get("method", "GET")

    print(f"Incoming request: {method} {raw_path}")

    # Set CORS and Edge Caching headers
    headers = {
        "Content-Type": "application/json",
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "GET, OPTIONS",
        # Cache for 2 hours on CloudFront Edge nodes across India
        "Cache-Control": "public, max-age=7200, s-maxage=7200"
    }

    if method == "OPTIONS":
        return {"statusCode": 200, "headers": headers, "body": ""}

    try:
        # Route: GET /briefing/today or GET /
        if raw_path == "/briefing/today" or raw_path == "/" or raw_path == "":
            today_str = datetime.utcnow().strftime("%Y-%m-%d")
            briefing = get_briefing_by_date(today_str)
            
            if not briefing:
                # If today's briefing hasn't arrived yet, query the most recent date
                briefing = get_latest_briefing()

            if not briefing:
                return {
                    "statusCode": 404,
                    "headers": headers,
                    "body": json.dumps({"error": "No briefing found."})
                }

            return {
                "statusCode": 200,
                "headers": headers,
                "body": json.dumps(briefing)
            }

        # Route: GET /briefing/{date} (e.g. /briefing/2026-10-15)
        elif raw_path.startswith("/briefing/"):
            date_param = raw_path.replace("/briefing/", "").strip()
            briefing = get_briefing_by_date(date_param)

            if not briefing:
                return {
                    "statusCode": 404,
                    "headers": headers,
                    "body": json.dumps({"error": f"No briefing found for date: {date_param}"})
                }

            return {
                "statusCode": 200,
                "headers": headers,
                "body": json.dumps(briefing)
            }

        else:
            return {
                "statusCode": 404,
                "headers": headers,
                "body": json.dumps({"error": "Route not found."})
            }

    except Exception as e:
        print(f"Error handling request: {str(e)}")
        return {
            "statusCode": 500,
            "headers": headers,
            "body": json.dumps({"error": "Internal server error."})
        }


def get_briefing_by_date(date_str: str):
    """
    Fetches the master daily briefing document from DynamoDB in a single round-trip.
    """
    response = table.get_item(
        Key={
            "PK": f"BRIEFING#{date_str}",
            "SK": "METADATA"
        }
    )
    item = response.get("Item")
    if item and "payload" in item:
        return item["payload"]
    return item


def get_latest_briefing():
    """
    Fallback: Scans the most recent item if today's is not yet populated.
    """
    # Simple query using GSI or scan
    response = table.scan(Limit=5)
    items = response.get("Items", [])
    if items:
        # Sort by PK descending
        items.sort(key=lambda x: x.get("PK", ""), reverse=True)
        latest = items[0]
        return latest.get("payload", latest)
    return None
