import { useRef, type RefObject, type TouchEvent } from "react";
import { bioSnippet, slugify } from "../poetry";
import type { Poem } from "./types";

type PoemReaderProps = {
  active: Poem;
  hasNextPoem: boolean;
  hasPreviousPoem: boolean;
  mobilePoemPanel: RefObject<HTMLElement | null>;
  onChoosePoem: (poemId: string) => Promise<unknown>;
  onDelete: () => Promise<void>;
  onNextPoem: () => Promise<void>;
  onNavigate: (path: string) => void;
  onPreviousPoem: () => Promise<void>;
  poemOfTheDay: Poem | null;
  poemPanel: RefObject<HTMLElement | null>;
  viewer: boolean;
  viewerPoetId: string | null;
};

export function MobilePoemReader({
  active,
  hasNextPoem,
  hasPreviousPoem,
  mobilePoemPanel,
  onChoosePoem,
  onDelete,
  onNextPoem,
  onNavigate,
  onPreviousPoem,
  poemOfTheDay,
  viewerPoetId,
}: Omit<PoemReaderProps, "poemPanel" | "viewer">) {
  const touchStart = useRef<{ x: number; y: number } | null>(null);

  function startSwipe(event: TouchEvent<HTMLElement>) {
    if (event.touches.length !== 1) return;
    const touch = event.touches[0];
    touchStart.current = { x: touch.clientX, y: touch.clientY };
  }

  function finishSwipe(event: TouchEvent<HTMLElement>) {
    const start = touchStart.current;
    touchStart.current = null;
    if (!start || event.changedTouches.length !== 1) return;

    const target = event.target as HTMLElement;
    if (target.closest("a, button, input, select, summary, textarea")) return;

    const touch = event.changedTouches[0];
    const horizontalDistance = touch.clientX - start.x;
    const verticalDistance = touch.clientY - start.y;
    if (
      Math.abs(horizontalDistance) < 64 ||
      Math.abs(horizontalDistance) <= Math.abs(verticalDistance)
    ) {
      return;
    }

    void (horizontalDistance < 0 ? onNextPoem() : onPreviousPoem());
  }

  return (
    <article
      ref={mobilePoemPanel}
      onTouchEnd={finishSwipe}
      onTouchStart={startSwipe}
      className="poetry-scroll min-h-0 flex-1 overflow-y-auto px-5 py-8"
    >
      <div className="mx-auto max-w-xl">
        {poemOfTheDay && poemOfTheDay.poemId !== active.poemId && (
          <MobilePoemOfTheDay poem={poemOfTheDay} onChoosePoem={onChoosePoem} />
        )}
        <PoemDetails
          compact
          navigation={{
            hasNextPoem,
            hasPreviousPoem,
            onNextPoem,
            onPreviousPoem,
          }}
          poem={active}
          viewerPoetId={viewerPoetId}
          onDelete={onDelete}
          onNavigate={onNavigate}
        />
      </div>
    </article>
  );
}

export function DesktopPoemReader({
  active,
  onChoosePoem,
  onDelete,
  onNavigate,
  poemOfTheDay,
  poemPanel,
  viewerPoetId,
}: Omit<
  PoemReaderProps,
  | "hasNextPoem"
  | "hasPreviousPoem"
  | "mobilePoemPanel"
  | "onNextPoem"
  | "onPreviousPoem"
  | "viewer"
>) {
  return (
    <div className="flex min-h-0 flex-1">
      <article
        ref={poemPanel}
        className="poetry-scroll h-full flex-1 overflow-y-auto px-6 py-12"
      >
        <div className="mx-auto max-w-2xl">
          <PoemDetails
            poem={active}
            viewerPoetId={viewerPoetId}
            onDelete={onDelete}
            onNavigate={onNavigate}
          />
        </div>
      </article>
      {poemOfTheDay && (
        <DesktopPoemOfTheDay poem={poemOfTheDay} onChoosePoem={onChoosePoem} />
      )}
    </div>
  );
}

export function HomeFooter({
  compact = false,
  onNavigate,
  viewer,
}: {
  compact?: boolean;
  onNavigate: (path: string) => void;
  viewer: boolean;
}) {
  return (
    <footer
      className={`flex shrink-0 items-center justify-between gap-4 border-t border-[#1e2235] bg-[#0e1018] px-4 py-3 text-xs ${compact ? "" : "sm:px-7"}`}
    >
      <a href="/" className="text-[#c9a84c] hover:text-[#e8c97a]">
        {compact ? "About" : "About Think or Drink Poetry"}
      </a>
      {viewer && (
        <button
          type="button"
          onClick={() => onNavigate("/contact")}
          className="border border-[#3d3660] px-3 py-2 uppercase tracking-wider text-[#c8c0b0] hover:border-[#c9a84c] hover:text-[#e8c97a]"
        >
          Contact Me
        </button>
      )}
    </footer>
  );
}

function PoemDetails({
  compact = false,
  navigation,
  onDelete,
  onNavigate,
  poem,
  viewerPoetId,
}: {
  compact?: boolean;
  navigation?: {
    hasNextPoem: boolean;
    hasPreviousPoem: boolean;
    onNextPoem: () => Promise<void>;
    onPreviousPoem: () => Promise<void>;
  };
  onDelete: () => Promise<void>;
  onNavigate: (path: string) => void;
  poem: Poem;
  viewerPoetId: string | null;
}) {
  const bioUrl = `/poets/${poem.poetId}/${slugify(poem.poetDisplayName)}/bio?poem=${encodeURIComponent(poem.poemId)}`;
  const ownsPoem = viewerPoetId === poem.poetId;

  return (
    <>
      <p className="text-center text-xs uppercase tracking-[.25em]">
        <a href={bioUrl} className="text-[#c9a84c] hover:text-[#e8c97a]">
          {poem.poetDisplayName}
        </a>
      </p>
      {poem.poetBio?.trim() && (
        <p className="mx-auto mt-2 max-w-xl text-center text-xs italic leading-relaxed text-[#8b8992]">
          <span className="not-italic text-[#c8c0b0]">
            About {poem.poetDisplayName}:{" "}
          </span>
          {bioSnippet(poem.poetBio)}{" "}
          <a
            href={bioUrl}
            className="not-italic text-[#c9a84c] hover:text-[#e8c97a]"
          >
            Read full bio
          </a>
        </p>
      )}
      {navigation ? (
        <>
          <div className="mt-3 grid grid-cols-[2.5rem_minmax(0,1fr)_2.5rem] items-center gap-2">
            <PoemNavigationButton
              direction="previous"
              available={navigation.hasPreviousPoem}
              onClick={navigation.onPreviousPoem}
            />
            <h1 className="text-center font-serif text-3xl leading-tight">
              {poem.title}
            </h1>
            <PoemNavigationButton
              direction="next"
              available={navigation.hasNextPoem}
              onClick={navigation.onNextPoem}
            />
          </div>
          {ownsPoem && (
            <PoemActions
              poem={poem}
              onDelete={onDelete}
              onNavigate={onNavigate}
            />
          )}
        </>
      ) : (
        <div className="mt-3 flex items-center justify-center gap-3">
          <h1 className="font-serif text-4xl">{poem.title}</h1>
          {ownsPoem && (
            <PoemActions
              poem={poem}
              onDelete={onDelete}
              onNavigate={onNavigate}
            />
          )}
        </div>
      )}
      <p className="mt-3 text-center text-xs">
        <a
          href={`/poems/${poem.poemId}/${slugify(poem.title)}`}
          className="text-[#c9a84c]"
        >
          Open shareable poem page
        </a>
      </p>
      <div
        className={`mx-auto my-6 h-px bg-[#c9a84c55] ${compact ? "w-40" : "w-48"}`}
      />
      <div className="whitespace-pre-wrap font-serif text-lg italic leading-loose text-[#c8c0b0]">
        {poem.poem}
      </div>
    </>
  );
}

function PoemNavigationButton({
  available,
  direction,
  onClick,
}: {
  available: boolean;
  direction: "next" | "previous";
  onClick: () => Promise<void>;
}) {
  if (!available) return <span aria-hidden="true" />;

  const isPrevious = direction === "previous";
  return (
    <button
      type="button"
      onClick={() => void onClick()}
      aria-label={`${isPrevious ? "Previous" : "Next"} poem`}
      title={`${isPrevious ? "Previous" : "Next"} poem`}
      className="grid h-9 w-9 place-items-center rounded-full border border-[#3d3660] text-lg text-[#c9a84c] transition hover:border-[#c9a84c] hover:text-[#e8c97a]"
    >
      {isPrevious ? "←" : "→"}
    </button>
  );
}

function PoemActions({
  onDelete,
  onNavigate,
  poem,
}: {
  onDelete: () => Promise<void>;
  onNavigate: (path: string) => void;
  poem: Poem;
}) {
  return (
    <span className="flex items-center justify-center gap-3">
      <button
        type="button"
        onClick={() => onNavigate(`/my-poems/${poem.poemId}/edit`)}
        title="Edit this poem"
        aria-label={`Edit ${poem.title}`}
        className="text-xl text-[#c9a84c] hover:text-[#e8c97a]"
      >
        ✎
      </button>
      <button
        type="button"
        onClick={() => void onDelete()}
        title="Delete this poem"
        aria-label={`Delete ${poem.title}`}
        className="text-lg text-red-300 hover:text-red-200"
      >
        🗑
      </button>
    </span>
  );
}

function MobilePoemOfTheDay({
  onChoosePoem,
  poem,
}: {
  onChoosePoem: (poemId: string) => Promise<unknown>;
  poem: Poem;
}) {
  return (
    <details className="mb-8 border border-[#2a2840] bg-[#0d0f1a] p-4">
      <summary className="cursor-pointer list-none">
        <span className="flex items-center gap-2 text-xs uppercase tracking-[.2em] text-[#c9a84c]">
          <span aria-hidden="true">▸</span>
          Poem of the Day
        </span>
        <span className="mt-2 block font-serif text-base text-[#e4ddd0]">
          {poem.title}
        </span>
      </summary>
      <a
        href="/poem-of-the-day"
        className="mt-3 block text-xs text-[#c9a84c] hover:text-[#e8c97a]"
      >
        Open today’s shareable page →
      </a>
      <button
        type="button"
        onClick={() => void onChoosePoem(poem.poemId)}
        className="mt-4 block w-full text-left"
      >
        <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
          {poem.poetDisplayName}
        </p>
        <h2 className="mt-2 font-serif text-xl">{poem.title}</h2>
        <p className="mt-3 font-serif text-[13px] italic leading-relaxed text-[#c8c0b0]">
          {poem.poem.slice(0, 300)}
          {poem.poem.length > 300 ? "…" : ""}
        </p>
        <p className="mt-4 text-xs text-[#c9a84c]">Read in the reader →</p>
      </button>
    </details>
  );
}

function DesktopPoemOfTheDay({
  onChoosePoem,
  poem,
}: {
  onChoosePoem: (poemId: string) => Promise<unknown>;
  poem: Poem;
}) {
  return (
    <aside className="poetry-scroll hidden h-full w-[23rem] shrink-0 overflow-y-auto border-l border-[#1e2235] bg-[#0d0f1a] xl:block">
      <div className="border-b border-[#1e2235] p-4">
        <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
          Poem of the Day
        </p>
        <p className="mt-1 text-xs text-[#8b8992]">
          A shared reading for today
        </p>
        <a
          href="/poem-of-the-day"
          className="mt-3 inline-block text-xs text-[#c9a84c] hover:text-[#e8c97a]"
        >
          Open today’s shareable page →
        </a>
      </div>
      <button
        type="button"
        onClick={() => void onChoosePoem(poem.poemId)}
        className="block w-full px-6 py-8 text-left hover:bg-[#161a27]"
      >
        <p className="text-xs uppercase tracking-[.2em] text-[#c9a84c]">
          {poem.poetDisplayName}
        </p>
        <h2 className="mt-3 font-serif text-3xl text-[#e4ddd0]">
          {poem.title}
        </h2>
        <div className="my-5 h-px w-32 bg-[#c9a84c55]" />
        <div className="whitespace-pre-wrap font-serif text-base italic leading-loose text-[#c8c0b0]">
          {poem.poem}
        </div>
        <p className="mt-6 text-xs text-[#c9a84c]">Read in the main panel →</p>
      </button>
    </aside>
  );
}
