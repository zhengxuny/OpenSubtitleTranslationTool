package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private Long taskId;
    private String message;
    private String originalFilename;
    private String storedFilename;
}