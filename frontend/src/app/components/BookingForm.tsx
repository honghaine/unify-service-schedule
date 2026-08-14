"use client";

import { useEffect, useState, type FormEvent } from "react";
import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import { toast } from "sonner";
import {
  createAppointment,
  describeError,
  getTechnicianAvailability,
  type AppointmentResponse,
  type TechnicianAvailability,
} from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

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

function startOfToday(): Date {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
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

  const [date, setDate] = useState<Date>(startOfToday());
  const [datePickerOpen, setDatePickerOpen] = useState(false);
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

  const dateIso = format(date, "yyyy-MM-dd");
  const dealership = DEALERSHIPS.find((d) => d.id === Number(dealershipId)) ?? DEALERSHIPS[0];

  useEffect(() => {
    // Standard data-fetch-with-loading-state effect (react.dev's own
    // "Fetching data" example uses this exact shape) — not the derived-state
    // anti-pattern react-hooks/set-state-in-effect otherwise guards against.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAvailabilityLoading(true);
    setAvailabilityError(null);
    setSelectedSlot(null);
    getTechnicianAvailability(Number(dealershipId), serviceType, dateIso)
      .then((data) => setAvailability(data))
      .catch((err) => setAvailabilityError(describeError(err)))
      .finally(() => setAvailabilityLoading(false));
  }, [dealershipId, serviceType, dateIso]);

  // Derived during render rather than synced via a second effect: if the
  // stored pick is no longer in the current availability list (dealership/
  // service/date changed underneath it), treat selection as cleared.
  const technicianId = availability.some((t) => String(t.technicianId) === technicianIdRaw)
    ? technicianIdRaw
    : "";

  function handleDealershipChange(nextId: string | null) {
    if (!nextId) return;
    setDealershipId(nextId);
    const next = DEALERSHIPS.find((d) => d.id === Number(nextId));
    if (next && !next.specialties.includes(serviceType)) {
      setServiceType(next.specialties[0]);
    }
  }

  function techniciansFreeAt(slot: string): TechnicianAvailability[] {
    return availability.filter((t) => !t.busyWindows.some((w) => slotOverlapsBusyWindow(dateIso, slot, w)));
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
        desiredStart: `${dateIso}T${selectedSlot}:00`,
        desiredEnd: `${dateIso}T${addHour(selectedSlot)}:00`,
        technicianId: technicianId ? Number(technicianId) : undefined,
        customerName,
        customerEmail,
        customerPhone,
        vehicleVin,
        vehicleMake,
        vehicleModel,
      });
      setResult(appointment);
      toast.success(`Ticket No. ${appointment.id} confirmed`, {
        description: `${appointment.technicianName} · Bay ${appointment.bayNumber} · ${appointment.startTime}`,
      });
    } catch (err) {
      const message = describeError(err);
      setError(message);
      toast.error("Booking rejected", { description: message });
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
            <div className="field">
              <Label>Dealership</Label>
              <Select value={dealershipId} onValueChange={handleDealershipChange}>
                <SelectTrigger className="w-full">
                  <SelectValue>
                    {(value: string) => DEALERSHIPS.find((d) => String(d.id) === value)?.name ?? value}
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {DEALERSHIPS.map((d) => (
                    <SelectItem key={d.id} value={String(d.id)}>
                      {d.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="field">
              <Label>Service</Label>
              <Select value={serviceType} onValueChange={(v) => v && setServiceType(v)}>
                <SelectTrigger className="w-full">
                  <SelectValue>{(value: string) => value.replaceAll("_", " ")}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  {dealership.specialties.map((s) => (
                    <SelectItem key={s} value={s}>
                      {s.replaceAll("_", " ")}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>

        <div className="step">
          <span className="step-number">02</span>
          <div className="step-body">
            <div className="row">
              <div className="field">
                <Label>Make</Label>
                <Input value={vehicleMake} onChange={(e) => setVehicleMake(e.target.value)} placeholder="Toyota" required />
              </div>
              <div className="field">
                <Label>Model</Label>
                <Input value={vehicleModel} onChange={(e) => setVehicleModel(e.target.value)} placeholder="Corolla" required />
              </div>
            </div>
            <div className="field">
              <Label>VIN / registration</Label>
              <Input
                className="mono-input"
                value={vehicleVin}
                onChange={(e) => setVehicleVin(e.target.value)}
                placeholder="e.g. 1HGCM82633A004352"
                required
              />
            </div>
          </div>
        </div>

        <div className="step">
          <span className="step-number">03</span>
          <div className="step-body">
            <div className="field">
              <Label>Date</Label>
              <Popover open={datePickerOpen} onOpenChange={setDatePickerOpen}>
                <PopoverTrigger
                  render={
                    <Button type="button" variant="outline" className="w-full justify-start font-normal">
                      <CalendarIcon className="size-4" />
                      {format(date, "EEE, MMM d yyyy")}
                    </Button>
                  }
                />
                <PopoverContent className="w-auto p-0">
                  <Calendar
                    mode="single"
                    className="[--cell-size:2.25rem]"
                    selected={date}
                    defaultMonth={date}
                    disabled={{ before: startOfToday() }}
                    onSelect={(picked) => {
                      if (picked) {
                        setDate(picked);
                        setDatePickerOpen(false);
                      }
                    }}
                  />
                </PopoverContent>
              </Popover>
            </div>

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
            <div className="field">
              <Label>Technician</Label>
              <Select value={technicianId || "any"} onValueChange={(v) => setTechnicianId(!v || v === "any" ? "" : v)}>
                <SelectTrigger className="w-full">
                  <SelectValue>
                    {(value: string) =>
                      value === "any"
                        ? "No preference — assign for me"
                        : (availability.find((t) => String(t.technicianId) === value)?.technicianName ?? value)
                    }
                  </SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="any">No preference — assign for me</SelectItem>
                  {availability.map((t) => (
                    <SelectItem
                      key={t.technicianId}
                      value={String(t.technicianId)}
                      disabled={selectedSlot ? !techniciansFreeAt(selectedSlot).some((f) => f.technicianId === t.technicianId) : false}
                    >
                      {t.technicianName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </div>

        <div className="step">
          <span className="step-number">05</span>
          <div className="step-body">
            <div className="field">
              <Label>Full name</Label>
              <Input value={customerName} onChange={(e) => setCustomerName(e.target.value)} required />
            </div>
            <div className="row">
              <div className="field">
                <Label>Email</Label>
                <Input value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} type="email" required />
              </div>
              <div className="field">
                <Label>Phone</Label>
                <Input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} type="tel" placeholder="555-0100" required />
              </div>
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
