package com.niit.subtitletranslationtool.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件存储服务类，负责文件的存储、加载及删除操作。
 * 作为Spring服务组件，通过配置注入文件上传目录路径，支持绝对路径与相对路径初始化。
 */
@Service
public class StorageService {

    private final Path rootLocation;

    /**
     * 构造方法初始化文件存储根路径。
     * 根据配置的上传目录路径，自动处理绝对路径与相对路径（相对于应用工作目录）。
     *
     * @param uploadDir 从配置文件注入的上传目录路径（如"${file.upload-dir}"）
     */
    public StorageService(@Value("${file.upload-dir}") String uploadDir) {
        Path configPath = Paths.get(uploadDir);
        this.rootLocation = configPath.isAbsolute()
                ? configPath
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    /**
     * 初始化文件存储目录。
     * 在Spring Bean初始化完成后自动执行，确保存储根目录存在（递归创建缺失目录）。
     *
     * @throws RuntimeException 目录创建失败时抛出（如权限不足）
     */
    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(rootLocation);
            System.out.println("上传目录已就绪: " + rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("存储目录初始化失败", e);
        }
    }

    /**
     * 存储上传文件到指定目录，生成唯一文件名避免冲突。
     *
     * @param file         客户端上传的MultipartFile文件对象
     * @param uniquePrefix 可选文件名前缀（用于分类标识，如"subtitle_"）
     * @return 生成的唯一存储文件名（不含路径）
     * @throws RuntimeException 文件为空、含非法路径或存储IO异常时抛出
     */
    public String store(MultipartFile file, String uniquePrefix) {
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        String extension = StringUtils.getFilenameExtension(originalFilename);

        // 生成唯一文件名：[前缀_]UUID.扩展名（无扩展名则省略）
        String storedFilename = (uniquePrefix != null && !uniquePrefix.isEmpty() ? uniquePrefix + "_" : "")
                + UUID.randomUUID()
                + (extension != null ? "." + extension : "");

        try {
            if (file.isEmpty()) {
                throw new RuntimeException("无法存储空文件: " + originalFilename);
            }
            if (originalFilename.contains("..")) {
                throw new RuntimeException("禁止存储含相对路径的文件: " + originalFilename);
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(
                        inputStream,
                        rootLocation.resolve(storedFilename),
                        StandardCopyOption.REPLACE_EXISTING
                );
                return storedFilename;
            }
        } catch (IOException e) {
            throw new RuntimeException("文件存储失败: " + originalFilename, e);
        }
    }

    /**
     * 根据存储文件名获取文件完整路径对象。
     *
     * @param filename 存储的唯一文件名（由store方法返回）
     * @return 文件完整路径的Path对象
     */
    public Path load(String filename) {
        return rootLocation.resolve(filename);
    }

    /**
     * 将存储文件转换为Spring Resource对象以便访问。
     *
     * @param filename 存储的唯一文件名
     * @return 可读取的文件Resource对象
     * @throws RuntimeException 文件不存在、不可读或路径格式异常时抛出
     */
    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("文件不可读: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("文件路径格式异常: " + filename, e);
        }
    }

    /**
     * 递归删除存储目录下的所有文件及子目录。
     * 注意：此操作为不可逆操作，会清空整个上传目录。
     */
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}