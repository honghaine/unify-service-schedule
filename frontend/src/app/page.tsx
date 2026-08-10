import AppointmentLookup from "./components/AppointmentLookup";
import BookingForm from "./components/BookingForm";

export default function Home() {
  return (
    <main className="page">
      <h1>Unified Service Scheduler</h1>
      <p className="subtitle">
        Demo UI for the Keyloop Scenario A backend. Booking auto-assigns a
        qualified technician and free service bay, and rejects conflicting
        slots with a 409. Try booking vehicle 1 / OIL_CHANGE / Downtown twice
        for the same slot to see the conflict.
      </p>
      <div className="grid">
        <BookingForm />
        <AppointmentLookup />
      </div>
    </main>
  );
}
