package com.ibrhalil.forgesys.dto;

import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;

import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        UUID assigneeId,
        LocalDate dueDate
) {
}
