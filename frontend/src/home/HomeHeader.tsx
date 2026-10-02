import { useState } from "react";
import ravenLogo from "../imports/Raven_Logo.png";
import { formatDate } from "../poetry";
import { DESKTOP_QUERY, useMediaQuery } from "../useMediaQuery";
import type { DisplayedPoem, MobilePanel, Poet, SearchResults } from "./types";

const headerButton =
  "inline-flex items-center justify-center border border-line-strong bg-raised px-3 py-2 text-xs uppercase tracking-wider text-parchment transition hover:border-gold hover:text-gold-light";
const newPoemButton =
  "inline-flex items-center justify-center border border-gold bg-gold px-3 py-2 text-xs font-semibold uppercase tracking-wider text-night transition hover:bg-gold-light";

type HomeHeaderProps = {
  activePoemId: string;
  activePoetId: string;
  directory: Poet[] | null;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onChoosePoem: (poemId: string) => Promise<void>;
  onSelectListPoem: (poemId: string) => Promise<void>;
  onChoosePoet: (poetId: string) => Promise<void>;
  onLoadMore: () => Promise<void>;
  onMyPoems: () => Promise<void>;
  onNavigate: (path: string) => void;
  onSearch: (query: string) => void;
  onShowAllPoems: () => Promise<void>;
  onSignOut: () => Promise<void>;
  query: string;
  results: SearchResults | null;
  showingAllPoems: boolean;
  viewer: boolean;
  viewerIsAdmin: boolean;
};

export default function HomeHeader({
  activePoemId,
  activePoetId,
  directory,
  displayedPoems,
  moreRecentPoems,
  onChoosePoem,
  onSelectListPoem,
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
  viewerIsAdmin,
}: HomeHeaderProps) {
  const isDesktop = useMediaQuery(DESKTOP_QUERY);
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
    // A new tab keeps the reader where it was.
    window.open("/swagger-ui.html", "_blank", "noopener,noreferrer");
  }

  function chooseDesktopPoet(poetId: string) {
    setDesktopSearchFocused(false);
    void onChoosePoet(poetId);
  }

  function chooseDesktopPoem(poemId: string) {
    setDesktopSearchFocused(false);
    void onChoosePoem(poemId);
  }

  if (!isDesktop)
    return (
      <header className="z-30 shrink-0 border-b border-line-soft bg-panel/93 px-4 py-3 backdrop-blur">
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
            className="ml-auto border border-line-strong px-3 py-2 text-xs uppercase tracking-wider text-parchment"
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
            onSearch(event.target.value);
          }}
          placeholder="Search poets or poems…"
          className="mt-3 w-full border border-line bg-night px-3 py-2 text-sm outline-none focus:border-gold"
        />
        {mobilePanel === "search" && (
          <div className="mt-2 max-h-60 overflow-y-auto border border-line bg-raised">
            <SearchMatches
              compact={false}
              directory={directory}
              results={results}
              showDirectory={query.trim().length < 2}
              onChoosePoet={(poetId) => {
                void onChoosePoet(poetId);
                closeMobilePanel();
              }}
              onChoosePoem={(poemId) => {
                void onChoosePoem(poemId);
                closeMobilePanel();
              }}
            />
          </div>
        )}
        {mobilePanel === "browse" && (
          <MobileBrowsePanel
            activePoemId={activePoemId}
            activePoetId={activePoetId}
            directory={directory}
            displayedPoems={displayedPoems}
            moreRecentPoems={moreRecentPoems}
            onChoosePoem={(poemId) => {
              void onSelectListPoem(poemId);
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
            viewerIsAdmin={viewerIsAdmin}
          />
        )}
      </header>
    );

  return (
    <header className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-4 border-b border-line-soft bg-panel/93 px-4 py-3 backdrop-blur sm:px-7">
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
          onSearch={onSearch}
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
            viewerIsAdmin={viewerIsAdmin}
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
  );
}

/**
 * The poet directory (before a search) or search matches, as a list of buttons.
 * Mobile uses roomier rows than the desktop dropdown.
 */
function SearchMatches({
  compact,
  directory,
  results,
  showDirectory,
  onChoosePoet,
  onChoosePoem,
}: {
  compact: boolean;
  directory: Poet[] | null;
  results: SearchResults | null;
  showDirectory: boolean;
  onChoosePoet: (poetId: string) => void;
  onChoosePoem: (poemId: string) => void;
}) {
  const item = `block w-full border-b border-line px-3 text-left ${compact ? "py-2 text-xs" : "py-3 text-sm"}`;
  const detail = compact ? "text-muted" : "text-xs text-muted";
  const poetButton = (poet: Poet) => (
    <button
      key={poet.poetId}
      type="button"
      onClick={() => onChoosePoet(poet.poetId)}
      className={item}
    >
      {poet.displayName}{" "}
      <span className={detail}>· {poet.poemCount} poems</span>
    </button>
  );

  if (showDirectory)
    return (
      <>
        <p className="border-b border-line px-3 py-2 text-[10px] uppercase tracking-widest text-muted">
          All poets
        </p>
        {directory?.map(poetButton)}
      </>
    );
  return (
    <>
      {results?.poets.map(poetButton)}
      {results?.poems.map((poem) => (
        <button
          key={poem.poemId}
          type="button"
          onClick={() => onChoosePoem(poem.poemId)}
          className={item}
        >
          {poem.title} <span className={detail}>by {poem.poetDisplayName}</span>
        </button>
      ))}
    </>
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
    <div className="mt-2 border border-line bg-sidebar">
      <div className="grid grid-cols-2 border-b border-line">
        <button
          type="button"
          onClick={() => onViewChange("poets")}
          className={`px-3 py-3 text-xs uppercase tracking-wider ${view === "poets" ? "bg-raised text-gold-light" : "text-muted"}`}
        >
          Poets
        </button>
        <button
          type="button"
          onClick={() => onViewChange("poems")}
          className={`px-3 py-3 text-xs uppercase tracking-wider ${view === "poems" ? "bg-raised text-gold-light" : "text-muted"}`}
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
              className={`block w-full border-b border-line px-4 py-3 text-left text-sm ${showingAllPoems ? "bg-raised text-gold-light" : ""}`}
            >
              All Poems{" "}
              <span className="text-xs text-muted">— newest poems</span>
            </button>
            {directory?.map((poet) => (
              <button
                type="button"
                key={poet.poetId}
                onClick={() => onChoosePoet(poet.poetId)}
                className={`block w-full border-b border-line px-4 py-3 text-left text-sm ${!showingAllPoems && activePoetId === poet.poetId ? "bg-raised text-gold-light" : ""}`}
              >
                {poet.displayName}{" "}
                <span className="text-xs text-muted">({poet.poemCount})</span>
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
                className={`block w-full border-b border-line px-4 py-3 text-left ${activePoemId === poem.poemId ? "bg-raised" : ""}`}
              >
                <span className="font-serif text-base">{poem.title}</span>
                <span className="ml-2 text-[10px] uppercase tracking-wider text-muted">
                  {formatDate(poem.createdAt)}
                </span>
                {showingAllPoems && (
                  <span className="ml-2 text-xs text-gold">
                    {poem.poetDisplayName}
                  </span>
                )}
              </button>
            ))}
            {showingAllPoems && moreRecentPoems && (
              <button
                type="button"
                onClick={onLoadMore}
                className="block w-full px-4 py-3 text-left text-xs uppercase tracking-widest text-gold"
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
        className="w-full border border-line bg-night px-3 py-2 text-xs outline-none focus:border-gold"
      />
      {(showingDirectory || showingResults) && (
        <div className="absolute z-30 mt-1 max-h-80 w-full overflow-auto border border-line bg-raised">
          <SearchMatches
            compact
            directory={directory}
            results={showingResults ? results : null}
            showDirectory={showingDirectory}
            onChoosePoet={onChoosePoet}
            onChoosePoem={onChoosePoem}
          />
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
  viewerIsAdmin,
}: {
  onNavigate: (path: string) => void;
  onOpenSwagger: () => void;
  onSignOut: () => void;
  viewer: boolean;
  viewerIsAdmin: boolean;
}) {
  if (!viewer) {
    return (
      <div className="mt-2 grid grid-cols-2 gap-2 border border-line bg-sidebar p-2">
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
    <div className="mt-2 grid grid-cols-2 gap-2 border border-line bg-sidebar p-2">
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
      {viewerIsAdmin && (
        <button
          type="button"
          onClick={() => onNavigate("/admin")}
          className={headerButton}
        >
          Admin
        </button>
      )}
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
  viewerIsAdmin,
}: {
  open: boolean;
  onNavigate: (path: string) => void;
  onOpenSwagger: () => void;
  onSignOut: () => void;
  onToggle: () => void;
  viewerIsAdmin: boolean;
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
          className="absolute right-0 z-40 mt-2 w-40 border border-line-strong bg-raised p-1 shadow-xl"
        >
          <MenuButton onClick={() => onNavigate("/account/setup")}>
            Profile
          </MenuButton>
          <MenuButton onClick={onOpenSwagger}>Swagger</MenuButton>
          <MenuButton onClick={() => onNavigate("/contact")}>
            Contact
          </MenuButton>
          {viewerIsAdmin && (
            <MenuButton onClick={() => onNavigate("/admin")}>Admin</MenuButton>
          )}
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
      className={`block w-full px-3 py-2 text-left text-xs uppercase tracking-wider hover:bg-raised-hover ${destructive ? "text-red-200 hover:text-red-100" : "text-parchment hover:text-gold-light"}`}
    >
      {children}
    </button>
  );
}
