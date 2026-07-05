package com.yowpainter.modules.shop.domain.port.out;

import com.yowpainter.modules.shop.domain.model.Order;
import com.yowpainter.modules.shop.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<Order> findByKernelSalesOrderId(UUID kernelSalesOrderId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt);

    long countByStatus(OrderStatus status);

    BigDecimal sumTotalAmountByStatus(OrderStatus status);
}
