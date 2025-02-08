package com.neu.AdvBigDataIndexing.util;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class JsonValidator {
    public void validateJson(JSONObject object) throws IOException {
        try(InputStream inputStream = getClass().getResourceAsStream("/JsonSchema.json")){
            assert inputStream != null;
            JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
            Schema schema = SchemaLoader.load(rawSchema);
            schema.validate(object);
        }
    }
}
