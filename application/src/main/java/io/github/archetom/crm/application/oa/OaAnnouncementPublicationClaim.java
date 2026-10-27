package io.github.archetom.crm.application.oa;

import java.util.Optional;

/** A claim can be an ordinary effect attempt or a root-finalization attempt. */
public record OaAnnouncementPublicationClaim(
        Optional<OaAnnouncementPublicationOccurrence> occurrence,
        boolean terminalClaim) {

    public OaAnnouncementPublicationClaim {
        occurrence = occurrence == null ? Optional.empty() : occurrence;
        if (occurrence.isEmpty() && terminalClaim) {
            throw new IllegalArgumentException("empty OA publication claim cannot be terminal");
        }
    }

    public static OaAnnouncementPublicationClaim none() {
        return new OaAnnouncementPublicationClaim(Optional.empty(), false);
    }
}
