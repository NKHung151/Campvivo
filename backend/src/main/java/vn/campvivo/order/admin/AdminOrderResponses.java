package vn.campvivo.order.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.payment.PaymentMethod;
import vn.campvivo.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Kiểu dữ liệu API quản lý đơn hàng (FR-13) — docs/02-ky-thuat/api.md mục 4.10. */
public final class AdminOrderResponses {

    private AdminOrderResponses() {
    }

    /** Bộ lọc GET /admin/orders. `q`: mã đơn, SĐT người nhận, email/họ tên khách. Ngày theo giờ Việt Nam. */
    public record Query(OrderStatus status, PaymentMethod paymentMethod, String q, LocalDate from, LocalDate to,
                        int page, int limit) {}

    public record Customer(Long id, String email, String fullName, String phone) {}

    public record Summary(
            Long id,
            String orderCode,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            BigDecimal finalAmount,
            int itemCount,
            String firstItemName,
            String firstItemImageUrl,
            Customer customer,
            String recipientName,
            String recipientPhone,
            String shippingOrderCode,
            OffsetDateTime createdAt
    ) {}

    /**
     * Chi tiết cho nhân viên: đơn (`OrderDetail`) + khách + các trạng thái nhân viên được chuyển tới ngay lúc này
     * (giao diện chỉ hiện đúng các nút đó) + có tạo/tạo lại vận đơn GHN được không.
     */
    public record Detail(OrderDetailResponse order, Customer customer, List<OrderStatus> allowedStatuses,
                         boolean canCreateShipment) {}

    /** PATCH /admin/orders/{id}/status. Huỷ bắt buộc `note`. */
    public record StatusRequest(
            @NotNull(message = "Vui lòng chọn trạng thái") OrderStatus status,
            @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
    ) {}

    public record StatusChange(Long orderId, OrderStatus oldStatus, OrderStatus newStatus, OffsetDateTime updatedAt) {}

    /** Hành trình vận đơn đọc trực tiếp từ GHN. `status` là mã trạng thái GHN (ready_to_pick, delivering, delivered...). */
    public record Tracking(String carrier, String trackingCode, String status, OffsetDateTime expectedDeliveryTime,
                           OffsetDateTime updatedAt, List<TrackingLog> log) {}

    public record TrackingLog(String status, OffsetDateTime updatedAt) {}
}
