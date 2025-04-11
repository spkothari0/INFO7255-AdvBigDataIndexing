package com.neu.AdvBigDataIndexing.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
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
                case "SAVE" -> indexParentAndChildren(jsonBody);
                case "DELETE" -> deleteDocuments(jsonBody);
                default -> System.out.println("Unsupported operation: " + operation);
            }
        } catch (Exception e) {
            System.err.println("Failed to process message in RabbitMQ: " + message);
            e.printStackTrace();
        }
    }

    private void indexParentAndChildren(JSONObject planJson) throws IOException {
        ensureIndexExists();

        // Index parent plan
        String planId = planJson.getString("objectId");
        Map<String, Object> planMap = extractFields(planJson);
        planMap.put("plan_join", "plan");  // define as root of join tree

        elasticsearchClient.index(IndexRequest.of(i -> i
                .index(INDEX_NAME)
                .id(planId)
                .document(planMap)
                .routing(planId)
        ));

        // Index planCostShares as child
        if (planJson.has("planCostShares")) {
            JSONObject costShares = planJson.getJSONObject("planCostShares");
            String childId = costShares.getString("objectId");

            Map<String, Object> childMap = extractFields(costShares);
            childMap.put("plan_join", Map.of("name", "planCostShares", "parent", planId));

            elasticsearchClient.index(IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(childId)
                    .routing(planId)
                    .document(childMap)
            ));
        }

        // Index linkedPlanServices and their nested children
        if (planJson.has("linkedPlanServices")) {
            JSONArray linkedServices = planJson.getJSONArray("linkedPlanServices");

            for (int i = 0; i < linkedServices.length(); i++) {
                JSONObject serviceObj = linkedServices.getJSONObject(i);
                String serviceId = serviceObj.getString("objectId");

                Map<String, Object> serviceMap = extractFields(serviceObj);
                serviceMap.put("plan_join", Map.of("name", "linkedPlanServices", "parent", planId));

                elasticsearchClient.index(IndexRequest.of(iReq -> iReq
                        .index(INDEX_NAME)
                        .id(serviceId)
                        .routing(planId)
                        .document(serviceMap)
                ));

                // linkedService child
                if (serviceObj.has("linkedService")) {
                    JSONObject linkedService = serviceObj.getJSONObject("linkedService");
                    String lsId = linkedService.getString("objectId");

                    Map<String, Object> lsMap = extractFields(linkedService);
                    lsMap.put("plan_join", Map.of("name", "linkedService", "parent", serviceId));

                    elasticsearchClient.index(IndexRequest.of(iReq -> iReq
                            .index(INDEX_NAME)
                            .id(lsId)
                            .routing(serviceId)
                            .document(lsMap)
                    ));
                }

                // planserviceCostShares child
                if (serviceObj.has("planserviceCostShares")) {
                    JSONObject costShare = serviceObj.getJSONObject("planserviceCostShares");
                    String pcsId = costShare.getString("objectId");

                    Map<String, Object> pcsMap = extractFields(costShare);
                    pcsMap.put("plan_join", Map.of("name", "planserviceCostShares", "parent", serviceId));

                    elasticsearchClient.index(IndexRequest.of(iReq -> iReq
                            .index(INDEX_NAME)
                            .id(pcsId)
                            .routing(serviceId)
                            .document(pcsMap)
                    ));
                }
            }
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
            try{
            elasticsearchClient.indices().create(CreateIndexRequest.of(c -> c
                    .index(INDEX_NAME)
                    .settings(IndexSettings.of(s -> s.numberOfShards("1").numberOfReplicas("1")))
                    .mappings(m -> m
                            .properties("plan_join", Property.of(p -> p.join(j -> j
                                    .relations("plan", List.of("planCostShares", "linkedPlanServices"))
                                    .relations("linkedPlanServices", List.of("linkedService", "planserviceCostShares"))
                            )))
                    )
            ));
            } catch (Exception e) {
                System.err.println("Failed to create index:");
                e.printStackTrace();
                throw e;
            }
        }
    }

    private Map<String, Object> extractFields(JSONObject jsonObject) {
        Map<String, Object> map = new HashMap<>();
        for (String key : jsonObject.keySet()) {
            Object value = jsonObject.get(key);
            if (!(value instanceof JSONObject || value instanceof JSONArray)) {
                map.put(key, value);
            }
        }
        return map;
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
            } else if (val instanceof JSONArray) {
                for (Object obj : (JSONArray) val) {
                    if (obj instanceof JSONObject) {
                        ids.addAll(extractAllObjectIds((JSONObject) obj));
                    }
                }
            }
        }

        return ids;
    }
}
