package vn.campvivo.user.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.order.OrderQueryService;
import vn.campvivo.user.Role;
import vn.campvivo.user.User;
import vn.campvivo.user.UserRepository;
import vn.campvivo.user.admin.AdminUserResponses.Detail;
import vn.campvivo.user.admin.AdminUserResponses.Summary;

import java.util.Map;

import static vn.campvivo.user.UserSpecifications.hasRole;
import static vn.campvivo.user.UserSpecifications.isActive;
import static vn.campvivo.user.UserSpecifications.matchesKeyword;

/** Quản lý người dùng cho Admin (FR-14): danh sách (N3-06), chi tiết và sửa tên / SĐT / role / khoá (N3-07). */
@Service
public class AdminUserService {

    /** Mới nhất trước; `id` làm khoá phụ để thứ tự ổn định khi nhiều tài khoản cùng thời điểm (phân trang không nhảy dòng). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final UserRepository userRepository;
    private final OrderQueryService orderQueryService;

    public AdminUserService(UserRepository userRepository, OrderQueryService orderQueryService) {
        this.userRepository = userRepository;
        this.orderQueryService = orderQueryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(AdminUserQuery query) {
        Specification<User> spec = Specification.<User>where(matchesKeyword(query.q()))
                .and(hasRole(query.role()))
                .and(isActive(query.isActive()));
        Page<User> result = userRepository.findAll(spec, PageRequest.of(query.page() - 1, query.limit(), NEWEST_FIRST));
        return PageResponse.of(result.map(Summary::from));
    }

    /** Chi tiết một người dùng kèm thống kê đơn. Không tồn tại → 404 NOT_FOUND. */
    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return detail(findOrThrow(id));
    }

    /**
     * Sửa từng phần (FR-14). Tên và SĐT cùng luật với tự sửa hồ sơ (N3-05). `role` / `isActive` chỉ tăng token_version khi đổi giá
     * trị thật, bằng cùng một câu UPDATE nguyên tử nên token của người đó bị từ chối ngay. Admin KHÔNG được tự khoá mình hay tự đổi
     * role của mình (CANNOT_MODIFY_SELF) — vẫn tự sửa được tên và SĐT. Không chặn trường hợp hai admin đồng thời hạ quyền lẫn nhau
     * (để hệ thống không còn admin); đã ghi trong api.md.
     */
    @Transactional
    public Detail update(Long id, AdminUpdateUserRequest request, Long actorId) {
        User user = findOrThrow(id);
        Role newRole = request.role() != null ? request.role() : user.getRole();
        boolean newActive = request.isActive() != null ? request.isActive() : user.isActive();
        boolean accessChanges = newRole != user.getRole() || newActive != user.isActive();

        if (id.equals(actorId) && accessChanges) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SELF);
        }

        if (request.fullName() != null) {
            String fullName = request.fullName().trim();
            if (fullName.length() < 2) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                        Map.of("fieldErrors", Map.of("fullName", "Họ tên phải từ 2 đến 100 ký tự")));
            }
            user.setFullName(fullName);
        }
        if (request.phone() != null) {
            String phone = request.phone().trim();
            user.setPhone(phone.isEmpty() ? null : phone);
        }
        if (accessChanges) {
            // Câu UPDATE tự flush phần sửa tên / SĐT ở trên rồi đặt role, is_active và tăng token_version cùng lúc.
            userRepository.changeAccess(id, newRole, newActive);
        }
        return detail(findOrThrow(id));
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Detail detail(User user) {
        return Detail.from(user, orderQueryService.statsForUser(user.getId()));
    }
}
