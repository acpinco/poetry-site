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
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResults | null>(null);
  const [directory, setDirectory] = useState<Poet[] | null>(null);
  const [recentPoems, setRecentPoems] = useState<RecentPoem[]>([]);
  const [recentOffset, setRecentOffset] = useState(0);
  const [moreRecentPoems, setMoreRecentPoems] = useState(false);
  const [showingAllPoems, setShowingAllPoems] = useState(false);
  const [error, setError] = useState("");
  const poemPanel = useRef<HTMLElement | null>(null);
  const mobilePoemPanel = useRef<HTMLElement | null>(null);

  useEffect(() => {
    void load(initialMyPoemId);
  }, []);
  useEffect(() => {
    void loadDirectory();
  }, []);
  async function load(preferredMyPoemId?: string, forcePublicBrowse = false) {
    try {
      const [dailyPoem, me] = await Promise.all([
        fetch("/api/discovery/poem-of-the-day"),
        fetch("/api/auth/me", { credentials: "include" }),
      ]);
      const daily = dailyPoem.ok ? ((await dailyPoem.json()) as Poem) : null;
      setPoemOfTheDay(daily);
      if (me.ok) {
        const session = (await me.json()) as { poetId: string | null };
        setViewer(true);
        setViewerPoetId(session.poetId);
        if (preferredMyPoemId && (await choosePoem(preferredMyPoemId))) return;
        // A signed-in poet should always return to their own collection.
        if (session.poetId && !forcePublicBrowse) {
          await myPoems(preferredMyPoemId);
          return;
        }
      }

      if (preferredMyPoemId && (await choosePoem(preferredMyPoemId))) return;

      if (await showAllPoems()) return;

      // This fallback is only reached when no Poem of the Day can be assigned,
      // such as before the first poem has been published.
      const home = await fetch("/api/discovery/home");
      if (!home.ok) throw new Error("No poems are available yet.");
      const value = await home.json();
      setData(value);
      setActive(value.selectedPoem);
      if (!me.ok) {
        setViewer(false);
        setViewerPoetId(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Unable to load poems.");
    }
  }

  useEffect(() => {
    poemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
    mobilePoemPanel.current?.scrollTo({ top: 0, behavior: "smooth" });
  }, [active?.poemId]);

  async function loadDirectory() {
    if (directory) return;
    const response = await fetch("/api/discovery/poets");
    if (response.ok) setDirectory(await response.json());
  }

  async function choosePoem(id: string) {
    const response = await fetch(`/api/discovery/poems/${id}`);
    if (response.ok) {
      const poem = (await response.json()) as Poem;
      const poetResponse = await fetch(
        `/api/discovery/poets/${poem.poetId}/poems`,
      );
      if (!poetResponse.ok) return false;
      const poetData = (await poetResponse.json()) as Pick<
        HomeData,
        "poet" | "poems"
      >;
      setData({ ...poetData, selectedPoem: poem });
      setActive(poem);
      setShowingAllPoems(false);
      closeSearch();
      return true;
    }
    closeSearch();
    return false;
  }

  async function search(value: string) {
    setQuery(value);
    if (value.trim().length < 2) {
      setResults(null);
      return;
    }
    const response = await fetch(
      `/api/discovery/search?q=${encodeURIComponent(value)}`,
    );
    if (response.ok) setResults(await response.json());
  }

  async function choosePoet(id: string) {
    const response = await fetch(`/api/discovery/poets/${id}/poems`);
    if (!response.ok) return;
    const value = await response.json();
    const poem = value.poems[0]
      ? await fetch(`/api/discovery/poems/${value.poems[0].poemId}`).then(
          (response) => response.json(),
        )
      : null;
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
    const [poemsResponse, profileResponse] = await Promise.all([
      fetch("/api/poems", { credentials: "include" }),
      fetch("/api/poets/me", { credentials: "include" }),
    ]);
    if (!poemsResponse.ok || !profileResponse.ok) return;
    const poems = (await poemsResponse.json()) as OwnedPoem[];
    const profile = await profileResponse.json();
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
    const detail = await fetch(`/api/discovery/poems/${selectedPoemId}`).then(
      (response) => response.json(),
    );
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

  async function showAllPoems(loadMore = false) {
    const offset = loadMore ? recentOffset : 0;
    const response = await fetch(
      `/api/discovery/poems/recent?limit=60&offset=${offset}`,
    );
    if (!response.ok) return false;
    const value = (await response.json()) as {
      poems: RecentPoem[];
      hasMore: boolean;
    };
    const poems = loadMore ? [...recentPoems, ...value.poems] : value.poems;
    setRecentPoems(poems);
    setRecentOffset(poems.length);
    setMoreRecentPoems(value.hasMore);
    setShowingAllPoems(true);
    if (!loadMore && poems[0]) {
      const detailResponse = await fetch(
        `/api/discovery/poems/${poems[0].poemId}`,
      );
      if (!detailResponse.ok) return false;
      const detail = (await detailResponse.json()) as Poem;
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
    return poems.length > 0;
  }

  async function chooseRecentPoem(id: string) {
    const response = await fetch(`/api/discovery/poems/${id}`);
    if (!response.ok) return;
    const poem = (await response.json()) as Poem;
    setActive(poem);
  }

  async function deleteActivePoem() {
    if (!window.confirm(`Delete “${active?.title}”? This cannot be undone.`))
      return;
    const response = await fetch(`/api/poems/${active?.poemId}`, {
      method: "DELETE",
      credentials: "include",
    });
    if (!response.ok) {
      setError("Your poem could not be deleted. Please try again.");
      return;
    }
    await myPoems();
  }

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST", credentials: "include" });
    onNavigate("/sign-in");
  }

  if (error)
    return (
      <main className="grid min-h-screen place-items-center bg-[#080a0f] text-[#e4ddd0]">
        {error}
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
  return (
    <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
      <HomeHeader
        activePoemId={active.poemId}
        activePoetId={data.poet.poetId}
        directory={directory}
        displayedPoems={displayedPoems}
        moreRecentPoems={moreRecentPoems}
        onChoosePoem={choosePoem}
        onChooseRecentPoem={chooseRecentPoem}
        onChoosePoet={choosePoet}
        onLoadMore={async () => {
          await showAllPoems(true);
        }}
        onMyPoems={async () => {
          await myPoems();
        }}
        onNavigate={onNavigate}
        onSearch={search}
        onShowAllPoems={async () => {
          await showAllPoems();
        }}
        onSignOut={signOut}
        query={query}
        results={results}
        showingAllPoems={showingAllPoems}
        viewer={viewer}
      />

      <div className="flex min-h-0 flex-1 flex-col lg:hidden">
        <MobilePoemReader
          active={active}
          mobilePoemPanel={mobilePoemPanel}
          onChoosePoem={choosePoem}
          onDelete={deleteActivePoem}
          onNavigate={onNavigate}
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
            onChoosePoem={choosePoem}
            onChooseRecentPoem={chooseRecentPoem}
            onLoadMore={async () => {
              await showAllPoems(true);
            }}
            onShowAllPoems={showAllPoems}
            poetName={data.poet.displayName}
            poetPoemCount={data.poet.poemCount}
            showingAllPoems={showingAllPoems}
          />
          <DesktopPoemReader
            active={active}
            onChoosePoem={choosePoem}
            onDelete={deleteActivePoem}
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
