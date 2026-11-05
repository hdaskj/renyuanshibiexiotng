import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { Album, AuditEvent, ExperimentSummary } from "../types";

interface Props {
  token: string;
}

export function AuditPanel({ token }: Props) {
  const [albums, setAlbums] = useState<Album[]>([]);
  const [albumId, setAlbumId] = useState("album-cabin");
  const [events, setEvents] = useState<AuditEvent[]>([]);
  const [experiments, setExperiments] = useState<ExperimentSummary[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api.albums(token)
      .then(items => {
        setAlbums(items);
        setAlbumId(current =>
          items.some(item => item.id === current) ? current : items[0]?.id ?? ""
        );
      })
      .catch(cause => setError(message(cause)));
  }, [token]);

  async function load() {
    try {
      const [audit, experiment] = await Promise.all([
        api.audit(token, albumId),
        api.experiments(token)
      ]);
      setEvents(audit);
      setExperiments(experiment);
      setError("");
    } catch (cause) {
      setError(message(cause));
    }
  }

  return (
    <section className="workspace">
      <header className="section-heading">
        <div>
          <p className="eyebrow">AUDIT & EVALUATION</p>
          <h2>审计与灰度实验</h2>
          <p>查看审批、检索、反馈、撤回和模型版本记录。</p>
        </div>
      </header>

      <div className="panel owner-toolbar">
        <label>
          相册
          <select value={albumId} onChange={event => setAlbumId(event.target.value)}>
            {albums.map(album => (
              <option key={album.id} value={album.id}>{album.name}</option>
            ))}
          </select>
        </label>
        <button type="button" className="primary" onClick={load}>刷新数据</button>
      </div>
      {error && <p className="error" role="alert">{error}</p>}

      <div className="audit-grid">
        <div className="panel">
          <h3>最近审计事件</h3>
          <div className="timeline">
            {events.map(event => (
              <div key={event.id}>
                <strong>{event.action}</strong>
                <span>{event.resourceType} · {event.resourceId}</span>
                <time>{new Date(event.occurredAt).toLocaleString()}</time>
              </div>
            ))}
            {events.length === 0 && <p className="empty">暂无审计数据。</p>}
          </div>
        </div>

        <div className="panel">
          <h3>A/B 实验摘要</h3>
          <div className="experiment-list">
            {experiments.map(item => (
              <article key={item.key}>
                <div>
                  <strong>{item.key}</strong>
                  <span>{item.modelVersion} · {item.rolloutPercent}%</span>
                </div>
                <dl>
                  <div><dt>Top-K</dt><dd>{(item.topKRecall * 100).toFixed(1)}%</dd></div>
                  <div><dt>NDCG@10</dt><dd>{item.ndcgAt10.toFixed(3)}</dd></div>
                  <div><dt>P95</dt><dd>{item.p95LatencyMs}ms</dd></div>
                </dl>
              </article>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

function message(error: unknown) {
  return error instanceof Error ? error.message : "操作失败";
}
