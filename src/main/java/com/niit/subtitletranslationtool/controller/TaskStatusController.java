package com.niit.subtitletranslationtool.controller;

import com.niit.subtitletranslationtool.entity.Task;
import com.niit.subtitletranslationtool.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task")
public class TaskStatusController {

    private final TaskMapper taskMapper;

    @Autowired
    public TaskStatusController(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    // 状态查询接口
    @GetMapping("/status/{taskId}")
    public ResponseEntity<Task> getTaskStatus(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    // 翻译后SRT下载接口
    @GetMapping("/download/srt/translated/{taskId}")
    public ResponseEntity<Resource> downloadTranslatedSrt(@PathVariable Long taskId) {
        Task task = taskMapper.findById(taskId);
        if (task == null || task.getTranslatedSrtFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(task.getTranslatedSrtFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + task.getTranslatedSrtFilename() + "\"")
                .body(resource);
    }
}
