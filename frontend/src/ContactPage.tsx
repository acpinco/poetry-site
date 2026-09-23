import { FormEvent, useState } from "react";
import ravenLogo from "./imports/Raven_Logo.png";

export default function ContactPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSending(true);
    try {
      const response = await fetch("/api/contact", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message })
      });
      if (response.status === 401 || response.status === 403) { onNavigate("/sign-in"); return; }
      if (!response.ok) throw new Error("Your message could not be sent. Please try again.");
      setSent(true);
      setMessage("");
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Your message could not be sent.");
    } finally {
      setSending(false);
    }
  }

  return <main className="min-h-screen bg-[#080a0f] px-4 py-10 text-[#e4ddd0] sm:py-16">
    <div className="mx-auto max-w-3xl">
      <button type="button" onClick={() => onNavigate("/home?mine=1")} className="mb-8 text-xs uppercase tracking-widest text-[#c9a84c]">← Back to my poems</button>
      <section className="border border-[#2a2840] bg-[#0e1018] p-6 shadow-[0_0_30px_rgba(201,168,76,.08)] sm:p-10">
        <img src={ravenLogo} alt="Think or Drink Poetry" className="mx-auto h-12 max-w-full object-contain" />
        <p className="mt-8 text-center text-xs uppercase tracking-[.2em] text-[#c9a84c]">The Raven's Nest</p>
        <h1 className="mt-3 text-center font-serif text-3xl">Contact Me</h1>
        <p className="mx-auto mt-4 max-w-xl text-center leading-relaxed text-[#c8c0b0]">Want to report a bug, ask for an enhancement, or just banter with the admin of this site? Send a note to The Raven's Nest.</p>
        {sent ? <div className="mt-10 text-center"><p role="status" className="text-lg text-[#e8c97a]">Your message has taken flight.</p><p className="mt-2 text-sm text-[#8b8992]">Thank you for reaching out.</p><button type="button" onClick={() => onNavigate("/home?mine=1")} className="mt-8 border border-[#c9a84c] px-5 py-3 text-xs uppercase tracking-widest text-[#c9a84c]">Back to my poems</button></div> : <form onSubmit={send} className="mx-auto mt-10 max-w-xl space-y-5">
          <div><label htmlFor="contact-message" className="mb-2 block text-xs uppercase tracking-wider text-[#8b8992]">Your message</label><textarea id="contact-message" required maxLength={10000} rows={14} value={message} onChange={event => setMessage(event.target.value)} placeholder="Tell the Raven what is on your mind…" className="w-full resize-y border border-[#2a2840] bg-[#080a0f] px-4 py-3 font-serif leading-relaxed outline-none focus:border-[#c9a84c]" /><p className="mt-1 text-right text-xs text-[#8b8992]">{message.length.toLocaleString()} / 10,000</p></div>
          <p className="text-xs leading-relaxed text-[#8b8992]">Your stored full name, pen name, and account email will be included so the admin knows who sent this message.</p>
          {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end"><button type="button" onClick={() => onNavigate("/home?mine=1")} className="border border-[#3d3660] px-5 py-3 text-xs uppercase tracking-widest text-[#c8c0b0]">Cancel</button><button type="submit" disabled={sending} className="bg-[#c9a84c] px-5 py-3 text-xs font-semibold uppercase tracking-widest text-[#080a0f] disabled:opacity-60">{sending ? "Sending…" : "Send to the Raven"}</button></div>
        </form>}
      </section>
    </div>
  </main>;
}
