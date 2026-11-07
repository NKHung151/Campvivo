package vn.campvivo.shipping.dto;

/**
 * `fee` = phí khách trả (0 khi được miễn phí), `carrierFee` = phí GHN báo (shop trả GHN),
 * `freeShippingThreshold` để giao diện hiện "miễn phí từ ...".
 */
public record ShippingFeeResponse(long fee, long carrierFee, boolean freeShipping, long freeShippingThreshold) {}
