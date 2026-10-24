package vn.campvivo.common.upload;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vn.campvivo.common.config.AppProperties;
import vn.campvivo.common.exception.BusinessException;
import vn.campvivo.common.exception.ErrorCode;

import java.util.Map;

@Component
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    private final AppProperties.Cloudinary props;
    private final Cloudinary cloudinary;

    public CloudinaryImageStorage(AppProperties appProperties) {
        this.props = appProperties.cloudinary();
        this.cloudinary = props.configured()
                ? new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", props.cloudName(),
                        "api_key", props.apiKey(),
                        "api_secret", props.apiSecret(),
                        "secure", true))
                : null;
    }

    @Override
    public String store(byte[] bytes, String folder) {
        if (cloudinary == null) {
            log.error("Chưa cấu hình app.cloudinary.* — không upload được ảnh");
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", props.folder() + "/" + folder,
                    "resource_type", "image",
                    "unique_filename", true,
                    "overwrite", false));
            Object url = result.get("secure_url");
            if (url == null) {
                throw new IllegalStateException("Cloudinary không trả secure_url");
            }
            return url.toString();
        } catch (Exception e) {
            // Không log khoá; chỉ log lỗi để tra theo X-Request-Id
            log.error("Upload ảnh lên Cloudinary thất bại", e);
            throw new BusinessException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
    }
}
