package com.niit.subtitletranslationtool.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 使用Lombok的@Data注解为类自动生成getter和setter方法，以及toString、equals和hashCode等方法
@Data
// 使用Lombok的@NoArgsConstructor注解生成一个无参的构造方法
@NoArgsConstructor
// 使用Lombok的@AllArgsConstructor注解生成一个包含所有字段的构造方法
@AllArgsConstructor
// UploadResponse类表示文件上传后返回的响应信息
public class UploadResponse {

    // taskId变量表示上传任务的唯一标识符
    private Long taskId;

    // message变量包含了上传过程中的详细信息或状态描述
    private String message;

    // originalFilename变量包含了上传文件的原始名称
    private String originalFilename;

    // storedFilename变量包含了服务器上存储文件时使用的名称
    private String storedFilename;

    // 该类的目的是封装文件上传后的响应信息，以便客户端了解上传任务的状态和结果。
    // 它由taskId、message、originalFilename和storedFilename四个字段构成，
    // 分别代表上传任务的唯一标识符、上传过程中的消息、上传文件的原始名称和存储在服务器上的文件名称。
}