-- Outbox table for the payment webhook flow: the row is written in the same
-- transaction as the idempotency check, before the Kafka publish attempt, so
-- a crash between "webhook received" and "event published" leaves a PENDING
-- row that can be republished instead of silently losing the event.
CREATE TABLE payment_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    CONSTRAINT uk_payment_event_order_id UNIQUE (order_id)
);
