import { StrictMode, useEffect, useState, type FormEvent, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import { motion, AnimatePresence } from "framer-motion";
import {
  Activity,
  ArrowRight,
  CheckCircle2,
  ChevronRight,
  CircleGauge,
  Clock,
  ExternalLink,
  FlaskConical,
  KeyRound,
  Lock,
  LogOut,
  Menu,
  Play,
  RotateCcw,
  Search,
  Send,
  ShieldCheck,
  Sparkles,
  UserCheck,
  Users,
  X,
  XCircle,
  AlertTriangle
} from "lucide-react";
import { ClerkProvider, SignIn, useAuth } from "@clerk/clerk-react";
import "./styles.css";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const CLERK_PUBLISHABLE_KEY = import.meta.env.VITE_CLERK_PUBLISHABLE_KEY ?? "";
const IS_CLERK_CONFIGURED = CLERK_PUBLISHABLE_KEY &&
  !CLERK_PUBLISHABLE_KEY.includes("replace_with") &&
  !CLERK_PUBLISHABLE_KEY.includes("placeholder");

// --- API Data Types ---
type Entitlement = { learnerId: string; tokens: number; reservedAttemptId: string | null; reservations?: string[]; startedAttempts?: string[] };
type Attempt = { id: number; learnerId: string; labId: string; status: string; score?: number; notes?: string; createdAt?: string };
type Feedback = { id: number; learnerId: string; attemptId: string; text: string; acknowledged: boolean; instructorId?: string; acknowledgedAt?: string };
type Session = { token: string; username: string; role: string; fullName: string };
type RetryRequest = { id: number; learnerId: string; reason: string; status: string; decisionReason?: string; grantedTokens: number; requestedAt?: string };
type AuditLog = { id: number; action: string; learnerId: string; attemptId?: string; performedBy?: string; details?: string; timestamp: string };

let clerkTokenGetter: (() => Promise<string | null>) | null = null;

// --- Real API Client with Clerk bearer token ---
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  const token = await clerkTokenGetter?.();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init?.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const response = await fetch(`${API}${path}`, { ...init, headers });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(body.message ?? body.error ?? `Request failed (${response.status})`);
  }
  return body as T;
}

const post = <T,>(path: string) => request<T>(path, { method: "POST" });

// ============================================================================
// MAIN APPLICATION ROOT
// ============================================================================

function App() {
  const { getToken, isLoaded, isSignedIn, signOut } = useAuth();
  const [session, setSession] = useState<Session | null>(null);
  const [authError, setAuthError] = useState("");

  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authMode] = useState<"clerk">("clerk");
  const [activeTab, setActiveTab] = useState<"overview" | "attempts" | "requests" | "audit">("overview");

  clerkTokenGetter = getToken;

  useEffect(() => {
    let cancelled = false;
    if (!isLoaded) return;
    if (!isSignedIn) {
      setSession(null);
      setAuthError("");
      return;
    }

    void request<Omit<Session, "token"> & { userId?: string }>("/auth/me")
      .then((identity) => {
        if (cancelled) return;
        const username = identity.username ?? identity.userId ?? "";
        setSession({
          token: "",
          username,
          role: identity.role,
          fullName: identity.fullName ?? (username || "RetryLab user")
        });
        setAuthError("");
        setAuthModalOpen(false);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setSession(null);
        setAuthError(error instanceof Error ? error.message : "Unable to load your RetryLab identity");
      });

    return () => {
      cancelled = true;
    };
  }, [getToken, isLoaded, isSignedIn]);

  const handleLogout = () => {
    setSession(null);
    void signOut();
  };

  if (!isLoaded) {
    return <div className="loading-screen">Loading secure RetryLab session...</div>;
  }

  // If user is authenticated, show their respective dashboard
  if (session) {
    if (session.role === "ROLE_INSTRUCTOR") {
      return <InstructorDashboard session={session} onLogout={handleLogout} />;
    }
    return <LearnerDashboard session={session} onLogout={handleLogout} />;
  }

  // Otherwise, display the Public Landing Page with Hero Video & Modals
  return (
    <div className="landing-wrap">
      {authError && <div className="toast-notice error">{authError}</div>}
      <LandingNav
        onOpenAuth={() => {
          setAuthModalOpen(true);
        }}
      />

      <HeroSection
        onGetStarted={() => {
          setAuthModalOpen(true);
        }}
      />

      <FeaturesSection />
      <WorkflowSection />
      <SecuritySection />
      <LandingFooter />

      <AnimatePresence>
        {authModalOpen && (
          <AuthModal
            mode={authMode}
            onClose={() => setAuthModalOpen(false)}
            onSelectMode={() => undefined}
            onSuccess={() => undefined}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

// ============================================================================
// LANDING PAGE COMPONENTS
// ============================================================================

function LandingNav({ onOpenAuth }: { onOpenAuth: (mode: "demo" | "custom" | "clerk") => void }) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="landing-nav">
      <div className="brand-link" onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}>
        <div className="brand-logo-mark">
          <FlaskConical size={22} />
        </div>
        <div>
          <span className="brand-title">RetryLab</span>
          <span className="brand-sub">Laboratory Entitlement</span>
        </div>
      </div>

      <nav className="nav-links">
        <a className="nav-link" href="#workflow">Workflow</a>
        <a className="nav-link" href="#features">Features</a>
        <a className="nav-link" href="#security">Security & Specs</a>
        <a className="nav-link" href="#help">Documentation</a>
      </nav>

      <div className="nav-actions">
        <button className="btn-secondary" onClick={() => onOpenAuth("demo")}>
          <Users size={15} /> Demo Sign In
        </button>
        <button className="btn-primary" onClick={() => onOpenAuth(IS_CLERK_CONFIGURED ? "clerk" : "demo")}>
          Get Started <ArrowRight size={15} />
        </button>
      </div>

      <button className="mobile-menu-btn" onClick={() => setMobileMenuOpen(!mobileMenuOpen)} aria-label="Toggle Navigation">
        {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
      </button>

      <AnimatePresence>
        {mobileMenuOpen && (
          <motion.div
            className="mobile-sheet"
            initial={{ opacity: 0, x: 100 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 100 }}
            transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}
            style={{
              position: "fixed",
              top: 70,
              right: 16,
              width: "280px",
              background: "#131722",
              border: "1px solid #282f45",
              borderRadius: "16px",
              padding: "24px",
              zIndex: 100,
              boxShadow: "0 20px 50px rgba(0,0,0,0.5)"
            }}
          >
            <div style={{ display: "flex", flexDirection: "column", gap: "16px" }}>
              <a className="nav-link" href="#workflow" onClick={() => setMobileMenuOpen(false)}>Workflow</a>
              <a className="nav-link" href="#features" onClick={() => setMobileMenuOpen(false)}>Features</a>
              <a className="nav-link" href="#security" onClick={() => setMobileMenuOpen(false)}>Security</a>
              <div style={{ borderTop: "1px solid #242a3d", paddingTop: "16px", display: "flex", flexDirection: "column", gap: "10px" }}>
                <button className="btn-secondary" onClick={() => { setMobileMenuOpen(false); onOpenAuth("demo"); }}>
                  Demo Accounts
                </button>
                <button className="btn-primary" onClick={() => { setMobileMenuOpen(false); onOpenAuth("clerk"); }}>
                  Get Started
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}

function HeroSection({ onGetStarted }: { onGetStarted: () => void }) {
  const [simTokens, setSimTokens] = useState(2);
  const [simReserved, setSimReserved] = useState(false);
  const [simAcked, setSimAcked] = useState(true);
  const [simNotice, setSimNotice] = useState<string | null>(null);

  const handleSimReserve = () => {
    if (!simAcked) {
      setSimNotice("Feedback acknowledgement is required before reserving!");
      return;
    }
    if (simReserved) return;
    if (simTokens <= 0) {
      setSimNotice("No tokens available to reserve!");
      return;
    }
    setSimReserved(true);
    setSimNotice("Retry Reserved! Token balance is preserved (still 2).");
  };

  const handleSimCancel = () => {
    if (!simReserved) return;
    setSimReserved(false);
    setSimNotice("Reservation cancelled before start. Token preserved (remains 2)!");
  };

  const handleSimStart = () => {
    if (!simReserved) {
      setSimNotice("Must reserve retry before start!");
      return;
    }
    setSimReserved(false);
    setSimTokens((prev) => Math.max(0, prev - 1));
    setSimNotice("Retry Started! Exactly 1 token consumed. Kafka LabRetryStarted event emitted.");
  };

  return (
    <section className="hero-section">
      <div className="hero-video-container">
        <video
          className="hero-video"
          autoPlay
          muted
          loop
          playsInline
          poster="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'%3E%3Crect width='100' height='100' fill='%2308090f'/%3E%3C/svg%3E"
        >
          <source
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260606_131516_eca35265-ea66-4fbd-8d52-22aae6e1a503.mp4"
            type="video/mp4"
          />
        </video>
        <div className="hero-video-overlay" />
      </div>

      <motion.div
        className="hero-content"
        initial={{ opacity: 0, y: 28 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="hero-eyebrow">
          <Sparkles size={14} /> LABORATORY RETRY ENTITLEMENT
        </div>
        <h1 className="hero-heading">Every Retry. Earned with Confidence.</h1>
        <p className="hero-supporting">
          A transparent path from lab feedback to your next attempt. Acknowledge feedback, manage retry entitlements, and track every step.
        </p>

        <div className="hero-ctas">
          <button className="btn-primary" onClick={onGetStarted}>
            Get Started <ArrowRight size={16} />
          </button>
          <a className="btn-secondary" href="#workflow">
            Explore How It Works
          </a>
        </div>

        <div className="hero-stats">
          <div className="hero-stat-item">
            <strong>2 Roles</strong>
            <span>Learner & Instructor</span>
          </div>
          <div className="hero-stat-item">
            <strong>100%</strong>
            <span>Durable Persistence</span>
          </div>
          <div className="hero-stat-item">
            <strong>Kafka</strong>
            <span>LabRetryStarted Events</span>
          </div>
        </div>
      </motion.div>

      <motion.div
        className="hero-card"
        initial={{ opacity: 0, y: 35 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.7, delay: 0.15, ease: [0.22, 1, 0.36, 1] }}
      >
        <div className="card-pill-header">
          <span className="status-badge verified">
            <ShieldCheck size={13} /> Token Lifecycle Rule
          </span>
          <span style={{ fontSize: "11px", color: "#9aa4bf" }}>Interactive Simulator</span>
        </div>

        <h3 className="card-sim-title">Token Reservation & Consumption</h3>
        <p className="card-sim-desc">
          Reserving holds a token without deduction. Cancelling restores it safely. Consumption occurs only when the lab actually starts.
        </p>

        <div className="token-balance-strip">
          <div>
            <span style={{ fontSize: "11px", color: "#9aa4bf", display: "block" }}>CURRENT BALANCE</span>
            <strong style={{ fontSize: "20px", color: "#fff" }}>{simTokens} Tokens Available</strong>
          </div>
          <div className="token-pill-group">
            <div className={`token-circle ${simTokens < 1 ? "empty" : ""}`}>1</div>
            <div className={`token-circle ${simTokens < 2 ? "empty" : ""}`}>2</div>
          </div>
        </div>

        <div style={{ background: "rgba(255,255,255,0.03)", padding: "14px", borderRadius: "10px", marginBottom: "18px", border: "1px solid #232a3e" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" }}>
            <span style={{ fontSize: "12px", color: "#9aa4bf" }}>Reservation Status:</span>
            <span style={{ fontSize: "12px", fontWeight: "600", color: simReserved ? "#a78bfa" : "#9aa4bf" }}>
              {simReserved ? "Reserved (Attempt #1)" : "None"}
            </span>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: "12px", color: "#9aa4bf" }}>Feedback Acknowledged:</span>
            <button
              onClick={() => setSimAcked(!simAcked)}
              style={{ background: "none", border: "none", color: simAcked ? "#34d399" : "#fbbf24", cursor: "pointer", fontSize: "12px", textDecoration: "underline" }}
            >
              {simAcked ? "Yes (Verified)" : "No (Required)"}
            </button>
          </div>
        </div>

        {simNotice && (
          <div style={{ fontSize: "12px", padding: "8px 12px", background: "rgba(115, 66, 226, 0.15)", borderRadius: "8px", color: "#c7b3ff", marginBottom: "16px" }}>
            {simNotice}
          </div>
        )}

        <div style={{ display: "flex", gap: "10px" }}>
          <button className="btn-secondary" style={{ flex: 1, padding: "8px" }} onClick={handleSimReserve} disabled={simReserved}>
            Reserve Retry
          </button>
          <button className="btn-secondary" style={{ flex: 1, padding: "8px" }} onClick={handleSimCancel} disabled={!simReserved}>
            Cancel Hold
          </button>
          <button className="btn-primary" style={{ flex: 1, padding: "8px", background: "#10b981" }} onClick={handleSimStart} disabled={!simReserved}>
            Start Lab
          </button>
        </div>
      </motion.div>
    </section>
  );
}

function FeaturesSection() {
  return (
    <section className="features-section" id="features">
      <div className="section-head">
        <span className="section-eyebrow">Enterprise-Grade SOA</span>
        <h2 className="section-title">Engineered for Academic Integrity</h2>
        <p className="section-sub">
          RetryLab combines microservice decoupling with strict domain policies to guarantee educational feedback loops are respected.
        </p>
      </div>

      <div className="feature-cards-grid">
        <div className="feature-box">
          <div className="feature-icon-wrap violet">
            <ShieldCheck size={24} />
          </div>
          <h3>Feedback-First Gating</h3>
          <p>
            Retries are locked until the learner explicitly reviews and acknowledges detailed feedback from the laboratory instructor.
          </p>
        </div>

        <div className="feature-box">
          <div className="feature-icon-wrap green">
            <RotateCcw size={24} />
          </div>
          <h3>Zero-Loss Cancellation</h3>
          <p>
            Learners can reserve retry slots in advance. If cancelled prior to start, entitlement tokens are fully restored with zero forfeiture.
          </p>
        </div>

        <div className="feature-box">
          <div className="feature-icon-wrap blue">
            <Activity size={24} />
          </div>
          <h3>Event-Driven Kafka</h3>
          <p>
            When a retry officially launches, the system emits an immutable `LabRetryStarted` event to `soa.s09.t09.events` with deduplication.
          </p>
        </div>

        <div className="feature-box">
          <div className="feature-icon-wrap orange">
            <Lock size={24} />
          </div>
          <h3>Record-Level Authorization</h3>
          <p>
            Learners are cryptographically scoped to their own attempts. Tampering with route parameters is rejected with 403 Forbidden.
          </p>
        </div>

        <div className="feature-box">
          <div className="feature-icon-wrap violet">
            <Users size={24} />
          </div>
          <h3>Teacher Approval Workflow</h3>
          <p>
            Students whose tokens are exhausted can submit extension requests. Instructors approve or reject requests with auditable notes.
          </p>
        </div>

        <div className="feature-box">
          <div className="feature-icon-wrap green">
            <KeyRound size={24} />
          </div>
          <h3>Clerk & JWT Integration</h3>
          <p>
            Plug-and-play Clerk React authentication backed by Spring Security resource-server validation and internal JWT fallback.
          </p>
        </div>
      </div>
    </section>
  );
}

function WorkflowSection() {
  return (
    <section className="workflow-section" id="workflow">
      <div className="section-head">
        <span className="section-eyebrow">Interactive Process</span>
        <h2 className="section-title">The RetryLab 4-Step Lifecycle</h2>
        <p className="section-sub">
          A predictable sequence ensuring student reflection and controlled lab resource consumption.
        </p>
      </div>

      <div className="workflow-timeline">
        <div className="workflow-card">
          <span className="step-badge">STEP 01</span>
          <h4>Failed Attempt Recorded</h4>
          <p>Lab Attempt Service captures the failure, saving execution score and error traces to durable JPA storage.</p>
        </div>

        <div className="workflow-card">
          <span className="step-badge">STEP 02</span>
          <h4>Instructor Issues Feedback</h4>
          <p>Lab instructor inspects the attempt and publishes corrective guidance into the FeedbackAck service.</p>
        </div>

        <div className="workflow-card">
          <span className="step-badge">STEP 03</span>
          <h4>Student Acknowledges</h4>
          <p>The learner reviews the notes and signs off. Only acknowledged attempts become eligible for retry reservation.</p>
        </div>

        <div className="workflow-card">
          <span className="step-badge">STEP 04</span>
          <h4>Reserve & Start Retry</h4>
          <p>Reservation holds a token. Cancellation preserves it. Starting consumes 1 token and fires the Kafka notification.</p>
        </div>
      </div>
    </section>
  );
}

function SecuritySection() {
  return (
    <section className="workflow-section" id="security" style={{ background: "rgba(255,255,255,0.01)" }}>
      <div className="section-head">
        <span className="section-eyebrow">Section 9, Team 9 Specification</span>
        <h2 className="section-title">Architecture & Security Compliance</h2>
        <p className="section-sub">
          Built following modern Service-Oriented Architecture with Eureka discovery, Gateway routing, and separate JPA entity models.
        </p>
      </div>

      <div style={{ maxWidth: "900px", margin: "0 auto", background: "#131722", border: "1px solid #232a3e", borderRadius: "16px", padding: "32px" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "13px" }}>
          <thead>
            <tr style={{ borderBottom: "1px solid #282f45", textAlign: "left" }}>
              <th style={{ padding: "12px", color: "#9aa4bf" }}>SERVICE</th>
              <th style={{ padding: "12px", color: "#9aa4bf" }}>PORT</th>
              <th style={{ padding: "12px", color: "#9aa4bf" }}>STORAGE / TOPIC</th>
              <th style={{ padding: "12px", color: "#9aa4bf" }}>STATUS</th>
            </tr>
          </thead>
          <tbody>
            <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <td style={{ padding: "12px" }}><strong>Gateway Service</strong></td>
              <td style={{ padding: "12px" }}>:8080</td>
              <td style={{ padding: "12px" }}>Reactive JWT Filter & CORS</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
            <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <td style={{ padding: "12px" }}><strong>Discovery (Eureka)</strong></td>
              <td style={{ padding: "12px" }}>:8761</td>
              <td style={{ padding: "12px" }}>Service Registry & Health</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
            <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <td style={{ padding: "12px" }}><strong>Auth Service</strong></td>
              <td style={{ padding: "12px" }}>:8081</td>
              <td style={{ padding: "12px" }}>JPA Users / Signed JWTs</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
            <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <td style={{ padding: "12px" }}><strong>Lab Attempt Service</strong></td>
              <td style={{ padding: "12px" }}>:8082</td>
              <td style={{ padding: "12px" }}>JPA Attempts / Kafka Consumer</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
            <tr style={{ borderBottom: "1px solid rgba(255,255,255,0.05)" }}>
              <td style={{ padding: "12px" }}><strong>Feedback Ack Service</strong></td>
              <td style={{ padding: "12px" }}>:8083</td>
              <td style={{ padding: "12px" }}>JPA Feedback / Status API</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
            <tr>
              <td style={{ padding: "12px" }}><strong>Retry Entitlement</strong></td>
              <td style={{ padding: "12px" }}>:8084</td>
              <td style={{ padding: "12px" }}>JPA Entitlements & Kafka Outbox</td>
              <td style={{ padding: "12px", color: "#34d399" }}>● Operational</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}

function LandingFooter() {
  return (
    <footer className="landing-footer">
      <div>
        <strong>RetryLab</strong> — Section 9, Team 9 Academic Laboratory Entitlement Platform.
      </div>
      <div>
        Spring Cloud 2024 · Spring Boot 3.4 · React 19 · Clerk Integration
      </div>
    </footer>
  );
}

// ============================================================================
// AUTHENTICATION MODAL (CLERK & LOCAL DEMO ROLES)
// ============================================================================

function AuthModal({
  mode,
  onClose,
  onSelectMode,
  onSuccess
}: {
  mode: "demo" | "custom" | "clerk";
  onClose: () => void;
  onSelectMode: (mode: "demo" | "custom" | "clerk") => void;
  onSuccess: (session: Session) => void;
}) {
  const [username, setUsername] = useState("learner1");
  const [password, setPassword] = useState("password");
  const [errorMsg, setErrorMsg] = useState("");
  const [loading, setLoading] = useState(false);

  const loginWithCredentials = async (_user: string, _pass: string) => {
    setLoading(true);
    setErrorMsg("Password authentication is disabled. Use Clerk sign-in.");
    setLoading(false);
  };

  const handleCustomSubmit = (e: FormEvent) => {
    e.preventDefault();
    loginWithCredentials(username, password);
  };

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <motion.div
        className="auth-modal"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        transition={{ duration: 0.2 }}
      >
        <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
          <X size={20} />
        </button>
        <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "8px" }}>
          <div className="brand-logo-mark" style={{ width: "32px", height: "32px" }}>
            <FlaskConical size={16} />
          </div>
          <strong style={{ fontFamily: "var(--font-heading)", fontSize: "20px" }}>Sign In to RetryLab</strong>
        </div>
        <p style={{ fontSize: "13px", color: "var(--text-secondary)" }}>
          Continue with Clerk to access your server-authorized workspace.
        </p>
        {IS_CLERK_CONFIGURED ? (
          <div style={{ display: "flex", justifyContent: "center", padding: "12px 0" }}>
            <SignIn />
          </div>
        ) : (
          <div className="toast-notice error">
            <AlertTriangle size={16} /> Clerk is not configured. Set VITE_CLERK_PUBLISHABLE_KEY and restart the frontend.
          </div>
        )}
      </motion.div>
    </div>
  );

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <motion.div
        className="auth-modal"
        onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95 }}
        animate={{ opacity: 1, scale: 1 }}
        exit={{ opacity: 0, scale: 0.95 }}
        transition={{ duration: 0.2 }}
      >
        <button className="modal-close-btn" onClick={onClose} aria-label="Close modal">
          <X size={20} />
        </button>

        <div style={{ display: "flex", alignItems: "center", gap: "10px", marginBottom: "8px" }}>
          <div className="brand-logo-mark" style={{ width: "32px", height: "32px" }}>
            <FlaskConical size={16} />
          </div>
          <strong style={{ fontFamily: "var(--font-heading)", fontSize: "20px" }}>Sign In to RetryLab</strong>
        </div>
        <p style={{ fontSize: "13px", color: "var(--text-secondary)" }}>
          Access your personalized workspace with role-based permissions.
        </p>

        <div className="auth-tabs">
          <button
            className={`auth-tab-btn ${mode === "demo" ? "active" : ""}`}
            onClick={() => onSelectMode("demo")}
          >
            Demo Accounts
          </button>
          <button
            className={`auth-tab-btn ${mode === "custom" ? "active" : ""}`}
            onClick={() => onSelectMode("custom")}
          >
            Password
          </button>
          <button
            className={`auth-tab-btn ${mode === "clerk" ? "active" : ""}`}
            onClick={() => onSelectMode("clerk")}
          >
            Clerk Auth
          </button>
        </div>

        {errorMsg && (
          <div className="toast-notice error" style={{ margin: "14px 0" }}>
            <XCircle size={16} /> {errorMsg}
          </div>
        )}

        {mode === "demo" && (
          <div>
            <span style={{ fontSize: "12px", color: "var(--text-muted)", display: "block", marginBottom: "12px" }}>
              SELECT PRE-SEEDED ROLE (REAL SIGNED JWT ISSUED BY AUTH SERVICE):
            </span>
            <div className="demo-account-list">
              <div
                className="demo-account-card"
                onClick={() => loginWithCredentials("learner1", "password")}
              >
                <div>
                  <strong style={{ fontSize: "13px", display: "block" }}>learner1 (Alex Mercer)</strong>
                  <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>Role: Learner · 2 Tokens · Lab-09 Attempt</span>
                </div>
                <ArrowRight size={16} color="var(--color-accent)" />
              </div>

              <div
                className="demo-account-card"
                onClick={() => loginWithCredentials("learner2", "password")}
              >
                <div>
                  <strong style={{ fontSize: "13px", display: "block" }}>learner2 (Sam Taylor)</strong>
                  <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>Role: Learner · 1 Token · Lab-04 Attempt</span>
                </div>
                <ArrowRight size={16} color="var(--color-accent)" />
              </div>

              <div
                className="demo-account-card"
                onClick={() => loginWithCredentials("instructor1", "password")}
                style={{ borderColor: "rgba(115, 66, 226, 0.4)" }}
              >
                <div>
                  <strong style={{ fontSize: "13px", display: "block" }}>instructor1 (Dr. Sarah Chen)</strong>
                  <span style={{ fontSize: "11px", color: "#a78bfa" }}>Role: Lab Instructor · Issue Feedback & Approvals</span>
                </div>
                <ArrowRight size={16} color="var(--color-accent)" />
              </div>
            </div>
          </div>
        )}

        {mode === "custom" && (
          <form onSubmit={handleCustomSubmit} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
            <div>
              <label style={{ fontSize: "12px", color: "var(--text-secondary)", display: "block", marginBottom: "6px" }}>
                Username
              </label>
              <input value={username} onChange={(e) => setUsername(e.target.value)} required />
            </div>
            <div>
              <label style={{ fontSize: "12px", color: "var(--text-secondary)", display: "block", marginBottom: "6px" }}>
                Password
              </label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
            <button className="btn-primary" style={{ marginTop: "10px", justifyContent: "center" }} disabled={loading}>
              {loading ? "Authenticating..." : "Sign In with JWT"}
            </button>
          </form>
        )}

        {mode === "clerk" && (
          <div>
            {IS_CLERK_CONFIGURED ? (
              <div style={{ textAlign: "center", padding: "12px 0" }}>
                <p style={{ fontSize: "13px", marginBottom: "16px" }}>
                  Clerk is configured. Click below to open Clerk Sign In:
                </p>
                <div style={{ display: "flex", justifyContent: "center" }}>
                  <SignIn />
                </div>
              </div>
            ) : (
              <div style={{ background: "rgba(245, 158, 11, 0.08)", border: "1px solid rgba(245, 158, 11, 0.25)", borderRadius: "10px", padding: "16px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "8px", color: "#fbbf24", marginBottom: "8px" }}>
                  <AlertTriangle size={18} />
                  <strong>Clerk Key Not Configured</strong>
                </div>
                <p style={{ fontSize: "12px", color: "var(--text-secondary)", lineHeight: "1.5", marginBottom: "12px" }}>
                  To activate real Clerk Auth, supply your key in <code>frontend/.env.local</code>:
                </p>
                <code style={{ display: "block", background: "#0a0c14", padding: "8px", borderRadius: "6px", fontSize: "11px", color: "#c7b3ff", marginBottom: "14px" }}>
                  VITE_CLERK_PUBLISHABLE_KEY=pk_test_your_key_here
                </code>
                <p style={{ fontSize: "12px", color: "var(--text-muted)" }}>
                  In the meantime, the <strong>Demo Accounts</strong> tab provides 100% verified signed JWTs with real role enforcement.
                </p>
                <button
                  className="btn-primary"
                  style={{ marginTop: "14px", width: "100%", justifyContent: "center" }}
                  onClick={() => onSelectMode("demo")}
                >
                  Switch to Demo Accounts
                </button>
              </div>
            )}
          </div>
        )}
      </motion.div>
    </div>
  );
}

// ============================================================================
// LEARNER DASHBOARD COMPONENT
// ============================================================================

function LearnerDashboard({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const learnerId = session.username;
  const [entitlement, setEntitlement] = useState<Entitlement | null>(null);
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [feedbackList, setFeedbackList] = useState<Feedback[]>([]);
  const [requests, setRequests] = useState<RetryRequest[]>([]);

  const [activeAttemptId, setActiveAttemptId] = useState("");
  const [newLabId, setNewLabId] = useState("lab-09");
  const [newLabNotes, setNewLabNotes] = useState("");
  const [requestReason, setRequestReason] = useState("");

  const [notice, setNotice] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const refreshData = async () => {
    try {
      const [entData, attemptsData, fbData, reqData] = await Promise.all([
        request<Entitlement>(`/entitlements/${learnerId}`),
        request<Attempt[]>("/attempts"),
        request<Feedback[]>("/feedback"),
        request<RetryRequest[]>(`/entitlements/${learnerId}/requests`)
      ]);
      setEntitlement(entData);
      setAttempts(attemptsData);
      setFeedbackList(fbData);
      setRequests(reqData);

      if (!activeAttemptId && attemptsData.length > 0) {
        setActiveAttemptId(String(attemptsData[0].id));
      }
    } catch (err) {
      setNotice({ type: "error", text: err instanceof Error ? err.message : "Unable to reach RetryLab platform" });
    }
  };

  useEffect(() => {
    void refreshData();
  }, [learnerId]);

  const executeAction = async (action: () => Promise<unknown>, successMsg: string) => {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await refreshData();
      setNotice({ type: "success", text: successMsg });
    } catch (err) {
      setNotice({ type: "error", text: err instanceof Error ? err.message : "Action failed" });
    } finally {
      setBusy(false);
    }
  };

  // Step 1: Record Failed Attempt
  const handleRecordAttempt = () =>
    executeAction(async () => {
      const created = await request<Attempt>("/attempts", {
        method: "POST",
        body: JSON.stringify({ learnerId, labId: newLabId, notes: newLabNotes })
      });
      setActiveAttemptId(String(created.id));
      setNewLabNotes("");
    }, "Failed lab attempt recorded in database.");

  // Step 2: Acknowledge Feedback
  const handleAcknowledgeFeedback = (feedbackId: number) =>
    executeAction(async () => {
      await post(`/feedback/${feedbackId}/acknowledge?learnerId=${encodeURIComponent(learnerId)}`);
    }, "Feedback acknowledged successfully. You are now eligible to reserve a retry.");

  // Step 3: Reserve Retry
  const handleReserveRetry = () =>
    executeAction(async () => {
      await post(`/entitlements/${learnerId}/reserve?attemptId=${encodeURIComponent(activeAttemptId)}`);
    }, "Retry reserved without consuming a token. Token balance is preserved.");

  // Step 3 (Alternative): Cancel Reservation
  const handleCancelReservation = () =>
    executeAction(async () => {
      await post(`/entitlements/${learnerId}/cancel?attemptId=${encodeURIComponent(activeAttemptId)}`);
    }, "Reservation cancelled. Retry token preserved.");

  // Step 4: Start Retry
  const handleStartRetry = () =>
    executeAction(async () => {
      await post(`/entitlements/${learnerId}/start?attemptId=${encodeURIComponent(activeAttemptId)}`);
    }, "Retry started! Exactly 1 token consumed. Kafka LabRetryStarted event emitted.");

  // Optional Teacher-Approval Request
  const handleSubmitRetryRequest = (e: FormEvent) => {
    e.preventDefault();
    if (!requestReason.trim()) return;
    executeAction(async () => {
      await request(`/entitlements/${learnerId}/requests`, {
        method: "POST",
        body: JSON.stringify({ reason: requestReason })
      });
      setRequestReason("");
    }, "Additional retry request submitted for instructor review.");
  };

  // Find feedback for selected attempt
  const currentFeedback = feedbackList.find((f) => f.attemptId === activeAttemptId);
  const unacknowledgedCount = feedbackList.filter((f) => !f.acknowledged).length;

  return (
    <div className="app-container">
      <aside className="app-sidebar">
        <div className="sidebar-brand">
          <div className="brand-logo-mark" style={{ width: "34px", height: "34px" }}>
            <FlaskConical size={18} />
          </div>
          <div>
            <strong style={{ fontFamily: "var(--font-heading)", fontSize: "16px", display: "block" }}>RetryLab</strong>
            <span style={{ fontSize: "10px", color: "var(--text-muted)" }}>Learner Workspace</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <button className="sidebar-nav-item active">
            <CircleGauge size={16} /> Overview
          </button>
          <button className="sidebar-nav-item">
            <Activity size={16} /> Attempt History
          </button>
          <button className="sidebar-nav-item">
            <ShieldCheck size={16} /> Policy & Tokens
          </button>
        </nav>

        <div className="sidebar-footer">
          <div className="user-badge-row">
            <div className="user-avatar-circle">
              {session.fullName.charAt(0)}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "12px", display: "block", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {session.fullName}
              </strong>
              <span style={{ fontSize: "10px", color: "#34d399" }}>Learner (Active)</span>
            </div>
          </div>
          <button className="sidebar-nav-item" onClick={onLogout} style={{ color: "#ef4444" }}>
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </aside>

      <main className="app-main">
        <header className="app-header">
          <div>
            <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: "700", letterSpacing: "1.2px", textTransform: "uppercase" }}>
              LEARNER WORKSPACE / {learnerId.toUpperCase()}
            </span>
            <h1>Retry Readiness & Entitlements</h1>
            <p style={{ color: "var(--text-secondary)", fontSize: "14px", marginTop: "4px" }}>
              A verified path from instructor feedback to your next lab execution.
            </p>
          </div>
          <div style={{ display: "flex", gap: "10px" }}>
            <button className="btn-secondary" onClick={() => void refreshData()} disabled={busy}>
              <RotateCcw size={14} /> Refresh
            </button>
          </div>
        </header>

        {notice && (
          <div className={`toast-notice ${notice.type}`}>
            {notice.type === "success" ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
            {notice.text}
          </div>
        )}

        {/* Feedback Awaiting Ack Alert Banner */}
        {unacknowledgedCount > 0 && (
          <div style={{ background: "rgba(245, 158, 11, 0.12)", border: "1px solid rgba(245, 158, 11, 0.3)", borderRadius: "12px", padding: "16px 20px", marginBottom: "24px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
              <AlertTriangle size={20} color="#fbbf24" />
              <div>
                <strong style={{ fontSize: "14px", color: "#fbbf24", display: "block" }}>
                  Feedback Awaiting Acknowledgement ({unacknowledgedCount})
                </strong>
                <span style={{ fontSize: "12px", color: "var(--text-secondary)" }}>
                  Review and acknowledge feedback below to unlock retry reservation.
                </span>
              </div>
            </div>
          </div>
        )}

        {/* Metrics Row */}
        <section className="dashboard-metrics-grid">
          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon violet">
              <RotateCcw size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>AVAILABLE TOKENS</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#fff" }}>
                {entitlement?.tokens ?? 0}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Retries remaining</small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon blue">
              <ShieldCheck size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>ACTIVE RESERVATION</span>
              <strong style={{ fontSize: "20px", fontFamily: "var(--font-heading)", color: "#fff" }}>
                {entitlement?.reservedAttemptId ? `Attempt #${entitlement.reservedAttemptId}` : "None"}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>
                {entitlement?.reservedAttemptId ? "Holding 1 token" : "Ready to reserve"}
              </small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon orange">
              <FlaskConical size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>RECORDED ATTEMPTS</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#fff" }}>
                {attempts.length}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>In this workspace</small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon green">
              <CheckCircle2 size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>POLICY STATUS</span>
              <strong style={{ fontSize: "20px", fontFamily: "var(--font-heading)", color: "#34d399" }}>
                {unacknowledgedCount === 0 ? "Eligible" : "Pending Ack"}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Feedback-first policy</small>
            </div>
          </div>
        </section>

        {/* 2-Column Workflow & History */}
        <section className="dashboard-grid-2col">
          {/* Guided Workflow Card */}
          <div className="dashboard-card">
            <div className="dashboard-card-head">
              <div>
                <span style={{ fontSize: "10px", color: "#9aa4bf", fontWeight: "700", letterSpacing: "1px" }}>GUIDED LIFECYCLE</span>
                <h2>Prepare a Lab Retry</h2>
              </div>
              <span className="status-badge verified">4 Steps</span>
            </div>

            {/* Step 1 */}
            <div style={{ padding: "14px 0", borderTop: "1px solid var(--border-color)" }}>
              <div style={{ display: "flex", gap: "12px", alignItems: "flex-start" }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: "12px", color: "#7342E2", fontWeight: "bold" }}>01</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: "13px", display: "block" }}>Record Failed Lab Attempt</strong>
                  <span style={{ fontSize: "12px", color: "var(--text-secondary)", display: "block", marginBottom: "8px" }}>
                    Creates the attempt record in the database requiring another run.
                  </span>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <input
                      style={{ width: "120px" }}
                      value={newLabId}
                      onChange={(e) => setNewLabId(e.target.value)}
                      placeholder="Lab ID (e.g. lab-09)"
                    />
                    <input
                      style={{ flex: 1 }}
                      value={newLabNotes}
                      onChange={(e) => setNewLabNotes(e.target.value)}
                      placeholder="Notes / Failure details"
                    />
                    <button className="btn-secondary" onClick={handleRecordAttempt} disabled={busy}>
                      Record
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Step 2 */}
            <div style={{ padding: "14px 0", borderTop: "1px solid var(--border-color)" }}>
              <div style={{ display: "flex", gap: "12px", alignItems: "flex-start" }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: "12px", color: "#7342E2", fontWeight: "bold" }}>02</span>
                <div style={{ flex: 1 }}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "6px" }}>
                    <strong style={{ fontSize: "13px" }}>Review & Acknowledge Instructor Feedback</strong>
                    <select
                      style={{ width: "auto", padding: "4px 8px", fontSize: "11px" }}
                      value={activeAttemptId}
                      onChange={(e) => setActiveAttemptId(e.target.value)}
                    >
                      {attempts.map((a) => (
                        <option key={a.id} value={a.id}>
                          Attempt #{a.id} ({a.labId})
                        </option>
                      ))}
                    </select>
                  </div>

                  {currentFeedback ? (
                    <div style={{ background: "rgba(255,255,255,0.03)", border: "1px solid var(--border-color)", borderRadius: "8px", padding: "12px", marginTop: "6px" }}>
                      <p style={{ fontSize: "12px", color: "var(--text-secondary)", marginBottom: "8px" }}>
                        "{currentFeedback.text}"
                      </p>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                          By {currentFeedback.instructorId ?? "Instructor"}
                        </span>
                        {currentFeedback.acknowledged ? (
                          <span style={{ color: "#34d399", fontSize: "11px", display: "flex", alignItems: "center", gap: "4px", fontWeight: "600" }}>
                            <CheckCircle2 size={13} /> Acknowledged
                          </span>
                        ) : (
                          <button
                            className="btn-primary"
                            style={{ padding: "4px 10px", fontSize: "11px" }}
                            onClick={() => handleAcknowledgeFeedback(currentFeedback.id)}
                            disabled={busy}
                          >
                            Acknowledge Feedback
                          </button>
                        )}
                      </div>
                    </div>
                  ) : (
                    <div style={{ fontSize: "12px", color: "var(--text-muted)", fontStyle: "italic", marginTop: "4px" }}>
                      No feedback has been issued for Attempt #{activeAttemptId} yet.
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* Step 3 */}
            <div style={{ padding: "14px 0", borderTop: "1px solid var(--border-color)" }}>
              <div style={{ display: "flex", gap: "12px", alignItems: "flex-start" }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: "12px", color: "#7342E2", fontWeight: "bold" }}>03</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: "13px", display: "block" }}>Reserve Your Retry</strong>
                  <span style={{ fontSize: "12px", color: "var(--text-secondary)", display: "block", marginBottom: "8px" }}>
                    Reservation holds a token without consuming it. Cancelling returns the token immediately.
                  </span>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button
                      className="btn-secondary"
                      onClick={handleReserveRetry}
                      disabled={busy || !activeAttemptId || !currentFeedback?.acknowledged || entitlement?.reservedAttemptId === activeAttemptId}
                    >
                      <ShieldCheck size={14} /> Reserve Retry
                    </button>
                    <button
                      className="btn-secondary"
                      onClick={handleCancelReservation}
                      disabled={busy || entitlement?.reservedAttemptId !== activeAttemptId}
                      style={{ color: "#f87171" }}
                    >
                      <XCircle size={14} /> Cancel Reservation
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Step 4 */}
            <div style={{ padding: "14px 0", borderTop: "1px solid var(--border-color)" }}>
              <div style={{ display: "flex", gap: "12px", alignItems: "flex-start" }}>
                <span style={{ fontFamily: "var(--font-heading)", fontSize: "12px", color: "#7342E2", fontWeight: "bold" }}>04</span>
                <div style={{ flex: 1 }}>
                  <strong style={{ fontSize: "13px", display: "block" }}>Start the Retry</strong>
                  <span style={{ fontSize: "12px", color: "var(--text-secondary)", display: "block", marginBottom: "8px" }}>
                    Token is consumed ONLY when the reserved retry officially starts.
                  </span>
                  <button
                    className="btn-primary"
                    style={{ background: "#10b981", borderColor: "#059669" }}
                    onClick={handleStartRetry}
                    disabled={busy || entitlement?.reservedAttemptId !== activeAttemptId}
                  >
                    <Play size={14} fill="currentColor" /> Start Retry
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Attempt History & Additional Request */}
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#9aa4bf", fontWeight: "700", letterSpacing: "1px" }}>ACTIVITY</span>
                  <h2>Attempt History</h2>
                </div>
              </div>

              {attempts.length === 0 ? (
                <div style={{ textAlign: "center", padding: "32px 0", color: "var(--text-muted)" }}>
                  <FlaskConical size={28} style={{ margin: "0 auto 8px" }} />
                  <p style={{ fontSize: "13px" }}>No attempts recorded yet</p>
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  {attempts.map((att) => {
                    const fb = feedbackList.find((f) => f.attemptId === String(att.id));
                    return (
                      <div
                        key={att.id}
                        style={{
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "space-between",
                          padding: "10px 14px",
                          background: activeAttemptId === String(att.id) ? "rgba(115, 66, 226, 0.12)" : "rgba(255,255,255,0.02)",
                          border: "1px solid",
                          borderColor: activeAttemptId === String(att.id) ? "rgba(115, 66, 226, 0.4)" : "var(--border-color)",
                          borderRadius: "8px",
                          cursor: "pointer"
                        }}
                        onClick={() => setActiveAttemptId(String(att.id))}
                      >
                        <div>
                          <strong style={{ fontSize: "13px", display: "block" }}>
                            Attempt #{att.id} — {att.labId}
                          </strong>
                          <span style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                            Score: {att.score ?? "—"}/100 {att.notes ? `· ${att.notes}` : ""}
                          </span>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <span
                            style={{
                              fontSize: "10px",
                              padding: "3px 8px",
                              borderRadius: "4px",
                              fontWeight: "bold",
                              background: att.status === "RETRY_STARTED" ? "rgba(16, 185, 129, 0.15)" : "rgba(239, 68, 68, 0.15)",
                              color: att.status === "RETRY_STARTED" ? "#34d399" : "#f87171"
                            }}
                          >
                            {att.status}
                          </span>
                          <span style={{ display: "block", fontSize: "10px", color: fb?.acknowledged ? "#34d399" : fb ? "#fbbf24" : "var(--text-muted)", marginTop: "3px" }}>
                            {fb?.acknowledged ? "Feedback Acked" : fb ? "Feedback Pending" : "No Feedback"}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Optional Extension: Request Additional Retries */}
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#a78bfa", fontWeight: "700", letterSpacing: "1px" }}>OPTIONAL EXTENSION</span>
                  <h2>Request Additional Tokens</h2>
                </div>
              </div>
              <p style={{ fontSize: "12px", color: "var(--text-secondary)", marginBottom: "12px" }}>
                Exhausted your retry allowance? Submit a justification for instructor review.
              </p>
              <form onSubmit={handleSubmitRetryRequest} style={{ display: "flex", gap: "8px", marginBottom: "16px" }}>
                <input
                  value={requestReason}
                  onChange={(e) => setRequestReason(e.target.value)}
                  placeholder="Reason for requesting additional retry..."
                  required
                />
                <button className="btn-secondary" disabled={busy}>
                  Submit
                </button>
              </form>

              {requests.length > 0 && (
                <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                  <span style={{ fontSize: "11px", color: "var(--text-muted)", fontWeight: "600" }}>YOUR REQUESTS:</span>
                  {requests.map((r) => (
                    <div key={r.id} style={{ background: "rgba(255,255,255,0.02)", padding: "10px", borderRadius: "8px", border: "1px solid var(--border-color)", fontSize: "12px" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "4px" }}>
                        <span style={{ color: "var(--text-primary)" }}>{r.reason}</span>
                        <strong style={{ color: r.status === "APPROVED" ? "#34d399" : r.status === "REJECTED" ? "#f87171" : "#fbbf24" }}>
                          {r.status}
                        </strong>
                      </div>
                      {r.decisionReason && (
                        <div style={{ fontSize: "11px", color: "var(--text-muted)" }}>
                          Instructor note: {r.decisionReason} {r.status === "APPROVED" ? `(+${r.grantedTokens} token)` : ""}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}

// ============================================================================
// INSTRUCTOR DASHBOARD COMPONENT
// ============================================================================

function InstructorDashboard({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const [attempts, setAttempts] = useState<Attempt[]>([]);
  const [feedbackList, setFeedbackList] = useState<Feedback[]>([]);
  const [requests, setRequests] = useState<RetryRequest[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);

  const [searchTerm, setSearchTerm] = useState("");
  const [filterStatus, setFilterStatus] = useState("ALL");

  const [targetLearnerId, setTargetLearnerId] = useState("learner1");
  const [targetAttemptId, setTargetAttemptId] = useState("1");
  const [newFeedbackText, setNewFeedbackText] = useState("");

  const [notice, setNotice] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);

  const refreshData = async () => {
    try {
      const [attemptsData, fbData, reqData, auditData] = await Promise.all([
        request<Attempt[]>("/attempts"),
        request<Feedback[]>("/feedback"),
        request<RetryRequest[]>("/entitlements/requests"),
        request<AuditLog[]>("/entitlements/audit")
      ]);
      setAttempts(attemptsData);
      setFeedbackList(fbData);
      setRequests(reqData);
      setAuditLogs(auditData);
    } catch (err) {
      setNotice({ type: "error", text: err instanceof Error ? err.message : "Unable to load instructor console" });
    }
  };

  useEffect(() => {
    void refreshData();
  }, []);

  const executeAction = async (action: () => Promise<unknown>, successMsg: string) => {
    setBusy(true);
    setNotice(null);
    try {
      await action();
      await refreshData();
      setNotice({ type: "success", text: successMsg });
    } catch (err) {
      setNotice({ type: "error", text: err instanceof Error ? err.message : "Action failed" });
    } finally {
      setBusy(false);
    }
  };

  // Issue Feedback
  const handleIssueFeedback = (e: FormEvent) => {
    e.preventDefault();
    if (!newFeedbackText.trim()) return;
    executeAction(async () => {
      await request("/feedback", {
        method: "POST",
        body: JSON.stringify({
          learnerId: targetLearnerId,
          attemptId: targetAttemptId,
          text: newFeedbackText
        })
      });
      setNewFeedbackText("");
    }, `Feedback issued to ${targetLearnerId} for Attempt #${targetAttemptId}.`);
  };

  // Review Extra Retry Request
  const handleReviewRequest = (requestId: number, approved: boolean) => {
    const reason = window.prompt(
      approved ? "Enter approval note (e.g. Granted after code review):" : "Enter rejection reason:"
    );
    if (reason === null) return;

    executeAction(async () => {
      await post(
        `/entitlements/requests/${requestId}/review?approved=${approved}&decisionReason=${encodeURIComponent(
          reason || (approved ? "Approved" : "Rejected")
        )}&grantedTokens=1`
      );
    }, approved ? "Request approved: 1 token granted to student balance (requires reservation)." : "Request rejected.");
  };

  // Filter attempts
  const filteredAttempts = attempts.filter((a) => {
    const matchesSearch =
      a.learnerId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      a.labId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      String(a.id).includes(searchTerm);
    const matchesStatus = filterStatus === "ALL" || a.status === filterStatus;
    return matchesSearch && matchesStatus;
  });

  const pendingRequestsCount = requests.filter((r) => r.status === "PENDING").length;
  const unackedFeedbackCount = feedbackList.filter((f) => !f.acknowledged).length;

  return (
    <div className="app-container">
      <aside className="app-sidebar">
        <div className="sidebar-brand">
          <div className="brand-logo-mark" style={{ width: "34px", height: "34px" }}>
            <FlaskConical size={18} />
          </div>
          <div>
            <strong style={{ fontFamily: "var(--font-heading)", fontSize: "16px", display: "block" }}>RetryLab</strong>
            <span style={{ fontSize: "10px", color: "#a78bfa" }}>Instructor Console</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          <button className="sidebar-nav-item active">
            <ShieldCheck size={16} /> Attempts & Review
          </button>
          <button className="sidebar-nav-item">
            <Users size={16} /> Learner Roster
          </button>
          <button className="sidebar-nav-item">
            <Activity size={16} /> Audit Trail
          </button>
        </nav>

        <div className="sidebar-footer">
          <div className="user-badge-row">
            <div className="user-avatar-circle" style={{ background: "#7342E2" }}>
              {session.fullName.charAt(0)}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <strong style={{ fontSize: "12px", display: "block", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {session.fullName}
              </strong>
              <span style={{ fontSize: "10px", color: "#a78bfa" }}>Instructor</span>
            </div>
          </div>
          <button className="sidebar-nav-item" onClick={onLogout} style={{ color: "#ef4444" }}>
            <LogOut size={16} /> Sign out
          </button>
        </div>
      </aside>

      <main className="app-main">
        <header className="app-header">
          <div>
            <span style={{ fontSize: "11px", color: "#a78bfa", fontWeight: "700", letterSpacing: "1.2px", textTransform: "uppercase" }}>
              LAB INSTRUCTOR WORKSPACE / {session.username.toUpperCase()}
            </span>
            <h1>Laboratory Operations & Review</h1>
            <p style={{ color: "var(--text-secondary)", fontSize: "14px", marginTop: "4px" }}>
              Issue targeted feedback, audit retry lifecycle events, and decide extension requests.
            </p>
          </div>
          <button className="btn-secondary" onClick={() => void refreshData()} disabled={busy}>
            <RotateCcw size={14} /> Refresh Console
          </button>
        </header>

        {notice && (
          <div className={`toast-notice ${notice.type}`}>
            {notice.type === "success" ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
            {notice.text}
          </div>
        )}

        {/* Metrics */}
        <section className="dashboard-metrics-grid">
          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon violet">
              <FlaskConical size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>TOTAL ATTEMPTS</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#fff" }}>
                {attempts.length}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Across all learners</small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon orange">
              <Clock size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>AWAITING ACK</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#fbbf24" }}>
                {unackedFeedbackCount}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Learner reflection needed</small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon blue">
              <Users size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>PENDING REQUESTS</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#60a5fa" }}>
                {pendingRequestsCount}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Extension requests</small>
            </div>
          </div>

          <div className="dashboard-metric-card">
            <div className="metric-symbol-icon green">
              <ShieldCheck size={20} />
            </div>
            <div>
              <span style={{ fontSize: "11px", color: "var(--text-muted)", display: "block" }}>AUDIT TRAIL</span>
              <strong style={{ fontSize: "24px", fontFamily: "var(--font-heading)", color: "#34d399" }}>
                {auditLogs.length}
              </strong>
              <small style={{ color: "var(--text-secondary)", fontSize: "11px" }}>Audited transactions</small>
            </div>
          </div>
        </section>

        {/* 2-Column: Attempt Management & Review Queue */}
        <section className="dashboard-grid-2col">
          {/* Left Column: Attempt Table & Issue Feedback */}
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#9aa4bf", fontWeight: "700", letterSpacing: "1px" }}>SEARCH & FILTER</span>
                  <h2>Student Laboratory Attempts</h2>
                </div>
              </div>

              <div style={{ display: "flex", gap: "10px", marginBottom: "16px" }}>
                <div style={{ position: "relative", flex: 1 }}>
                  <Search size={15} style={{ position: "absolute", left: "10px", top: "12px", color: "var(--text-muted)" }} />
                  <input
                    style={{ paddingLeft: "32px" }}
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Search by learner ID, lab ID, or attempt..."
                  />
                </div>
                <select
                  style={{ width: "140px" }}
                  value={filterStatus}
                  onChange={(e) => setFilterStatus(e.target.value)}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="FAILED">FAILED</option>
                  <option value="RETRY_STARTED">RETRY_STARTED</option>
                </select>
              </div>

              <div className="data-table-wrap">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>ID</th>
                      <th>LEARNER</th>
                      <th>LAB</th>
                      <th>STATUS</th>
                      <th>FEEDBACK</th>
                      <th>ACTION</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredAttempts.map((att) => {
                      const fb = feedbackList.find((f) => f.attemptId === String(att.id));
                      return (
                        <tr key={att.id}>
                          <td><strong>#{att.id}</strong></td>
                          <td>{att.learnerId}</td>
                          <td>{att.labId}</td>
                          <td>
                            <span
                              style={{
                                fontSize: "10px",
                                padding: "2px 6px",
                                borderRadius: "4px",
                                fontWeight: "bold",
                                background: att.status === "RETRY_STARTED" ? "rgba(16, 185, 129, 0.15)" : "rgba(239, 68, 68, 0.15)",
                                color: att.status === "RETRY_STARTED" ? "#34d399" : "#f87171"
                              }}
                            >
                              {att.status}
                            </span>
                          </td>
                          <td>
                            {fb ? (
                              <span style={{ color: fb.acknowledged ? "#34d399" : "#fbbf24", fontSize: "11px", fontWeight: "500" }}>
                                {fb.acknowledged ? "Acknowledged" : "Pending Ack"}
                              </span>
                            ) : (
                              <span style={{ color: "var(--text-muted)", fontSize: "11px" }}>None</span>
                            )}
                          </td>
                          <td>
                            <button
                              className="btn-secondary"
                              style={{ padding: "4px 8px", fontSize: "11px" }}
                              onClick={() => {
                                setTargetLearnerId(att.learnerId);
                                setTargetAttemptId(String(att.id));
                              }}
                            >
                              Select
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Issue Feedback Form */}
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#a78bfa", fontWeight: "700", letterSpacing: "1px" }}>AUTHORITATIVE ACTION</span>
                  <h2>Issue Technical Feedback</h2>
                </div>
              </div>
              <form onSubmit={handleIssueFeedback} style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
                <div style={{ display: "flex", gap: "10px" }}>
                  <div style={{ flex: 1 }}>
                    <label style={{ fontSize: "11px", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>
                      Target Learner ID
                    </label>
                    <input
                      value={targetLearnerId}
                      onChange={(e) => setTargetLearnerId(e.target.value)}
                      placeholder="e.g. learner1"
                      required
                    />
                  </div>
                  <div style={{ flex: 1 }}>
                    <label style={{ fontSize: "11px", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>
                      Attempt ID
                    </label>
                    <input
                      value={targetAttemptId}
                      onChange={(e) => setTargetAttemptId(e.target.value)}
                      placeholder="e.g. 1"
                      required
                    />
                  </div>
                </div>

                <div>
                  <label style={{ fontSize: "11px", color: "var(--text-muted)", display: "block", marginBottom: "4px" }}>
                    Corrective Feedback & Guidance
                  </label>
                  <textarea
                    rows={3}
                    value={newFeedbackText}
                    onChange={(e) => setNewFeedbackText(e.target.value)}
                    placeholder="Provide specific code review feedback that the student must acknowledge..."
                    required
                  />
                </div>

                <button className="btn-primary" style={{ alignSelf: "flex-start" }} disabled={busy}>
                  <Send size={14} /> Issue Feedback
                </button>
              </form>
            </div>
          </div>

          {/* Right Column: Retry Requests & Audit Trail */}
          <div style={{ display: "flex", flexDirection: "column", gap: "24px" }}>
            {/* Optional Extension: Review Requests */}
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#a78bfa", fontWeight: "700", letterSpacing: "1px" }}>REVIEW QUEUE</span>
                  <h2>Additional Retry Requests</h2>
                </div>
                {pendingRequestsCount > 0 && (
                  <span className="status-badge" style={{ background: "rgba(245, 158, 11, 0.15)", color: "#fbbf24" }}>
                    {pendingRequestsCount} Pending
                  </span>
                )}
              </div>

              {requests.length === 0 ? (
                <div style={{ textAlign: "center", padding: "24px 0", color: "var(--text-muted)", fontSize: "13px" }}>
                  No retry extension requests in queue.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {requests.map((req) => (
                    <div
                      key={req.id}
                      style={{
                        background: "rgba(255,255,255,0.02)",
                        border: "1px solid var(--border-color)",
                        borderRadius: "8px",
                        padding: "12px"
                      }}
                    >
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "6px" }}>
                        <div>
                          <strong style={{ fontSize: "13px", color: "#fff" }}>{req.learnerId}</strong>
                          <p style={{ fontSize: "12px", color: "var(--text-secondary)", marginTop: "2px" }}>
                            "{req.reason}"
                          </p>
                        </div>
                        <span
                          style={{
                            fontSize: "10px",
                            padding: "2px 6px",
                            borderRadius: "4px",
                            fontWeight: "bold",
                            background: req.status === "APPROVED" ? "rgba(16, 185, 129, 0.15)" : req.status === "REJECTED" ? "rgba(239, 68, 68, 0.15)" : "rgba(245, 158, 11, 0.15)",
                            color: req.status === "APPROVED" ? "#34d399" : req.status === "REJECTED" ? "#f87171" : "#fbbf24"
                          }}
                        >
                          {req.status}
                        </span>
                      </div>

                      {req.decisionReason && (
                        <div style={{ fontSize: "11px", color: "var(--text-muted)", marginBottom: "6px" }}>
                          Decision note: {req.decisionReason} {req.status === "APPROVED" ? `(+${req.grantedTokens} token)` : ""}
                        </div>
                      )}

                      {req.status === "PENDING" && (
                        <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
                          <button
                            className="btn-primary"
                            style={{ padding: "4px 10px", fontSize: "11px", background: "#10b981", borderColor: "#059669" }}
                            onClick={() => handleReviewRequest(req.id, true)}
                            disabled={busy}
                          >
                            Approve (+1 Token)
                          </button>
                          <button
                            className="btn-secondary"
                            style={{ padding: "4px 10px", fontSize: "11px", color: "#f87171" }}
                            onClick={() => handleReviewRequest(req.id, false)}
                            disabled={busy}
                          >
                            Reject
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Audit Trail */}
            <div className="dashboard-card">
              <div className="dashboard-card-head">
                <div>
                  <span style={{ fontSize: "10px", color: "#9aa4bf", fontWeight: "700", letterSpacing: "1px" }}>IMMUTABLE AUDIT</span>
                  <h2>Security Activity Trail</h2>
                </div>
              </div>

              <div style={{ display: "flex", flexDirection: "column", gap: "8px", maxHeight: "380px", overflowY: "auto" }}>
                {auditLogs.slice(0, 15).map((log) => (
                  <div
                    key={log.id}
                    style={{
                      background: "rgba(255,255,255,0.02)",
                      border: "1px solid rgba(255,255,255,0.04)",
                      borderRadius: "6px",
                      padding: "8px 12px",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center"
                    }}
                  >
                    <div>
                      <strong style={{ fontSize: "12px", color: "#a78bfa" }}>{log.action}</strong>
                      <span style={{ fontSize: "11px", color: "var(--text-secondary)", display: "block" }}>
                        {log.details ?? `${log.learnerId} · Attempt ${log.attemptId ?? "N/A"}`}
                      </span>
                    </div>
                    <span style={{ fontSize: "10px", color: "var(--text-muted)" }}>
                      by {log.performedBy ?? "system"}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  );
}

// ============================================================================
// CLERK WRAPPER BOOTSTRAP
// ============================================================================

const rootElement = document.getElementById("root");
if (rootElement) {
  if (IS_CLERK_CONFIGURED) {
    createRoot(rootElement).render(
      <StrictMode>
        <ClerkProvider publishableKey={CLERK_PUBLISHABLE_KEY}>
          <App />
        </ClerkProvider>
      </StrictMode>
    );
  } else {
    createRoot(rootElement).render(
      <StrictMode>
        <App />
      </StrictMode>
    );
  }
}
