package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
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
@Table(name = "invoice", uniqueConstraints = @UniqueConstraint(columnNames = "reservationId"))
public class InvoiceEntity extends PanacheEntity {

    public String customerId;
    public String reservationId;

    @Enumerated(EnumType.STRING)
    public InvoiceStatus status;

    @Convert(converter = InvoiceLinesConverter.class)
    public List<InvoiceLine> lines;
}