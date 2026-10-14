#!/bin/bash
# Kurrent Universal EC2 Setup Script
# Auto-detects OS: Amazon Linux 2023, Amazon Linux 2, Ubuntu, Debian, CentOS, RHEL

set -e

CURRENT_USER=$(id -un)
APP_DIR=$(pwd)

echo "=== Detected User: $CURRENT_USER | Directory: $APP_DIR ==="

echo "=== 1. Installing System Packages ==="
if command -v dnf &> /dev/null; then
    echo "Detected Amazon Linux 2023 / RHEL 9 (using dnf)..."
    dnf update -y
    dnf install -y python3 python3-pip git cronie
    systemctl enable --now crond || true
elif command -v yum &> /dev/null; then
    echo "Detected Amazon Linux 2 / CentOS (using yum)..."
    yum update -y
    yum install -y python3 python3-pip git cronie
    systemctl enable --now crond || true
elif command -v apt-get &> /dev/null; then
    echo "Detected Ubuntu / Debian (using apt-get)..."
    apt-get update -y
    apt-get install -y python3 python3-pip git cron curl
    systemctl enable --now cron || true
else
    echo "Unknown package manager. Please ensure python3 and pip are installed."
fi

echo "=== 2. Installing Python requirements ==="
pip3 install fastapi "uvicorn[standard]"

echo "=== 3. Creating and Registering Systemd Service ==="
PYTHON_BIN=$(which python3)

cat <<EOF > /tmp/kurrent.service
[Unit]
Description=Kurrent FastAPI Backend Service
After=network.target

[Service]
User=$CURRENT_USER
WorkingDirectory=$APP_DIR
ExecStart=$PYTHON_BIN -m uvicorn server:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1
EnvironmentFile=$APP_DIR/.env

[Install]
WantedBy=multi-user.target
EOF

cp /tmp/kurrent.service /etc/systemd/system/kurrent.service
systemctl daemon-reload
systemctl enable kurrent
systemctl restart kurrent

echo "=== 4. Setting up Ingestion Cron (05:15 AM IST / 23:45 UTC) ==="
CRON_JOB="45 23 * * * $PYTHON_BIN $APP_DIR/ingest.py >> $APP_DIR/ingest.log 2>&1"
(crontab -l 2>/dev/null | grep -v "ingest.py" ; echo "$CRON_JOB") | crontab -

echo "=== 5. Verifying Service Status ==="
systemctl status kurrent --no-pager

echo ""
echo "============================================================"
echo " Kurrent Backend is now LIVE on your EC2 instance!"
echo " Test your API: curl http://localhost:8000/health"
echo " Interactive Docs: http://<YOUR-EC2-PUBLIC-IP>:8000/docs"
echo "============================================================"
