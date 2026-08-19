package com.datafuturex.assistant.biztools.stub;

import com.datafuturex.assistant.biztools.api.GraphContextEnrichPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpGraphContextEnrichPort implements GraphContextEnrichPort {

    @Override
    public String enrich(String message) {
        return message;
    }
}
