"use client";

import { useState, type FormEvent } from "react";
import { describeError, getAppointment, type AppointmentResponse } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export default function AppointmentLookup() {
  const [id, setId] = useState("");
  const [result, setResult] = useState<AppointmentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      setResult(await getAppointment(id));
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="relative flex flex-col overflow-hidden rounded-[4px] border border-panel-line bg-panel before:block before:h-[10px] before:[background-image:radial-gradient(circle_at_10px_5px,var(--bg)_4px,transparent_4.5px)] before:[background-repeat:repeat-x] before:[background-size:20px_10px]"
    >
      <div className="flex items-baseline justify-between border-b border-dashed border-panel-line px-6 pt-4 pb-3">
        <h2 className="font-heading text-[1.05rem] font-bold tracking-[0.03em] uppercase">Look up a ticket</h2>
        <span className="font-mono text-[0.72rem] tracking-[0.04em] whitespace-nowrap text-muted-text">LOOKUP</span>
      </div>

      <div className="flex flex-col gap-[1.1rem] px-6 pt-[1.35rem] pb-6">
        <div className="flex flex-col gap-[0.35rem]">
          <Label>Appointment No.</Label>
          <Input
            className="font-mono"
            value={id}
            onChange={(e) => setId(e.target.value)}
            type="number"
            min={1}
            placeholder="e.g. 42"
            required
          />
        </div>

        <Button
          type="submit"
          disabled={loading}
          className="mt-1 cursor-pointer rounded-[3px] bg-brand px-4 py-[0.7rem] font-heading text-base font-bold tracking-[0.03em] text-brand-ink uppercase transition-transform duration-150 [&:hover:not(:disabled)]:-translate-y-px disabled:cursor-not-allowed disabled:opacity-45 motion-reduce:transition-none"
        >
          {loading ? "Looking up…" : "Get appointment"}
        </Button>

        {result && (
          <div className="relative rotate-[-1.2deg] animate-[stamp-down_0.28s_ease-out] rounded-[4px] border-[3px] border-stamp bg-transparent px-4 py-[0.85rem] text-[0.875rem] leading-[1.5] text-stamp motion-reduce:animate-none">
            <strong className="inline-block font-heading text-[1.05rem] font-extrabold tracking-[0.05em] uppercase">
              No. {result.id} — {result.status}
            </strong>
            <p className="font-mono text-[0.82rem] text-ink">
              Vehicle {result.vehicleVin} · {result.technicianName} · Bay {result.bayNumber}
            </p>
            <p className="font-mono text-[0.82rem] text-ink">
              {result.serviceType.replaceAll("_", " ")} · {result.startTime} → {result.endTime}
            </p>
          </div>
        )}
        {error && (
          <div className="rounded-[3px] border border-warn bg-warn-soft px-4 py-[0.85rem] text-[0.875rem] leading-[1.5] text-ink">
            {error}
          </div>
        )}
      </div>
    </form>
  );
}
