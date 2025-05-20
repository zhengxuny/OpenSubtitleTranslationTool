package com.niit.subtitletranslationtool.service.impl;

import com.niit.subtitletranslationtool.service.StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct; // 注意使用jakarta包下的注解
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 文件系统存储服务实现类，实现StorageService接口
 */
@Service
public class FileSystemStorageService implements StorageService {

    // 文件存储的根目录路径
    private final Path rootLocation;

    /**
     * 构造函数，通过@Value注入配置文件中的上传目录路径
     * @param uploadDir 上传目录路径，可以是相对路径或绝对路径
     */
    public FileSystemStorageService(@Value("${file.upload-dir}") String uploadDir) {
        // 判断路径是否为绝对路径
        if (Paths.get(uploadDir).isAbsolute()) {
            this.rootLocation = Paths.get(uploadDir);
        } else {
            // 如果是相对路径，则拼接当前工作目录
            this.rootLocation = Paths.get(System.getProperty("user.dir"), uploadDir);
        }
    }

    /**
     * 初始化方法，在Bean创建后执行，确保存储目录存在
     */
    @Override
    @PostConstruct
    public void init() {
        try {
            // 创建存储目录（如果不存在）
            Files.createDirectories(rootLocation);
            System.out.println("上传目录已创建/确认存在: " + rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("无法初始化存储目录", e);
        }
    }

    /**
     * 存储上传的文件
     * @param file 上传的文件对象
     * @param uniquePrefix 可选的文件名前缀，用于区分文件
     * @return 存储后的文件名
     */
    @Override
    public String store(MultipartFile file, String uniquePrefix) {
        // 清理原始文件名中的路径信息
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        // 获取文件扩展名
        String extension = StringUtils.getFilenameExtension(originalFilename);
        // 生成存储文件名：前缀_UUID.扩展名
        String storedFilename = (uniquePrefix != null ? uniquePrefix + "_" : "") +
                UUID.randomUUID().toString() +
                (extension != null ? "." + extension : "");

        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                throw new RuntimeException("无法存储空文件: " + originalFilename);
            }
            // 安全检查：防止路径遍历攻击
            if (originalFilename.contains("..")) {
                throw new RuntimeException(
                        "不能存储包含相对路径的文件: " + originalFilename);
            }
            // 将文件流写入目标位置
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, this.rootLocation.resolve(storedFilename),
                        StandardCopyOption.REPLACE_EXISTING);
                return storedFilename; // 返回存储后的文件名
            }
        } catch (IOException e) {
            throw new RuntimeException("存储文件失败: " + originalFilename, e);
        }
    }

    /**
     * 根据文件名获取文件路径
     * @param filename 文件名
     * @return 完整的文件路径
     */
    @Override
    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    /**
     * 将文件作为Resource资源加载
     * @param filename 文件名
     * @return 文件资源对象
     */
    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            // 检查资源是否存在且可读
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("无法读取文件: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("无法读取文件: " + filename, e);
        }
    }

    /**
     * 删除所有存储的文件（清空存储目录）
     */
    @Override
    public void deleteAll() {
        // 递归删除存储目录下的所有文件
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}