package vn.campvivo.auth.dto;

/** Kết quả đăng ký: `{ email }` (đã chuẩn hoá chữ thường) để frontend chuyển sang /verify-email?email=... */
public record RegisterResponse(String email) {}
