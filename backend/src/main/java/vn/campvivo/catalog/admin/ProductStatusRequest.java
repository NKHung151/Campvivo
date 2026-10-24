package vn.campvivo.catalog.admin;

import jakarta.validation.constraints.NotNull;

/** Body PATCH /api/admin/products/{id}/status — ẩn/hiện nhanh từ bảng (BR-14: không xoá cứng). */
public record ProductStatusRequest(@NotNull(message = "Thiếu trạng thái.") Boolean isActive) {}
