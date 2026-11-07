"use client";

import { useEffect, useState } from "react";
import { Alert, App, Button, Descriptions, Drawer, Form, Input, Select, Skeleton, Space, Switch, Tag, Typography } from "antd";
import { useAuth } from "@/components/features/auth/AuthProvider";
import { ROLE_LABEL, ROLES } from "@/components/admin/user/userLabels";
import { getErrorMessage } from "@/lib/errorMessages";
import { vnd } from "@/lib/format";
import { fmtDateTime } from "@/lib/orderStatus";
import { getUser, updateUser } from "@/services/admin/adminUserService";
import type { AdminUserDetail, UpdateAdminUserRequest } from "@/types/api/adminUser";
import type { Role } from "@/types/api/common";

interface FormValues {
  fullName: string;
  phone: string;
  role: Role;
  isActive: boolean;
}

const toForm = (u: AdminUserDetail): FormValues => ({ fullName: u.fullName, phone: u.phone ?? "", role: u.role, isActive: u.isActive });

/**
 * Ngăn kéo chi tiết + sửa một người dùng (FR-14): thông tin, thống kê đơn (số đơn, tổng chi tiêu của đơn hoàn tất) và form sửa
 * họ tên / SĐT / vai trò / trạng thái. Chỉ gửi các trường đã đổi (PATCH từng phần). Đổi vai trò hoặc khoá làm người đó bị đăng
 * xuất ở mọi thiết bị nên có hộp xác nhận. Admin không tự khoá hay tự đổi vai trò của mình (server cũng chặn: CANNOT_MODIFY_SELF).
 */
export function UserDrawer({ userId, onClose, onSaved }: { userId: number | null; onClose: () => void; onSaved: () => void }) {
  const { message, modal } = App.useApp();
  const { user: me } = useAuth();
  const [form] = Form.useForm<FormValues>();
  const [detail, setDetail] = useState<AdminUserDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (userId === null) {
      setDetail(null);
      return;
    }
    let alive = true;
    setLoading(true);
    setLoadError(null);
    setDetail(null);
    getUser(userId)
      .then((d) => alive && setDetail(d))
      .catch((e) => alive && setLoadError(getErrorMessage(e)))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [userId]);

  // Đồng bộ các ô của form với người vừa tải / vừa lưu. `form` sống ở thành phần này nên Ant Design Form GIỮ giá trị của lần
  // mở trước và để giá trị đó ghi đè `initialValues` khi dựng lại: không có đoạn này, mở người khác vẫn thấy họ tên / SĐT /
  // vai trò / trạng thái của người đầu tiên đã mở.
  useEffect(() => {
    if (detail) form.setFieldsValue(toForm(detail));
  }, [detail, form]);

  const isSelf = !!detail && me?.id === detail.id;

  async function save(current: AdminUserDetail, payload: UpdateAdminUserRequest) {
    setSaving(true);
    try {
      const saved = await updateUser(current.id, payload);
      setDetail(saved);
      message.success("Đã lưu thay đổi.");
      onSaved();
    } catch (e) {
      message.error(getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  function onFinish(values: FormValues) {
    if (!detail) return;
    const payload: UpdateAdminUserRequest = {};
    const fullName = values.fullName.trim();
    if (fullName !== detail.fullName) payload.fullName = fullName;
    const phone = (values.phone ?? "").trim();
    if (phone !== (detail.phone ?? "")) payload.phone = phone;
    if (!isSelf && values.role !== detail.role) payload.role = values.role;
    if (!isSelf && values.isActive !== detail.isActive) payload.isActive = values.isActive;

    if (Object.keys(payload).length === 0) {
      message.info("Không có thay đổi.");
      return;
    }
    if (payload.role === undefined && payload.isActive === undefined) {
      void save(detail, payload);
      return;
    }
    modal.confirm({
      title: "Xác nhận thay đổi quyền / trạng thái",
      content: (
        <div>
          {payload.role !== undefined && (
            <div>
              Vai trò: <b>{ROLE_LABEL[detail.role].label}</b> → <b>{ROLE_LABEL[payload.role].label}</b>
            </div>
          )}
          {payload.isActive !== undefined && (
            <div>
              Tài khoản: <b>{payload.isActive ? "mở khoá" : "khoá (không đăng nhập được nữa)"}</b>
            </div>
          )}
          <div style={{ marginTop: 8 }}>Người này sẽ bị đăng xuất khỏi mọi thiết bị và cần đăng nhập lại.</div>
        </div>
      ),
      okText: "Xác nhận",
      cancelText: "Huỷ",
      onOk: () => save(detail, payload),
    });
  }

  return (
    <Drawer
      open={userId !== null}
      onClose={onClose}
      width="min(480px, 100vw)"
      destroyOnClose
      title={detail ? detail.fullName : "Người dùng"}
      extra={detail && <Tag color={ROLE_LABEL[detail.role].color}>{ROLE_LABEL[detail.role].label}</Tag>}
    >
      {loading && <Skeleton active paragraph={{ rows: 8 }} />}
      {loadError && (
        <Alert
          type="error"
          showIcon
          message="Không tải được người dùng"
          description={loadError}
          action={
            <Button size="small" onClick={onClose}>
              Đóng
            </Button>
          }
        />
      )}
      {detail && (
        <Space direction="vertical" size={20} style={{ width: "100%" }}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Email">
              <Typography.Text copyable>{detail.email}</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="Xác thực email">
              {detail.emailVerified ? <Tag color="green">Đã xác thực</Tag> : <Tag>Chưa xác thực</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="Địa chỉ mặc định">{detail.address ?? "—"}</Descriptions.Item>
            <Descriptions.Item label="Ngày tạo">{fmtDateTime(detail.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="Số đơn">
              {detail.orderCount} <Typography.Text type="secondary">({detail.completedOrderCount} đã hoàn tất)</Typography.Text>
            </Descriptions.Item>
            <Descriptions.Item label="Tổng chi tiêu">
              <b>{vnd(detail.totalSpent)}</b> <Typography.Text type="secondary">(đơn hoàn tất)</Typography.Text>
            </Descriptions.Item>
          </Descriptions>

          {isSelf && (
            <Alert type="info" showIcon message="Đây là tài khoản của bạn: không thể tự khoá hay đổi vai trò, chỉ sửa được họ tên và số điện thoại." />
          )}

          <Form<FormValues>
            key={detail.id}
            form={form}
            layout="vertical"
            initialValues={toForm(detail)}
            preserve={false}
            onFinish={onFinish}
            requiredMark={false}
          >
            <Form.Item
              name="fullName"
              label="Họ và tên"
              rules={[
                { required: true, whitespace: true, message: "Vui lòng nhập họ tên." },
                { min: 2, max: 100, message: "Họ tên phải từ 2 đến 100 ký tự." },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item
              name="phone"
              label="Số điện thoại"
              extra="Để trống để xoá số đã lưu."
              rules={[{ pattern: /^(0\d{9})?$/, message: "Số điện thoại gồm 10 số, bắt đầu bằng 0." }]}
            >
              <Input inputMode="tel" maxLength={10} />
            </Form.Item>
            <Form.Item name="role" label="Vai trò">
              <Select disabled={isSelf} options={ROLES.map((r) => ({ value: r, label: ROLE_LABEL[r].label }))} />
            </Form.Item>
            <Form.Item name="isActive" label="Trạng thái" valuePropName="checked">
              <Switch disabled={isSelf} checkedChildren="Hoạt động" unCheckedChildren="Đã khoá" />
            </Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" loading={saving}>
                Lưu thay đổi
              </Button>
              <Button onClick={onClose}>Đóng</Button>
            </Space>
          </Form>
        </Space>
      )}
    </Drawer>
  );
}
