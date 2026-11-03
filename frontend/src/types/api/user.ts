/** Body `PUT /users/me` — docs/02-ky-thuat/api.md mục 4.2. Chỉ ba trường này sửa được (email, role không). */
export interface UpdateProfileRequest {
  fullName: string;
  /** Rỗng/null = xoá số đã lưu. */
  phone: string | null;
  /** Rỗng/null = xoá địa chỉ đã lưu. */
  address: string | null;
}

/** Body `PUT /users/me/password`. Thành công thì mọi token cũ (kể cả của thiết bị này) hết hiệu lực. */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
