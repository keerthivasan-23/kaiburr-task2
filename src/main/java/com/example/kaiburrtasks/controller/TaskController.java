package com.example.kaiburrtasks.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.kaiburrtasks.model.Task;
import com.example.kaiburrtasks.model.TaskExecution;
import com.example.kaiburrtasks.repository.TaskRepository;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository; //Dependency injection

    //  Create a new Task
    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        if (!isValidCommand(task.getCommand())) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        Task savedTask = taskRepository.save(task);
        return new ResponseEntity<>(savedTask, HttpStatus.CREATED);
    }

    //  Get all tasks
    @GetMapping
    public ResponseEntity<?> getAllTasks(@RequestParam(required = false) String id) {
        if (id != null) {
            Optional<Task> task = taskRepository.findById(id);
            return task.<ResponseEntity<?>>map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                    .orElseGet(() -> new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND));
        }
        return new ResponseEntity<>(taskRepository.findAll(), HttpStatus.OK);
    }

    //  Get a task by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getTaskById(@PathVariable String id) {
        Optional<Task> task = taskRepository.findById(id);
        return task.<ResponseEntity<?>>map(value -> new ResponseEntity<>(value, HttpStatus.OK))
                .orElseGet(() -> new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND));
    }

    // Search tasks by name
    @GetMapping("/search")
    public ResponseEntity<?> searchTasksByName(@RequestParam String name) {
        List<Task> tasks = taskRepository.findByNameContainingIgnoreCase(name);
        if (tasks.isEmpty()) {
            return new ResponseEntity<>("No tasks found", HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(tasks, HttpStatus.OK);
    }

    // Update a task (PUT)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateTask(@PathVariable String id, @RequestBody Task updatedTask) {
        if (!isValidCommand(updatedTask.getCommand())) {
            return new ResponseEntity<>("Invalid command!", HttpStatus.BAD_REQUEST);
        }
        Optional<Task> existingTask = taskRepository.findById(id);
        if (existingTask.isPresent()) {
            Task task = existingTask.get();
            task.setName(updatedTask.getName());
            task.setOwner(updatedTask.getOwner());
            task.setCommand(updatedTask.getCommand());
            task.setTaskExecutions(updatedTask.getTaskExecutions());
            taskRepository.save(task);
            return new ResponseEntity<>("Task updated successfully!", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND);
        }
    }

    //  Delete a task
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTask(@PathVariable String id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return new ResponseEntity<>("Task deleted successfully!", HttpStatus.OK);
        } else {
            return new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND);
        }
    }

    private boolean isValidCommand(String command) {
    if (command == null || command.isBlank()) {
        return false;
    }

    // Allow only a whitelist of safe commands
    String lower = command.toLowerCase();

    return lower.startsWith("echo") || 
           lower.startsWith("dir") || 
           lower.startsWith("ping");
}
@PutMapping("/{id}/executions")
public ResponseEntity<?> runTask(@PathVariable String id) {
    Optional<Task> taskOptional = taskRepository.findById(id);
    if (taskOptional.isEmpty()) {
        return new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND);
    }

    Task task = taskOptional.get();

    if (!isValidCommand(task.getCommand())) {
        return new ResponseEntity<>("Invalid or unsafe command!", HttpStatus.BAD_REQUEST);
    }

    Instant start = Instant.now();
    StringBuilder output = new StringBuilder();

    try {
        // Always run via Windows cmd
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/c", task.getCommand());
        builder.redirectErrorStream(true); // merge stdout + stderr
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return new ResponseEntity<>("Command failed with exit code: " + exitCode 
                    + "\nOutput: " + output, HttpStatus.INTERNAL_SERVER_ERROR);
        }

    } catch (IOException | InterruptedException e) {
    return new ResponseEntity<>("Error while executing command: " + e.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR);
        }

    Instant end = Instant.now();
    TaskExecution execution = new TaskExecution(start, end, output.toString().trim());

    task.getTaskExecutions().add(execution);
    taskRepository.save(task);

    return new ResponseEntity<>(execution, HttpStatus.OK);
}

}
