"use client";

import { Card, Result } from "antd";

/** Chỗ giữ cho trang admin chưa làm — để menu không dẫn tới 404. */
export function UnderConstruction({ title }: { title: string }) {
  return (
    <Card>
      <Result status="info" title={`${title} — đang xây dựng`} subTitle="Trang này sẽ có khi module tương ứng hoàn thành." />
    </Card>
  );
}
