package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.entity.User;
import com.niit.subtitletranslationtool.enums.TaskStatus;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import com.niit.subtitletranslationtool.mapper.UserMapper;
import com.niit.subtitletranslationtool.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 用户管理页面
    @GetMapping("/users")
    public String userManagement(Model model) {
        List<User> users = userMapper.findAllUsers();
        model.addAttribute("users", users);
        return "admin/user-management";
    }

    // 添加用户表单
    @GetMapping("/users/add")
    public String addUserForm(Model model) {
        model.addAttribute("user", new User());
        return "admin/user-form";
    }

    // 编辑用户表单
    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userMapper.findById(id);
        model.addAttribute("user", user);
        return "admin/user-form";
    }

    // 保存用户（新增/编辑）
    @PostMapping("/users/save")
    public String saveUser(User user) {
        if (user.getId() == null) {
            // 新增用户
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insertUser(user);
        } else {
            // 编辑用户
            User existing = userMapper.findById(user.getId());
            existing.setUsername(user.getUsername());
            existing.setEmail(user.getEmail());
            existing.setBalance(user.getBalance());
            if (!user.getPassword().isEmpty()) {
                existing.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            existing.setUpdatedAt(LocalDateTime.now());
            userMapper.updateUser(existing);
        }
        return "redirect:/admin/users";
    }

    // 删除用户
    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userMapper.deleteUser(id);
        return "redirect:/admin/users";
    }

    // 任务管理页面
    @GetMapping("/tasks")
    public String taskManagement(Model model, @RequestParam(required = false) Long userId) {
        List<Task> tasks = userId != null ? taskMapper.findByUserId(userId) : taskMapper.findAllTasks();
        List<User> users = userMapper.findAllUsers();

        // Prepare a Map<userId, username> for template
        Map<Long, String> userIdUsernameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        model.addAttribute("tasks", tasks);
        model.addAttribute("users", users);
        model.addAttribute("userIdUsernameMap", userIdUsernameMap);  // Add map to model

        return "admin/task-management";
    }

    // 任务详情页面
    @GetMapping("/tasks/{id}")
    public String taskDetails(@PathVariable Long id, Model model) {
        Task task = taskMapper.findById(id);
        model.addAttribute("task", task);
        return "admin/task-details";
    }

    // 新增：后台首页数据处理方法
    @GetMapping("/index")
    public String adminIndex(Model model, Authentication authentication) {
        // 调试：检查认证对象是否存在
        if (authentication == null) {
            System.err.println("警告：管理员未认证，SecurityContext中无Authentication对象");
        } else {
            System.out.println("当前管理员用户名（from Authentication）: " + authentication.getName());
            System.out.println("当前管理员权限: " + authentication.getAuthorities());
        }
        // 总用户数
        int totalUsers = userMapper.countAllUsers();
        // 总任务数
        int totalTasks = taskMapper.countAllTasks();
        // 完成任务数（假设TaskStatus有COMPLETED状态）
        int completedTasks = taskMapper.countTasksByStatus(TaskStatus.COMPLETED);
        // 失败任务数
        int failedTasks = taskMapper.countTasksByStatus(TaskStatus.FAILED);
        // 最近5个任务（按创建时间倒序）
        List<Task> recentTasks = taskMapper.findRecentTasks(5);
        // 最近5个用户（按注册时间倒序）
        List<User> recentUsers = userMapper.findRecentUsers(5);

        // 传递数据到模板
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("failedTasks", failedTasks);
        model.addAttribute("recentTasks", recentTasks);
        model.addAttribute("recentUsers", recentUsers);

        return "admin/adminindex";
    }


}
