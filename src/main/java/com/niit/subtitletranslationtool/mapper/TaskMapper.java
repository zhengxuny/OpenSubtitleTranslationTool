package com.niit.subtitletranslationtool.mapper;

import com.niit.subtitletranslationtool.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * TaskMapper接口用于定义操作任务数据的CRUD方法
 * 通过注解@Mapper标识这是一个MyBatis的Mapper接口
 */
@Mapper
public interface TaskMapper {

    /**
     * 插入新的任务到数据库
     * @param task: 任务对象，包含了需要插入的任务信息
     */
    void insertTask(Task task);

    /**
     * 根据ID查询单个任务信息
     * 此方法将在后续阶段用于获取任务详情
     * @param id: 任务的唯一标识符，数据库中的主键
     * @return: 返回Task对象，包含了查询到的任务信息；如果未找到相应ID的任务，则返回null
     */
    Task findById(Long id);

    /**
     * 更新任务的信息和状态
     * 此方法将用于修改数据库中存在的任务的详细信息或执行状态
     * @param task: 包含需要更新的任务信息的Task对象
     */
    void updateTask(Task task);

    /**
     * 根据用户ID查询所有任务信息
     * 此方法将用于获取特定用户的所有任务
     * @param userId: 用户的唯一标识符
     * @return: 返回List<Task>对象，包含了查询到的任务信息；如果没有找到相关的任务，则返回空列表
     */
    List<Task> findByUserId(Long userId);

    //查找全部任务
    List<Task> findAllTasks();
}