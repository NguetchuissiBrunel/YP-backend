package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Invoice;
import com.yowpainter.modules.shop.domain.port.out.InvoiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InvoiceRepositoryAdapter implements InvoiceRepositoryPort {

    private final InvoiceJpaRepository jpaRepository;

    @Override
    public Invoice save(Invoice invoice) {
        return jpaRepository.save(invoice);
    }

    @Override
    public Optional<Invoice> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Invoice> findByOrderId(UUID orderId) {
        return jpaRepository.findByOrderId(orderId);
    }

    @Override
    public Optional<Invoice> findByInvoiceNumber(String invoiceNumber) {
        return jpaRepository.findByInvoiceNumber(invoiceNumber);
    }

    @Override
    public List<Invoice> findByBuyerId(UUID buyerId) {
        return jpaRepository.findByBuyerId(buyerId);
    }

    @Override
    public List<Invoice> findByOrganizationId(UUID organizationId) {
        return jpaRepository.findByOrganizationId(organizationId);
    }
}
