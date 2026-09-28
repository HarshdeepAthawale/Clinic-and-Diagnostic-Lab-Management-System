package com.cdlms.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByConsultationId(UUID consultationId);

    Optional<Invoice> findByLabOrderId(UUID labOrderId);

    /** The invoice billing an ordered test (a visit invoice or a lab-only one). */
    @Query(value = """
            SELECT i.* FROM invoices i
            JOIN invoice_items l ON l.invoice_id = i.id
            WHERE l.lab_order_item_id = :labOrderItemId
            """, nativeQuery = true)
    Optional<Invoice> findByLabOrderItemId(@Param("labOrderItemId") UUID labOrderItemId);

    /** Next number in the INV sequence, formatted like {@code INV-000123}. */
    @Query(value = "SELECT 'INV-' || lpad(nextval('invoice_code_seq')::text, 6, '0')", nativeQuery = true)
    String nextCode();
}
