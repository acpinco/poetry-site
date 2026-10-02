import { useEffect, useRef, useState } from "react";
import BrowseSidebar from "./home/BrowseSidebar";
import HomeHeader from "./home/HomeHeader";
import {
  DesktopPoemReader,
  HomeFooter,
  MobilePoemReader,
} from "./home/PoemReader";
import type {
  DisplayedPoem,
  HomeData,
  OwnedPoem,
  Poet,
  Poem,
  PoemSummary,
  RecentPoem,
  SearchResults,
} from "./home/types";
import { ApiError, getJson, isAbort } from "./api";

type PoetPoems = Pick<HomeData, "poet" | "poems">;
type RecentPoemsPage = { poems: RecentPoem[]; hasMore: boolean };
type Profile = {
  poetId: string;
  penName: string;
  fullName: string;
  bio: string | null;
};

const SEARCH_DELAY_MS = 700;

/** Treats a missing poem as "nothing to show" while letting other failures surface. */
function falseIfMissing(reason: unknown) {
  if (reason instanceof ApiError && reason.status === 404) return false;
  throw reason;
}

export default function Home({
  initialMyPoemId,
  onNavigate,
}: {
  initialMyPoemId?: string;
  onNavigate: (path: string) => void;
}) {
  const [data, setData] = useState<HomeData | null>(null);
  const [active, setActive] = useState<Poem | null>(null);
  const [poemOfTheDay, setPoemOfTheDay] = useState<Poem | null>(null);
  const [viewer, setViewer] = useState(false);
  const [viewerPoetId, setViewerPoetId] = useState<string | null>(null);
  const [viewerIsAdmin, setViewerIsAdmin] = useState(false);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResults | null>(null);
  const [directory, setDirectory] = useState<Poet[] | null>(null);
  const [recentPoems, setRecentPoems] = useState<RecentPoem[]>([]);
  const [recentOffset, setRecentOffset] = useState(0);
  const [moreRecentPoems, setMoreRecentPoems] = useState(false);
  const [showingAllPoems, setShowingAllPoems] = useState(false);
  // error: nothing could be shown at all. notice: an action failed but the page still works.
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const poemPanel = useRef<HTMLElement | null>(null);
  const mobilePoemPanel = useRef<HTMLElement | null>(null);
  // Each navigation takes a number; responses for anything but the latest are dropped,
  // so a slow earlier click can never replace the poem the visitor picked last.
  const latestNavigation = useRef(0);

  useEffect(() => {
    void load(initialMyPoemId);
    void loadDirectory();
  }, []);

  useEffect(() => {
    poemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
    mobilePoemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [active?.poemId]);

  // Search waits for a pause in typing and cancels the previous request, so results
  // always match the current query and typing does not exhaust the search rate limit.
  useEffect(() => {
    const term = query.trim();
    if (term.length < 2) {
      setResults(null);
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const value = await getJson<SearchResults>(
          `/api/discovery/search?q=${encodeURIComponent(term)}`,
          { signal: controller.signal },
        );
        if (!controller.signal.aborted) setResults(value);
      } catch (reason) {
        if (!isAbort(reason)) setResults(null);
      }
    }, SEARCH_DELAY_MS);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query]);

  function beginNavigation() {
    setNotice("");
    return ++latestNavigation.current;
  }

  function isLatest(navigation: number) {
    return navigation === latestNavigation.current;
  }

  function report(reason: unknown) {
    const message =
      reason instanceof Error ? reason.message : "Unable to load poems.";
    if (data) setNotice(message);
    else setError(message);
  }

  /** Wraps a user action so a failure shows a message instead of being lost. */
  function guarded<Args extends unknown[]>(
    action: (...args: Args) => Promise<unknown>,
  ) {
    return async (...args: Args) => {
      try {
        await action(...args);
      } catch (reason) {
        report(reason);
      }
    };
  }

  async function load(preferredMyPoemId?: string, forcePublicBrowse = false) {
    try {
      const [dailyPoem, me] = await Promise.all([
        fetch("/api/discovery/poem-of-the-day"),
        fetch("/api/auth/me"),
      ]);
      const daily = dailyPoem.ok ? ((await dailyPoem.json()) as Poem) : null;
      setPoemOfTheDay(daily);
      if (me.ok) {
        const session = (await me.json()) as {
          poetId: string | null;
          admin: boolean;
        };
        setViewer(true);
        setViewerPoetId(session.poetId);
        setViewerIsAdmin(session.admin);
        if (
          preferredMyPoemId &&
          (await choosePoem(preferredMyPoemId).catch(falseIfMissing))
        )
          return;
        // A signed-in poet should always return to their own collection.
        if (session.poetId && !forcePublicBrowse) {
          await myPoems(preferredMyPoemId);
          return;
        }
      }

      if (
        preferredMyPoemId &&
        (await choosePoem(preferredMyPoemId).catch(falseIfMissing))
      )
        return;

      if (await showAllPoems()) return;

      // This fallback is only reached when no Poem of the Day can be assigned,
      // such as before the first poem has been published.
      const navigation = beginNavigation();
      const home = await fetch("/api/discovery/home");
      if (!home.ok) throw new Error("No poems are available yet.");
      const value = (await home.json()) as HomeData;
      if (!isLatest(navigation)) return;
      setData(value);
      setActive(value.selectedPoem);
      if (!me.ok) {
        setViewer(false);
        setViewerPoetId(null);
      }
    } catch (reason) {
      report(reason);
    }
  }

  async function loadDirectory() {
    try {
      setDirectory(await getJson<Poet[]>("/api/discovery/poets"));
    } catch {
      // The directory only feeds the search dropdown; browsing works without it.
    }
  }

  /** Shows a poem alongside its poet's other poems. Resolves false if superseded. */
  async function choosePoem(id: string) {
    const navigation = beginNavigation();
    const poem = await getJson<Poem>(`/api/discovery/poems/${id}`);
    const poetData = await getJson<PoetPoems>(
      `/api/discovery/poets/${poem.poetId}/poems`,
    );
    if (!isLatest(navigation)) return false;
    setData({ ...poetData, selectedPoem: poem });
    setActive(poem);
    setShowingAllPoems(false);
    closeSearch();
    return true;
  }

  async function choosePoet(id: string) {
    const navigation = beginNavigation();
    const value = await getJson<PoetPoems>(`/api/discovery/poets/${id}/poems`);
    if (!value.poems[0]) throw new Error("This poet has no poems yet.");
    const poem = await getJson<Poem>(
      `/api/discovery/poems/${value.poems[0].poemId}`,
    );
    if (!isLatest(navigation)) return;
    setData({ poet: value.poet, poems: value.poems, selectedPoem: poem });
    setActive(poem);
    setShowingAllPoems(false);
    closeSearch();
  }

  function closeSearch() {
    setResults(null);
    setQuery("");
  }

  async function myPoems(preferredPoemId?: string) {
    const navigation = beginNavigation();
    const [poems, profile] = await Promise.all([
      getJson<OwnedPoem[]>("/api/poems"),
      getJson<Profile>("/api/poets/me"),
    ]);
    if (!isLatest(navigation)) return;
    if (!poems.length) {
      await load(undefined, true);
      return;
    }
    const summaries = poems.map((poem) => ({
      poemId: poem.poemId,
      title: poem.title,
      excerpt: poem.poem.replace(/\s+/g, " ").slice(0, 160),
      createdAt: poem.createdAt,
    }));
    const selectedPoemId = summaries.some(
      (poem) => poem.poemId === preferredPoemId,
    )
      ? preferredPoemId!
      : summaries[0].poemId;
    const detail = await getJson<Poem>(
      `/api/discovery/poems/${selectedPoemId}`,
    );
    if (!isLatest(navigation)) return;
    const poet = {
      poetId: profile.poetId,
      displayName: profile.penName || profile.fullName,
      bio: profile.bio,
      poemCount: poems.length,
    };
    setData({ poet, poems: summaries, selectedPoem: detail });
    setActive(detail);
    setShowingAllPoems(false);
  }

  /** Lists the newest poems site-wide. Resolves false when there is nothing to show. */
  async function showAllPoems(loadMore = false) {
    const navigation = beginNavigation();
    const offset = loadMore ? recentOffset : 0;
    const value = await getJson<RecentPoemsPage>(
      `/api/discovery/poems/recent?limit=60&offset=${offset}`,
    );
    const poems = loadMore ? [...recentPoems, ...value.poems] : value.poems;
    if (!poems.length) return false;
    const detail = loadMore
      ? null
      : await getJson<Poem>(`/api/discovery/poems/${poems[0].poemId}`);
    if (!isLatest(navigation)) return false;
    setRecentPoems(poems);
    setRecentOffset(poems.length);
    setMoreRecentPoems(value.hasMore);
    setShowingAllPoems(true);
    if (detail) {
      setData({
        poet: {
          poetId: "all-poems",
          displayName: "All Poems",
          bio: null,
          poemCount: poems.length,
        },
        poems: poems.map((poem) => ({
          poemId: poem.poemId,
          title: poem.title,
          excerpt: poem.excerpt,
          createdAt: poem.createdAt,
        })),
        selectedPoem: detail,
      });
      setActive(detail);
    }
    return true;
  }

  async function chooseRecentPoem(id: string) {
    const navigation = beginNavigation();
    const poem = await getJson<Poem>(`/api/discovery/poems/${id}`);
    if (isLatest(navigation)) setActive(poem);
  }

  async function chooseAdjacentPoem(direction: -1 | 1) {
    if (!active) return;
    const activeIndex = displayedPoems.findIndex(
      (poem) => poem.poemId === active.poemId,
    );
    const adjacentPoem = displayedPoems[activeIndex + direction];
    if (!adjacentPoem) return;

    if (showingAllPoems) {
      await chooseRecentPoem(adjacentPoem.poemId);
      return;
    }
    await choosePoem(adjacentPoem.poemId);
  }

  async function deleteActivePoem() {
    if (!window.confirm(`Delete “${active?.title}”? This cannot be undone.`))
      return;
    const response = await fetch(`/api/poems/${active?.poemId}`, {
      method: "DELETE",
    });
    if (!response.ok)
      throw new Error("Your poem could not be deleted. Please try again.");
    await myPoems();
  }

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    onNavigate("/sign-in");
  }

  const onChoosePoem = guarded(choosePoem);
  const onChoosePoet = guarded(choosePoet);
  const onChooseRecentPoem = guarded(chooseRecentPoem);
  const onMyPoems = guarded(() => myPoems());
  const onShowAllPoems = guarded(() => showAllPoems());
  const onLoadMore = guarded(() => showAllPoems(true));
  const onDelete = guarded(deleteActivePoem);
  const onNextPoem = guarded(() => chooseAdjacentPoem(1));
  const onPreviousPoem = guarded(() => chooseAdjacentPoem(-1));

  if (error)
    return (
      <main className="grid min-h-screen place-items-center bg-[#080a0f] px-4 text-center text-[#e4ddd0]">
        <div>
          <p role="alert">{error}</p>
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
  if (!data || !active)
    return (
      <main className="grid min-h-screen place-items-center bg-[#080a0f] text-[#8b8992]">
        Gathering poems…
      </main>
    );
  const displayedPoems: DisplayedPoem[] = showingAllPoems
    ? recentPoems
    : data.poems.map((poem) => ({
        ...poem,
        poetId: data.poet.poetId,
        poetDisplayName: data.poet.displayName,
      }));
  const activePoemIndex = displayedPoems.findIndex(
    (poem) => poem.poemId === active.poemId,
  );
  const hasPreviousPoem = activePoemIndex > 0;
  const hasNextPoem = activePoemIndex < displayedPoems.length - 1;
  return (
    <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
      {notice && (
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
      )}
      <HomeHeader
        activePoemId={active.poemId}
        activePoetId={data.poet.poetId}
        directory={directory}
        displayedPoems={displayedPoems}
        moreRecentPoems={moreRecentPoems}
        onChoosePoem={onChoosePoem}
        onChooseRecentPoem={onChooseRecentPoem}
        onChoosePoet={onChoosePoet}
        onLoadMore={onLoadMore}
        onMyPoems={onMyPoems}
        onNavigate={onNavigate}
        onSearch={setQuery}
        onShowAllPoems={onShowAllPoems}
        onSignOut={signOut}
        query={query}
        results={results}
        showingAllPoems={showingAllPoems}
        viewer={viewer}
        viewerIsAdmin={viewerIsAdmin}
      />

      <div className="flex min-h-0 flex-1 flex-col lg:hidden">
        <MobilePoemReader
          active={active}
          mobilePoemPanel={mobilePoemPanel}
          onChoosePoem={onChoosePoem}
          onDelete={onDelete}
          onNextPoem={onNextPoem}
          onNavigate={onNavigate}
          onPreviousPoem={onPreviousPoem}
          hasNextPoem={hasNextPoem}
          hasPreviousPoem={hasPreviousPoem}
          poemOfTheDay={poemOfTheDay}
          viewerPoetId={viewerPoetId}
        />
        <HomeFooter compact onNavigate={onNavigate} viewer={viewer} />
      </div>

      <div className="hidden min-h-0 flex-1 flex-col lg:flex">
        <div className="flex min-h-0 flex-1">
          <BrowseSidebar
            activePoemId={active.poemId}
            displayedPoems={displayedPoems}
            moreRecentPoems={moreRecentPoems}
            onChoosePoem={onChoosePoem}
            onChooseRecentPoem={onChooseRecentPoem}
            onLoadMore={onLoadMore}
            onShowAllPoems={onShowAllPoems}
            poetName={data.poet.displayName}
            poetPoemCount={data.poet.poemCount}
            showingAllPoems={showingAllPoems}
          />
          <DesktopPoemReader
            active={active}
            onChoosePoem={onChoosePoem}
            onDelete={onDelete}
            onNavigate={onNavigate}
            poemOfTheDay={poemOfTheDay}
            poemPanel={poemPanel}
            viewerPoetId={viewerPoetId}
          />
        </div>
        <HomeFooter onNavigate={onNavigate} viewer={viewer} />
      </div>
    </main>
  );
}
