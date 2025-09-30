package io.emop.integrationtest.usecase.common;

import io.emop.model.common.GenericModelObject;
import io.emop.model.common.Schema;
import io.emop.model.metadata.AttributeDefinition.DomainType;
import io.emop.model.metadata.TypeDefinition;
import io.emop.model.metadata.Types;
import io.emop.model.metadata.ValueDomainData;
import io.emop.model.query.Q;
import io.emop.service.S;
import io.emop.service.api.metadata.MetadataService;
import io.emop.service.api.metadata.MetadataUpdateService;
import io.emop.service.api.metadata.ValueDomainDataService;
import io.emop.service.api.data.ObjectService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.emop.integrationtest.util.Assertion.*;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ValueDomainTest {

    @BeforeAll
    public void setup() {
        initializeCascadeExample();
    }

    @Test
    @Order(1)
    public void testRetrieveData() {
        GenericModelObject address = new GenericModelObject("Address");
        address.set("province", "a");
        address.set("city", "b");
        address.set("district", "e");

        TypeDefinition definition = S.service(MetadataService.class).retrieveFullTypeDefinition("Address");

        ValueDomainDataService service = S.service(ValueDomainDataService.class);

        List<ValueDomainData> result = service.getEnumValues("Address.gender");
        assertEquals(2, result.size());
        assertContains(result, "男");
        log.info("data: {}", result);

        result = definition.getAttribute("gender").asEnum().getEnumValues();
        assertEquals(2, result.size());
        assertContains(result, "女");
        log.info("data: {}", result);
        assertFalse(definition.getAttribute("gender").asEnum().isValid("xxxx"));

        result = definition.getAttribute("province").asCascade().getCascadeValues(address);
        assertEquals(2, result.size());
        log.info("data: {}", result);
        assertContains(result, "浙江");
        assertContains(result, "江苏");

        result = definition.getAttribute("city").asCascade().getCascadeValues(address);
        assertEquals(2, result.size());
        log.info("data: {}", result);
        assertContains(result, "宁波");
        assertContains(result, "杭州");

        result = definition.getAttribute("district").asCascade().getCascadeValues(address);
        assertEquals(1, result.size());
        log.info("data: {}", result);
        assertContains(result, "江东");

        assertTrue(definition.getAttribute("city").asCascade().isValid(address));
        assertTrue(definition.getAttribute("province").asCascade().isValid(address));
        assertTrue(definition.getAttribute("district").asCascade().isValid(address));
        address.set("district", "d");
        assertFalse(definition.getAttribute("district").asCascade().isValid(address));
        address.set("district", "dxxxx");
        assertFalse(definition.getAttribute("district").asCascade().isValid(address));
    }

    private void assertContains(List<ValueDomainData> result, String zhCNVal) {
        assertTrue(result.stream().anyMatch(v -> v.get("name", Locale.CHINA).equals(zhCNVal)));
    }

    private void initializeCascadeExample() {
        TypeDefinition typeDef = TypeDefinition.builder("Address")
                .superType(GenericModelObject.class.getName())
                .persistentInfo(Schema.SAMPLE, "Address")
                .description("manually create Address")
                .requiredAttribute("province", Types.STRING)
                .requiredAttribute("city", Types.STRING)
                .requiredAttribute("district", Types.STRING)
                .modifyAttribute("province").cascadeValueDomain(Arrays.asList("province", "city", "district")).typeBuilder()
                .modifyAttribute("city").cascadeValueDomain(Arrays.asList("province", "city", "district")).typeBuilder()
                .modifyAttribute("district").cascadeValueDomain(Arrays.asList("province", "city", "district")).typeBuilder()
                .requiredAttribute("gender", Types.STRING)
                .modifyAttribute("gender").enumValueDomain().typeBuilder()
                .build();

        S.service(MetadataUpdateService.class).createOrUpdateType(typeDef);
        S.service(MetadataService.class).reloadTypeDefinitions();

        Q.result(ValueDomainData.class).where("value in ('a','b','c','d','e','f','g','h','i')").delete();
        // 第一级
        createValueDomainData("Address.province", "a", "浙江", "Zhejiang", null);
        createValueDomainData("Address.province", "b", "江苏", "Jiangsu", null);

        // 第二级
        createValueDomainData("Address.city", "a", "宁波", "Ningbo", "a");
        createValueDomainData("Address.city", "b", "杭州", "Hangzhou", "a");
        createValueDomainData("Address.city", "c", "苏州", "Suzhou", "b");
        createValueDomainData("Address.city", "d", "南京", "Nanjing", "b");

        // 第三级
        createValueDomainData("Address.district", "d", "海曙", "Haishu", "a/a");
        createValueDomainData("Address.district", "e", "江东", "Jiangdong", "a/b");
        createValueDomainData("Address.district", "f", "姑苏", "Gusu", "b/c");
        createValueDomainData("Address.district", "g", "虎丘", "Huqiu", "b/c");

        //枚举型
        createValueDomainData("Address.gender", "h", "男", "Man", DomainType.ENUM, null);
        createValueDomainData("Address.gender", "i", "女", "Female", DomainType.ENUM, null);
    }

    private void createValueDomainData(String attributePath, String code, String zhName, String enName, DomainType domainType, String cascadePath) {
        ValueDomainData data = new ValueDomainData();
        data.setAttributePath(attributePath);
        data.setDomainType(domainType.name());
        data.setValue(code);
        data.setNameZhCn(zhName);
        data.setNameEnUs(enName);
        data.setCascadeValuePath(cascadePath);

        S.service(ObjectService.class).save(data);
    }

    private void createValueDomainData(String attributePath, String code, String zhName, String enName, String cascadePath) {
        createValueDomainData(attributePath, code, zhName, enName, DomainType.CASCADE, cascadePath);
    }
}