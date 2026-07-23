package com.ibrhalil.systemforge.exception;

/**
 * Thrown when a requested entity/resource cannot be found. Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
