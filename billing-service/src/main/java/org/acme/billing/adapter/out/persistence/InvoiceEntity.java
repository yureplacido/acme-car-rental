package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.InvoiceStatus;

import java.util.List;

@Entity
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(columnNames = "reservation_id"))
public class InvoiceEntity extends PanacheEntity {

    @Column(name = "customer_id")
    public String customerId;

    @Column(name = "reservation_id")
    public String reservationId;

    @Enumerated(EnumType.STRING)
    public InvoiceStatus status;

    @Convert(converter = InvoiceLinesConverter.class)
    public List<InvoiceLine> lines;
}