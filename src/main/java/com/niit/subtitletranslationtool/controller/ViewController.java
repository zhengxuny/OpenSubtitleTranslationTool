package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.service.TaskService;
import com.niit.subtitletranslationtool.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
        return "login"; // 返回 login.html 模板
    }

    /**
     * 用户访问"/register"路径时调用此方法。
     * 该方法返回一个表示注册页面的字符串，将会被Spring解析为"register.html"模板文件。
     *
     * @return 字符串"register"，表示返回register.html视图
     */
    @GetMapping("/register")
    public String showRegisterPage() {
        return "register"; // 返回 register.html 模板
    }

    /**
     * 当用户访问根路径"/"或者"/index"路径时，会调用此方法。
     * 该方法返回一个代表主页的字符串，将会被Spring解析为"uplode.html"模板文件。
     * 这意味着不论是直接访问网站根路径还是明确指定了/index路径，都将会导向同一主页。
     *
     * @return 字符串"index"，表示返回index.html视图
     */
    @GetMapping({"/upload"})
    public String showIndexPage() {
        return "uplode"; // 返回 uplode.html 模板
    }


    // 新增充值页面路由
    @GetMapping("/topup")
    public String showTopUpPage() {
        return "topup"; // 返回topup.html模板
    }

    @GetMapping({"/", "/index"})
    public String showIndexPage(Model model) {
        // Get current authenticated username
        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        // Get user and their tasks
        User user = userService.getUserByUsername(username);
        List<Task> tasks = taskService.getTasksByUserId(user.getId());

        // Pass data to template
        model.addAttribute("username", username);
        model.addAttribute("tasks", tasks);

        return "index";
    }

    // 新增：视频详情页路由
    @GetMapping("/video-details/{taskId}")
    public String showVideoDetailsPage(@PathVariable Long taskId, Model model) {
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
        return "details";
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

}
