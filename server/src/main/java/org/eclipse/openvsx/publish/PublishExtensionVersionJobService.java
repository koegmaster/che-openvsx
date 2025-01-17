/** ******************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class PublishExtensionVersionJobService {

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    StorageUtilService storageUtil;

    @Autowired
    ExtensionService extensions;

    public FileResource getFileResource(long id) {
        return entityManager.find(FileResource.class, id);
    }

    @Transactional
    public void deleteFileResources(ExtensionVersion extVersion) {
        repositories.findFiles(extVersion)
                .filter(f -> !f.getType().equals(FileResource.DOWNLOAD))
                .forEach(entityManager::remove);
    }

    @Retryable
    public void storeDownload(FileResource resource) {
        // Store file resource in the DB or external storage
        if (storageUtil.shouldStoreExternally(resource)) {
            storageUtil.uploadFile(resource);
        } else {
            resource.setStorageType(FileResource.STORAGE_DB);
        }
    }

    @Retryable
    public void storeResource(FileResource resource) {
        // Store file resource in the DB or external storage
        if (storageUtil.shouldStoreExternally(resource)) {
            storageUtil.uploadFile(resource);
            // Don't store the binary content in the DB - it's now stored externally
            resource.setContent(null);
        } else {
            resource.setStorageType(FileResource.STORAGE_DB);
        }
    }

    @Transactional
    public void persistResource(FileResource resource) {
        entityManager.persist(resource);
    }

    @Transactional
    public void activateExtension(ExtensionVersion extVersion) {
        extVersion.setActive(true);
        extVersion = entityManager.merge(extVersion);
        extensions.updateExtension(extVersion.getExtension());
    }

    @Transactional
    public void updateResource(FileResource resource) {
        entityManager.merge(resource);
    }
}
