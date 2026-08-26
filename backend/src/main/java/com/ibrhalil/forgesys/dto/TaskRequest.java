package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Create/update a task; {@code status}/{@code priority} default to
 * {@code TODO}/{@code MEDIUM} on create and {@code null} means "leave unchanged"
 * on update. {@code assigneeId} is validated against the tenant's users when present.
 */
public record TaskRequest(
        @NotBlank(message = "Task title is required")
        @Size(max = 200, message = "Task title must be at most 200 characters")
        String title,

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        String description,

        TaskStatus status,

        TaskPriority priority,

        UUID assigneeId,

        LocalDate dueDate
) {
}
