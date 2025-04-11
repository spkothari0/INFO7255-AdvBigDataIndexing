package com.neu.AdvBigDataIndexing.controller;

import com.neu.AdvBigDataIndexing.service.PlanService;
import com.neu.AdvBigDataIndexing.util.JsonValidator;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.everit.json.schema.ValidationException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RequiredArgsConstructor
@RestController
@RequestMapping(path = "/api/v1/plan")
public class PlanController {
    private final JsonValidator validator;
    private final PlanService planService;
    private final RabbitTemplate template;

    @Value("${spring.rabbitmq.topic.exchange}")
    private String exchange;

    @Value("${spring.rabbitmq.template.default-receive-queue}")
    private String routingKey;

    @PostMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPlan(@RequestBody(required = false) String planObject) throws JSONException, BadRequestException {

        if (planObject == null || planObject.isEmpty())
            throw new BadRequestException("Request body is missing!");

        JSONObject json = new JSONObject(planObject);
        try {
            validator.validateJson(json);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", ex.getErrorMessage()).toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String key = json.get("objectType").toString() + "_" + json.get("objectId").toString();
        if (planService.isKeyPresent(key)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new JSONObject().put("Message", "Plan already exist").toString());
        }

        String newEtag = planService.createPlan(json, key);

        // Send a message to queue for indexing
        sendToQueue("SAVE", planObject);

        return ResponseEntity.status(HttpStatus.CREATED).eTag(newEtag).body(new JSONObject().put("Message", "Created data with key: " + json.get("objectId")).toString());
    }

    @GetMapping(value = "/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPlan(@PathVariable String objectId,
                                     @RequestHeader HttpHeaders headers) throws JSONException, BadRequestException {
        String key = "plan_" + objectId;
        if (!planService.isKeyPresent(key))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());

        // Check if the ETag provided is not corrupt
        List<String> ifNoneMatch;
        try {
            ifNoneMatch = headers.get("if-none-match");
        } catch (Exception e) {
            throw new BadRequestException("ETag value invalid! Make sure the ETag value is a string!");
        }

        String eTag = planService.getETag(key);

        if (ifNoneMatch != null && ifNoneMatch.contains(eTag))
            return new ResponseEntity<>(null, HttpStatus.NOT_MODIFIED);
        else {
            JSONObject objectToReturn = planService.getPlan(key);
            return new ResponseEntity<>(objectToReturn.toString(), HttpStatus.OK);
        }
    }

    @DeleteMapping("/{objectId}")
    public ResponseEntity<?> deletePlan(@PathVariable String objectId) {
        String key = "plan_" + objectId;
        if (!planService.isKeyPresent(key))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());

        JSONObject plan = planService.getPlan(key);

        planService.deletePlan(key);

        // Send message to queue for deleting indices
        sendToQueue("DELETE", plan.toString());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping(path = "/{objectId}", produces = "application/json")
    public ResponseEntity<Object> patchPlan(@RequestBody String medicalPlan,
                                            @PathVariable String objectId, @RequestHeader HttpHeaders headers) throws BadRequestException {

        if (medicalPlan == null || medicalPlan.isEmpty())
            throw new BadRequestException("Request body is missing!");

        JSONObject planObject = new JSONObject(medicalPlan);
        try {
            validator.validateJson(planObject);
        } catch (ValidationException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new JSONObject().put("Error", ex.getErrorMessage()).toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        List<String> ifNoneMatch;
        try {
            ifNoneMatch = headers.get("if-match");
        } catch (Exception e) {
            throw new BadRequestException("ETag value invalid! Make sure the ETag value is a string!");
        }

        if (!planService.isKeyPresent("plan_" + objectId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Message", "ObjectId does not exist").toString());
        }

        if (ifNoneMatch == null)
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                    .body(new JSONObject().put("Message", "Precondition failed, missing eTag").toString());

        String newEtag = planService.patchPlan(planObject);

        String key = "plan_" + objectId;
        JSONObject objectToReturn = planService.getPlan(key);

        // Send message to queue for index update
        sendToQueue("SAVE", medicalPlan);
        return ResponseEntity.status(HttpStatus.OK).eTag(newEtag).body(objectToReturn.toString());
    }

    private void sendToQueue(String operation, String body) {
        Map<String, String> message = new HashMap<>();
        message.put("operation", operation);
        message.put("body", body);

        System.out.println("Sending message: " + message);
        template.convertAndSend(exchange, routingKey, message, m -> {
            m.getMessageProperties().setContentType("application/json");
            return m;
        });
    }
}
