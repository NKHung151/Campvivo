"use client";

import { useCallback, useEffect, useState } from "react";
import { getDistricts, getProvinces, getWards } from "@/services/shippingService";
import { getErrorMessage } from "@/lib/errorMessages";

/**
 * 3 dropdown Tỉnh/Thành → Quận/Huyện → Phường/Xã theo danh mục GHN (api.md 4.12, D-27).
 * Đổi tỉnh thì xoá quận + phường, đổi quận thì xoá phường. Trả về cả mã (để tính phí/tạo vận đơn) lẫn tên (để hiển thị).
 */

export interface AddressCodes {
  provinceId?: number;
  districtId?: number;
  wardCode?: string;
}

export interface AddressSelection extends AddressCodes {
  province: string;
  district: string;
  ward: string;
}

interface Option {
  value: string;
  label: string;
}

// Danh mục địa chỉ ít đổi: nhớ theo phiên trang để mở lại form không tải lại (lỗi thì bỏ để lần sau hỏi lại).
const optionCache = new Map<string, Promise<Option[]>>();

function loadOptions(key: string, fetcher: () => Promise<Option[]>): Promise<Option[]> {
  let pending = optionCache.get(key);
  if (!pending) {
    pending = fetcher();
    optionCache.set(key, pending);
    pending.catch(() => optionCache.delete(key));
  }
  return pending;
}

type ListState = { status: "idle" | "loading" | "ready"; options: Option[] } | { status: "error"; options: Option[]; message: string };

const IDLE: ListState = { status: "idle", options: [] };

/** Tải một danh sách khi `key` khác null; đổi key thì bỏ kết quả cũ đang chờ. */
function useOptions(key: string | null, fetcher: () => Promise<Option[]>): [ListState, () => void] {
  const [state, setState] = useState<ListState>(IDLE);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!key) {
      setState(IDLE);
      return;
    }
    let active = true;
    setState({ status: "loading", options: [] });
    loadOptions(key, fetcher)
      .then((options) => active && setState({ status: "ready", options }))
      .catch((err: unknown) => active && setState({ status: "error", options: [], message: getErrorMessage(err) }));
    return () => {
      active = false;
    };
    // fetcher đổi theo key — chỉ cần theo dõi key và lượt thử lại
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return [state, retry];
}

export function AddressSelects({ value, onChange }: { value: AddressCodes; onChange: (next: AddressSelection) => void }) {
  const { provinceId, districtId, wardCode } = value;

  const [provinces, retryProvinces] = useOptions("provinces", () =>
    getProvinces().then((list) => list.map((p) => ({ value: String(p.id), label: p.name }))),
  );
  const [districts, retryDistricts] = useOptions(provinceId ? `districts:${provinceId}` : null, () =>
    getDistricts(provinceId!).then((list) => list.map((d) => ({ value: String(d.id), label: d.name }))),
  );
  const [wards, retryWards] = useOptions(districtId ? `wards:${districtId}` : null, () =>
    getWards(districtId!).then((list) => list.map((w) => ({ value: w.code, label: w.name }))),
  );

  const labelOf = (list: ListState, v: string) => list.options.find((o) => o.value === v)?.label ?? "";

  const pickProvince = (v: string) =>
    onChange({ provinceId: v ? Number(v) : undefined, province: labelOf(provinces, v), district: "", ward: "" });

  const pickDistrict = (v: string) =>
    onChange({
      provinceId,
      province: labelOf(provinces, String(provinceId ?? "")),
      districtId: v ? Number(v) : undefined,
      district: labelOf(districts, v),
      ward: "",
    });

  const pickWard = (v: string) =>
    onChange({
      provinceId,
      province: labelOf(provinces, String(provinceId ?? "")),
      districtId,
      district: labelOf(districts, String(districtId ?? "")),
      wardCode: v || undefined,
      ward: labelOf(wards, v),
    });

  return (
    <>
      <div className="col-6">
        <AddressSelect
          id="ddlProvince"
          placeholder="Chọn Tỉnh/Thành phố"
          value={provinceId ? String(provinceId) : ""}
          state={provinces}
          onChange={pickProvince}
          onRetry={retryProvinces}
        />
      </div>
      <div className="col-6">
        <AddressSelect
          id="ddlDistrict"
          placeholder={provinceId ? "Chọn Quận/Huyện" : "Chọn Tỉnh/Thành phố trước"}
          value={districtId ? String(districtId) : ""}
          state={districts}
          onChange={pickDistrict}
          onRetry={retryDistricts}
        />
      </div>
      <div className="col-6">
        <AddressSelect
          id="ddlWard"
          placeholder={districtId ? "Chọn Phường/Xã" : "Chọn Quận/Huyện trước"}
          value={wardCode ?? ""}
          state={wards}
          onChange={pickWard}
          onRetry={retryWards}
        />
      </div>
    </>
  );
}

function AddressSelect({
  id,
  placeholder,
  value,
  state,
  onChange,
  onRetry,
}: {
  id: string;
  placeholder: string;
  value: string;
  state: ListState;
  onChange: (v: string) => void;
  onRetry: () => void;
}) {
  const loading = state.status === "loading";
  return (
    <>
      <select
        id={id}
        className="select-single"
        value={state.status === "ready" ? value : ""}
        onChange={(e) => onChange(e.target.value)}
        disabled={state.status !== "ready"}
        required
      >
        <option value="">{loading ? "Đang tải..." : placeholder}</option>
        {state.options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {state.status === "error" && (
        <div className="note text-danger">
          {state.message}{" "}
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              onRetry();
            }}
          >
            Thử lại
          </a>
        </div>
      )}
    </>
  );
}
