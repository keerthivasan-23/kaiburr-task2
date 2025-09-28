package com.example.kaiburrtasks.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.kaiburrtasks.model.Task;

public interface TaskRepository extends MongoRepository<Task, String> {
    // Custom query to search tasks by name (case-insensitive, contains)
    List<Task> findByNameContainingIgnoreCase(String name);
}
