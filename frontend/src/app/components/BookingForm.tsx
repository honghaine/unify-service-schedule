"use client";

import { useState, type FormEvent } from "react";
import { createAppointment, describeError, type AppointmentResponse } from "@/lib/api";

const DEALERSHIPS = [
  { id: 1, name: "Downtown Keyloop Motors", specialties: ["OIL_CHANGE", "BRAKES", "TIRE_ROTATION"] },
  { id: 2, name: "Uptown Keyloop Motors", specialties: ["OIL_CHANGE", "BRAKES"] },
];

export default function BookingForm() {
  const [vehicleId, setVehicleId] = useState("1");
  const [dealershipId, setDealershipId] = useState(String(DEALERSHIPS[0].id));
  const [serviceType, setServiceType] = useState(DEALERSHIPS[0].specialties[0]);
  const [date, setDate] = useState("");
  const [startTime, setStartTime] = useState("09:00");
  const [endTime, setEndTime] = useState("10:00");
  const [result, setResult] = useState<AppointmentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const dealership = DEALERSHIPS.find((d) => d.id === Number(dealershipId)) ?? DEALERSHIPS[0];

  function handleDealershipChange(nextId: string) {
    setDealershipId(nextId);
    const next = DEALERSHIPS.find((d) => d.id === Number(nextId));
    if (next && !next.specialties.includes(serviceType)) {
      setServiceType(next.specialties[0]);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const appointment = await createAppointment({
        vehicleId: Number(vehicleId),
        serviceType,
        dealershipId: Number(dealershipId),
        desiredStart: `${date}T${startTime}:00`,
        desiredEnd: `${date}T${endTime}:00`,
      });
      setResult(appointment);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="card">
      <h2>Book an appointment</h2>

      <label>
        Vehicle ID
        <input value={vehicleId} onChange={(e) => setVehicleId(e.target.value)} type="number" min={1} required />
      </label>

      <label>
        Dealership
        <select value={dealershipId} onChange={(e) => handleDealershipChange(e.target.value)}>
          {DEALERSHIPS.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </label>

      <label>
        Service type
        <select value={serviceType} onChange={(e) => setServiceType(e.target.value)}>
          {dealership.specialties.map((s) => (
            <option key={s} value={s}>
              {s.replaceAll("_", " ")}
            </option>
          ))}
        </select>
      </label>

      <label>
        Date
        <input value={date} onChange={(e) => setDate(e.target.value)} type="date" required />
      </label>

      <div className="row">
        <label>
          Start
          <input value={startTime} onChange={(e) => setStartTime(e.target.value)} type="time" required />
        </label>
        <label>
          End
          <input value={endTime} onChange={(e) => setEndTime(e.target.value)} type="time" required />
        </label>
      </div>

      <button type="submit" disabled={loading}>
        {loading ? "Booking…" : "Book appointment"}
      </button>

      {result && (
        <div className="result success">
          <strong>Confirmed — appointment #{result.id}</strong>
          <p>
            {result.technicianName} · Bay {result.bayNumber} · {result.serviceType.replaceAll("_", " ")}
          </p>
          <p>
            {result.startTime} → {result.endTime}
          </p>
        </div>
      )}
      {error && <div className="result error">{error}</div>}
    </form>
  );
}
