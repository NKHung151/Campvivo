package vn.campvivo.order.dto;

import vn.campvivo.order.OrderStatus;
import vn.campvivo.payment.PaymentMethod;

import java.math.BigDecimal;

/** POST /orders → 201 (api.md 4.5). `paymentUrl` khác null với VNPay — frontend chuyển khách sang đó. */
public record CreateOrderResponse(Long orderId, String orderCode, OrderStatus status, BigDecimal finalAmount,
                                  PaymentMethod paymentMethod, String paymentUrl) {}
