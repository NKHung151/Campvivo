package vn.campvivo.order.admin;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.order.Order;
import vn.campvivo.order.OrderItem;
import vn.campvivo.order.OrderRepository;
import vn.campvivo.order.OrderShipmentService;
import vn.campvivo.order.OrderStateService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.admin.AdminOrderResponses.Customer;
import vn.campvivo.order.admin.AdminOrderResponses.Detail;
import vn.campvivo.order.admin.AdminOrderResponses.Query;
import vn.campvivo.order.admin.AdminOrderResponses.StatusChange;
import vn.campvivo.order.admin.AdminOrderResponses.StatusRequest;
import vn.campvivo.order.admin.AdminOrderResponses.Summary;
import vn.campvivo.order.admin.AdminOrderResponses.Tracking;
import vn.campvivo.order.admin.AdminOrderResponses.TrackingLog;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentService;
import vn.campvivo.payment.PaymentStatus;
import vn.campvivo.shipping.GhnOrderDetail;
import vn.campvivo.shipping.GhnService;
import vn.campvivo.user.UserService;
import vn.campvivo.user.dto.UserProfileResponse;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Xử lý đơn của Employee/Admin (FR-13, database.md mục 5.1, user-flows mục 7):
 * PENDING + COD → xác nhận (CONFIRMED, trừ kho, tự tạo vận đơn GHN) hoặc huỷ (bắt buộc ghi chú);
 * PENDING + VNPay → không thao tác (chờ IPN/Return URL hoặc job hết hạn); CONFIRMED → SHIPPING → COMPLETED.
 * Đổi trạng thái luôn khoá đơn trước rồi mới kiểm, nên hai nhân viên bấm cùng lúc chỉ một người thành công.
 */
@Service
public class AdminOrderService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Giới hạn số khách khớp từ khoá khi tìm đơn theo email/họ tên. */
    private static final int MAX_CUSTOMER_MATCHES = 500;

    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final OrderShipmentService shipmentService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final GhnService ghnService;

    public AdminOrderService(OrderRepository orderRepository, OrderStateService orderStateService,
                             OrderShipmentService shipmentService, PaymentService paymentService,
                             UserService userService, GhnService ghnService) {
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.shipmentService = shipmentService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.ghnService = ghnService;
    }

    /** Trạng thái nhân viên được chuyển tới từ trạng thái hiện tại của đơn. */
    static Set<OrderStatus> staffTargets(Order order) {
        return switch (order.getStatus()) {
            case PENDING -> order.getPaymentMethod() == PaymentMethod.COD
                    ? EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED)
                    : EnumSet.noneOf(OrderStatus.class);
            case CONFIRMED -> EnumSet.of(OrderStatus.SHIPPING);
            case SHIPPING -> EnumSet.of(OrderStatus.COMPLETED);
            default -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(Query q) {
        String keyword = q.q() == null ? "" : q.q().trim();
        // Tra khách theo email/họ tên một lần (lambda Specification chạy cho cả câu đếm lẫn câu lấy dữ liệu)
        List<Long> customerIds = keyword.isEmpty() ? List.of() : userService.findIdsByKeyword(keyword, MAX_CUSTOMER_MATCHES);
        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (q.status() != null) {
                and.add(cb.equal(root.get("status"), q.status()));
            }
            if (q.paymentMethod() != null) {
                and.add(cb.equal(root.get("paymentMethod"), q.paymentMethod()));
            }
            if (q.from() != null) {
                and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), q.from().atStartOfDay(VN).toOffsetDateTime()));
            }
            if (q.to() != null) {
                and.add(cb.lessThan(root.get("createdAt"), q.to().plusDays(1).atStartOfDay(VN).toOffsetDateTime()));
            }
            if (!keyword.isEmpty()) {
                String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                List<Predicate> or = new ArrayList<>();
                or.add(cb.like(cb.lower(root.get("orderCode")), like));
                or.add(cb.like(root.get("recipientPhone"), "%" + keyword + "%"));
                if (!customerIds.isEmpty()) {
                    or.add(root.get("userId").in(customerIds));
                }
                and.add(cb.or(or.toArray(Predicate[]::new)));
            }
            return cb.and(and.toArray(Predicate[]::new));
        };
        Page<Order> page = orderRepository.findAll(spec,
                PageRequest.of(q.page() - 1, q.limit(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        List<Long> ids = page.getContent().stream().map(Order::getId).toList();
        Map<Long, PaymentStatus> payments = paymentService.statusesByOrderIds(ids);
        Map<Long, UserProfileResponse> customers = userService.profiles(
                page.getContent().stream().map(Order::getUserId).distinct().toList());
        return PageResponse.of(page.map(o -> {
            OrderItem first = o.getItems().isEmpty() ? null : o.getItems().get(0);
            return new Summary(o.getId(), o.getOrderCode(), o.getStatus(), o.getPaymentMethod(), payments.get(o.getId()),
                    o.getFinalAmount(), o.totalQuantity(), first == null ? null : first.getItemName(),
                    first == null ? null : first.getItemImageUrl(), customer(customers.get(o.getUserId())),
                    o.getRecipientName(), o.getRecipientPhone(), o.getShippingOrderCode(), o.getCreatedAt());
        }));
    }

    @Transactional(readOnly = true)
    public Detail detail(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return toDetail(order);
    }

    @Transactional
    public StatusChange changeStatus(Long orderId, StatusRequest req, Long staffId) {
        Order order = orderStateService.lock(orderId);
        OrderStatus from = order.getStatus();
        if (!staffTargets(order).contains(req.status())) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (req.status() == OrderStatus.CANCELLED && note == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Vui lòng nhập lý do huỷ đơn.",
                    Map.of("fieldErrors", Map.of("note", "Vui lòng nhập lý do huỷ đơn")));
        }
        if (req.status() == OrderStatus.SHIPPING && note == null && order.getShippingOrderCode() != null) {
            note = "Mã vận đơn GHN: " + order.getShippingOrderCode();
        }
        orderStateService.apply(order, req.status(), staffId, note);
        return new StatusChange(order.getId(), from, order.getStatus(), order.getUpdatedAt());
    }

    /**
     * Tạo (lại) vận đơn GHN — dùng khi lần tự động trước lỗi. Lỗi GHN được báo về giao diện; transaction vẫn commit
     * (noRollbackFor) để lưu lại `shipping_order_error`.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Detail createShipment(Long orderId) {
        return toDetail(shipmentService.createShipment(orderId, true));
    }

    /** Hành trình vận đơn hiện tại trên GHN. Đơn chưa có vận đơn → NOT_FOUND. */
    @Transactional(readOnly = true)
    public Tracking tracking(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (order.getShippingOrderCode() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng chưa có vận đơn.", null);
        }
        GhnOrderDetail d = ghnService.getOrderDetail(order.getShippingOrderCode());
        return new Tracking("GHN", d.orderCode(), d.status(), d.expectedDeliveryTime(), d.updatedAt(),
                d.log().stream().map(l -> new TrackingLog(l.status(), l.updatedAt())).toList());
    }

    private Detail toDetail(Order order) {
        UserProfileResponse profile = userService.profiles(List.of(order.getUserId())).get(order.getUserId());
        OrderDetailResponse detail = OrderDetailResponse.from(order, paymentService.findByOrderId(order.getId()).orElse(null));
        boolean canShip = order.getShippingOrderCode() == null
                && (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.SHIPPING);
        return new Detail(detail, customer(profile), List.copyOf(staffTargets(order)), canShip);
    }

    private static Customer customer(UserProfileResponse p) {
        return p == null ? null : new Customer(p.id(), p.email(), p.fullName(), p.phone());
    }
}
