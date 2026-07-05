package com.yowpainter.modules.shop.infrastructure.adapter.out.persistence;

import com.yowpainter.modules.shop.domain.model.Order;
import com.yowpainter.modules.shop.domain.model.OrderStatus;
import com.yowpainter.modules.shop.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId) {
        return jpaRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId);
    }

    @Override
    public List<Order> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId) {
        return jpaRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }

    @Override
    public Optional<Order> findByKernelSalesOrderId(UUID kernelSalesOrderId) {
        return jpaRepository.findByKernelSalesOrderId(kernelSalesOrderId);
    }

    @Override
    public List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt) {
        return jpaRepository.findByStatusAndCreatedAtBefore(status, createdAt);
    }

    @Override
    public long countByStatus(OrderStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public BigDecimal sumTotalAmountByStatus(OrderStatus status) {
        return jpaRepository.sumTotalAmountByStatus(status);
    }
}
