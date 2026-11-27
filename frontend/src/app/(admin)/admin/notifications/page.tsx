"use client";

import { Card } from "antd";
import { STAFF } from "@/lib/admin/nav";
import { RequireRole } from "@/components/admin/shell/RequireRole";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { NotificationList } from "@/components/features/notification/NotificationList";

/** /admin/notifications — thông báo của nhân viên (FR-22, D-42, D-43), EMPLOYEE và ADMIN. */
export default function AdminNotificationsPage() {
  return (
    <RequireRole roles={STAFF}>
      <PageHeader title="Thông báo" />
      <Card>
        <NotificationList staff />
      </Card>
    </RequireRole>
  );
}
