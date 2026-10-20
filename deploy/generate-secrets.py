"""Run once on the deployment host. Existing secrets are never overwritten."""
from pathlib import Path
import base64
import os
import secrets

root = Path(__file__).resolve().parent / "secrets"
root.mkdir(mode=0o700, exist_ok=True)
for name in ("db-password", "db-root-password", "jwt-secret"):
    path = root / name
    if path.exists():
        print(f"Preserved {name}")
        continue
    with path.open("x", encoding="ascii") as output:
        output.write(base64.b64encode(secrets.token_bytes(48)).decode())
    os.chmod(path, 0o600 if name == "db-root-password" else 0o444)
    print(f"Created {name}")
