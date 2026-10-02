import { useState, type FormEvent } from "react";
import { Link, Navigate, useSearchParams } from "react-router";
import { ApiError, sendJson } from "../api";
import { useSession } from "../session";
import TurnstileWidget from "../TurnstileWidget";
import AuthCard, {
  AuthDivider,
  AuthLoading,
  AuthSubmitButton,
  authInputClass,
  authLabelClass,
} from "./AuthCard";

type Mode = "sign-in" | "sign-up";

function failureMessage(reason: unknown, mode: Mode) {
  if (reason instanceof ApiError && reason.status === 429)
    return "Too many sign-in requests. Please wait a while and try again.";
  if (mode === "sign-up" && reason instanceof ApiError && reason.status === 400)
    return "We could not verify that you are human. Please try again.";
  return "We could not send a sign-in link. Please check the email address and try again.";
}

export default function SignInPage() {
  const { session } = useSession();
  const [searchParams] = useSearchParams();
  const [mode, setMode] = useState<Mode>("sign-in");
  const [email, setEmail] = useState("");
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);
  const [turnstileKey, setTurnstileKey] = useState(0);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const expiredLink = searchParams.get("error") === "magic-link";

  if (session.status === "loading") return <AuthLoading />;
  if (session.status === "signed-in")
    return (
      <Navigate to={session.poetId ? "/home" : "/account/setup"} replace />
    );

  function switchMode(next: Mode) {
    setMode(next);
    setError("");
    setMessage("");
  }

  async function requestLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    const signingUp = mode === "sign-up";
    if (signingUp && !turnstileToken) {
      setError("Please complete the human check first.");
      return;
    }
    setSubmitting(true);
    try {
      await sendJson(
        signingUp ? "/api/auth/sign-up" : "/api/auth/magic-links",
        "POST",
        signingUp ? { email, turnstileToken } : { email },
      );
      setMessage(
        signingUp
          ? "Check your email for your sign-in link. It will open your profile form."
          : "If an account exists for that email, a sign-in link is on its way.",
      );
    } catch (reason) {
      setError(failureMessage(reason, mode));
    } finally {
      setSubmitting(false);
      // Turnstile tokens are single use; show a fresh challenge for any retry.
      if (signingUp) setTurnstileKey((key) => key + 1);
    }
  }

  return (
    <AuthCard>
      <form onSubmit={requestLink} className="space-y-5">
        <div className="text-center">
          <h1 className="text-2xl text-cream">
            {mode === "sign-in" ? "Find your voice" : "Join the poets"}
          </h1>
          <p className="mt-2 text-sm text-muted">
            {mode === "sign-in"
              ? "Enter your account email and we’ll send you a sign-in link."
              : "Enter your email and we’ll send a link to create your account."}
          </p>
        </div>
        <div>
          <label htmlFor="email" className={authLabelClass}>
            Email Address <span className="text-gold">*</span>
          </label>
          <input
            id="email"
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="you@example.com"
            className={authInputClass}
          />
        </div>
        {mode === "sign-up" && (
          <TurnstileWidget key={turnstileKey} onToken={setTurnstileToken} />
        )}
        {expiredLink && (
          <p role="alert" className="text-sm text-red-300">
            That sign-in link is invalid or has expired. Please request a new
            one.
          </p>
        )}
        {error && (
          <p role="alert" className="text-sm text-red-300">
            {error}
          </p>
        )}
        {message && (
          <p role="status" className="text-sm text-gold-light">
            {message}
          </p>
        )}
        {message && mode === "sign-in" && (
          <p className="text-sm text-muted">
            No email after a minute? Check your spam folder, or you may need to{" "}
            <button
              type="button"
              onClick={() => switchMode("sign-up")}
              className="text-gold underline-offset-4 hover:underline"
            >
              create an account
            </button>{" "}
            first.
          </p>
        )}
        <AuthDivider />
        <AuthSubmitButton busy={submitting} busyLabel="Sending…" />
        <p className="text-center text-sm text-muted">
          {mode === "sign-in" ? "New here? " : "Already a poet? "}
          <button
            type="button"
            onClick={() =>
              switchMode(mode === "sign-in" ? "sign-up" : "sign-in")
            }
            className="text-gold underline-offset-4 hover:underline"
          >
            {mode === "sign-in" ? "Create an account" : "Sign in instead"}
          </button>
        </p>
        <Link
          to="/home"
          className="block w-full py-3 text-center text-sm tracking-widest uppercase text-gold"
          style={{
            letterSpacing: ".14em",
            border: "1px solid var(--color-line-strong)",
          }}
        >
          Show Me What You Got
        </Link>
      </form>
    </AuthCard>
  );
}
