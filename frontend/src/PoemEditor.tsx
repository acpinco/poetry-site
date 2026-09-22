import { FormEvent, useEffect, useState } from "react";
import ravenLogo from "./imports/Raven_Logo.png";

type EditablePoem = { poemId: string; title: string; poem: string };

export default function PoemEditor({ poemId, onNavigate }: { poemId?: string; onNavigate: (path: string) => void }) {
  const [title, setTitle] = useState("");
  const [poem, setPoem] = useState("");
  const [loading, setLoading] = useState(Boolean(poemId));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!poemId) return;
    void loadPoem(poemId);
  }, [poemId]);

  async function loadPoem(id: string) {
    try {
      const response = await fetch(`/api/poems/${id}`, { credentials: "include" });
      if (response.status === 401 || response.status === 403) { onNavigate("/"); return; }
      if (!response.ok) throw new Error("That poem could not be opened for editing.");
      const value = await response.json() as EditablePoem;
      setTitle(value.title);
      setPoem(value.poem);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "That poem could not be opened for editing.");
    } finally {
      setLoading(false);
    }
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSaving(true);
    try {
      const response = await fetch(poemId ? `/api/poems/${poemId}` : "/api/poems", {
        method: poemId ? "PUT" : "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ title, poem })
      });
      if (response.status === 401 || response.status === 403) { onNavigate("/"); return; }
      if (!response.ok) throw new Error("Your poem could not be saved. Please try again.");
      const saved = await response.json() as EditablePoem;
      onNavigate(`/home?mine=1&poem=${encodeURIComponent(saved.poemId)}`);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Your poem could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  return <main className="min-h-screen bg-[#080a0f] px-4 py-10 text-[#e4ddd0] sm:py-16">
    <div className="mx-auto max-w-3xl">
      <button type="button" onClick={() => onNavigate("/home")} className="mb-8 text-xs uppercase tracking-widest text-[#c9a84c]">← Back to poems</button>
      <section className="border border-[#2a2840] bg-[#0e1018] p-6 shadow-[0_0_30px_rgba(201,168,76,.08)] sm:p-10">
        <img src={ravenLogo} alt="Think or Drink Poetry" className="mx-auto h-12 max-w-full object-contain" />
        <h1 className="mt-8 text-center font-serif text-3xl">{poemId ? "Edit your poem" : "Write a new poem"}</h1>
        <p className="mt-2 text-center text-sm text-[#8b8992]">Your words are yours. Take your time.</p>
        {loading ? <p className="mt-10 text-center text-[#8b8992]">Opening poem…</p> : <form onSubmit={save} className="mt-10 space-y-6">
          <div>
            <label htmlFor="poem-title" className="mb-2 block text-xs uppercase tracking-wider text-[#8b8992]">Title</label>
            <input id="poem-title" required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} placeholder="Give your poem a title" className="w-full border border-[#2a2840] bg-[#080a0f] px-4 py-3 text-base outline-none focus:border-[#c9a84c]" />
          </div>
          <div>
            <label htmlFor="poem-body" className="mb-2 block text-xs uppercase tracking-wider text-[#8b8992]">Poem</label>
            <textarea id="poem-body" required maxLength={100000} value={poem} onChange={event => setPoem(event.target.value)} placeholder="Begin writing…" rows={18} className="w-full resize-y border border-[#2a2840] bg-[#080a0f] px-4 py-3 font-serif text-base leading-relaxed outline-none focus:border-[#c9a84c]" />
            <p className="mt-1 text-right text-xs text-[#8b8992]">{poem.length.toLocaleString()} / 100,000</p>
          </div>
          {error && <p role="alert" className="text-sm text-red-300">{error}</p>}
          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button type="button" onClick={() => onNavigate("/home")} className="border border-[#3d3660] px-5 py-3 text-xs uppercase tracking-widest text-[#c8c0b0]">Cancel</button>
            <button type="submit" disabled={saving} className="bg-[#c9a84c] px-5 py-3 text-xs font-semibold uppercase tracking-widest text-[#080a0f] disabled:opacity-60">{saving ? "Saving…" : poemId ? "Save changes" : "Publish poem"}</button>
          </div>
        </form>}
      </section>
    </div>
  </main>;
}
