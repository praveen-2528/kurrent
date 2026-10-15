"""
FastAPI Server for Kurrent on EC2
Serves current affairs data to the Android App with sub-10ms response times.
Includes auto-generated Swagger documentation at: http://<EC2-IP>:8000/docs
"""

from fastapi import FastAPI, HTTPException, Header, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
import os
import secrets
import json
import base64
import hmac
import hashlib
import time

from database import (
    get_briefing_by_date,
    get_latest_briefing,
    init_db,
    save_otp,
    verify_otp,
    create_user,
    get_user_by_email,
    get_user_by_id,
    verify_password,
    sync_user_progress,
    get_user_progress,
)
from ingest import fetch_and_ingest

app = FastAPI(
    title="Kurrent Daily Current Affairs API",
    description="High-performance backend for UPSC, SSC, and IBPS current affairs.",
    version="1.0.0"
)

# Enable CORS for all clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

JWT_SECRET = os.environ.get("KURRENT_JWT_SECRET", "kurrent-exam-prep-jwt-secret-2026")


def create_token(user_id: str, email: str) -> str:
    payload = {
        "sub": user_id,
        "email": email,
        "exp": int(time.time()) + (30 * 86400)
    }
    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    signature = hmac.new(JWT_SECRET.encode(), f"{header_b64}.{payload_b64}".encode(), hashlib.sha256).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}.{sig_b64}"


def verify_token(token: str) -> Optional[dict]:
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        expected_sig = hmac.new(JWT_SECRET.encode(), f"{header_b64}.{payload_b64}".encode(), hashlib.sha256).digest()
        expected_b64 = base64.urlsafe_b64encode(expected_sig).decode().rstrip("=")
        if not secrets.compare_digest(sig_b64, expected_b64):
            return None
        payload_padded = payload_b64 + "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.urlsafe_b64decode(payload_padded).decode())
        if payload.get("exp", 0) < time.time():
            return None
        return payload
    except Exception:
        return None


def get_current_user_id(authorization: Optional[str] = Header(None)) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing or invalid Authorization header")
    token = authorization.split(" ", 1)[1]
    payload = verify_token(token)
    if not payload:
        raise HTTPException(status_code=401, detail="Session expired or invalid token")
    return payload["sub"]


def send_email_otp(recipient_email: str, otp_code: str) -> bool:
    email_account = os.environ.get("EMAIL_ACCOUNT", "").strip()
    email_app_pass = os.environ.get("EMAIL_APP_PASSWORD", "").strip()

    if not email_account or "your-email" in email_account or not email_app_pass or "your-" in email_app_pass:
        print(f"[OTP DEV FALLBACK] OTP for {recipient_email}: {otp_code}")
        return True

    try:
        import smtplib
        from email.mime.text import MIMEText
        from email.mime.multipart import MIMEMultipart

        msg = MIMEMultipart("alternative")
        msg["Subject"] = f"Your Kurrent Verification Code: {otp_code}"
        msg["From"] = f"Kurrent App <{email_account}>"
        msg["To"] = recipient_email

        html = f"""
        <html>
        <body style="font-family: Arial, sans-serif; background-color: #0F172A; color: #FFFFFF; padding: 24px;">
            <div style="max-width: 480px; margin: 0 auto; background-color: #1E293B; border-radius: 12px; padding: 24px; border: 1px solid #334155;">
                <h2 style="color: #0D9488; margin-top: 0;">Kurrent Verification Code</h2>
                <p style="color: #94A3B8; font-size: 14px;">Use this 6-digit code to complete your Kurrent verification:</p>
                <div style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #FFFFFF; text-align: center; padding: 18px; background-color: #0F172A; border-radius: 8px; margin: 20px 0;">
                    {otp_code}
                </div>
                <p style="color: #94A3B8; font-size: 12px;">Valid for 10 minutes. If you did not request this, please ignore.</p>
            </div>
        </body>
        </html>
        """
        msg.attach(MIMEText(html, "html"))
        with smtplib.SMTP_SSL("smtp.gmail.com", 465, timeout=10) as s:
            s.login(email_account, email_app_pass)
            s.sendmail(email_account, recipient_email, msg.as_string())
        return True
    except Exception as e:
        print(f"[OTP SEND ERROR] {e}. Falling back to console logging.")
        return True


# --- Pydantic Request Models ---
class SendOtpRequest(BaseModel):
    email: str
    purpose: str = "signup"


class RegisterWithOtpRequest(BaseModel):
    email: str
    otp: str
    password: str
    display_name: str
    target_exam: str = "ALL"


class LoginRequest(BaseModel):
    email: str
    password: str


class SyncProgressRequest(BaseModel):
    current_date: str
    read_article_ids: List[str] = []
    bookmarked_article_ids: List[str] = []


@app.on_event("startup")
def on_startup():
    init_db()


@app.get("/health")
def health_check():
    return {"status": "healthy", "timestamp": datetime.utcnow().isoformat()}


@app.get("/briefing/today")
def get_today_briefing():
    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    briefing = get_briefing_by_date(today_str)
    if not briefing:
        briefing = get_latest_briefing()
    if not briefing:
        raise HTTPException(status_code=404, detail="No briefings available yet. Please run ingestion.")
    return briefing


@app.get("/briefing/{date}")
def get_briefing_for_date(date: str):
    briefing = get_briefing_by_date(date)
    if not briefing:
        raise HTTPException(status_code=404, detail=f"No briefing found for date: {date}")
    return briefing


@app.post("/ingest/trigger")
def trigger_ingestion():
    success = fetch_and_ingest(force_check_seen=True)
    if success:
        return {"status": "success", "message": "Successfully ingested briefing(s)."}
    else:
        return {"status": "warning", "message": "Ingestion completed, but no new briefing was saved."}


# --- Authentication & OTP Endpoints ---

@app.post("/auth/send-otp")
def api_send_otp(req: SendOtpRequest):
    """
    Generates a 6-digit OTP code and dispatches it to the user's email address.
    """
    clean_email = req.email.strip().lower()
    if not clean_email or "@" not in clean_email:
        raise HTTPException(status_code=400, detail="Please provide a valid email address.")

    if req.purpose == "signup":
        existing = get_user_by_email(clean_email)
        if existing:
            raise HTTPException(status_code=400, detail="An account with this email already exists. Please log in.")

    # 6-digit OTP
    otp_code = f"{secrets.randbelow(900000) + 100000}"
    save_otp(clean_email, otp_code, req.purpose)
    send_email_otp(clean_email, otp_code)

    return {
        "status": "success",
        "message": f"Verification code sent to {clean_email}.",
        "email": clean_email
    }


@app.post("/auth/register")
def api_register(req: RegisterWithOtpRequest):
    """
    Verifies the email OTP, creates the user account, and returns a 30-day session token.
    """
    clean_email = req.email.strip().lower()
    if len(req.password.strip()) < 6:
        raise HTTPException(status_code=400, detail="Password must be at least 6 characters.")

    # Verify OTP
    is_valid = verify_otp(clean_email, req.otp.strip(), "signup")
    if not is_valid:
        raise HTTPException(status_code=400, detail="Invalid or expired OTP code. Please request a new code.")

    # Check existing user
    if get_user_by_email(clean_email):
        raise HTTPException(status_code=400, detail="Account already exists for this email.")

    user = create_user(clean_email, req.password.strip(), req.display_name.strip(), req.target_exam)
    token = create_token(user["id"], user["email"])

    return {
        "status": "success",
        "message": "Account created successfully!",
        "token": token,
        "user": user
    }


@app.post("/auth/login")
def api_login(req: LoginRequest):
    """
    Authenticates email and password, returning user profile and session token.
    """
    clean_email = req.email.strip().lower()
    user = get_user_by_email(clean_email)
    if not user:
        raise HTTPException(status_code=401, detail="No account found with this email.")

    if not verify_password(req.password.strip(), user["password_hash"], user["salt"]):
        raise HTTPException(status_code=401, detail="Incorrect password.")

    token = create_token(user["id"], user["email"])
    return {
        "status": "success",
        "message": "Logged in successfully!",
        "token": token,
        "user": {
            "id": user["id"],
            "email": user["email"],
            "display_name": user["display_name"],
            "target_exam": user["target_exam"]
        }
    }


# --- User Profile & Cloud Progress Sync ---

@app.get("/user/profile")
def api_get_profile(user_id: str = Depends(get_current_user_id)):
    """
    Fetches the authenticated user's profile and progress summary.
    """
    user = get_user_by_id(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found.")

    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    progress = get_user_progress(user_id, today_str)

    return {
        "user": user,
        "progress": progress
    }


@app.post("/user/sync")
def api_sync_progress(req: SyncProgressRequest, user_id: str = Depends(get_current_user_id)):
    """
    Syncs local reading progress and bookmarks with cloud database.
    Updates streaks and returns consolidated cloud data.
    """
    result = sync_user_progress(
        user_id=user_id,
        current_date=req.current_date,
        read_ids=req.read_article_ids,
        bookmark_ids=req.bookmarked_article_ids
    )

    return {
        "status": "success",
        "synced_at": datetime.utcnow().isoformat(),
        "stats": result
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
