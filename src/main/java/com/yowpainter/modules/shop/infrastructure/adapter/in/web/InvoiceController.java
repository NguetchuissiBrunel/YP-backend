package com.yowpainter.modules.shop.infrastructure.adapter.in.web;

import com.yowpainter.modules.artist.domain.model.Artist;
import com.yowpainter.modules.auth.domain.model.AppUser;
import com.yowpainter.modules.auth.domain.model.UserRole;
import com.yowpainter.modules.shop.application.service.InvoiceService;
import com.yowpainter.modules.shop.domain.model.Invoice;
import com.yowpainter.shared.security.AuthenticatedUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Invoices", description = "Endpoints de gestion et consultation des factures")
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @GetMapping("/my-purchases")
    @PreAuthorize("hasRole('BUYER') or hasRole('ARTIST')")
    @Operation(summary = "Consulter ses factures d'achat (Acheteur)")
    public ResponseEntity<List<Invoice>> getMyPurchases(Authentication authentication) {
        AppUser buyer = authenticatedUserResolver.requireUser(authentication);
        return ResponseEntity.ok(invoiceService.getInvoicesForBuyer(buyer.getId()));
    }

    @GetMapping("/my-sales")
    @PreAuthorize("hasRole('ARTIST')")
    @Operation(summary = "Consulter ses factures de vente (Artiste)")
    public ResponseEntity<List<Invoice>> getMySales(Authentication authentication) {
        Artist artist = authenticatedUserResolver.requireArtist(authentication);
        return ResponseEntity.ok(invoiceService.getInvoicesForArtist(artist));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulter les details d'une facture spécifique")
    public ResponseEntity<Invoice> getInvoice(@PathVariable UUID id, Authentication authentication) {
        AppUser user = authenticatedUserResolver.requireUser(authentication);
        Invoice invoice = invoiceService.getInvoice(id)
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable"));

        // Sécurité : Vérifier l'accès
        boolean isBuyer = (user.getRole() == UserRole.ROLE_BUYER && invoice.getOrder().getBuyerId().equals(user.getId()));
        boolean isArtist = false;
        
        if (user.getRole() == UserRole.ROLE_ARTIST) {
            Artist artist = authenticatedUserResolver.requireArtist(authentication);
            isArtist = artist.getOrganizationId() != null && artist.getOrganizationId().equals(invoice.getOrder().getOrganizationId());
        }

        if (!isBuyer && !isArtist) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(invoice);
    }

    @GetMapping("/{invoiceNumber}/pdf")
    @Operation(summary = "Telecharger la facture au format PDF")
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable String invoiceNumber) {
        // PDF fictif avec en-têtes valides pour l'intégration
        String dummyPdf = "%PDF-1.4\n" +
                "1 0 obj\n" +
                "<< /Title (Facture YowPainter) /Creator (YowPainter Backend) >>\n" +
                "endobj\n" +
                "2 0 obj\n" +
                "<< /Type /Catalog /Pages 3 0 R >>\n" +
                "endobj\n" +
                "3 0 obj\n" +
                "<< /Type /Pages /Kids [4 0 R] /Count 1 >>\n" +
                "endobj\n" +
                "4 0 obj\n" +
                "<< /Type /Page /Parent 3 0 R /MediaBox [0 0 595 842] /Contents 5 0 R >>\n" +
                "endobj\n" +
                "5 0 obj\n" +
                "<< /Length 120 >>\n" +
                "stream\n" +
                "BT\n" +
                "/F1 24 Tf\n" +
                "100 700 Td\n" +
                "(FACTURE YOWPAINTER: " + invoiceNumber + ") Tj\n" +
                "ET\n" +
                "endstream\n" +
                "endobj\n" +
                "xref\n" +
                "0 6\n" +
                "0000000000 65535 f\n" +
                "trailer\n" +
                "<< /Size 6 /Root 2 0 R >>\n" +
                "startxref\n" +
                "450\n" +
                "%%EOF";
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=" + invoiceNumber + ".pdf")
                .body(dummyPdf.getBytes());
    }
}
