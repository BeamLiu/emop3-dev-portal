package io.emop.integrationtest.usecase.common;

import io.emop.integrationtest.domain.SampleMaterial;
import io.emop.model.common.CheckoutInfo;
import io.emop.model.common.UserContext;
import io.emop.model.draft.DraftModelObject;
import io.emop.service.S;
import io.emop.service.api.data.ObjectService;
import io.emop.service.api.domain.common.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.emop.integrationtest.util.Assertion.assertEquals;
import static io.emop.integrationtest.util.Assertion.assertNotNull;

@RequiredArgsConstructor
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CheckoutServiceTest {

    private final CheckoutService checkoutService = S.service(CheckoutService.class);
    private final ObjectService objectService = S.service(ObjectService.class);

    @BeforeAll
    public void setup() {
        UserContext.setCurrentUser(new UserContext(100L, List.of("admin")));
    }

    @Test
    @Order(1)
    public void testCheckoutInfoDeserialization() {
        S.withStrongConsistency(this::testCheckoutInfoDeserializationInternal);
    }

    /**
     * 测试 CheckoutInfo 从数据库反序列化的问题
     * 重现问题：当对象被 checkout 后保存到数据库，再从数据库读取时，
     * _checkout 字段从 JSONB 反序列化为 Map 而不是 CheckoutInfo 对象
     */
    private void testCheckoutInfoDeserializationInternal() {
        // 1. 创建草稿对象
        DraftModelObject draft = new DraftModelObject();
        draft.setTargetObjectType(SampleMaterial.class.getName());
        draft.set("code", "CHECKOUT-TEST-" + System.currentTimeMillis());
        draft.set("name", "Checkout Test Material");
        draft.set("revId", "A");

        // 保存草稿并转换为正式对象
        draft = objectService.save(draft);
        SampleMaterial material = (SampleMaterial) draft.convertToModelObject();
        
        log.info("Created material with id: {}", material.getId());

        // 2. 签出对象
        material = checkoutService.checkout(material, "Test checkout", 60);
        assertNotNull(material.getCheckoutInfo(), "CheckoutInfo should not be null after checkout");
        
        Long materialId = material.getId();
        log.info("Checked out material, CheckoutInfo: {}", material.getCheckoutInfo());

        // 3. 从数据库重新加载对象（这里会触发反序列化）
        SampleMaterial reloadedMaterial = objectService.findById(materialId);
        assertNotNull(reloadedMaterial, "Reloaded material should not be null");
        
        log.info("Reloaded material from database");

        // 4. 尝试获取 CheckoutInfo
        CheckoutInfo checkoutInfo = reloadedMaterial.getCheckoutInfo();
        
        assertNotNull(checkoutInfo, "CheckoutInfo should not be null after reload");
        assertEquals(true, reloadedMaterial.isCheckedOut(), "Material should be checked out");
        
        log.info("Successfully retrieved CheckoutInfo after reload: {}", checkoutInfo);

        // 5. 签入对象
        reloadedMaterial = checkoutService.checkin(reloadedMaterial, "Test checkin");
        assertEquals(false, reloadedMaterial.isCheckedOut(), "Material should not be checked out after checkin");
    }
}
