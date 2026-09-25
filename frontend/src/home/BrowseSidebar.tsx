import { formatDate } from "../poetry";
import type { DisplayedPoem } from "./types";

type BrowseSidebarProps = {
  activePoemId: string;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onChoosePoem: (poemId: string) => Promise<unknown>;
  onChooseRecentPoem: (poemId: string) => Promise<unknown>;
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
  onChoosePoem,
  onChooseRecentPoem,
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
    <aside className="flex w-72 shrink-0 flex-col border-r border-[#1e2235] bg-[#10121e] xl:w-80">
      <div className="border-b border-[#1e2235] bg-[#0d0f1a] p-4">
        <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
          {showingAllPoems ? "Browse poems" : "Selected poet"}
        </p>
        <div className="mt-2 flex items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="truncate font-serif text-xl text-[#e4ddd0]">
              {heading}
            </h2>
            <p className="mt-1 text-xs text-[#8b8992]">{description}</p>
          </div>
          {!showingAllPoems && (
            <button
              type="button"
              onClick={() => void onShowAllPoems()}
              className="shrink-0 border border-[#3d3660] px-2 py-1 text-[10px] uppercase tracking-wider text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]"
            >
              All Poems
            </button>
          )}
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col">
        <p className="shrink-0 border-b border-[#1e2235] px-4 py-3 text-xs text-[#8b8992]">
          {showingAllPoems
            ? "Choose a poem or search for a poet above."
            : "Choose a poem, or search for another poet above."}
        </p>
        <div className="poetry-scroll min-h-0 flex-1 overflow-y-auto">
          {displayedPoems.map((poem) => (
            <button
              type="button"
              key={poem.poemId}
              onClick={() =>
                void (showingAllPoems
                  ? onChooseRecentPoem(poem.poemId)
                  : onChoosePoem(poem.poemId))
              }
              className={`block w-full border-b border-[#1e2235] px-4 py-3 text-left transition hover:bg-[#161a27] ${activePoemId === poem.poemId ? "border-l-2 border-l-[#c9a84c] bg-[#161a27]" : "border-l-2 border-l-transparent"}`}
            >
              <span className="block truncate font-serif text-sm text-[#e4ddd0]">
                {poem.title}
              </span>
              <span className="mt-1 block text-[10px] uppercase tracking-wider text-[#8b8992]">
                {formatDate(poem.createdAt)}
              </span>
            </button>
          ))}
          {showingAllPoems && moreRecentPoems && (
            <button
              type="button"
              onClick={() => void onLoadMore()}
              className="block w-full border-b border-[#1e2235] px-4 py-3 text-left text-xs uppercase tracking-widest text-[#c9a84c] hover:bg-[#161a27]"
            >
              Load more poems
            </button>
          )}
        </div>
      </div>
    </aside>
  );
}
