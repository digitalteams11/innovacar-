package com.carrental.dto.contract;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdditionalDriverSignatureLinkResponse {
    private String signingUrl;
    private LocalDateTime expiresAt;
}
