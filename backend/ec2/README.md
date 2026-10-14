# Kurrent — EC2 Setup & Deployment Guide

This guide walks you through running the entire Kurrent backend on a **single AWS EC2 instance** using **Ubuntu + Python + SQLite + FastAPI + Cron**.

---

## Why EC2 is Great for You
* **One Single Machine**: Everything lives in one place. No jumping between Lambda, API Gateway, DynamoDB, and EventBridge.
* **Dead Simple to Modify**: SSH into the server, edit files with `nano` or upload via git/scp, and test instantly in the terminal.
* **100% Free Tier**: AWS Free Tier includes **750 hours/month of `t2.micro` or `t3.micro`** (runs 24/7 all month for free for 12 months) + 30 GB EBS SSD.
* **Zero Database Configuration**: Uses **SQLite** (`kurrent.db`). No RDS, no DynamoDB capacity units, no database bill.
* **Interactive API Docs**: Built-in Swagger UI at `http://<YOUR-EC2-IP>:8000/docs`.

---

## 1. Launch Your Free-Tier EC2 Instance

1. Open the [AWS EC2 Console](https://console.aws.amazon.com/ec2/).
2. Click **Launch Instance**:
   * **Name**: `kurrent-backend`
   * **OS**: **Ubuntu 24.04 LTS** or **Ubuntu 22.04 LTS** (Free tier eligible)
   * **Instance Type**: **`t2.micro`** (or `t3.micro` if in Mumbai `ap-south-1`)
   * **Key Pair**: Create new or select existing (download the `.pem` file)
   * **Network Settings / Security Group**:
     * Allow **SSH** (Port 22) from My IP
     * Allow **Custom TCP** (Port 8000) from `0.0.0.0/0` (for Android App access)
     * *(Optional)* Allow **HTTP** (Port 80) and **HTTPS** (Port 443)
   * **Storage**: 20 GB gp3 (Free tier allows up to 30 GB)
3. Click **Launch Instance**.

---

## 2. Connect to Your EC2 Instance

Use **EC2 Instance Connect** (1-click browser terminal in AWS Console) OR SSH from your terminal:
```bash
ssh -i "your-key.pem" ubuntu@<YOUR-EC2-PUBLIC-IP>
```

---

## 3. Copy Backend Code & Run 1-Click Setup

Inside your EC2 terminal:

```bash
# 1. Clone or create the directory
mkdir -p ~/kurrent/backend/ec2
cd ~/kurrent/backend/ec2

# 2. Copy the files (or clone your repo)
# Files needed:
#   - parser.py
#   - database.py
#   - ingest.py
#   - server.py
#   - setup_ec2.sh
#   - kurrent.service
#   - .env

# 3. Create your .env file with your Gmail credentials
nano .env
```

Paste your Gmail app password into `.env`:
```text
EMAIL_ACCOUNT=your-email@gmail.com
EMAIL_APP_PASSWORD=xxxx xxxx xxxx xxxx
IMAP_SERVER=imap.gmail.com
```
*(Press `Ctrl+O`, `Enter`, then `Ctrl+X` to save in nano).*

Now run the automated setup script:
```bash
chmod +x setup_ec2.sh
./setup_ec2.sh
```

---

## 4. That's It! Your Backend is LIVE

1. **Check FastAPI Status**:
   ```bash
   sudo systemctl status kurrent
   ```
2. **Test Ingestion Manually**:
   ```bash
   python3 ingest.py
   ```
3. **Open in Browser**:
   * Swagger Documentation: `http://<YOUR-EC2-PUBLIC-IP>:8000/docs`
   * Today's Briefing: `http://<YOUR-EC2-PUBLIC-IP>:8000/briefing/today`

---

## 5. Daily Ingestion Automation
The setup script automatically registers a Linux cron job:
```cron
45 23 * * * /usr/bin/python3 /home/ubuntu/kurrent/backend/ec2/ingest.py >> /home/ubuntu/kurrent/backend/ec2/ingest.log 2>&1
```
*(Note: 23:45 UTC corresponds exactly to 05:15 AM Indian Standard Time).*
You can view the morning ingestion logs anytime:
```bash
cat ~/kurrent/backend/ec2/ingest.log
```
