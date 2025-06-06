package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.service.TaskService;
import com.niit.subtitletranslationtool.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * Spring Web MVC控制器，负责处理用户界面的页面请求及相关数据传递。
 * 支持登录、注册、上传、充值、视频详情展示、文件下载等功能页面的路由映射。
 */
@Controller
public class ViewController {

    private final UserService userService;
    private final TaskService taskService;

    /**
     * 构造方法，通过依赖注入初始化用户服务和任务服务。
     *
     * @param userService 用户业务逻辑服务
     * @param taskService 任务业务逻辑服务
     */
    @Autowired
    public ViewController(UserService userService, TaskService taskService) {
        this.userService = userService;
        this.taskService = taskService;
    }

    /**
     * 测试路径接口，返回当前项目根目录路径。
     *
     * @return 包含项目根目录路径的字符串
     */
    @GetMapping("/test-path")
    @ResponseBody
    public String testPath() {
        return "项目根目录：" + System.getProperty("user.dir");
    }

    /**
     * 映射登录页面请求，返回登录视图。
     *
     * @return 登录页面模板路径（user/login.html）
     */
    @GetMapping("/login")
    public String showLoginPage() {
        return "user/login";
    }

    /**
     * 映射注册页面请求，返回注册视图。
     *
     * @return 注册页面模板路径（user/register.html）
     */
    @GetMapping("/register")
    public String showRegisterPage() {
        return "user/register";
    }

    /**
     * 映射上传页面请求，传递当前用户信息到视图。
     * 若用户已认证，将用户名、余额、用户ID添加到模型；未认证则不传递用户信息。
     *
     * @param model Thymeleaf模型对象，用于向视图传递数据
     * @return 上传页面模板路径（user/uplode.html）
     */
    @GetMapping({"/upload"})
    public String showUploadPage(Model model) {
        // 获取当前认证信息
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // 检查用户是否已认证且非匿名用户
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance());
            model.addAttribute("userId", user.getId());
        }
        return "user/uplode";
    }

    /**
     * 映射充值页面请求，传递当前用户信息到视图。
     * 若用户已认证，将用户名、余额、用户ID添加到模型。
     *
     * @param model Thymeleaf模型对象，用于向视图传递数据
     * @return 充值页面模板路径（user/topup.html）
     */
    @GetMapping("/topup")
    public String showTopUpPage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance());
            model.addAttribute("userId", user.getId());
        }
        return "user/topup";
    }

    /**
     * 映射主页请求，传递用户任务列表及账户信息到视图。
     *
     * @param model Thymeleaf模型对象，用于向视图传递数据
     * @return 主页模板路径（user/index.html）
     */
    @GetMapping({"/", "/index"})
    public String showIndexPage(Model model) {
        // 获取当前认证用户的用户名
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // 查询用户信息及关联任务
        User user = userService.getUserByUsername(username);
        List<Task> tasks = taskService.getTasksByUserId(user.getId());

        // 向视图传递用户信息和任务数据
        model.addAttribute("username", username);
        model.addAttribute("tasks", tasks);
        model.addAttribute("userBalance", user.getBalance());
        model.addAttribute("userId", user.getId());

        return "user/index";
    }

    /**
     * 映射视频详情页面请求，传递任务信息及字幕内容到视图。
     * 若字幕文件存在则读取内容，否则设置提示信息。
     *
     * @param taskId 任务ID，从路径变量获取
     * @param model  Thymeleaf模型对象，用于向视图传递数据
     * @return 视频详情页面模板路径（user/details.html）
     */
    @GetMapping("/video-details/{taskId}")
    public String showVideoDetailsPage(@PathVariable Long taskId, Model model) {
        // 传递当前用户信息到视图（若已认证）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance());
            model.addAttribute("userId", user.getId());
        }

        // 查询任务并读取字幕文件内容
        Task task = taskService.getTaskById(taskId);
        try {
            Path srtPath = Paths.get(task.getTranslatedSrtFilePath());
            // 检查文件是否存在并读取内容
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

    /**
     * 视频文件下载接口，根据任务ID获取视频文件并返回下载响应。
     *
     * @param taskId 任务ID，从路径变量获取
     * @return 包含视频文件的响应实体，头部指定下载文件名
     * @throws IOException 文件路径无效或读取失败时抛出
     */
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

    /**
     * 字幕文件下载接口，根据任务ID获取字幕文件并返回下载响应。
     *
     * @param taskId 任务ID，从路径变量获取
     * @return 包含字幕文件的响应实体，头部指定下载文件名
     * @throws IOException 文件路径无效或读取失败时抛出
     */
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
     * 映射文本翻译页面请求，传递当前用户信息到视图。
     * 若用户已认证，将用户名、余额、用户ID添加到模型。
     *
     * @param model Thymeleaf模型对象，用于向视图传递数据
     * @return 文本翻译页面模板路径（user/SimpleTranslation.html）
     */
    @GetMapping("/SimpleTranslation")
    public String showTranslationPage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getName())) {
            String username = authentication.getName();
            User user = userService.getUserByUsername(username);
            model.addAttribute("username", username);
            model.addAttribute("userBalance", user.getBalance());
            model.addAttribute("userId", user.getId());
        }
        return "user/SimpleTranslation";
    }

    /**
     * 映射管理员登录页面请求，返回管理员登录视图。
     *
     * @return 管理员登录页面模板路径（admin/adminlogin.html）
     */
    @GetMapping("/admin/login")
    public String showAdminLoginPage() {
        return "admin/adminlogin";
    }

    /**
     * 处理退出登录请求，使当前会话失效并重定向到登录页面。
     *
     * @param session 当前HTTP会话对象
     * @return 重定向到登录页面的路径
     */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate(); // 清除会话信息
        return "redirect:/login";
    }
}