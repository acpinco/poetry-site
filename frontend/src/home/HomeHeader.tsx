import { useState } from "react";
import ravenLogo from "../imports/Raven_Logo.png";
import { formatDate } from "../poetry";
import type { DisplayedPoem, MobilePanel, Poet, SearchResults } from "./types";

const headerButton =
  "inline-flex items-center justify-center border border-[#3d3660] bg-[#161a27] px-3 py-2 text-xs uppercase tracking-wider text-[#c8c0b0] transition hover:border-[#c9a84c] hover:text-[#e8c97a]";
const newPoemButton =
  "inline-flex items-center justify-center border border-[#c9a84c] bg-[#c9a84c] px-3 py-2 text-xs font-semibold uppercase tracking-wider text-[#080a0f] transition hover:bg-[#e8c97a]";

type HomeHeaderProps = {
  activePoemId: string;
  activePoetId: string;
  directory: Poet[] | null;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onChoosePoem: (poemId: string) => Promise<boolean>;
  onChooseRecentPoem: (poemId: string) => Promise<void>;
  onChoosePoet: (poetId: string) => Promise<void>;
  onLoadMore: () => Promise<void>;
  onMyPoems: () => Promise<void>;
  onNavigate: (path: string) => void;
  onSearch: (query: string) => Promise<void>;
  onShowAllPoems: () => Promise<void>;
  onSignOut: () => Promise<void>;
  query: string;
  results: SearchResults | null;
  showingAllPoems: boolean;
  viewer: boolean;
};

export default function HomeHeader({
  activePoemId,
  activePoetId,
  directory,
  displayedPoems,
  moreRecentPoems,
  onChoosePoem,
  onChooseRecentPoem,
  onChoosePoet,
  onLoadMore,
  onMyPoems,
  onNavigate,
  onSearch,
  onShowAllPoems,
  onSignOut,
  query,
  results,
  showingAllPoems,
  viewer,
}: HomeHeaderProps) {
  const [mobilePanel, setMobilePanel] = useState<MobilePanel>(null);
  const [mobileBrowseView, setMobileBrowseView] = useState<"poets" | "poems">(
    "poets",
  );
  const [desktopSearchFocused, setDesktopSearchFocused] = useState(false);
  const [desktopMenuOpen, setDesktopMenuOpen] = useState(false);
  const showingDirectory = desktopSearchFocused && query.trim().length < 2;
  const showingResults =
    desktopSearchFocused && query.trim().length >= 2 && results;

  function closeMobilePanel() {
    setMobilePanel(null);
  }

  function openSwagger() {
    window.location.assign("/swagger-ui.html");
  }

  function chooseDesktopPoet(poetId: string) {
    setDesktopSearchFocused(false);
    void onChoosePoet(poetId);
  }

  function chooseDesktopPoem(poemId: string) {
    setDesktopSearchFocused(false);
    void onChoosePoem(poemId);
  }

  return (
    <>
      <header className="z-30 shrink-0 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur lg:hidden">
        <div className="flex items-center gap-2">
          <img
            src={ravenLogo}
            alt="Think or Drink Poetry"
            className="h-9 shrink-0 object-contain"
          />
          <button
            type="button"
            onClick={() =>
              setMobilePanel(mobilePanel === "browse" ? null : "browse")
            }
            className={headerButton}
          >
            Browse
          </button>
          {viewer && (
            <button
              type="button"
              onClick={() => void onMyPoems()}
              className={headerButton}
            >
              My Poems
            </button>
          )}
          <button
            type="button"
            onClick={() =>
              setMobilePanel(mobilePanel === "menu" ? null : "menu")
            }
            className="ml-auto border border-[#3d3660] px-3 py-2 text-xs uppercase tracking-wider text-[#c8c0b0]"
            aria-expanded={mobilePanel === "menu"}
          >
            ☰ Menu
          </button>
        </div>
        <input
          value={query}
          onFocus={() => setMobilePanel("search")}
          onChange={(event) => {
            setMobilePanel("search");
            void onSearch(event.target.value);
          }}
          placeholder="Search poets or poems…"
          className="mt-3 w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-sm outline-none focus:border-[#c9a84c]"
        />
        {mobilePanel === "search" && (
          <MobileSearchResults
            query={query}
            results={results}
            onChoosePoet={(poetId) => {
              void onChoosePoet(poetId);
              closeMobilePanel();
            }}
            onChoosePoem={(poemId) => {
              void onChoosePoem(poemId);
              closeMobilePanel();
            }}
          />
        )}
        {mobilePanel === "browse" && (
          <MobileBrowsePanel
            activePoemId={activePoemId}
            activePoetId={activePoetId}
            directory={directory}
            displayedPoems={displayedPoems}
            moreRecentPoems={moreRecentPoems}
            onChoosePoem={(poemId) => {
              void (showingAllPoems
                ? onChooseRecentPoem(poemId)
                : onChoosePoem(poemId));
              closeMobilePanel();
            }}
            onChoosePoet={(poetId) => {
              void onChoosePoet(poetId);
              closeMobilePanel();
            }}
            onLoadMore={() => void onLoadMore()}
            onShowAllPoems={() => {
              void onShowAllPoems();
              setMobileBrowseView("poems");
            }}
            showingAllPoems={showingAllPoems}
            view={mobileBrowseView}
            onViewChange={setMobileBrowseView}
          />
        )}
        {mobilePanel === "menu" && (
          <MobileAccountMenu
            onNavigate={(path) => {
              closeMobilePanel();
              onNavigate(path);
            }}
            onSignOut={() => void onSignOut()}
            onOpenSwagger={openSwagger}
            viewer={viewer}
          />
        )}
      </header>

      <header className="sticky top-0 z-20 hidden flex-wrap items-center justify-between gap-4 border-b border-[#1e2235] bg-[#0e1018ee] px-4 py-3 backdrop-blur sm:px-7 lg:flex">
        <div className="flex min-w-0 items-center gap-3">
          <img
            src={ravenLogo}
            alt="Think or Drink Poetry"
            className="h-10 shrink-0 object-contain"
          />
          <DesktopSearch
            directory={directory}
            onChoosePoem={chooseDesktopPoem}
            onChoosePoet={chooseDesktopPoet}
            onFocusChange={setDesktopSearchFocused}
            onSearch={(value) => void onSearch(value)}
            query={query}
            results={results}
            showingDirectory={showingDirectory}
            showingResults={Boolean(showingResults)}
          />
        </div>
        {viewer ? (
          <div className="ml-auto flex flex-wrap justify-end gap-2">
            <button
              type="button"
              onClick={() => void onMyPoems()}
              className={headerButton}
            >
              My Poems
            </button>
            <button
              type="button"
              onClick={() => onNavigate("/my-poems/new")}
              className={newPoemButton}
            >
              + New Poem
            </button>
            <DesktopAccountMenu
              open={desktopMenuOpen}
              onToggle={() => setDesktopMenuOpen((open) => !open)}
              onNavigate={(path) => {
                setDesktopMenuOpen(false);
                onNavigate(path);
              }}
              onOpenSwagger={openSwagger}
              onSignOut={() => void onSignOut()}
            />
          </div>
        ) : (
          <button
            type="button"
            onClick={() => onNavigate("/sign-in")}
            className={headerButton}
          >
            Sign In
          </button>
        )}
      </header>
    </>
  );
}

function MobileSearchResults({
  query,
  results,
  onChoosePoet,
  onChoosePoem,
}: {
  query: string;
  results: SearchResults | null;
  onChoosePoet: (poetId: string) => void;
  onChoosePoem: (poemId: string) => void;
}) {
  if (query.trim().length < 2) {
    return (
      <div className="mt-2">
        <p className="px-1 py-3 text-xs text-[#8b8992]">
          Enter at least two characters.
        </p>
      </div>
    );
  }

  return (
    <div className="mt-2 max-h-60 overflow-y-auto border border-[#2a2840] bg-[#161a27]">
      {results?.poets.map((poet) => (
        <button
          key={poet.poetId}
          type="button"
          onClick={() => onChoosePoet(poet.poetId)}
          className="block w-full border-b border-[#2a2840] px-3 py-3 text-left text-sm"
        >
          {poet.displayName}{" "}
          <span className="text-xs text-[#8b8992]">
            · {poet.poemCount} poems
          </span>
        </button>
      ))}
      {results?.poems.map((poem) => (
        <button
          key={poem.poemId}
          type="button"
          onClick={() => onChoosePoem(poem.poemId)}
          className="block w-full border-b border-[#2a2840] px-3 py-3 text-left text-sm"
        >
          {poem.title}{" "}
          <span className="text-xs text-[#8b8992]">
            by {poem.poetDisplayName}
          </span>
        </button>
      ))}
    </div>
  );
}

function MobileBrowsePanel({
  activePoemId,
  activePoetId,
  directory,
  displayedPoems,
  moreRecentPoems,
  onChoosePoem,
  onChoosePoet,
  onLoadMore,
  onShowAllPoems,
  showingAllPoems,
  view,
  onViewChange,
}: {
  activePoemId: string;
  activePoetId: string;
  directory: Poet[] | null;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onChoosePoem: (poemId: string) => void;
  onChoosePoet: (poetId: string) => void;
  onLoadMore: () => void;
  onShowAllPoems: () => void;
  showingAllPoems: boolean;
  view: "poets" | "poems";
  onViewChange: (view: "poets" | "poems") => void;
}) {
  return (
    <div className="mt-2 border border-[#2a2840] bg-[#10121e]">
      <div className="grid grid-cols-2 border-b border-[#2a2840]">
        <button
          type="button"
          onClick={() => onViewChange("poets")}
          className={`px-3 py-3 text-xs uppercase tracking-wider ${view === "poets" ? "bg-[#161a27] text-[#e8c97a]" : "text-[#8b8992]"}`}
        >
          Poets
        </button>
        <button
          type="button"
          onClick={() => onViewChange("poems")}
          className={`px-3 py-3 text-xs uppercase tracking-wider ${view === "poems" ? "bg-[#161a27] text-[#e8c97a]" : "text-[#8b8992]"}`}
        >
          Poems
        </button>
      </div>
      <div className="max-h-64 overflow-y-auto">
        {view === "poets" ? (
          <>
            <button
              type="button"
              onClick={onShowAllPoems}
              className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left text-sm ${showingAllPoems ? "bg-[#161a27] text-[#e8c97a]" : ""}`}
            >
              All Poems{" "}
              <span className="text-xs text-[#8b8992]">— newest poems</span>
            </button>
            {directory?.map((poet) => (
              <button
                type="button"
                key={poet.poetId}
                onClick={() => onChoosePoet(poet.poetId)}
                className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left text-sm ${!showingAllPoems && activePoetId === poet.poetId ? "bg-[#161a27] text-[#e8c97a]" : ""}`}
              >
                {poet.displayName}{" "}
                <span className="text-xs text-[#8b8992]">
                  ({poet.poemCount})
                </span>
              </button>
            ))}
          </>
        ) : (
          <>
            {displayedPoems.map((poem) => (
              <button
                type="button"
                key={poem.poemId}
                onClick={() => onChoosePoem(poem.poemId)}
                className={`block w-full border-b border-[#2a2840] px-4 py-3 text-left ${activePoemId === poem.poemId ? "bg-[#161a27]" : ""}`}
              >
                <span className="font-serif text-base">{poem.title}</span>
                <span className="ml-2 text-[10px] uppercase tracking-wider text-[#8b8992]">
                  {formatDate(poem.createdAt)}
                </span>
                {showingAllPoems && (
                  <span className="ml-2 text-xs text-[#c9a84c]">
                    {poem.poetDisplayName}
                  </span>
                )}
              </button>
            ))}
            {showingAllPoems && moreRecentPoems && (
              <button
                type="button"
                onClick={onLoadMore}
                className="block w-full px-4 py-3 text-left text-xs uppercase tracking-widest text-[#c9a84c]"
              >
                Load more poems
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function DesktopSearch({
  directory,
  onChoosePoem,
  onChoosePoet,
  onFocusChange,
  onSearch,
  query,
  results,
  showingDirectory,
  showingResults,
}: {
  directory: Poet[] | null;
  onChoosePoem: (poemId: string) => void;
  onChoosePoet: (poetId: string) => void;
  onFocusChange: (focused: boolean) => void;
  onSearch: (query: string) => void;
  query: string;
  results: SearchResults | null;
  showingDirectory: boolean;
  showingResults: boolean;
}) {
  return (
    <div className="relative w-64 max-w-[55vw]">
      <input
        value={query}
        onChange={(event) => onSearch(event.target.value)}
        onFocus={() => onFocusChange(true)}
        onBlur={() => window.setTimeout(() => onFocusChange(false), 150)}
        placeholder="Search poets or poems…"
        className="w-full border border-[#2a2840] bg-[#080a0f] px-3 py-2 text-xs outline-none focus:border-[#c9a84c]"
      />
      {(showingDirectory || showingResults) && (
        <div className="absolute z-30 mt-1 max-h-80 w-full overflow-auto border border-[#2a2840] bg-[#161a27]">
          {showingDirectory && (
            <>
              <p className="border-b border-[#2a2840] px-3 py-2 text-[10px] uppercase tracking-widest text-[#8b8992]">
                All poets
              </p>
              {directory?.map((poet) => (
                <button
                  key={poet.poetId}
                  type="button"
                  onClick={() => onChoosePoet(poet.poetId)}
                  className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs"
                >
                  {poet.displayName}{" "}
                  <span className="text-[#8b8992]">
                    · {poet.poemCount} poems
                  </span>
                </button>
              ))}
            </>
          )}
          {showingResults && results && (
            <>
              {results.poets.map((poet) => (
                <button
                  key={poet.poetId}
                  type="button"
                  onClick={() => onChoosePoet(poet.poetId)}
                  className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs"
                >
                  {poet.displayName}{" "}
                  <span className="text-[#8b8992]">
                    · {poet.poemCount} poems
                  </span>
                </button>
              ))}
              {results.poems.map((poem) => (
                <button
                  key={poem.poemId}
                  type="button"
                  onClick={() => onChoosePoem(poem.poemId)}
                  className="block w-full border-b border-[#2a2840] px-3 py-2 text-left text-xs"
                >
                  {poem.title}{" "}
                  <span className="text-[#8b8992]">
                    by {poem.poetDisplayName}
                  </span>
                </button>
              ))}
            </>
          )}
        </div>
      )}
    </div>
  );
}

function MobileAccountMenu({
  onNavigate,
  onOpenSwagger,
  onSignOut,
  viewer,
}: {
  onNavigate: (path: string) => void;
  onOpenSwagger: () => void;
  onSignOut: () => void;
  viewer: boolean;
}) {
  if (!viewer) {
    return (
      <div className="mt-2 grid grid-cols-2 gap-2 border border-[#2a2840] bg-[#10121e] p-2">
        <button
          type="button"
          onClick={() => onNavigate("/sign-in")}
          className={`${headerButton} col-span-2`}
        >
          Sign In
        </button>
      </div>
    );
  }

  return (
    <div className="mt-2 grid grid-cols-2 gap-2 border border-[#2a2840] bg-[#10121e] p-2">
      <button
        type="button"
        onClick={() => onNavigate("/my-poems/new")}
        className={newPoemButton}
      >
        New Poem
      </button>
      <button
        type="button"
        onClick={() => onNavigate("/account/setup")}
        className={headerButton}
      >
        Profile
      </button>
      <button type="button" onClick={onOpenSwagger} className={headerButton}>
        Swagger
      </button>
      <button
        type="button"
        onClick={() => onNavigate("/contact")}
        className={headerButton}
      >
        Contact
      </button>
      <button type="button" onClick={onSignOut} className={headerButton}>
        Sign Out
      </button>
    </div>
  );
}

function DesktopAccountMenu({
  open,
  onNavigate,
  onOpenSwagger,
  onSignOut,
  onToggle,
}: {
  open: boolean;
  onNavigate: (path: string) => void;
  onOpenSwagger: () => void;
  onSignOut: () => void;
  onToggle: () => void;
}) {
  return (
    <div className="relative">
      <button
        type="button"
        onClick={onToggle}
        className={headerButton}
        aria-expanded={open}
        aria-controls="account-menu"
      >
        ☰ Menu
      </button>
      {open && (
        <div
          id="account-menu"
          className="absolute right-0 z-40 mt-2 w-40 border border-[#3d3660] bg-[#161a27] p-1 shadow-xl"
        >
          <MenuButton onClick={() => onNavigate("/account/setup")}>
            Profile
          </MenuButton>
          <MenuButton onClick={onOpenSwagger}>Swagger</MenuButton>
          <MenuButton onClick={() => onNavigate("/contact")}>
            Contact
          </MenuButton>
          <MenuButton destructive onClick={onSignOut}>
            Sign Out
          </MenuButton>
        </div>
      )}
    </div>
  );
}

function MenuButton({
  children,
  destructive = false,
  onClick,
}: {
  children: string;
  destructive?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`block w-full px-3 py-2 text-left text-xs uppercase tracking-wider hover:bg-[#22263a] ${destructive ? "text-red-200 hover:text-red-100" : "text-[#c8c0b0] hover:text-[#e8c97a]"}`}
    >
      {children}
    </button>
  );
}
