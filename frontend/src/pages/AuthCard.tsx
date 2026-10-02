import type { ReactNode } from "react";
import { Link } from "react-router";
import ravenLogo from "../imports/Raven_Logo.png";

function FeatherDecor({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 40 120"
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <path
        d="M20 5 C30 25 35 50 25 70 C20 80 18 95 20 115"
        stroke="#c9a84c"
        strokeWidth="1.2"
        strokeLinecap="round"
        opacity=".4"
      />
      <path
        d="M20 20 C28 18 34 22 30 30 C26 26 22 22 20 20Z"
        fill="#c9a84c"
        opacity=".25"
      />
      <path
        d="M22 35 C30 30 36 35 32 44 C28 38 24 34 22 35Z"
        fill="#c9a84c"
        opacity=".2"
      />
      <path
        d="M20 18 C12 16 6 22 10 30 C14 26 18 22 20 18Z"
        fill="#c9a84c"
        opacity=".22"
      />
    </svg>
  );
}

/** Text inputs on the sign-in and profile cards; focus styling is pure CSS. */
export const authInputClass =
  "w-full bg-[#0e1018] border border-[#2a2840] hover:border-[#3d3660] focus:border-[#c9a84c] focus:shadow-[0_0_0_1px_rgba(201,168,76,0.25)] text-[#e4ddd0] placeholder-[#4a4857] rounded px-4 py-3 text-sm outline-none transition-all duration-300";

export const authLabelClass = "block text-xs mb-2 tracking-wide text-[#8b8992]";

export function AuthDivider() {
  return (
    <div
      className="h-px"
      style={{
        background: "linear-gradient(90deg, transparent, #2a2840, transparent)",
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
        background: "linear-gradient(135deg, #c9a84c 0%, #a8872d 100%)",
        color: "#080a0f",
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
          "radial-gradient(ellipse at 30% 20%, #12102a 0%, #080a0f 60%)",
      }}
    >
      <FeatherDecor className="absolute top-10 left-8 w-8 h-24 feather-float opacity-60 rotate-12" />
      <FeatherDecor className="absolute bottom-20 right-12 w-6 h-20 feather-float opacity-40 -rotate-6" />
      <div className="relative z-10 w-full max-w-lg">
        {backTo && (
          <Link
            to={backTo}
            className="mb-8 inline-block text-xs uppercase tracking-widest text-[#c9a84c]"
          >
            ← Back to poems
          </Link>
        )}
        <div
          className="card-glow relative w-full"
          style={{
            background: "linear-gradient(160deg, #161a27 0%, #0e1018 100%)",
            border: "1px solid #2a2840",
            borderRadius: "4px",
          }}
        >
          <div
            className="absolute top-0 left-8 right-8 h-px"
            style={{
              background:
                "linear-gradient(90deg, transparent, #c9a84c55, transparent)",
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
              style={{ color: "#6a6580" }}
            >
              Where poets find their voice in shadow and light.
            </p>
            {children}
          </div>
          <div
            className="absolute bottom-0 left-8 right-8 h-px"
            style={{
              background:
                "linear-gradient(90deg, transparent, #c9a84c55, transparent)",
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
      <p className="text-center text-[#8b8992]">Preparing your page…</p>
    </AuthCard>
  );
}
