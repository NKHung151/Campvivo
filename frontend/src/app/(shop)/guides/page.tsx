import type { Metadata } from "next";
import { Shell } from "@/components/features/layout/Shell";
import { VideoHtml } from "@/components/features/content/VideoHtml";
import { getCampvivology } from "@/services/catalogService";

export const metadata: Metadata = { title: "Campvivology – Kiến thức & kinh nghiệm outdoor | Campvivo" };

// Knowledge hub: Campvivo's own heading/intro, then the category/article/video sections from mock data.
export default function GuidesPage() {
  const g = getCampvivology();
  return (
    <Shell page="guides">
      <div className="campvivology">
        <div className="container">
          <h1 className="campvivology_heading">CAMPVIVOLOGY</h1>
          <div className="campvivology_desc">
            Campvivology là góc kiến thức outdoor của Campvivo: hướng dẫn chọn và dùng trang bị cắm trại, leo núi, chèo
            thuyền… theo từng điều kiện thực tế, giúp bạn chuẩn bị đủ và đúng cho mỗi chuyến đi.
          </div>
        </div>
      </div>
      <VideoHtml className="campvivology" html={g?.html ?? ""} />
    </Shell>
  );
}
