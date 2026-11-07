package vn.campvivo.promotion;

/** Trạng thái voucher tính từ dữ liệu cho trang admin (api.md mục 4.10) — không lưu DB, xem {@link Voucher#statusAt}. */
public enum VoucherStatus {
    UPCOMING, ACTIVE, EXPIRED, EXHAUSTED, INACTIVE
}
