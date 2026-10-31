"use client";

import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import {
  Alert,
  App,
  Button,
  Card,
  DatePicker,
  Dropdown,
  Empty,
  Form,
  Image,
  Input,
  InputNumber,
  Modal,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from "antd";
import type { TableProps } from "antd";
import {
  ClearOutlined,
  GiftOutlined,
  HistoryOutlined,
  MinusOutlined,
  MoreOutlined,
  PlusOutlined,
  ReloadOutlined,
  ScissorOutlined,
  SyncOutlined,
} from "@ant-design/icons";
import { PageHeader } from "@/components/admin/common/PageHeader";
import { useAdmin } from "@/components/admin/shell/AdminSession";
import { getErrorMessage } from "@/lib/errorMessages";
import { ApiError } from "@/services/apiError";
import { adjustStock, listInventory, listInventoryLogs } from "@/services/admin/adminInventoryService";
import { packCombo, unpackCombo } from "@/services/admin/adminComboService";
import type { Pagination } from "@/types/api/common";
import type { InventoryItem, InventoryItemType, InventoryLogEntry, InventoryReason } from "@/types/api/inventory";

type TabKey = "products" | "combos" | "logs";
type AdjustMode = "IMPORT" | "EXPORT" | "ADJUST";
type PackMode = "pack" | "unpack";

const PAGE_SIZES = [10, 20, 50];
const DEFAULT_LIMIT = 20;

const REASONS: Record<InventoryReason, { label: string; color: string }> = {
  IMPORT: { label: "Nhập", color: "green" },
  EXPORT: { label: "Xuất", color: "volcano" },
  ADJUST: { label: "Kiểm kê", color: "blue" },
  RETURN: { label: "Trả hàng", color: "purple" },
};

const fmtTime = (iso: string) =>
  new Date(iso).toLocaleString("vi-VN", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });

/** Ô hiển thị khả dụng: hết / sắp hết / còn. */
function AvailableTag({ item }: { item: InventoryItem }) {
  if (item.availableQuantity <= 0) return <Tag color="red">Hết hàng</Tag>;
  if (item.isLowStock) return <Tag color="orange">Sắp hết · {item.availableQuantity}</Tag>;
  return <Tag color="green">{item.availableQuantity}</Tag>;
}

/**
 * Trang Tồn kho (FR-08, BR-8) — ADMIN và EMPLOYEE.
 * - Sản phẩm: Nhập (+), Xuất (−), Kiểm kê (gõ số đếm thực tế → hệ thống tự tính chênh lệch).
 * - Combo: thêm bộ bằng Đóng gói (trừ hàng lẻ), Tháo (trả hàng lẻ), Xuất bộ hỏng/mất; KHÔNG nhập tay (D-27).
 * - Nhật ký: mọi lần nhập/xuất/kiểm kê/đóng gói/tháo, lọc theo mặt hàng, lý do, khoảng ngày.
 * Tab, bộ lọc, trang nằm trên URL.
 */
export function InventoryView() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const tab = (params.get("tab") ?? "products") as TabKey;

  const setQuery = useCallback(
    (patch: Record<string, string | number | undefined>, replaceAll = false) => {
      const next = replaceAll ? new URLSearchParams() : new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(patch)) {
        if (v === undefined || v === "" || (k === "page" && v === 1) || (k === "tab" && v === "products")) next.delete(k);
        else next.set(k, String(v));
      }
      const qs = next.toString();
      router.replace(qs ? `${pathname}?${qs}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  /** Từ một dòng tồn kho mở tab Nhật ký lọc sẵn theo mặt hàng đó. */
  const openLogs = (item: InventoryItem) =>
    setQuery({ tab: "logs", item: `${item.type === "PRODUCT" ? "p" : "c"}${item.id}`, itemName: item.name }, true);

  return (
    <>
      <PageHeader title="Tồn kho" />
      <Card>
        <Tabs
          activeKey={tab}
          onChange={(k) => setQuery({ tab: k }, true)}
          destroyOnHidden
          items={[
            { key: "products", label: "Sản phẩm", children: <StockTable type="PRODUCT" setQuery={setQuery} onLogs={openLogs} /> },
            { key: "combos", label: "Combo", children: <StockTable type="COMBO" setQuery={setQuery} onLogs={openLogs} /> },
            { key: "logs", label: "Nhật ký kho", children: <LogTable setQuery={setQuery} /> },
          ]}
        />
      </Card>
    </>
  );
}

type SetQuery = (patch: Record<string, string | number | undefined>, replaceAll?: boolean) => void;

function StockTable({ type, setQuery, onLogs }: { type: InventoryItemType; setQuery: SetQuery; onLogs: (item: InventoryItem) => void }) {
  const params = useSearchParams();
  const { user } = useAdmin();
  const isAdmin = user?.role === "ADMIN";
  const q = params.get("q") ?? "";
  const lowStock = params.get("low") === "1";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<InventoryItem[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState(q);
  const [adjusting, setAdjusting] = useState<{ item: InventoryItem; mode: AdjustMode } | null>(null);
  const [packing, setPacking] = useState<{ item: InventoryItem; mode: PackMode } | null>(null);

  useEffect(() => setSearch(q), [q]);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    listInventory({ type, q: q || undefined, lowStock: lowStock || undefined, page, limit })
      .then((res) => {
        setRows(res.data);
        setPagination(res.pagination);
      })
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, [type, q, lowStock, page, limit]);

  useEffect(load, [load]);

  const editHref = (i: InventoryItem) => (i.type === "PRODUCT" ? `/admin/products/${i.id}` : `/admin/combos/${i.id}`);

  const productActions = (i: InventoryItem) => (
    <Space size={4}>
      <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setAdjusting({ item: i, mode: "IMPORT" })}>
        Nhập
      </Button>
      <Button size="small" icon={<MinusOutlined />} disabled={i.availableQuantity <= 0} onClick={() => setAdjusting({ item: i, mode: "EXPORT" })}>
        Xuất
      </Button>
      <Dropdown
        trigger={["click"]}
        placement="bottomRight"
        menu={{
          items: [
            { key: "count", icon: <SyncOutlined />, label: "Kiểm kê", onClick: () => setAdjusting({ item: i, mode: "ADJUST" }) },
            { key: "logs", icon: <HistoryOutlined />, label: "Xem nhật ký", onClick: () => onLogs(i) },
          ],
        }}
      >
        <Button size="small" type="text" icon={<MoreOutlined />} aria-label={`Thao tác khác với ${i.name}`} />
      </Dropdown>
    </Space>
  );

  const comboActions = (i: InventoryItem) => (
    <Space size={4}>
      <Tooltip title={i.maxPackable ? `Đóng gói được tối đa ${i.maxPackable} bộ` : "Hàng lẻ không đủ để đóng gói"}>
        <Button size="small" type="primary" icon={<GiftOutlined />} disabled={!i.maxPackable} onClick={() => setPacking({ item: i, mode: "pack" })}>
          Đóng gói
        </Button>
      </Tooltip>
      <Button size="small" icon={<ScissorOutlined />} disabled={i.availableQuantity <= 0} onClick={() => setPacking({ item: i, mode: "unpack" })}>
        Tháo
      </Button>
      <Dropdown
        trigger={["click"]}
        placement="bottomRight"
        menu={{
          items: [
            {
              key: "export",
              icon: <MinusOutlined />,
              label: "Xuất bộ hỏng / mất",
              disabled: i.availableQuantity <= 0,
              onClick: () => setAdjusting({ item: i, mode: "EXPORT" }),
            },
            { key: "logs", icon: <HistoryOutlined />, label: "Xem nhật ký", onClick: () => onLogs(i) },
          ],
        }}
      >
        <Button size="small" type="text" icon={<MoreOutlined />} aria-label={`Thao tác khác với ${i.name}`} />
      </Dropdown>
    </Space>
  );

  const columns: TableProps<InventoryItem>["columns"] = [
    {
      title: type === "PRODUCT" ? "Sản phẩm" : "Combo",
      key: "item",
      render: (_, i) => (
        <div className="cv-prod-cell">
          {i.imageUrl ? <Image src={i.imageUrl} alt={i.name} width={44} height={44} className="cv-prod-thumb" /> : <div className="cv-img-empty">—</div>}
          <div className="cv-prod-info">
            {isAdmin ? (
              <Link href={editHref(i)} className="cv-prod-name" title={i.name}>
                {i.name}
              </Link>
            ) : (
              <span className="cv-prod-name" title={i.name}>
                {i.name}
              </span>
            )}
            {!i.isActive && (
              <Typography.Text type="secondary" className="cv-prod-slug">
                Đang ẩn trên cửa hàng
              </Typography.Text>
            )}
          </div>
        </div>
      ),
    },
    { title: "Tồn kho", dataIndex: "stockQuantity", width: 100, align: "right" },
    {
      title: (
        <Tooltip title="Đã nằm trong đơn chờ xử lý, chưa trừ hẳn khỏi kho">
          <span>Giữ chỗ</span>
        </Tooltip>
      ),
      dataIndex: "reservedQuantity",
      width: 100,
      align: "right",
      render: (v: number) => (v > 0 ? v : <Typography.Text type="secondary">0</Typography.Text>),
    },
    {
      title: (
        <Tooltip title="Bán được = Tồn kho − Giữ chỗ">
          <span>Khả dụng</span>
        </Tooltip>
      ),
      key: "available",
      width: 130,
      render: (_, i) => <AvailableTag item={i} />,
    },
    {
      key: "actions",
      width: type === "PRODUCT" ? 210 : 230,
      fixed: "right",
      render: (_, i) => (type === "PRODUCT" ? productActions(i) : comboActions(i)),
    },
  ];

  return (
    <>
      <div className="cv-table-toolbar">
        <Space wrap>
          <Input.Search
            allowClear
            placeholder={type === "PRODUCT" ? "Tìm sản phẩm (gõ không dấu được)" : "Tìm combo"}
            style={{ width: 320 }}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onSearch={(v) => setQuery({ q: v.trim(), page: 1 })}
          />
          <Space size={6}>
            <Switch size="small" checked={lowStock} onChange={(v) => setQuery({ low: v ? "1" : undefined, page: 1 })} />
            <span>Chỉ hàng sắp hết</span>
          </Space>
        </Space>
        <Tooltip title="Tải lại">
          <Button icon={<ReloadOutlined />} onClick={load} aria-label="Tải lại" />
        </Tooltip>
      </div>

      {error ? (
        <Alert
          type="error"
          showIcon
          message="Không tải được tồn kho"
          description={error}
          action={
            <Button size="small" onClick={load}>
              Thử lại
            </Button>
          }
        />
      ) : (
        <Table<InventoryItem>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 760 }}
          locale={{ emptyText: <Empty description={q || lowStock ? "Không có mặt hàng nào khớp bộ lọc." : "Chưa có mặt hàng nào."} /> }}
          pagination={{
            current: pagination?.currentPage ?? page,
            pageSize: limit,
            total: pagination?.totalItems ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZES,
            showTotal: (t, [from, to]) => `${from}–${to} / ${t}`,
            onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size === DEFAULT_LIMIT ? undefined : size }),
          }}
        />
      )}

      {adjusting && (
        <AdjustModal
          item={adjusting.item}
          mode={adjusting.mode}
          onClose={() => setAdjusting(null)}
          onDone={() => {
            setAdjusting(null);
            load();
          }}
        />
      )}
      {packing && (
        <PackModal
          item={packing.item}
          mode={packing.mode}
          onClose={() => setPacking(null)}
          onDone={() => {
            setPacking(null);
            load();
          }}
        />
      )}
    </>
  );
}

/** Nhập / xuất / kiểm kê một mặt hàng. Kiểm kê: gõ số đếm thực tế, hệ thống tự tính chênh lệch. */
function AdjustModal({ item, mode, onClose, onDone }: { item: InventoryItem; mode: AdjustMode; onClose: () => void; onDone: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<{ quantity?: number; note?: string }>();
  const [saving, setSaving] = useState(false);
  const quantity = Form.useWatch("quantity", form);
  const isCombo = item.type === "COMBO";

  const change = quantity == null ? 0 : mode === "IMPORT" ? quantity : mode === "EXPORT" ? -quantity : quantity - item.stockQuantity;
  const after = item.stockQuantity + change;
  const title =
    mode === "IMPORT" ? `Nhập kho — ${item.name}` : mode === "EXPORT" ? `${isCombo ? "Xuất bộ hỏng / mất" : "Xuất kho"} — ${item.name}` : `Kiểm kê — ${item.name}`;

  const submit = async () => {
    const v = await form.validateFields();
    if (change === 0) {
      message.info("Số lượng không đổi — không có gì để lưu.");
      return;
    }
    setSaving(true);
    try {
      const log = await adjustStock({
        productId: isCombo ? undefined : item.id,
        comboId: isCombo ? item.id : undefined,
        changeQuantity: change,
        reason: mode,
        note: v.note?.trim() || null,
      });
      message.success(`Đã cập nhật "${item.name}": tồn kho ${item.stockQuantity} → ${log.stockAfter}.`);
      onDone();
    } catch (e) {
      message.error(e instanceof ApiError && e.message ? e.message : getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open title={title} okText="Lưu" cancelText="Huỷ" confirmLoading={saving} onOk={submit} onCancel={onClose} destroyOnHidden>
      <Form form={form} layout="vertical" requiredMark={false} initialValues={mode === "ADJUST" ? { quantity: item.stockQuantity } : undefined}>
        <div className="cv-mini-stats" style={{ marginBottom: 16 }}>
          <div>
            <span>Tồn kho</span>
            <b>{item.stockQuantity}</b>
          </div>
          <div>
            <span>Giữ chỗ</span>
            <b>{item.reservedQuantity}</b>
          </div>
          <div>
            <span>Sau khi lưu</span>
            <b className={after < item.reservedQuantity ? "is-bad" : "is-good"}>{after}</b>
          </div>
        </div>
        <Form.Item
          name="quantity"
          label={mode === "IMPORT" ? "Số lượng nhập" : mode === "EXPORT" ? "Số lượng xuất" : "Số lượng đếm được thực tế"}
          rules={[
            { required: true, message: "Vui lòng nhập số lượng." },
            {
              validator: async (_, v?: number) => {
                if (v == null) return;
                if (mode !== "ADJUST" && v < 1) throw new Error("Số lượng phải từ 1.");
                if (mode === "EXPORT" && v > item.availableQuantity)
                  throw new Error(`Chỉ xuất được tối đa ${item.availableQuantity} (phần còn lại đang giữ chỗ cho đơn hàng).`);
                if (mode === "ADJUST" && v < item.reservedQuantity)
                  throw new Error(`Không được thấp hơn số đang giữ chỗ cho đơn hàng (${item.reservedQuantity}).`);
              },
            },
          ]}
        >
          <InputNumber<number> min={0} max={100_000} precision={0} style={{ width: "100%" }} size="large" autoFocus />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú" style={{ marginBottom: 0 }} rules={[{ max: 500, message: "Tối đa 500 ký tự." }]}>
          <Input.TextArea
            autoSize={{ minRows: 2, maxRows: 4 }}
            maxLength={500}
            placeholder={mode === "IMPORT" ? "VD: Lô hàng NCC Naturehike 25/10" : mode === "EXPORT" ? "VD: Hàng lỗi trả nhà cung cấp" : "VD: Kiểm kho cuối tháng"}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** Đóng gói (lấy hàng lẻ ra tạo bộ combo) hoặc tháo (trả hàng lẻ về kho). */
function PackModal({ item, mode, onClose, onDone }: { item: InventoryItem; mode: PackMode; onClose: () => void; onDone: () => void }) {
  const { message } = App.useApp();
  const [sets, setSets] = useState<number | null>(1);
  const [saving, setSaving] = useState(false);
  const max = mode === "pack" ? (item.maxPackable ?? 0) : item.availableQuantity;
  const after = item.stockQuantity + (mode === "pack" ? (sets ?? 0) : -(sets ?? 0));

  const submit = async () => {
    if (!sets || sets < 1 || sets > max) return;
    setSaving(true);
    try {
      const updated = mode === "pack" ? await packCombo(item.id, sets) : await unpackCombo(item.id, sets);
      message.success(
        mode === "pack"
          ? `Đã đóng gói ${sets} bộ "${item.name}" — kho combo còn ${updated.stockQuantity} bộ.`
          : `Đã tháo ${sets} bộ "${item.name}" — hàng lẻ đã trả về kho.`,
      );
      onDone();
    } catch (e) {
      message.error(e instanceof ApiError && e.message ? e.message : getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      title={`${mode === "pack" ? "Đóng gói" : "Tháo"} — ${item.name}`}
      okText={mode === "pack" ? "Đóng gói" : "Tháo"}
      cancelText="Huỷ"
      okButtonProps={{ disabled: !sets || sets < 1 || sets > max }}
      confirmLoading={saving}
      onOk={submit}
      onCancel={onClose}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary">
        {mode === "pack"
          ? "Lấy hàng lẻ ra khỏi kho để tạo bộ combo: mỗi bộ trừ đúng số lượng từng sản phẩm trong combo."
          : "Tách bộ combo ra: hàng lẻ được cộng trả lại kho từng sản phẩm."}
      </Typography.Paragraph>
      <div className="cv-mini-stats" style={{ marginBottom: 16 }}>
        <div>
          <span>Kho combo hiện tại</span>
          <b>{item.stockQuantity}</b>
        </div>
        <div>
          <span>{mode === "pack" ? "Đóng gói được tối đa" : "Tháo được tối đa"}</span>
          <b>{max}</b>
        </div>
        <div>
          <span>Sau khi lưu</span>
          <b className="is-good">{after}</b>
        </div>
      </div>
      <InputNumber<number> min={1} max={Math.max(1, max)} precision={0} value={sets} onChange={setSets} size="large" style={{ width: "100%" }} suffix="bộ" autoFocus />
    </Modal>
  );
}

function LogTable({ setQuery }: { setQuery: SetQuery }) {
  const params = useSearchParams();
  const item = params.get("item") ?? "";
  const itemName = params.get("itemName") ?? "";
  const type = (params.get("ltype") ?? "") as InventoryItemType | "";
  const reason = (params.get("reason") ?? "") as InventoryReason | "";
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const page = Math.max(1, Number(params.get("page")) || 1);
  const limit = PAGE_SIZES.includes(Number(params.get("limit"))) ? Number(params.get("limit")) : DEFAULT_LIMIT;

  const [rows, setRows] = useState<InventoryLogEntry[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    const id = Number(item.slice(1));
    listInventoryLogs({
      productId: item.startsWith("p") && id ? id : undefined,
      comboId: item.startsWith("c") && id ? id : undefined,
      type: type || undefined,
      reason: reason || undefined,
      from: from || undefined,
      to: to || undefined,
      page,
      limit,
    })
      .then((res) => {
        setRows(res.data);
        setPagination(res.pagination);
      })
      .catch((e) => setError(getErrorMessage(e)))
      .finally(() => setLoading(false));
  }, [item, type, reason, from, to, page, limit]);

  useEffect(load, [load]);

  const columns: TableProps<InventoryLogEntry>["columns"] = [
    { title: "Thời gian", dataIndex: "createdAt", width: 150, render: (v: string) => fmtTime(v) },
    {
      title: "Mặt hàng",
      key: "item",
      render: (_, l) => (
        <Space size={6}>
          <Tag bordered={false}>{l.type === "PRODUCT" ? "SP" : "Combo"}</Tag>
          <span>{l.itemName}</span>
        </Space>
      ),
    },
    {
      title: "Thay đổi",
      dataIndex: "changeQuantity",
      width: 100,
      align: "right",
      render: (v: number) => <b style={{ color: v > 0 ? "#2f7a43" : "#c0392b" }}>{v > 0 ? `+${v}` : v}</b>,
    },
    { title: "Tồn sau", dataIndex: "stockAfter", width: 90, align: "right" },
    {
      title: "Lý do",
      dataIndex: "reason",
      width: 100,
      render: (r: InventoryReason) => (
        <Tag color={REASONS[r].color} bordered={false}>
          {REASONS[r].label}
        </Tag>
      ),
    },
    { title: "Ghi chú", dataIndex: "note", render: (v: string | null) => v ?? <Typography.Text type="secondary">—</Typography.Text> },
    { title: "Người thực hiện", key: "user", width: 160, render: (_, l) => l.user.fullName },
  ];

  const filtered = Boolean(item || type || reason || from || to);

  return (
    <>
      <div className="cv-table-toolbar">
        <Space wrap>
          {item && (
            <Tag closable color="blue" onClose={() => setQuery({ item: undefined, itemName: undefined, page: 1 })}>
              {itemName || "Mặt hàng đã chọn"}
            </Tag>
          )}
          <Segmented
            value={type || "all"}
            onChange={(v) => setQuery({ ltype: v === "all" ? undefined : (v as string), page: 1 })}
            options={[
              { value: "all", label: "Tất cả" },
              { value: "PRODUCT", label: "Sản phẩm" },
              { value: "COMBO", label: "Combo" },
            ]}
          />
          <Select
            allowClear
            placeholder="Mọi lý do"
            style={{ width: 150 }}
            value={reason || undefined}
            onChange={(v?: string) => setQuery({ reason: v, page: 1 })}
            options={(Object.keys(REASONS) as InventoryReason[]).map((r) => ({ value: r, label: REASONS[r].label }))}
          />
          <DatePicker.RangePicker
            format="DD/MM/YYYY"
            placeholder={["Từ ngày", "Đến ngày"]}
            onChange={(_, s) => {
              const toIso = (d: string) => (d ? d.split("/").reverse().join("-") : undefined);
              setQuery({ from: toIso(s[0]), to: toIso(s[1]), page: 1 });
            }}
          />
          {filtered && (
            <Button
              icon={<ClearOutlined />}
              onClick={() => setQuery({ item: undefined, itemName: undefined, ltype: undefined, reason: undefined, from: undefined, to: undefined, page: 1 })}
            >
              Xoá bộ lọc
            </Button>
          )}
        </Space>
        <Tooltip title="Tải lại">
          <Button icon={<ReloadOutlined />} onClick={load} aria-label="Tải lại" />
        </Tooltip>
      </div>

      {error ? (
        <Alert type="error" showIcon message="Không tải được nhật ký kho" description={error} />
      ) : (
        <Table<InventoryLogEntry>
          rowKey="id"
          size="middle"
          columns={columns}
          dataSource={rows}
          loading={loading}
          scroll={{ x: 900 }}
          locale={{ emptyText: <Empty description={filtered ? "Không có dòng nhật ký nào khớp bộ lọc." : "Chưa có thay đổi tồn kho nào."} /> }}
          pagination={{
            current: pagination?.currentPage ?? page,
            pageSize: limit,
            total: pagination?.totalItems ?? 0,
            showSizeChanger: true,
            pageSizeOptions: PAGE_SIZES,
            showTotal: (t, [a, b]) => `${a}–${b} / ${t} dòng`,
            onChange: (p, size) => setQuery({ page: size !== limit ? 1 : p, limit: size === DEFAULT_LIMIT ? undefined : size }),
          }}
        />
      )}
    </>
  );
}
