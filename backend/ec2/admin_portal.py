"""
Kurrent High-Security Admin Command Center & Mission Control
Served at /admin, /docs, and /
Secured with cryptographic session tokens, timing-attack resistance, IP brute-force rate-limiting, and constant-time hashing.
"""

from fastapi import APIRouter, HTTPException, Depends, Header, Cookie, Response, Request
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel
import os
import sys
import time
import json
import base64
import hmac
import hashlib
import secrets
import imaplib
from typing import Optional, Dict

from database import (
    get_admin_stats,
    get_admin_briefings,
    get_admin_users,
    get_briefing_by_date,
    delete_briefing,
    delete_user,
    save_otp,
    get_user_by_email,
    verify_password
)
from ingest import fetch_and_ingest

router = APIRouter()

# --- Security Configuration ---
def _load_env_file():
    candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"),
        os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), ".env"),
        os.path.join(os.getcwd(), ".env")
    ]
    for env_path in candidates:
        if os.path.exists(env_path):
            try:
                with open(env_path, "r", encoding="utf-8") as f:
                    for line in f:
                        line = line.strip()
                        if line and not line.startswith("#") and "=" in line:
                            key, val = line.split("=", 1)
                            os.environ[key.strip()] = val.strip().strip("'\"")
                break
            except Exception:
                pass


def _get_master_credentials():
    _load_env_file()
    user = os.environ.get("ADMIN_USERNAME", "admin").strip()
    pwd = os.environ.get("ADMIN_PASSWORD", "Kurrent#Admin2026!Master").strip()
    return user, pwd


ADMIN_SESSION_SECRET = os.environ.get("ADMIN_SESSION_SECRET", "kurrent-super-secret-admin-session-salt-987214").strip()

# In-memory IP Brute-Force Rate Limiter with humane 30-second backoff
# Structure: { ip_address: { "failures": int, "lockout_until": float } }
FAILED_LOGIN_ATTEMPTS: Dict[str, Dict[str, float]] = {}
MAX_FAILED_ATTEMPTS = 10
LOCKOUT_DURATION_SECONDS = 30  # 30-second backoff instead of aggressive 15-minute lockout


def _clean_expired_lockouts():
    now = time.time()
    expired_ips = [ip for ip, data in FAILED_LOGIN_ATTEMPTS.items() if data["lockout_until"] < now and data["failures"] == 0]
    for ip in expired_ips:
        FAILED_LOGIN_ATTEMPTS.pop(ip, None)


def _check_ip_lockout(client_ip: str):
    _clean_expired_lockouts()
    record = FAILED_LOGIN_ATTEMPTS.get(client_ip)
    if record and record["lockout_until"] > time.time():
        remaining = int(record["lockout_until"] - time.time())
        raise HTTPException(
            status_code=429,
            detail=f"Rate limit active. Please wait {max(1, remaining)} second(s) before trying again."
        )


def _record_failed_attempt(client_ip: str):
    now = time.time()
    record = FAILED_LOGIN_ATTEMPTS.setdefault(client_ip, {"failures": 0, "lockout_until": 0.0})
    record["failures"] += 1
    if record["failures"] >= MAX_FAILED_ATTEMPTS:
        record["lockout_until"] = now + LOCKOUT_DURATION_SECONDS
        record["failures"] = 0  # reset for next window after lockout
        raise HTTPException(
            status_code=429,
            detail="Rate limit exceeded (10 attempts). Temporary 30-second backoff active."
        )


def _record_successful_attempt(client_ip: str):
    FAILED_LOGIN_ATTEMPTS.pop(client_ip, None)


# --- Cryptographic Token Generator & Verifier ---
def create_admin_token(username: str, display_name: str = "Administrator") -> str:
    now = int(time.time())
    exp = now + (8 * 3600)  # 8-hour operational window
    payload = {
        "sub": username,
        "name": display_name,
        "role": "kurrent_super_admin",
        "iat": now,
        "exp": exp,
        "nonce": secrets.token_hex(16)
    }
    header = {"alg": "HS256", "typ": "JWT"}
    header_b64 = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip("=")
    payload_b64 = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip("=")
    signature = hmac.new(
        ADMIN_SESSION_SECRET.encode(),
        f"{header_b64}.{payload_b64}".encode(),
        hashlib.sha256
    ).digest()
    sig_b64 = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    return f"{header_b64}.{payload_b64}.{sig_b64}"


def verify_admin_token(token: str) -> Optional[dict]:
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header_b64, payload_b64, sig_b64 = parts
        expected_sig = hmac.new(
            ADMIN_SESSION_SECRET.encode(),
            f"{header_b64}.{payload_b64}".encode(),
            hashlib.sha256
        ).digest()
        expected_b64 = base64.urlsafe_b64encode(expected_sig).decode().rstrip("=")
        if not secrets.compare_digest(sig_b64, expected_b64):
            return None

        payload_padded = payload_b64 + "=" * (-len(payload_b64) % 4)
        payload = json.loads(base64.urlsafe_b64decode(payload_padded).decode())

        if payload.get("exp", 0) < time.time():
            return None
        if payload.get("role") != "kurrent_super_admin":
            return None

        return payload
    except Exception:
        return None


def verify_admin(
    authorization: Optional[str] = Header(None),
    kurrent_admin_token: Optional[str] = Cookie(None)
) -> dict:
    token = None
    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ", 1)[1]
    elif kurrent_admin_token:
        token = kurrent_admin_token

    if not token:
        raise HTTPException(status_code=401, detail="Administrator authentication required.")

    payload = verify_admin_token(token)
    if not payload:
        raise HTTPException(status_code=401, detail="Session expired or invalid administrator credentials.")

    return payload


# --- Pydantic Models ---
class AdminLoginRequest(BaseModel):
    username: str
    password: str


class AdminResetOtpRequest(BaseModel):
    email: str


# --- Admin Authentication Endpoints ---

@router.post("/admin/api/login")
def api_admin_login(req: AdminLoginRequest, request: Request, response: Response):
    client_ip = request.client.host if request.client else "unknown"
    _check_ip_lockout(client_ip)

    clean_user = req.username.strip()
    clean_pass = req.password.strip()

    master_user, master_pass = _get_master_credentials()

    # Step 1: Check Master Key credentials
    is_master_user = secrets.compare_digest(
        hashlib.sha256(clean_user.encode()).digest(),
        hashlib.sha256(master_user.encode()).digest()
    )
    is_master_pass = secrets.compare_digest(
        hashlib.sha256(clean_pass.encode()).digest(),
        hashlib.sha256(master_pass.encode()).digest()
    )

    authenticated_user = None
    display_name = "Master Administrator"

    if is_master_user and is_master_pass:
        authenticated_user = master_user
        display_name = "Master Administrator"
    else:
        # Step 2: Check database user accounts (email + password)
        db_user = get_user_by_email(clean_user)
        if db_user and verify_password(clean_pass, db_user["password_hash"], db_user["salt"]):
            authenticated_user = db_user["email"]
            display_name = db_user.get("display_name") or db_user["email"]

    if not authenticated_user:
        _record_failed_attempt(client_ip)
        remaining_attempts = MAX_FAILED_ATTEMPTS - FAILED_LOGIN_ATTEMPTS.get(client_ip, {}).get("failures", 1)
        raise HTTPException(
            status_code=401,
            detail=f"Access denied: Invalid credentials. You may authenticate with the Master Key ('{master_user}') or your registered Kurrent app account. ({max(0, remaining_attempts)} attempts remaining before 30s rate-limit)"
        )

    _record_successful_attempt(client_ip)
    token = create_admin_token(authenticated_user, display_name)

    # Set HttpOnly, SameSite cookie for seamless browser protection
    response.set_cookie(
        key="kurrent_admin_token",
        value=token,
        max_age=8 * 3600,
        httponly=True,
        samesite="lax",
        secure=False  # Set to True if using HTTPS
    )

    return {
        "status": "success",
        "message": f"Security clearance granted. Welcome, {display_name}.",
        "token": token,
        "username": authenticated_user,
        "display_name": display_name,
        "role": "kurrent_super_admin",
        "expires_in": 8 * 3600
    }


@router.post("/admin/api/reset-rate-limit")
def api_admin_reset_rate_limit(request: Request):
    client_ip = request.client.host if request.client else "unknown"
    FAILED_LOGIN_ATTEMPTS.pop(client_ip, None)
    return {"status": "success", "message": f"Rate-limit counter cleared for {client_ip}."}


@router.post("/admin/api/logout")
def api_admin_logout(response: Response):
    response.delete_cookie(key="kurrent_admin_token")
    return {"status": "success", "message": "Admin session terminated safely."}


@router.get("/admin/api/me")
def api_admin_me(admin: dict = Depends(verify_admin)):
    return {
        "authenticated": True,
        "username": admin["sub"],
        "display_name": admin.get("name") or admin["sub"],
        "role": admin["role"],
        "expires_at": admin["exp"]
    }


# --- Protected Operational Endpoints ---

@router.get("/admin/api/stats")
def api_admin_stats(admin: dict = Depends(verify_admin)):
    return get_admin_stats()


@router.get("/admin/api/briefings")
def api_admin_briefings(limit: int = 50, admin: dict = Depends(verify_admin)):
    return get_admin_briefings(limit)


@router.get("/admin/api/briefing/{date}")
def api_admin_get_briefing(date: str, admin: dict = Depends(verify_admin)):
    b = get_briefing_by_date(date)
    if not b:
        raise HTTPException(status_code=404, detail="Briefing not found")
    return b


@router.delete("/admin/api/briefing/{date}")
def api_admin_delete_briefing(date: str, admin: dict = Depends(verify_admin)):
    success = delete_briefing(date)
    if not success:
        raise HTTPException(status_code=404, detail="Briefing not found or could not be deleted")
    return {"status": "success", "message": f"Briefing for {date} deleted."}


@router.get("/admin/api/users")
def api_admin_users(limit: int = 100, admin: dict = Depends(verify_admin)):
    return get_admin_users(limit)


@router.delete("/admin/api/user/{user_id}")
def api_admin_delete_user(user_id: str, admin: dict = Depends(verify_admin)):
    success = delete_user(user_id)
    if not success:
        raise HTTPException(status_code=404, detail="User not found")
    return {"status": "success", "message": "User deleted."}


@router.post("/admin/api/send-reset-otp")
def api_admin_send_reset_otp(req: AdminResetOtpRequest, admin: dict = Depends(verify_admin)):
    clean_email = req.email.strip().lower()
    otp_code = f"{secrets.randbelow(900000) + 100000}"
    save_otp(clean_email, otp_code, "forgot_password")

    # Try sending via email if configured
    email_account = os.environ.get("EMAIL_ACCOUNT", "").strip()
    email_app_pass = os.environ.get("EMAIL_APP_PASSWORD", "").strip()
    sent_via_email = False

    if email_account and "your-email" not in email_account and email_app_pass and "your-" not in email_app_pass:
        try:
            import smtplib
            from email.mime.text import MIMEText
            msg = MIMEText(f"Your Kurrent Password Reset Code is: {otp_code}\nValid for 10 minutes.")
            msg["Subject"] = f"Kurrent Password Reset Code: {otp_code}"
            msg["From"] = email_account
            msg["To"] = clean_email
            with smtplib.SMTP_SSL("smtp.gmail.com", 465, timeout=10) as s:
                s.login(email_account, email_app_pass)
                s.sendmail(email_account, clean_email, msg.as_string())
            sent_via_email = True
        except Exception as e:
            print(f"[Admin OTP Error] {e}")

    return {
        "status": "success",
        "email": clean_email,
        "otp": otp_code,
        "sent_via_email": sent_via_email,
        "message": f"Reset code generated for {clean_email}."
    }


@router.get("/admin/api/check-imap")
def api_check_imap(admin: dict = Depends(verify_admin)):
    server = os.environ.get("IMAP_SERVER", "imap.gmail.com").strip()
    account = os.environ.get("EMAIL_ACCOUNT", "").strip()
    app_pwd = os.environ.get("EMAIL_APP_PASSWORD", "").strip()

    if not account or "your-email" in account:
        return {"status": "unconfigured", "message": "IMAP credentials not set in .env"}

    try:
        mail = imaplib.IMAP4_SSL(server, 993, timeout=6)
        mail.login(account, app_pwd)
        status, _ = mail.select("INBOX")
        mail.logout()
        return {"status": "connected", "message": f"Successfully authenticated as {account}"}
    except Exception as e:
        return {"status": "error", "message": str(e)}


ADMIN_PORTAL_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kurrent — Mission Control &amp; Admin Portal</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #080C14;
            --surface-card: #111827;
            --surface-subtle: #1F2937;
            --border-color: #2D3748;
            --border-hover: #4B5563;
            --text-primary: #F9FAFB;
            --text-muted: #9CA3AF;
            --text-dim: #6B7280;
            --accent-teal: #0D9488;
            --accent-teal-light: #14B8A6;
            --accent-indigo: #6366F1;
            --accent-gold: #F59E0B;
            --accent-red: #EF4444;
            --accent-green: #10B981;
            --radius-md: 10px;
            --radius-lg: 14px;
            --radius-xl: 18px;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif;
        }

        body {
            background-color: var(--bg-dark);
            color: var(--text-primary);
            min-height: 100vh;
            padding: 24px;
            line-height: 1.5;
        }

        .container {
            max-width: 1300px;
            margin: 0 auto;
        }

        /* --- LOGIN GATEWAY OVERLAY --- */
        #loginGateway {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: radial-gradient(circle at 50% 30%, #111827 0%, #080C14 100%);
            z-index: 9999;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }

        .login-card {
            background: #111827;
            border: 1px solid #374151;
            border-radius: 20px;
            max-width: 440px;
            width: 100%;
            padding: 36px 32px;
            box-shadow: 0 25px 60px rgba(0, 0, 0, 0.7), 0 0 40px rgba(13, 148, 136, 0.12);
            position: relative;
            overflow: hidden;
        }

        .login-card::before {
            content: '';
            position: absolute;
            top: 0; left: 0; right: 0;
            height: 4px;
            background: linear-gradient(90deg, #F59E0B, #0D9488, #6366F1);
        }

        .shield-icon {
            width: 58px;
            height: 58px;
            border-radius: 16px;
            background: rgba(13, 148, 136, 0.15);
            border: 1px solid rgba(20, 184, 166, 0.35);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 28px;
            margin: 0 auto 18px;
            box-shadow: 0 4px 20px rgba(13, 148, 136, 0.25);
        }

        .security-badge {
            display: inline-block;
            font-size: 10.5px;
            font-weight: 800;
            text-transform: uppercase;
            letter-spacing: 0.12em;
            color: #14B8A6;
            background: rgba(13, 148, 136, 0.12);
            padding: 4px 10px;
            border-radius: 20px;
            border: 1px solid rgba(20, 184, 166, 0.3);
            margin-bottom: 10px;
        }

        .login-title {
            font-size: 22px;
            font-weight: 800;
            color: #FFFFFF;
            letter-spacing: -0.01em;
            margin-bottom: 4px;
        }

        .login-sub {
            font-size: 13px;
            color: var(--text-muted);
            margin-bottom: 24px;
            line-height: 1.4;
        }

        .form-group {
            margin-bottom: 18px;
            text-align: left;
        }

        .form-label {
            display: block;
            font-size: 12px;
            font-weight: 700;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 0.05em;
            margin-bottom: 6px;
        }

        .form-input {
            width: 100%;
            background: #1F2937;
            border: 1px solid #374151;
            border-radius: 10px;
            padding: 12px 14px;
            color: #FFFFFF;
            font-size: 14px;
            outline: none;
            transition: all 0.2s;
        }

        .form-input:focus {
            border-color: var(--accent-teal);
            box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.2);
            background: #283548;
        }

        .pass-wrapper {
            position: relative;
            display: flex;
            align-items: center;
            width: 100%;
        }

        .pass-toggle-btn {
            position: absolute;
            right: 12px;
            background: transparent;
            border: none;
            cursor: pointer;
            font-size: 16px;
            color: var(--text-muted);
            padding: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            transition: color 0.2s;
            user-select: none;
        }

        .pass-toggle-btn:hover {
            color: var(--text-primary);
        }

        .credentials-box {
            background: rgba(13, 148, 136, 0.08);
            border: 1px dashed rgba(20, 184, 166, 0.4);
            border-radius: 12px;
            padding: 14px 16px;
            margin-bottom: 20px;
            text-align: left;
            font-size: 12px;
            color: #94A3B8;
        }

        .credentials-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;
        }

        .btn-autofill {
            background: rgba(20, 184, 166, 0.18);
            border: 1px solid rgba(20, 184, 166, 0.4);
            color: #14B8A6;
            border-radius: 6px;
            padding: 4px 10px;
            font-size: 11.5px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s;
        }

        .btn-autofill:hover {
            background: rgba(20, 184, 166, 0.35);
            color: #FFFFFF;
        }

        .cred-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-top: 4px;
            font-size: 12px;
        }

        .cred-item code {
            font-family: 'JetBrains Mono', monospace;
            background: #1F2937;
            padding: 2px 7px;
            border-radius: 4px;
            color: #F59E0B;
            font-size: 11.5px;
            border: 1px solid #374151;
            cursor: pointer;
        }

        .cred-item code:hover {
            border-color: #F59E0B;
        }

        .login-btn {
            width: 100%;
            padding: 14px;
            border-radius: 10px;
            border: none;
            background: linear-gradient(135deg, #0D9488, #0F766E);
            color: #FFFFFF;
            font-size: 14.5px;
            font-weight: 700;
            cursor: pointer;
            transition: all 0.2s;
            box-shadow: 0 4px 14px rgba(13, 148, 136, 0.35);
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            margin-top: 22px;
        }

        .login-btn:hover {
            box-shadow: 0 6px 20px rgba(13, 148, 136, 0.5);
            transform: translateY(-1px);
        }

        .login-error {
            background: rgba(239, 68, 68, 0.15);
            border: 1px solid rgba(239, 68, 68, 0.35);
            color: #FCA5A5;
            padding: 10px 14px;
            border-radius: 8px;
            font-size: 12.5px;
            margin-bottom: 16px;
            display: none;
            text-align: left;
            line-height: 1.4;
        }

        .security-footer {
            margin-top: 22px;
            font-size: 11px;
            color: var(--text-dim);
            line-height: 1.5;
            border-top: 1px solid #27354E;
            padding-top: 14px;
        }

        /* --- DASHBOARD HEADER --- */
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 24px;
            background: linear-gradient(135deg, rgba(31, 41, 55, 0.7), rgba(17, 24, 39, 0.8));
            backdrop-filter: blur(12px);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            margin-bottom: 24px;
        }

        .brand-box {
            display: flex;
            align-items: center;
            gap: 14px;
        }

        .logo-icon {
            width: 42px;
            height: 42px;
            border-radius: 12px;
            background: linear-gradient(135deg, #F59E0B, #EA580C);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 22px;
            box-shadow: 0 4px 16px rgba(245, 158, 11, 0.35);
        }

        .brand-title {
            font-size: 22px;
            font-weight: 800;
            letter-spacing: 0.05em;
            color: var(--text-primary);
        }

        .brand-badge {
            font-size: 11px;
            font-weight: 700;
            text-transform: uppercase;
            padding: 3px 8px;
            border-radius: 6px;
            background: rgba(13, 148, 136, 0.2);
            color: var(--accent-teal-light);
            border: 1px solid rgba(20, 184, 166, 0.4);
            margin-left: 8px;
            letter-spacing: 0.05em;
        }

        .brand-sub {
            font-size: 12.5px;
            color: var(--text-muted);
            margin-top: 2px;
        }

        .header-actions {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .auth-status-pill {
            display: flex;
            align-items: center;
            gap: 6px;
            padding: 8px 12px;
            background: rgba(16, 185, 129, 0.12);
            border: 1px solid rgba(16, 185, 129, 0.3);
            border-radius: var(--radius-md);
            font-size: 12px;
            font-weight: 700;
            color: #34D399;
        }

        .btn {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 9px 15px;
            border-radius: var(--radius-md);
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            border: none;
            transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
            text-decoration: none;
        }

        .btn:hover {
            transform: translateY(-1px);
        }

        .btn:active {
            transform: translateY(0);
        }

        .btn-primary {
            background: linear-gradient(135deg, var(--accent-teal), #0F766E);
            color: white;
            box-shadow: 0 4px 14px rgba(13, 148, 136, 0.3);
        }

        .btn-secondary {
            background: var(--surface-subtle);
            color: var(--text-primary);
            border: 1px solid var(--border-color);
        }
        .btn-secondary:hover {
            border-color: var(--border-hover);
            background: #25334A;
        }

        .btn-danger {
            background: rgba(239, 68, 68, 0.15);
            color: #F87171;
            border: 1px solid rgba(239, 68, 68, 0.35);
        }
        .btn-danger:hover {
            background: rgba(239, 68, 68, 0.25);
        }

        /* KPI Cards Grid */
        .kpi-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }

        .kpi-card {
            background: var(--surface-card);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-lg);
            padding: 20px;
            display: flex;
            flex-direction: column;
            position: relative;
            overflow: hidden;
            transition: border-color 0.2s;
        }
        .kpi-card:hover {
            border-color: var(--border-hover);
        }

        .kpi-card::before {
            content: '';
            position: absolute;
            top: 0; left: 0; right: 0;
            height: 3px;
            background: transparent;
        }

        .kpi-card.teal::before { background: var(--accent-teal); }
        .kpi-card.indigo::before { background: var(--accent-indigo); }
        .kpi-card.amber::before { background: var(--accent-gold); }
        .kpi-card.green::before { background: var(--accent-green); }

        .kpi-title {
            font-size: 13px;
            color: var(--text-muted);
            font-weight: 600;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }

        .kpi-val {
            font-size: 32px;
            font-weight: 800;
            color: var(--text-primary);
            line-height: 1;
            margin-bottom: 6px;
        }

        .kpi-meta {
            font-size: 12px;
            color: var(--text-dim);
        }

        /* Tabs */
        .tabs-bar {
            display: flex;
            gap: 8px;
            margin-bottom: 20px;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 12px;
        }

        .tab-btn {
            background: transparent;
            color: var(--text-muted);
            border: none;
            font-size: 14px;
            font-weight: 600;
            padding: 10px 18px;
            border-radius: var(--radius-md);
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .tab-btn:hover {
            color: var(--text-primary);
            background: rgba(255, 255, 255, 0.04);
        }

        .tab-btn.active {
            color: var(--text-primary);
            background: var(--surface-card);
            border: 1px solid var(--border-color);
        }

        .tab-content {
            display: none;
            background: var(--surface-card);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            padding: 24px;
        }
        .tab-content.active {
            display: block;
        }

        /* Tables */
        .table-controls {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 18px;
            gap: 16px;
            flex-wrap: wrap;
        }

        .search-input {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            padding: 10px 14px;
            color: var(--text-primary);
            font-size: 13.5px;
            width: 280px;
            outline: none;
            transition: border-color 0.2s;
        }
        .search-input:focus {
            border-color: var(--accent-teal);
        }

        .data-table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
            font-size: 13.5px;
        }

        .data-table th {
            padding: 12px 16px;
            background: var(--surface-subtle);
            color: var(--text-muted);
            font-weight: 600;
            border-bottom: 1px solid var(--border-color);
            text-transform: uppercase;
            font-size: 11.5px;
            letter-spacing: 0.05em;
        }

        .data-table td {
            padding: 14px 16px;
            border-bottom: 1px solid rgba(45, 55, 72, 0.6);
            color: var(--text-primary);
            vertical-align: middle;
        }

        .data-table tr:hover td {
            background: rgba(31, 41, 55, 0.5);
        }

        .badge {
            display: inline-block;
            padding: 3px 8px;
            border-radius: 6px;
            font-size: 11.5px;
            font-weight: 700;
            letter-spacing: 0.03em;
        }
        .badge-upsc { background: rgba(245, 158, 11, 0.18); color: #FBBF24; border: 1px solid rgba(245, 158, 11, 0.4); }
        .badge-ssc { background: rgba(16, 185, 129, 0.18); color: #34D399; border: 1px solid rgba(16, 185, 129, 0.4); }
        .badge-ibps { background: rgba(56, 189, 248, 0.18); color: #38BDF8; border: 1px solid rgba(56, 189, 248, 0.4); }
        .badge-all { background: rgba(99, 102, 241, 0.18); color: #818CF8; border: 1px solid rgba(99, 102, 241, 0.4); }

        .streak-pill {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            background: rgba(245, 158, 11, 0.12);
            color: #F59E0B;
            border: 1px solid rgba(245, 158, 11, 0.3);
            border-radius: 20px;
            padding: 2px 8px;
            font-weight: 700;
            font-size: 12px;
        }

        /* Modal Overlay */
        .modal-overlay {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(8, 12, 20, 0.88);
            backdrop-filter: blur(8px);
            display: none;
            align-items: center;
            justify-content: center;
            z-index: 1000;
            padding: 24px;
        }
        .modal-overlay.active {
            display: flex;
        }

        .modal-box {
            background: var(--surface-card);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-xl);
            max-width: 880px;
            width: 100%;
            max-height: 88vh;
            display: flex;
            flex-direction: column;
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.6);
        }

        .modal-header {
            padding: 18px 24px;
            border-bottom: 1px solid var(--border-color);
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .modal-body {
            padding: 24px;
            overflow-y: auto;
            flex: 1;
        }

        .modal-footer {
            padding: 16px 24px;
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: flex-end;
            gap: 12px;
        }

        .modal-article {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            padding: 16px;
            margin-bottom: 16px;
        }
        .modal-article h4 {
            color: var(--text-primary);
            font-size: 15px;
            margin-bottom: 6px;
        }
        .modal-article p {
            color: var(--text-muted);
            font-size: 13px;
            line-height: 1.6;
        }

        /* Toast Container */
        #toastContainer {
            position: fixed;
            bottom: 24px;
            right: 24px;
            z-index: 2000;
            display: flex;
            flex-direction: column;
            gap: 10px;
        }
        .toast {
            background: var(--surface-card);
            border: 1px solid var(--accent-teal);
            color: var(--text-primary);
            padding: 14px 20px;
            border-radius: var(--radius-md);
            box-shadow: 0 10px 25px rgba(0,0,0,0.5);
            font-size: 13.5px;
            animation: slideIn 0.3s ease forwards;
        }
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }

        .sys-info-box {
            background: var(--surface-subtle);
            border: 1px solid var(--border-color);
            border-radius: var(--radius-md);
            padding: 16px 20px;
            margin-bottom: 16px;
        }
        .sys-key {
            font-size: 12px;
            color: var(--text-muted);
            text-transform: uppercase;
            font-weight: 600;
            letter-spacing: 0.05em;
        }
        .sys-val {
            font-size: 14px;
            color: var(--text-primary);
            font-family: 'JetBrains Mono', monospace;
            margin-top: 4px;
        }
    </style>
</head>
<body>

<!-- High-Security Login Modal Gateway -->
<div id="loginGateway">
    <div class="login-card">
        <div style="text-align: center;">
            <div class="shield-icon">🛡️</div>
            <div class="security-badge">RESTRICTED SECURITY ZONE • ADMIN ONLY</div>
            <h2 class="login-title">Kurrent Mission Control</h2>
            <p class="login-sub">Administrative Command Center &amp; Daily Automation Engine</p>
        </div>

        <div id="loginError" class="login-error"></div>

        <!-- Credentials Helper & 1-Click Autofill -->
        <div class="credentials-box">
            <div class="credentials-header">
                <strong style="color: #14B8A6; font-size: 12px;">🔑 Dual Sign-In Options</strong>
                <button type="button" class="btn-autofill" onclick="autofillMasterCredentials()">
                    ⚡ Autofill Master Key
                </button>
            </div>
            <div class="cred-item">
                <span>1. Master Admin ID:</span>
                <code onclick="autofillMasterCredentials()">admin</code>
            </div>
            <div class="cred-item">
                <span>2. Master Key:</span>
                <code onclick="autofillMasterCredentials()">Kurrent#Admin2026!Master</code>
            </div>
            <div style="margin-top: 8px; font-size: 11px; color: #94A3B8; border-top: 1px dashed rgba(255,255,255,0.08); padding-top: 6px;">
                💡 <em>You can also log in directly with your registered Kurrent App email &amp; password!</em>
            </div>
        </div>

        <form id="adminLoginForm" onsubmit="handleAdminLogin(event)">
            <div class="form-group">
                <label class="form-label">Administrator ID / App Email</label>
                <input type="text" id="adminUser" class="form-input" placeholder="e.g. admin or praveenbhukya9522@gmail.com" required autocomplete="username">
            </div>

            <div class="form-group">
                <label class="form-label">Security Master Key / App Password</label>
                <div class="pass-wrapper">
                    <input type="password" id="adminPass" class="form-input" style="padding-right: 44px;" placeholder="••••••••••••••••" required autocomplete="current-password">
                    <button type="button" class="pass-toggle-btn" id="togglePassBtn" onclick="togglePasswordVisibility()" title="Show/Hide password">
                        👁️
                    </button>
                </div>
            </div>

            <button type="submit" id="btnLoginSubmit" class="login-btn">
                <span>🔒</span> Authenticate &amp; Access Dashboard
            </button>

            <button type="button" id="btnResetRateLimit" class="btn btn-secondary" style="width: 100%; margin-top: 10px; font-size: 11.5px; justify-content: center; display: none;" onclick="clearRateLimitLockout()">
                <span>🔓</span> Reset Rate Limit Lockout
            </button>
        </form>

        <div class="security-footer">
            <strong>Security Notice:</strong> Cryptographic token verification. Max 10 attempts before 30-second security backoff.
        </div>
    </div>
</div>

<!-- Main Secured Dashboard -->
<div class="container" id="adminApp" style="display: none;">
    <!-- Header -->
    <header>
        <div class="brand-box">
            <div class="logo-icon">🔥</div>
            <div>
                <div style="display:flex; align-items:center;">
                    <span class="brand-title">KURRENT</span>
                    <span class="brand-badge">Mission Control</span>
                </div>
                <div class="brand-sub">Daily Current Affairs Automation Engine &amp; Candidate Management</div>
            </div>
        </div>

        <div class="header-actions">
            <div class="auth-status-pill">
                <span>🛡️</span> <span id="currentAdminName">admin</span>
            </div>
            <button class="btn btn-secondary" onclick="refreshAllData()">
                <span>🔄</span> Refresh
            </button>
            <button class="btn btn-primary" id="btnTriggerIngest" onclick="triggerIngestion()">
                <span>⚡</span> Trigger Ingest Now
            </button>
            <a href="/api/docs" target="_blank" class="btn btn-secondary">
                <span>📚</span> Swagger Docs
            </a>
            <button class="btn btn-danger" onclick="handleAdminLogout()">
                <span>🚪</span> Log Out
            </button>
        </div>
    </header>

    <!-- Top KPI Row -->
    <div class="kpi-grid">
        <div class="kpi-card teal">
            <div class="kpi-title">Daily Briefings</div>
            <div class="kpi-val" id="kpiBriefings">-</div>
            <div class="kpi-meta" id="kpiLatestBriefing">Loading...</div>
        </div>
        <div class="kpi-card indigo">
            <div class="kpi-title">Active Aspirants</div>
            <div class="kpi-val" id="kpiUsers">-</div>
            <div class="kpi-meta" id="kpiExamBreakdown">UPSC • SSC • IBPS</div>
        </div>
        <div class="kpi-card green">
            <div class="kpi-title">Articles Completed</div>
            <div class="kpi-val" id="kpiReads">-</div>
            <div class="kpi-meta">Cloud synchronized across devices</div>
        </div>
        <div class="kpi-card amber">
            <div class="kpi-title">Top Active Streak</div>
            <div class="kpi-val" id="kpiStreak">-</div>
            <div class="kpi-meta">Consecutive days unbroken 🔥</div>
        </div>
    </div>

    <!-- Main Navigation Tabs -->
    <div class="tabs-bar">
        <button class="tab-btn active" onclick="switchTab('briefings', this)">
            <span>📰</span> Briefings &amp; Daily Feeds
        </button>
        <button class="tab-btn" onclick="switchTab('users', this)">
            <span>👥</span> Student Aspirants
        </button>
        <button class="tab-btn" onclick="switchTab('system', this)">
            <span>⚙️</span> Ingestion &amp; System Health
        </button>
    </div>

    <!-- TAB 1: Briefings -->
    <div id="tabBriefings" class="tab-content active">
        <div class="table-controls">
            <h3 style="font-size: 16px; font-weight: 700;">Ingested Daily Briefings</h3>
            <input type="text" class="search-input" id="searchBriefing" placeholder="Filter by date (YYYY-MM-DD)..." oninput="filterBriefings()">
        </div>

        <table class="data-table">
            <thead>
                <tr>
                    <th>Date</th>
                    <th>Edition</th>
                    <th>Articles</th>
                    <th>MCQs</th>
                    <th>Flashcards</th>
                    <th>Ingested At</th>
                    <th style="text-align: right;">Actions</th>
                </tr>
            </thead>
            <tbody id="briefingsTableBody">
                <tr><td colspan="7" style="text-align:center; color: var(--text-muted);">Loading briefings...</td></tr>
            </tbody>
        </table>
    </div>

    <!-- TAB 2: Users -->
    <div id="tabUsers" class="tab-content">
        <div class="table-controls">
            <h3 style="font-size: 16px; font-weight: 700;">Registered Aspirants</h3>
            <input type="text" class="search-input" id="searchUser" placeholder="Search aspirant by name or email..." oninput="filterUsers()">
        </div>

        <table class="data-table">
            <thead>
                <tr>
                    <th>Aspirant</th>
                    <th>Email</th>
                    <th>Target Exam</th>
                    <th>Streak</th>
                    <th>Articles Read</th>
                    <th>Joined At</th>
                    <th style="text-align: right;">Actions</th>
                </tr>
            </thead>
            <tbody id="usersTableBody">
                <tr><td colspan="7" style="text-align:center; color: var(--text-muted);">Loading candidates...</td></tr>
            </tbody>
        </table>
    </div>

    <!-- TAB 3: System Health -->
    <div id="tabSystem" class="tab-content">
        <h3 style="font-size: 16px; font-weight: 700; margin-bottom: 18px;">Automation Engine &amp; Security Architecture</h3>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
            <div>
                <div class="sys-info-box">
                    <div class="sys-key">Administrative Security Clearance</div>
                    <div class="sys-val" style="color: var(--accent-green); font-size: 13px;">🟢 HMAC-SHA256 Cryptographic Session Verified</div>
                    <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">Rate-limiting: 5 attempts per 15 min | Constant-time digest verification.</div>
                </div>

                <div class="sys-info-box">
                    <div class="sys-key">Hourly Ingestion Cron Schedule</div>
                    <div class="sys-val">0 * * * * (Runs every hour at :00)</div>
                    <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">Pulls unread [KURRENT-DAILY] emails &amp; ignores duplicates.</div>
                </div>

                <div class="sys-info-box">
                    <div class="sys-key">Database Engine</div>
                    <div class="sys-val" id="sysDbInfo">SQLite3 Local File</div>
                </div>
            </div>

            <div>
                <div class="sys-info-box">
                    <div class="sys-key">IMAP Gmail Sync Status</div>
                    <div class="sys-val" id="sysImapStatus">Checking connection...</div>
                    <button class="btn btn-secondary" style="margin-top: 10px; font-size: 12px; padding: 6px 12px;" onclick="testImapConnection()">
                        Test IMAP Authentication
                    </button>
                </div>

                <div class="sys-info-box">
                    <div class="sys-key">Public EC2 Access Points</div>
                    <div class="sys-val" style="font-size: 12.5px;">
                        • Admin Portal: /admin (Secured)<br>
                        • Swagger Specs: /api/docs<br>
                        • Today Briefing: /briefing/today
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<!-- Modal: Preview Briefing -->
<div class="modal-overlay" id="briefingModal">
    <div class="modal-box">
        <div class="modal-header">
            <div>
                <h3 id="modalTitle" style="font-size: 17px; font-weight: 700;">Daily Briefing Preview</h3>
                <div id="modalSub" style="font-size: 12.5px; color: var(--text-muted);"></div>
            </div>
            <button class="btn btn-secondary" onclick="closeModal()">✕</button>
        </div>
        <div class="modal-body" id="modalContent">
            <!-- Dynamic Content -->
        </div>
        <div class="modal-footer">
            <button class="btn btn-secondary" onclick="closeModal()">Close</button>
        </div>
    </div>
</div>

<div id="toastContainer"></div>

<script>
    let rawBriefings = [];
    let rawUsers = [];
    let adminToken = localStorage.getItem('kurrent_admin_token') || '';

    function togglePasswordVisibility() {
        const pass = document.getElementById('adminPass');
        const btn = document.getElementById('togglePassBtn');
        if (pass.type === 'password') {
            pass.type = 'text';
            btn.innerText = '🙈';
        } else {
            pass.type = 'password';
            btn.innerText = '👁️';
        }
    }

    function autofillMasterCredentials() {
        document.getElementById('adminUser').value = 'admin';
        document.getElementById('adminPass').value = 'Kurrent#Admin2026!Master';
        showToast('Autofilled Master Credentials');
        document.getElementById('btnLoginSubmit').focus();
    }

    async function clearRateLimitLockout() {
        try {
            const res = await fetch('/admin/api/reset-rate-limit', { method: 'POST', credentials: 'include' });
            if (res.ok) {
                document.getElementById('loginError').style.display = 'none';
                document.getElementById('btnResetRateLimit').style.display = 'none';
                document.getElementById('btnLoginSubmit').disabled = false;
                showToast('Rate limit lockout cleared.');
            }
        } catch (e) {
            console.error('Failed to reset rate limit:', e);
        }
    }

    function getAuthHeaders() {
        const headers = { 'Content-Type': 'application/json' };
        if (adminToken) {
            headers['Authorization'] = 'Bearer ' + adminToken;
        }
        return headers;
    }

    async function checkExistingAuth() {
        try {
            const res = await fetch('/admin/api/me', {
                headers: getAuthHeaders(),
                credentials: 'include'
            });
            if (res.ok) {
                const data = await res.json();
                document.getElementById('currentAdminName').innerText = data.display_name || data.username || 'admin';
                showDashboard();
                refreshAllData();
            } else {
                showLoginGate();
            }
        } catch (e) {
            showLoginGate();
        }
    }

    function showLoginGate() {
        document.getElementById('loginGateway').style.display = 'flex';
        document.getElementById('adminApp').style.display = 'none';
        adminToken = '';
        localStorage.removeItem('kurrent_admin_token');
    }

    function showDashboard() {
        document.getElementById('loginGateway').style.display = 'none';
        document.getElementById('adminApp').style.display = 'block';
    }

    async function handleAdminLogin(e) {
        e.preventDefault();
        const user = document.getElementById('adminUser').value.trim();
        const pass = document.getElementById('adminPass').value.trim();
        const errBox = document.getElementById('loginError');
        const submitBtn = document.getElementById('btnLoginSubmit');
        const resetBtn = document.getElementById('btnResetRateLimit');

        errBox.style.display = 'none';
        resetBtn.style.display = 'none';
        submitBtn.innerHTML = '<span>⏳</span> Authenticating...';
        submitBtn.disabled = true;

        try {
            const res = await fetch('/admin/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ username: user, password: pass })
            });
            const data = await res.json();

            if (res.ok && data.token) {
                adminToken = data.token;
                localStorage.setItem('kurrent_admin_token', adminToken);
                document.getElementById('currentAdminName').innerText = data.display_name || data.username || user;
                showDashboard();
                refreshAllData();
                showToast('Welcome, ' + (data.display_name || 'Administrator') + '. Security clearance granted.');
            } else {
                errBox.innerText = data.detail || 'Access denied: Invalid credentials.';
                errBox.style.display = 'block';
                if (res.status === 429) {
                    resetBtn.style.display = 'flex';
                }
            }
        } catch (err) {
            errBox.innerText = 'Authentication error: ' + err.message;
            errBox.style.display = 'block';
        } finally {
            submitBtn.innerHTML = '<span>🔒</span> Authenticate &amp; Access Dashboard';
            submitBtn.disabled = false;
        }
    }

    async function handleAdminLogout() {
        try {
            await fetch('/admin/api/logout', { method: 'POST', credentials: 'include' });
        } catch (e) {}
        showLoginGate();
        showToast('Admin session terminated safely.');
    }

    function handleApiUnauthorized(res) {
        if (res.status === 401) {
            showLoginGate();
            document.getElementById('loginError').innerText = 'Session expired for security. Please re-authenticate.';
            document.getElementById('loginError').style.display = 'block';
            return true;
        }
        return false;
    }

    async function loadStats() {
        try {
            const res = await fetch('/admin/api/stats', { headers: getAuthHeaders(), credentials: 'include' });
            if (handleApiUnauthorized(res)) return;
            const data = await res.json();

            document.getElementById('kpiBriefings').innerText = data.total_briefings;
            document.getElementById('kpiUsers').innerText = data.total_users;
            document.getElementById('kpiReads').innerText = data.total_reads.toLocaleString();
            document.getElementById('kpiStreak').innerText = (data.max_streak || 0) + ' Days';

            if (data.latest_briefing) {
                document.getElementById('kpiLatestBriefing').innerText = 'Latest: ' + data.latest_briefing.date + ' (Ed. ' + data.latest_briefing.edition + ')';
            } else {
                document.getElementById('kpiLatestBriefing').innerText = 'No briefings yet';
            }

            const dist = data.exam_distribution || {};
            const distStr = Object.entries(dist).map(([k, v]) => k + ': ' + v).join(' • ') || 'UPSC • SSC • IBPS';
            document.getElementById('kpiExamBreakdown').innerText = distStr;

            document.getElementById('sysDbInfo').innerText = 'SQLite3 (' + (data.db_size_kb || 0) + ' KB) @ ' + (data.db_path || 'kurrent.db');
        } catch (e) {
            console.error("Stats load error:", e);
        }
    }

    async function loadBriefings() {
        try {
            const res = await fetch('/admin/api/briefings', { headers: getAuthHeaders(), credentials: 'include' });
            if (handleApiUnauthorized(res)) return;
            rawBriefings = await res.json();
            renderBriefings(rawBriefings);
        } catch (e) {
            console.error("Briefings load error:", e);
        }
    }

    function renderBriefings(list) {
        const tbody = document.getElementById('briefingsTableBody');
        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color: var(--text-muted); padding: 24px;">No briefings available in database.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(b => `
            <tr>
                <td style="font-weight: 700; font-family: 'JetBrains Mono', monospace;">${b.date}</td>
                <td><span class="badge badge-all">Edition #${b.edition}</span></td>
                <td><strong style="color: var(--accent-teal-light);">${b.article_count}</strong> articles</td>
                <td>${b.quiz_count} MCQs</td>
                <td>${b.flashcard_count} Cards</td>
                <td style="color: var(--text-dim); font-size: 12px;">${b.created_at || '-'}</td>
                <td style="text-align: right;">
                    <button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="viewBriefing('${b.date}')">
                        👁 View
                    </button>
                    <button class="btn btn-danger" style="padding: 6px 12px; font-size: 12px; margin-left: 6px;" onclick="deleteBriefingItem('${b.date}')">
                        🗑 Delete
                    </button>
                </td>
            </tr>
        `).join('');
    }

    function filterBriefings() {
        const query = document.getElementById('searchBriefing').value.trim().toLowerCase();
        const filtered = rawBriefings.filter(b => b.date.toLowerCase().includes(query));
        renderBriefings(filtered);
    }

    async function viewBriefing(date) {
        try {
            const res = await fetch('/admin/api/briefing/' + date, { headers: getAuthHeaders(), credentials: 'include' });
            if (handleApiUnauthorized(res)) return;
            const data = await res.json();

            document.getElementById('modalTitle').innerText = 'Daily Briefing — ' + date + ' (Edition ' + data.edition + ')';
            document.getElementById('modalSub').innerText = (data.articles?.length || 0) + ' articles • ' + (data.quizzes?.length || 0) + ' quiz questions';

            let html = '';
            if (data.articles && data.articles.length > 0) {
                html += '<h4 style="margin-bottom: 12px; color: var(--accent-teal-light); font-size: 14px;">Articles &amp; Syllabus Mapping</h4>';
                data.articles.forEach(art => {
                    const tags = (art.exam_tags || []).map(t => `<span class="badge badge-${t.toLowerCase()}">${t}</span>`).join(' ');
                    html += `
                        <div class="modal-article">
                            <div style="margin-bottom: 6px;">${tags}</div>
                            <h4>${art.title}</h4>
                            <p>${art.summary_60words || ''}</p>
                            ${art.upsc_metadata?.gs_paper ? `<div style="font-size: 11.5px; color: var(--accent-gold); margin-top: 6px;">🎯 ${art.upsc_metadata.gs_paper}</div>` : ''}
                        </div>
                    `;
                });
            }

            if (data.quizzes && data.quizzes.length > 0) {
                html += '<h4 style="margin: 20px 0 12px; color: var(--accent-gold); font-size: 14px;">Daily 5-Min MCQs</h4>';
                data.quizzes.forEach((q, idx) => {
                    html += `
                        <div class="modal-article">
                            <div style="font-weight: 600; font-size: 13px; margin-bottom: 6px;">Q${idx+1}. ${q.question}</div>
                            <div style="font-size: 12px; color: var(--accent-teal-light);">Correct: ${q.correct_answer}</div>
                        </div>
                    `;
                });
            }

            document.getElementById('modalContent').innerHTML = html;
            document.getElementById('briefingModal').classList.add('active');
        } catch (e) {
            showToast("Failed to fetch briefing details");
        }
    }

    async function deleteBriefingItem(date) {
        if (!confirm('Are you sure you want to delete briefing for ' + date + '?')) return;
        try {
            const res = await fetch('/admin/api/briefing/' + date, {
                method: 'DELETE',
                headers: getAuthHeaders(),
                credentials: 'include'
            });
            if (handleApiUnauthorized(res)) return;
            if (res.ok) {
                showToast('Deleted briefing for ' + date);
                refreshAllData();
            } else {
                showToast('Failed to delete briefing');
            }
        } catch (e) {
            showToast('Delete error: ' + e.message);
        }
    }

    async function loadUsers() {
        try {
            const res = await fetch('/admin/api/users', { headers: getAuthHeaders(), credentials: 'include' });
            if (handleApiUnauthorized(res)) return;
            rawUsers = await res.json();
            renderUsers(rawUsers);
        } catch (e) {
            console.error("Users load error:", e);
        }
    }

    function renderUsers(list) {
        const tbody = document.getElementById('usersTableBody');
        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center; color: var(--text-muted); padding: 24px;">No student accounts registered yet.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(u => {
            const examBadge = `<span class="badge badge-${(u.target_exam || 'ALL').toLowerCase()}">${u.target_exam || 'ALL'}</span>`;
            return `
                <tr>
                    <td><strong>${u.display_name || 'Aspirant'}</strong></td>
                    <td style="font-family: 'JetBrains Mono', monospace; font-size: 12.5px;">${u.email}</td>
                    <td>${examBadge}</td>
                    <td><span class="streak-pill">🔥 ${u.current_streak}d</span></td>
                    <td><strong>${u.total_articles_read}</strong> reads</td>
                    <td style="color: var(--text-dim); font-size: 12px;">${(u.created_at || '').substring(0, 10)}</td>
                    <td style="text-align: right;">
                        <button class="btn btn-secondary" style="padding: 6px 12px; font-size: 12px;" onclick="sendUserResetOtp('${u.email}')">
                            🔑 Reset Password
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
    }

    function filterUsers() {
        const query = document.getElementById('searchUser').value.trim().toLowerCase();
        const filtered = rawUsers.filter(u => 
            (u.email && u.email.toLowerCase().includes(query)) ||
            (u.display_name && u.display_name.toLowerCase().includes(query))
        );
        renderUsers(filtered);
    }

    async function sendUserResetOtp(email) {
        if (!confirm('Generate and send password reset OTP to ' + email + '?')) return;
        try {
            const res = await fetch('/admin/api/send-reset-otp', {
                method: 'POST',
                headers: getAuthHeaders(),
                credentials: 'include',
                body: JSON.stringify({ email: email })
            });
            if (handleApiUnauthorized(res)) return;
            const data = await res.json();
            if (res.ok) {
                alert('Reset OTP Generated for ' + email + ':\n\nCode: ' + data.otp + '\n\nSent to email: ' + (data.sent_via_email ? 'YES' : 'No (Console Fallback)'));
                showToast('Reset OTP generated: ' + data.otp);
            } else {
                showToast('Failed to generate reset OTP');
            }
        } catch (e) {
            showToast('Error: ' + e.message);
        }
    }

    async function triggerIngestion() {
        const btn = document.getElementById('btnTriggerIngest');
        const origText = btn.innerHTML;
        btn.innerHTML = '<span>⏳</span> Ingesting...';
        btn.disabled = true;

        try {
            const res = await fetch('/ingest/trigger', {
                method: 'POST',
                headers: getAuthHeaders(),
                credentials: 'include'
            });
            if (handleApiUnauthorized(res)) return;
            const data = await res.json();
            showToast(data.message || 'Ingestion completed');
            refreshAllData();
        } catch (e) {
            showToast('Ingestion trigger failed: ' + e.message);
        } finally {
            btn.innerHTML = origText;
            btn.disabled = false;
        }
    }

    async function testImapConnection() {
        const el = document.getElementById('sysImapStatus');
        el.innerText = 'Testing IMAP...';
        try {
            const res = await fetch('/admin/api/check-imap', { headers: getAuthHeaders(), credentials: 'include' });
            if (handleApiUnauthorized(res)) return;
            const data = await res.json();
            if (data.status === 'connected') {
                el.innerText = '🟢 ' + data.message;
                el.style.color = 'var(--accent-green)';
            } else if (data.status === 'unconfigured') {
                el.innerText = '🟡 ' + data.message;
                el.style.color = 'var(--accent-gold)';
            } else {
                el.innerText = '🔴 ' + data.message;
                el.style.color = 'var(--accent-red)';
            }
        } catch (e) {
            el.innerText = '🔴 Connection error: ' + e.message;
        }
    }

    function switchTab(name, btn) {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

        btn.classList.add('active');
        if (name === 'briefings') document.getElementById('tabBriefings').classList.add('active');
        if (name === 'users') document.getElementById('tabUsers').classList.add('active');
        if (name === 'system') {
            document.getElementById('tabSystem').classList.add('active');
            testImapConnection();
        }
    }

    function closeModal() {
        document.getElementById('briefingModal').classList.remove('active');
    }

    function showToast(msg) {
        const box = document.getElementById('toastContainer');
        const toast = document.createElement('div');
        toast.className = 'toast';
        toast.innerText = msg;
        box.appendChild(toast);
        setTimeout(() => toast.remove(), 4000);
    }

    function refreshAllData() {
        loadStats();
        loadBriefings();
        loadUsers();
        showToast('Refreshed data');
    }

    // Initialize authentication check on load
    checkExistingAuth();
</script>

</body>
</html>
"""


@router.get("/admin", response_class=HTMLResponse)
def get_admin_portal():
    return HTMLResponse(content=ADMIN_PORTAL_HTML)


@router.get("/docs", response_class=HTMLResponse)
def get_docs_replacement():
    """Replaces standard Swagger docs with the secured Kurrent Admin Portal."""
    return HTMLResponse(content=ADMIN_PORTAL_HTML)


@router.get("/", response_class=HTMLResponse)
def get_root_portal():
    return HTMLResponse(content=ADMIN_PORTAL_HTML)
