package com.example.company.forwarder.common.error;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Dựng ErrorBody với message song ngữ lấy từ i18n/errors_{vi,en}.properties (key "error.&lt;tên mã&gt;"). */
@Component
public class ErrorMessages {

    private static final Locale VI = Locale.forLanguageTag("vi");
    private static final Locale EN = Locale.ENGLISH;

    private final MessageSource messageSource;

    public ErrorMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public ErrorBody body(ForwarderErrorCode code, String requestId) {
        String key = "error." + code.name();
        return new ErrorBody(code.code(), code.errorCode(),
                new ErrorBody.Message(messageSource.getMessage(key, null, key, VI),
                        messageSource.getMessage(key, null, key, EN)),
                requestId);
    }
}
