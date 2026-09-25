import type { MouseEvent, PointerEvent, WheelEvent } from "react";
import { formatDate } from "../poetry";
import type { DisplayedPoem, Poet } from "./types";

type BrowseStripsProps = {
  activePoemId: string;
  activePoetId: string;
  directory: Poet[] | null;
  displayedPoems: DisplayedPoem[];
  moreRecentPoems: boolean;
  onChoosePoem: (poemId: string) => Promise<unknown>;
  onChoosePoet: (poetId: string) => Promise<unknown>;
  onChooseRecentPoem: (poemId: string) => Promise<unknown>;
  onClickCapture: (event: MouseEvent<HTMLDivElement>) => void;
  onLoadMore: () => Promise<unknown>;
  onPointerCancel: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerDown: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLDivElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLDivElement>) => void;
  onShowAllPoems: () => Promise<unknown>;
  onWheel: (event: WheelEvent<HTMLDivElement>) => void;
  poetChips: { current: Record<string, HTMLButtonElement | null> };
  poetName: string;
  poetPoemCount: number;
  showingAllPoems: boolean;
};

export default function BrowseStrips({
  activePoemId,
  activePoetId,
  directory,
  displayedPoems,
  moreRecentPoems,
  onChoosePoem,
  onChoosePoet,
  onChooseRecentPoem,
  onClickCapture,
  onLoadMore,
  onPointerCancel,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onShowAllPoems,
  onWheel,
  poetChips,
  poetName,
  poetPoemCount,
  showingAllPoems,
}: BrowseStripsProps) {
  const horizontalHandlers = {
    onWheel,
    onPointerDown,
    onPointerMove,
    onPointerUp,
    onPointerCancel,
    onClickCapture,
  };

  return (
    <>
      <section className="shrink-0 border-b border-[#1e2235] bg-[#0d0f1a]">
        <SectionHeading title="Browse poets" />
        <div className="flex gap-2 px-4 py-2 sm:px-7">
          <button
            type="button"
            onClick={() => void onShowAllPoems()}
            className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${showingAllPoems ? "border-[#c9a84c] bg-[#161a27] text-[#e8c97a]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}
          >
            All Poets <span className="text-[#8b8992]">(newest)</span>
          </button>
          <div
            {...horizontalHandlers}
            className="poetry-horizontal-scroll flex min-w-0 flex-1 gap-2 overflow-x-auto"
          >
            {directory?.map((poet) => (
              <button
                type="button"
                key={poet.poetId}
                ref={(element) => {
                  poetChips.current[poet.poetId] = element;
                }}
                onClick={() => void onChoosePoet(poet.poetId)}
                className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${!showingAllPoems && activePoetId === poet.poetId ? "border-[#c9a84c] bg-[#161a27] text-[#e8c97a]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}
              >
                {poet.displayName}{" "}
                <span className="text-[#8b8992]">({poet.poemCount})</span>
              </button>
            ))}
          </div>
        </div>
      </section>

      <section className="shrink-0 border-b border-[#1e2235] bg-[#10121e]">
        <SectionHeading
          title={
            showingAllPoems
              ? "All poems — newest added first"
              : `${poetName} — ${poetPoemCount} poems`
          }
        />
        <div
          {...horizontalHandlers}
          className="poetry-horizontal-scroll flex gap-2 overflow-x-auto px-4 py-2 sm:px-7"
        >
          {displayedPoems.map((poem) => (
            <button
              type="button"
              key={poem.poemId}
              onClick={() =>
                void (showingAllPoems
                  ? onChooseRecentPoem(poem.poemId)
                  : onChoosePoem(poem.poemId))
              }
              className={`shrink-0 whitespace-nowrap border px-3 py-2 text-left text-xs transition ${activePoemId === poem.poemId ? "border-[#c9a84c] bg-[#161a27]" : "border-[#2a2840] bg-[#080a0f] hover:border-[#c9a84c]"}`}
            >
              <span className="font-serif text-sm">{poem.title}</span>
              <span className="ml-2 text-[10px] uppercase tracking-wider text-[#8b8992]">
                {formatDate(poem.createdAt)}
              </span>
            </button>
          ))}
          {showingAllPoems && moreRecentPoems && (
            <button
              type="button"
              onClick={() => void onLoadMore()}
              className="shrink-0 whitespace-nowrap border border-dashed border-[#3d3660] px-3 py-2 text-xs uppercase tracking-widest text-[#c9a84c] hover:border-[#c9a84c]"
            >
              Load more poems
            </button>
          )}
        </div>
      </section>
    </>
  );
}

function SectionHeading({ title }: { title: string }) {
  return (
    <div className="flex items-center justify-between px-4 pt-2 sm:px-7">
      <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
        {title}
      </p>
      <p className="text-xs text-[#8b8992]">Drag to explore</p>
    </div>
  );
}
