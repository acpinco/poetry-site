import type { ReactNode } from "react";
import { Link } from "react-router";
import ravenLogo from "../imports/Raven_Logo.png";

function FeatherDecor({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 40 120"
      fill="none"
      className={`text-gold ${className ?? ""}`}
      aria-hidden="true"
    >
      <path
        d="M20 5 C30 25 35 50 25 70 C20 80 18 95 20 115"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinecap="round"
        opacity=".4"
      />
      <path
        d="M20 20 C28 18 34 22 30 30 C26 26 22 22 20 20Z"
        fill="currentColor"
        opacity=".25"
      />
      <path
        d="M22 35 C30 30 36 35 32 44 C28 38 24 34 22 35Z"
        fill="currentColor"
        opacity=".2"
      />
      <path
        d="M20 18 C12 16 6 22 10 30 C14 26 18 22 20 18Z"
        fill="currentColor"
        opacity=".22"
      />
    </svg>
  );
}

/** Text inputs on the sign-in and profile cards; focus styling is pure CSS. */
export const authInputClass =
  "w-full bg-panel border border-line hover:border-line-strong focus:border-gold focus:shadow-[0_0_0_1px_rgba(201,168,76,0.25)] text-cream placeholder:text-faint rounded px-4 py-3 text-sm outline-none transition-all duration-300";

export const authLabelClass = "block text-xs mb-2 tracking-wide text-muted";

export function AuthDivider() {
  return (
    <div
      className="h-px"
      style={{
        background:
          "linear-gradient(90deg, transparent, var(--color-line), transparent)",
      }}
    />
  );
}

export function AuthSubmitButton({
  busy,
  busyLabel,
}: {
  busy: boolean;
  busyLabel: string;
}) {
  return (
    <button
      type="submit"
      disabled={busy}
      className="w-full py-3.5 text-sm tracking-widest uppercase disabled:opacity-60"
      style={{
        letterSpacing: ".2em",
        background:
          "linear-gradient(135deg, var(--color-gold) 0%, var(--color-gold-dark) 100%)",
        color: "var(--color-night)",
        fontWeight: 600,
        borderRadius: "2px",
        border: "none",
        cursor: "pointer",
      }}
    >
      {busy ? busyLabel : "Take Flight"}
    </button>
  );
}

/** The centred raven card shared by sign-in, profile setup, and loading. */
export default function AuthCard({
  backTo,
  children,
}: {
  /** Shows a "Back to poems" link above the card. */
  backTo?: string;
  children: ReactNode;
}) {
  return (
    <main
      className="min-h-screen flex items-center justify-center px-4 py-16 relative overflow-hidden"
      style={{
        background:
          "radial-gradient(ellipse at 30% 20%, var(--color-twilight) 0%, var(--color-night) 60%)",
      }}
    >
      <FeatherDecor className="absolute top-10 left-8 w-8 h-24 feather-float opacity-60 rotate-12" />
      <FeatherDecor className="absolute bottom-20 right-12 w-6 h-20 feather-float opacity-40 -rotate-6" />
      <div className="relative z-10 w-full max-w-lg">
        {backTo && (
          <Link
            to={backTo}
            className="mb-8 inline-block text-xs uppercase tracking-widest text-gold"
          >
            ← Back to poems
          </Link>
        )}
        <div
          className="card-glow relative w-full"
          style={{
            background:
              "linear-gradient(160deg, var(--color-raised) 0%, var(--color-panel) 100%)",
            border: "1px solid var(--color-line)",
            borderRadius: "4px",
          }}
        >
          <div
            className="absolute top-0 left-8 right-8 h-px"
            style={{
              background:
                "linear-gradient(90deg, transparent, color-mix(in srgb, var(--color-gold) 33%, transparent), transparent)",
            }}
          />
          <div className="px-6 py-10 sm:px-10 sm:py-12">
            <div className="flex justify-center mb-6">
              <img
                src={ravenLogo}
                alt="Think or Drink Poetry"
                className="w-72 max-w-full object-contain"
              />
            </div>
            <p
              className="text-center text-sm italic mb-10"
              style={{ color: "var(--color-dusk)" }}
            >
              Where poets find their voice in shadow and light.
            </p>
            {children}
          </div>
          <div
            className="absolute bottom-0 left-8 right-8 h-px"
            style={{
              background:
                "linear-gradient(90deg, transparent, color-mix(in srgb, var(--color-gold) 33%, transparent), transparent)",
            }}
          />
        </div>
      </div>
    </main>
  );
}

export function AuthLoading() {
  return (
    <AuthCard>
      <p className="text-center text-muted">Preparing your page…</p>
    </AuthCard>
  );
}
