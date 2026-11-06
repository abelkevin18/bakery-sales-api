package com.darkbit.bakerysales.storage.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.darkbit.bakerysales.storage.infrastructure.JdbcImageUsageRepository;

/**
 * Borra objetos de storage que quedaron sin referencia (imagen reemplazada o
 * registro eliminado). Se ejecuta después del commit y nunca interrumpe la
 * operación de negocio: si el borrado falla solo se registra la advertencia y
 * el objeto queda como huérfano para una limpieza manual.
 */
@Service
public class ImageCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ImageCleanupService.class);

	private final StorageGateway gateway;
	private final JdbcImageUsageRepository usageRepository;

	public ImageCleanupService(StorageGateway gateway, JdbcImageUsageRepository usageRepository) {
		this.gateway = gateway;
		this.usageRepository = usageRepository;
	}

	public void deleteAfterCommit(String path) {
		if (path == null || path.isBlank()) {
			return;
		}
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deleteIfUnused(path);
				}
			});
			return;
		}
		deleteIfUnused(path);
	}

	public boolean deleteIfUnused(String path) {
		if (path == null || path.isBlank()) {
			return false;
		}
		try {
			if (usageRepository.isReferenced(path)) {
				return false;
			}
			gateway.delete(path);
			return true;
		}
		catch (RuntimeException exception) {
			log.warn("No se pudo eliminar la imagen huérfana '{}': {}", path, exception.getMessage());
			return false;
		}
	}
}
