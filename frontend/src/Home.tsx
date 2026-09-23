import { useEffect, useRef, useState } from "react";
import ravenLogo from "./imports/Raven_Logo.png";

type Poet = { poetId: string; displayName: string; bio: string | null; poemCount: number };
type PoemSummary = { poemId: string; title: string; excerpt: string; createdAt: string };
type Poem = { poemId: string; poetId: string; title: string; poem: string; poetDisplayName: string; createdAt: string };
type HomeData = { poet: Poet; poems: PoemSummary[]; selectedPoem: Poem };
type SearchResults = { poets: Poet[]; poems: Array<PoemSummary & { poetId: string; poetDisplayName: string }> };

export default function Home({ initialMyPoemId, showMyPoems, onNavigate }: { initialMyPoemId?: string; showMyPoems: boolean; onNavigate: (path: string) => void }) {
  const [data, setData] = useState<HomeData | null>(null);
  const [active, setActive] = useState<Poem | null>(null);
  const [viewer, setViewer] = useState(false);
  const [viewerPoetId, setViewerPoetId] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<SearchResults | null>(null);
  const [directory, setDirectory] = useState<Poet[] | null>(null);
  const [searchFocused, setSearchFocused] = useState(false);
  const [error, setError] = useState("");
  const [showingRandomSelection, setShowingRandomSelection] = useState(false);
  const poemPanel = useRef<HTMLElement | null>(null);

  useEffect(() => { void load(showMyPoems, initialMyPoemId); }, []);

  async function load(showOwnPoems = false, preferredMyPoemId?: string) {
    try {
      const [home, me] = await Promise.all([fetch("/api/discovery/home"), fetch("/api/auth/me", { credentials: "include" })]);
      if (!home.ok) throw new Error("No poems are available yet.");
      if (showOwnPoems && me.ok) {
        const session = await me.json() as { poetId: string | null };
        setViewer(true);
        setViewerPoetId(session.poetId);
        await myPoems(preferredMyPoemId);
        return;
      }
      const value = await home.json();
      setData(value);
      setActive(value.selectedPoem);
      setShowingRandomSelection(true);
      if (me.ok) {
        const session = await me.json() as { poetId: string | null };
        setViewer(true);
        setViewerPoetId(session.poetId);
      } else {
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
    if (!poems.length) { await load(); return; }
    const profile = await profileResponse.json();
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

  return <main className="flex h-screen flex-col overflow-hidden bg-[#080a0f] text-[#e4ddd0]">
    <header className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-4 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur sm:px-7">
      <div className="relative w-64 max-w-[45vw]">
        <input value={query} onChange={event => void search(event.target.value)} onFocus={() => { setSearchFocused(true); void loadDirectory(); }} onBlur={() => window.setTimeout(() => setSearchFocused(false), 150)} placeholder="Search poets or poems…" className="w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-xs outline-none focus:border-[#c9a84c]" />
        {viewer && <div className="mt-1 flex gap-3 text-xs"><button onClick={() => void myPoems()} className="text-[#c9a84c]">My Poems</button><button onClick={() => onNavigate("/my-poems/new")} className="font-semibold text-[#e8c97a]">+ New Poem</button></div>}
        {(showingDirectory || showingResults) && <div className="absolute z-30 mt-1 max-h-80 w-full overflow-auto border border-[#2a2840] bg-[#161a27]">
          {showingDirectory && <><p className="border-b border-[#2a2840] px-3 py-2 text-[10px] uppercase tracking-widest text-[#8b8992]">All poets</p>{directory?.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}</>}
          {showingResults && <>{results.poets.map(poet => <button key={poet.poetId} onClick={() => void choosePoet(poet.poetId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poet.displayName} <span className="text-[#8b8992]">· {poet.poemCount} poems</span></button>)}{results.poems.map(poem => <button key={poem.poemId} onClick={() => void choosePoem(poem.poemId)} className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs">{poem.title} <span className="text-[#8b8992]">by {poem.poetDisplayName}</span></button>)}</>}
        </div>}
      </div>
      <img src={ravenLogo} alt="Think or Drink Poetry" className="h-10 max-w-[35vw] object-contain" />
      <p className="order-last w-full text-center text-base italic text-[#c8c0b0]">Ability to add and remove your own poems coming next week!</p>
      {viewer ? <div className="flex gap-3 text-xs"><button onClick={() => onNavigate("/account/setup")} className="text-[#c9a84c]">Profile</button><button onClick={() => void signOut()} className="text-[#8b8992]">Sign Out</button></div> : <button onClick={() => onNavigate("/sign-in")} className="border border-[#c9a84c] px-3 py-2 text-xs uppercase tracking-wider text-[#c9a84c]">Sign In</button>}
    </header>
    <div className="flex min-h-0 flex-1">
      <aside className="poetry-scroll h-full w-64 shrink-0 overflow-y-auto border-r border-[#1e2235] bg-[#0d0f1a]"><div className="border-b border-[#1e2235] p-4"><p className="text-xs uppercase tracking-widest text-[#c9a84c]">{data.poet.displayName}</p>{showingRandomSelection && <p className="mt-1 text-xs italic text-[#c8c0b0]">Random poet of the day</p>}<p className="mt-1 text-xs text-[#8b8992]">{data.poet.poemCount} poems</p></div>{data.poems.map(poem => <button key={poem.poemId} onClick={() => void choosePoem(poem.poemId)} className={`block w-full border-b border-[#1a1d2a] px-4 py-4 text-left ${active.poemId === poem.poemId ? "border-l-2 border-l-[#c9a84c] bg-[#161a27]" : ""}`}><p className="text-sm">{poem.title}</p><p className="mt-1 overflow-hidden text-ellipsis whitespace-nowrap text-xs italic text-[#8b8992]">{poem.excerpt}</p><p className="mt-2 text-[10px] uppercase tracking-wider text-[#6f6b78]">Created {formatDate(poem.createdAt)}</p></button>)}</aside>
      <article ref={poemPanel} className="poetry-scroll h-full flex-1 overflow-y-auto px-6 py-12"><div className="mx-auto max-w-2xl">{showingRandomSelection && <p className="text-center text-xs uppercase tracking-[.25em] text-[#8b8992]">Random poem for the day</p>}<p className="text-center text-xs uppercase tracking-[.25em] text-[#c9a84c]">{active.poetDisplayName}</p><div className="mt-3 flex items-center justify-center gap-3"><h1 className="font-serif text-4xl">{active.title}</h1>{viewerPoetId === active.poetId && <><button type="button" onClick={() => onNavigate(`/my-poems/${active.poemId}/edit`)} title="Edit this poem" aria-label={`Edit ${active.title}`} className="text-xl text-[#c9a84c] hover:text-[#e8c97a]">✎</button><button type="button" onClick={() => void deleteActivePoem()} title="Delete this poem" aria-label={`Delete ${active.title}`} className="text-lg text-red-300 hover:text-red-200">🗑</button></>}</div><p className="mt-3 text-center text-xs"><a href={`/poems/${active.poemId}/${slugify(active.title)}`} className="text-[#c9a84c]">Open shareable poem page</a></p><div className="mx-auto my-6 h-px w-48 bg-[#c9a84c55]" /><div className="whitespace-pre-wrap font-serif text-lg italic leading-loose text-[#c8c0b0]">{active.poem}</div></div></article>
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
