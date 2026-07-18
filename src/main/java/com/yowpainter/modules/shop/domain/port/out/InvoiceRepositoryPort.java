package com.yowpainter.modules.shop.domain.port.out;

import com.yowpainter.modules.shop.domain.model.Invoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepositoryPort {
    Invoice save(Invoice invoice);
    Optional<Invoice> findById(UUID id);
    Optional<Invoice> findByOrderId(UUID orderId);
    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);
    List<Invoice> findByBuyerId(UUID buyerId);
    List<Invoice> findByOrganizationId(UUID organizationId);
}
