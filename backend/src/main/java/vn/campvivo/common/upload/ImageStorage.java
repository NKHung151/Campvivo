package vn.campvivo.common.upload;

public interface ImageStorage {
    String store(byte[] bytes, String folder);
}
