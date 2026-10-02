import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";

export type Session =
  | { status: "loading" }
  | { status: "signed-out" }
  | {
      status: "signed-in";
      email: string;
      /** Null until the person has created their poet profile. */
      poetId: string | null;
      admin: boolean;
    };

type SessionContextValue = {
  session: Session;
  /** Re-reads the session, e.g. after creating a profile or signing out. */
  refresh: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

async function fetchSession(): Promise<Session> {
  try {
    const response = await fetch("/api/auth/me");
    if (!response.ok) return { status: "signed-out" };
    const me = (await response.json()) as {
      email: string;
      poetId: string | null;
      admin: boolean;
    };
    return { status: "signed-in", ...me };
  } catch {
    return { status: "signed-out" };
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session>({ status: "loading" });

  const refresh = useCallback(async () => {
    setSession(await fetchSession());
  }, []);

  useEffect(() => {
    let cancelled = false;
    void fetchSession().then((value) => {
      if (!cancelled) setSession(value);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <SessionContext.Provider value={{ session, refresh }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error("useSession must be used inside SessionProvider");
  return value;
}
