import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { ApiError, getJson, isAbort, sendJson } from "../api";
import BrowseSidebar from "../home/BrowseSidebar";
import HomeHeader from "../home/HomeHeader";
import {
  DesktopPoemReader,
  HomeFooter,
  MobilePoemReader,
} from "../home/PoemReader";
import type {
  DisplayedPoem,
  OwnedPoem,
  Poem,
  Poet,
  PoemSummary,
  RecentPoem,
  SearchResults,
} from "../home/types";
import { useSession } from "../session";

const SEARCH_DELAY_MS = 700;
const RECENT_PAGE_SIZE = 60;

type ListKind = "mine" | "poet" | "all";

/** The poems shown in the sidebar: the viewer's own, one poet's, or everyone's newest. */
type Collection =
  | { kind: "mine" | "poet"; key: string; poet: Poet; poems: DisplayedPoem[] }
  | { kind: "all"; key: string; poems: DisplayedPoem[]; hasMore: boolean };

type Profile = {
  poetId: string;
  penName: string;
  fullName: string;
  bio: string | null;
};

function messageFor(reason: unknown) {
  return reason instanceof Error ? reason.message : "Unable to load poems.";
}

function withPoet(poems: PoemSummary[], poet: Poet): DisplayedPoem[] {
  return poems.map((poem) => ({
    ...poem,
    poetId: poet.poetId,
    poetDisplayName: poet.displayName,
  }));
}

async function loadCollection(
  kind: ListKind,
  key: string,
  poetId: string | null,
): Promise<Collection> {
  if (kind === "mine") {
    const [poems, profile] = await Promise.all([
      getJson<OwnedPoem[]>("/api/poems"),
      getJson<Profile>("/api/poets/me"),
    ]);
    const poet = {
      poetId: profile.poetId,
      displayName: profile.penName || profile.fullName,
      bio: profile.bio,
      poemCount: poems.length,
    };
    const summaries = poems.map((poem) => ({
      poemId: poem.poemId,
      title: poem.title,
      excerpt: poem.poem.replace(/\s+/g, " ").slice(0, 160),
      createdAt: poem.createdAt,
    }));
    return { kind, key, poet, poems: withPoet(summaries, poet) };
  }
  if (kind === "poet") {
    const value = await getJson<{ poet: Poet; poems: PoemSummary[] }>(
      `/api/discovery/poets/${poetId}/poems`,
    );
    return {
      kind,
      key,
      poet: value.poet,
      poems: withPoet(value.poems, value.poet),
    };
  }
  const page = await getJson<{ poems: RecentPoem[]; hasMore: boolean }>(
    `/api/discovery/poems/recent?limit=${RECENT_PAGE_SIZE}&offset=0`,
  );
  return { kind, key, poems: page.poems, hasMore: page.hasMore };
}

/**
 * The reading room. The URL is the source of truth, so Back, refresh, and
 * shared links all work:
 *   /home?view=mine | /home?poet=<id> | /home?view=all   which list to show
 *   &poem=<id>                                           which poem is open
 * A bare /home?poem=<id> (from a bio page) opens that poem's poet. The older
 * /home?mine=1 form still works.
 */
export default function HomePage() {
  const { session, refresh } = useSession();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const poemParam = searchParams.get("poem");
  const poetParam = searchParams.get("poet");
  const viewParam =
    searchParams.get("view") ??
    (searchParams.get("mine") === "1" ? "mine" : null);
  const viewer = session.status === "signed-in";
  const viewerPoetId = session.status === "signed-in" ? session.poetId : null;
  const viewerIsAdmin = session.status === "signed-in" && session.admin;

  const listKind: ListKind | "resolve-poem" | "waiting" =
    session.status === "loading"
      ? "waiting"
      : viewParam === "mine" && viewerPoetId
        ? "mine"
        : viewParam === "all"
          ? "all"
          : poetParam
            ? "poet"
            : poemParam
              ? "resolve-poem"
              : viewerPoetId
                ? "mine"
                : "all";
  const listKey = listKind === "poet" ? `poet:${poetParam}` : listKind;

  const [collection, setCollection] = useState<Collection | null>(null);
  const [active, setActive] = useState<Poem | null>(null);
  const [poemOfTheDay, setPoemOfTheDay] = useState<Poem | null>(null);
  const [directory, setDirectory] = useState<Poet[] | null>(null);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState<{
    term: string;
    results: SearchResults;
  } | null>(null);
  // A failure before anything loaded shows a full-page error; afterwards, a banner.
  const [notice, setNotice] = useState("");
  const [reloads, setReloads] = useState(0);
  const poemPanel = useRef<HTMLElement | null>(null);
  const mobilePoemPanel = useRef<HTMLElement | null>(null);

  // While a new list loads, the previous one stays on screen; only a list that
  // matches the URL may choose which poem opens by default.
  const currentCollection = collection?.key === listKey ? collection : null;
  const targetPoemId = poemParam ?? currentCollection?.poems[0]?.poemId ?? null;
  const term = query.trim();
  const results =
    term.length >= 2 && search?.term === term ? search.results : null;

  useEffect(() => {
    let cancelled = false;
    getJson<Poem>("/api/discovery/poem-of-the-day")
      .then((poem) => !cancelled && setPoemOfTheDay(poem))
      .catch(() => {});
    // The directory only feeds the search dropdown; browsing works without it.
    getJson<Poet[]>("/api/discovery/poets")
      .then((poets) => !cancelled && setDirectory(poets))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (listKind === "waiting" || listKind === "resolve-poem") return;
    let cancelled = false;
    loadCollection(listKind, listKey, poetParam)
      .then((value) => !cancelled && setCollection(value))
      .catch((reason) => !cancelled && setNotice(messageFor(reason)));
    return () => {
      cancelled = true;
    };
  }, [listKind, listKey, poetParam, reloads]);

  useEffect(() => {
    if (listKind !== "resolve-poem" || !poemParam) return;
    let cancelled = false;
    getJson<Poem>(`/api/discovery/poems/${poemParam}`)
      .then((poem) => {
        if (cancelled) return;
        setActive(poem);
        navigate(`/home?poet=${poem.poetId}&poem=${poem.poemId}`, {
          replace: true,
        });
      })
      .catch((reason) => {
        if (cancelled) return;
        setNotice(
          reason instanceof ApiError && reason.status === 404
            ? "That poem could not be found."
            : messageFor(reason),
        );
        if (reason instanceof ApiError && reason.status === 404)
          navigate("/home", { replace: true });
      });
    return () => {
      cancelled = true;
    };
  }, [listKind, poemParam, navigate]);

  useEffect(() => {
    if (!targetPoemId || listKind === "resolve-poem") return;
    if (active?.poemId === targetPoemId) return;
    let cancelled = false;
    getJson<Poem>(`/api/discovery/poems/${targetPoemId}`)
      .then((poem) => !cancelled && setActive(poem))
      .catch((reason) => !cancelled && setNotice(messageFor(reason)));
    return () => {
      cancelled = true;
    };
  }, [targetPoemId, listKind, active?.poemId]);

  useEffect(() => {
    poemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
    mobilePoemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [active?.poemId]);

  // Search waits for a pause in typing and cancels the previous request, so
  // results always match the box and typing does not exhaust the rate limit.
  useEffect(() => {
    if (term.length < 2) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      getJson<SearchResults>(
        `/api/discovery/search?q=${encodeURIComponent(term)}`,
        { signal: controller.signal },
      )
        .then((value) => {
          if (!controller.signal.aborted) setSearch({ term, results: value });
        })
        .catch((reason) => {
          if (!isAbort(reason)) setSearch(null);
        });
    }, SEARCH_DELAY_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [term]);

  /** Moves to a new reading-room location; the effects above load it. */
  function go(params: Record<string, string | null | undefined>) {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(params))
      if (value) query.set(name, value);
    setNotice("");
    setQuery("");
    navigate(`/home?${query}`);
  }

  function listParams() {
    if (collection?.kind === "all") return { view: "all" };
    if (collection?.kind === "mine") return { view: "mine" };
    return { poet: collection?.poet.poetId };
  }

  /** Selects a poem from the current list, keeping that list. */
  async function selectListPoem(poemId: string) {
    go({ ...listParams(), poem: poemId });
  }

  /** Opens any poem (search, Poem of the Day) alongside its poet's other poems. */
  async function openPoem(poemId: string) {
    go({ poem: poemId });
  }

  async function choosePoet(poetId: string) {
    go({ poet: poetId });
  }

  async function showAllPoems() {
    go({ view: "all" });
  }

  async function showMyPoems() {
    go({ view: "mine" });
  }

  async function loadMore() {
    if (collection?.kind !== "all") return;
    const { key, poems } = collection;
    try {
      const page = await getJson<{ poems: RecentPoem[]; hasMore: boolean }>(
        `/api/discovery/poems/recent?limit=${RECENT_PAGE_SIZE}&offset=${poems.length}`,
      );
      // Ignore the page if the visitor moved on or another page landed first.
      setCollection((current) =>
        current?.kind === "all" &&
        current.key === key &&
        current.poems.length === poems.length
          ? {
              ...current,
              poems: [...current.poems, ...page.poems],
              hasMore: page.hasMore,
            }
          : current,
      );
    } catch (reason) {
      setNotice(messageFor(reason));
    }
  }

  async function chooseAdjacentPoem(direction: -1 | 1) {
    if (!active || !collection) return;
    const index = collection.poems.findIndex(
      (poem) => poem.poemId === active.poemId,
    );
    const adjacent = collection.poems[index + direction];
    if (adjacent) await selectListPoem(adjacent.poemId);
  }

  async function deleteActivePoem() {
    if (
      !active ||
      !window.confirm(`Delete “${active.title}”? This cannot be undone.`)
    )
      return;
    try {
      const deletedId = active.poemId;
      await sendJson(`/api/poems/${deletedId}`, "DELETE");
      // Drop it at once so the reader never reopens it while the list reloads.
      setCollection((current) =>
        current
          ? {
              ...current,
              poems: current.poems.filter((poem) => poem.poemId !== deletedId),
            }
          : current,
      );
      setReloads((count) => count + 1);
      go({ view: "mine" });
    } catch {
      setNotice("Your poem could not be deleted. Please try again.");
    }
  }

  async function signOut() {
    await sendJson("/api/auth/logout", "POST").catch(() => undefined);
    await refresh();
    navigate("/sign-in");
  }

  if (!collection && notice)
    return (
      <main className="grid min-h-screen place-items-center bg-[#080a0f] px-4 text-center text-[#e4ddd0]">
        <div>
          <p role="alert">{notice}</p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-6 border border-[#c9a84c] px-5 py-3 text-xs uppercase tracking-widest text-[#c9a84c] hover:text-[#e8c97a]"
          >
            Try again
          </button>
        </div>
      </main>
    );

  const showingAllPoems = collection?.kind === "all";
  const displayedPoems = collection?.poems ?? [];
  const emptyMyPoems =
    collection?.kind === "mine" && collection.poems.length === 0;
  const noticeBanner = notice && (
    <div
      role="alert"
      className="flex shrink-0 items-center justify-between gap-3 border-b border-[#5a2a33] bg-[#24121a] px-4 py-2 text-sm text-red-200"
    >
      <span>{notice}</span>
      <button
        type="button"
        onClick={() => setNotice("")}
        className="shrink-0 text-xs uppercase tracking-wider text-red-100 hover:text-white"
      >
        Dismiss
      </button>
    </div>
  );
  const header = (
    <HomeHeader
      activePoemId={emptyMyPoems ? "" : (active?.poemId ?? "")}
      activePoetId={
        collection && collection.kind !== "all" ? collection.poet.poetId : ""
      }
      directory={directory}
      displayedPoems={displayedPoems}
      moreRecentPoems={collection?.kind === "all" && collection.hasMore}
      onChoosePoem={openPoem}
      onSelectListPoem={selectListPoem}
      onChoosePoet={choosePoet}
      onLoadMore={loadMore}
      onMyPoems={showMyPoems}
      onNavigate={navigate}
      onSearch={setQuery}
      onShowAllPoems={showAllPoems}
      onSignOut={signOut}
      query={query}
      results={results}
      showingAllPoems={showingAllPoems}
      viewer={viewer}
      viewerIsAdmin={viewerIsAdmin}
    />
  );

  if (emptyMyPoems || (collection?.kind === "all" && !displayedPoems.length))
    return (
      <main className="flex min-h-screen flex-col bg-[#080a0f] text-[#e4ddd0]">
        {noticeBanner}
        {header}
        <section className="grid flex-1 place-items-center px-6 py-16 text-center">
          {emptyMyPoems ? (
            <div className="max-w-md">
              <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
                My Poems
              </p>
              <h1 className="mt-3 font-serif text-3xl">
                You haven’t added any poems yet
              </h1>
              <p className="mt-4 leading-relaxed text-[#8b8992]">
                Your collection will appear here once you publish your first
                poem.
              </p>
              <div className="mt-8 flex flex-col justify-center gap-3 sm:flex-row">
                <Link
                  to="/my-poems/new"
                  className="bg-[#c9a84c] px-5 py-3 text-xs font-semibold uppercase tracking-widest text-[#080a0f] hover:bg-[#e8c97a]"
                >
                  Write your first poem
                </Link>
                <Link
                  to="/home?view=all"
                  className="border border-[#3d3660] px-5 py-3 text-xs uppercase tracking-widest text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]"
                >
                  Browse all poems
                </Link>
              </div>
            </div>
          ) : (
            <p className="text-[#8b8992]">No poems are available yet.</p>
          )}
        </section>
        <HomeFooter onNavigate={navigate} viewer={viewer} />
      </main>
    );

  if (!collection || !active)
    return (
      <main className="grid min-h-screen place-items-center bg-[#080a0f] text-[#8b8992]">
        Gathering poems…
      </main>
    );

  const activeIndex = displayedPoems.findIndex(
    (poem) => poem.poemId === active.poemId,
  );
  const hasPreviousPoem = activeIndex > 0;
  const hasNextPoem =
    activeIndex >= 0 && activeIndex < displayedPoems.length - 1;
  return (
    <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
      {noticeBanner}
      {header}

      <div className="flex min-h-0 flex-1 flex-col lg:hidden">
        <MobilePoemReader
          active={active}
          mobilePoemPanel={mobilePoemPanel}
          onChoosePoem={openPoem}
          onDelete={deleteActivePoem}
          onNextPoem={() => chooseAdjacentPoem(1)}
          onNavigate={navigate}
          onPreviousPoem={() => chooseAdjacentPoem(-1)}
          hasNextPoem={hasNextPoem}
          hasPreviousPoem={hasPreviousPoem}
          poemOfTheDay={poemOfTheDay}
          viewerPoetId={viewerPoetId}
        />
        <HomeFooter compact onNavigate={navigate} viewer={viewer} />
      </div>

      <div className="hidden min-h-0 flex-1 flex-col lg:flex">
        <div className="flex min-h-0 flex-1">
          <BrowseSidebar
            activePoemId={active.poemId}
            displayedPoems={displayedPoems}
            moreRecentPoems={collection.kind === "all" && collection.hasMore}
            onSelectListPoem={selectListPoem}
            onLoadMore={loadMore}
            onShowAllPoems={showAllPoems}
            poetName={
              collection.kind === "all"
                ? "All Poems"
                : collection.poet.displayName
            }
            poetPoemCount={
              collection.kind === "all"
                ? displayedPoems.length
                : collection.poet.poemCount
            }
            showingAllPoems={showingAllPoems}
          />
          <DesktopPoemReader
            active={active}
            onChoosePoem={openPoem}
            onDelete={deleteActivePoem}
            onNavigate={navigate}
            poemOfTheDay={poemOfTheDay}
            poemPanel={poemPanel}
            viewerPoetId={viewerPoetId}
          />
        </div>
        <HomeFooter onNavigate={navigate} viewer={viewer} />
      </div>
    </main>
  );
}
