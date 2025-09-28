package com.example.kaiburrtasks.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.kaiburrtasks.model.Task;
import com.example.kaiburrtasks.repository.TaskRepository;

@RestController
public class HelloController {

    @Autowired
    private TaskRepository taskRepository;

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello from Spring Boot!";
    }
    @PostMapping("/test-tasks")
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        Task savedTask = taskRepository.save(task);
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }
}
