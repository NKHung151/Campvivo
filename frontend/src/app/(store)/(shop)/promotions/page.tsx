import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { ListingView } from "@/components/features/catalog/ListingView";
import { getPromotionsListing } from "@/services/catalogService";

export const metadata: Metadata = { title: "Khuyến mãi | Campvivo" };

/** /promotions — mọi sản phẩm đang giảm giá (FR-17, BR-6): lọc thương hiệu/khoảng giá, sắp xếp, phân trang. */
export default async function PromotionsPage() {
  const page = await getPromotionsListing();
  return (
    <Shell page="shop">
      <ListingView
        title="Khuyến mãi"
        breadcrumbs={[{ name: "Khuyến mãi", href: "/promotions" }]}
        intro={null}
        sideTitle="Chương trình đang diễn ra"
        sideCats={page.sideCats}
        subCats={[]}
        filters={page.filters}
        guide={null}
        products={page.products}
        bottomHtml={null}
        commentsKey="promotions"
        priceFilter
        pageSize={24}
        hideSaleFilter
        emptyText={page.products.length ? undefined : "Hiện chưa có sản phẩm nào đang giảm giá. Quay lại sau nhé!"}
      />
    </Shell>
  );
}
