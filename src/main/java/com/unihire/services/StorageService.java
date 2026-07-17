package com.unihire.services;

import com.unihire.dto.StoredObject;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String uploadFile(MultipartFile file);
    StoredObject uploadFile(MultipartFile file, String pathPrefix);
    String getFileUrl(String filename);
    java.io.InputStream getFileStream(String filename);
    void deleteFile(String objectKey);
}
