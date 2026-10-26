package vn.campvivo.shipping;

/** Tham số tính phí gửi GHN; kho gửi, gói dịch vụ, kích thước kiện lấy từ app.ghn.*. Cân nặng tính bằng gram. */
public record GhnFeeRequest(int toDistrictId, String toWardCode, int weight, long insuranceValue) {}
