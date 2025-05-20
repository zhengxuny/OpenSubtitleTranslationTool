package com.niit.subtitletranslationtool.service;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;

public interface StorageService {

    void init(); // 初始化存储

    String store(MultipartFile file, String uniquePrefix); // 返回存储后的文件名（不含路径）

    Path load(String filename); // 返回文件的Path对象

    Resource loadAsResource(String filename); // 返回文件的Resource对象，用于下载

    void deleteAll(); // (可选) 清空所有存储的文件
}