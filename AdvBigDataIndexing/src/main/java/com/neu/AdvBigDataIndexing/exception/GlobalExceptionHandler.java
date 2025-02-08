package com.neu.AdvBigDataIndexing.exception;

import org.apache.coyote.BadRequestException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import java.util.ArrayList;
import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(Exception.class)
    public final ResponseEntity<Object> handleAllExceptions(Exception ex) {
        String detail = ex.getLocalizedMessage();
        System.err.println("Exception: " + detail);
        return new ResponseEntity<Object>(new JSONObject().put("Server Error", detail).toString(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BadRequestException.class)
    public final ResponseEntity<Object> handleBadRequestException(BadRequestException ex) {
        String detail = ex.getLocalizedMessage();
        return new ResponseEntity<Object>(new JSONObject().put("BadRequestException: ", detail).toString(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public final ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        String detail = ex.getLocalizedMessage();
        return new ResponseEntity<Object>(new JSONObject().put("RuntimeException: ", detail).toString(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(JSONException.class)
    public final ResponseEntity<Object> handleResourceNotFoundException(JSONException ex) {
        List<String> details = new ArrayList<>();
        details.add(ex.getLocalizedMessage());
        return new ResponseEntity<Object>(new JSONObject().put("JsonException: ", details).toString(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public final ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = new ArrayList<>();
        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            details.add(error.getDefaultMessage());
        }
        return new ResponseEntity<Object>(new JSONObject().put("Validation Error", details).toString(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        String detail = ex.getLocalizedMessage();
        return new ResponseEntity<Object>(new JSONObject().put("Validation Error", detail).toString(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingPathVariableException.class)
    protected ResponseEntity<Object> handleMissingPathVariable(MissingPathVariableException ex) {
        return new ResponseEntity<Object>("Please enter all fields", HttpStatus.BAD_REQUEST);
    }
}
