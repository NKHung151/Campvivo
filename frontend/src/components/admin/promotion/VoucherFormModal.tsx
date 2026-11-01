"use client";

import { useEffect, useState } from "react";
import { App, Col, DatePicker, Form, Input, InputNumber, Modal, Radio, Row, Switch, Typography } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { ApiError } from "@/services/apiError";
import { getErrorMessage } from "@/lib/errorMessages";
import { createVoucher, updateVoucher } from "@/services/admin/adminVoucherService";
import type { DiscountType } from "@/types/api/common";
import type { AdminVoucher, VoucherRequest } from "@/types/api/promotion";

const vndFormatter = (v: number | string | undefined) => (v === undefined || v === "" ? "" : `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, "."));
const vndParser = (v: string | undefined) => Number((v ?? "").replace(/\D/g, "")) as unknown as 0;

interface FormValues {
  code: string;
  discountType: DiscountType;
  discountValue: number;
  maxDiscount?: number | null;
  minOrderValue: number;
  quantity: number;
  period: [Dayjs, Dayjs];
  isActive: boolean;
  isPublic: boolean;
}

/** Lỗi backend → ô tương ứng trong form (startDate/expiryDate gộp vào ô "Thời gian hiệu lực"). */
const FIELD_MAP: Record<string, keyof FormValues> = { startDate: "period", expiryDate: "period" };

/**
 * Modal thêm/sửa voucher (FR-16). `voucher` null = thêm mới. Mã tự IN HOA; "giảm tối đa" chỉ cho loại %.
 * Lỗi `fieldErrors` hiện dưới đúng ô, mã trùng hiện ở ô Mã.
 */
export function VoucherFormModal({
  open,
  voucher,
  onClose,
  onSaved,
}: {
  open: boolean;
  voucher: AdminVoucher | null;
  onClose: () => void;
  onSaved: (saved: AdminVoucher) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [saving, setSaving] = useState(false);
  const discountType = Form.useWatch("discountType", form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue(
      voucher
        ? {
            code: voucher.code,
            discountType: voucher.discountType,
            discountValue: voucher.discountValue,
            maxDiscount: voucher.maxDiscount,
            minOrderValue: voucher.minOrderValue,
            quantity: voucher.quantity,
            period: [dayjs(voucher.startDate), dayjs(voucher.expiryDate)],
            isActive: voucher.isActive,
            isPublic: voucher.isPublic,
          }
        : {
            discountType: "PERCENTAGE",
            minOrderValue: 0,
            quantity: 100,
            period: [dayjs().startOf("minute"), dayjs().add(30, "day").endOf("day")],
            isActive: true,
            isPublic: true,
          }
    );
  }, [open, voucher, form]);

  const submit = async (v: FormValues) => {
    setSaving(true);
    const body: VoucherRequest = {
      code: v.code.trim().toUpperCase(),
      discountType: v.discountType,
      discountValue: v.discountValue,
      maxDiscount: v.discountType === "PERCENTAGE" ? v.maxDiscount || null : null,
      minOrderValue: v.minOrderValue ?? 0,
      quantity: v.quantity,
      startDate: v.period[0].format(),
      expiryDate: v.period[1].format(),
      isActive: v.isActive,
      isPublic: v.isPublic,
    };
    try {
      const saved = voucher ? await updateVoucher(voucher.id, body) : await createVoucher(body);
      message.success(voucher ? `Đã lưu mã ${saved.code}.` : `Đã tạo mã ${saved.code}.`);
      onSaved(saved);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
        form.setFields(Object.entries(e.fieldErrors).map(([f, msg]) => ({ name: FIELD_MAP[f] ?? (f as keyof FormValues), errors: [msg] })));
      } else if (e instanceof ApiError && e.errorCode === "VOUCHER_CODE_ALREADY_EXISTS") {
        form.setFields([{ name: "code", errors: [e.message] }]);
      } else {
        message.error(getErrorMessage(e));
      }
    } finally {
      setSaving(false);
    }
  };

  const usedCount = voucher?.usedCount ?? 0;

  return (
    <Modal
      open={open}
      title={voucher ? `Sửa mã ${voucher.code}` : "Thêm mã giảm giá"}
      okText={voucher ? "Lưu" : "Tạo mã"}
      cancelText="Huỷ"
      confirmLoading={saving}
      onOk={() => form.submit()}
      onCancel={onClose}
      width={640}
      forceRender
    >
      <Form<FormValues> form={form} layout="vertical" onFinish={submit} requiredMark="optional" disabled={saving}>
        <Form.Item
          name="code"
          label="Mã"
          normalize={(v: string) => v?.toUpperCase()}
          rules={[
            { required: true, whitespace: true, message: "Vui lòng nhập mã." },
            { pattern: /^[A-Za-z0-9_-]{3,50}$/, message: "3–50 ký tự: chữ không dấu, số, - hoặc _." },
          ]}
          extra="Khách nhập không phân biệt hoa thường."
        >
          <Input placeholder="CAMPVIVO10" maxLength={50} />
        </Form.Item>

        <Form.Item name="discountType" label="Loại giảm" rules={[{ required: true }]}>
          <Radio.Group
            optionType="button"
            buttonStyle="solid"
            options={[
              { value: "PERCENTAGE", label: "Theo %" },
              { value: "FIXED_AMOUNT", label: "Số tiền cố định" },
            ]}
          />
        </Form.Item>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item
              name="discountValue"
              label="Giá trị giảm"
              rules={[
                { required: true, message: "Vui lòng nhập giá trị giảm." },
                discountType === "PERCENTAGE"
                  ? { type: "number", min: 1, max: 100, message: "Từ 1 đến 100%." }
                  : { type: "number", min: 1, message: "Phải lớn hơn 0." },
              ]}
            >
              {discountType === "PERCENTAGE" ? (
                <InputNumber<number> style={{ width: "100%" }} min={1} max={100} precision={0} addonAfter="%" />
              ) : (
                <InputNumber<number> style={{ width: "100%" }} min={1} precision={0} formatter={vndFormatter} parser={vndParser} addonAfter="₫" />
              )}
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            {discountType === "PERCENTAGE" && (
              <Form.Item name="maxDiscount" label="Giảm tối đa" extra="Bỏ trống = không giới hạn.">
                <InputNumber<number> style={{ width: "100%" }} min={1} precision={0} formatter={vndFormatter} parser={vndParser} addonAfter="₫" />
              </Form.Item>
            )}
          </Col>
        </Row>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="minOrderValue" label="Đơn tối thiểu" rules={[{ required: true, message: "Vui lòng nhập (0 = mọi đơn)." }]}>
              <InputNumber<number> style={{ width: "100%" }} min={0} precision={0} formatter={vndFormatter} parser={vndParser} addonAfter="₫" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="quantity"
              label="Số lượng phát hành"
              extra={usedCount > 0 ? `Đã dùng ${usedCount} lượt — không được nhỏ hơn số này.` : undefined}
              rules={[
                { required: true, message: "Vui lòng nhập số lượng." },
                { type: "number", min: Math.max(1, usedCount), message: `Tối thiểu ${Math.max(1, usedCount)}.` },
              ]}
            >
              <InputNumber<number> style={{ width: "100%" }} min={1} max={1_000_000} precision={0} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          name="period"
          label="Thời gian hiệu lực"
          rules={[
            { required: true, message: "Vui lòng chọn thời gian." },
            {
              validator: (_, v?: [Dayjs, Dayjs]) =>
                !v || v[1].isAfter(v[0]) ? Promise.resolve() : Promise.reject(new Error("Ngày hết hạn phải sau ngày bắt đầu.")),
            },
          ]}
        >
          <DatePicker.RangePicker showTime={{ format: "HH:mm" }} format="DD/MM/YYYY HH:mm" style={{ width: "100%" }} />
        </Form.Item>

        <Row gutter={16}>
          <Col xs={24} sm={12}>
            <Form.Item name="isActive" label="Kích hoạt" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="isPublic" label="Hiển thị công khai" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Col>
        </Row>
        <Typography.Text type="secondary">
          Mã công khai hiện trong danh sách mã ở trang thanh toán và thanh khuyến mãi đầu trang. Mã ẩn chỉ dùng được khi khách tự nhập.
        </Typography.Text>
      </Form>
    </Modal>
  );
}
