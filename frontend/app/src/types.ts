export type Role = "PARTICIPANT" | "OWNER" | "ADMIN";

export interface User {
  id: string;
  displayName: string;
  role: Role;
}

export interface LoginResponse {
  token: string;
  user: User;
  expiresAt: string;
}

export interface Album {
  id: string;
  name: string;
  photoCount: number;
  indexStatus: string;
  role: string;
}

export interface SearchRequest {
  id: string;
  albumId: string;
  requesterId: string;
  purposeCode: string;
  status: string;
  requestedDays: number;
  approvedDays?: number;
  decidedBy?: string;
  createdAt: string;
  expiresAt?: string;
  consentId?: string;
}

export interface SearchResult {
  photoId: string;
  title: string;
  albumId: string;
  albumName: string;
  scene: string;
  outfit: string;
  date: string;
  score: number;
  factors: {
    face: number;
    outfit: number;
    scene: number;
  };
  summary: string;
  identityDisclaimer: string;
  feedback?: "RELEVANT" | "IRRELEVANT";
}

export interface SearchSession {
  id: string;
  requestId: string;
  status: string;
  pollAfterMs: number;
  modelVersion: string;
  consentVersion: string;
  items: SearchResult[];
}

export interface AuditEvent {
  id: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  occurredAt: string;
}

export interface ExperimentSummary {
  key: string;
  modelVersion: string;
  rolloutPercent: number;
  topKRecall: number;
  ndcgAt10: number;
  p95LatencyMs: number;
}
