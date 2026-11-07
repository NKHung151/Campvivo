"use client";

import { useEffect, useState } from "react";
import type { Customer } from "@/types/shop";
import { AddressSelects, type AddressSelection } from "@/components/features/cart/AddressSelects";

export const EMPTY_CUSTOMER: Customer = {
  gender: "Anh", name: "", phone: "", email: "", province: "", district: "", ward: "", street: "", note: "",
};

/** Có đủ mã địa chỉ GHN để tính phí ship và tạo vận đơn (địa chỉ lưu từ bản cũ chỉ có tên → phải chọn lại). */
export const hasGhnAddress = (c: Customer | null): c is Customer & { provinceId: number; districtId: number; wardCode: string } =>
  !!c && !!c.provinceId && !!c.districtId && !!c.wardCode;

/** Địa chỉ đầy đủ gửi lên đơn hàng / GHN. */
export const fullAddress = (c: Customer) => [c.street, c.ward, c.district, c.province].filter(Boolean).join(", ");

/**
 * Form "Thông tin giao hàng" (FR-10): người nhận, SĐT, Tỉnh/Quận/Phường GHN (D-27), số nhà, ghi chú, lưu làm mặc định.
 * Mở ra thì điền sẵn từ `initial` (địa chỉ lần trước hoặc hồ sơ).
 */
export function DeliveryInfoModal({
  open,
  initial,
  saveAsDefault,
  onSaveAsDefault,
  onClose,
  onConfirm,
}: {
  open: boolean;
  initial: Customer;
  saveAsDefault: boolean;
  onSaveAsDefault: (v: boolean) => void;
  onClose: () => void;
  onConfirm: (c: Customer) => void;
}) {
  const [form, setForm] = useState<Customer>(initial);
  const [err, setErr] = useState("");

  useEffect(() => {
    if (open) {
      setForm(initial);
      setErr("");
    }
    // Chỉ nạp lại khi vừa mở form
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const set = (k: keyof Customer) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));
  // Đổi tỉnh/quận thì AddressSelects trả về mã cấp dưới rỗng → ghi đè cả mã cũ
  const setAddress = (a: AddressSelection) => setForm((f) => ({ ...f, ...a, districtId: a.districtId, wardCode: a.wardCode }));

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) return setErr("Vui lòng nhập họ tên người nhận.");
    if (!/^0\d{9}$/.test(form.phone.trim())) return setErr("Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).");
    if (!hasGhnAddress(form) || !form.street.trim()) {
      return setErr("Vui lòng chọn Tỉnh/Thành, Quận/Huyện, Phường/Xã và nhập số nhà, tên đường.");
    }
    onConfirm({ ...form, name: form.name.trim(), phone: form.phone.trim(), street: form.street.trim() });
  };

  return (
    <div className={`modal modal-info-cart${open ? " hi" : ""}`} onClick={(e) => e.target === e.currentTarget && onClose()}>
      <form id="infoCart" className="modal-box" onSubmit={submit}>
        <div className="modal-header">
          <span>Thông tin giao hàng</span>
          <div className="close-btn" onClick={onClose}>
            ×
          </div>
        </div>
        <div id="cartNotification" className="text-danger">
          {err}
        </div>
        <div className="cv-form">
          <div className="form-section-modal">
            <label className="label-title-modal">Thông tin người nhận</label>
            <div className="row">
              <div className="col-12">
                {(["Anh", "Chị"] as const).map((g) => (
                  <span key={g}>
                    <input
                      id={g === "Anh" ? "rbNam" : "rbNu"}
                      type="radio"
                      name="gender"
                      checked={form.gender === g}
                      onChange={() => setForm((f) => ({ ...f, gender: g }))}
                    />{" "}
                    <label htmlFor={g === "Anh" ? "rbNam" : "rbNu"}>{g}</label>{" "}
                  </span>
                ))}
              </div>
              <div className="col-12">
                <div className="row">
                  <div className="col-6">
                    <input className="control" id="tbHoTenKH" type="text" maxLength={100} required placeholder=" " value={form.name} onChange={set("name")} />
                    <label className="form-label" htmlFor="tbHoTenKH">
                      Họ và tên
                    </label>
                  </div>
                  <div className="col-6">
                    <input className="control" id="tbSoDienThoai" type="tel" maxLength={10} required placeholder=" " value={form.phone} onChange={set("phone")} />
                    <label className="form-label" htmlFor="tbSoDienThoai">
                      Số điện thoại
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div className="form-section-location">
            <div className="add-address">Địa chỉ nhận hàng</div>
            <div className="row">
              <div className="col-12">
                <div className="row">
                  {open && <AddressSelects value={form} onChange={setAddress} />}
                  <div className="col-6">
                    <input className="control" id="tbDiaChiNhanHang" type="text" maxLength={200} placeholder=" " value={form.street} onChange={set("street")} required />
                    <label className="form-label" htmlFor="tbDiaChiNhanHang">
                      Số nhà, tên đường
                    </label>
                  </div>
                </div>
              </div>
              <div className="col-12">
                <div className="row" style={{ height: "100%" }}>
                  <div className="col-12">
                    <textarea id="tbYeuCauKhac" className="control" rows={3} maxLength={500} placeholder=" " value={form.note} onChange={set("note")} />
                    <label className="form-label" htmlFor="tbYeuCauKhac">
                      Ghi chú cho đơn hàng (Không bắt buộc)
                    </label>
                  </div>
                </div>
              </div>
              <div className="col-12">
                <label>
                  <input type="checkbox" checked={saveAsDefault} onChange={(e) => onSaveAsDefault(e.target.checked)} /> Lưu số điện thoại
                  và địa chỉ này vào hồ sơ
                </label>
              </div>
            </div>
          </div>
        </div>
        <div className="modal-footer">
          <button type="submit" className="confirm-btn">
            Xác Nhận
          </button>
        </div>
      </form>
    </div>
  );
}
