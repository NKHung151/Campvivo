package vn.campvivo.order.returns;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.upload.ImageUploadService;
import vn.campvivo.inventory.InventoryService;
import vn.campvivo.order.Order;
import vn.campvivo.order.OrderItem;
import vn.campvivo.order.OrderRepository;
import vn.campvivo.order.OrderStateService;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.admin.AdminOrderResponses.Customer;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.order.dto.OrderDetailResponse.ReturnInfo;
import vn.campvivo.order.returns.dto.AdminReturnResponses;
import vn.campvivo.order.returns.dto.ReturnRequestResponse;
import vn.campvivo.payment.PaymentService;
import vn.campvivo.user.UserService;
import vn.campvivo.user.dto.UserProfileResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Trả hàng (FR-19, BR-9, D-07, D-41): trả nguyên đơn, mỗi đơn tối đa 1 yêu cầu.
 * <ul>
 *   <li>Khách: đơn của mình, COMPLETED, chưa quá `app.return.window-days` kể từ lúc hoàn tất → chọn lý do, mô tả (không
 *       bắt buộc), 1–{@value #MAX_IMAGES} ảnh → yêu cầu PENDING.</li>
 *   <li>Nhân viên: duyệt → cộng lại tồn kho (nhật ký RETURN), đơn COMPLETED → RETURNED, yêu cầu APPROVED; hoặc từ chối
 *       (bắt buộc ghi chú) → đơn giữ COMPLETED, khách không gửi lại được. Hoàn tiền làm ngoài hệ thống.</li>
 * </ul>
 * Thứ tự khoá: orders → return_requests → products → product_combos → product_variants.
 */
@Service
public class ReturnService {

    public static final int MAX_IMAGES = 2;
    public static final int MAX_DESCRIPTION = 2000;

    private final ReturnRequestRepository repository;
    private final OrderRepository orderRepository;
    private final OrderStateService orderStateService;
    private final InventoryService inventoryService;
    private final ImageUploadService imageUploadService;
    private final PaymentService paymentService;
    private final UserService userService;
    private final TransactionTemplate tx;
    private final int windowDays;
    private final Clock clock;

    public ReturnService(ReturnRequestRepository repository, OrderRepository orderRepository,
                         OrderStateService orderStateService, InventoryService inventoryService,
                         ImageUploadService imageUploadService, PaymentService paymentService, UserService userService,
                         PlatformTransactionManager transactionManager, AppProperties appProperties, Clock clock) {
        this.repository = repository;
        this.orderRepository = orderRepository;
        this.orderStateService = orderStateService;
        this.inventoryService = inventoryService;
        this.imageUploadService = imageUploadService;
        this.paymentService = paymentService;
        this.userService = userService;
        this.tx = new TransactionTemplate(transactionManager);
        this.windowDays = appProperties.returns().windowDays();
        this.clock = clock;
    }

    // ------------------------------------------------------------------ khách

    /**
     * Gửi yêu cầu trả hàng. Kiểm điều kiện trước khi tải ảnh lên Cloudinary (không giữ khoá đơn trong lúc gọi dịch vụ
     * ngoài), rồi khoá đơn và kiểm lại trước khi lưu. Đơn người khác → NOT_FOUND.
     */
    public ReturnRequestResponse create(Long userId, Long orderId, String reasonCode, String description,
                                        List<MultipartFile> images) {
        ReturnReason reason = parseReason(reasonCode);
        String text = description == null || description.isBlank() ? null : description.trim();
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw fieldError("description", "Mô tả tối đa " + MAX_DESCRIPTION + " ký tự.");
        }
        List<MultipartFile> files = images == null ? List.of() : images.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (files.isEmpty() || files.size() > MAX_IMAGES) {
            throw fieldError("images", "Vui lòng chụp hoặc tải lên 1–" + MAX_IMAGES + " ảnh sản phẩm.");
        }
        tx.executeWithoutResult(s -> ensureCanReturn(ownedOrder(userId, orderId)));

        List<String> urls = files.stream().map(f -> imageUploadService.upload(f, "returns")).toList();

        return tx.execute(s -> {
            Order order = orderStateService.lock(orderId);
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            ensureCanReturn(order);
            BigDecimal refund = order.getSubtotal().subtract(order.getDiscountAmount());
            ReturnRequest saved = repository.save(new ReturnRequest(order.getId(), reason, text, urls, refund,
                    OffsetDateTime.now(clock)));
            return ReturnRequestResponse.from(saved);
        });
    }

    /** Yêu cầu, hạn gửi và quyền gửi của một đơn — cho `OrderDetail` (khách và nhân viên). */
    @Transactional(readOnly = true)
    public ReturnInfo infoFor(Order order) {
        ReturnRequestResponse request = repository.findByOrderId(order.getId()).map(ReturnRequestResponse::from).orElse(null);
        OffsetDateTime deadline = deadline(order);
        boolean canReturn = request == null && order.getStatus() == OrderStatus.COMPLETED && deadline != null
                && !OffsetDateTime.now(clock).isAfter(deadline);
        return new ReturnInfo(request, deadline, canReturn);
    }

    // ------------------------------------------------------------------ nhân viên

    @Transactional(readOnly = true)
    public PageResponse<AdminReturnResponses.Summary> list(ReturnStatus status, int page, int limit) {
        PageRequest pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<ReturnRequest> result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        Map<Long, Order> orders = orderRepository.findAllById(result.getContent().stream().map(ReturnRequest::getOrderId).toList())
                .stream().collect(Collectors.toMap(Order::getId, Function.identity()));
        Map<Long, UserProfileResponse> customers = userService.profiles(
                orders.values().stream().map(Order::getUserId).distinct().toList());
        return PageResponse.of(result.map(r -> {
            Order o = orders.get(r.getOrderId());
            OrderItem first = o == null || o.getItems().isEmpty() ? null : o.getItems().get(0);
            return new AdminReturnResponses.Summary(r.getId(), r.getOrderId(), o == null ? null : o.getOrderCode(),
                    o == null ? null : o.getStatus(), r.getReason(), r.getReason().getLabel(), r.getRefundAmount(),
                    r.getStatus(), o == null ? 0 : o.totalQuantity(), first == null ? null : first.getItemName(),
                    first == null ? null : first.getItemImageUrl(),
                    r.getImageUrls().isEmpty() ? null : r.getImageUrls().get(0),
                    o == null ? null : customer(customers.get(o.getUserId())), r.getCreatedAt(), r.getProcessedAt());
        }));
    }

    @Transactional(readOnly = true)
    public AdminReturnResponses.Detail detail(Long id) {
        ReturnRequest r = repository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return toDetail(r, orderRepository.findById(r.getOrderId()).orElseThrow());
    }

    /**
     * Duyệt (cộng lại kho, đơn RETURNED) hoặc từ chối (bắt buộc ghi chú). Khoá đơn rồi mới khoá yêu cầu và kiểm còn
     * PENDING — hai nhân viên bấm cùng lúc chỉ một người thành công, người sau nhận INVALID_STATUS_TRANSITION.
     */
    @Transactional
    public AdminReturnResponses.Detail process(Long id, AdminReturnResponses.ProcessRequest req, Long staffId) {
        Long orderId = repository.findOrderIdById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        Order order = orderStateService.lock(orderId);
        ReturnRequest r = repository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (r.getStatus() != ReturnStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "Yêu cầu trả hàng này đã được xử lý.", null);
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (req.action() == AdminReturnResponses.Action.REJECT) {
            if (note == null) {
                throw fieldError("note", "Vui lòng nhập lý do từ chối.");
            }
            r.process(ReturnStatus.REJECTED, staffId, note, now);
        } else {
            inventoryService.restockReturn(order.stockLines(), staffId, "Trả hàng đơn " + order.getOrderCode());
            orderStateService.apply(order, OrderStatus.RETURNED, staffId,
                    note == null ? "Đã duyệt yêu cầu trả hàng" : "Đã duyệt yêu cầu trả hàng: " + note);
            r.process(ReturnStatus.APPROVED, staffId, note, now);
        }
        repository.flush();
        return toDetail(r, order);
    }

    // ------------------------------------------------------------------

    private Order ownedOrder(Long userId, Long orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** BR-9: đơn COMPLETED, chưa quá hạn, chưa có yêu cầu. */
    private void ensureCanReturn(Order order) {
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.RETURN_NOT_ALLOWED);
        }
        OffsetDateTime deadline = deadline(order);
        if (deadline == null || OffsetDateTime.now(clock).isAfter(deadline)) {
            throw new BusinessException(ErrorCode.RETURN_WINDOW_EXPIRED);
        }
        if (repository.existsByOrderId(order.getId())) {
            throw new BusinessException(ErrorCode.RETURN_ALREADY_EXISTS);
        }
    }

    private OffsetDateTime deadline(Order order) {
        return order.getCompletedAt() == null ? null : order.getCompletedAt().plusDays(windowDays);
    }

    private AdminReturnResponses.Detail toDetail(ReturnRequest r, Order order) {
        OrderDetailResponse detail = OrderDetailResponse.from(order,
                paymentService.findByOrderId(order.getId()).orElse(null),
                new ReturnInfo(ReturnRequestResponse.from(r), deadline(order), false));
        UserProfileResponse profile = userService.profiles(List.of(order.getUserId())).get(order.getUserId());
        return new AdminReturnResponses.Detail(ReturnRequestResponse.from(r), detail, customer(profile));
    }

    private static Customer customer(UserProfileResponse p) {
        return p == null ? null : new Customer(p.id(), p.email(), p.fullName(), p.phone());
    }

    private static ReturnReason parseReason(String code) {
        if (code == null || code.isBlank()) {
            throw fieldError("reason", "Vui lòng chọn lý do trả hàng.");
        }
        try {
            return ReturnReason.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            throw fieldError("reason", "Lý do trả hàng không hợp lệ.");
        }
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, Map.of("fieldErrors", Map.of(field, message)));
    }
}
