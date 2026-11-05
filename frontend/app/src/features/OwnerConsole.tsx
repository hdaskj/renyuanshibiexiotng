import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Album, SearchRequest, User } from "../types";

interface Props {
  token: string;
  user: User;
}

export function OwnerConsole({ token, user }: Props) {
  const [albums, setAlbums] = useState<Album[]>([]);
  const [albumId, setAlbumId] = useState("album-cabin");
  const [requests, setRequests] = useState<SearchRequest[]>([]);
  const [status, setStatus] = useState("选择相册后刷新申请。");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.albums(token)
      .then(items => {
        setAlbums(items);
        setAlbumId(current =>
          items.some(item => item.id === current) ? current : items[0]?.id ?? ""
        );
      })
      .catch(error => setStatus(message(error)));
  }, [token]);

  useEffect(() => {
    refresh().catch(error => setStatus(message(error)));
  }, [albumId]);

  async function refresh() {
    setRequests(await api.requests(token, albumId));
  }

  async function decide(request: SearchRequest, decision: "APPROVE" | "REJECT") {
    setBusy(true);
    try {
      const updated = await api.decideRequest(token, request.id, decision, 7);
      setRequests(items => items.map(item => item.id === updated.id ? updated : item));
      setStatus(`${request.id} 已${decision === "APPROVE" ? "批准 7 天" : "拒绝"}`);
    } catch (error) {
      setStatus(message(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="workspace">
      <header className="section-heading">
        <div>
          <p className="eyebrow">ALBUM OWNER CONSOLE</p>
          <h2>相册协作与审批</h2>
          <p>{user.displayName}，审批检索用途、范围和有效期。</p>
        </div>
      </header>

      <div className="panel owner-toolbar">
        <label>
          管理相册
          <select value={albumId} onChange={event => setAlbumId(event.target.value)}>
            {albums.map(album => (
              <option key={album.id} value={album.id}>
                {album.name} · {album.photoCount} 张
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="secondary" onClick={() => refresh()} disabled={busy}>
          刷新申请
        </button>
      </div>

      <p className="status-line" aria-live="polite">{status}</p>

      <div className="request-grid">
        {requests.map(request => (
          <article className="request-card" key={request.id}>
            <div>
              <span className="request-id">{request.id}</span>
              <h3>{request.requesterId}</h3>
              <p>{request.purposeCode} · 申请 {request.requestedDays} 天</p>
            </div>
            <span className={`request-state ${request.status.toLowerCase()}`}>{request.status}</span>
            {request.status === "REQUESTED" && (
              <div className="button-row">
                <button type="button" className="primary" onClick={() => decide(request, "APPROVE")} disabled={busy}>
                  同意 7 天
                </button>
                <button type="button" className="secondary" onClick={() => decide(request, "REJECT")} disabled={busy}>
                  拒绝
                </button>
              </div>
            )}
            {request.consentId && <code>{request.consentId}</code>}
          </article>
        ))}
        {requests.length === 0 && <p className="empty">该相册当前没有检索申请。</p>}
      </div>
    </section>
  );
}

function message(error: unknown) {
  return error instanceof Error ? error.message : "操作失败";
}
