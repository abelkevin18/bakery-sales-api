package com.darkbit.bakerysales;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"app.supabase.url=https://project.supabase.co",
	"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://project.supabase.co/auth/v1",
	"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://project.supabase.co/auth/v1/.well-known/jwks.json"
})
class BakerySalesApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
