package vn.campvivo.user.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;
import vn.campvivo.user.admin.AdminUserResponses.Summary;

import static vn.campvivo.user.UserSpecifications.hasRole;
import static vn.campvivo.user.UserSpecifications.isActive;
import static vn.campvivo.user.UserSpecifications.matchesKeyword;

/** Quản lý người dùng cho Admin (FR-14). Phần danh sách (N3-06); chi tiết và sửa role / khoá làm ở N3-07. */
@Service
public class AdminUserService {

    /** Mới nhất trước; `id` làm khoá phụ để thứ tự ổn định khi nhiều tài khoản cùng thời điểm (phân trang không nhảy dòng). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(AdminUserQuery query) {
        Specification<User> spec = Specification.<User>where(matchesKeyword(query.q()))
                .and(hasRole(query.role()))
                .and(isActive(query.isActive()));
        Page<User> result = userRepository.findAll(spec, PageRequest.of(query.page() - 1, query.limit(), NEWEST_FIRST));
        return PageResponse.of(result.map(Summary::from));
    }
}
