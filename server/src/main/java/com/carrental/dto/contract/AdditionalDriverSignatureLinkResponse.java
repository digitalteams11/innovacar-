package com.carrental.dto.contract;

import com.carrental.entity.AdditionalDriverDeliveryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdditionalDriverSignatureLinkResponse {
    private String signingUrl;
    private LocalDateTime expiresAt;
    /** Only meaningful when the caller requested channel=EMAIL — the real, provider-confirmed outcome. */
    private AdditionalDriverDeliveryStatus deliveryStatus;
    private String deliveryFailureMessageSafe;
}
