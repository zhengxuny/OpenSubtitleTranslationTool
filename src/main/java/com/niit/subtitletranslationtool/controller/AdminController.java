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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员功能控制器，处理管理员角色用户的后台管理请求。
 * 提供用户管理、任务管理及后台首页统计数据展示等功能，仅允许具有ADMIN角色的用户访问。
 */
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

    /**
     * 跳转至用户管理页面，展示所有用户列表。
     *
     * @param model 模型对象，用于向视图传递用户列表数据
     * @return 用户管理视图路径
     */
    @GetMapping("/users")
    public String userManagement(Model model) {
        List<User> users = userMapper.findAllUsers();
        model.addAttribute("users", users);
        return "admin/user-management";
    }

    /**
     * 跳转至添加用户的表单页面。
     *
     * @param model 模型对象，用于向视图传递新用户对象（初始化表单）
     * @return 用户表单视图路径
     */
    @GetMapping("/users/add")
    public String addUserForm(Model model) {
        model.addAttribute("user", new User());
        return "admin/user-form";
    }

    /**
     * 跳转至编辑用户的表单页面。
     *
     * @param id    待编辑用户的ID
     * @param model 模型对象，用于向视图传递待编辑的用户信息
     * @return 用户表单视图路径
     */
    @GetMapping("/users/edit/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userMapper.findById(id);
        model.addAttribute("user", user);
        return "admin/user-form";
    }

    /**
     * 保存用户信息（新增或编辑）。
     * 新增用户时自动加密密码并设置创建/更新时间；编辑时仅更新非密码字段（密码非空时重新加密）。
     *
     * @param user 用户实体对象（新增时ID为空，编辑时ID存在）
     * @return 重定向至用户管理页面的路径
     */
    @PostMapping("/users/save")
    public String saveUser(User user) {
        if (user.getId() == null) {
            // 新增用户场景
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insertUser(user);
        } else {
            // 编辑用户场景
            User existing = userMapper.findById(user.getId());
            existing.setUsername(user.getUsername());
            existing.setEmail(user.getEmail());
            existing.setBalance(user.getBalance());
            if (!user.getPassword().isEmpty()) {
                // 仅当新密码非空时更新加密后的密码
                existing.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            existing.setUpdatedAt(LocalDateTime.now());
            userMapper.updateUser(existing);
        }
        return "redirect:/admin/users";
    }

    /**
     * 删除指定ID的用户。
     *
     * @param id 待删除用户的ID
     * @return 重定向至用户管理页面的路径
     */
    @GetMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userMapper.deleteUser(id);
        return "redirect:/admin/users";
    }

    /**
     * 跳转至任务管理页面，展示任务列表及用户关联信息。
     * 支持按用户ID筛选任务（可选参数），并提供用户ID到用户名的映射用于视图展示。
     *
     * @param model  模型对象，用于传递任务列表、用户列表及ID-用户名映射
     * @param userId 可选参数，指定用户ID以筛选该用户的任务（为null时展示所有任务）
     * @return 任务管理视图路径
     */
    @GetMapping("/tasks")
    public String taskManagement(Model model, @RequestParam(required = false) Long userId) {
        List<Task> tasks = userId != null ? taskMapper.findByUserId(userId) : taskMapper.findAllTasks();
        List<User> users = userMapper.findAllUsers();

        // 构建用户ID到用户名的映射，用于视图快速查找
        Map<Long, String> userIdUsernameMap = users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        model.addAttribute("tasks", tasks);
        model.addAttribute("users", users);
        model.addAttribute("userIdUsernameMap", userIdUsernameMap);

        return "admin/task-management";
    }

    /**
     * 跳转至指定任务的详情页面。
     *
     * @param id    待查看详情的任务ID
     * @param model 模型对象，用于传递任务详细信息
     * @return 任务详情视图路径
     */
    @GetMapping("/tasks/{id}")
    public String taskDetails(@PathVariable Long id, Model model) {
        Task task = taskMapper.findById(id);
        model.addAttribute("task", task);
        return "admin/task-details";
    }

    /**
     * 跳转至管理员后台首页，展示系统统计数据和最近动态。
     * 包含用户总数、任务总数、完成/失败任务数，以及最近5个任务和用户的动态数据。
     *
     * @param model          模型对象，用于传递各项统计数据和最近动态列表
     * @param authentication Spring Security认证对象，用于获取当前管理员的认证信息（可选）
     * @return 后台首页视图路径
     */
    @GetMapping("/index")
    public String adminIndex(Model model, Authentication authentication) {
        // 调试日志：检查SecurityContext中是否存在认证对象
        if (authentication == null) {
            System.err.println("警告：管理员未认证，SecurityContext中无Authentication对象");
        } else {
            //调试用代码
            System.out.println("当前管理员用户名（from Authentication）: " + authentication.getName());
            System.out.println("当前管理员权限: " + authentication.getAuthorities());
        }

        // 获取系统总用户数
        int totalUsers = userMapper.countAllUsers();
        // 获取系统总任务数
        int totalTasks = taskMapper.countAllTasks();
        // 获取状态为完成的任务数量
        int completedTasks = taskMapper.countTasksByStatus(TaskStatus.COMPLETED);
        // 获取状态为失败的任务数量
        int failedTasks = taskMapper.countTasksByStatus(TaskStatus.FAILED);
        // 获取最近5个创建的任务（按时间倒序）
        List<Task> recentTasks = taskMapper.findRecentTasks(5);
        // 获取最近5个注册的用户（按时间倒序）
        List<User> recentUsers = userMapper.findRecentUsers(5);

        // 传递统计数据到视图模板
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalTasks", totalTasks);
        model.addAttribute("completedTasks", completedTasks);
        model.addAttribute("failedTasks", failedTasks);
        model.addAttribute("recentTasks", recentTasks);
        model.addAttribute("recentUsers", recentUsers);

        // 获取最近7天新增用户数据
        List<Map<String, Object>> dailyNewUsers = userMapper.countDailyNewUsers(7);
        model.addAttribute("dailyNewUsers", dailyNewUsers);

        // 获取最近7天新增任务数据（新增）
        List<Map<String, Object>> dailyNewTasks = taskMapper.countDailyNewTasks(7);
        model.addAttribute("dailyNewTasks", dailyNewTasks);

        // 获取当前管理员的用户名
        String adminName = getAdminName();
        model.addAttribute("adminName", adminName);

        return "admin/adminindex";
    }

    /**
     * 获取当前管理员的用户名。
     *
     * @return 当前管理员的用户名
     */
    private String getAdminName() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "管理员";
    }
}