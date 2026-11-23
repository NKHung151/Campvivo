package vn.campvivo.order.returns.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.campvivo.order.OrderStatus;
import vn.campvivo.order.admin.AdminOrderResponses.Customer;
import vn.campvivo.order.dto.OrderDetailResponse;
import vn.campvivo.order.returns.ReturnReason;
import vn.campvivo.order.returns.ReturnStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Kiểu dữ liệu API xử lý trả hàng của nhân viên (FR-19b, D-41) — docs/02-ky-thuat/api.md mục 4.10. */
public final class AdminReturnResponses {

    private AdminReturnResponses() {
    }

    public record Summary(
            Long id,
            Long orderId,
            String orderCode,
            OrderStatus orderStatus,
            ReturnReason reason,
            String reasonLabel,
            BigDecimal refundAmount,
            ReturnStatus status,
            int itemCount,
            String firstItemName,
            String firstItemImageUrl,
            String firstImageUrl,
            Customer customer,
            OffsetDateTime createdAt,
            OffsetDateTime processedAt
    ) {}

    /** Yêu cầu + đơn (`OrderDetail`) + khách. */
    public record Detail(ReturnRequestResponse request, OrderDetailResponse order, Customer customer) {}

    public enum Action { APPROVE, REJECT }

    /** PATCH /admin/returns/{id}. Từ chối bắt buộc `note` (khách thấy ghi chú này). */
    public record ProcessRequest(
            @NotNull(message = "Vui lòng chọn duyệt hoặc từ chối") Action action,
            @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự") String note
    ) {}
}
