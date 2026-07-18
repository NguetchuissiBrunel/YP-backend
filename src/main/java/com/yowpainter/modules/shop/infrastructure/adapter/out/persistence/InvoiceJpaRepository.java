package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceJpaRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByOrderId(UUID orderId);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    @Query("SELECT i FROM Invoice i WHERE i.order.buyerId = :buyerId ORDER BY i.createdAt DESC")
    List<Invoice> findByBuyerId(@Param("buyerId") UUID buyerId);

    @Query("SELECT i FROM Invoice i WHERE i.order.organizationId = :organizationId ORDER BY i.createdAt DESC")
    List<Invoice> findByOrganizationId(@Param("organizationId") UUID organizationId);
}
