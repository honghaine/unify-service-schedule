import AppointmentLookup from "./components/AppointmentLookup";
import BookingForm from "./components/BookingForm";

export default function Home() {
  return (
    <main className="page">
      <h1>Unified Service Scheduler</h1>
      <p className="subtitle">
        Demo UI for the Keyloop Scenario A backend. Pick a service, vehicle,
        date and time slot — leave the technician on &ldquo;no
        preference&rdquo; and the backend auto-assigns a qualified,
        available one, or pick a specific technician yourself. Book the
        same slot twice to see the 409 conflict.
      </p>
      <div className="grid">
        <BookingForm />
        <AppointmentLookup />
      </div>
    </main>
  );
}
