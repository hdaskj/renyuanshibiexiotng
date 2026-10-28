import json
import mimetypes
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path


BASE = "http://127.0.0.1:8081/api/v1"
ROOT = Path(__file__).resolve().parents[1]
SAMPLE = ROOT / "frontend" / "prototype" / "prototype-preview.png"


def json_request(path, method="GET", body=None, token=None):
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Content-Type": "application/json", "User-Agent": "Codex"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload = response.read()
            return response.status, json.loads(payload) if payload else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> {error.code}: {detail}") from error


def multipart_search(token, request_id, query):
    boundary = "----AI03" + uuid.uuid4().hex
    fields = {
        "requestId": request_id,
        "queryText": query,
        "sort": "OVERALL",
        "diversity": "true",
    }
    chunks = []
    for name, value in fields.items():
        chunks.append(f"--{boundary}\r\n".encode())
        chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        chunks.append(value.encode("utf-8") + b"\r\n")
    content_type = mimetypes.guess_type(SAMPLE.name)[0] or "image/png"
    chunks.append(f"--{boundary}\r\n".encode())
    chunks.append(
        f'Content-Disposition: form-data; name="sample"; filename="{SAMPLE.name}"\r\n'.encode()
    )
    chunks.append(f"Content-Type: {content_type}\r\n\r\n".encode())
    chunks.append(SAMPLE.read_bytes() + b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode())
    request = urllib.request.Request(
        BASE + "/search-sessions",
        data=b"".join(chunks),
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "Codex",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.status, json.loads(response.read())


def main():
    print("health", json_request("/health")[1])
    _, participant = json_request(
        "/auth/dev-login", "POST", {"username": "participant"}
    )
    _, owner = json_request("/auth/dev-login", "POST", {"username": "owner"})
    participant_token = participant["token"]
    owner_token = owner["token"]

    _, albums = json_request("/albums", token=participant_token)
    assert any(album["id"] == "album-cabin" for album in albums)

    _, requests = json_request(
        "/search-requests?albumId=album-cabin", token=owner_token
    )
    pending = next((item for item in requests if item["status"] == "REQUESTED"), None)
    if pending is None:
        _, pending = json_request(
            "/search-requests",
            "POST",
            {"albumId": "album-cabin", "requestedDays": 7},
            participant_token,
        )
    _, approved = json_request(
        f"/search-requests/{pending['id']}",
        "PATCH",
        {"decision": "APPROVE", "approvedDays": 7},
        owner_token,
    )
    assert approved["status"] == "APPROVED"
    assert approved["consentId"]

    status, search = multipart_search(
        participant_token, pending["id"], "穿蓝色针织衫，在木屋咖啡馆"
    )
    assert status == 202
    assert search["status"] == "COMPLETED"
    assert len(search["items"]) == 9
    assert "蓝色针织衫" in search["items"][0]["title"]

    _, feedback = json_request(
        "/feedback",
        "POST",
        {
            "sessionId": search["id"],
            "resultId": search["items"][0]["photoId"],
            "relevance": "RELEVANT",
        },
        participant_token,
    )
    assert feedback["status"] == "RECORDED"

    _, results = json_request(
        f"/search-sessions/{search['id']}/results", token=participant_token
    )
    assert len(results["items"]) == 9

    _, audit = json_request("/audit-events?albumId=album-cabin", token=owner_token)
    actions = {event["action"] for event in audit}
    assert {"REQUEST_APPROVED", "SEARCH_COMPLETED", "FEEDBACK_RECORDED"} <= actions

    _, revoked = json_request(
        f"/consents/{approved['consentId']}", "DELETE", token=participant_token
    )
    assert revoked["status"] == "CLEANUP_PENDING"

    try:
        json_request(
            f"/search-sessions/{search['id']}/results", token=participant_token
        )
        raise AssertionError("Revoked session must no longer be accessible")
    except RuntimeError as error:
        assert "-> 404:" in str(error)

    print(
        json.dumps(
            {
                "status": "passed",
                "requestId": pending["id"],
                "consentId": approved["consentId"],
                "sessionId": search["id"],
                "modelVersion": search["modelVersion"],
                "topResult": search["items"][0]["title"],
                "auditActions": sorted(actions),
                "revokeStatus": revoked["status"],
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"E2E FAILED: {exc}", file=sys.stderr)
        raise
