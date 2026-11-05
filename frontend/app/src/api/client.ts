import type {
  Album,
  AuditEvent,
  ExperimentSummary,
  LoginResponse,
  SearchRequest,
  SearchSession
} from "../types";

const API_BASE = import.meta.env.VITE_API_BASE ?? "/api/v1";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string
  ) {
    super(message);
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const headers = new Headers(options.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new ApiError(
      body?.message ?? body?.error ?? response.statusText,
      response.status,
      body?.error
    );
  }
  return body as T;
}

export const api = {
  health: () => request<{ status: string; service: string }>("/health"),

  login: (username: string) =>
    request<LoginResponse>("/auth/dev-login", {
      method: "POST",
      body: JSON.stringify({ username })
    }),

  albums: (token: string) => request<Album[]>("/albums", {}, token),

  createRequest: (token: string, albumId: string, requestedDays = 7) =>
    request<SearchRequest>(
      "/search-requests",
      {
        method: "POST",
        body: JSON.stringify({ albumId, requestedDays })
      },
      token
    ),

  requests: (token: string, albumId: string) =>
    request<SearchRequest[]>(
      `/search-requests?albumId=${encodeURIComponent(albumId)}`,
      {},
      token
    ),

  decideRequest: (
    token: string,
    requestId: string,
    decision: "APPROVE" | "REJECT",
    approvedDays = 7
  ) =>
    request<SearchRequest>(
      `/search-requests/${encodeURIComponent(requestId)}`,
      {
        method: "PATCH",
        body: JSON.stringify({ decision, approvedDays })
      },
      token
    ),

  search: (
    token: string,
    input: {
      requestId: string;
      queryText: string;
      sort: string;
      diversity: boolean;
      sample: File;
    }
  ) => {
    const form = new FormData();
    form.append("sample", input.sample);
    form.append("requestId", input.requestId);
    form.append("queryText", input.queryText);
    form.append("sort", input.sort);
    form.append("diversity", String(input.diversity));
    return request<SearchSession>(
      "/search-sessions",
      { method: "POST", body: form },
      token
    );
  },

  feedback: (
    token: string,
    sessionId: string,
    resultId: string,
    relevance: "RELEVANT" | "IRRELEVANT" | "UNDO"
  ) =>
    request<{ feedbackId: string; status: string }>(
      "/feedback",
      {
        method: "POST",
        body: JSON.stringify({ sessionId, resultId, relevance })
      },
      token
    ),

  revoke: (token: string, consentId: string) =>
    request<{ consentId: string; status: string; cleanupJobId: string }>(
      `/consents/${encodeURIComponent(consentId)}`,
      { method: "DELETE" },
      token
    ),

  audit: (token: string, albumId: string) =>
    request<AuditEvent[]>(
      `/audit-events?albumId=${encodeURIComponent(albumId)}`,
      {},
      token
    ),

  experiments: (token: string) =>
    request<ExperimentSummary[]>("/experiments/summary", {}, token)
};
