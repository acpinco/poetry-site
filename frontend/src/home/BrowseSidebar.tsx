import { formatDate } from "../poetry";
import type { DisplayedPoem } from "./types";

type BrowseSidebarProps = {
  activePoemId: string;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onSelectListPoem: (poemId: string) => Promise<unknown>;
  onLoadMore: () => Promise<unknown>;
  onShowAllPoems: () => Promise<unknown>;
  poetName: string;
  poetPoemCount: number;
  showingAllPoems: boolean;
};

export default function BrowseSidebar({
  activePoemId,
  displayedPoems,
  moreRecentPoems,
  onSelectListPoem,
  onLoadMore,
  onShowAllPoems,
  poetName,
  poetPoemCount,
  showingAllPoems,
}: BrowseSidebarProps) {
  const heading = showingAllPoems ? "All Poems" : poetName;
  const description = showingAllPoems
    ? "Newest additions first"
    : `${poetPoemCount} ${poetPoemCount === 1 ? "poem" : "poems"}`;

  return (
    <aside className="flex w-72 shrink-0 flex-col border-r border-line-soft bg-sidebar xl:w-80">
      <div className="border-b border-line-soft bg-sidebar-deep p-4">
        <p className="text-xs uppercase tracking-[.2em] text-gold">
          {showingAllPoems ? "Browse poems" : "Selected poet"}
        </p>
        <div className="mt-2 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate font-serif text-xl text-cream">
              {heading}
            </h2>
            <p className="mt-1 text-xs text-muted">{description}</p>
          </div>
          {!showingAllPoems && (
            <button
              type="button"
              onClick={() => void onShowAllPoems()}
              className="shrink-0 border border-line-strong px-2 py-1 text-[10px] uppercase tracking-wider text-parchment hover:border-gold hover:text-gold-light"
            >
              All Poems
            </button>
          )}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col">
        <p className="shrink-0 border-b border-line-soft px-4 py-3 text-xs text-muted">
          {showingAllPoems
            ? "Choose a poem or search for a poet above."
            : "Choose a poem, or search for another poet above."}
        </p>
        <div className="poetry-scroll min-h-0 flex-1 overflow-y-auto">
          {displayedPoems.map((poem) => (
            <button
              type="button"
              key={poem.poemId}
              onClick={() => void onSelectListPoem(poem.poemId)}
              className={`block w-full border-b border-line-soft px-4 py-3 text-left transition hover:bg-raised ${activePoemId === poem.poemId ? "border-l-2 border-l-gold bg-raised" : "border-l-2 border-l-transparent"}`}
            >
              <span className="block truncate font-serif text-sm text-cream">
                {poem.title}
              </span>
              <span className="mt-1 block text-[10px] uppercase tracking-wider text-muted">
                {formatDate(poem.createdAt)}
              </span>
            </button>
          ))}
          {showingAllPoems && moreRecentPoems && (
            <button
              type="button"
              onClick={() => void onLoadMore()}
              className="block w-full border-b border-line-soft px-4 py-3 text-left text-xs uppercase tracking-widest text-gold hover:bg-raised"
            >
              Load more poems
            </button>
          )}
        </div>
      </div>
    </aside>
  );
}
