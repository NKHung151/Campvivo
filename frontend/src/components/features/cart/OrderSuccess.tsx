"use client";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { vnd } from "@/lib/format";
import { useShop } from "@/components/features/shop/ShopProvider";
import { OrderCard } from "@/components/features/cart/OrderCard";

/**
 * Order confirmation. campvivo.vn's real confirmation is only reachable after placing a real order,
 * so this page is authored in the site's visual language rather than copied.
 */
export function OrderSuccess() {
  const id = useSearchParams().get("id");
  const { orders, ready, user, openLogin } = useShop();
  const order = orders.find((o) => o.id === id);
  if (!ready) return <div className="pageContent" style={{ minHeight: 400 }} />;
  if (!order) {
    return (
      <div className="pageContent cv-success">
        <h1>Không tìm thấy đơn hàng</h1>
        <p>Đơn hàng có thể đã được đặt trên trình duyệt khác.</p>
        <div className="btns">
          <Link className="cv-btn" href="/">
            Về trang chủ
          </Link>
        </div>
      </div>
    );
  }
  return (
    <div className="pageContent cv-success">
      <div className="ic">✓</div>
      <h1>Đặt hàng thành công!</h1>
      <p>
        Cảm ơn {order.customer.gender} <b>{order.customer.name}</b> đã mua sắm tại Campvivo. Mã đơn hàng: <b>{order.id}</b>
      </p>
      <p>
        Nhân viên CSKH sẽ gọi xác nhận qua số <b>{order.customer.phone}</b>. Tổng thanh toán: <b style={{ color: "#c7370f" }}>{vnd(order.total)}</b>
      </p>
      <div className="cv-demo-note">Bản demo: đơn hàng chỉ được lưu trong trình duyệt này, không có giao dịch thật.</div>
      <div className="box">
        <OrderCard order={order} />
      </div>
      {!user && (
        <p>
          <a
            href="#"
            onClick={(e) => {
              e.preventDefault();
              openLogin();
            }}
          >
            Đăng nhập
          </a>{" "}
          để theo dõi đơn hàng và tích điểm WePoint.
        </p>
      )}
      <div className="btns">
        <Link className="cv-btn ghost" href="/products">
          Tiếp tục mua sắm
        </Link>
        <Link className="cv-btn" href="/account">
          Xem đơn hàng của tôi
        </Link>
      </div>
    </div>
  );
}
