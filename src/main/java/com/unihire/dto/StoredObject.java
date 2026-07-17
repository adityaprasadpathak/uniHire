package com.unihire.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoredObject {
    private String objectKey;
    private String originalFilename;
    private String contentType;
    private Long size;
}
