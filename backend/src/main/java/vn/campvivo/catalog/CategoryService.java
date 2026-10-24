package vn.campvivo.catalog;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.campvivo.catalog.dto.CategoryNodeResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh mục đa cấp (BR-13). Hiện có: dựng cây cho GET /categories và ô chọn danh mục ở admin.
 * Tạo/sửa/xoá danh mục: N2-18.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryNodeResponse> tree() {
        List<Category> all = categoryRepository.findAllForTree();
        Map<Long, List<Category>> childrenOf = new LinkedHashMap<>();
        List<Category> roots = new ArrayList<>();
        for (Category c : all) {
            if (c.getParentId() == null) {
                roots.add(c);
            } else {
                childrenOf.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        return roots.stream().map(r -> toNode(r, childrenOf)).toList();
    }

    private CategoryNodeResponse toNode(Category c, Map<Long, List<Category>> childrenOf) {
        List<CategoryNodeResponse> children = childrenOf.getOrDefault(c.getId(), List.of()).stream()
                .map(child -> toNode(child, childrenOf))
                .toList();
        return new CategoryNodeResponse(c.getId(), c.getName(), c.getSlug(), children);
    }
}
