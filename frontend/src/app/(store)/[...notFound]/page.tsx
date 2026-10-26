import { notFound } from "next/navigation";

/**
 * Dự án có hai root layout ((store), (admin)) nên không có `app/not-found.tsx` dùng chung. Mọi URL
 * không khớp route nào rơi vào catch-all này để hiển thị trang 404 của storefront
 * (`(store)/not-found.tsx`) thay vì trang 404 mặc định của Next.
 */
export default function UnmatchedRoute() {
  notFound();
}
