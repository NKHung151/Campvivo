"use client";
/* eslint-disable @next/next/no-img-element -- ảnh mặt hàng (Cloudinary) hiển thị nguyên */

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import dayjs, { type Dayjs } from "dayjs";
import { Alert, Card, Col, DatePicker, Empty, Row, Segmented, Space, Spin, Statistic, Table, Tag, Tooltip, Typography } from "antd";
import { Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip as ChartTooltip, XAxis, YAxis } from "recharts";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { getAnalyticsSummary, getRevenueSeries, getTopItems } from "@/services/admin/adminAnalyticsService";
import { getErrorMessage } from "@/lib/errorMessages";
import { ORDER_STATUS, ORDER_STATUSES } from "@/lib/orderStatus";
import { vnd } from "@/lib/format";
import type { AnalyticsGroupBy, AnalyticsSummary, RevenuePoint, TopItem } from "@/types/api/analytics";

const FMT = "YYYY-MM-DD";
/** Xem theo ngày tối đa 366 ngày (backend cũng chặn). */
const MAX_DAYS_BY_DAY = 366;

const PRESETS: { label: string; value: [Dayjs, Dayjs] }[] = [
  { label: "7 ngày qua", value: [dayjs().subtract(6, "day"), dayjs()] },
  { label: "30 ngày qua", value: [dayjs().subtract(29, "day"), dayjs()] },
  { label: "Tháng này", value: [dayjs().startOf("month"), dayjs()] },
  { label: "Tháng trước", value: [dayjs().subtract(1, "month").startOf("month"), dayjs().subtract(1, "month").endOf("month")] },
  { label: "Năm nay", value: [dayjs().startOf("year"), dayjs()] },
  { label: "12 tháng qua", value: [dayjs().subtract(11, "month").startOf("month"), dayjs()] },
];

/** 1.250.000 → "1,25tr"; 12.000 → "12k" (trục biểu đồ). */
const shortMoney = (v: number) =>
  v >= 1e9 ? `${+(v / 1e9).toFixed(2)}tỷ` : v >= 1e6 ? `${+(v / 1e6).toFixed(2)}tr` : v >= 1e3 ? `${Math.round(v / 1e3)}k` : `${v}`;

/** "2026-10-17" → "17/10"; "2026-10" → "10/2026"; "2026" giữ nguyên. */
const periodLabel = (p: string, g: AnalyticsGroupBy) =>
  g === "day" ? dayjs(p).format("DD/MM") : g === "month" ? dayjs(`${p}-01`).format("MM/YYYY") : p;

/**
 * Dashboard (FR-24, chỉ ADMIN): khoảng ngày → 4 ô số liệu, biểu đồ doanh thu + số đơn hoàn tất theo ngày / tháng / năm,
 * số đơn theo trạng thái, top 10 mặt hàng bán chạy. Doanh thu = đơn COMPLETED theo ngày hoàn tất (giờ Việt Nam).
 */
export function DashboardView() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(PRESETS[1].value);
  const [groupBy, setGroupBy] = useState<AnalyticsGroupBy>("day");
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [series, setSeries] = useState<RevenuePoint[] | null>(null);
  const [top, setTop] = useState<TopItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const params = useMemo(() => ({ from: range[0].format(FMT), to: range[1].format(FMT) }), [range]);
  const days = range[1].diff(range[0], "day") + 1;
  const dayDisabled = days > MAX_DAYS_BY_DAY;
  const effectiveGroup: AnalyticsGroupBy = groupBy === "day" && dayDisabled ? "month" : groupBy;

  useEffect(() => {
    let alive = true;
    setError(null);
    setSummary(null);
    setTop(null);
    Promise.all([getAnalyticsSummary(params), getTopItems(params, 10)])
      .then(([s, t]) => {
        if (!alive) return;
        setSummary(s);
        setTop(t);
      })
      .catch((e) => alive && setError(getErrorMessage(e)));
    return () => {
      alive = false;
    };
  }, [params]);

  useEffect(() => {
    let alive = true;
    setSeries(null);
    getRevenueSeries(params, effectiveGroup)
      .then((s) => alive && setSeries(s))
      .catch((e) => alive && setError(getErrorMessage(e)));
    return () => {
      alive = false;
    };
  }, [params, effectiveGroup]);

  const pending = summary ? summary.ordersByStatus.PENDING + summary.ordersByStatus.CONFIRMED : 0;
  const totalOrders = summary ? ORDER_STATUSES.reduce((s, k) => s + summary.ordersByStatus[k], 0) : 0;
  const chartData = (series ?? []).map((p) => ({ ...p, label: periodLabel(p.period, effectiveGroup) }));
  const hasRevenue = chartData.some((p) => p.orderCount > 0);

  return (
    <>
      <PageHeader
        title="Dashboard"
        actions={
          <Space wrap>
            <DatePicker.RangePicker
              value={range}
              format="DD/MM/YYYY"
              allowClear={false}
              presets={PRESETS}
              disabledDate={(d) => d.isAfter(dayjs(), "day")}
              onChange={(v) => v?.[0] && v[1] && setRange([v[0], v[1]])}
            />
            <Segmented<AnalyticsGroupBy>
              value={effectiveGroup}
              onChange={setGroupBy}
              options={[
                { value: "day", label: "Theo ngày", disabled: dayDisabled },
                { value: "month", label: "Theo tháng" },
                { value: "year", label: "Theo năm" },
              ]}
            />
          </Space>
        }
      />

      {error && <Alert type="error" showIcon message="Không tải được số liệu" description={error} style={{ marginBottom: 16 }} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Doanh thu" value={summary ? vnd(summary.revenue, " ₫") : "—"} loading={!summary && !error} />
            <Typography.Text type="secondary" className="cv-dash-note">
              Đơn hoàn tất trong khoảng
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Đơn hoàn tất" value={summary?.completedOrders ?? "—"} loading={!summary && !error} />
            <Typography.Text type="secondary" className="cv-dash-note">
              Theo ngày hoàn tất
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Đơn chờ xử lý" value={summary ? pending : "—"} loading={!summary && !error} />
            <Typography.Text type="secondary" className="cv-dash-note">
              Đặt trong khoảng, chờ xác nhận hoặc chờ giao
            </Typography.Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Khách hàng mới" value={summary?.newCustomers ?? "—"} loading={!summary && !error} />
            <Typography.Text type="secondary" className="cv-dash-note">
              Tài khoản đăng ký trong khoảng
            </Typography.Text>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={16}>
          <Card title="Doanh thu và số đơn hoàn tất">
            {!series ? (
              <div className="cv-dash-center">
                <Spin />
              </div>
            ) : !hasRevenue ? (
              <Empty description="Chưa có đơn hoàn tất trong khoảng này" />
            ) : (
              <ResponsiveContainer width="100%" height={320}>
                <ComposedChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tick={{ fontSize: 12 }} minTickGap={12} />
                  <YAxis yAxisId="money" tickFormatter={shortMoney} tick={{ fontSize: 12 }} width={60} />
                  <YAxis yAxisId="count" orientation="right" allowDecimals={false} tick={{ fontSize: 12 }} width={36} />
                  <ChartTooltip
                    formatter={(v: number, name: string) => (name === "Doanh thu" ? vnd(v, " ₫") : v)}
                    labelFormatter={(l: string) => l}
                  />
                  <Legend />
                  <Bar yAxisId="money" dataKey="revenue" name="Doanh thu" fill="#2f6a3f" radius={[3, 3, 0, 0]} maxBarSize={40} />
                  <Line yAxisId="count" dataKey="orderCount" name="Số đơn" stroke="#d48806" strokeWidth={2} dot={chartData.length <= 31} />
                </ComposedChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="Đơn đặt theo trạng thái" extra={summary && <Typography.Text type="secondary">{totalOrders} đơn</Typography.Text>}>
            {!summary ? (
              <div className="cv-dash-center">
                <Spin />
              </div>
            ) : (
              <div className="cv-dash-status">
                {ORDER_STATUSES.map((k) => {
                  const n = summary.ordersByStatus[k];
                  const pct = totalOrders ? Math.round((n / totalOrders) * 100) : 0;
                  return (
                    <div key={k} className="cv-dash-status-row">
                      <span className="cv-dash-dot" style={{ background: ORDER_STATUS[k].color }} />
                      <span className="cv-dash-status-name">{ORDER_STATUS[k].label}</span>
                      <span className="cv-dash-status-bar">
                        <span style={{ width: `${pct}%`, background: ORDER_STATUS[k].color }} />
                      </span>
                      <Tooltip title={`${pct}%`}>
                        <b className="cv-dash-status-n">{n}</b>
                      </Tooltip>
                    </div>
                  );
                })}
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Card title="Top 10 mặt hàng bán chạy" style={{ marginTop: 16 }} extra={<Typography.Text type="secondary">Theo số lượng trong đơn hoàn tất</Typography.Text>}>
        <Table<TopItem>
          rowKey={(t) => `${t.type}-${t.id}`}
          size="middle"
          pagination={false}
          loading={!top && !error}
          dataSource={top ?? []}
          scroll={{ x: 640 }}
          locale={{ emptyText: <Empty description="Chưa có mặt hàng nào bán được trong khoảng này" /> }}
          columns={[
            { title: "#", key: "rank", width: 48, render: (_, __, i) => <b>{i + 1}</b> },
            {
              title: "Mặt hàng",
              key: "name",
              render: (_, t) => (
                <div className="cv-prod-cell">
                  {t.imageUrl ? <img src={t.imageUrl} alt={t.name} width={40} height={40} className="cv-prod-thumb" /> : <div className="cv-img-empty">—</div>}
                  <Link href={t.type === "PRODUCT" ? `/admin/products/${t.id}` : `/admin/combos/${t.id}`} className="cv-prod-name" title={t.name}>
                    {t.name}
                  </Link>
                </div>
              ),
            },
            {
              title: "Loại",
              dataIndex: "type",
              width: 100,
              render: (v: TopItem["type"]) => <Tag bordered={false} color={v === "COMBO" ? "gold" : "green"}>{v === "COMBO" ? "Combo" : "Sản phẩm"}</Tag>,
            },
            { title: "Đã bán", dataIndex: "quantitySold", width: 100, align: "right" },
            { title: "Doanh thu", dataIndex: "revenue", width: 150, align: "right", render: (v: number) => <span className="cv-price">{vnd(v, " ₫")}</span> },
          ]}
        />
      </Card>
    </>
  );
}
