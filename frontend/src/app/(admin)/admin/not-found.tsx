"use client";

import { Button, Result } from "antd";

export default function AdminNotFound() {
  return (
    <Result
      status="404"
      title="Không tìm thấy trang"
      subTitle="Đường dẫn không tồn tại trong khu vực quản trị."
      extra={
        <Button type="primary" href="/admin">
          Về trang quản trị
        </Button>
      }
    />
  );
}
