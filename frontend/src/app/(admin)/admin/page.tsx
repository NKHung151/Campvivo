"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Card, Col, Row, Statistic, Typography } from "antd";
import { ADMIN_ONLY, homeFor } from "@/lib/admin/nav";
import { useAdmin } from "@/components/admin/shell/AdminSession";
import { RequireRole } from "@/components/admin/shell/RequireRole";

/** /admin — dashboard (chỉ ADMIN). Nhân viên được chuyển tới trang đơn hàng (pages.md). */
export default function AdminDashboardPage() {
  const { ready, user } = useAdmin();
  const router = useRouter();

  useEffect(() => {
    if (ready && user?.role === "EMPLOYEE") router.replace(homeFor("EMPLOYEE"));
  }, [ready, user, router]);

  return (
    <RequireRole roles={ADMIN_ONLY}>
      {/* TODO(N2-31, N2-32): số liệu thật từ /admin/analytics/* */}
      <Row gutter={[16, 16]}>
        {["Doanh thu tháng này", "Đơn hoàn tất", "Đơn chờ xử lý", "Khách hàng mới"].map((t) => (
          <Col xs={24} sm={12} xl={6} key={t}>
            <Card>
              <Statistic title={t} value="—" />
            </Card>
          </Col>
        ))}
      </Row>
      <Card style={{ marginTop: 16 }}>
        <Typography.Text type="secondary">Biểu đồ doanh thu và top mặt hàng sẽ có ở task N2-32.</Typography.Text>
      </Card>
    </RequireRole>
  );
}
