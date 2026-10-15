"""
Kurrent Admin Command Center & Mission Control
Served at /admin, /docs, and /
Provides full operational visibility, ingestion triggers, briefings management, and user controls.
"""

from fastapi import APIRouter, HTTPException, Depends
from fastapi.responses import HTMLResponse
from pydantic import BaseModel
import os
import json
import imaplib
from datetime import datetime

from database import (
    get_admin_stats,
    get_admin_briefings,
    get_admin_users,
    get_briefing_by_date,
    delete_briefing,
    delete_user,
    save_otp
)
from ingest import fetch_and_ingest

router = APIRouter()


@router.get("/admin/api/stats")
def api_admin_stats():
    return get_admin_stats()


@router.get("/admin/api/briefings")
def api_admin_briefings(limit: int = 50):
    return get_admin_briefings(limit)


@router.get("/admin/api/briefing/{date}")
def api_admin_get_briefing(date: str):
    b = get_briefing_by_date(date)
    if not b:
        raise HTTPException(status_code=404, detail="Briefing not found")
    return b


@router.delete("/admin/api/briefing/{date}")
def api_admin_delete_briefing(date: str):
    success = delete_briefing(date)
    if not success:
        raise HTTPException(status_code=404, detail="Briefing not found or could not be deleted")
    return {"status": "success", "message": f"Briefing for {date} deleted."}


@router.get("/admin/api/users")
def api_admin_users(limit: int = 100):
    return get_admin_users(limit)


@router.delete("/admin/api/user/{user_id}")
def api_admin_delete_user(user_id: str):
    success = delete_user(user_id)
    if not success:
        raise HTTPException(status_code=404, detail="User not found")
    return {"status": "success", "message": "User deleted."}


class AdminResetOtpRequest(BaseModel):
    email: str


@router.post("/admin/api/send-reset-otp")
def api_admin_send_reset_otp(req: AdminResetOtpRequest):
    import secrets
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
def api_check_imap():
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
            --bg-dark: #0B0F19;
            --surface-card: #151D2F;
            --surface-subtle: #1E293B;
            --border-color: #27354E;
            --border-hover: #3B4E72;
            --text-primary: #F8FAFC;
            --text-muted: #94A3B8;
            --text-dim: #64748B;
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

        /* Top Header */
        header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 16px 24px;
            background: linear-gradient(135deg, rgba(30, 41, 59, 0.7), rgba(15, 23, 42, 0.8));
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
            gap: 12px;
        }

        .btn {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 10px 16px;
            border-radius: var(--radius-md);
            font-size: 13.5px;
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
        .btn-primary:hover {
            box-shadow: 0 6px 18px rgba(13, 148, 136, 0.45);
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
            top: 0;
            left: 0;
            right: 0;
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

        /* Navigation Tabs */
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
            font-size: 14.5px;
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

        /* Tab Content Panel */
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

        /* Table styling */
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
            border-bottom: 1px solid rgba(39, 53, 78, 0.5);
            color: var(--text-primary);
            vertical-align: middle;
        }

        .data-table tr:hover td {
            background: rgba(30, 41, 59, 0.4);
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
            background: rgba(11, 15, 25, 0.85);
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
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.5);
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

        /* Article Card Inside Modal */
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

        /* Toast notifications */
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
            box-shadow: 0 10px 25px rgba(0,0,0,0.4);
            font-size: 13.5px;
            animation: slideIn 0.3s ease forwards;
        }
        @keyframes slideIn {
            from { transform: translateX(100%); opacity: 0; }
            to { transform: translateX(0); opacity: 1; }
        }

        /* System Info Cards */
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

<div class="container">
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
            <button class="btn btn-secondary" onclick="refreshAllData()">
                <span>🔄</span> Refresh
            </button>
            <button class="btn btn-primary" id="btnTriggerIngest" onclick="triggerIngestion()">
                <span>⚡</span> Trigger Ingest Now
            </button>
            <a href="/api/docs" target="_blank" class="btn btn-secondary">
                <span>📚</span> Swagger API Docs
            </a>
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
        <h3 style="font-size: 16px; font-weight: 700; margin-bottom: 18px;">Automation Engine &amp; Cloud Environment</h3>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px;">
            <div>
                <div class="sys-info-box">
                    <div class="sys-key">Hourly Ingestion Cron Schedule</div>
                    <div class="sys-val">0 * * * * (Runs every hour at :00)</div>
                    <div style="font-size: 12px; color: var(--text-muted); margin-top: 4px;">Pulls unread [KURRENT-DAILY] emails &amp; ignores duplicates.</div>
                </div>

                <div class="sys-info-box">
                    <div class="sys-key">Database Engine</div>
                    <div class="sys-val" id="sysDbInfo">SQLite3 Local File</div>
                </div>

                <div class="sys-info-box">
                    <div class="sys-key">FastAPI Framework</div>
                    <div class="sys-val">v1.0.0 (High-performance async ASGI)</div>
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
                        • Admin Portal: /admin<br>
                        • Swagger Docs: /api/docs<br>
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

    async function loadStats() {
        try {
            const res = await fetch('/admin/api/stats');
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
            const res = await fetch('/admin/api/briefings');
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
            const res = await fetch('/admin/api/briefing/' + date);
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
            const res = await fetch('/admin/api/briefing/' + date, { method: 'DELETE' });
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
            const res = await fetch('/admin/api/users');
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: email })
            });
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
            const res = await fetch('/ingest/trigger', { method: 'POST' });
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
            const res = await fetch('/admin/api/check-imap');
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

    // Auto load on start
    loadStats();
    loadBriefings();
    loadUsers();
</script>

</body>
</html>
"""


@router.get("/admin", response_class=HTMLResponse)
def get_admin_portal():
    return HTMLResponse(content=ADMIN_PORTAL_HTML)


@router.get("/docs", response_class=HTMLResponse)
def get_docs_replacement():
    """Replaces standard Swagger docs with the interactive Kurrent Admin Portal."""
    return HTMLResponse(content=ADMIN_PORTAL_HTML)


@router.get("/", response_class=HTMLResponse)
def get_root_portal():
    return HTMLResponse(content=ADMIN_PORTAL_HTML)
