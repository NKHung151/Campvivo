// District lists for the two cities most orders ship to. Other provinces fall back to free-text input
// (districts/wards for other provinces will come from the address API).
export const DISTRICTS: Record<string, string[]> = {
  "Thành phố Hà Nội": [
    "Quận Ba Đình", "Quận Hoàn Kiếm", "Quận Tây Hồ", "Quận Long Biên", "Quận Cầu Giấy", "Quận Đống Đa",
    "Quận Hai Bà Trưng", "Quận Hoàng Mai", "Quận Thanh Xuân", "Quận Nam Từ Liêm", "Quận Bắc Từ Liêm", "Quận Hà Đông",
    "Huyện Gia Lâm", "Huyện Đông Anh", "Huyện Thanh Trì", "Huyện Sóc Sơn",
  ],
  "Thành phố Hồ Chí Minh": [
    "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6", "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
    "Quận Bình Thạnh", "Quận Gò Vấp", "Quận Phú Nhuận", "Quận Tân Bình", "Quận Tân Phú", "Quận Bình Tân",
    "Thành phố Thủ Đức", "Huyện Bình Chánh", "Huyện Hóc Môn", "Huyện Nhà Bè", "Huyện Củ Chi",
  ],
};
