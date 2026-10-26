package vn.campvivo.common.upload;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/admin/uploads")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUploadController {

    private final ImageUploadService imageUploadService;

    public AdminUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    public record UploadResponse(String url) {}

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadResponse> uploadImage(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam(defaultValue = "products") String folder) {
        return ApiResponse.of(new UploadResponse(imageUploadService.upload(file, folder)));
    }
}
