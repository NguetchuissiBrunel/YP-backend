package com.yowpainter.modules.admin.infrastructure.adapter.in.web;

import com.yowpainter.shared.kernel.KernelBootstrapAdminSession;
import com.yowpainter.shared.kernel.port.KernelWalletPort;
import com.yowpainter.shared.kernel.adapter.dto.KernelTransactionResponseDto;
import com.yowpainter.shared.kernel.adapter.dto.KernelChainValidationReportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/blockchain")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Blockchain Audit", description = "Endpoints d'audit et validation de la blockchain (Restreint aux Admins)")
public class AdminBlockchainController {

    private final KernelWalletPort kernelWalletPort;
    private final KernelBootstrapAdminSession bootstrapAdminSession;

    @GetMapping("/transactions")
    @Operation(summary = "Lister les transactions de la blockchain pour un tenant")
    public ResponseEntity<List<KernelTransactionResponseDto>> listTransactions(
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "COMOPS_MAIN") String chainCode) {
        String adminToken = bootstrapAdminSession.getBootstrapAdminAccessToken();
        return ResponseEntity.ok(kernelWalletPort.listTransactions(organizationId, chainCode, adminToken));
    }

    @GetMapping("/validate")
    @Operation(summary = "Valider l'integrite de la chaine de blocs d'un tenant")
    public ResponseEntity<KernelChainValidationReportDto> validateChain(
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "COMOPS_MAIN") String chainCode) {
        String adminToken = bootstrapAdminSession.getBootstrapAdminAccessToken();
        return ResponseEntity.ok(kernelWalletPort.validateChain(organizationId, chainCode, adminToken));
    }
}
