package io.emop.integrationtest.usecase.other;

import io.emop.integrationtest.util.Assertion;
import io.emop.service.S;
import io.emop.service.api.other.MockService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class ScriptingSampleTest {

    @Test
    void testScriptHelloWorld() {
        String reply = S.service(MockService.class).scriptHelloWorld("EMOP3.0 Platform");
        log.info("reply: {}", reply);
        Assertion.assertTrue(reply.startsWith("hello EMOP3.0 Platform, registered types: ["));
    }
}