"""
FastAPI Server for Kurrent on EC2
Serves current affairs data to the Android App with sub-10ms response times.
Includes auto-generated Swagger documentation at: http://<EC2-IP>:8000/docs
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from datetime import datetime
from database import get_briefing_by_date, get_latest_briefing, init_db
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


@app.on_event("startup")
def on_startup():
    init_db()


@app.get("/health")
def health_check():
    return {"status": "healthy", "timestamp": datetime.utcnow().isoformat()}


@app.get("/briefing/today")
def get_today_briefing():
    """
    Returns today's daily briefing with articles, mnemonics, traps, recalls, and quizzes.
    Falls back to the most recent briefing if today's is not yet received.
    """
    today_str = datetime.utcnow().strftime("%Y-%m-%d")
    briefing = get_briefing_by_date(today_str)
    
    if not briefing:
        # Fallback to latest
        briefing = get_latest_briefing()

    if not briefing:
        raise HTTPException(status_code=404, detail="No briefings available yet. Please run ingestion.")

    return briefing


@app.get("/briefing/{date}")
def get_briefing_for_date(date: str):
    """
    Returns the briefing for a specific date (Format: YYYY-MM-DD).
    """
    briefing = get_briefing_by_date(date)
    if not briefing:
        raise HTTPException(status_code=404, detail=f"No briefing found for date: {date}")
    return briefing


@app.post("/ingest/trigger")
def trigger_ingestion():
    """
    Manual trigger endpoint to fetch and parse the latest email on-demand.
    """
    success = fetch_and_ingest()
    if success:
        return {"status": "success", "message": "Successfully ingested latest email."}
    else:
        return {"status": "warning", "message": "Ingestion completed, but no new briefing was saved."}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("server:app", host="0.0.0.0", port=8000, reload=True)
