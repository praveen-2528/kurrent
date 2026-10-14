#!/bin/bash
# Kurrent EC2 1-Click Setup Script
# Run this on a clean Ubuntu 22.04 or 24.04 LTS t2.micro/t3.micro instance.

set -e

echo "=== 1. Updating packages ==="
sudo apt-get update -y
sudo apt-get install -y python3 python3-pip python3-venv git curl

echo "=== 2. Installing Python requirements ==="
pip3 install fastapi "uvicorn[standard]"

echo "=== 3. Setting up Systemd Service ==="
sudo cp kurrent.service /etc/systemd/system/kurrent.service
sudo systemctl daemon-reload
sudo systemctl enable kurrent
sudo systemctl restart kurrent

echo "=== 4. Setting up Morning Ingestion Cron (05:15 AM IST) ==="
# Note: AWS EC2 system clock is UTC. 05:15 AM IST is 23:45 UTC.
CRON_JOB="45 23 * * * /usr/bin/python3 $(pwd)/ingest.py >> $(pwd)/ingest.log 2>&1"
(crontab -l 2>/dev/null | grep -v "ingest.py" ; echo "$CRON_JOB") | crontab -

echo "=== 5. Checking Service Status ==="
sudo systemctl status kurrent --no-pager

echo ""
echo "============================================================"
echo " Kurrent Backend is now LIVE on your EC2 instance!"
echo " Test your API: curl http://localhost:8000/health"
echo " Interactive Docs: http://<YOUR-EC2-PUBLIC-IP>:8000/docs"
echo "============================================================"
