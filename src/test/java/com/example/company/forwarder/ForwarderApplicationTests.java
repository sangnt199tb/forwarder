package com.example.company.forwarder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Khởi động được với khoá test (không cần file secret thật). R2DBC chỉ kết nối DB khi có truy vấn. */
@SpringBootTest
class ForwarderApplicationTests {

	@DynamicPropertySource
	static void gatewayKey(DynamicPropertyRegistry registry) {
		registry.add("forwarder.gateway-token.private-key", () -> TestKeys.PRIVATE_KEY);
	}

	@Test
	void contextLoads() {
	}

}
