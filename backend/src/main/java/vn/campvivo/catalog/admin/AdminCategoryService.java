package vn.campvivo.catalog.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.Category;
import vn.campvivo.catalog.CategoryRepository;
import vn.campvivo.catalog.admin.AdminCategoryResponses.Node;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;
import vn.campvivo.common.util.SlugUtil;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Quản lý danh mục đa cấp — FR-05, BR-13 (docs/02-ky-thuat/database.md mục 5).
 * <ul>
 *   <li>Slug bỏ trống: tạo mới thì sinh từ tên (thêm -2, -3 nếu trùng); sửa thì giữ slug cũ. Tự nhập mà trùng → 409.</li>
 *   <li>Đổi cha: chặn chọn chính nó/danh mục con của nó (CIRCULAR_CATEGORY_DEPENDENCY); path cả cây con đổi theo.</li>
 *   <li>Xoá: chặn nếu còn danh mục con, sản phẩm hoặc combo (CATEGORY_NOT_EMPTY).</li>
 *   <li>Khung thông số (D-21): danh sách tên, rỗng = thừa hưởng từ danh mục cha gần nhất.</li>
 * </ul>
 */
@Service
public class AdminCategoryService {

    private static final String DEFAULT_SLUG = "danh-muc";

    private final CategoryRepository repository;
    private final Clock clock;

    public AdminCategoryService(CategoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Node> tree() {
        Map<Long, Long> products = counts(repository.countProductsByCategory());
        Map<Long, Long> combos = counts(repository.countCombosByCategory());
        List<Category> all = repository.findAllForTree();
        Map<Long, List<Category>> childrenOf = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                childrenOf.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        return roots.stream().map(r -> toNode(r, childrenOf, products, combos)).toList();
    }

    @Transactional
    public Node create(CategoryRequest req) {
        Category parent = req.parentId() == null ? null : findParent(req.parentId());
        String slug = isBlank(req.slug()) ? uniqueSlugFrom(req.name()) : requireFreeSlug(req.slug(), null);
        Category c = repository.saveAndFlush(new Category(parent, req.name().trim(), slug, OffsetDateTime.now(clock)));
        c.assignPath();
        return toNode(c, Map.of(), Map.of(), Map.of());
    }

    @Transactional
    public Node update(Long id, CategoryRequest req) {
        Category c = find(id);
        String slug = isBlank(req.slug()) ? c.getSlug() : requireFreeSlug(req.slug(), id);
        OffsetDateTime now = OffsetDateTime.now(clock);
        c.update(req.name().trim(), slug, now);

        if (!Objects.equals(req.parentId(), c.getParentId())) {
            Category parent = req.parentId() == null ? null : findParent(req.parentId());
            if (parent != null && parent.getPath().startsWith(c.getPath())) {
                throw new BusinessException(ErrorCode.CIRCULAR_CATEGORY_DEPENDENCY);
            }
            String oldPrefix = c.getPath();
            String newPrefix = (parent == null ? "/" : parent.getPath()) + c.getId() + "/";
            c.moveTo(parent, now);
            repository.movePaths(oldPrefix, newPrefix);
            c = find(id);
        }
        return toNode(c, Map.of(), Map.of(), Map.of());
    }

    @Transactional
    public void delete(Long id) {
        Category c = find(id);
        if (repository.existsByParent_Id(c.getId()) || repository.hasItems(c.getId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_EMPTY);
        }
        repository.delete(c);
    }

    // ---------------------------------------------------------------------------------------------

    private Node toNode(Category c, Map<Long, List<Category>> childrenOf, Map<Long, Long> products, Map<Long, Long> combos) {
        List<Node> children = childrenOf.getOrDefault(c.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenOf, products, combos))
                .toList();
        return new Node(c.getId(), c.getName(), c.getSlug(), c.getParentId(), products.getOrDefault(c.getId(), 0L),
                combos.getOrDefault(c.getId(), 0L), children);
    }

    private static Map<Long, Long> counts(List<Object[]> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] r : rows) {
            out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return out;
    }

    private Category find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy danh mục.", null));
    }

    private Category findParent(Long parentId) {
        return repository.findById(parentId).orElseThrow(() -> new BusinessException(ErrorCode.PARENT_CATEGORY_NOT_FOUND));
    }


    private String requireFreeSlug(String slug, Long excludeId) {
        boolean taken = excludeId == null ? repository.existsBySlug(slug) : repository.existsBySlugAndIdNot(slug, excludeId);
        if (taken) {
            throw new BusinessException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        return slug;
    }

    private String uniqueSlugFrom(String name) {
        String base = SlugUtil.toSlug(name);
        if (base.isEmpty()) {
            base = DEFAULT_SLUG;
        }
        String candidate = base;
        for (int n = 2; repository.existsBySlug(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
