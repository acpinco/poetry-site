import { useEffect, useState } from "react";
import { Link } from "react-router";
import { ApiError, getJson } from "../api";
import ravenLogo from "../imports/Raven_Logo.png";
import { formatDate, formatDateTime, timeAgo } from "../poetry";

type AdminPoet = {
  poetId: string;
  email: string;
  fullName: string;
  penName: string;
  createdAt: string;
  accountStatus: "ACTIVE" | "LOCKED" | "LEGACY_UNCLAIMED";
  role: "USER" | "ADMIN";
  lastSeenAt: string | null;
};

type SortKey = "name" | "email" | "status" | "joined" | "lastSeen";
type Sort = { key: SortKey; descending: boolean };

const STATUS_LABELS: Record<AdminPoet["accountStatus"], string> = {
  ACTIVE: "Active",
  LOCKED: "Locked",
  LEGACY_UNCLAIMED: "Unclaimed (1999)",
};
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

function sortValue(poet: AdminPoet, key: SortKey) {
  switch (key) {
    case "name":
      return poet.penName.toLowerCase();
    case "email":
      return poet.email;
    case "status":
      return STATUS_LABELS[poet.accountStatus];
    case "joined":
      return Date.parse(poet.createdAt);
    case "lastSeen":
      return poet.lastSeenAt === null ? null : Date.parse(poet.lastSeenAt);
  }
}

function sortPoets(poets: AdminPoet[], { key, descending }: Sort) {
  return [...poets].sort((a, b) => {
    const left = sortValue(a, key);
    const right = sortValue(b, key);
    // Poets who have never been seen stay at the bottom in both directions.
    if (left === null || right === null)
      return left === right ? 0 : left === null ? 1 : -1;
    const order = left < right ? -1 : left > right ? 1 : 0;
    return descending ? -order : order;
  });
}

/** Site admins' list of poets, sortable by when each was last seen. */
export default function AdminPage() {
  // loadedAt pins "now" for the relative times, keeping rendering pure.
  const [loaded, setLoaded] = useState<{
    poets: AdminPoet[];
    loadedAt: Date;
  } | null>(null);
  const [error, setError] = useState("");
  const [sort, setSort] = useState<Sort>({ key: "lastSeen", descending: true });
  const poets = loaded?.poets ?? null;

  useEffect(() => {
    let cancelled = false;
    getJson<AdminPoet[]>("/api/admin/poets")
      .then((value) => {
        if (!cancelled) setLoaded({ poets: value, loadedAt: new Date() });
      })
      .catch((reason) => {
        if (cancelled) return;
        setError(
          reason instanceof ApiError && reason.status === 403
            ? "This page is only available to site admins."
            : "The poet list could not be loaded.",
        );
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function sortBy(key: SortKey) {
    setSort((current) =>
      current.key === key
        ? { key, descending: !current.descending }
        : // Dates read best newest-first; text reads best A–Z.
          { key, descending: key === "joined" || key === "lastSeen" },
    );
  }

  const sorted = poets ? sortPoets(poets, sort) : [];
  const seenThisWeek = loaded
    ? sorted.filter(
        (poet) =>
          poet.lastSeenAt &&
          loaded.loadedAt.getTime() - new Date(poet.lastSeenAt).getTime() <
            WEEK_MS,
      ).length
    : 0;

  function header(key: SortKey, label: string) {
    const active = sort.key === key;
    return (
      <th
        scope="col"
        aria-sort={
          active ? (sort.descending ? "descending" : "ascending") : "none"
        }
        className="border-b border-line px-3 py-3 text-left font-normal"
      >
        <button
          type="button"
          onClick={() => sortBy(key)}
          className={`text-xs uppercase tracking-wider hover:text-gold-light ${active ? "text-gold" : "text-muted"}`}
        >
          {label}
          <span aria-hidden="true" className="ml-1 inline-block w-3">
            {active ? (sort.descending ? "↓" : "↑") : ""}
          </span>
        </button>
      </th>
    );
  }

  return (
    <main className="min-h-screen bg-night px-4 py-10 text-cream sm:py-16">
      <div className="mx-auto max-w-5xl">
        <Link
          to="/home"
          className="mb-8 inline-block text-xs uppercase tracking-widest text-gold"
        >
          ← Back to poems
        </Link>
        <section className="border border-line bg-panel p-6 shadow-[0_0_30px_rgba(201,168,76,.08)] sm:p-10">
          <img
            src={ravenLogo}
            alt="Think or Drink Poetry"
            className="mx-auto h-12 max-w-full object-contain"
          />
          <h1 className="mt-8 text-center font-serif text-3xl">Poets</h1>
          {error ? (
            <p role="alert" className="mt-10 text-center text-red-300">
              {error}
            </p>
          ) : !poets ? (
            <p className="mt-10 text-center text-muted">Loading poets…</p>
          ) : (
            <>
              <p className="mt-2 text-center text-sm text-muted">
                {poets.length} {poets.length === 1 ? "poet" : "poets"} ·{" "}
                {seenThisWeek} seen in the last 7 days
              </p>
              <div className="mt-8 overflow-x-auto">
                <table className="w-full min-w-[640px] border-collapse text-sm">
                  <thead>
                    <tr>
                      {header("name", "Poet")}
                      {header("email", "Email")}
                      {header("status", "Status")}
                      {header("joined", "Joined")}
                      {header("lastSeen", "Last seen")}
                    </tr>
                  </thead>
                  <tbody>
                    {sorted.map((poet) => (
                      <tr
                        key={poet.poetId}
                        className="border-b border-line-soft hover:bg-raised"
                      >
                        <td className="px-3 py-3">
                          <span className="font-serif">{poet.penName}</span>
                          {poet.role === "ADMIN" && (
                            <span className="ml-2 border border-gold px-1.5 text-[10px] uppercase tracking-wider text-gold">
                              Admin
                            </span>
                          )}
                          {poet.penName !== poet.fullName && (
                            <span className="block text-xs text-muted">
                              {poet.fullName}
                            </span>
                          )}
                        </td>
                        <td className="px-3 py-3 text-parchment">
                          {poet.email}
                        </td>
                        <td
                          className={`px-3 py-3 ${poet.accountStatus === "LOCKED" ? "text-red-300" : "text-parchment"}`}
                        >
                          {STATUS_LABELS[poet.accountStatus]}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3 text-parchment">
                          {formatDate(poet.createdAt)}
                        </td>
                        <td className="whitespace-nowrap px-3 py-3">
                          {poet.lastSeenAt ? (
                            <time
                              dateTime={poet.lastSeenAt}
                              title={formatDateTime(poet.lastSeenAt)}
                            >
                              {timeAgo(poet.lastSeenAt, loaded!.loadedAt)}
                            </time>
                          ) : (
                            <span className="text-muted">Never</span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </section>
      </div>
    </main>
  );
}
