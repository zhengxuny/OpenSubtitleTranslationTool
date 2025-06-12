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
 * 设计目标是提供一个统一的接口，用于处理应用程序中所有与文件存储相关的操作。
 * 作为Spring服务组件，通过配置注入文件上传目录路径，支持绝对路径与相对路径初始化。
 */
@Service
public class StorageService {

    private final Path rootLocation;

    /**
     * 构造方法，用于初始化文件存储的根路径。
     * 根据配置的上传目录路径，自动处理绝对路径与相对路径。
     * 如果配置的是相对路径，则相对于应用程序的工作目录。
     *
     * @param uploadDir 从配置文件注入的上传目录路径（例如："${file.upload-dir}"）。
     *                  这个路径定义了文件存储的根位置。
     */
    public StorageService(@Value("${file.upload-dir}") String uploadDir) {
        Path configPath = Paths.get(uploadDir);
        // 判断配置的路径是否为绝对路径。如果是，则直接使用；否则，将其视为相对于应用工作目录的路径。
        this.rootLocation = configPath.isAbsolute()
                ? configPath
                : Paths.get(System.getProperty("user.dir"), uploadDir);
    }

    /**
     * 初始化文件存储目录。
     * 在Spring Bean初始化完成后自动执行，确保存储根目录存在。
     * 如果目录不存在，则递归创建缺失的目录。
     * 使用 `@PostConstruct` 注解确保在依赖注入完成后执行。
     *
     * @throws RuntimeException 当目录创建失败时抛出，例如权限不足等情况。
     */
    @PostConstruct
    public void init() {
        try {
            // 尝试创建目录，如果目录已存在则不会执行任何操作。
            Files.createDirectories(rootLocation);
            System.out.println("上传目录已就绪: " + rootLocation.toAbsolutePath());
        } catch (IOException e) {
            // 如果创建目录失败，则抛出运行时异常，指示存储目录初始化失败。
            throw new RuntimeException("存储目录初始化失败", e);
        }
    }

    /**
     * 存储上传的文件到指定目录，并生成一个唯一的文件名以避免冲突。
     *
     * @param file         客户端上传的MultipartFile文件对象，包含了文件的内容和元数据。
     * @param uniquePrefix 可选的文件名前缀，用于分类标识，例如 "subtitle_"。可以为空。
     * @return 生成的唯一存储文件名（不包含路径）。
     * @throws RuntimeException 当文件为空、包含非法路径或存储IO异常时抛出。
     */
    public String store(MultipartFile file, String uniquePrefix) {
        // 获取原始文件名，并进行清理，防止路径注入攻击。
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        // 获取文件扩展名，例如 "txt" 或 "pdf"。
        String extension = StringUtils.getFilenameExtension(originalFilename);

        // 生成唯一的文件名：[前缀_]UUID.扩展名（如果文件有扩展名）。
        String storedFilename = (uniquePrefix != null && !uniquePrefix.isEmpty() ? uniquePrefix + "_" : "")
                + UUID.randomUUID()
                + (extension != null ? "." + extension : "");

        try {
            // 检查上传的文件是否为空。
            if (file.isEmpty()) {
                throw new RuntimeException("无法存储空文件: " + originalFilename);
            }
            // 检查文件名是否包含 ".."，以防止尝试访问存储目录之外的文件。
            if (originalFilename.contains("..")) {
                throw new RuntimeException("禁止存储包含相对路径的文件: " + originalFilename);
            }

            // 使用 try-with-resources 确保输入流被正确关闭。
            try (InputStream inputStream = file.getInputStream()) {
                // 将文件内容复制到存储位置，如果目标文件已存在，则替换它。
                Files.copy(
                        inputStream,
                        rootLocation.resolve(storedFilename),
                        StandardCopyOption.REPLACE_EXISTING
                );
                // 返回存储的文件名。
                return storedFilename;
            }
        } catch (IOException e) {
            // 如果在存储文件时发生任何IO异常，则抛出运行时异常。
            throw new RuntimeException("文件存储失败: " + originalFilename, e);
        }
    }

    /**
     * 根据存储的文件名获取文件的完整路径对象。
     *
     * @param filename 存储的唯一文件名（由 store 方法返回）。
     * @return 文件的完整路径的 Path 对象。
     */
    public Path load(String filename) {
        // 构建文件的完整路径。
        return rootLocation.resolve(filename);
    }

    /**
     * 将存储的文件转换为 Spring Resource 对象，以便可以方便地访问文件内容。
     *
     * @param filename 存储的唯一文件名。
     * @return 可读取的文件 Resource 对象。
     * @throws RuntimeException 当文件不存在、不可读或路径格式异常时抛出。
     */
    public Resource loadAsResource(String filename) {
        try {
            // 获取文件的完整路径。
            Path file = load(filename);
            // 创建一个指向文件的 URL 资源。
            Resource resource = new UrlResource(file.toUri());

            // 检查资源是否存在且可读。
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                // 如果文件不存在或不可读，则抛出运行时异常。
                throw new RuntimeException("文件不可读: " + filename);
            }
        } catch (MalformedURLException e) {
            // 如果文件路径格式不正确，则抛出运行时异常。
            throw new RuntimeException("文件路径格式异常: " + filename, e);
        }
    }

    /**
     * 递归删除存储目录下的所有文件及子目录。
     * **注意：** 此操作是不可逆的，会清空整个上传目录。请谨慎使用。
     */
    public void deleteAll() {
        // 使用 Spring 的 FileSystemUtils 工具类递归删除目录。
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
    }
}