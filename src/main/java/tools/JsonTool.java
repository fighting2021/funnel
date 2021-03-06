package tools;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule.Priority;

public class JsonTool {
	private static ObjectMapper objectMapper;
	
	static{
        JaxbAnnotationModule jaxbMod = new JaxbAnnotationModule();
        jaxbMod.setPriority(Priority.SECONDARY);
        objectMapper = new ObjectMapper();           
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.registerModule(jaxbMod);
    }
	
    public static String toJsonQuietly(Object obj) {
    	String json = "";
    	if(obj == null){
    	    return json;
        }
    	if(obj instanceof String){
    	    return (String) obj;
        }
    	try {
			json = objectMapper.writeValueAsString(obj);
		} catch (JsonProcessingException e) {}  
    	return json;
    }
    
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
			objectMapper.readValue(json, clazz);
		} catch (JsonProcessingException e) {
		}
        return null;
    }
    
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
    	try {
			objectMapper.readValue(json, typeReference);
		} catch (JsonProcessingException e) {
		}
        return null;
    }
    
}
