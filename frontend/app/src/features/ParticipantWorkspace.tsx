import { useEffect, useMemo, useState } from "react";
import { api, ApiError } from "../api/client";
import type { Album, SearchRequest, SearchResult, SearchSession, User } from "../types";

interface Props {
  token: string;
  user: User;
}

type FeedbackValue = "RELEVANT" | "IRRELEVANT";

export function ParticipantWorkspace({ token, user }: Props) {
  const [albums, setAlbums] = useState<Album[]>([]);
  const [albumId, setAlbumId] = useState("album-cabin");
  const [requests, setRequests] = useState<SearchRequest[]>([]);
  const [requestId, setRequestId] = useState("");
  const [consentId, setConsentId] = useState("");
  const [queryText, setQueryText] = useState("穿蓝色针织衫，在木屋咖啡馆");
  const [sort, setSort] = useState("OVERALL");
  const [diversity, setDiversity] = useState(true);
  const [sample, setSample] = useState<File | null>(null);
  const [session, setSession] = useState<SearchSession | null>(null);
  const [results, setResults] = useState<SearchResult[]>([]);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState("先提交申请，再由相册所有者批准。");

  const selectedRequest = useMemo(
    () => requests.find(item => item.id === requestId),
    [requestId, requests]
  );

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
    if (!albumId) return;
    refreshRequests(albumId, token).catch(error => setStatus(message(error)));
  }, [albumId, token]);

  async function refreshRequests(targetAlbum = albumId, authToken = token) {
    const items = await api.requests(authToken, targetAlbum);
    setRequests(items);
    if (!requestId && items[0]) setRequestId(items[0].id);
    return items;
  }

  async function createRequest() {
    setBusy(true);
    try {
      const created = await api.createRequest(token, albumId, 7);
      setRequestId(created.id);
      setStatus(`申请已创建：${created.id}。请由相册所有者批准。`);
      await refreshRequests();
    } catch (error) {
      setStatus(message(error));
    } finally {
      setBusy(false);
    }
  }

  async function loadSelectedRequest() {
    setBusy(true);
    try {
      const items = await refreshRequests();
      const found = items.find(item => item.id === requestId) ?? items[0];
      if (!found) throw new Error("当前账号没有检索申请");
      setRequestId(found.id);
      setConsentId(found.consentId ?? "");
      setStatus(
        found.status === "APPROVED"
          ? `申请已批准，授权有效至 ${new Date(found.expiresAt ?? "").toLocaleString()}`
          : `当前状态：${found.status}`
      );
    } catch (error) {
      setStatus(message(error));
    } finally {
      setBusy(false);
    }
  }

  async function runSearch() {
    if (!sample) {
      setStatus("请选择一张检索样本");
      return;
    }
    setBusy(true);
    setStatus("正在提取多模态特征并执行授权内检索...");
    try {
      const response = await api.search(token, {
        requestId,
        queryText,
        sort,
        diversity,
        sample
      });
      setSession(response);
      setResults(response.items);
      setConsentId(response.consentVersion);
      setStatus(`检索完成 · ${response.modelVersion} · ${response.items.length} 条结果`);
    } catch (error) {
      setStatus(message(error));
    } finally {
      setBusy(false);
    }
  }

  async function sendFeedback(item: SearchResult, relevance: FeedbackValue) {
    if (!session) return;
    try {
      await api.feedback(token, session.id, item.photoId, relevance);
      setResults(current =>
        current.map(result =>
          result.photoId === item.photoId ? { ...result, feedback: relevance } : result
        )
      );
    } catch (error) {
      setStatus(message(error));
    }
  }

  async function revokeConsent() {
    if (!consentId) {
      setStatus("当前没有可撤回的授权");
      return;
    }
    setBusy(true);
    try {
      const response = await api.revoke(token, consentId);
      setResults([]);
      setSession(null);
      setStatus(`授权已撤回，清理状态：${response.status}，任务 ${response.cleanupJobId}`);
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
          <p className="eyebrow">PARTICIPANT WORKSPACE</p>
          <h2>本人照片检索</h2>
          <p>{user.displayName}，只检索你已获准访问的私人相册。</p>
        </div>
        <span className={`request-state ${selectedRequest?.status?.toLowerCase() ?? ""}`}>
          {selectedRequest?.status ?? "NO REQUEST"}
        </span>
      </header>

      <div className="control-grid">
        <div className="panel">
          <h3>1. 申请与授权</h3>
          <label>
            相册
            <select value={albumId} onChange={event => setAlbumId(event.target.value)}>
              {albums.map(album => (
                <option key={album.id} value={album.id}>
                  {album.name} · {album.photoCount} 张
                </option>
              ))}
            </select>
          </label>
          <label>
            申请编号
            <select value={requestId} onChange={event => setRequestId(event.target.value)}>
              <option value="">选择申请</option>
              {requests.map(item => (
                <option key={item.id} value={item.id}>
                  {item.id} · {item.status}
                </option>
              ))}
            </select>
          </label>
          <div className="button-row">
            <button type="button" className="primary" onClick={createRequest} disabled={busy}>
              提交 7 天申请
            </button>
            <button type="button" className="secondary" onClick={loadSelectedRequest} disabled={busy}>
              刷新状态
            </button>
          </div>
        </div>

        <div className="panel">
          <h3>2. 检索样本</h3>
          <label>
            自然语言描述
            <input value={queryText} onChange={event => setQueryText(event.target.value)} />
          </label>
          <label className="file-field">
            <span>{sample ? sample.name : "选择照片样本"}</span>
            <input
              type="file"
              accept="image/*"
              onChange={event => setSample(event.target.files?.[0] ?? null)}
            />
          </label>
          <div className="inline-controls">
            <select value={sort} onChange={event => setSort(event.target.value)}>
              <option value="OVERALL">综合排序</option>
              <option value="FACE">人脸优先</option>
              <option value="OUTFIT">衣着优先</option>
              <option value="SCENE">场景优先</option>
            </select>
            <label className="switch-row">
              <input
                type="checkbox"
                checked={diversity}
                onChange={event => setDiversity(event.target.checked)}
              />
              增加多样性
            </label>
          </div>
          <div className="button-row">
            <button type="button" className="primary" onClick={runSearch} disabled={busy || !sample}>
              开始检索
            </button>
            <button type="button" className="danger" onClick={revokeConsent} disabled={busy || !consentId}>
              撤回授权
            </button>
          </div>
        </div>
      </div>

      <p className="status-line" aria-live="polite">{status}</p>

      <div className="result-grid">
        {results.map((item, index) => (
          <article className="result-card" key={item.photoId}>
            <div className="score">{item.score}</div>
            <span className="rank">#{index + 1}</span>
            <h3>{item.title}</h3>
            <p className="meta">{item.albumName} · {item.date}</p>
            <p className="summary">{item.summary}</p>
            <div className="factors">
              <span>人脸 {item.factors.face}</span>
              <span>衣着 {item.factors.outfit}</span>
              <span>场景 {item.factors.scene}</span>
            </div>
            <div className="button-row">
              <button
                type="button"
                className={item.feedback === "RELEVANT" ? "selected" : "secondary"}
                onClick={() => sendFeedback(item, "RELEVANT")}
              >
                相关
              </button>
              <button
                type="button"
                className={item.feedback === "IRRELEVANT" ? "selected danger" : "secondary"}
                onClick={() => sendFeedback(item, "IRRELEVANT")}
              >
                不相关
              </button>
            </div>
            <p className="disclaimer">{item.identityDisclaimer}</p>
          </article>
        ))}
      </div>
    </section>
  );
}

function message(error: unknown) {
  if (error instanceof ApiError) return `${error.code ?? error.status}：${error.message}`;
  return error instanceof Error ? error.message : "操作失败";
}
