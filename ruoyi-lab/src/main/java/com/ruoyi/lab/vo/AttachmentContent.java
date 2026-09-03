package com.ruoyi.lab.vo;

import java.io.InputStream;

/** Authorized attachment stream returned to the web adapter. */
public record AttachmentContent(String originalName, String mimeType, long size, InputStream input)
{
}
