# Kurrent Backend Architecture & Deployment Guide

This backend is architected to operate **100% within the AWS Free Tier** with zero recurring monthly charges.

---

## 1. System Flow & Components

```
[3:00 - 5:00 AM] Daily Email arrives in Gmail Inbox
         │
         ▼ (5:15 AM IST Cron)
[Amazon EventBridge Rule: cron(45 23 * * ? *)]
         │
         ▼
[AWS Lambda: Email Ingestion Worker]
 ├── Connects via IMAP SSL (imaplib)
 ├── Searches for "[KURRENT-DAILY]" subject
 ├── Parses sections with parser.py
 └── Batch writes to Amazon DynamoDB
         │
         ▼
[Amazon DynamoDB: kurrent_briefings]
 ├── PK: BRIEFING#YYYY-MM-DD (Master payload for sub-20ms fetch)
 └── PK: DATE#YYYY-MM-DD     (Granular articles, recalls, quizzes)
         │
         ▼
[Amazon API Gateway (HTTP API)]
 ├── GET /briefing/today
 └── GET /briefing/{date}
         │
         ▼
[Amazon CloudFront CDN] (Edge caches responses across India)
         │
         ▼
[Kurrent Android App]
```

---

## 2. AWS Free-Tier Breakdown

| AWS Resource | Always Free Allowance | Kurrent Consumption | Cost |
| :--- | :--- | :--- | :--- |
| **AWS Lambda** | 1,000,000 invocations / month<br>3.2M seconds compute time | ~30 runs/month for ingestor<br>~5,000 API calls | **\$0.00** |
| **Amazon DynamoDB** | 25 GB storage forever<br>25 WCU / 25 RCU capacity | ~15 MB / year | **\$0.00** |
| **Amazon CloudFront** | 1 TB data transfer / month forever<br>10,000,000 HTTPS requests | ~200 MB / month | **\$0.00** |
| **API Gateway (HTTP API)** | 1,000,000 requests / month | Cached at CloudFront edge | **\$0.00** |
| **Amazon EventBridge** | Unlimited scheduled rules | 1 scheduled cron rule | **\$0.00** |

---

## 3. Prerequisites: Gmail App Password (1 Minute)

To allow the Lambda function to read the morning email from Gmail without exposing your master account password:
1. Go to your **Google Account** $\rightarrow$ **Security**.
2. Turn on **2-Step Verification** (if not already enabled).
3. Search for **App passwords** (or go to `myaccount.google.com/apppasswords`).
4. Enter an app name (e.g. `Kurrent Backend`) and click **Create**.
5. Copy the generated 16-character password (e.g., `abcd efgh ijkl mnop`).

---

## 4. Deployment Options

### Option A: One-Command Deployment using AWS SAM (Recommended)

1. Open PowerShell and navigate to the `backend` folder:
   ```powershell
   cd c:\Users\manus\Desktop\Kurrent\backend
   ```
2. Build the containerless deployment package:
   ```bash
   sam build
   ```
3. Deploy to your AWS account:
   ```bash
   sam deploy --guided
   ```
   *Prompt inputs:*
   * **Stack Name**: `kurrent-backend`
   * **AWS Region**: `ap-south-1` (Mumbai, lowest latency in India)
   * **EmailAccount**: `your-email@gmail.com`
   * **EmailAppPassword**: `your-16-char-app-password`
   * Allow SAM to create IAM roles: `Y`

Once complete, SAM will output the live **API Gateway URL** (e.g., `https://xyz123.execute-api.ap-south-1.amazonaws.com`).

---

### Option B: Manual Setup via AWS Management Console

1. **DynamoDB**:
   - Go to DynamoDB $\rightarrow$ Create Table:
     - Table Name: `kurrent_briefings`
     - Partition Key (`PK`): String
     - Sort Key (`SK`): String
     - Billing: Pay-per-request / On-Demand

2. **Ingestor Lambda**:
   - Function Name: `kurrent-email-worker`
   - Runtime: `Python 3.11`
   - Paste `parser.py` and `lambda_email_worker.py` into the code editor.
   - Set Environment Variables:
     - `EMAIL_ACCOUNT`
     - `EMAIL_APP_PASSWORD`
     - `DYNAMODB_TABLE`: `kurrent_briefings`
   - Set Timeout: `60 seconds`.
   - Add EventBridge Trigger: Schedule `cron(45 23 * * ? *)` (05:15 AM IST).

3. **API Lambda & HTTP API**:
   - Function Name: `kurrent-api-handler`
   - Code: `lambda_api_handler.py`.
   - Attach HTTP API with route `GET /briefing/today`.
