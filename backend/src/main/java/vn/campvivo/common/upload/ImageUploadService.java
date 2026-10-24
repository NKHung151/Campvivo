package vn.campvivo.common.upload;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
public class ImageUploadService {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> FOLDERS = Set.of("products", "combos", "articles", "campaigns");

    private final ImageStorage storage;

    public ImageUploadService(ImageStorage storage) {
        this.storage = storage;
    }

    public String upload(MultipartFile file, String folder) {
        if (!FOLDERS.contains(folder)) {
            throw fieldError("folder", "Thư mục ảnh không hợp lệ.");
        }
        if (file == null || file.isEmpty()) {
            throw fieldError("file", "Vui lòng chọn một ảnh.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw fieldError("file", "Ảnh vượt quá 5MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw fieldError("file", "Không đọc được file ảnh.");
        }
        if (!isSupportedImage(bytes)) {
            throw fieldError("file", "Chỉ chấp nhận ảnh JPG, PNG hoặc WEBP.");
        }
        return storage.store(bytes, folder);
    }

    static boolean isSupportedImage(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return true;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return true;
        }
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private static BusinessException fieldError(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                Map.of("fieldErrors", Map.of(field, message)));
    }
}
