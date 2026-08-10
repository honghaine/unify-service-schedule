-- Dealership ids are not modeled as a separate table (out of scope for this
-- service); 1 = "Downtown Keyloop Motors", 2 = "Uptown Keyloop Motors".

INSERT INTO customer (id, name, email) VALUES
    (1, 'Alice Nguyen', 'alice.nguyen@example.com'),
    (2, 'Ben Carter', 'ben.carter@example.com'),
    (3, 'Chloe Diaz', 'chloe.diaz@example.com');

INSERT INTO vehicle (id, vin, make, model, customer_id) VALUES
    (1, '1HGCM82633A004352', 'Honda', 'Accord', 1),
    (2, '1FTFW1ET4EFA12345', 'Ford', 'F-150', 2),
    (3, 'WBA3B1C50FP123456', 'BMW', '3 Series', 3);

INSERT INTO technician (id, name, specialty, dealership_id) VALUES
    (1, 'Sam Rivera', 'OIL_CHANGE', 1),
    (2, 'Jordan Lee', 'BRAKES', 1),
    (3, 'Taylor Brooks', 'TIRE_ROTATION', 1),
    (4, 'Morgan Blake', 'OIL_CHANGE', 2),
    (5, 'Casey Kim', 'BRAKES', 2);

INSERT INTO service_bay (id, dealership_id, bay_number) VALUES
    (1, 1, 'A1'),
    (2, 1, 'A2'),
    (3, 2, 'B1');
