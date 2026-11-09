package vn.campvivo.review.moderation;

/**
 * Kiểm tra nội dung đánh giá có ngôn từ không phù hợp (D-33). Kết quả chỉ dùng để làm mờ, không chặn đăng.
 * Hiện chỉ có {@link DictionaryModerator}; lớp AI (nếu làm sau) cắm thêm qua interface này.
 */
public interface ContentModerator {

    /** {@code content} null/rỗng → {@link ModerationResult#SAFE}. */
    ModerationResult check(String content);
}
