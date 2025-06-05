package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.service.TaskService;
import com.niit.subtitletranslationtool.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication; // 引入 Authentication 类
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 该类是一个Spring Web MVC控制器，主要负责处理用户页面请求的逻辑。
 * 它映射了与用户登录、注册以及主页相关的URL路径，并返回相应的HTML模板页面。
 */
@Controller
public class ViewController {

    private final UserService userService;
    private final TaskService taskService;

    @Autowired
    public ViewController(UserService userService, TaskService taskService) {
        this.userService = userService;
        this.taskService = taskService;
    }

    @GetMapping("/test-path")
    @ResponseBody
    public String testPath() {
        return "项目根目录：" + System.getProperty("user.dir");
    }

    /**
     * 用户访问"/login"路径时调用此方法。
     * 该方法返回一个表示登录页面的字符串，将会被Spring解析为"login.html"模板文件。
     *
     * @return 字符串"login"，表示返回login.html视图
     */
    @GetMapping("/login")
    public String showLoginPage() {
        return "user/login"; // 返回 login.html 模板
    }

    /**
     * 用户访问"/register"路径时调用此方法。
     * 该方法返回一个表示注册页面的字符串，将会被Spring解析为"register.html"模板文件。
     *
     * @return 字符串"register"，表示返回register.html视图
     */
    @GetMapping("/register")
    public String showRegisterPage() {
        return "user/register"; // 返回 register.html 模板
    }

    /**
     * 当用户访问"/upload"路径时，会调用此方法。
     * 该方法现在会获取当前认证用户的用户名和余额，并将其添加到Model中。
     * 这确保了即使通过/upload路径访问，导航栏也能显示正确的用户信息。
     *
     * @param model Thymeleaf 模型，用于向视图传递数据
     * @return 字符串"uplode"，表示返回uplode.html视图
     */
    @GetMapping({"/upload"})
    public String showUploadPage(Model model) { // 将 showIndexPage 重命名为 showUploadPage 以避免混淆
        // 尝试获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 检查用户是否已认证且不是匿名用户
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance()); // 使用 userBalance
            model.addAttribute("userId", user.getId());
        } else {
            // 如果未认证或匿名用户，可以设置默认值或不添加这些属性
            // navbar会处理#authentication?.name ?: '访客'和userBalance的null情况
        }
        return "user/uplode"; // 返回 uplode.html 模板
    }


    // 新增充值页面路由
    @GetMapping("/topup")
    public String showTopUpPage(Model model) { // 添加 Model 参数
        // 尝试获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance()); // 使用 userBalance
            model.addAttribute("userId", user.getId());
        }
        return "user/topup"; // 返回topup.html模板
    }

    @GetMapping({"/", "/index"})
    public String showIndexPage(Model model) {
        // Get current authenticated username
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Get user and their tasks
        User user = userService.getUserByUsername(username); // user对象包含balance
        List<Task> tasks = taskService.getTasksByUserId(user.getId());

        // Pass data to template
        model.addAttribute("username", username);
        model.addAttribute("tasks", tasks);
        // 修正：将用户余额的模型属性名改为 "userBalance"
        model.addAttribute("userBalance", user.getBalance()); // 将balance传递给前端
        model.addAttribute("userId", user.getId()); // 如果前端需要userId也可以添加

        return "user/index";
    }

    // 新增：视频详情页路由
    @GetMapping("/video-details/{taskId}")
    public String showVideoDetailsPage(@PathVariable Long taskId, Model model) {
        // 尝试获取当前认证信息，并将其添加到Model中
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance()); // 使用 userBalance
            model.addAttribute("userId", user.getId());
        }

        Task task = taskService.getTaskById(taskId);
        // 读取字幕文件内容（需要处理异常）
        try {
            Path srtPath = Paths.get(task.getTranslatedSrtFilePath());
            // 检查文件是否存在
            if (Files.exists(srtPath)) {
                String srtContent = new String(Files.readAllBytes(srtPath));
                task.setTranslatedSrtContent(srtContent);
            } else {
                task.setTranslatedSrtContent("字幕文件不存在");
            }
        } catch (IOException e) {
            task.setTranslatedSrtContent("字幕文件读取失败：" + e.getMessage());
        }
        model.addAttribute("task", task);
        return "user/details";
    }

    // 新增：视频下载接口
    @GetMapping("/download-video/{taskId}")
    public ResponseEntity<?> downloadVideo(@PathVariable Long taskId) throws IOException {
        Task task = taskService.getTaskById(taskId);
        Path videoPath = Paths.get(task.getSubtitledVideoFilePath());
        UrlResource resource = new UrlResource(videoPath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getSubtitledVideoFilename() + "\"")
                .body(resource);
    }

    // 新增：字幕下载接口
    @GetMapping("/download-srt/{taskId}")
    public ResponseEntity<?> downloadSrt(@PathVariable Long taskId) throws IOException {
        Task task = taskService.getTaskById(taskId);
        Path srtPath = Paths.get(task.getTranslatedSrtFilePath());
        UrlResource resource = new UrlResource(srtPath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getTranslatedSrtFilename() + "\"")
                .body(resource);
    }

    /**
     * 映射文本翻译页面路由 "/SimpleTranslation"。
     * 该方法获取当前认证用户的用户名和余额，并将其添加到Model中。
     *
     * @param model Thymeleaf 模型，用于向视图传递数据
     * @return 字符串"SimpleTranslation"，表示返回SimpleTranslation.html视图
     */
    @GetMapping("/SimpleTranslation")
    public String showTranslationPage(Model model) { // 添加 Model 参数
        // 尝试获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance()); // 使用 userBalance
            model.addAttribute("userId", user.getId());
        }
        return "user/SimpleTranslation"; // 返回 SimpleTranslation.html 模板
    }

    //路由管理员登录页面adminlogin.html到/admin/login
    @GetMapping("/admin/login")
    public String showAdminLoginPage() {
        return "admin/adminlogin"; // 返回 adminlogin.html 模板
    }

//    //路由管理员主页/admin/index.html到/admin/index
//    @GetMapping("/admin/index")
//    public String showAdminIndexPage() {
//        return "admin/adminindex"; // 返回 adminindex.html 模板
//    }
}