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
  { id: 2, name: "Uptown Keyloop Motors", specialties: ["OIL_CHANGE", "BRAKES", "TIRE_ROTATION"] },
];

const VEHICLE_MAKES = [
  { make: "Honda", models: ["Accord", "Civic", "CR-V", "Pilot"] },
  { make: "Toyota", models: ["Camry", "Corolla", "RAV4", "Highlander"] },
  { make: "Ford", models: ["F-150", "Escape", "Explorer", "Mustang"] },
  { make: "BMW", models: ["3 Series", "5 Series", "X3", "X5"] },
  { make: "Chevrolet", models: ["Silverado", "Equinox", "Malibu", "Tahoe"] },
  { make: "Nissan", models: ["Altima", "Rogue", "Sentra", "Pathfinder"] },
];

const SLOT_HOURS = ["09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00"];

const FIELD = "flex flex-col gap-[0.35rem]";
const ROW = "flex gap-[0.9rem] max-[480px]:flex-col";
const ROW_FIELD = `${FIELD} min-w-0 flex-1`;
const STEP = "flex items-start gap-[0.85rem]";
const STEP_NUMBER =
  "flex size-8 flex-none rotate-[-2deg] items-center justify-center rounded-[2px] border border-structural/35 bg-structural-soft font-heading text-[1.05rem] font-extrabold text-structural";
const STEP_BODY = "flex flex-1 flex-col gap-[0.7rem] pt-[0.15rem]";
const RESULT_ERROR = "rounded-[3px] border border-warn bg-warn-soft px-4 py-[0.85rem] text-[0.875rem] leading-[1.5] text-ink";

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

  const [vehicleMake, setVehicleMake] = useState(VEHICLE_MAKES[0].make);
  const [vehicleModel, setVehicleModel] = useState(VEHICLE_MAKES[0].models[0]);
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
  const selectedMake = VEHICLE_MAKES.find((m) => m.make === vehicleMake) ?? VEHICLE_MAKES[0];

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

  function handleMakeChange(nextMake: string | null) {
    if (!nextMake) return;
    setVehicleMake(nextMake);
    const next = VEHICLE_MAKES.find((m) => m.make === nextMake);
    if (next && !next.models.includes(vehicleModel)) {
      setVehicleModel(next.models[0]);
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
    <form
      onSubmit={handleSubmit}
      className="relative flex flex-col overflow-hidden rounded-[4px] border border-panel-line bg-panel before:block before:h-[10px] before:[background-image:radial-gradient(circle_at_10px_5px,var(--bg)_4px,transparent_4.5px)] before:[background-repeat:repeat-x] before:[background-size:20px_10px]"
    >
      <div className="flex items-baseline justify-between border-b border-dashed border-panel-line px-6 pt-4 pb-3">
        <h2 className="font-heading text-[1.05rem] font-bold tracking-[0.03em] uppercase">Work order</h2>
        <span className="font-mono text-[0.72rem] tracking-[0.04em] whitespace-nowrap text-muted-text">
          {result ? `No. ${result.id}` : "DRAFT"}
        </span>
      </div>

      <div className="flex flex-col gap-[1.1rem] px-6 pt-[1.35rem] pb-6">
        <div className={STEP}>
          <span className={STEP_NUMBER}>01</span>
          <div className={STEP_BODY}>
            <div className={FIELD}>
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
            <div className={FIELD}>
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

        <div className={STEP}>
          <span className={STEP_NUMBER}>02</span>
          <div className={STEP_BODY}>
            <div className={ROW}>
              <div className={ROW_FIELD}>
                <Label>Make</Label>
                <Select value={vehicleMake} onValueChange={handleMakeChange}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {VEHICLE_MAKES.map((m) => (
                      <SelectItem key={m.make} value={m.make}>
                        {m.make}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className={ROW_FIELD}>
                <Label>Model</Label>
                <Select value={vehicleModel} onValueChange={(v) => v && setVehicleModel(v)}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {selectedMake.models.map((model) => (
                      <SelectItem key={model} value={model}>
                        {model}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className={FIELD}>
              <Label>VIN / registration</Label>
              <Input
                className="font-mono"
                value={vehicleVin}
                onChange={(e) => setVehicleVin(e.target.value)}
                placeholder="e.g. 1HGCM82633A004352"
                required
              />
            </div>
          </div>
        </div>

        <div className={STEP}>
          <span className={STEP_NUMBER}>03</span>
          <div className={STEP_BODY}>
            <div className={FIELD}>
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

            {availabilityLoading && <p className="text-[0.85rem] text-muted-text">Checking the bay schedule…</p>}
            {availabilityError && <div className={RESULT_ERROR}>{availabilityError}</div>}

            {!availabilityLoading && !availabilityError && (
              <div className="grid grid-cols-[repeat(auto-fill,minmax(90px,1fr))] gap-2">
                {SLOT_HOURS.map((slot) => {
                  const bookable = isSlotBookable(slot);
                  const selected = selectedSlot === slot;
                  return (
                    <button
                      type="button"
                      key={slot}
                      disabled={!bookable}
                      onClick={() => setSelectedSlot(slot)}
                      className={`rounded-[3px] border px-[0.4rem] py-[0.55rem] font-mono text-[0.82rem] disabled:cursor-not-allowed disabled:opacity-[0.32] disabled:line-through ${
                        selected
                          ? "border-structural bg-structural font-semibold text-structural-soft"
                          : "border-panel-line bg-bg text-ink"
                      }`}
                    >
                      {formatSlotLabel(slot)}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        <div className={STEP}>
          <span className={STEP_NUMBER}>04</span>
          <div className={STEP_BODY}>
            <div className={FIELD}>
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

        <div className={STEP}>
          <span className={STEP_NUMBER}>05</span>
          <div className={STEP_BODY}>
            <div className={FIELD}>
              <Label>Full name</Label>
              <Input value={customerName} onChange={(e) => setCustomerName(e.target.value)} required />
            </div>
            <div className={ROW}>
              <div className={ROW_FIELD}>
                <Label>Email</Label>
                <Input value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} type="email" required />
              </div>
              <div className={ROW_FIELD}>
                <Label>Phone</Label>
                <Input value={customerPhone} onChange={(e) => setCustomerPhone(e.target.value)} type="tel" placeholder="555-0100" required />
              </div>
            </div>
          </div>
        </div>

        <button
          type="submit"
          disabled={loading || !selectedSlot}
          className="mt-1 cursor-pointer rounded-[3px] bg-brand px-4 py-[0.7rem] font-heading text-base font-bold tracking-[0.03em] text-brand-ink uppercase transition-transform duration-150 [&:hover:not(:disabled)]:-translate-y-px disabled:cursor-not-allowed disabled:opacity-45 motion-reduce:transition-none"
        >
          {loading ? "Booking…" : selectedSlot ? `Book ${formatSlotLabel(selectedSlot)}` : "Pick a time slot"}
        </button>

        {result && (
          <div className="relative rotate-[-1.2deg] animate-[stamp-down_0.28s_ease-out] rounded-[4px] border-[3px] border-stamp bg-transparent px-4 py-[0.85rem] text-[0.875rem] leading-[1.5] text-stamp motion-reduce:animate-none">
            <strong className="inline-block font-heading text-[1.05rem] font-extrabold tracking-[0.05em] uppercase">
              Confirmed — No. {result.id}
            </strong>
            <p className="font-mono text-[0.82rem] text-ink">
              {result.technicianName} · Bay {result.bayNumber} · {result.serviceType.replaceAll("_", " ")}
            </p>
            <p className="font-mono text-[0.82rem] text-ink">
              {result.startTime} → {result.endTime}
            </p>
          </div>
        )}
        {error && <div className={RESULT_ERROR}>{error}</div>}
      </div>
    </form>
  );
}
