package vn.campvivo.order.returns;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.auth.CurrentUserId;
import vn.campvivo.common.dto.ApiResponse;
import vn.campvivo.order.returns.dto.ReturnRequestResponse;

import java.util.List;

/** Khách gửi yêu cầu trả hàng — api.md mục 4.5 (quyền CUSTOMER theo `/api/orders/**`, SecurityConfig). */
@RestController
@RequestMapping("/api/orders")
public class ReturnController {

    private final ReturnService returnService;

    public ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    /** multipart: `reason` (mã ReturnReason), `description?` (≤ 2000), `images` (1–2 ảnh JPG/PNG/WEBP ≤ 5MB). */
    @PostMapping(path = "/{id}/returns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReturnRequestResponse> create(@CurrentUserId Long userId, @PathVariable Long id,
                                                     @RequestParam(required = false) String reason,
                                                     @RequestParam(required = false) String description,
                                                     @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.of(returnService.create(userId, id, reason, description, images));
    }
}
