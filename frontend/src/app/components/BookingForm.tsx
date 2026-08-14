"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  createAppointment,
  describeError,
  getTechnicianAvailability,
  type AppointmentResponse,
  type TechnicianAvailability,
} from "@/lib/api";

const DEALERSHIPS = [
  { id: 1, name: "Downtown Keyloop Motors", specialties: ["OIL_CHANGE", "BRAKES", "TIRE_ROTATION"] },
  { id: 2, name: "Uptown Keyloop Motors", specialties: ["OIL_CHANGE", "BRAKES"] },
];

const SLOT_HOURS = ["09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00"];

function addHour(hhmm: string): string {
  const [h, m] = hhmm.split(":").map(Number);
  return `${String(h + 1).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function formatSlotLabel(hhmm: string): string {
  const [h] = hhmm.split(":").map(Number);
  const period = h < 12 ? "AM" : "PM";
  const hour12 = h % 12 === 0 ? 12 : h % 12;
  return `${hour12}:00 ${period}`;
}

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function slotOverlapsBusyWindow(dateIso: string, slotStart: string, busy: { startTime: string; endTime: string }): boolean {
  const start = new Date(`${dateIso}T${slotStart}:00`).getTime();
  const end = new Date(`${dateIso}T${addHour(slotStart)}:00`).getTime();
  const busyStart = new Date(busy.startTime).getTime();
  const busyEnd = new Date(busy.endTime).getTime();
  return start < busyEnd && busyStart < end;
}

export default function BookingForm() {
  const [dealershipId, setDealershipId] = useState(String(DEALERSHIPS[0].id));
  const [serviceType, setServiceType] = useState(DEALERSHIPS[0].specialties[0]);

  const [vehicleMake, setVehicleMake] = useState("");
  const [vehicleModel, setVehicleModel] = useState("");
  const [vehicleVin, setVehicleVin] = useState("");

  const [date, setDate] = useState(todayIso());
  const [selectedSlot, setSelectedSlot] = useState<string | null>(null);
  const [technicianIdRaw, setTechnicianId] = useState("");

  const [customerName, setCustomerName] = useState("");
  const [customerEmail, setCustomerEmail] = useState("");
  const [customerPhone, setCustomerPhone] = useState("");

  const [availability, setAvailability] = useState<TechnicianAvailability[]>([]);
  const [availabilityLoading, setAvailabilityLoading] = useState(false);
  const [availabilityError, setAvailabilityError] = useState<string | null>(null);

  const [result, setResult] = useState<AppointmentResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const dealership = DEALERSHIPS.find((d) => d.id === Number(dealershipId)) ?? DEALERSHIPS[0];

  useEffect(() => {
    if (!date) return;
    // Standard data-fetch-with-loading-state effect (react.dev's own
    // "Fetching data" example uses this exact shape) — not the derived-state
    // anti-pattern react-hooks/set-state-in-effect otherwise guards against.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAvailabilityLoading(true);
    setAvailabilityError(null);
    setSelectedSlot(null);
    getTechnicianAvailability(Number(dealershipId), serviceType, date)
      .then((data) => setAvailability(data))
      .catch((err) => setAvailabilityError(describeError(err)))
      .finally(() => setAvailabilityLoading(false));
  }, [dealershipId, serviceType, date]);

  // Derived during render rather than synced via a second effect: if the
  // stored pick is no longer in the current availability list (dealership/
  // service/date changed underneath it), treat selection as cleared.
  const technicianId = availability.some((t) => String(t.technicianId) === technicianIdRaw)
    ? technicianIdRaw
    : "";

  function handleDealershipChange(nextId: string) {
    setDealershipId(nextId);
    const next = DEALERSHIPS.find((d) => d.id === Number(nextId));
    if (next && !next.specialties.includes(serviceType)) {
      setServiceType(next.specialties[0]);
    }
  }

  function techniciansFreeAt(slot: string): TechnicianAvailability[] {
    return availability.filter((t) => !t.busyWindows.some((w) => slotOverlapsBusyWindow(date, slot, w)));
  }

  function isSlotBookable(slot: string): boolean {
    const free = techniciansFreeAt(slot);
    if (technicianId) {
      return free.some((t) => String(t.technicianId) === technicianId);
    }
    return free.length > 0;
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!selectedSlot) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const appointment = await createAppointment({
        serviceType,
        dealershipId: Number(dealershipId),
        desiredStart: `${date}T${selectedSlot}:00`,
        desiredEnd: `${date}T${addHour(selectedSlot)}:00`,
        technicianId: technicianId ? Number(technicianId) : undefined,
        customerName,
        customerEmail,
        customerPhone,
        vehicleVin,
        vehicleMake,
        vehicleModel,
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
      <div className="ticket-head">
        <h2>Work order</h2>
        <span className="ticket-no">{result ? `No. ${result.id}` : "DRAFT"}</span>
      </div>

      <div className="card-body">
        <div className="step">
          <span className="step-number">01</span>
          <div className="step-body">
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
              Service
              <select value={serviceType} onChange={(e) => setServiceType(e.target.value)}>
                {dealership.specialties.map((s) => (
                  <option key={s} value={s}>
                    {s.replaceAll("_", " ")}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <div className="step">
          <span className="step-number">02</span>
          <div className="step-body">
            <div className="row">
              <label>
                Make
                <input value={vehicleMake} onChange={(e) => setVehicleMake(e.target.value)} placeholder="Toyota" required />
              </label>
              <label>
                Model
                <input value={vehicleModel} onChange={(e) => setVehicleModel(e.target.value)} placeholder="Corolla" required />
              </label>
            </div>
            <label>
              VIN / registration
              <input
                className="mono-input"
                value={vehicleVin}
                onChange={(e) => setVehicleVin(e.target.value)}
                placeholder="e.g. 1HGCM82633A004352"
                required
              />
            </label>
          </div>
        </div>

        <div className="step">
          <span className="step-number">03</span>
          <div className="step-body">
            <label>
              Date
              <input value={date} onChange={(e) => setDate(e.target.value)} type="date" min={todayIso()} required />
            </label>

            {availabilityLoading && <p className="hint">Checking the bay schedule…</p>}
            {availabilityError && <div className="result error">{availabilityError}</div>}

            {!availabilityLoading && !availabilityError && (
              <div className="slot-grid">
                {SLOT_HOURS.map((slot) => {
                  const bookable = isSlotBookable(slot);
                  return (
                    <button
                      type="button"
                      key={slot}
                      disabled={!bookable}
                      className={`slot-btn${selectedSlot === slot ? " selected" : ""}`}
                      onClick={() => setSelectedSlot(slot)}
                    >
                      {formatSlotLabel(slot)}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div className="step">
          <span className="step-number">04</span>
          <div className="step-body">
            <label>
              Technician
              <select value={technicianId} onChange={(e) => setTechnicianId(e.target.value)}>
                <option value="">No preference — assign for me</option>
                {availability.map((t) => (
                  <option
                    key={t.technicianId}
                    value={t.technicianId}
                    disabled={selectedSlot ? !techniciansFreeAt(selectedSlot).some((f) => f.technicianId === t.technicianId) : false}
                  >
                    {t.technicianName}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>

        <div className="step">
          <span className="step-number">05</span>
          <div className="step-body">
            <label>
              Full name
              <input value={customerName} onChange={(e) => setCustomerName(e.target.value)} required />
            </label>
            <div className="row">
              <label>
                Email
                <input value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} type="email" required />
              </label>
              <label>
                Phone
                <input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} type="tel" placeholder="555-0100" required />
              </label>
            </div>
          </div>
        </div>

        <button type="submit" disabled={loading || !selectedSlot}>
          {loading ? "Booking…" : selectedSlot ? `Book ${formatSlotLabel(selectedSlot)}` : "Pick a time slot"}
        </button>

        {result && (
          <div className="result success">
            <strong>Confirmed — No. {result.id}</strong>
            <p>
              {result.technicianName} · Bay {result.bayNumber} · {result.serviceType.replaceAll("_", " ")}
            </p>
            <p>
              {result.startTime} → {result.endTime}
            </p>
          </div>
        )}
        {error && <div className="result error">{error}</div>}
      </div>
    </form>
  );
}
