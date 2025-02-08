package com.neu.AdvBigDataIndexing.service;

import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

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
