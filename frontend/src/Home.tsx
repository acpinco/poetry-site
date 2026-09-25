import { useEffect, useRef, useState } from "react";
import ravenLogo from "./imports/Raven_Logo.png";

type Poet = { poetId: string; displayName: string; bio: string | null; poemCount: number };
type PoemSummary = { poemId: string; title: string; excerpt: string; createdAt: string };
type Poem = { poemId: string; poetId: string; title: string; poem: string; poetDisplayName: string; createdAt: string };
type HomeData = { poet: Poet; poems: PoemSummary[]; selectedPoem: Poem };
type SearchResults = { poets: Poet[]; poems: Array<PoemSummary & { poetId: string; poetDisplayName: string }> };

export default function Home({ initialMyPoemId, onNavigate }: { initialMyPoemId?: string; onNavigate: (path: string) => void }) {
  const [data, setData] = useState<HomeData | null>(null);
  const [active, setActive] = useState<Poem | null>(null);
  const [poemOfTheDay, setPoemOfTheDay] = useState<Poem | null>(null);
  const [viewer, setViewer] = useState(false);
  const [viewerPoetId, setViewerPoetId] = useState<string | null>(null);
  const [viewerPoetName, setViewerPoetName] = useState<string | null>(null);
  const [viewerBio, setViewerBio] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResults | null>(null);
  const [directory, setDirectory] = useState<Poet[] | null>(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const [error, setError] = useState("");
  const [showingRandomSelection, setShowingRandomSelection] = useState(false);
  const poemPanel = useRef<HTMLElement | null>(null);

  useEffect(() => { void load(initialMyPoemId); }, []);

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
        // A signed-in poet should always return to their own collection.
        if (session.poetId && !forcePublicBrowse) {
          await myPoems(preferredMyPoemId);
          return;
        }
      }

      // In read-only mode, the fixed daily poem is also the poem opened in the
      // reader and determines the poet shown in the left-hand list.
      if (daily) {
        const poetResponse = await fetch(`/api/discovery/poets/${daily.poetId}/poems`);
        if (!poetResponse.ok) throw new Error("Unable to load the poet for today's poem.");
        const poetData = await poetResponse.json() as Pick<HomeData, "poet" | "poems">;
        setData({ ...poetData, selectedPoem: daily });
        setActive(daily);
        setShowingRandomSelection(false);
        return;
      }

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

  useEffect(() => { poemPanel.current?.scrollTo({ top: 0, behavior: "smooth" }); }, [active?.poemId]);

  async function loadDirectory() {
    if (directory) return;
    const response = await fetch("/api/discovery/poets");
    if (response.ok) setDirectory(await response.json());
  }

  async function choosePoem(id: string) {
    const response = await fetch(`/api/discovery/poems/${id}`);
    if (response.ok) {
      const poem = await response.json() as Poem;
      const poetResponse = await fetch(`/api/discovery/poets/${poem.poetId}/poems`);
      if (!poetResponse.ok) return;
      const poetData = await poetResponse.json() as Pick<HomeData, "poet" | "poems">;
      setData({ ...poetData, selectedPoem: poem });
      setActive(poem);
      setShowingRandomSelection(false);
    }
    closeSearch();
  }

  async function search(value: string) {
    setQuery(value);
    if (value.trim().length < 2) { setResults(null); return; }
    const response = await fetch(`/api/discovery/search?q=${encodeURIComponent(value)}`);
    if (response.ok) setResults(await response.json());
  }

  async function choosePoet(id: string) {
    const response = await fetch(`/api/discovery/poets/${id}/poems`);
    if (!response.ok) return;
    const value = await response.json();
    const poem = value.poems[0] ? await fetch(`/api/discovery/poems/${value.poems[0].poemId}`).then(response => response.json()) : null;
    setData({ poet: value.poet, poems: value.poems, selectedPoem: poem });
    setActive(poem);
    setShowingRandomSelection(false);
    closeSearch();
  }

  function closeSearch() { setResults(null); setQuery(""); setSearchFocused(false); }

  async function myPoems(preferredPoemId?: string) {
    const [poemsResponse, profileResponse] = await Promise.all([fetch("/api/poems", { credentials: "include" }), fetch("/api/poets/me", { credentials: "include" })]);
    if (!poemsResponse.ok || !profileResponse.ok) return;
    const poems = await poemsResponse.json();
    const profile = await profileResponse.json();
    setViewerPoetName(profile.penName || profile.fullName);
    setViewerBio(profile.bio || null);
    if (!poems.length) { await load(undefined, true); return; }
    const summaries = poems.map((poem: any) => ({ poemId: poem.poemId, title: poem.title, excerpt: poem.poem.replace(/\s+/g, " ").slice(0, 160), createdAt: poem.createdAt }));
    const selectedPoemId = summaries.some(poem => poem.poemId === preferredPoemId) ? preferredPoemId! : summaries[0].poemId;
    const detail = await fetch(`/api/discovery/poems/${selectedPoemId}`).then(response => response.json());
    const poet = { poetId: profile.poetId, displayName: profile.penName || profile.fullName, bio: profile.bio, poemCount: poems.length };
    setData({ poet, poems: summaries, selectedPoem: detail });
    setActive(detail);
    setShowingRandomSelection(false);
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
  const headerButton = "inline-flex items-center justify-center border border-[#3d3660] bg-[#161a27] px-3 py-2 text-xs uppercase tracking-wider text-[#c8c0b0] transition hover:border-[#c9a84c] hover:text-[#e8c97a]";
  const newPoemButton = "inline-flex items-center justify-center border border-[#c9a84c] bg-[#c9a84c] px-3 py-2 text-xs font-semibold uppercase tracking-wider text-[#080a0f] transition hover:bg-[#e8c97a]";
  const viewerBioUrl = viewerPoetId && viewerPoetName ? `/poets/${viewerPoetId}/${slugify(viewerPoetName)}/bio` : null;
  const hasViewerBio = Boolean(viewerBio?.trim());

  return <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
    <header className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-4 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur sm:px-7">
      <div className="relative w-64 max-w-[45vw]">
        <input value={query} onChange={event => void search(event.target.value)} onFocus={() => { setSearchFocused(true); void loadDirectory(); }} onBlur={() => window.setTimeout(() => setSearchFocused(false), 150)} placeholder="Search poets or poems…" className="w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-xs outline-none focus:border-[#c9a84c]" />
        {(showingDirectory || showingResults) && <div className="absolute z-30 mt-1 max-h-80 w-full overflow-auto border border-[#2a2840] bg-[#161a27]">
          {showingDirectory && <><p className="border-b border-[#2a2840] px-3 py-2 text-[10px] uppercase tracking-widest text-[#8b8992]">All poets</p>{directory?.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}</>}
          {showingResults && <>{results.poets.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}{results.poems.map(poem => <button key={poem.poemId} onClick={() => void choosePoem(poem.poemId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poem.title} <span className="text-[#8b8992]">by {poem.poetDisplayName}</span></button>)}</>}
        </div>}
      </div>
      <div className="flex max-w-[40vw] flex-col items-center gap-1"><img src={ravenLogo} alt="Think or Drink Poetry" className="h-10 max-w-full object-contain" />{viewer && (hasViewerBio && viewerBioUrl ? <div className="flex max-w-full items-baseline gap-1 text-xs italic text-[#8b8992]"><span className="truncate">{viewerBio}</span><a href={viewerBioUrl} className="shrink-0 not-italic text-[#c9a84c] hover:text-[#e8c97a]">See full bio</a></div> : <button type="button" onClick={() => onNavigate("/account/setup")} className="text-xs text-[#c9a84c] hover:text-[#e8c97a]">Create a bio</button>)}</div>
      {viewer ? <div className="ml-auto flex flex-wrap justify-end gap-2"><button type="button" onClick={() => void myPoems()} className={headerButton}>My Poems</button><button type="button" onClick={() => onNavigate("/my-poems/new")} className={newPoemButton}>+ New Poem</button><button type="button" onClick={() => onNavigate("/account/setup")} className={headerButton}>Profile</button><button type="button" onClick={() => void signOut()} className={headerButton}>Sign Out</button></div> : <button type="button" onClick={() => onNavigate("/sign-in")} className={headerButton}>Sign In</button>}
    </header>
    <div className="flex min-h-0 flex-1">
      <aside className="poetry-scroll h-full w-64 shrink-0 overflow-y-auto border-r border-[#1e2235] bg-[#0d0f1a]"><div className="border-b border-[#1e2235] p-4"><p className="text-xs uppercase tracking-widest text-[#c9a84c]">{data.poet.displayName}</p>{showingRandomSelection && <p className="mt-1 text-xs italic text-[#c8c0b0]">Random poet of the day</p>}<p className="mt-1 text-xs text-[#8b8992]">{data.poet.poemCount} poems</p></div>{data.poems.map(poem => <button key={poem.poemId} onClick={() => void choosePoem(poem.poemId)} className={`block w-full border-b border-[#1a1d2a] px-4 py-4 text-left ${active.poemId === poem.poemId ? "border-l-2 border-l-[#c9a84c] bg-[#161a27]" : ""}`}><p className="text-sm">{poem.title}</p><p className="mt-1 overflow-hidden text-ellipsis whitespace-nowrap text-xs italic text-[#8b8992]">{poem.excerpt}</p><p className="mt-2 text-[10px] uppercase tracking-wider text-[#6f6b78]">Created {formatDate(poem.createdAt)}</p></button>)}</aside>
      <article ref={poemPanel} className="poetry-scroll h-full flex-1 overflow-y-auto px-6 py-12"><div className="mx-auto max-w-2xl"><p className="text-center text-xs uppercase tracking-[.25em]"><a href={`/poets/${active.poetId}/${slugify(active.poetDisplayName)}/bio`} className="text-[#c9a84c] hover:text-[#e8c97a]">{active.poetDisplayName}</a></p><div className="mt-3 flex items-center justify-center gap-3"><h1 className="font-serif text-4xl">{active.title}</h1>{viewerPoetId === active.poetId && <><button type="button" onClick={() => onNavigate(`/my-poems/${active.poemId}/edit`)} title="Edit this poem" aria-label={`Edit ${active.title}`} className="text-xl text-[#c9a84c] hover:text-[#e8c97a]">✎</button><button type="button" onClick={() => void deleteActivePoem()} title="Delete this poem" aria-label={`Delete ${active.title}`} className="text-lg text-red-300 hover:text-red-200">🗑</button></>}</div><p className="mt-3 text-center text-xs"><a href={`/poems/${active.poemId}/${slugify(active.title)}`} className="text-[#c9a84c]">Open shareable poem page</a></p><div className="mx-auto my-6 h-px w-48 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-lg italic leading-loose text-[#c8c0b0]">{active.poem}</div></div></article>
      {poemOfTheDay && <aside className="poetry-scroll hidden h-full w-[23rem] shrink-0 overflow-y-auto border-l border-[#1e2235] bg-[#0d0f1a] xl:block"><div className="border-b border-[#1e2235] p-4"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">Poem of the Day</p><p className="mt-1 text-xs text-[#8b8992]">A shared reading for today</p></div><button type="button" onClick={() => void choosePoem(poemOfTheDay.poemId)} className="block w-full px-6 py-8 text-left hover:bg-[#161a27]"><p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">{poemOfTheDay.poetDisplayName}</p><h2 className="mt-3 font-serif text-3xl text-[#e4ddd0]">{poemOfTheDay.title}</h2><div className="my-5 h-px w-32 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-base italic leading-loose text-[#c8c0b0]">{poemOfTheDay.poem}</div><p className="mt-6 text-xs text-[#c9a84c]">Read in the main panel →</p></button></aside>}
    </div>
    <footer className="flex shrink-0 items-center justify-between gap-4 border-t border-[#1e2235] bg-[#0e1018] px-4 py-3 text-xs sm:px-7"><a href="/" className="text-[#c9a84c] hover:text-[#e8c97a]">About Think or Drink Poetry</a>{viewer && <button type="button" onClick={() => onNavigate("/contact")} className="border border-[#3d3660] px-3 py-2 uppercase tracking-wider text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]">Contact Me</button>}</footer>
  </main>;
}

function slugify(value: string) {
  const slug = value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
  return slug || "poem";
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(value));
}
