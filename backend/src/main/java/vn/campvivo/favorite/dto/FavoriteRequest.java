package vn.campvivo.favorite.dto;

/** `POST /favorites` — đúng một trong ba id (kiểm ở `FavoriteService.target`). */
public record FavoriteRequest(Long productId, Long comboId, Long articleId) {}
