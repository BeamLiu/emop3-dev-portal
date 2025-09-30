package io.emop.integrationtest.usecase.other;

import io.emop.integrationtest.util.Assertion;
import io.emop.integrationtest.util.TimerUtils;
import io.emop.service.S;
import io.emop.model.common.Revisionable;
import io.emop.service.api.domain.common.RevisionService;
import io.emop.model.common.ItemRevision;
import io.emop.service.api.other.MockService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
public class TransactionManagementTest {

    private static final String dateCode = String.valueOf(System.currentTimeMillis());
    private List<ItemRevision> testData;

    @BeforeEach
    void setUp() {
        testData = prepareData(10);
    }

    @Test
    void testTransactionRollback() {
        TimerUtils.measureExecutionTime("TransactionTest - rollback in transaction", () -> {
            testWithTransaction(testData);
        });
    }

    @Test
    void testCommitWithoutTransaction() {
        TimerUtils.measureExecutionTime("TransactionTest - commit without transaction", () -> {
            testWithoutTransaction(testData);
        });
    }

    private List<ItemRevision> prepareData(int size) {
        List<ItemRevision> data = IntStream.rangeClosed(1, size).mapToObj(idx -> {
            ItemRevision rev = ItemRevision.newModel(dateCode + "-" + idx + "_", "A");
            rev.setName("TransactionManagement-" + dateCode + "-" + idx);
            return rev;
        }).collect(Collectors.toList());
        log.info("saved {}", data);
        return data;
    }

    private void testWithTransaction(List<ItemRevision> data) {
        ItemRevision firstElement = data.get(0);
        try {
            S.service(MockService.class).saveWithTransaction(Arrays.asList(firstElement, data.get(1)));
            throw new RuntimeException("not reachable");
        } catch (Exception e) {
            if (!getFullStackTrace(e).contains("an expected breakpoint")) {
                throw e;
            }
        }
        //the first data should be rolled-back
        Assertion.assertNull(S.service(RevisionService.class).queryRevision(new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), firstElement.getCode(), firstElement.getRevId())));
    }

    private void testWithoutTransaction(List<ItemRevision> data) {
        ItemRevision firstElement = data.get(2);
        try {
            S.service(MockService.class).saveWithoutTransaction(Arrays.asList(firstElement, data.get(3)));
            throw new RuntimeException("not reachable");
        } catch (Exception e) {
            if (!getFullStackTrace(e).contains("an expected breakpoint")) {
                throw e;
            }
        }
        //the first data should not be rolled-back
        Assertion.assertNotNull(S.service(RevisionService.class).queryRevision(new Revisionable.CriteriaByCodeAndRevId<>(ItemRevision.class.getName(), firstElement.getCode(), firstElement.getRevId())));
    }

    public static String getFullStackTrace(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}