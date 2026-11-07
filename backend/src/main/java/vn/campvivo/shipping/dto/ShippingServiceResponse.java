package vn.campvivo.shipping.dto;

/** Gói dịch vụ GHN khả dụng cho tuyến kho → quận nhận (vd. "Hàng nhẹ", service_type_id 2). */
public record ShippingServiceResponse(int serviceId, int serviceTypeId, String name) {}
