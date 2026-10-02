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
type PoetsPage = {
  poets: AdminPoet[];
  page: number;
  size: number;
  totalPoets: number;
  seenLastWeek: number;
};

const PAGE_SIZE = 50;
const STATUS_LABELS: Record<AdminPoet["accountStatus"], string> = {
  ACTIVE: "Active",
  LOCKED: "Locked",
  LEGACY_UNCLAIMED: "Unclaimed (1999)",
};

/**
 * Site admins' list of poets. The server sorts and pages it, so the page stays
 * quick however many poets join.
 */
export default function AdminPage() {
  const [sort, setSort] = useState<Sort>({ key: "lastSeen", descending: true });
  const [page, setPage] = useState(0);
  // loadedAt pins "now" for the relative times, keeping rendering pure.
  const [loaded, setLoaded] = useState<{
    result: PoetsPage;
    loadedAt: Date;
  } | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const query = new URLSearchParams({
      sort: sort.key,
      direction: sort.descending ? "desc" : "asc",
      page: String(page),
      size: String(PAGE_SIZE),
    });
    getJson<PoetsPage>(`/api/admin/poets?${query}`)
      .then((result) => {
        if (!cancelled) setLoaded({ result, loadedAt: new Date() });
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
  }, [sort.key, sort.descending, page]);

  function sortBy(key: SortKey) {
    setPage(0);
    setSort((current) =>
      current.key === key
        ? { key, descending: !current.descending }
        : // Dates read best newest-first; text reads best A–Z.
          { key, descending: key === "joined" || key === "lastSeen" },
    );
  }

  const poets = loaded?.result.poets ?? null;
  const totalPoets = loaded?.result.totalPoets ?? 0;
  const pageCount = Math.max(1, Math.ceil(totalPoets / PAGE_SIZE));

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
                {totalPoets} {totalPoets === 1 ? "poet" : "poets"} ·{" "}
                {loaded!.result.seenLastWeek} seen in the last 7 days
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
                    {poets.map((poet) => (
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
              {pageCount > 1 && (
                <nav
                  aria-label="Poet list pages"
                  className="mt-6 flex items-center justify-center gap-4 text-xs uppercase tracking-wider"
                >
                  <button
                    type="button"
                    disabled={page === 0}
                    onClick={() => setPage((current) => current - 1)}
                    className="border border-line-strong px-3 py-2 text-parchment hover:border-gold hover:text-gold-light disabled:opacity-40"
                  >
                    ← Previous
                  </button>
                  <span className="text-muted">
                    Page {page + 1} of {pageCount}
                  </span>
                  <button
                    type="button"
                    disabled={page + 1 >= pageCount}
                    onClick={() => setPage((current) => current + 1)}
                    className="border border-line-strong px-3 py-2 text-parchment hover:border-gold hover:text-gold-light disabled:opacity-40"
                  >
                    Next →
                  </button>
                </nav>
              )}
            </>
          )}
        </section>
      </div>
    </main>
  );
}
