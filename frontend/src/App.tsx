import { FormEvent, useEffect, useState } from "react";
import ravenLogo from "./imports/Raven_Logo.png";
import Home from "./Home";
import PoemEditor from "./PoemEditor";
import ContactPage from "./ContactPage";

function FeatherDecor({ className }: { className?: string }) {
  return <svg viewBox="0 0 40 120" fill="none" className={className} aria-hidden="true">
    <path d="M20 5 C30 25 35 50 25 70 C20 80 18 95 20 115" stroke="#c9a84c" strokeWidth="1.2" strokeLinecap="round" opacity=".4" />
    <path d="M20 20 C28 18 34 22 30 30 C26 26 22 22 20 20Z" fill="#c9a84c" opacity=".25" />
    <path d="M22 35 C30 30 36 35 32 44 C28 38 24 34 22 35Z" fill="#c9a84c" opacity=".2" />
    <path d="M20 18 C12 16 6 22 10 30 C14 26 18 22 20 18Z" fill="#c9a84c" opacity=".22" />
  </svg>;
}

export default function App() {
  const [path, setPath] = useState(() => window.location.pathname + window.location.search);
  const [screen, setScreen] = useState<"loading" | "login" | "profile" | "poem-editor" | "contact">("loading");
  const [existingProfile, setExistingProfile] = useState(false);
  const [email, setEmail] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [penName, setPenName] = useState("");
  const [bio, setBio] = useState("");
  const [focused, setFocused] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const derivedPenName = penName.trim() || (firstName.trim() && lastName.trim() ? `${firstName.trim()} ${lastName.trim()}` : "");
  const inputBase = "w-full bg-[#0e1018] border text-[#e4ddd0] placeholder-[#4a4857] rounded px-4 py-3 text-sm outline-none transition-all duration-300";
  const inputStyle = (field: string) => `${inputBase} ${focused === field ? "border-[#c9a84c] shadow-[0_0_0_1px_rgba(201,168,76,0.25)]" : "border-[#2a2840] hover:border-[#3d3660]"}`;
  const [pathname, queryString = ""] = path.split("?", 2);
  const editMatch = pathname.match(/^\/my-poems\/([^/]+)\/edit$/);
  const isPoemEditor = pathname === "/my-poems/new" || editMatch !== null;
  const isContactPage = pathname === "/contact";
  const homeQuery = new URLSearchParams(queryString);
  const hasMagicLinkError = homeQuery.get("error") === "magic-link";
  const selectedMyPoemId = homeQuery.get("poem") ?? undefined;

  useEffect(() => { if (pathname !== "/home") void loadSession(); }, [pathname]);

  function navigate(nextPath: string) { window.history.pushState({}, "", nextPath); setPath(nextPath); }

  async function loadSession() {
    try {
      const session = await fetch("/api/auth/me", { credentials: "include" });
      if (!session.ok) { setScreen("login"); return; }
      const profile = await fetch("/api/poets/me", { credentials: "include" });
      if (profile.ok) {
        if (pathname === "/account/setup") {
          const data = await profile.json();
          setFirstName(data.firstName ?? ""); setLastName(data.lastName ?? "");
          setPenName(data.penName === data.fullName ? "" : (data.penName ?? "")); setBio(data.bio ?? "");
          setExistingProfile(true); setScreen("profile");
        } else if (isPoemEditor) {
          setScreen("poem-editor");
        } else if (isContactPage) {
          setScreen("contact");
        } else navigate("/home");
        return;
      }
      setScreen("profile");
    } catch { setScreen("login"); }
  }

  async function requestMagicLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(""); setMessage(""); setSubmitting(true);
    try {
      const response = await fetch("/api/auth/magic-links", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      if (!response.ok) throw new Error("We could not send a sign-in link. Please check the email address and try again.");
      setMessage("Check your email for your sign-in link. It will open your profile form.");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "We could not send a sign-in link."); }
    finally { setSubmitting(false); }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(""); setMessage(""); setSubmitting(true);
    try {
      const response = await fetch(existingProfile ? "/api/poets/me" : "/api/poets", {
        method: existingProfile ? "PUT" : "POST", credentials: "include", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firstName, lastName, penName: penName.trim() || null, bio: bio.trim() || null })
      });
      if (!response.ok) {
        if (response.status === 401 || response.status === 403) throw new Error("Please sign in with a magic link before creating your profile.");
        throw new Error("We could not save your profile. Please try again.");
      }
      navigate(existingProfile ? "/home?mine=1" : "/home");
    } catch (reason) { setError(reason instanceof Error ? reason.message : "We could not save your profile."); }
    finally { setSubmitting(false); }
  }

  if (pathname === "/home") return <Home initialMyPoemId={selectedMyPoemId} onNavigate={navigate} />;
  if (screen === "poem-editor") return <PoemEditor poemId={editMatch?.[1]} onNavigate={navigate} />;
  if (screen === "contact") return <ContactPage onNavigate={navigate} />;

  return <main className="min-h-screen flex items-center justify-center px-4 py-16 relative overflow-hidden" style={{ background: "radial-gradient(ellipse at 30% 20%, #12102a 0%, #080a0f 60%)" }}>
    <FeatherDecor className="absolute top-10 left-8 w-8 h-24 feather-float opacity-60 rotate-12" />
    <FeatherDecor className="absolute bottom-20 right-12 w-6 h-20 feather-float opacity-40 -rotate-6" />
    <div className="relative z-10 w-full max-w-lg">
      {screen === "profile" && existingProfile && <button type="button" onClick={() => navigate("/home?mine=1")} className="mb-8 text-xs uppercase tracking-widest text-[#c9a84c]">← Back to poems</button>}
    <div className="card-glow relative w-full" style={{ background: "linear-gradient(160deg, #161a27 0%, #0e1018 100%)", border: "1px solid #2a2840", borderRadius: "4px" }}>
      <div className="absolute top-0 left-8 right-8 h-px" style={{ background: "linear-gradient(90deg, transparent, #c9a84c55, transparent)" }} />
      <div className="px-6 py-10 sm:px-10 sm:py-12">
        <div className="flex justify-center mb-6"><img src={ravenLogo} alt="Think or Drink Poetry" className="w-72 max-w-full object-contain" /></div>
        <p className="text-center text-sm italic mb-10" style={{ color: "#6a6580" }}>Where poets find their voice in shadow and light.</p>
        {screen === "loading" ? <p className="text-center text-[#8b8992]">Preparing your page…</p> : screen === "login" ? <form onSubmit={requestMagicLink} className="space-y-5">
          <div className="text-center"><h1 className="text-2xl text-[#e4ddd0]">Find your voice</h1><p className="mt-2 text-sm text-[#8b8992]">Enter your email and we’ll send you a sign-in link.</p></div>
          <div><label htmlFor="email" className="block text-xs mb-2 tracking-wide text-[#8b8992]">Email Address <span className="text-[#c9a84c]">*</span></label><input id="email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} onFocus={() => setFocused("email")} onBlur={() => setFocused(null)} placeholder="you@example.com" className={inputStyle("email")} /></div>
          {hasMagicLinkError && <p role="alert" className="text-sm text-red-300">That sign-in link is invalid or has expired. Please request a new one.</p>}
          {error && <p role="alert" className="text-sm text-red-300">{error}</p>}{message && <p role="status" className="text-sm text-[#e8c97a]">{message}</p>}
          <div className="h-px" style={{ background: "linear-gradient(90deg, transparent, #2a2840, transparent)" }} />
          <button type="submit" disabled={submitting} className="w-full py-3.5 text-sm tracking-widest uppercase disabled:opacity-60" style={{ letterSpacing: ".2em", background: "linear-gradient(135deg, #c9a84c 0%, #a8872d 100%)", color: "#080a0f", fontWeight: 600, borderRadius: "2px", border: "none", cursor: "pointer" }}>{submitting ? "Sending…" : "Take Flight"}</button>
          <button type="button" onClick={() => navigate("/home")} className="w-full py-3 text-sm tracking-widest uppercase text-[#c9a84c]" style={{ letterSpacing: ".14em", border: "1px solid #3d3660", background: "transparent", cursor: "pointer" }}>Show Me What You Got</button>
        </form> : <form onSubmit={submit} className="space-y-5">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field label="First Name" id="firstName" value={firstName} onChange={setFirstName} focused={focused} setFocused={setFocused} required placeholder="Eleanor" inputStyle={inputStyle} />
            <Field label="Last Name" id="lastName" value={lastName} onChange={setLastName} focused={focused} setFocused={setFocused} required placeholder="Voss" inputStyle={inputStyle} />
          </div>
          <div><label htmlFor="penName" className="block text-xs mb-2 tracking-wide text-[#8b8992]">Pen Name <span className="text-[#4a4857] italic">— optional</span></label><input id="penName" value={penName} onChange={(e) => setPenName(e.target.value)} onFocus={() => setFocused("penName")} onBlur={() => setFocused(null)} maxLength={100} placeholder={derivedPenName || "Your name for the world to know"} className={inputStyle("penName")} />{derivedPenName && !penName && <p className="text-xs mt-1.5 italic text-[#4a4857]">Will appear as <span className="text-[#c9a84c88]">{derivedPenName}</span></p>}</div>
          <div><label htmlFor="bio" className="block text-xs mb-2 tracking-wide text-[#8b8992]">A Few Words About You <span className="text-[#4a4857] italic">— optional</span></label><textarea id="bio" rows={4} value={bio} onChange={(e) => setBio(e.target.value)} onFocus={() => setFocused("bio")} onBlur={() => setFocused(null)} maxLength={500} placeholder="I write at the edge of night, where the words I cannot speak find their shape..." className={`${inputStyle("bio")} resize-none leading-relaxed`} /><p className="text-xs mt-1.5 text-right text-[#4a4857]">{bio.length} / 500</p></div>
          {error && <p role="alert" className="text-sm text-red-300">{error}</p>}{message && <p role="status" className="text-sm text-[#e8c97a]">{message}</p>}
          <div className="h-px" style={{ background: "linear-gradient(90deg, transparent, #2a2840, transparent)" }} />
          <button type="submit" disabled={submitting} className="w-full py-3.5 text-sm tracking-widest uppercase disabled:opacity-60" style={{ letterSpacing: ".2em", background: "linear-gradient(135deg, #c9a84c 0%, #a8872d 100%)", color: "#080a0f", fontWeight: 600, borderRadius: "2px", border: "none", cursor: "pointer" }}>{submitting ? "Saving…" : "Take Flight"}</button>
        </form>}
      </div>
      <div className="absolute bottom-0 left-8 right-8 h-px" style={{ background: "linear-gradient(90deg, transparent, #c9a84c55, transparent)" }} />
    </div>
    </div>
  </main>;
}

function Field({ label, id, value, onChange, focused, setFocused, required, placeholder, inputStyle }: { label: string; id: string; value: string; onChange: (value: string) => void; focused: string | null; setFocused: (value: string | null) => void; required: boolean; placeholder: string; inputStyle: (field: string) => string }) {
  return <div><label htmlFor={id} className="block text-xs mb-2 tracking-wide text-[#8b8992]">{label} {required && <span className="text-[#c9a84c]">*</span>}</label><input id={id} required={required} value={value} onChange={(e) => onChange(e.target.value)} onFocus={() => setFocused(id)} onBlur={() => setFocused(null)} maxLength={100} placeholder={placeholder} className={inputStyle(id)} /></div>;
}
