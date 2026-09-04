package com.ruoyi.lab.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.domain.LabAttachment;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabAttachmentMapper;
import com.ruoyi.lab.service.AttachmentService;
import com.ruoyi.lab.storage.AttachmentPolicy;
import com.ruoyi.lab.storage.AttachmentPolicy.ValidatedAttachment;
import com.ruoyi.lab.storage.LabAttachmentObjectAuthorizer;
import com.ruoyi.lab.storage.StorageService;
import com.ruoyi.lab.storage.StoredObject;
import com.ruoyi.lab.vo.AttachmentContent;
import com.ruoyi.lab.vo.AttachmentVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transactional metadata workflow around private binary storage. */
@Service
public class AttachmentServiceImpl implements AttachmentService
{
    private static final Logger LOG = LoggerFactory.getLogger(AttachmentServiceImpl.class);

    private final LabAttachmentMapper attachmentMapper;
    private final LabAttachmentObjectAuthorizer objectAuthorizer;
    private final AttachmentPolicy attachmentPolicy;
    private final StorageService storageService;

    public AttachmentServiceImpl(LabAttachmentMapper attachmentMapper,
            LabAttachmentObjectAuthorizer objectAuthorizer, AttachmentPolicy attachmentPolicy,
            StorageService storageService)
    {
        this.attachmentMapper = attachmentMapper;
        this.objectAuthorizer = objectAuthorizer;
        this.attachmentPolicy = attachmentPolicy;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttachmentVo> list(String businessType, Long businessId)
    {
        long objectId = requirePositive(businessId);
        String normalizedType = objectAuthorizer.normalizeBusinessType(businessType);
        objectAuthorizer.assertReadable(normalizedType, objectId);
        return attachmentMapper.selectListByObject(normalizedType, objectId).stream()
                .map(AttachmentVo::from).toList();
    }

    @Override
    @Transactional
    public AttachmentVo upload(String businessType, Long businessId, String originalName,
            String declaredMimeType, byte[] content, String username)
    {
        long objectId = requirePositive(businessId);
        String normalizedType = objectAuthorizer.normalizeBusinessType(businessType);
        objectAuthorizer.lockAndAssertManageable(normalizedType, objectId);
        int currentCount = attachmentMapper.countActiveByObject(normalizedType, objectId);
        ValidatedAttachment validated = attachmentPolicy.validate(originalName, declaredMimeType,
                content, currentCount);

        StoredObject stored = store(content, validated.extension());
        registerRollbackCleanup(stored.storageKey());
        LabAttachment attachment = metadata(normalizedType, objectId, validated, stored, username);
        attachmentMapper.insert(attachment);
        return AttachmentVo.from(attachment);
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent download(Long attachmentId)
    {
        LabAttachment attachment = requireActive(requirePositive(attachmentId));
        objectAuthorizer.assertReadable(attachment.getBusinessType(), attachment.getBusinessId());
        try
        {
            InputStream input = storageService.load(attachment.getStorageKey());
            return new AttachmentContent(attachment.getOriginalName(), attachment.getMimeType(),
                    attachment.getSize(), input);
        }
        catch (IOException exception)
        {
            throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR, "附件内容暂时不可用");
        }
    }

    @Override
    @Transactional
    public void delete(Long attachmentId)
    {
        long id = requirePositive(attachmentId);
        LabAttachment snapshot = requireActive(id);
        objectAuthorizer.lockAndAssertManageable(snapshot.getBusinessType(), snapshot.getBusinessId());
        LabAttachment locked = attachmentMapper.selectByIdForUpdate(id);
        if (locked == null)
        {
            throw notFound();
        }
        if (!Objects.equals(snapshot.getBusinessType(), locked.getBusinessType())
                || !Objects.equals(snapshot.getBusinessId(), locked.getBusinessId()))
        {
            throw duplicateOperation();
        }
        if (attachmentMapper.markDeleted(id) != 1)
        {
            throw duplicateOperation();
        }
        registerCommittedDelete(locked.getStorageKey());
    }

    private StoredObject store(byte[] content, String extension)
    {
        try
        {
            return storageService.store(new java.io.ByteArrayInputStream(content), content.length, extension);
        }
        catch (IOException exception)
        {
            throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR, "附件存储失败");
        }
    }

    private LabAttachment requireActive(long attachmentId)
    {
        LabAttachment attachment = attachmentMapper.selectByIdActive(attachmentId);
        if (attachment == null)
        {
            throw notFound();
        }
        return attachment;
    }

    private static LabAttachment metadata(String businessType, long businessId,
            ValidatedAttachment validated, StoredObject stored, String username)
    {
        LabAttachment attachment = new LabAttachment();
        attachment.setBusinessType(businessType);
        attachment.setBusinessId(businessId);
        attachment.setOriginalName(validated.originalName());
        attachment.setStoredName(stored.storedName());
        attachment.setMimeType(validated.mimeType());
        attachment.setSize(stored.sizeBytes());
        attachment.setStorageKey(stored.storageKey());
        attachment.setSha256(stored.sha256());
        attachment.setCreateBy(requireUsername(username));
        attachment.setCreateTime(LocalDateTime.now());
        attachment.setDelFlag("0");
        return attachment;
    }

    private void registerRollbackCleanup(String storageKey)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if (status != STATUS_COMMITTED)
                {
                    deleteQuietly(storageKey);
                }
            }
        });
    }

    private void registerCommittedDelete(String storageKey)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
        {
            deleteQuietly(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCommit()
            {
                deleteQuietly(storageKey);
            }
        });
    }

    private void deleteQuietly(String storageKey)
    {
        try
        {
            storageService.delete(storageKey);
        }
        catch (IOException exception)
        {
            LOG.warn("Unable to remove private attachment object after metadata transition");
        }
    }

    private static long requirePositive(Long id)
    {
        if (id == null || id <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "对象编号无效");
        }
        return id;
    }

    private static String requireUsername(String username)
    {
        if (username == null || username.isBlank())
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "操作人不能为空");
        }
        return username.trim();
    }

    private static LabBusinessException notFound()
    {
        return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "附件不存在");
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理");
    }
}
