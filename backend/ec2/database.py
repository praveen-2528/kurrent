"""
SQLite Database Layer for Kurrent on EC2
Stores daily briefings, articles, recalls, and quizzes in a local SQLite file.
Zero configuration, zero external service dependency, blazing fast.
"""

import sqlite3
import json
import os
from datetime import datetime
from typing import Optional, Dict, Any

DB_FILE = os.environ.get("KURRENT_DB_PATH", os.path.join(os.path.dirname(__file__), "kurrent.db"))


def get_connection():
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    """Initializes the database schema if not already present."""
    with get_connection() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS briefings (
                date TEXT PRIMARY KEY,
                edition INTEGER,
                payload_json TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()


def save_briefing(date_str: str, edition: int, payload: Dict[str, Any]):
    """Saves or updates today's parsed briefing."""
    init_db()
    with get_connection() as conn:
        conn.execute("""
            INSERT INTO briefings (date, edition, payload_json)
            VALUES (?, ?, ?)
            ON CONFLICT(date) DO UPDATE SET
                edition = excluded.edition,
                payload_json = excluded.payload_json,
                created_at = CURRENT_TIMESTAMP
        """, (date_str, edition, json.dumps(payload)))
        conn.commit()


def get_briefing_by_date(date_str: str) -> Optional[Dict[str, Any]]:
    """Retrieves a briefing by YYYY-MM-DD."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT payload_json FROM briefings WHERE date = ?", (date_str,))
        row = cursor.fetchone()
        if row:
            return json.loads(row["payload_json"])
    return None


def get_latest_briefing() -> Optional[Dict[str, Any]]:
    """Retrieves the most recent briefing available."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT payload_json FROM briefings ORDER BY date DESC LIMIT 1")
        row = cursor.fetchone()
        if row:
            return json.loads(row["payload_json"])
    return None


if __name__ == "__main__":
    init_db()
    print(f"Database initialized at {DB_FILE}")
