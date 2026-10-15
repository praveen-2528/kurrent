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
        conn.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                display_name TEXT NOT NULL,
                target_exam TEXT DEFAULT 'ALL',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS email_otps (
                email TEXT PRIMARY KEY,
                otp_code TEXT NOT NULL,
                expires_at REAL NOT NULL,
                purpose TEXT DEFAULT 'signup'
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS user_progress (
                user_id TEXT NOT NULL,
                article_id TEXT NOT NULL,
                briefing_date TEXT NOT NULL,
                is_read INTEGER DEFAULT 0,
                is_bookmarked INTEGER DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_id, article_id)
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS user_streaks (
                user_id TEXT PRIMARY KEY,
                current_streak INTEGER DEFAULT 0,
                longest_streak INTEGER DEFAULT 0,
                last_active_date TEXT,
                total_articles_read INTEGER DEFAULT 0
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


import hashlib
import secrets
import time
import uuid
from datetime import timedelta


def hash_password(password: str, salt: str = None) -> tuple:
    if not salt:
        salt = secrets.token_hex(16)
    key = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt.encode("utf-8"), 100000)
    return key.hex(), salt


def verify_password(password: str, password_hash: str, salt: str) -> bool:
    key, _ = hash_password(password, salt)
    return secrets.compare_digest(key, password_hash)


def save_otp(email_addr: str, otp_code: str, purpose: str = "signup"):
    """Stores a 6-digit OTP code with a 10-minute expiry."""
    init_db()
    clean_email = email_addr.strip().lower()
    expires_at = time.time() + 600.0  # 10 minutes from now
    with get_connection() as conn:
        conn.execute("""
            INSERT INTO email_otps (email, otp_code, expires_at, purpose)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(email) DO UPDATE SET
                otp_code = excluded.otp_code,
                expires_at = excluded.expires_at,
                purpose = excluded.purpose
        """, (clean_email, otp_code, expires_at, purpose))
        conn.commit()


def verify_otp(email_addr: str, otp_code: str, purpose: str = "signup") -> bool:
    """Verifies that the OTP exists, matches, and is not expired."""
    init_db()
    clean_email = email_addr.strip().lower()
    now = time.time()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT otp_code, expires_at FROM email_otps WHERE email = ? AND purpose = ?", (clean_email, purpose))
        row = cursor.fetchone()
        if row:
            stored_otp = row["otp_code"]
            expires_at = row["expires_at"]
            if now <= expires_at and secrets.compare_digest(stored_otp, otp_code.strip()):
                # Clean up used OTP
                conn.execute("DELETE FROM email_otps WHERE email = ?", (clean_email,))
                conn.commit()
                return True
    return False


def create_user(email_addr: str, password: str, display_name: str, target_exam: str = "ALL") -> dict:
    """Creates a new student user account."""
    init_db()
    clean_email = email_addr.strip().lower()
    pwd_hash, salt = hash_password(password)
    user_id = "usr_" + uuid.uuid4().hex[:12]

    with get_connection() as conn:
        conn.execute("""
            INSERT INTO users (id, email, password_hash, salt, display_name, target_exam)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (user_id, clean_email, pwd_hash, salt, display_name.strip(), target_exam))

        conn.execute("""
            INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_active_date, total_articles_read)
            VALUES (?, 0, 0, NULL, 0)
        """, (user_id,))
        conn.commit()

    return {
        "id": user_id,
        "email": clean_email,
        "display_name": display_name.strip(),
        "target_exam": target_exam
    }


def get_user_by_email(email_addr: str) -> Optional[Dict[str, Any]]:
    init_db()
    clean_email = email_addr.strip().lower()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE email = ?", (clean_email,))
        row = cursor.fetchone()
        if row:
            return dict(row)
    return None


def get_user_by_id(user_id: str) -> Optional[Dict[str, Any]]:
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT id, email, display_name, target_exam, created_at FROM users WHERE id = ?", (user_id,))
        row = cursor.fetchone()
        if row:
            return dict(row)
    return None


def sync_user_progress(user_id: str, current_date: str, read_ids: list, bookmark_ids: list) -> dict:
    """
    Syncs reading progress and bookmarks, and updates the user's reading streak.
    """
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()

        # 1. Update read articles
        for art_id in read_ids:
            cursor.execute("""
                INSERT INTO user_progress (user_id, article_id, briefing_date, is_read)
                VALUES (?, ?, ?, 1)
                ON CONFLICT(user_id, article_id) DO UPDATE SET
                    is_read = 1,
                    updated_at = CURRENT_TIMESTAMP
            """, (user_id, art_id, current_date))

        # 2. Reset or update bookmarks
        # Set all previous bookmarks to 0, then mark provided ones as 1
        cursor.execute("UPDATE user_progress SET is_bookmarked = 0 WHERE user_id = ?", (user_id,))
        for art_id in bookmark_ids:
            cursor.execute("""
                INSERT INTO user_progress (user_id, article_id, briefing_date, is_bookmarked)
                VALUES (?, ?, ?, 1)
                ON CONFLICT(user_id, article_id) DO UPDATE SET
                    is_bookmarked = 1,
                    updated_at = CURRENT_TIMESTAMP
            """, (user_id, art_id, current_date))

        # 3. Streak Calculation
        cursor.execute("SELECT current_streak, longest_streak, last_active_date, total_articles_read FROM user_streaks WHERE user_id = ?", (user_id,))
        streak_row = cursor.fetchone()
        current_streak = streak_row["current_streak"] if streak_row else 0
        longest_streak = streak_row["longest_streak"] if streak_row else 0
        last_active = streak_row["last_active_date"] if streak_row else None

        # Check if user read something today
        if len(read_ids) > 0 and current_date:
            try:
                today_dt = datetime.strptime(current_date, "%Y-%m-%d").date()
                if last_active:
                    last_dt = datetime.strptime(last_active, "%Y-%m-%d").date()
                    delta_days = (today_dt - last_dt).days
                    if delta_days == 1:
                        current_streak += 1
                    elif delta_days > 1:
                        current_streak = 1
                    # If delta_days == 0, already counted today
                else:
                    current_streak = 1

                if current_streak > longest_streak:
                    longest_streak = current_streak

                last_active = current_date
            except Exception:
                pass

        # Total read articles count
        cursor.execute("SELECT COUNT(*) as total_read FROM user_progress WHERE user_id = ? AND is_read = 1", (user_id,))
        total_read = cursor.fetchone()["total_read"]

        cursor.execute("""
            INSERT INTO user_streaks (user_id, current_streak, longest_streak, last_active_date, total_articles_read)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                current_streak = excluded.current_streak,
                longest_streak = excluded.longest_streak,
                last_active_date = excluded.last_active_date,
                total_articles_read = excluded.total_articles_read
        """, (user_id, current_streak, longest_streak, last_active, total_read))

        # 4. Fetch all active cloud bookmarks
        cursor.execute("SELECT article_id FROM user_progress WHERE user_id = ? AND is_bookmarked = 1", (user_id,))
        cloud_bookmarks = [r["article_id"] for r in cursor.fetchall()]

        # 5. Fetch all today's read IDs
        cursor.execute("SELECT article_id FROM user_progress WHERE user_id = ? AND briefing_date = ? AND is_read = 1", (user_id, current_date))
        today_read = [r["article_id"] for r in cursor.fetchall()]

        conn.commit()

    return {
        "current_streak": current_streak,
        "longest_streak": longest_streak,
        "total_articles_read": total_read,
        "last_active_date": last_active,
        "cloud_bookmarks": cloud_bookmarks,
        "today_read_ids": today_read
    }


def get_user_progress(user_id: str, current_date: str) -> dict:
    """Returns active progress, streaks, and bookmarks for a user."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT current_streak, longest_streak, last_active_date, total_articles_read FROM user_streaks WHERE user_id = ?", (user_id,))
        streak_row = cursor.fetchone()
        current_streak = streak_row["current_streak"] if streak_row else 0
        longest_streak = streak_row["longest_streak"] if streak_row else 0
        last_active = streak_row["last_active_date"] if streak_row else None
        total_read = streak_row["total_articles_read"] if streak_row else 0

        cursor.execute("SELECT article_id FROM user_progress WHERE user_id = ? AND is_bookmarked = 1", (user_id,))
        cloud_bookmarks = [r["article_id"] for r in cursor.fetchall()]

        cursor.execute("SELECT article_id FROM user_progress WHERE user_id = ? AND briefing_date = ? AND is_read = 1", (user_id, current_date))
        today_read = [r["article_id"] for r in cursor.fetchall()]

    return {
        "current_streak": current_streak,
        "longest_streak": longest_streak,
        "total_articles_read": total_read,
        "last_active_date": last_active,
        "cloud_bookmarks": cloud_bookmarks,
        "today_read_ids": today_read
    }


def reset_password_with_otp(email_addr: str, otp_code: str, new_password: str) -> bool:
    """Verifies OTP and resets the user's password."""
    init_db()
    clean_email = email_addr.strip().lower()

    # 1. Verify user exists
    user = get_user_by_email(clean_email)
    if not user:
        return False

    # 2. Verify OTP for forgot_password
    if not verify_otp(clean_email, otp_code, "forgot_password"):
        return False

    # 3. Hash new password and update
    pwd_hash, salt = hash_password(new_password)
    with get_connection() as conn:
        conn.execute("UPDATE users SET password_hash = ?, salt = ? WHERE email = ?", (pwd_hash, salt, clean_email))
        conn.commit()

    return True


# --- Admin Portal Queries ---

def get_admin_stats() -> dict:
    """Consolidated KPI metrics for Kurrent Admin Command Center."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()

        # Briefings count
        cursor.execute("SELECT COUNT(*) as count FROM briefings")
        total_briefings = cursor.fetchone()["count"]

        # Latest briefing info
        cursor.execute("SELECT date, edition, created_at, payload_json FROM briefings ORDER BY date DESC LIMIT 1")
        latest_row = cursor.fetchone()
        latest_briefing = None
        if latest_row:
            try:
                p = json.loads(latest_row["payload_json"])
                latest_briefing = {
                    "date": latest_row["date"],
                    "edition": latest_row["edition"],
                    "created_at": latest_row["created_at"],
                    "articles_count": len(p.get("articles", [])),
                    "quizzes_count": len(p.get("quizzes", []))
                }
            except Exception:
                latest_briefing = {"date": latest_row["date"], "edition": latest_row["edition"]}

        # Users count
        cursor.execute("SELECT COUNT(*) as count FROM users")
        total_users = cursor.fetchone()["count"]

        # Total articles read
        cursor.execute("SELECT COUNT(*) as count FROM user_progress WHERE is_read = 1")
        total_reads = cursor.fetchone()["count"]

        # Total bookmarks
        cursor.execute("SELECT COUNT(*) as count FROM user_progress WHERE is_bookmarked = 1")
        total_bookmarks = cursor.fetchone()["count"]

        # Highest streak
        cursor.execute("SELECT MAX(longest_streak) as max_streak, MAX(current_streak) as max_cur FROM user_streaks")
        streak_row = cursor.fetchone()
        max_streak = streak_row["max_streak"] if streak_row and streak_row["max_streak"] else 0
        max_cur = streak_row["max_cur"] if streak_row and streak_row["max_cur"] else 0

        # Target exam distribution
        cursor.execute("SELECT target_exam, COUNT(*) as count FROM users GROUP BY target_exam")
        exam_distribution = {row["target_exam"]: row["count"] for row in cursor.fetchall()}

        # Database file size
        db_size_kb = 0
        if os.path.exists(DB_FILE):
            db_size_kb = round(os.path.getsize(DB_FILE) / 1024, 1)

    return {
        "total_briefings": total_briefings,
        "latest_briefing": latest_briefing,
        "total_users": total_users,
        "total_reads": total_reads,
        "total_bookmarks": total_bookmarks,
        "max_streak": max(max_streak, max_cur),
        "exam_distribution": exam_distribution,
        "db_size_kb": db_size_kb,
        "db_path": DB_FILE
    }


def get_admin_briefings(limit: int = 50) -> list:
    """Returns summarized list of briefings for Admin dashboard table."""
    init_db()
    briefings = []
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("SELECT date, edition, created_at, payload_json FROM briefings ORDER BY date DESC LIMIT ?", (limit,))
        for row in cursor.fetchall():
            art_count = 0
            quiz_count = 0
            flashcard_count = 0
            topics = []
            try:
                p = json.loads(row["payload_json"])
                articles = p.get("articles", [])
                art_count = len(articles)
                quiz_count = len(p.get("quizzes", []))
                flashcard_count = len(p.get("flashcards", []))
                topics = list({t for a in articles for t in a.get("topics", [])})[:4]
            except Exception:
                pass

            briefings.append({
                "date": row["date"],
                "edition": row["edition"],
                "created_at": row["created_at"],
                "article_count": art_count,
                "quiz_count": quiz_count,
                "flashcard_count": flashcard_count,
                "topics": topics
            })
    return briefings


def get_admin_users(limit: int = 100) -> list:
    """Returns list of student users with their reading streaks and stats."""
    init_db()
    users = []
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("""
            SELECT u.id, u.email, u.display_name, u.target_exam, u.created_at,
                   COALESCE(s.current_streak, 0) as current_streak,
                   COALESCE(s.longest_streak, 0) as longest_streak,
                   COALESCE(s.total_articles_read, 0) as total_articles_read,
                   s.last_active_date
            FROM users u
            LEFT JOIN user_streaks s ON u.id = s.user_id
            ORDER BY u.created_at DESC
            LIMIT ?
        """, (limit,))
        for row in cursor.fetchall():
            users.append(dict(row))
    return users


def delete_briefing(date_str: str) -> bool:
    """Deletes a daily briefing by date."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM briefings WHERE date = ?", (date_str,))
        conn.commit()
        return cursor.rowcount > 0


def delete_user(user_id: str) -> bool:
    """Removes a user and their progress history."""
    init_db()
    with get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute("DELETE FROM users WHERE id = ?", (user_id,))
        cursor.execute("DELETE FROM user_progress WHERE user_id = ?", (user_id,))
        cursor.execute("DELETE FROM user_streaks WHERE user_id = ?", (user_id,))
        conn.commit()
        return cursor.rowcount > 0


if __name__ == "__main__":
    init_db()
    print(f"Database initialized at {DB_FILE}")

