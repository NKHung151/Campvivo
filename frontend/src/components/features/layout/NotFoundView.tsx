import Link from "next/link";
import { Shell } from "@/components/features/layout/Shell";

export function NotFoundView() {
  return (
    <Shell page="shop">
      <div className="pageContent cv-success">
        <h1>Không tìm thấy trang</h1>
        <p>Trang bạn tìm không tồn tại hoặc đã bị gỡ.</p>
        <div className="btns">
          <Link className="cv-btn" href="/">
            Về trang chủ
          </Link>
          <Link className="cv-btn ghost" href="/products">
            Xem sản phẩm
          </Link>
        </div>
      </div>
    </Shell>
  );
}
