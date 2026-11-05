import { useState } from "react";
import { api } from "../api/client";
import type { LoginResponse, User } from "../types";

interface Props {
  user: User | null;
  onLogin: (session: LoginResponse) => void;
  onLogout: () => void;
}

export function LoginPanel({ user, onLogin, onLogout }: Props) {
  const [username, setUsername] = useState("participant");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function login() {
    setBusy(true);
    setError("");
    try {
      onLogin(await api.login(username));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "登录失败");
    } finally {
      setBusy(false);
    }
  }

  return (
    <aside className="login-panel">
      <div className="brand-row">
        <div className="brand-mark" aria-hidden="true">相</div>
        <div>
          <strong>相寻</strong>
          <span>PRIVATE MOMENT SEARCH</span>
        </div>
      </div>

      <div className="login-copy">
        <p className="eyebrow">V0.2 · REACT CLIENT</p>
        <h1>在授权范围内，找回属于你的照片</h1>
        <p>角色分离、临时授权、可解释推荐、反馈与撤回，全部连接真实后端。</p>
      </div>

      {user ? (
        <div className="identity-card">
          <span>当前身份</span>
          <strong>{user.displayName}</strong>
          <small>{user.role}</small>
          <button type="button" className="ghost" onClick={onLogout}>退出登录</button>
        </div>
      ) : (
        <div className="login-form">
          <label htmlFor="dev-user">开发账户</label>
          <select id="dev-user" value={username} onChange={event => setUsername(event.target.value)}>
            <option value="participant">林岚 · 参与者</option>
            <option value="owner">林澜 · 相册所有者</option>
            <option value="admin">系统管理员</option>
          </select>
          <button type="button" className="primary" onClick={login} disabled={busy}>
            {busy ? "登录中..." : "登录并继续"}
          </button>
          {error && <p className="error" role="alert">{error}</p>}
        </div>
      )}

      <div className="privacy-note">
        <strong>隐私边界</strong>
        <span>仅限授权私人相册 · 不提供陌生人检索 · 结果不构成身份认定</span>
      </div>
    </aside>
  );
}
