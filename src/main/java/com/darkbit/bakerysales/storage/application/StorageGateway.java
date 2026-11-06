package com.darkbit.bakerysales.storage.application;

import java.time.Duration;

public interface StorageGateway {

	SignedUpload createSignedUpload(String path, Duration ttl);

	void delete(String path);

	record SignedUpload(String uploadUrl, String token) {
	}
}
