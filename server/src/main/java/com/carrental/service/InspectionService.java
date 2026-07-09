package com.carrental.service;

import com.carrental.dto.inspection.CreateInspectionTokenRequest;
import com.carrental.dto.inspection.InspectionMediaResponse;
import com.carrental.dto.inspection.InspectionResponse;
import com.carrental.entity.*;
import com.carrental.exception.InspectionTokenExpiredException;
import com.carrental.exception.InspectionUploadException;
import com.carrental.exception.ResourceNotFoundException;
import com.carrental.repository.*;
import com.carrental.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InspectionService {
    private static final long MAX_PHOTO_BYTES = 20L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 80L * 1024 * 1024;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm", "video/quicktime");
    private static final Set<String> REQUIRED_LABELS = Set.of(
            "FRONT_SIDE", "REAR_SIDE", "LEFT_SIDE", "RIGHT_SIDE", "INTERIOR",
            "DASHBOARD", "MILEAGE", "FUEL_LEVEL", "WHEELS", "TRUNK",
            "DOCUMENTS", "ACCESSORIES");
    private static final String OPTIONAL_VIDEO_LABEL = "VIDEO_WALKAROUND";

    private final InspectionRepository inspectionRepository;
    private final InspectionMediaRepository mediaRepository;
    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantSettingsRepository tenantSettingsRepository;

    @Value("${app.inspection.storage-dir:storage/inspection-media}")
    private String storageDir;

    @Value("${app.inspection.token-hours:24}")
    private long tokenHours;

    @Transactional
    public InspectionResponse createToken(CreateInspectionTokenRequest request) {
        Long tenantId = TenantContext.getCurrentTenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Reservation reservation = null;
        Contract contract = null;

        if (request.getContractId() != null) {
            contract = contractRepository.findByIdAndTenantId(request.getContractId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contract not found"));
            reservation = contract.getReservation();
        } else if (request.getReservationId() != null) {
            reservation = reservationRepository.findByIdAndTenantId(request.getReservationId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
            contract = reservation.getContract();
        } else {
            throw new IllegalArgumentException("reservationId or contractId is required");
        }

        Client client = contract != null && contract.getClient() != null ? contract.getClient() : reservation.getClient();
        Vehicle vehicle = contract != null && contract.getVehicle() != null ? contract.getVehicle() : reservation.getVehicle();
        if (client == null || vehicle == null) {
            throw new IllegalStateException("Inspection requires linked client and vehicle");
        }

        Inspection existingInspection = findReusableInspection(tenantId, contract, reservation, request.getType());
        if (existingInspection != null) {
            refreshToken(existingInspection);
            Inspection savedExisting = inspectionRepository.save(existingInspection);
            return InspectionResponse.from(savedExisting, buildCaptureUrl(request.getFrontendUrl(), savedExisting.getToken()));
        }

        int retentionDays = tenantSettingsRepository.findByTenantId(tenantId)
                .map(TenantSettings::getInspectionRetentionDays)
                .filter(days -> days != null && days > 0)
                .orElse(7);
        LocalDate endDate = reservation != null ? reservation.getDateEnd() : contract.getEndDate();
        LocalDateTime mediaExpiresAt = endDate.plusDays(retentionDays).atTime(23, 59, 59);

        Inspection inspection = Inspection.builder()
                .tenant(tenant)
                .reservation(reservation)
                .contract(contract)
                .client(client)
                .vehicle(vehicle)
                .employee(currentUser())
                .type(request.getType())
                .status(InspectionStatus.NOT_STARTED)
                .token(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                .tokenExpiresAt(LocalDateTime.now().plusHours(tokenHours))
                .mediaExpiresAt(mediaExpiresAt)
                .build();
        Inspection saved = inspectionRepository.save(inspection);
        return InspectionResponse.from(saved, buildCaptureUrl(request.getFrontendUrl(), saved.getToken()));
    }

    @Transactional(readOnly = true)
    public InspectionResponse getByToken(String token) {
        Inspection inspection = findValidInspection(token);
        return InspectionResponse.from(inspection);
    }

    @Transactional
    public InspectionResponse upload(Long inspectionId, String token, MultipartFile file, String label,
                                     String notes, InspectionMediaType type, Integer duration) {
        Inspection inspection = findValidInspection(token);
        if (!inspection.getId().equals(inspectionId)) {
            throw new IllegalArgumentException("Token does not belong to this inspection");
        }
        validateUpload(file, type, duration);
        try {
            String extension = extensionFor(file.getContentType());
            String accessToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            Path tenantDir = Path.of(storageDir, "tenant-" + inspection.getTenant().getId(), "inspection-" + inspection.getId());
            Files.createDirectories(tenantDir);
            Path destination = tenantDir.resolve(UUID.randomUUID() + extension);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            String fileUrl = publicFileUrl(destination);

            InspectionMedia media = InspectionMedia.builder()
                    .inspection(inspection)
                    .type(type)
                    .label(clean(label))
                    .notes(clean(notes))
                    .storagePath(destination.toAbsolutePath().toString())
                    .fileUrl(fileUrl)
                    .thumbnailUrl(null)
                    .size(file.getSize())
                    .duration(duration)
                    .contentType(file.getContentType())
                    .accessToken(accessToken)
                    .build();
            InspectionMedia saved = mediaRepository.save(media);
            inspection.getMedia().add(saved);

            inspection.setStatus(InspectionStatus.IN_PROGRESS);
            if (hasRequiredBeforeMedia(inspection) || hasRequiredAfterMedia(inspection)) {
                inspection.setStatus(InspectionStatus.COMPLETED);
                inspection.setCompletedAt(LocalDateTime.now());
            }
            inspectionRepository.save(inspection);
            return InspectionResponse.from(inspection);
        } catch (IOException exception) {
            throw new IllegalStateException("Media upload failed");
        }
    }

    @Transactional
    public InspectionMediaResponse uploadPublic(String token, MultipartFile file, String label,
                                                String notes, InspectionType inspectionType) {
        String normalizedLabel = normalizeLabel(label);
        log.info("Public inspection upload requested: tokenPresent={}, label={}, fileSize={}, fileType={}",
                token != null && !token.isBlank(),
                normalizedLabel,
                file == null ? null : file.getSize(),
                file == null ? null : file.getContentType());
        Inspection inspection = findValidInspection(token);
        if (inspectionType != null && inspection.getType() != inspectionType) {
            throw new IllegalArgumentException("Inspection type does not match this link.");
        }
        validateLabel(normalizedLabel);
        validateUpload(file, InspectionMediaType.PHOTO, null);
        InspectionMedia saved = storeMedia(inspection, file, normalizedLabel, notes, InspectionMediaType.PHOTO, null);
        updateInspectionStatus(inspection);
        inspectionRepository.save(inspection);
        log.info("Public inspection upload saved: inspectionId={}, mediaId={}, savedSuccessfully=true",
                inspection.getId(), saved.getId());
        return InspectionMediaResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> getReservationInspections(Long reservationId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return inspectionRepository.findAllByTenantIdAndReservationIdOrderByCreatedAtDesc(tenantId, reservationId)
                .stream().map(InspectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> getClientInspections(Long clientId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return inspectionRepository.findAllByTenantIdAndClientIdOrderByCreatedAtDesc(tenantId, clientId)
                .stream().map(InspectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<InspectionResponse> getContractInspections(Long contractId) {
        Long tenantId = TenantContext.getCurrentTenantId();
        return inspectionRepository.findAllByTenantIdAndContractIdOrderByCreatedAtDesc(tenantId, contractId)
                .stream().map(InspectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public InspectionMedia getMedia(Long mediaId, String accessToken) {
        InspectionMedia media = mediaRepository.findByIdAndAccessToken(mediaId, accessToken)
                .orElseThrow(() -> new ResourceNotFoundException("Media not found"));
        if (LocalDateTime.now().isAfter(media.getInspection().getMediaExpiresAt())) {
            throw new ResourceNotFoundException("Media expired");
        }
        Path storagePath = Path.of(media.getStoragePath());
        if (!Files.exists(storagePath)) {
            log.warn("[INSPECTION_MEDIA_FILE_MISSING] mediaId={} storagePath={} exists=false",
                    mediaId, media.getStoragePath());
            throw new ResourceNotFoundException("Media file not found on server");
        }
        return media;
    }

    @Transactional
    public int deleteExpiredMedia() {
        List<Inspection> expired = inspectionRepository.findAllByMediaExpiresAtBeforeAndStatusNot(
                LocalDateTime.now(), InspectionStatus.EXPIRED);
        for (Inspection inspection : expired) {
            for (InspectionMedia media : inspection.getMedia()) {
                try {
                    Files.deleteIfExists(Path.of(media.getStoragePath()));
                } catch (IOException ignored) {
                    // Keep cleanup best-effort; DB state still marks media expired.
                }
            }
            inspection.getMedia().clear();
            inspection.setStatus(InspectionStatus.EXPIRED);
        }
        inspectionRepository.saveAll(expired);
        return expired.size();
    }

    public boolean missingBeforeDeliveryInspection(Contract contract) {
        if (contract == null || contract.getId() == null || contract.getTenant() == null) {
            return true;
        }
        return !inspectionRepository.existsByTenantIdAndContractIdAndTypeAndStatus(
                contract.getTenant().getId(), contract.getId(), InspectionType.BEFORE_DELIVERY, InspectionStatus.COMPLETED);
    }

    private Inspection findValidInspection(String token) {
        Inspection inspection = inspectionRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid inspection token."));
        if (inspection.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            inspection.setStatus(InspectionStatus.EXPIRED);
            inspectionRepository.save(inspection);
            throw new InspectionTokenExpiredException("Inspection link expired.");
        }
        return inspection;
    }

    private void validateUpload(MultipartFile file, InspectionMediaType type, Integer duration) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Media file is required");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (type == InspectionMediaType.PHOTO) {
            if (!PHOTO_TYPES.contains(contentType)) throw new IllegalArgumentException("Only JPEG, PNG, or WebP images are allowed.");
            if (file.getSize() > MAX_PHOTO_BYTES) throw new IllegalArgumentException("Upload failed. File is too large.");
        } else if (type == InspectionMediaType.VIDEO) {
            if (!VIDEO_TYPES.contains(contentType)) throw new IllegalArgumentException("Unsupported video type");
            if (file.getSize() > MAX_VIDEO_BYTES) throw new IllegalArgumentException("Video exceeds 80MB limit");
            if (duration != null && duration > 90) throw new IllegalArgumentException("Video duration cannot exceed 90 seconds");
        }
    }

    private boolean hasRequiredBeforeMedia(Inspection inspection) {
        if (inspection.getType() != InspectionType.BEFORE_DELIVERY) return false;
        Set<String> labels = inspection.getMedia().stream()
                .filter(media -> media.getType() == InspectionMediaType.PHOTO)
                .map(InspectionMedia::getLabel)
                .collect(java.util.stream.Collectors.toSet());
        return labels.containsAll(REQUIRED_LABELS);
    }

    private boolean hasRequiredAfterMedia(Inspection inspection) {
        return inspection.getType() == InspectionType.AFTER_RETURN && inspection.getMedia().size() >= 3;
    }

    private User currentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal() : null;
        if (principal instanceof User user) return user;
        String email = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName() : null;
        return email != null ? userRepository.findByEmail(email).orElse(null) : null;
    }

    private String buildCaptureUrl(String frontendUrl, String token) {
        String base = frontendUrl == null || frontendUrl.isBlank() ? "" : frontendUrl.replaceAll("/+$", "");
        return base + "/inspection/" + token;
    }

    private Inspection findReusableInspection(Long tenantId, Contract contract, Reservation reservation, InspectionType type) {
        if (contract != null && contract.getId() != null) {
            return inspectionRepository.findFirstByTenantIdAndContractIdAndTypeAndStatusNotOrderByCreatedAtDesc(
                    tenantId, contract.getId(), type, InspectionStatus.EXPIRED).orElse(null);
        }
        if (reservation != null && reservation.getId() != null) {
            return inspectionRepository.findFirstByTenantIdAndReservationIdAndTypeAndStatusNotOrderByCreatedAtDesc(
                    tenantId, reservation.getId(), type, InspectionStatus.EXPIRED).orElse(null);
        }
        return null;
    }

    private void refreshToken(Inspection inspection) {
        inspection.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        inspection.setTokenExpiresAt(LocalDateTime.now().plusHours(tokenHours));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String extensionFor(String contentType) {
        return switch (contentType == null ? "" : contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            case "video/quicktime" -> ".mov";
            default -> throw new IllegalArgumentException("Unsupported media type");
        };
    }

    private InspectionMedia storeMedia(Inspection inspection, MultipartFile file, String label,
                                       String notes, InspectionMediaType type, Integer duration) {
        try {
            String extension = extensionFor(file.getContentType());
            String accessToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            Path inspectionDir = Path.of(storageDir, String.valueOf(inspection.getId()));
            Files.createDirectories(inspectionDir);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
            String fileName = inspection.getId() + "_" + safeFileToken(label) + "_" + timestamp + extension;
            Path destination = inspectionDir.resolve(fileName);
            log.info("Saving inspection media: inspectionId={}, label={}, storagePath={}", inspection.getId(), label, destination.toAbsolutePath());
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            String fileUrl = publicFileUrl(destination);

            // Deactivate any existing active media for the same label so duplicates don't appear.
            if (inspection.getMedia() != null) {
                inspection.getMedia().stream()
                        .filter(m -> label != null && label.equals(m.getLabel()) && Boolean.TRUE.equals(m.getActive()))
                        .forEach(old -> {
                            old.setActive(false);
                            mediaRepository.save(old);
                        });
            }

            InspectionMedia media = InspectionMedia.builder()
                    .inspection(inspection)
                    .type(type)
                    .label(label)
                    .notes(clean(notes))
                    .storagePath(destination.toAbsolutePath().toString())
                    .fileUrl(fileUrl)
                    .thumbnailUrl(null)
                    .size(file.getSize())
                    .duration(duration)
                    .contentType(file.getContentType())
                    .accessToken(accessToken)
                    .build();
            InspectionMedia saved = mediaRepository.save(media);
            inspection.getMedia().add(saved);

            log.info("[INSPECTION_UPLOAD_DEBUG] contractId={} reservationId={} vehicleId={} clientId={} category={} originalFilename={} storedFilename={} storedPath={} publicUrl={} fileExistsOnDisk={} fileSize={}",
                    inspection.getContract() != null ? inspection.getContract().getId() : null,
                    inspection.getReservation() != null ? inspection.getReservation().getId() : null,
                    inspection.getVehicle() != null ? inspection.getVehicle().getId() : null,
                    inspection.getClient() != null ? inspection.getClient().getId() : null,
                    label,
                    file.getOriginalFilename(),
                    fileName,
                    destination.toAbsolutePath(),
                    fileUrl,
                    Files.exists(destination),
                    file.getSize());

            return saved;
        } catch (IOException exception) {
            log.warn("Unable to save inspection media: inspectionId={}, fileSize={}, fileType={}",
                    inspection.getId(), file == null ? null : file.getSize(),
                    file == null ? null : file.getContentType(), exception);
            throw new InspectionUploadException("Unable to upload inspection photo", exception);
        }
    }

    private void updateInspectionStatus(Inspection inspection) {
        inspection.setStatus(InspectionStatus.IN_PROGRESS);
        if (hasRequiredBeforeMedia(inspection) || hasRequiredAfterMedia(inspection)) {
            inspection.setStatus(InspectionStatus.COMPLETED);
            inspection.setCompletedAt(LocalDateTime.now());
        }
    }

    private String normalizeLabel(String label) {
        return label == null ? "" : label.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private void validateLabel(String label) {
        if (!REQUIRED_LABELS.contains(label) && !OPTIONAL_VIDEO_LABEL.equals(label)) {
            throw new IllegalArgumentException("Invalid inspection photo label.");
        }
    }

    private String safeFileToken(String value) {
        String token = normalizeLabel(value).replaceAll("[^A-Z0-9_]", "");
        return token.isBlank() ? "PHOTO" : token;
    }

    private String publicFileUrl(Path destination) {
        Path uploadsRoot = Path.of("uploads").toAbsolutePath().normalize();
        Path absoluteDestination = destination.toAbsolutePath().normalize();
        if (absoluteDestination.startsWith(uploadsRoot)) {
            return "/uploads/" + uploadsRoot.relativize(absoluteDestination).toString().replace('\\', '/');
        }
        return "/" + destination.normalize().toString().replace('\\', '/');
    }
}
