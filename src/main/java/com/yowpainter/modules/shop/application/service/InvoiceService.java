package com.yowpainter.modules.shop.application.service;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.shop.domain.model.Invoice;
import com.yowpainter.modules.shop.domain.model.Order;
import com.yowpainter.modules.shop.domain.port.out.InvoiceRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepositoryPort invoiceRepository;

    @Transactional
    public Invoice createInvoice(Order order) {
        log.info("Generating invoice for order: {}", order.getId());
        
        String invoiceNumber = String.format("INV-%s-%s", 
                System.currentTimeMillis(), 
                UUID.randomUUID().toString().substring(0, 4).toUpperCase());

        Invoice invoice = Invoice.builder()
                .order(order)
                .invoiceNumber(invoiceNumber)
                .amount(order.getTotalAmount())
                .currency("XAF")
                .status("PENDING")
                .pdfUrl("/api/invoices/" + invoiceNumber + "/pdf")
                .build();

        return invoiceRepository.save(invoice);
    }

    public Optional<Invoice> getInvoice(UUID id) {
        return invoiceRepository.findById(id);
    }

    public Optional<Invoice> getInvoiceByOrderId(UUID orderId) {
        return invoiceRepository.findByOrderId(orderId);
    }

    public List<Invoice> getInvoicesForBuyer(UUID buyerId) {
        return invoiceRepository.findByBuyerId(buyerId);
    }

    public List<Invoice> getInvoicesForArtist(Artist artist) {
        if (artist.getOrganizationId() == null) {
            return List.of();
        }
        return invoiceRepository.findByOrganizationId(artist.getOrganizationId());
    }

    @Transactional
    public void markAsPaid(Invoice invoice) {
        invoice.setStatus("PAID");
        invoiceRepository.save(invoice);
    }

    @Transactional
    public void markAsCancelled(Invoice invoice) {
        invoice.setStatus("CANCELLED");
        invoiceRepository.save(invoice);
    }
}
