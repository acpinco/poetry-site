import { useEffect, useRef, useState, type MouseEvent, type PointerEvent, type WheelEvent } from "react";
import ravenLogo from "./imports/Raven_Logo.png";

type Poet = { poetId: string; displayName: string; bio: string | null; poemCount: number };
type PoemSummary = { poemId: string; title: string; excerpt: string; createdAt: string };
type Poem = { poemId: string; poetId: string; title: string; poem: string; poetDisplayName: string; poetBio: string | null; createdAt: string };
type RecentPoem = { poemId: string; poetId: string; title: string; poetDisplayName: string; excerpt: string; createdAt: string };
type HomeData = { poet: Poet; poems: PoemSummary[]; selectedPoem: Poem };
type SearchResults = { poets: Poet[]; poems: Array<PoemSummary & { poetId: string; poetDisplayName: string }> };

export default function Home({ initialMyPoemId, onNavigate }: { initialMyPoemId?: string; onNavigate: (path: string) => void }) {
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
  const [mobilePanel, setMobilePanel] = useState<"browse" | "search" | "menu" | null>(null);
  const [mobileBrowseView, setMobileBrowseView] = useState<"poets" | "poems">("poets");
  const [poetFocusRequest, setPoetFocusRequest] = useState(0);
  const [searchFocused, setSearchFocused] = useState(false);
  const [error, setError] = useState("");
  const [showingRandomSelection, setShowingRandomSelection] = useState(false);
  const poemPanel = useRef<HTMLElement | null>(null);
  const mobilePoemPanel = useRef<HTMLElement | null>(null);
  const poetChips = useRef<Record<string, HTMLButtonElement | null>>({});
  const focusPoetAfterSelection = useRef(false);
  const horizontalDrag = useRef<{ element: HTMLDivElement; pointerId: number; startX: number; startScrollLeft: number; moved: boolean; captured: boolean } | null>(null);
  const suppressClickAfterDrag = useRef(false);

  useEffect(() => { void load(initialMyPoemId); }, []);
  useEffect(() => { void loadDirectory(); }, []);
  useEffect(() => {
    if (showingAllPoems || !data || !focusPoetAfterSelection.current) return;
    poetChips.current[data.poet.poetId]?.scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
    focusPoetAfterSelection.current = false;
  }, [data?.poet.poetId, poetFocusRequest, showingAllPoems]);

  async function load(preferredMyPoemId?: string, forcePublicBrowse = false) {
    try {
      const [dailyPoem, me] = await Promise.all([
        fetch("/api/discovery/poem-of-the-day"),
        fetch("/api/auth/me", { credentials: "include" })
      ]);
      const daily = dailyPoem.ok ? (await dailyPoem.json()) as Poem : null;
      setPoemOfTheDay(daily);
      if (me.ok) {
        const session = await me.json() as { poetId: string | null };
        setViewer(true);
        setViewerPoetId(session.poetId);
        if (preferredMyPoemId && await choosePoem(preferredMyPoemId)) return;
        // A signed-in poet should always return to their own collection.
        if (session.poetId && !forcePublicBrowse) {
          await myPoems(preferredMyPoemId);
          return;
        }
      }

      if (preferredMyPoemId && await choosePoem(preferredMyPoemId)) return;

      if (await showAllPoems()) return;

      // This fallback is only reached when no Poem of the Day can be assigned,
      // such as before the first poem has been published.
      const home = await fetch("/api/discovery/home");
      if (!home.ok) throw new Error("No poems are available yet.");
      const value = await home.json();
      setData(value);
      setActive(value.selectedPoem);
      setShowingRandomSelection(true);
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

  async function choosePoem(id: string, focusPoet = false) {
    const response = await fetch(`/api/discovery/poems/${id}`);
    if (response.ok) {
      const poem = await response.json() as Poem;
      const poetResponse = await fetch(`/api/discovery/poets/${poem.poetId}/poems`);
      if (!poetResponse.ok) return false;
      const poetData = await poetResponse.json() as Pick<HomeData, "poet" | "poems">;
      requestPoetFocus(focusPoet);
      setData({ ...poetData, selectedPoem: poem });
      setActive(poem);
      setShowingAllPoems(false);
      setShowingRandomSelection(false);
      closeSearch();
      return true;
    }
    closeSearch();
    return false;
  }

  async function search(value: string) {
    setQuery(value);
    if (value.trim().length < 2) { setResults(null); return; }
    const response = await fetch(`/api/discovery/search?q=${encodeURIComponent(value)}`);
    if (response.ok) setResults(await response.json());
  }

  async function choosePoet(id: string, focusPoet = false) {
    const response = await fetch(`/api/discovery/poets/${id}/poems`);
    if (!response.ok) return;
    const value = await response.json();
    const poem = value.poems[0] ? await fetch(`/api/discovery/poems/${value.poems[0].poemId}`).then(response => response.json()) : null;
    requestPoetFocus(focusPoet);
    setData({ poet: value.poet, poems: value.poems, selectedPoem: poem });
    setActive(poem);
    setShowingAllPoems(false);
    setShowingRandomSelection(false);
    closeSearch();
  }

  function closeSearch() { setResults(null); setQuery(""); setSearchFocused(false); }

  async function myPoems(preferredPoemId?: string, focusPoet = false) {
    const [poemsResponse, profileResponse] = await Promise.all([fetch("/api/poems", { credentials: "include" }), fetch("/api/poets/me", { credentials: "include" })]);
    if (!poemsResponse.ok || !profileResponse.ok) return;
    const poems = await poemsResponse.json();
    const profile = await profileResponse.json();
    if (!poems.length) { await load(undefined, true); return; }
    const summaries = poems.map((poem: any) => ({ poemId: poem.poemId, title: poem.title, excerpt: poem.poem.replace(/\s+/g, " ").slice(0, 160), createdAt: poem.createdAt }));
    const selectedPoemId = summaries.some(poem => poem.poemId === preferredPoemId) ? preferredPoemId! : summaries[0].poemId;
    const detail = await fetch(`/api/discovery/poems/${selectedPoemId}`).then(response => response.json());
    const poet = { poetId: profile.poetId, displayName: profile.penName || profile.fullName, bio: profile.bio, poemCount: poems.length };
    requestPoetFocus(focusPoet);
    setData({ poet, poems: summaries, selectedPoem: detail });
    setActive(detail);
    setShowingAllPoems(false);
    setShowingRandomSelection(false);
  }

  async function showAllPoems(loadMore = false) {
    const offset = loadMore ? recentOffset : 0;
    const response = await fetch(`/api/discovery/poems/recent?limit=60&offset=${offset}`);
    if (!response.ok) return false;
    const value = await response.json() as { poems: RecentPoem[]; hasMore: boolean };
    const poems = loadMore ? [...recentPoems, ...value.poems] : value.poems;
    setRecentPoems(poems);
    setRecentOffset(poems.length);
    setMoreRecentPoems(value.hasMore);
    setShowingAllPoems(true);
    setShowingRandomSelection(false);
    if (!loadMore && poems[0]) {
      const detailResponse = await fetch(`/api/discovery/poems/${poems[0].poemId}`);
      if (!detailResponse.ok) return false;
      const detail = await detailResponse.json() as Poem;
      setData({ poet: { poetId: "all-poems", displayName: "All Poems", bio: null, poemCount: poems.length }, poems: poems.map(poem => ({ poemId: poem.poemId, title: poem.title, excerpt: poem.excerpt, createdAt: poem.createdAt })), selectedPoem: detail });
      setActive(detail);
    }
    return poems.length > 0;
  }

  async function chooseRecentPoem(id: string) {
    const response = await fetch(`/api/discovery/poems/${id}`);
    if (!response.ok) return;
    const poem = await response.json() as Poem;
    setActive(poem);
    setShowingRandomSelection(false);
  }

  function scrollHorizontally(event: WheelEvent<HTMLDivElement>) {
    if (Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return;
    event.preventDefault();
    event.currentTarget.scrollLeft += event.deltaY;
  }

  function requestPoetFocus(focusPoet: boolean) {
    focusPoetAfterSelection.current = focusPoet;
    if (focusPoet) setPoetFocusRequest(request => request + 1);
  }

  function beginHorizontalDrag(event: PointerEvent<HTMLDivElement>) {
    if (event.button !== 0) return;
    const element = event.currentTarget;
    horizontalDrag.current = { element, pointerId: event.pointerId, startX: event.clientX, startScrollLeft: element.scrollLeft, moved: false, captured: false };
  }

  function dragHorizontally(event: PointerEvent<HTMLDivElement>) {
    const drag = horizontalDrag.current;
    if (!drag || drag.element !== event.currentTarget) return;
    const distance = event.clientX - drag.startX;
    if (Math.abs(distance) > 3 && !drag.moved) {
      drag.moved = true;
      drag.captured = true;
      drag.element.setPointerCapture(drag.pointerId);
    }
    drag.element.scrollLeft = drag.startScrollLeft - distance;
  }

  function endHorizontalDrag(event: PointerEvent<HTMLDivElement>) {
    const drag = horizontalDrag.current;
    if (!drag || drag.element !== event.currentTarget) return;
    if (drag.captured && drag.element.hasPointerCapture(event.pointerId)) drag.element.releasePointerCapture(event.pointerId);
    horizontalDrag.current = null;
    if (drag.moved) {
      suppressClickAfterDrag.current = true;
    }
  }

  function suppressDraggedClick(event: MouseEvent<HTMLDivElement>) {
    if (!suppressClickAfterDrag.current) return;
    event.preventDefault();
    event.stopPropagation();
    suppressClickAfterDrag.current = false;
  }

  async function deleteActivePoem() {
    if (!window.confirm(`Delete “${active?.title}”? This cannot be undone.`)) return;
    const response = await fetch(`/api/poems/${active?.poemId}`, { method: "DELETE", credentials: "include" });
    if (!response.ok) { setError("Your poem could not be deleted. Please try again."); return; }
    await myPoems();
  }

  async function signOut() { await fetch("/api/auth/logout", { method: "POST", credentials: "include" }); onNavigate("/sign-in"); }

  if (error) return <main className="grid min-h-screen place-items-center bg-[#080a0f] text-[#e4ddd0]">{error}</main>;
  if (!data || !active) return <main className="grid min-h-screen place-items-center bg-[#080a0f] text-[#8b8992]">Gathering poems…</main>;
  const showingDirectory = searchFocused && query.trim().length < 2;
  const showingResults = searchFocused && query.trim().length >= 2 && results;
  const displayedPoems: Array<PoemSummary & Partial<Pick<RecentPoem, "poetId" | "poetDisplayName">>> = showingAllPoems
    ? recentPoems
    : data.poems.map(poem => ({ ...poem, poetId: data.poet.poetId, poetDisplayName: data.poet.displayName }));
  const headerButton = "inline-flex items-center justify-center border border-[#3d3660] bg-[#161a27] px-3 py-2 text-xs uppercase tracking-wider text-[#c8c0b0] transition hover:border-[#c9a84c] hover:text-[#e8c97a]";
  const newPoemButton = "inline-flex items-center justify-center border border-[#c9a84c] bg-[#c9a84c] px-3 py-2 text-xs font-semibold uppercase tracking-wider text-[#080a0f] transition hover:bg-[#e8c97a]";

  return <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
    <header className="z-30 shrink-0 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur lg:hidden"><div className="flex items-center gap-2"><img src={ravenLogo} alt="Think or Drink Poetry" className="h-9 shrink-0 object-contain" /><button type="button" onClick={() => setMobilePanel(mobilePanel === "browse" ? null : "browse")} className={headerButton}>Browse</button>{viewer && <button type="button" onClick={() => void myPoems(undefined, true)} className={headerButton}>My Poems</button>}<button type="button" onClick={() => setMobilePanel(mobilePanel === "menu" ? null : "menu")} className="ml-auto border border-[#3d3660] px-3 py-2 text-xs uppercase tracking-wider text-[#c8c0b0]" aria-expanded={mobilePanel === "menu"}>Menu</button></div><button type="button" onClick={() => setMobilePanel(mobilePanel === "search" ? null : "search")} className="mt-3 w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-left text-xs text-[#8b8992]">Search poets or poems…</button>{mobilePanel === "search" && <div className="mt-2"><input autoFocus value={query} onChange={event => void search(event.target.value)} placeholder="Search poets or poems…" className="w-full border border-[#c9a84c] bg-[#080a0f] px-3 py-3 text-sm outline-none" />{query.trim().length < 2 ? <p className="px-1 py-3 text-xs text-[#8b8992]">Enter at least two characters.</p> : <div className="max-h-60 overflow-y-auto border border-[#2a2840] bg-[#161a27]">{results && <>{results.poets.map(poet => <button key={poet.poetId} type="button" onClick={() => { void choosePoet(poet.poetId, true); setMobilePanel(null); }} className="block w-full border-b border-[#2a2840] px-3 py-3 text-left text-sm">{poet.displayName} <span className="text-xs text-[#8b8992]">· {poet.poemCount} poems</span></button>)}{results.poems.map(poem => <button key={poem.poemId} type="button" onClick={() => { void choosePoem(poem.poemId, true); setMobilePanel(null); }} className="block w-full border-b border-[#2a2840] px-3 py-3 text-left text-sm">{poem.title} <span className="text-xs text-[#8b8992]">by {poem.poetDisplayName}</span></button>)}</>}</div>}</div>}{mobilePanel === "browse" && <div className="mt-2 border border-[#2a2840] bg-[#10121e]"><div className="grid grid-cols-2 border-b border-[#2a2840]"><button type="button" onClick={() => setMobileBrowseView("poets")} className={`px-3 py-3 text-xs uppercase tracking-wider ${mobileBrowseView === "poets" ? "bg-[#161a27] text-[#e8c97a]" : "text-[#8b8992]"}`}>Poets</button><button type="button" onClick={() => setMobileBrowseView("poems")} className={`px-3 py-3 text-xs uppercase tracking-wider ${mobileBrowseView === "poems" ? "bg-[#161a27] text-[#e8c97a]" : "text-[#8b8992]"}`}>Poems</button></div><div className="max-h-64 overflow-y-auto">{mobileBrowseView === "poets" ? <><button type="button" onClick={() => { void showAllPoems(); setMobileBrowseView("poems"); }} className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left text-sm ${showingAllPoems ? "bg-[#161a27] text-[#e8c97a]" : ""}`}>All Poets <span className="text-xs text-[#8b8992]">— newest poems</span></button>{directory?.map(poet => <button type="button" key={poet.poetId} onClick={() => { void choosePoet(poet.poetId); setMobilePanel(null); }} className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left text-sm ${!showingAllPoems && data.poet.poetId === poet.poetId ? "bg-[#161a27] text-[#e8c97a]" : ""}`}>{poet.displayName} <span className="text-xs text-[#8b8992]">({poet.poemCount})</span></button>)}</> : <>{displayedPoems.map(poem => <button type="button" key={poem.poemId} onClick={() => { void (showingAllPoems ? chooseRecentPoem(poem.poemId) : choosePoem(poem.poemId)); setMobilePanel(null); }} className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left ${active.poemId === poem.poemId ? "bg-[#161a27]" : ""}`}><span className="font-serif text-base">{poem.title}</span><span className="ml-2 text-[10px] uppercase tracking-wider text-[#8b8992]">{formatDate(poem.createdAt)}</span>{showingAllPoems && <span className="ml-2 text-xs text-[#c9a84c]">{poem.poetDisplayName}</span>}</button>)}{showingAllPoems && moreRecentPoems && <button type="button" onClick={() => void showAllPoems(true)} className="block w-full px-4 py-3 text-left text-xs uppercase tracking-widest text-[#c9a84c]">Load more poems</button>}</>}</div></div>}{mobilePanel === "menu" && <div className="mt-2 grid grid-cols-2 gap-2 border border-[#2a2840] bg-[#10121e] p-2">{viewer ? <><button type="button" onClick={() => onNavigate("/my-poems/new")} className={newPoemButton}>New Poem</button><button type="button" onClick={() => onNavigate("/account/setup")} className={headerButton}>Profile</button><button type="button" onClick={() => onNavigate("/contact")} className={headerButton}>Contact</button><button type="button" onClick={() => void signOut()} className={headerButton}>Sign Out</button></> : <button type="button" onClick={() => onNavigate("/sign-in")} className={`${headerButton} col-span-2`}>Sign In</button>}</div>}</header>
    <header className="sticky top-0 z-20 hidden flex-wrap items-center justify-between gap-4 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur sm:px-7 lg:flex">
      <div className="flex min-w-0 items-center gap-3"><img src={ravenLogo} alt="Think or Drink Poetry" className="h-10 shrink-0 object-contain" /><div className="relative w-64 max-w-[55vw]">
        <input value={query} onChange={event => void search(event.target.value)} onFocus={() => { setSearchFocused(true); void loadDirectory(); }} onBlur={() => window.setTimeout(() => setSearchFocused(false), 150)} placeholder="Search poets or poems…" className="w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-xs outline-none focus:border-[#c9a84c]" />
        {(showingDirectory || showingResults) && <div className="absolute z-30 mt-1 max-h-80 w-full overflow-auto border border-[#2a2840] bg-[#161a27]">
          {showingDirectory && <><p className="border-b border-[#2a2840] px-3 py-2 text-[10px] uppercase tracking-widest text-[#8b8992]">All poets</p>{directory?.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId, true)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}</>}
          {showingResults && <>{results.poets.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId, true)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}{results.poems.map(poem => <button key={poem.poemId} onClick={() => void choosePoem(poem.poemId, true)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poem.title} <span className="text-[#8b8992]">by {poem.poetDisplayName}</span></button>)}</>}
        </div>}
      </div></div>
      {viewer ? <div className="ml-auto flex flex-wrap justify-end gap-2"><button type="button" onClick={() => void myPoems(undefined, true)} className={headerButton}>My Poems</button><button type="button" onClick={() => onNavigate("/my-poems/new")} className={newPoemButton}>+ New Poem</button><button type="button" onClick={() => onNavigate("/account/setup")} className={headerButton}>Profile</button><button type="button" onClick={() => void signOut()} className={headerButton}>Sign Out</button></div> : <button type="button" onClick={() => onNavigate("/sign-in")} className={headerButton}>Sign In</button>}
    </header>
    <div className="flex min-h-0 flex-1 flex-col lg:hidden">
      <article ref={mobilePoemPanel} className="poetry-scroll min-h-0 flex-1 overflow-y-auto px-5 py-8"><div className="mx-auto max-w-xl"><p className="text-center text-xs uppercase tracking-[.25em]"><a href={`/poets/${active.poetId}/${slugify(active.poetDisplayName)}/bio?poem=${encodeURIComponent(active.poemId)}`} className="text-[#c9a84c] hover:text-[#e8c97a]">{active.poetDisplayName}</a></p>{active.poetBio?.trim() && <p className="mx-auto mt-2 max-w-xl text-center text-xs italic leading-relaxed text-[#8b8992]"><span className="not-italic text-[#c8c0b0]">About {active.poetDisplayName}: </span>{bioSnippet(active.poetBio)} <a href={`/poets/${active.poetId}/${slugify(active.poetDisplayName)}/bio?poem=${encodeURIComponent(active.poemId)}`} className="not-italic text-[#c9a84c] hover:text-[#e8c97a]">Read full bio</a></p>}<div className="mt-3 flex items-center justify-center gap-3"><h1 className="text-center font-serif text-3xl leading-tight">{active.title}</h1>{viewerPoetId === active.poetId && <><button type="button" onClick={() => onNavigate(`/my-poems/${active.poemId}/edit`)} title="Edit this poem" aria-label={`Edit ${active.title}`} className="text-xl text-[#c9a84c] hover:text-[#e8c97a]">✎</button><button type="button" onClick={() => void deleteActivePoem()} title="Delete this poem" aria-label={`Delete ${active.title}`} className="text-lg text-red-300 hover:text-red-200">🗑</button></>}</div><p className="mt-3 text-center text-xs"><a href={`/poems/${active.poemId}/${slugify(active.title)}`} className="text-[#c9a84c]">Open shareable poem page</a></p><div className="mx-auto my-6 h-px w-40 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-lg italic leading-loose text-[#c8c0b0]">{active.poem}</div>{poemOfTheDay && poemOfTheDay.poemId !== active.poemId && <details className="mt-12 border border-[#2a2840] bg-[#0d0f1a] p-4"><summary className="cursor-pointer text-xs uppercase tracking-[.2em] text-[#c9a84c]">Poem of the Day</summary><button type="button" onClick={() => void choosePoem(poemOfTheDay.poemId)} className="mt-4 block w-full text-left"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">{poemOfTheDay.poetDisplayName}</p><h2 className="mt-2 font-serif text-2xl">{poemOfTheDay.title}</h2><p className="mt-3 font-serif text-sm italic leading-relaxed text-[#c8c0b0]">{poemOfTheDay.poem.slice(0, 300)}{poemOfTheDay.poem.length > 300 ? "…" : ""}</p><p className="mt-4 text-xs text-[#c9a84c]">Read in the reader →</p></button></details>}</div></article>
      <footer className="flex shrink-0 items-center justify-between gap-4 border-t border-[#1e2235] bg-[#0e1018] px-4 py-3 text-xs"><a href="/" className="text-[#c9a84c] hover:text-[#e8c97a]">About</a>{viewer && <button type="button" onClick={() => onNavigate("/contact")} className="border border-[#3d3660] px-3 py-2 uppercase tracking-wider text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]">Contact Me</button>}</footer>
    </div>
    <div className="hidden min-h-0 flex-1 flex-col lg:flex">
    <section className="shrink-0 border-b border-[#1e2235] bg-[#0d0f1a]"><div className="flex items-center justify-between px-4 pt-2 sm:px-7"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">Browse poets</p><p className="text-xs text-[#8b8992]">Drag to explore</p></div><div className="flex gap-2 px-4 py-2 sm:px-7"><button type="button" onClick={() => void showAllPoems()} className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${showingAllPoems ? "border-[#c9a84c] bg-[#161a27] text-[#e8c97a]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}>All Poets <span className="text-[#8b8992]">(newest)</span></button><div onWheel={scrollHorizontally} onPointerDown={beginHorizontalDrag} onPointerMove={dragHorizontally} onPointerUp={endHorizontalDrag} onPointerCancel={endHorizontalDrag} onClickCapture={suppressDraggedClick} className="poetry-horizontal-scroll flex min-w-0 flex-1 gap-2 overflow-x-auto">{directory?.map(poet => <button type="button" key={poet.poetId} ref={element => { poetChips.current[poet.poetId] = element; }} onClick={() => void choosePoet(poet.poetId)} className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${!showingAllPoems && data.poet.poetId === poet.poetId ? "border-[#c9a84c] bg-[#161a27] text-[#e8c97a]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}>{poet.displayName} <span className="text-[#8b8992]">({poet.poemCount})</span></button>)}</div></div></section>
    <section className="shrink-0 border-b border-[#1e2235] bg-[#10121e]"><div className="flex items-center justify-between px-4 pt-2 sm:px-7"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">{showingAllPoems ? "All poems — newest added first" : `${data.poet.displayName} — ${data.poet.poemCount} poems`}</p><p className="text-xs text-[#8b8992]">Drag to explore</p></div><div onWheel={scrollHorizontally} onPointerDown={beginHorizontalDrag} onPointerMove={dragHorizontally} onPointerUp={endHorizontalDrag} onPointerCancel={endHorizontalDrag} onClickCapture={suppressDraggedClick} className="poetry-horizontal-scroll flex gap-2 overflow-x-auto px-4 py-2 sm:px-7">{displayedPoems.map(poem => <button type="button" key={poem.poemId} onClick={() => void (showingAllPoems ? chooseRecentPoem(poem.poemId) : choosePoem(poem.poemId))} className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${active.poemId === poem.poemId ? "border-[#c9a84c] bg-[#161a27]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}><span className="font-serif text-sm">{poem.title}</span><span className="ml-2 text-[10px] uppercase tracking-wider text-[#8b8992]">{formatDate(poem.createdAt)}</span></button>)}{showingAllPoems && moreRecentPoems && <button type="button" onClick={() => void showAllPoems(true)} className="shrink-0 whitespace-nowrap border border-dashed border-[#3d3660] px-3 py-2 text-xs uppercase tracking-widest text-[#c9a84c] hover:border-[#c9a84c]">Load more poems</button>}</div></section>
    <div className="flex min-h-0 flex-1">
      <article ref={poemPanel} className="poetry-scroll h-full flex-1 overflow-y-auto px-6 py-12"><div className="mx-auto max-w-2xl"><p className="text-center text-xs uppercase tracking-[.25em]"><a href={`/poets/${active.poetId}/${slugify(active.poetDisplayName)}/bio?poem=${encodeURIComponent(active.poemId)}`} className="text-[#c9a84c] hover:text-[#e8c97a]">{active.poetDisplayName}</a></p>{active.poetBio?.trim() && <p className="mx-auto mt-2 max-w-xl text-center text-xs italic leading-relaxed text-[#8b8992]"><span className="not-italic text-[#c8c0b0]">About {active.poetDisplayName}: </span>{bioSnippet(active.poetBio)} <a href={`/poets/${active.poetId}/${slugify(active.poetDisplayName)}/bio?poem=${encodeURIComponent(active.poemId)}`} className="not-italic text-[#c9a84c] hover:text-[#e8c97a]">Read full bio</a></p>}<div className="mt-3 flex items-center justify-center gap-3"><h1 className="font-serif text-4xl">{active.title}</h1>{viewerPoetId === active.poetId && <><button type="button" onClick={() => onNavigate(`/my-poems/${active.poemId}/edit`)} title="Edit this poem" aria-label={`Edit ${active.title}`} className="text-xl text-[#c9a84c] hover:text-[#e8c97a]">✎</button><button type="button" onClick={() => void deleteActivePoem()} title="Delete this poem" aria-label={`Delete ${active.title}`} className="text-lg text-red-300 hover:text-red-200">🗑</button></>}</div><p className="mt-3 text-center text-xs"><a href={`/poems/${active.poemId}/${slugify(active.title)}`} className="text-[#c9a84c]">Open shareable poem page</a></p><div className="mx-auto my-6 h-px w-48 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-lg italic leading-loose text-[#c8c0b0]">{active.poem}</div></div></article>
      {poemOfTheDay && <aside className="poetry-scroll hidden h-full w-[23rem] shrink-0 overflow-y-auto border-l border-[#1e2235] bg-[#0d0f1a] xl:block"><div className="border-b border-[#1e2235] p-4"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">Poem of the Day</p><p className="mt-1 text-xs text-[#8b8992]">A shared reading for today</p></div><button type="button" onClick={() => void choosePoem(poemOfTheDay.poemId)} className="block w-full px-6 py-8 text-left hover:bg-[#161a27]"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">{poemOfTheDay.poetDisplayName}</p><h2 className="mt-3 font-serif text-3xl text-[#e4ddd0]">{poemOfTheDay.title}</h2><div className="my-5 h-px w-32 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-base italic leading-loose text-[#c8c0b0]">{poemOfTheDay.poem}</div><p className="mt-6 text-xs text-[#c9a84c]">Read in the main panel →</p></button></aside>}
    </div>
    <footer className="flex shrink-0 items-center justify-between gap-4 border-t border-[#1e2235] bg-[#0e1018] px-4 py-3 text-xs sm:px-7"><a href="/" className="text-[#c9a84c] hover:text-[#e8c97a]">About Think or Drink Poetry</a>{viewer && <button type="button" onClick={() => onNavigate("/contact")} className="border border-[#3d3660] px-3 py-2 uppercase tracking-wider text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]">Contact Me</button>}</footer>
    </div>
  </main>;
}

function slugify(value: string) {
  const slug = value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  return slug || "poem";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(value));
}

function bioSnippet(value: string) {
  const compact = value.replace(/\s+/g, " ").trim();
  return compact.length <= 180 ? compact : `${compact.slice(0, 177)}…`;
}
