import { StrictMode, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { Activity, CheckCircle2, CircleGauge, FlaskConical, LogOut, Play, RotateCcw, ShieldCheck, Sparkles, XCircle } from "lucide-react";
import "./styles.css";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
type Entitlement = { learnerId: string; tokens: number; reservedAttemptId: string | null };
type Attempt = { id: number; learnerId: string; labId: string; status: string };
type Feedback = { id: number; learnerId: string; attemptId: string; text: string; acknowledged: boolean };
type Session = { token: string; username: string; role: string; fullName: string };

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  const token = localStorage.getItem("retrylab-token");
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init?.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  const response = await fetch(`${API}${path}`, { ...init, headers });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message ?? body.error ?? `Request failed (${response.status})`);
  return body as T;
}
const post = <T,>(path: string) => request<T>(path, { method: "POST" });

function App() {
  const [session, setSession] = useState<Session | null>(() => {
    const stored = localStorage.getItem("retrylab-session");
    return stored ? JSON.parse(stored) as Session : null;
  });
  const [learnerId, setLearnerId] = useState(() => session?.username ?? "learner1");
  const [password, setPassword] = useState("password");
  const [loginError, setLoginError] = useState("");
  const [entitlement, setEntitlement] = useState<Entitlement | null>(null);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [attemptId, setAttemptId] = useState("");
  const [labId, setLabId] = useState("lab-09");
  const [feedbackText, setFeedbackText] = useState("");
  const [notice, setNotice] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const login = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true); setLoginError("");
    try {
      const next = await request<Session>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: learnerId, password })
      });
      localStorage.setItem("retrylab-token", next.token);
      localStorage.setItem("retrylab-session", JSON.stringify(next));
      setSession(next);
    } catch (error) {
      setLoginError(error instanceof Error ? error.message : "Login failed");
    } finally {
      setBusy(false);
    }
  };
  const logout = () => {
    localStorage.removeItem("retrylab-token");
    localStorage.removeItem("retrylab-session");
    setSession(null);
  };

  const refresh = async () => {
    try {
      const [current, list] = await Promise.all([
        request<Entitlement>(`/entitlements/${learnerId}`),
        request<Attempt[]>("/attempts")
      ]);
      setEntitlement(current); setAttempts(list);
    } catch (error) {
      setNotice({ type: "error", text: error instanceof Error ? error.message : "Unable to reach the platform" });
    }
  };
  useEffect(() => { if (session) void refresh(); }, [learnerId, session]);
  const run = async (action: () => Promise<unknown>, message: string) => {
    setBusy(true); setNotice(null);
    try { await action(); await refresh(); setNotice({ type: "success", text: message }); }
    catch (error) { setNotice({ type: "error", text: error instanceof Error ? error.message : "Action failed" }); }
    finally { setBusy(false); }
  };
  const createAttempt = () => run(async () => {
    const created = await request<Attempt>("/attempts", {
      method: "POST",
      body: JSON.stringify({ learnerId, labId })
    });
    setAttemptId(String(created.id));
  }, "Failed lab attempt recorded.");
  const addFeedback = () => run(async () => {
    const created = await request<Feedback>("/feedback", {
      method: "POST",
      body: JSON.stringify({ learnerId, attemptId, text: feedbackText })
    });
    setFeedback(created); setFeedbackText("");
  }, "Feedback issued. Acknowledge it before reserving a retry.");
  const loadFeedback = () => run(async () => {
    const records = await request<Feedback[]>(`/feedback?attemptId=${encodeURIComponent(attemptId)}`);
    setFeedback(records[0] ?? null);
    if (!records[0]) throw new Error("No feedback found for this attempt");
  }, "Feedback loaded.");
  const acknowledge = () => run(async () => {
    if (!feedback) return;
    const updated = await post<Feedback>(`/feedback/${feedback.id}/acknowledge?learnerId=${encodeURIComponent(learnerId)}`);
    setFeedback(updated);
  }, "Feedback acknowledged.");
  const reserve = () => run(() => post(`/entitlements/${learnerId}/reserve?attemptId=${encodeURIComponent(attemptId)}`), "Retry reserved without consuming a token.");
  const cancel = () => run(() => post(`/entitlements/${learnerId}/cancel?attemptId=${encodeURIComponent(attemptId)}`), "Reservation cancelled; token preserved.");
  const start = () => run(() => post(`/entitlements/${learnerId}/start?attemptId=${encodeURIComponent(attemptId)}`), "Retry started; one token consumed.");

  if (!session) return <div className="login-page"><div className="login-card"><div className="brand login-brand"><div className="brand-mark"><FlaskConical size={20} /></div><div><strong>RetryLab</strong><span>Laboratory retry entitlement</span></div></div><p className="eyebrow">SECURE LEARNER ACCESS</p><h1>Welcome back</h1><p className="sub">Sign in to review feedback and prepare your next retry.</p><form onSubmit={login}><label>Username<input value={learnerId} onChange={e => setLearnerId(e.target.value)} autoComplete="username" /></label><label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>{loginError && <div className="notice error">{loginError}</div>}<button className="login-button" disabled={busy}>{busy ? "Signing in..." : "Sign in to RetryLab"}</button></form><small>Demo learner: learner1 / password</small></div></div>;

  return <div className="app">
    <aside className="sidebar">
      <div className="brand"><div className="brand-mark"><FlaskConical size={20} /></div><div><strong>RetryLab</strong><span>Entitlement platform</span></div></div>
      <nav><a className="active"><CircleGauge size={17} /> Overview</a><a><Activity size={17} /> Attempt history</a><a><ShieldCheck size={17} /> Access & policy</a></nav>
      <div className="sidebar-foot"><div className="secure"><CheckCircle2 size={16} /> Signed in</div><button className="logout" onClick={logout}><LogOut size={15} /> Sign out</button></div>
    </aside>
    <main>
      <header><div><p className="eyebrow">LEARNER WORKSPACE / {learnerId.toUpperCase()}</p><h1>Retry readiness</h1><p className="sub">A clear path from feedback to your next laboratory attempt.</p></div><div className="profile"><div className="avatar">L1</div><div><strong>{learnerId}</strong><span>Learner</span></div><Sparkles className="sparkle" size={17} /></div></header>
      {notice && <div className={`notice ${notice.type}`}>{notice.type === "success" ? <CheckCircle2 size={17} /> : <XCircle size={17} />}{notice.text}</div>}
      <section className="metrics"><Metric label="Available tokens" value={entitlement?.tokens ?? "—"} hint="Retries remaining" icon={<RotateCcw />} accent="blue" /><Metric label="Active reservation" value={entitlement?.reservedAttemptId ? "Reserved" : "None"} hint={entitlement?.reservedAttemptId ? `Attempt ${entitlement.reservedAttemptId}` : "Ready to reserve"} icon={<ShieldCheck />} accent="violet" /><Metric label="Failed attempts" value={attempts.length} hint="Across your workspace" icon={<FlaskConical />} accent="orange" /><Metric label="Policy status" value="Eligible" hint="Feedback-first policy" icon={<CheckCircle2 />} accent="green" /></section>
      <section className="grid">
        <div className="card workflow"><div className="card-head"><div><p className="eyebrow">GUIDED WORKFLOW</p><h2>Prepare a retry</h2></div><span className="pill">4 steps</span></div>
          <div className="step"><div className="step-num">01</div><div className="step-body"><strong>Record a failed lab attempt</strong><span>Create the attempt that needs another run.</span><div className="inline-form"><input value={labId} onChange={e => setLabId(e.target.value)} placeholder="Lab ID" /><button onClick={createAttempt} disabled={busy}>Record attempt</button></div></div></div>
          <div className="step"><div className="step-num">02</div><div className="step-body"><strong>Review your feedback</strong><span>Acknowledgement is required before entitlement reservation. Instructors issue feedback; learners load and acknowledge it.</span><div className="inline-form"><input value={attemptId} onChange={e => setAttemptId(e.target.value)} placeholder="Attempt ID" /><button onClick={loadFeedback} disabled={busy || !attemptId}>Load feedback</button>{session.role === "ROLE_INSTRUCTOR" && <><input value={feedbackText} onChange={e => setFeedbackText(e.target.value)} placeholder="Feedback text" /><button onClick={addFeedback} disabled={busy || !attemptId || !feedbackText}>Issue feedback</button></>}</div>{feedback && <div className="feedback"><span>{feedback.text}</span>{feedback.acknowledged ? <b className="ack">Acknowledged</b> : <button className="link-button" onClick={acknowledge}>Acknowledge</button>}</div>}</div></div>
          <div className="step"><div className="step-num">03</div><div className="step-body"><strong>Reserve your retry</strong><span>Reservation holds a token but does not consume it.</span><div className="actions"><button onClick={reserve} disabled={busy || !attemptId || !feedback?.acknowledged}><ShieldCheck size={15} /> Reserve retry</button><button className="secondary" onClick={cancel} disabled={busy || !entitlement?.reservedAttemptId}><XCircle size={15} /> Cancel</button></div></div></div>
          <div className="step"><div className="step-num">04</div><div className="step-body"><strong>Start the retry</strong><span>Token is consumed only when the reserved attempt starts.</span><button className="start" onClick={start} disabled={busy || entitlement?.reservedAttemptId !== attemptId}><Play size={15} fill="currentColor" /> Start retry</button></div></div>
        </div>
        <div className="card history"><div className="card-head"><div><p className="eyebrow">ACTIVITY</p><h2>Attempt history</h2></div><button className="icon-button" onClick={() => void refresh()}><RotateCcw size={16} /></button></div>{attempts.length === 0 ? <div className="empty"><FlaskConical size={28} /><p>No attempts yet</p><span>Your recorded laboratory attempts will appear here.</span></div> : <div className="attempt-list">{attempts.map(item => <div className="attempt" key={item.id}><div className="attempt-icon"><FlaskConical size={16} /></div><div><strong>Attempt #{item.id}</strong><span>{item.labId}</span></div><b>{item.status}</b></div>)}</div>}</div>
      </section>
      <footer><span><span className="dot" /> Services connected through Gateway :8080</span><span>RetryLab v0.1 · Team 9</span></footer>
    </main>
  </div>;
}
function Metric({ label, value, hint, icon, accent }: { label: string; value: string | number; hint: string; icon: ReactNode; accent: string }) {
  return <div className="metric"><div className={`metric-icon ${accent}`}>{icon}</div><div><span>{label}</span><strong>{value}</strong><small>{hint}</small></div></div>;
}
createRoot(document.getElementById("root")!).render(<StrictMode><App /></StrictMode>);
