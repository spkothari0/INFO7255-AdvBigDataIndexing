package com.neu.AdvBigDataIndexing.service;

import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanService {
    private final Jedis jedis;
    private final ETagService eTagService;

    public boolean isKeyPresent(String key) {
        String value = jedis.get(key);
        return !(value == null || value.isEmpty());
    }

    public String getETag(String key) {
        JSONObject response = getPlan(key);
        return eTagService.getETag(response);
    }

    public String createPlan(JSONObject plan, String key) {
        jedis.set(key, String.valueOf(plan));
        return eTagService.getETag(plan);
    }

    public String patchPlan(JSONObject jsonObject) {
        String id = "plan_" + jsonObject.getString("objectId");  // Fetch ID
        JSONObject oldPlan = getPlan(id);  // Retrieve existing object

        // Recursively update the JSON
        mergeJson(oldPlan, jsonObject);

        // Save the updated plan
        jedis.set(id, oldPlan.toString());

        return eTagService.getETag(oldPlan);
    }

    /**
     * Recursively merge new JSON into existing JSON
     */
    private void mergeJson(JSONObject existing, JSONObject updates) {
        for (String key : updates.keySet()) {
            Object newValue = updates.get(key);

            if (!existing.has(key)) {
                // If the key doesn't exist in old data, add it
                existing.put(key, newValue);
            } else {
                Object existingValue = existing.get(key);

                if (newValue instanceof JSONObject && existingValue instanceof JSONObject) {
                    // Recursively merge JSON objects
                    mergeJson((JSONObject) existingValue, (JSONObject) newValue);
                } else if (newValue instanceof JSONArray && existingValue instanceof JSONArray) {
                    // Merge JSON arrays intelligently
                    mergeJsonArray((JSONArray) existingValue, (JSONArray) newValue);
                } else {
                    // For simple key-value pairs, update only if different
                    if (!existingValue.equals(newValue)) {
                        existing.put(key, newValue);
                    }
                }
            }
        }
    }

    /**
     * Merge JSONArray:
     * - Append new elements if objectId doesn't exist
     * - Update existing elements based on objectId
     */
    private void mergeJsonArray(JSONArray existingArray, JSONArray newArray) {
        Map<String, JSONObject> existingMap = new HashMap<>();

        // Convert existing array to map for easy lookup
        for (int i = 0; i < existingArray.length(); i++) {
            JSONObject obj = existingArray.getJSONObject(i);
            if (obj.has("objectId")) {
                existingMap.put(obj.getString("objectId"), obj);
            }
        }

        for (int i = 0; i < newArray.length(); i++) {
            JSONObject newObj = newArray.getJSONObject(i);
            if (newObj.has("objectId")) {
                String objectId = newObj.getString("objectId");

                if (existingMap.containsKey(objectId)) {
                    // Update existing object in the array
                    mergeJson(existingMap.get(objectId), newObj);
                } else {
                    // Append new object to array
                    existingArray.put(newObj);
                }
            }
        }
    }


    public JSONObject getPlan(String key) {
        String jsonString = jedis.get(key);

        // Convert the string back to a JSONObject
        if (jsonString != null && !jsonString.isEmpty()) {
            return new JSONObject(jsonString);
        }
        // Return an empty JSONObject if the key does not exist
        return new JSONObject();
    }

    public void deletePlan(String key) {
        jedis.del(key);
    }
}
