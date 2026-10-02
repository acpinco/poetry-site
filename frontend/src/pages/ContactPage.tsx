import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { ApiError, sendJson } from "../api";
import ravenLogo from "../imports/Raven_Logo.png";

/** Lets a signed-in poet email the site admin. */
export default function ContactPage() {
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");

  async function send(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSending(true);
    try {
      await sendJson("/api/contact", "POST", { message });
      setSent(true);
      setMessage("");
    } catch (reason) {
      setError(
        reason instanceof ApiError && reason.status === 429
          ? reason.message
          : "Your message could not be sent. Please try again.",
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <main className="min-h-screen bg-night px-4 py-10 text-cream sm:py-16">
      <div className="mx-auto max-w-3xl">
        <Link
          to="/home?view=mine"
          className="mb-8 inline-block text-xs uppercase tracking-widest text-gold"
        >
          ← Back to my poems
        </Link>
        <section className="border border-line bg-panel p-6 shadow-[0_0_30px_rgba(201,168,76,.08)] sm:p-10">
          <img
            src={ravenLogo}
            alt="Think or Drink Poetry"
            className="mx-auto h-12 max-w-full object-contain"
          />
          <p className="mt-8 text-center text-xs uppercase tracking-[.2em] text-gold">
            The Raven's Nest
          </p>
          <h1 className="mt-3 text-center font-serif text-3xl">Contact Me</h1>
          <p className="mx-auto mt-4 max-w-xl text-center leading-relaxed text-parchment">
            Want to report a bug, ask for an enhancement, or just banter with
            the admin of this site? Send a note to The Raven's Nest.
          </p>
          {sent ? (
            <div className="mt-10 text-center">
              <p role="status" className="text-lg text-gold-light">
                Your message has taken flight.
              </p>
              <p className="mt-2 text-sm text-muted">
                Thank you for reaching out.
              </p>
              <Link
                to="/home?view=mine"
                className="mt-8 inline-block border border-gold px-5 py-3 text-xs uppercase tracking-widest text-gold"
              >
                Back to my poems
              </Link>
            </div>
          ) : (
            <form onSubmit={send} className="mx-auto mt-10 max-w-xl space-y-5">
              <div>
                <label
                  htmlFor="contact-message"
                  className="mb-2 block text-xs uppercase tracking-wider text-muted"
                >
                  Your message
                </label>
                <textarea
                  id="contact-message"
                  required
                  maxLength={10000}
                  rows={14}
                  value={message}
                  onChange={(event) => setMessage(event.target.value)}
                  placeholder="Tell the Raven what is on your mind…"
                  className="w-full resize-y border border-line bg-night px-4 py-3 font-serif leading-relaxed outline-none focus:border-gold"
                />
                <p className="mt-1 text-right text-xs text-muted">
                  {message.length.toLocaleString()} / 10,000
                </p>
              </div>
              <p className="text-xs leading-relaxed text-muted">
                Your stored full name, pen name, and account email will be
                included so the admin knows who sent this message.
              </p>
              {error && (
                <p role="alert" className="text-sm text-red-300">
                  {error}
                </p>
              )}
              <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
                <Link
                  to="/home?view=mine"
                  className="border border-line-strong px-5 py-3 text-center text-xs uppercase tracking-widest text-parchment"
                >
                  Cancel
                </Link>
                <button
                  type="submit"
                  disabled={sending}
                  className="bg-gold px-5 py-3 text-xs font-semibold uppercase tracking-widest text-night disabled:opacity-60"
                >
                  {sending ? "Sending…" : "Send to the Raven"}
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </main>
  );
}
