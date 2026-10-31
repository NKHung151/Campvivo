package vn.campvivo.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.cart.CartService;
import vn.campvivo.cart.CartService.CheckoutLine;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductService;
import vn.campvivo.combo.ComboService;
import vn.campvivo.combo.ProductCombo;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.inventory.InventoryService.StockItems;
import vn.campvivo.inventory.StockLine;
import vn.campvivo.order.dto.CreateOrderResponse;
import vn.campvivo.order.dto.OrderPreviewResponse;
import vn.campvivo.order.dto.OrderRequests;
import vn.campvivo.order.event.OrderStatusChangedEvent;
import vn.campvivo.payment.PaymentGateway.PaymentRequest;
import vn.campvivo.payment.PaymentService;
import vn.campvivo.promotion.PricingService;
import vn.campvivo.promotion.VoucherService;
import vn.campvivo.shipping.ShippingService;
import vn.campvivo.shipping.dto.ShippingFeeResponse;
import vn.campvivo.user.UserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xem trước và tạo đơn (FR-10, database.md mục 6). Giá tính bằng PricingService (BR-4/BR-6), voucher qua VoucherService
 * (BR-5), phí ship qua ShippingService (D-27), tồn kho qua InventoryService (BR-1/BR-2), thanh toán qua PaymentService.
 * Mọi lỗi nghiệp vụ trong lúc tạo đơn → rollback toàn bộ (không có đơn "nửa vời").
 */
@Service
public class OrderService {

    private final CartService cartService;
    private final InventoryService inventoryService;
    private final PricingService pricingService;
    private final VoucherService voucherService;
    private final ShippingService shippingService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final ProductService productService;
    private final ComboService comboService;
    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final OrderCodeGenerator codeGenerator;
    private final ApplicationEventPublisher events;
    private final AppProperties.Order orderProps;
    private final Clock clock;

    public OrderService(CartService cartService, InventoryService inventoryService, PricingService pricingService,
                        VoucherService voucherService, ShippingService shippingService, PaymentService paymentService,
                        UserService userService, ProductService productService, ComboService comboService,
                        OrderRepository orderRepository, OrderStateService orderStateService,
                        OrderCodeGenerator codeGenerator,
                        ApplicationEventPublisher events, AppProperties appProperties, Clock clock) {
        this.cartService = cartService;
        this.inventoryService = inventoryService;
        this.pricingService = pricingService;
        this.voucherService = voucherService;
        this.shippingService = shippingService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.productService = productService;
        this.comboService = comboService;
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.codeGenerator = codeGenerator;
        this.events = events;
        this.orderProps = appProperties.order();
        this.clock = clock;
    }

    /** Một dòng đã tính giá từ dữ liệu mặt hàng hiện tại. */
    private record PricedLine(CheckoutLine source, String type, Long itemId, String name, BigDecimal originalPrice,
                              BigDecimal unitPrice, boolean active) {
        BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(source.quantity()));
        }
    }

    private record Totals(List<PricedLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee,
                          BigDecimal finalAmount, boolean shippingCalculated, boolean freeShipping, String voucherCode) {}

    // ------------------------------------------------------------------ xem trước

    @Transactional(readOnly = true)
    public OrderPreviewResponse preview(Long userId, OrderRequests.Preview req) {
        List<CheckoutLine> lines = cartService.checkoutLines(userId, req.cartItemIds());
        List<PricedLine> priced = price(lines, load(lines, false));
        BigDecimal subtotal = subtotal(priced);
        BigDecimal discount = voucherService.discountFor(req.voucherCode(), subtotal, userId);
        boolean hasAddress = req.shippingDistrictId() != null && req.shippingWardCode() != null
                && !req.shippingWardCode().isBlank();
        long orderValue = subtotal.subtract(discount).longValueExact();
        long shippingFee = hasAddress
                ? shippingService.quote(req.shippingDistrictId(), req.shippingWardCode().trim(), quantity(lines), orderValue).fee()
                : 0;
        return toPreview(totals(priced, subtotal, discount, shippingFee, hasAddress, req.voucherCode()));
    }

    // ------------------------------------------------------------------ tạo đơn

    @Transactional
    public CreateOrderResponse create(Long userId, OrderRequests.Create req, String clientIp) {
        List<CheckoutLine> lines = cartService.checkoutLines(userId, req.cartItemIds());
        String wardCode = req.shippingWardCode().trim();

        // 1. Hỏi phí GHN TRƯỚC khi khoá tồn kho (không giữ khoá trong lúc gọi dịch vụ ngoài)
        BigDecimal estimatedSubtotal = subtotal(price(lines, load(lines, false)));
        BigDecimal estimatedDiscount = voucherService.discountFor(req.voucherCode(), estimatedSubtotal, userId);
        ShippingFeeResponse ship = shippingService.quote(req.shippingDistrictId(), wardCode, quantity(lines),
                estimatedSubtotal.subtract(estimatedDiscount).longValueExact());

        // 2. Khoá sản phẩm (id tăng dần) rồi combo (id tăng dần), tính lại giá trên dữ liệu đã khoá (BR-4, BR-14)
        List<PricedLine> priced = price(lines, load(lines, true));
        List<Map<String, Object>> inactive = priced.stream().filter(p -> !p.active())
                .map(p -> InventoryService.shortage(p.type(), p.itemId(), p.name(), 0)).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_INACTIVE,
                    "\"" + inactive.get(0).get("name") + "\" đã ngừng kinh doanh. Vui lòng bỏ khỏi giỏ hàng.",
                    Map.of("items", inactive));
        }
        BigDecimal subtotal = subtotal(priced);
        // Voucher: khi có module promotion, khoá voucher (sau products/combos) và trừ lượt ở đây (BR-5)
        BigDecimal discount = voucherService.discountFor(req.voucherCode(), subtotal, userId);
        long shippingFee = shippingService.customerFee(ship.carrierFee(), subtotal.subtract(discount).longValueExact());
        Totals totals = totals(priced, subtotal, discount, shippingFee, true, req.voucherCode());

        // 3. So với số tiền khách đang thấy (D-04)
        if (totals.finalAmount().compareTo(BigDecimal.valueOf(req.expectedTotal())) != 0) {
            throw new BusinessException(ErrorCode.PRICE_CHANGED,
                    "Giá đã thay đổi, tổng mới là " + totals.finalAmount().toPlainString() + " đ. Vui lòng xác nhận lại.",
                    toPreview(totals));
        }

        // 4. Giữ chỗ tồn kho (thiếu → OUT_OF_STOCK kèm danh sách)
        inventoryService.reserve(lines.stream().map(l -> l.productId() != null
                ? StockLine.product(l.productId(), l.quantity()) : StockLine.combo(l.comboId(), l.quantity())).toList());

        // 5. Tạo đơn + dòng (snapshot) + lịch sử, bản ghi thanh toán PENDING
        OffsetDateTime now = OffsetDateTime.now(clock);
        Order order = new Order(codeGenerator.next(), userId, blankToNull(req.voucherCode()),
                new Order.Amounts(totals.subtotal(), totals.discount(), totals.shippingFee(), totals.finalAmount()),
                new Order.Recipient(req.recipientName().trim(), req.recipientPhone().trim(), req.shippingAddress().trim(),
                        req.shippingDistrictId(), wardCode),
                req.paymentMethod(), blankToNull(req.note()), now);
        Map<String, String> images = imageUrls(lines);
        for (PricedLine p : priced) {
            CheckoutLine l = p.source();
            order.addItem(l.productId(), l.comboId(), p.name(), images.get(p.type() + ":" + p.itemId()),
                    p.originalPrice(), p.unitPrice(), l.quantity());
        }
        orderRepository.saveAndFlush(order);
        paymentService.createFor(order.getId(), order.getPaymentMethod(), order.getFinalAmount());

        // 6. Dọn giỏ, lưu địa chỉ mặc định
        cartService.removeItems(userId, lines.stream().map(CheckoutLine::cartItemId).toList());
        if (req.saveAsDefault()) {
            userService.updateDefaultContact(userId, order.getRecipientPhone(), order.getShippingAddress());
        }

        // 7. Cổng online: tạo URL thanh toán (lỗi → PAYMENT_GATEWAY_ERROR, rollback cả đơn)
        String paymentUrl = null;
        if (paymentService.requiresGateway(order.getPaymentMethod())) {
            paymentUrl = paymentService.createPaymentUrl(order.getPaymentMethod(), new PaymentRequest(
                    order.getOrderCode(), order.getFinalAmount(), "Thanh toan don hang " + order.getOrderCode(),
                    clientIp, now, now.plus(orderProps.paymentTimeout())));
        }
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), null, OrderStatus.PENDING, userId));
        return new CreateOrderResponse(order.getId(), order.getOrderCode(), order.getStatus(), order.getFinalAmount(),
                order.getPaymentMethod(), paymentUrl);
    }

    // ------------------------------------------------------------------ khách huỷ đơn

    /**
     * Khách huỷ đơn của chính mình khi còn PENDING (FR-12): giải phóng giữ chỗ (BR-2). Đơn người khác → NOT_FOUND;
     * không còn PENDING (vừa được thanh toán/xác nhận) → ORDER_NOT_CANCELLABLE.
     */
    @Transactional
    public void cancelByCustomer(Long userId, Long orderId, String reason) {
        Order order = orderStateService.lock(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        String note = reason == null || reason.isBlank() ? "Khách huỷ đơn" : "Khách huỷ đơn: " + reason.trim();
        orderStateService.apply(order, OrderStatus.CANCELLED, userId, note);
    }

    // ------------------------------------------------------------------ tính toán

    private StockItems load(List<CheckoutLine> lines, boolean lock) {
        List<Long> productIds = lines.stream().filter(l -> l.productId() != null).map(CheckoutLine::productId).toList();
        List<Long> comboIds = lines.stream().filter(l -> l.comboId() != null).map(CheckoutLine::comboId).toList();
        return lock ? inventoryService.lock(productIds, comboIds) : inventoryService.load(productIds, comboIds);
    }

    private List<PricedLine> price(List<CheckoutLine> lines, StockItems items) {
        List<PricedLine> out = new ArrayList<>();
        for (CheckoutLine l : lines) {
            if (l.productId() != null) {
                Product p = items.products().get(l.productId());
                if (p == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                out.add(new PricedLine(l, "PRODUCT", p.getId(), p.getName(), p.getPrice(), pricingService.unitPrice(p), p.isActive()));
            } else {
                ProductCombo c = items.combos().get(l.comboId());
                if (c == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                out.add(new PricedLine(l, "COMBO", c.getId(), c.getName(), c.getComboPrice(), pricingService.unitPrice(c), c.isActive()));
            }
        }
        return out;
    }

    private Totals totals(List<PricedLine> priced, BigDecimal subtotal, BigDecimal discount, long shippingFee,
                          boolean shippingCalculated, String voucherCode) {
        BigDecimal fee = BigDecimal.valueOf(shippingFee);
        boolean free = shippingCalculated && shippingService.isFree(subtotal.subtract(discount).longValueExact());
        return new Totals(priced, subtotal, discount, fee, subtotal.subtract(discount).add(fee), shippingCalculated, free,
                blankToNull(voucherCode));
    }

    private OrderPreviewResponse toPreview(Totals t) {
        List<OrderPreviewResponse.Item> items = t.lines().stream()
                .map(p -> new OrderPreviewResponse.Item(p.source().cartItemId(), p.type(), p.itemId(), p.name(),
                        p.source().quantity(), p.originalPrice(), p.unitPrice(), p.lineTotal()))
                .toList();
        return new OrderPreviewResponse(items, t.subtotal(), t.discount(), t.shippingFee(), t.finalAmount(),
                t.voucherCode(), t.shippingCalculated(), t.freeShipping(),
                BigDecimal.valueOf(shippingService.freeThreshold()));
    }

    private Map<String, String> imageUrls(List<CheckoutLine> lines) {
        Map<String, String> out = new LinkedHashMap<>();
        productService.primaryImageUrls(lines.stream().filter(l -> l.productId() != null).map(CheckoutLine::productId).toList())
                .forEach((id, url) -> out.put("PRODUCT:" + id, url));
        comboService.coverImageUrls(lines.stream().filter(l -> l.comboId() != null).map(CheckoutLine::comboId).toList())
                .forEach((id, url) -> out.put("COMBO:" + id, url));
        return out;
    }

    private static BigDecimal subtotal(List<PricedLine> priced) {
        return priced.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int quantity(List<CheckoutLine> lines) {
        return lines.stream().mapToInt(CheckoutLine::quantity).sum();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
