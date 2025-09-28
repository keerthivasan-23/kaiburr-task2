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
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.FileReader;
import java.util.Map;

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
}@PutMapping("/{id}/executions")
public ResponseEntity<?> runTask(@PathVariable String id) {
    Optional<Task> taskOptional = taskRepository.findById(id);
    if (taskOptional.isEmpty()) {
        return new ResponseEntity<>("Task not found", HttpStatus.NOT_FOUND);
    }

    Task task = taskOptional.get();

    if (!isValidCommand(task.getCommand())) {
        return new ResponseEntity<>("Invalid or unsafe command!", HttpStatus.BAD_REQUEST);
    }

    String namespace = "default"; // change if your app is deployed elsewhere
    String podName = "exec-" + System.currentTimeMillis();

    try {
        // 1. Connect to Kubernetes API
        ApiClient client;
        try {
            client = ClientBuilder.cluster().build(); // in-cluster
        } catch (Exception e) {
            String kubeConfigPath = System.getProperty("user.home") + "/.kube/config"; // local dev
            client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(new FileReader(kubeConfigPath))).build();
        }
        io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client);
        CoreV1Api coreApi = new CoreV1Api(client);

        // 2. Define the busybox pod
        V1Pod pod = new V1Pod()
                .metadata(new V1ObjectMeta()
                        .name(podName)
                        .labels(Map.of("job", "task-exec")))
                .spec(new V1PodSpec()
                        .restartPolicy("Never")
                        .containers(List.of(
                                new V1Container()
                                        .name("runner")
                                        .image("busybox:1.36.1")
                                        .command(List.of("sh", "-c", task.getCommand()))
                        )));

        // 3. Create the pod
        coreApi.createNamespacedPod(namespace, pod, null, null, null, null);

        // 4. Wait until pod finishes
        String phase = "Pending";
        long startTimeMillis = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTimeMillis < 60_000) { // timeout 60s
            V1Pod current = coreApi.readNamespacedPodStatus(podName, namespace, null);
            phase = current.getStatus().getPhase();
            if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                break;
            }
            Thread.sleep(1000);
        }

        // 5. Collect logs
        String logs = coreApi.readNamespacedPodLog(
                podName,
                namespace,
                "runner",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        // 6. Delete pod afterwards
        try {
            coreApi.deleteNamespacedPod(podName, namespace, null, null, null, null, null, null);
        } catch (Exception ignored) {}

        // 7. Save execution in DB
        Instant start = Instant.ofEpochMilli(startTimeMillis);
        Instant end = Instant.now();
        TaskExecution execution = new TaskExecution(start, end, logs.trim());

        task.getTaskExecutions().add(execution);
        taskRepository.save(task);

        return new ResponseEntity<>(execution, HttpStatus.OK);

    } catch (Exception e) {
        e.printStackTrace();
        return new ResponseEntity<>("Error while executing in Kubernetes: " + e.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

}
