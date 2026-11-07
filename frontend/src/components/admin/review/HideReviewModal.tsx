"use client";

import { useEffect, useState } from "react";
import { App, Form, Input, Modal, Typography } from "antd";
import { getErrorMessage } from "@/lib/errorMessages";
import { hideReview } from "@/services/admin/adminReviewService";
import type { AdminReview } from "@/types/api/review";

const MAX_REASON = 500;

/** Ẩn đánh giá: lý do bắt buộc (≤ 500 ký tự), khách thấy lý do này (D-31). Đang ẩn → sửa lại lý do. */
export function HideReviewModal({
  review,
  onClose,
  onDone,
}: {
  review: AdminReview | null;
  onClose: () => void;
  onDone: (updated: AdminReview) => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (review) form.setFieldsValue({ reason: review.hiddenReason ?? "" });
  }, [review, form]);

  const submit = async () => {
    if (!review) return;
    const { reason } = await form.validateFields();
    setSaving(true);
    try {
      const updated = await hideReview(review.id, reason.trim());
      message.success(review.isHidden ? "Đã cập nhật lý do ẩn" : "Đã ẩn đánh giá");
      onDone(updated);
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={!!review}
      title={review?.isHidden ? "Sửa lý do ẩn đánh giá" : "Ẩn đánh giá"}
      okText={review?.isHidden ? "Lưu lý do" : "Ẩn đánh giá"}
      okButtonProps={{ danger: !review?.isHidden, loading: saving }}
      cancelText="Huỷ"
      onOk={submit}
      onCancel={onClose}
      destroyOnClose
    >
      {review && (
        <>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
            Đánh giá của <b>{review.user?.fullName ?? "khách"}</b> cho <b>{review.item.name ?? "mặt hàng"}</b> sẽ không còn hiện
            công khai và không được tính vào điểm. Khách vẫn thấy đánh giá kèm lý do và chỉ có thể xoá.
          </Typography.Paragraph>
          <Form form={form} layout="vertical" preserve={false}>
            <Form.Item
              name="reason"
              label="Lý do ẩn (khách sẽ thấy)"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập lý do ẩn." },
                { max: MAX_REASON, message: `Tối đa ${MAX_REASON} ký tự.` },
              ]}
            >
              <Input.TextArea rows={4} maxLength={MAX_REASON} showCount placeholder="VD: Nội dung quảng cáo / spam" autoFocus />
            </Form.Item>
          </Form>
        </>
      )}
    </Modal>
  );
}
