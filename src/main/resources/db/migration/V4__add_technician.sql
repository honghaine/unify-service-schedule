-- Dealership 2 had OIL_CHANGE and BRAKES coverage but no TIRE_ROTATION
-- technician (dealership 1 has all three) — fills that gap rather than
-- adding a second technician to an already-covered specialty, so no
-- existing test's "exactly one qualified technician" assumption
-- (BookingServiceConcurrencyTest, the explicit-technician tests) breaks.
INSERT INTO technician (id, name, specialty, dealership_id) VALUES
    (6, 'Riley Chen', 'TIRE_ROTATION', 2);
