package vn.campvivo.review.moderation;

import java.util.List;

/** {@code flaggedTerms}: các mục từ điển bị bắt (đúng như viết trong file), không trùng, tổng ≤ 500 ký tự khi nối bằng dấu phẩy. */
public record ModerationResult(boolean sensitive, List<String> flaggedTerms) {

    public static final ModerationResult SAFE = new ModerationResult(false, List.of());
}
