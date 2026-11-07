package vn.campvivo.payment;

/** Trạng thái thanh toán (database.md mục 5.3): PENDING → SUCCESS | FAILED. */
public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED
}
