package vn.campvivo.chatbot.common.dto;

/**
 * Tham số phân trang đã chuẩn hoá theo api.md mục 1: page từ 1, limit mặc định 12, tối đa 100.
 */
public record PageQuery(int page, int limit) {

    public static PageQuery of(Integer page, Integer limit) {
        int p = page == null || page < 1 ? 1 : page;
        int l = limit == null || limit < 1 ? 12 : Math.min(limit, 100);
        return new PageQuery(p, l);
    }

    public int offset() {
        return (page - 1) * limit;
    }
}
