package vn.campvivo.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.cart.Cart;
import vn.campvivo.cart.CartItem;
import vn.campvivo.cart.CartRepository;
import vn.campvivo.catalog.Product;
import vn.campvivo.catalog.ProductRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ví dụ THAM CHIẾU cho luồng tạo đơn hàng — bất kỳ module nào khác chạm vào tồn kho
 * (vd. rental ở Tier 3) nên theo đúng pattern ở đây.
 *
 * Quy tắc áp dụng (xem docs/02-ky-thuat/database.md):
 * - BR-1: availableQuantity = stockQuantity - reservedQuantity
 * - BR-2: tạo đơn (PENDING) chỉ tăng reservedQuantity; CONFIRMED mới trừ thật stockQuantity
 * - Lock-ordering: khoá Product theo thứ tự productId tăng dần để tránh deadlock khi nhiều
 *   request đặt hàng song song đụng các sản phẩm chung
 */
@Service
public class OrderService {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    // TODO: inject OrderStatusHistoryRepository (BR-7), PromotionService (áp mã giảm giá),
    // PaymentService (tạo paymentUrl thật qua MoMo/VNPay) khi các module đó sẵn sàng.
    // Controller gọi OrderService; OrderService KHÔNG được gọi ngược lại Controller của module khác.

    public OrderService(CartRepository cartRepository,
                         ProductRepository productRepository,
                         OrderRepository orderRepository) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createOrder(Long userId, CreateOrderRequest request) {
        Cart cart = cartRepository.findByIdForUpdate(request.cartId())
                .orElseThrow(() -> new OutOfStockException("Giỏ hàng không tồn tại"));

        // Sắp xếp theo productId tăng dần TRƯỚC khi lock từng dòng — đây là phần quan trọng
        // nhất để tránh deadlock khi 2 request lock chung các sản phẩm theo thứ tự khác nhau.
        List<CartItem> sortedItems = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getProductId))
                .toList();

        if (sortedItems.isEmpty()) {
            throw new OutOfStockException("Giỏ hàng đang trống");
        }

        BigDecimal total = BigDecimal.ZERO;
        Order order = new Order(userId, generateOrderCode(), request.shippingAddressId().toString(),
                request.paymentMethod());

        for (CartItem item : sortedItems) {
            // SELECT ... FOR UPDATE — khoá đúng 1 dòng Product, không khoá cả bảng.
            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new OutOfStockException("Sản phẩm không tồn tại"));

            // Entity tự kiểm tra availableQuantity và throw nếu không đủ (xem Product.reserve).
            product.reserve(item.getQuantity());

            BigDecimal lineTotal = item.getPriceAtAdd().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(lineTotal);
            order.addItem(new OrderItem(item.getProductId(), item.getQuantity(), item.getPriceAtAdd()));
        }

        // TODO: áp PromotionService.validate(request.promotionCode(), total) nếu có mã (BR-5),
        // trừ vào discountAmount/finalAmount thay vì gán thẳng total ở dưới.
        order.setTotalAmount(total);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setFinalAmount(total);
        order.setPromotionCode(request.promotionCode());

        return orderRepository.save(order);
        // TODO: xoá cart_items sau khi tạo đơn thành công; gọi PaymentService để lấy paymentUrl
        // thật; ghi OrderStatusHistory dòng đầu tiên (BR-7).
    }

    /** Đơn PENDING -> CANCELLED: chỉ giải phóng reservedQuantity (BR-2), không đụng stock. */
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OutOfStockException("Đơn hàng không tồn tại"));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OutOfStockException("Chỉ có thể hủy đơn khi đang ở trạng thái PENDING");
        }
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findByIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new OutOfStockException("Sản phẩm không tồn tại"));
            product.release(item.getQuantity());
        }
        order.transitionTo(OrderStatus.CANCELLED);
        // TODO: ghi OrderStatusHistory (BR-7).
    }

    private String generateOrderCode() {
        return "ORD-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
