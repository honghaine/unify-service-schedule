"use client";

import { useState, type FormEvent } from "react";
import { describeError, getAppointment, type AppointmentResponse } from "@/lib/api";

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
    <form onSubmit={handleSubmit} className="card">
      <h2>Look up an appointment</h2>

      <label>
        Appointment ID
        <input value={id} onChange={(e) => setId(e.target.value)} type="number" min={1} required />
      </label>

      <button type="submit" disabled={loading}>
        {loading ? "Looking up…" : "Get appointment"}
      </button>

      {result && (
        <div className="result success">
          <strong>
            Appointment #{result.id} — {result.status}
          </strong>
          <p>
            Vehicle {result.vehicleVin} · {result.technicianName} · Bay {result.bayNumber}
          </p>
          <p>
            {result.serviceType.replaceAll("_", " ")} · {result.startTime} → {result.endTime}
          </p>
        </div>
      )}
      {error && <div className="result error">{error}</div>}
    </form>
  );
}
