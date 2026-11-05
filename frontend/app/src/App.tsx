import { useEffect, useState } from "react";
import { api } from "./api/client";
import { LoginPanel } from "./components/LoginPanel";
import { ParticipantWorkspace } from "./features/ParticipantWorkspace";
import { OwnerConsole } from "./features/OwnerConsole";
import { AuditPanel } from "./features/AuditPanel";
import type { LoginResponse, User } from "./types";

type Tab = "participant" | "owner" | "audit";

export default function App() {
  const [session, setSession] = useState<LoginResponse | null>(null);
  const [tab, setTab] = useState<Tab>("participant");
  const [backend, setBackend] = useState("检查中");

  useEffect(() => {
    api.health()
      .then(result => setBackend(result.status === "UP" ? "后端已连接" : result.status))
      .catch(() => setBackend("后端未连接"));
  }, []);

  function onLogin(next: LoginResponse) {
    setSession(next);
    setTab(next.user.role === "PARTICIPANT" ? "participant" : "owner");
  }

  function logout() {
    setSession(null);
    setTab("participant");
  }

  return (
    <div className="app-shell">
      <LoginPanel user={session?.user ?? null} onLogin={onLogin} onLogout={logout} />

      <main>
        <header className="topbar">
          <div>
            <span className="product-label">AI-03 · 相寻</span>
            <span className={`backend-state ${backend === "后端已连接" ? "online" : ""}`}>
              {backend}
            </span>
          </div>
          <nav aria-label="功能导航">
            <button type="button" className={tab === "participant" ? "active" : ""} onClick={() => setTab("participant")}>参与者</button>
            <button type="button" className={tab === "owner" ? "active" : ""} onClick={() => setTab("owner")}>所有者</button>
            <button type="button" className={tab === "audit" ? "active" : ""} onClick={() => setTab("audit")}>审计</button>
          </nav>
        </header>

        {!session ? (
          <EmptyState backend={backend} />
        ) : tab === "participant" ? (
          <ParticipantWorkspace token={session.token} user={session.user} />
        ) : tab === "owner" ? (
          <OwnerConsole token={session.token} user={session.user} />
        ) : (
          <AuditPanel token={session.token} />
        )}
      </main>
    </div>
  );
}

function EmptyState({ backend }: { backend: string }) {
  return (
    <section className="empty-hero">
      <p className="eyebrow">CONSENT-FIRST PHOTO DISCOVERY</p>
      <h2>授权先于检索，解释先于结论</h2>
      <p>从左侧选择参与者或相册所有者身份，体验真实申请、审批、照片检索和撤回清理流程。</p>
      <div className="boundary-grid">
        <article><strong>授权私人相册</strong><span>只检索明确允许的活动照片</span></article>
        <article><strong>多模态解释</strong><span>人脸、衣着、场景分别给出依据</span></article>
        <article><strong>可撤回清理</strong><span>授权变化后旧结果立即失效</span></article>
      </div>
      <p className={`backend-state ${backend === "后端已连接" ? "online" : ""}`}>{backend}</p>
    </section>
  );
}
