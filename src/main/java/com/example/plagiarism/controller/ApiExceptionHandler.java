package com.example.plagiarism.controller;

import java.io.IOException;
import org.springframework.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Spring handles missing parts, invalid JSON, unsupported media, and status exceptions as ProblemDetail. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large. Maximum ZIP: 2 MiB; source: 256 KiB.");
    }

    @ExceptionHandler({IOException.class, MultipartException.class})
    public ResponseEntity<Object> invalidUpload(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Could not read the upload. Send valid UTF-8 source files or a valid ZIP.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception ex) {
        logger.error("Unexpected comparison failure", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to complete the comparison.");
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Object> databaseUnavailable(Exception ex) {
        logger.error("Comparison history database operation failed", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "The history database is unavailable. Please try again shortly.");
    }

    private ResponseEntity<Object> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
