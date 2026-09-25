export type Poet = {
  poetId: string;
  displayName: string;
  bio: string | null;
  poemCount: number;
};

export type PoemSummary = {
  poemId: string;
  title: string;
  excerpt: string;
  createdAt: string;
};

export type Poem = {
  poemId: string;
  poetId: string;
  title: string;
  poem: string;
  poetDisplayName: string;
  poetBio: string | null;
  createdAt: string;
};

export type OwnedPoem = {
  poemId: string;
  poetId: string;
  title: string;
  poem: string;
  createdAt: string;
  updatedAt: string;
};

export type RecentPoem = {
  poemId: string;
  poetId: string;
  title: string;
  poetDisplayName: string;
  excerpt: string;
  createdAt: string;
};

export type HomeData = {
  poet: Poet;
  poems: PoemSummary[];
  selectedPoem: Poem;
};

export type SearchResults = {
  poets: Poet[];
  poems: Array<PoemSummary & { poetId: string; poetDisplayName: string }>;
};

export type DisplayedPoem = PoemSummary &
  Partial<Pick<RecentPoem, "poetId" | "poetDisplayName">>;

export type MobilePanel = "browse" | "search" | "menu" | null;
