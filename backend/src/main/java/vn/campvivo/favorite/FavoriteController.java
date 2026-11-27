package vn.campvivo.favorite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.common.dto.PageResponse;
import vn.campvivo.favorite.dto.FavoriteRequest;
import vn.campvivo.favorite.dto.FavoriteResponses;

/**
 * Yêu thích — api.md mục 4.2 (`/users/me/favorites[/ids]`) và 4.6 (`/favorites`), FR-20, D-47. Chỉ CUSTOMER (SecurityConfig).
 */
@RestController
@Validated
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 201 khi vừa thêm, 200 (id cũ) khi đã thích từ trước. */
    @PostMapping("/api/favorites")
    public ResponseEntity<ApiResponse<FavoriteResponses.Created>> add(@CurrentUserId Long userId,
                                                                       @RequestBody FavoriteRequest request) {
        FavoriteService.AddResult result = favoriteService.add(userId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.of(new FavoriteResponses.Created(result.id())));
    }

    @DeleteMapping("/api/favorites/{id}")
    public ApiResponse<Void> remove(@CurrentUserId Long userId, @PathVariable Long id) {
        favoriteService.remove(userId, id);
        return ApiResponse.of(null);
    }

    /** Bỏ thích từ trang chi tiết; chưa thích vẫn 200. */
    @DeleteMapping("/api/favorites")
    public ApiResponse<Void> removeByItem(@CurrentUserId Long userId,
                                          @RequestParam(required = false) Long productId,
                                          @RequestParam(required = false) Long comboId,
                                          @RequestParam(required = false) Long articleId) {
        favoriteService.remove(userId, FavoriteService.target(productId, comboId, articleId));
        return ApiResponse.of(null);
    }

    @GetMapping("/api/users/me/favorites")
    public PageResponse<FavoriteResponses.Item> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) FavoriteType type,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "Trang phải từ 1.") int page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "Số mục mỗi trang từ 1 đến 100.")
            @Max(value = 100, message = "Số mục mỗi trang từ 1 đến 100.") int limit) {
        return favoriteService.list(userId, type, page, limit);
    }

    @GetMapping("/api/users/me/favorites/ids")
    public ApiResponse<FavoriteResponses.Ids> ids(@CurrentUserId Long userId) {
        return ApiResponse.of(favoriteService.ids(userId));
    }
}
