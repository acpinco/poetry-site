import { useEffect, useRef, useState } from "react";

type Turnstile = {
  render: (
    container: HTMLElement,
    options: {
      sitekey: string;
      theme: "dark";
      callback: (token: string) => void;
      "expired-callback": () => void;
      "error-callback": () => void;
    },
  ) => string;
  remove: (widgetId: string) => void;
};

declare global {
  interface Window {
    turnstile?: Turnstile;
  }
}

const SCRIPT_URL =
  "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
let scriptLoading: Promise<Turnstile> | null = null;

function loadTurnstile() {
  scriptLoading ??= new Promise<Turnstile>((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_URL;
    script.async = true;
    script.onload = () =>
      window.turnstile
        ? resolve(window.turnstile)
        : reject(new Error("Turnstile did not load."));
    script.onerror = () => {
      scriptLoading = null;
      reject(new Error("Turnstile did not load."));
    };
    document.head.appendChild(script);
  });
  return scriptLoading;
}

/**
 * Cloudflare Turnstile human check for new-account sign-up. Tokens are single
 * use, so remount the widget (change its `key`) after each submission.
 */
export default function TurnstileWidget({
  onToken,
}: {
  onToken: (token: string | null) => void;
}) {
  const container = useRef<HTMLDivElement | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let widgetId: string | null = null;
    let cancelled = false;

    async function render() {
      try {
        const config = await fetch("/api/auth/sign-up/config");
        const { turnstileSiteKey } = (await config.json()) as {
          turnstileSiteKey: string | null;
        };
        if (!turnstileSiteKey) throw new Error("Sign-up is not configured.");
        const turnstile = await loadTurnstile();
        if (cancelled || !container.current) return;
        widgetId = turnstile.render(container.current, {
          sitekey: turnstileSiteKey,
          theme: "dark",
          callback: (token) => onToken(token),
          "expired-callback": () => onToken(null),
          "error-callback": () => onToken(null),
        });
      } catch {
        if (!cancelled) setFailed(true);
      }
    }

    void render();
    return () => {
      cancelled = true;
      if (widgetId) window.turnstile?.remove(widgetId);
      onToken(null);
    };
    // Render once per mount; onToken is a stable state setter from the parent.
  }, []);

  return failed ? (
    <p role="alert" className="text-sm text-red-300">
      Account sign-up is unavailable right now. Please try again later.
    </p>
  ) : (
    <div ref={container} className="flex min-h-[65px] justify-center" />
  );
}
