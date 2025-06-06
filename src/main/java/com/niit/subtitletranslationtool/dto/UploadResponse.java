package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应数据传输对象，用于封装文件上传任务的唯一标识、操作消息、原始文件名及服务器存储文件名等信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResponse {
    private Long taskId;
    private String message;
    private String originalFilename;
    private String storedFilename;
}