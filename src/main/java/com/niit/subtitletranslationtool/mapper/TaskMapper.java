package com.niit.subtitletranslationtool.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.enums.TaskStatus;

/**
 * 定义任务数据操作的MyBatis映射接口，提供任务信息的CRUD及统计查询功能。
 */
@Mapper
public interface TaskMapper {

    /**
     * 向数据库插入新的任务记录。
     *
     * @param task 待插入的任务对象，包含完整的任务信息（非空）
     */
    void insertTask(Task task);

    /**
     * 根据任务ID查询对应的任务信息。
     *
     * @param id 任务的唯一标识符（数据库主键，非空）
     * @return 匹配的任务对象；若不存在则返回null
     */
    Task findById(Long id);

    /**
     * 更新数据库中已有任务的信息（如内容、状态等）。
     *
     * @param task 包含更新后信息的任务对象（需包含有效ID）
     */
    void updateTask(Task task);

    /**
     * 查询指定用户的所有任务记录。
     *
     * @param userId 用户的唯一标识符（非空）
     * @return 用户关联的任务列表；无匹配记录时返回空列表
     */
    List<Task> findByUserId(Long userId);

    /**
     * 查询数据库中所有任务记录。
     *
     * @return 所有任务的列表；无记录时返回空列表
     */
    List<Task> findAllTasks();

    /**
     * 统计数据库中所有任务的总数量。
     *
     * @return 任务总数（非负整数）
     */
    int countAllTasks();

    /**
     * 按任务状态统计符合条件的任务数量。
     *
     * @param status 待统计的任务状态（非空）
     * @return 指定状态的任务数量（非负整数）
     */
    int countTasksByStatus(@Param("status") TaskStatus status);

    /**
     * 查询最近创建的任务记录（按创建时间倒序）。
     *
     * @param limit 限制返回的最大记录数（正整数）
     * @return 最近创建的任务列表；无记录时返回空列表
     */
    List<Task> findRecentTasks(int limit);
}