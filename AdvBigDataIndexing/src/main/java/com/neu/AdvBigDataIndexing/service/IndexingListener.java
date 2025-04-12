package com.neu.AdvBigDataIndexing.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch._types.mapping.*;
import com.neu.AdvBigDataIndexing.AdvBigDataIndexingApplication;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class IndexingListener {

    private static final String INDEX_NAME = "plan-index";

    private final ElasticsearchClient elasticsearchClient;
    private final Map<String, Map<String, Object>> documentMap = new HashMap<>();

    @RabbitListener(queues = AdvBigDataIndexingApplication.queueName)
    public void receiveMessage(Map<String, String> message) {
        System.out.println("Received message from RabbitMQ: " + message);

        if (message == null || message.isEmpty()) {
            System.out.println("Received empty message");
            return;
        }

        try {
            String operation = message.get("operation");
            JSONObject jsonBody = new JSONObject(message.get("body"));

            switch (operation.toUpperCase()) {
                case "SAVE" -> {
                    ensureIndexExists();
                    documentMap.clear();
                    flattenAndCollectDocuments(jsonBody, null, jsonBody.getString("objectType"), null);
                    bulkIndexDocuments();
                }
                case "DELETE" -> deleteDocuments(jsonBody);
                default -> System.out.println("Unsupported operation: " + operation);
            }
        } catch (Exception e) {
            System.err.println("Failed to process message in RabbitMQ: " + message);
            e.printStackTrace();
        }
    }

    private void flattenAndCollectDocuments(JSONObject jsonObject, String parentId, String objectType, String rootPlanId) {
        String objectId = jsonObject.getString("objectId");
        String docKey = parentId == null ? objectId : parentId + ":" + objectId;
        String routingKey = (parentId == null) ? objectId : parentId;

        Map<String, Object> flatMap = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object val = jsonObject.get(key);
            if (!(val instanceof JSONObject) && !(val instanceof JSONArray)) {
                flatMap.put(key, val);
            }
        }

        if (parentId == null) {
            flatMap.put("plan_join", objectType); // root
        } else {
            flatMap.put("plan_join", Map.of(
                    "name", objectType,
                    "parent", parentId
            ));
        }

        documentMap.put(docKey, flatMap);

        for (String key : jsonObject.keySet()) {
            Object val = jsonObject.get(key);
            if (val instanceof JSONObject subObj && subObj.has("objectId")) {
                flattenAndCollectDocuments(subObj, objectId, subObj.getString("objectType"), rootPlanId == null ? objectId : rootPlanId);
            } else if (val instanceof JSONArray arr) {
                for (Object item : arr) {
                    if (item instanceof JSONObject subJson && subJson.has("objectId")) {
                        flattenAndCollectDocuments(subJson, objectId, subJson.getString("objectType"), rootPlanId == null ? objectId : rootPlanId);
                    }
                }
            }
        }
    }

    private void bulkIndexDocuments() throws IOException {
        for (Map.Entry<String, Map<String, Object>> entry : documentMap.entrySet()) {
            String[] keyParts = entry.getKey().split(":");
            String parentId = keyParts.length == 2 ? keyParts[0] : keyParts[0];
            String objectId = keyParts.length == 2 ? keyParts[1] : keyParts[0];

            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(objectId)
                    .routing(parentId)
                    .document(entry.getValue())
            ));
        }
    }

    private void deleteDocuments(JSONObject jsonObject) throws IOException {
        List<String> ids = extractAllObjectIds(jsonObject);
        for (String id : ids) {
            elasticsearchClient.delete(DeleteRequest.of(d -> d.index(INDEX_NAME).id(id)));
        }
    }

    private void ensureIndexExists() throws IOException {
        boolean exists = elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME)).value();
        if (!exists) {
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("1")))
                    .mappings(m -> m
                            .properties("plan_join", Property.of(p -> p.join(j -> j
                                    .relations("plan", List.of("membercostshare", "planservice"))
                                    .relations("planservice", List.of("service", "planserviceCostShare"))
                            )))
                    )
            ));
        }
    }

    private List<String> extractAllObjectIds(JSONObject jsonObject) {
        List<String> ids = new ArrayList<>();
        if (jsonObject.has("objectId")) {
            ids.add(jsonObject.getString("objectId"));
        }
        for (String key : jsonObject.keySet()) {
            Object val = jsonObject.get(key);
            if (val instanceof JSONObject) {
                ids.addAll(extractAllObjectIds((JSONObject) val));
            } else if (val instanceof JSONArray arr) {
                for (Object obj : arr) {
                    if (obj instanceof JSONObject) {
                        ids.addAll(extractAllObjectIds((JSONObject) obj));
                    }
                }
            }
        }
        return ids;
    }
}