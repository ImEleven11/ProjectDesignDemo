from contextlib import asynccontextmanager
from pathlib import Path
import os

from fastapi import FastAPI


SERVICE_NAME = "reconstruction-worker"
DATA_ROOT = Path(os.getenv("WORKER_DATA_ROOT", "/data"))

@asynccontextmanager
async def lifespan(_: FastAPI):
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(
    title="Project Design Reconstruction Worker",
    version="0.1.0",
    lifespan=lifespan,
)


@app.get("/health", tags=["operations"])
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "service": SERVICE_NAME,
        "stage": "infrastructure",
    }
