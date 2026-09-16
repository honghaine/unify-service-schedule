package com.keyloop.scheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Outbox row for the payment webhook flow. Written PENDING in the same
 * transaction as the order_id idempotency check, before the Kafka publish
 * is attempted, so a crash between "webhook received" and "event published"
 * leaves a row a retry job can pick back up instead of losing the event.
 */
@Entity
@Table(name = "payment_event")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentEventStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected PaymentEvent() {
    }

    public PaymentEvent(String orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentEventStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = PaymentEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentEventStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
