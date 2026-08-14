import AppointmentLookup from "./components/AppointmentLookup";
import BookingForm from "./components/BookingForm";
import { Toaster } from "@/components/ui/sonner";

export default function Home() {
  return (
    <>
      <Toaster position="top-right" />
      <header className="shopfront">
        <div className="shopfront-inner">
          <span className="shopfront-mark">Keyloop Service Dept.</span>
          <span className="shopfront-status">
            <span className="dot" aria-hidden="true" />
            Booking open
          </span>
        </div>
      </header>
      <main className="page">
        <h1>Unified Service Scheduler</h1>
        <p className="subtitle">
          Pick a service, a vehicle, and a time slot. Leave the technician on
          &ldquo;no preference&rdquo; and we assign a qualified, available
          one — or choose your own. Every slot shown is live: book the same
          one twice and the second request gets turned away.
        </p>
        <div className="grid">
          <BookingForm />
          <AppointmentLookup />
        </div>
      </main>
    </>
  );
}
