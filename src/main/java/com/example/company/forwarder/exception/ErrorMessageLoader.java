package com.example.company.forwarder.exception;

import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Configuration
public class ErrorMessageLoader {
    private static Map<String, ErrorMessage> errorMessageMap;

    public ErrorMessageLoader(){
        try {
            Charset charset = StandardCharsets.UTF_8;

            Properties englishMessage = new Properties();
            englishMessage.load(new InputStreamReader(new ClassPathResource("message_forwarder_en.properties").getInputStream(), charset));

            errorMessageMap = englishMessage.entrySet().stream().collect(
                    Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> {
                                ErrorMessage errorMessage = new ErrorMessage();
                                errorMessage.setEn(e.getValue().toString());
                                return errorMessage;
                            }
                    )
            );

            Properties vietnameseMessage = new Properties();
            vietnameseMessage.load(new InputStreamReader(new ClassPathResource("message_forwarder_vn.properties").getInputStream(), charset));

            errorMessageMap.forEach((key, value) -> value.setVn(vietnameseMessage.getProperty(key)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ErrorMessage getMessage(String errorCode, String replaceStr){
        ErrorMessage errorMessage = errorMessageMap.get(errorCode);

        if(StringUtils.isNotBlank(replaceStr)){
            String msgEn = String.format(errorMessage.getEn(), replaceStr);
            String msgVn = String.format(errorMessage.getVn(), replaceStr);
            errorMessage.setEn(msgEn);
            errorMessage.setVn(msgVn);
        }
        return errorMessage;
    }

    public static ErrorMessage getMessage(String errorCode){
        if(errorCode.startsWith("HYD-35-1")){
            return errorMessageMap.get("HYD-35-101");
        }
        return errorMessageMap.get(errorCode);
    }
}
