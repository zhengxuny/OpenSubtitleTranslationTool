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
 * 文件存储服务实现类。
 * 这个类负责处理文件的存储、加载和删除等操作。
 * 它被标记为@Service，意味着Spring框架会创建并管理这个类的实例（Bean）。
 */
@Service // @Service注解表明这个类是一个服务层组件，Spring IoC容器会自动扫描并注册它。
public class StorageService {

    // 定义一个私有的、最终的（final）Path类型的变量，用于存储文件上传的根目录路径。
    // 'final' 意味着这个变量在对象构造完成后其引用不能再被修改。
    private final Path rootLocation;

    /**
     * 构造函数，用于初始化StorageService实例。
     * 它通过Spring的@Value注解从配置文件（如application.properties或application.yml）中
     * 读取名为 "file.upload-dir" 的属性值，并将其作为上传目录的路径。
     *
     * @param uploadDir 从配置文件注入的上传目录路径。这个路径可以是绝对路径（如 "/var/data/uploads"）
     *                  或相对路径（如 "uploads"）。
     */
    public StorageService(@Value("${file.upload-dir}") String uploadDir) {
        // 使用Paths.get(uploadDir)将字符串路径转换为Path对象。
        // 然后检查这个路径是否是绝对路径。
        if (Paths.get(uploadDir).isAbsolute()) {
            // 如果是绝对路径，直接将其赋值给rootLocation。
            this.rootLocation = Paths.get(uploadDir);
        } else {
            // 如果是相对路径，则将其解析为相对于当前Java应用程序工作目录的路径。
            // System.getProperty("user.dir") 获取当前工作目录。
            // Paths.get(base, ...parts) 会将多个路径部分连接起来形成一个完整的路径。
            this.rootLocation = Paths.get(System.getProperty("user.dir"), uploadDir);
        }
    }

    /**
     * 初始化方法，使用@PostConstruct注解。
     * 这个方法会在StorageService Bean被Spring容器创建并且所有依赖注入完成后自动执行。
     * 它的主要目的是确保文件存储的根目录实际存在于文件系统中。
     */
    @PostConstruct // @PostConstruct注解确保此方法在构造函数执行完毕且所有依赖注入完成后被调用。
    public void init() {
        try {
            // Files.createDirectories会创建指定的目录。
            // 如果目录已经存在，它不会做任何事情，也不会抛出异常。
            // 如果路径中的父目录不存在，它也会一并创建它们。
            Files.createDirectories(rootLocation);
            // 在控制台打印一条消息，确认上传目录已创建或已存在，并显示其绝对路径。
            // rootLocation.toAbsolutePath() 将可能存在的相对路径转换为绝对路径。
            System.out.println("上传目录已创建/确认存在: " + rootLocation.toAbsolutePath());
        } catch (IOException e) {
            // 如果在创建目录过程中发生I/O错误（例如，权限不足），则捕获IOException。
            // 抛出一个运行时异常（RuntimeException），并附带原始的IOException作为原因。
            // 这通常会导致应用程序启动失败，因为存储服务无法正常初始化。
            throw new RuntimeException("无法初始化存储目录 (Could not initialize storage location)", e);
        }
    }

    /**
     * 存储上传的文件到文件系统的指定位置。
     *
     * @param file         Spring MVC的MultipartFile对象，代表客户端上传的文件。
     * @param uniquePrefix 一个可选的字符串前缀，用于添加到生成的文件名之前，可以帮助组织或识别文件来源。
     * @return 存储成功后，新生成的文件名（不包含路径部分）。这个文件名是唯一的，以避免冲突。
     * @throws RuntimeException 如果文件为空、文件名包含非法字符（如".."试图进行路径遍历攻击）、或在存储过程中发生I/O错误。
     */
    public String store(MultipartFile file, String uniquePrefix) {
        // 使用Spring的StringUtils.cleanPath()方法清理原始文件名。
        // 这个方法会移除路径信息（如"../"）并规范化文件名，有助于防止路径遍历攻击。
        // Objects.requireNonNull()确保file.getOriginalFilename()不为null，如果为null则抛出NullPointerException。
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));

        // 使用Spring的StringUtils.getFilenameExtension()方法获取原始文件的扩展名（如 ".txt", ".jpg"）。
        String extension = StringUtils.getFilenameExtension(originalFilename);

        // 生成一个新的、唯一的存储文件名，以避免文件名冲突和潜在的安全问题。
        // 格式为：[uniquePrefix_]UUID.[extension]
        // (uniquePrefix != null ? uniquePrefix + "_" : "") : 如果提供了uniquePrefix，则将其和下划线加在前面。
        // UUID.randomUUID().toString() : 生成一个标准的UUID字符串，确保文件名几乎肯定是唯一的。
        // (extension != null ? "." + extension : "") : 如果文件有扩展名，则附加点和扩展名。
        String storedFilename = (uniquePrefix != null && !uniquePrefix.isEmpty() ? uniquePrefix + "_" : "") +
                UUID.randomUUID().toString() +
                (extension != null ? "." + extension : "");

        try {
            // 检查上传的文件是否为空（即文件大小为0或者没有选择文件）。
            if (file.isEmpty()) {
                // 如果文件为空，抛出运行时异常，指明无法存储空文件。
                throw new RuntimeException("无法存储空文件 (Failed to store empty file): " + originalFilename);
            }
            // 再次进行安全检查，确保清理后的原始文件名中不包含 ".."。
            // 这是一个额外的安全措施，防止路径遍历攻击。
            // 尽管StringUtils.cleanPath()通常会处理此问题，但多一层防御总是好的。
            if (originalFilename.contains("..")) {
                // 如果文件名包含 ".."，抛出运行时异常。
                throw new RuntimeException(
                        "不能存储包含相对路径的文件 (Cannot store file with relative path outside current directory): "
                                + originalFilename);
            }
            // 使用try-with-resources语句获取文件的输入流。
            // 这样可以确保输入流在操作完成后（无论成功还是异常）自动关闭。
            try (InputStream inputStream = file.getInputStream()) {
                // Files.copy()方法用于将输入流的内容复制到目标文件。
                // this.rootLocation.resolve(storedFilename) : 将根存储路径和新生成的唯一文件名结合，形成完整的目标文件路径。
                // StandardCopyOption.REPLACE_EXISTING : 如果目标文件已存在（虽然由于UUID不太可能），则替换它。
                Files.copy(inputStream, this.rootLocation.resolve(storedFilename),
                        StandardCopyOption.REPLACE_EXISTING);
                // 文件存储成功后，返回新生成的、唯一的文件名。
                return storedFilename;
            }
        } catch (IOException e) {
            // 如果在获取输入流或复制文件过程中发生I/O错误，捕获IOException。
            // 抛出一个运行时异常，并附带原始文件名和IOException作为原因。
            throw new RuntimeException("存储文件失败 (Failed to store file): " + originalFilename, e);
        }
    }

    /**
     * 根据存储在服务器上的文件名，构建并返回该文件的完整路径对象 (Path)。
     *
     * @param filename 存储在服务器上的文件名（通常是由store方法返回的唯一文件名）。
     * @return 文件的完整Path对象。
     */
    public Path load(String filename) {
        // rootLocation是存储的根目录。
        // resolve(filename)方法会将给定的文件名附加到根路径后面，形成一个完整的子路径。
        // 例如，如果rootLocation是 "/uploads" 且 filename 是 "myFile.txt"，结果将是 "/uploads/myFile.txt" 的Path对象。
        return rootLocation.resolve(filename);
    }

    /**
     * 根据文件名加载文件，并将其作为Spring的Resource对象返回。
     * Resource对象是对底层实际资源的抽象，可以用于读取文件内容，常用于文件下载。
     *
     * @param filename 存储在服务器上的文件名。
     * @return 一个Spring的Resource对象，代表指定的文件。
     * @throws RuntimeException 如果文件无法被读取（例如，文件不存在、无权限，或URL格式错误）。
     */
    public Resource loadAsResource(String filename) {
        try {
            // 首先，使用load方法获取文件的完整Path对象。
            Path file = load(filename);
            // 将Path对象转换为URI (Uniform Resource Identifier)，然后使用这个URI创建一个UrlResource对象。
            // UrlResource是Resource接口的一个实现，允许通过URL访问资源。
            Resource resource = new UrlResource(file.toUri());
            // 检查创建的Resource是否存在并且是可读的。
            if (resource.exists() || resource.isReadable()) {
                // 如果资源存在且可读，返回该Resource对象。
                return resource;
            } else {
                // 如果资源不存在或不可读（例如，文件被删除或权限问题），抛出运行时异常。
                throw new RuntimeException(
                        "无法读取文件 (Could not read file): " + filename);
            }
        } catch (MalformedURLException e) {
            // 如果Path对象的URI格式不正确（这在使用file.toUri()时非常罕见，但理论上可能），
            // UrlResource构造函数会抛出MalformedURLException。
            // 捕获此异常并抛出一个运行时异常。
            throw new RuntimeException("无法读取文件 (Could not read file): " + filename, e);
        }
    }

    /**
     * 删除存储根目录下的所有文件和子目录。
     * 这是一个危险的操作，因为它会清空整个上传目录。
     */
    public void deleteAll() {
        // 使用Spring的FileSystemUtils.deleteRecursively()方法递归删除文件或目录。
        // rootLocation.toFile() 将NIO的Path对象转换为旧的IO的File对象，因为该工具方法需要File对象。
        // 这个方法会删除rootLocation指向的目录及其所有内容。
        // 注意：这个操作是不可逆的。如果rootLocation指向一个重要的系统目录，可能会造成严重后果。
        // 通常，应确保rootLocation配置正确，并且只指向用于此应用程序上传的专用目录。
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
        // 可以考虑在删除后重新创建根目录，如果业务逻辑需要的话：
        // init(); // 或者直接调用 Files.createDirectories(rootLocation);
    }
}