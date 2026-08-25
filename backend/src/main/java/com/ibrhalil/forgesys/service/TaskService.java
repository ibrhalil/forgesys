package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.SearchRequest;
import com.ibrhalil.forgesys.dto.TaskRequest;
import com.ibrhalil.forgesys.dto.TaskResponse;
import com.ibrhalil.forgesys.entity.AuditEntity_;
import com.ibrhalil.forgesys.entity.Task;
import com.ibrhalil.forgesys.entity.TaskPriority;
import com.ibrhalil.forgesys.entity.TaskStatus;
import com.ibrhalil.forgesys.entity.Task_;
import com.ibrhalil.forgesys.exception.ResourceNotFoundException;
import com.ibrhalil.forgesys.persistence.repository.ProjectRepository;
import com.ibrhalil.forgesys.persistence.repository.TaskRepository;
import com.ibrhalil.forgesys.persistence.repository.UserRepository;
import com.ibrhalil.forgesys.audit.AuditLog;
import com.ibrhalil.forgesys.web.filter.FilterFieldSet;
import com.ibrhalil.forgesys.web.filter.FilterFieldType;
import com.ibrhalil.forgesys.web.filter.FilterSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Task operations scoped to a project (Faz 3 Stage 2). A task is always reached through
 * its owning project ({@code /projects/{projectId}/tasks}); a task belonging to another
 * project is not addressable here (404, no leak). Project + assignee existence are
 * validated explicitly so an invalid id yields a clean 404 rather than a DB integrity 500.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /**
     * Filterable/sortable attributes of the project's task list (K-49 — now fully
     * engine-wired); {@code q} matches {@code title} and {@code description}. The
     * kanban columns ({@code status}/{@code priority}) filter and sort; {@code dueDate}
     * is a DATE (ISO {@code yyyy-MM-dd}); {@code assigneeId} filters by the plain FK.
     */
    public static final FilterFieldSet FILTER_FIELDS = FilterFieldSet.builder()
            .field(Task_.TITLE, FilterFieldType.STRING, true)
            .field(Task_.DESCRIPTION, FilterFieldType.STRING, true)
            .enumField(Task_.STATUS, TaskStatus.class, false)
            .enumField(Task_.PRIORITY, TaskPriority.class, false)
            .field(Task_.ASSIGNEE_ID, FilterFieldType.UUID, false)
            .field(Task_.DUE_DATE, FilterFieldType.DATE, false)
            .field(AuditEntity_.CREATED_DATE, FilterFieldType.TEMPORAL, false)
            .field(AuditEntity_.UPDATED_AT, FilterFieldType.TEMPORAL, false)
            .build();

    private final TaskRepository taskRepository;
    private final TaskListQueryExecutor taskListQueryExecutor;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<TaskResponse> list(UUID projectId, String q, List<String> qFields, Pageable pageable) {
        assertProjectExists(projectId);
        Specification<Task> spec = FilterSpecifications.from(FILTER_FIELDS,
                StringUtils.hasText(q) ? q.trim() : null, qFields, List.of());
        return taskListQueryExecutor.search(spec.and(projectScope(projectId)), pageable);
    }

    /** Full {@link SearchRequest} variant backing {@code POST /projects/{id}/tasks/search}. */
    @Transactional(readOnly = true)
    public Page<TaskResponse> search(UUID projectId, SearchRequest request, Pageable pageable) {
        assertProjectExists(projectId);
        Specification<Task> spec = FilterSpecifications.from(FILTER_FIELDS, request.q(), request.qFields(),
                request.filters());
        return taskListQueryExecutor.search(spec.and(projectScope(projectId)), pageable);
    }

    private Specification<Task> projectScope(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get(Task_.PROJECT_ID), projectId);
    }

    private void assertProjectExists(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(UUID projectId, UUID taskId) {
        return toResponse(getTaskOrThrow(projectId, taskId));
    }

    @Transactional
    @AuditLog(action = "task_created", entityType = "Task", entityId = "#result.id", entityName = "#result.title")
    public TaskResponse create(UUID projectId, TaskRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project not found: " + projectId);
        }
        validateAssignee(request.assigneeId());
        Task task = new Task();
        task.setProjectId(projectId);
        applyRequest(task, request);
        // Defaults on create (status/priority are optional in the request).
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.TODO);
        }
        if (task.getPriority() == null) {
            task.setPriority(TaskPriority.MEDIUM);
        }
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "task_updated", entityType = "Task", entityId = "#result.id", entityName = "#result.title")
    public TaskResponse update(UUID projectId, UUID taskId, TaskRequest request) {
        Task task = getTaskOrThrow(projectId, taskId);
        validateAssignee(request.assigneeId());
        applyRequest(task, request);
        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    @Transactional
    @AuditLog(action = "task_deleted", entityType = "Task", entityId = "#taskId", entityName = "")
    public void delete(UUID projectId, UUID taskId) {
        if (!taskRepository.existsByIdAndProjectId(taskId, projectId)) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        taskRepository.deleteById(taskId);
    }

    private void applyRequest(Task task, TaskRequest request) {
        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.status() != null) {
            task.setStatus(request.status());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setAssigneeId(request.assigneeId());
        task.setDueDate(request.dueDate());
    }

    private Task getTaskOrThrow(UUID projectId, UUID taskId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private void validateAssignee(UUID assigneeId) {
        if (assigneeId != null && !userRepository.existsById(assigneeId)) {
            throw new ResourceNotFoundException("Assignee not found: " + assigneeId);
        }
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getAssigneeId(), task.getDueDate());
    }
}
