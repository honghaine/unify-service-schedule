CREATE TABLE customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT uk_customer_email UNIQUE (email)
);

CREATE TABLE vehicle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vin VARCHAR(17) NOT NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    customer_id BIGINT NOT NULL,
    CONSTRAINT uk_vehicle_vin UNIQUE (vin),
    CONSTRAINT fk_vehicle_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

-- dealership_id scopes a technician to the dealership they work out of, so
-- auto-assignment never pairs a technician from one dealership with a bay
-- at another.
CREATE TABLE technician (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialty VARCHAR(100) NOT NULL,
    dealership_id BIGINT NOT NULL
);

CREATE TABLE service_bay (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dealership_id BIGINT NOT NULL,
    bay_number VARCHAR(50) NOT NULL,
    CONSTRAINT uk_service_bay_dealership_number UNIQUE (dealership_id, bay_number)
);

CREATE TABLE appointment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    technician_id BIGINT NOT NULL,
    service_bay_id BIGINT NOT NULL,
    service_type VARCHAR(100) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_appointment_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_appointment_technician FOREIGN KEY (technician_id) REFERENCES technician (id),
    CONSTRAINT fk_appointment_service_bay FOREIGN KEY (service_bay_id) REFERENCES service_bay (id),
    CONSTRAINT chk_appointment_time_range CHECK (end_time > start_time)
);

-- Overlap checks filter by technician/bay + status then scan the time range,
-- so both lookups need a composite index leading with the resource column.
CREATE INDEX idx_appointment_technician_window ON appointment (technician_id, status, start_time, end_time);
CREATE INDEX idx_appointment_bay_window ON appointment (service_bay_id, status, start_time, end_time);
