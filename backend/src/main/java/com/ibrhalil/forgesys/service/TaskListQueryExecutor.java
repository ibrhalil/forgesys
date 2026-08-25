package com.ibrhalil.forgesys.service;

import com.ibrhalil.forgesys.dto.TaskResponse;
import com.ibrhalil.forgesys.entity.BaseEntity_;
import com.ibrhalil.forgesys.entity.Task;
import com.ibrhalil.forgesys.entity.Task_;
import com.ibrhalil.forgesys.web.projection.ProjectionListQuery;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Read side of the project task list (K-49): flat Criteria DTO projection through the
 * shared filter engine — the list gains {@code q} search, structured filters and the
 * {@code dueDate} (DATE) / {@code assigneeId} / {@code description} columns the kanban
 * board renders. The project scoping predicate is supplied by the caller.
 */
@Component
public class TaskListQueryExecutor {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<TaskResponse> search(@Nullable Specification<Task> spec, Pageable pageable) {
        return ProjectionListQuery.execute(entityManager, Task.class, TaskResponse.class,
                TaskService.FILTER_FIELDS,
                (root, query, cb) -> cb.construct(TaskResponse.class,
                        root.get(BaseEntity_.ID),
                        root.get(Task_.PROJECT_ID),
                        root.get(Task_.TITLE),
                        root.get(Task_.DESCRIPTION),
                        root.get(Task_.STATUS),
                        root.get(Task_.PRIORITY),
                        root.get(Task_.ASSIGNEE_ID),
                        root.get(Task_.DUE_DATE)),
                spec, pageable);
    }
}
