package com.changepilot.change.api;

import com.changepilot.change.domain.AcceptanceCriterion;
import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import com.changepilot.change.domain.RiskLevel;
import com.changepilot.change.persistence.EngineeringChangeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EngineeringChangeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EngineeringChangeRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void createReturns201AndLocation() throws Exception {
        mockMvc.perform(post("/api/engineering-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "  Add canary deploy  ",
                                  "description": "  Add canary deployment for checkout.  ",
                                  "risk": "HIGH",
                                  "affectedComponents": [" checkout ", "gateway"],
                                  "criteria": [
                                    {"text": "  Verify rollback runbook  "}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/engineering-changes/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Add canary deploy"))
                .andExpect(jsonPath("$.affectedComponents[0]").value("checkout"))
                .andExpect(jsonPath("$.criteria[0].completed").value(false))
                .andExpect(jsonPath("$.availableTransitions[0]").value("READY"))
                .andExpect(jsonPath("$.deletable").value(true));
    }

    @Test
    void createRejectsMissingCriteriaAtValidationLayer() throws Exception {
        mockMvc.perform(post("/api/engineering-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "description": "Description",
                                  "risk": "LOW",
                                  "affectedComponents": [],
                                  "criteria": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("criteria"));
    }

    @Test
    void listAndDetailWorkWithStatusFilterAndCounts() throws Exception {
        EngineeringChange draft = persistChange(ChangeStatus.DRAFT, RiskLevel.LOW,
                List.of(criterion("One", false)));
        EngineeringChange verified = persistChange(ChangeStatus.VERIFIED, RiskLevel.MEDIUM,
                List.of(criterion("One", true), criterion("Two", true)));

        mockMvc.perform(get("/api/engineering-changes").param("status", "VERIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(verified.getId().toString()))
                .andExpect(jsonPath("$[0].completedCriteriaCount").value(2));

        mockMvc.perform(get("/api/engineering-changes/{id}", draft.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(draft.getId().toString()))
                .andExpect(jsonPath("$.totalCriteriaCount").value(1))
                .andExpect(jsonPath("$.availableTransitions[0]").value("READY"));
    }

    @Test
    void updatePreservesCriterionCompletionByIdAndRemovesOmittedCriteria() throws Exception {
        EngineeringChange existing = persistChange(ChangeStatus.IN_PROGRESS, RiskLevel.HIGH,
                List.of(criterion("Keep", true), criterion("Remove", false)));
        UUID preservedId = existing.getAcceptanceCriteria().get(0).getId();

        String response = mockMvc.perform(put("/api/engineering-changes/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "description": "Updated description",
                                  "risk": "MEDIUM",
                                  "affectedComponents": ["api"],
                                  "criteria": [
                                    {"id": "%s", "text": "Updated keep"},
                                    {"text": "New criterion"}
                                  ]
                                }
                                """.formatted(preservedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria", hasSize(2)))
                .andExpect(jsonPath("$.criteria[0].id").value(preservedId.toString()))
                .andExpect(jsonPath("$.criteria[0].completed").value(true))
                .andExpect(jsonPath("$.criteria[1].completed").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assert json.get("criteria").size() == 2;
    }

    @Test
    void updateRejectsForeignCriterionIdAsBadRequest() throws Exception {
        EngineeringChange existing = persistChange(ChangeStatus.DRAFT, RiskLevel.LOW, List.of(criterion("Keep", false)));

        mockMvc.perform(put("/api/engineering-changes/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated",
                                  "description": "Updated description",
                                  "risk": "LOW",
                                  "affectedComponents": [],
                                  "criteria": [
                                    {"id": "%s", "text": "Oops"}
                                  ]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void criterionToggleUpdatesCompletion() throws Exception {
        EngineeringChange existing = persistChange(ChangeStatus.READY, RiskLevel.LOW, List.of(criterion("Check", false)));
        UUID criterionId = existing.getAcceptanceCriteria().getFirst().getId();

        mockMvc.perform(patch("/api/engineering-changes/{id}/criteria/{criterionId}", existing.getId(), criterionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria[0].completed").value(true))
                .andExpect(jsonPath("$.completedCriteriaCount").value(1));
    }

    @Test
    void verifiedGateAndRepresentativeInvalidTransitionReturnConflict() throws Exception {
        EngineeringChange gated = persistChange(ChangeStatus.IN_PROGRESS, RiskLevel.HIGH,
                List.of(criterion("Done", true), criterion("Pending", false)));

        mockMvc.perform(patch("/api/engineering-changes/{id}/status", gated.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "VERIFIED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_CONFLICT"));

        EngineeringChange draft = persistChange(ChangeStatus.DRAFT, RiskLevel.LOW, List.of(criterion("One", false)));
        mockMvc.perform(patch("/api/engineering-changes/{id}/status", draft.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus": "DONE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_CONFLICT"));
    }

    @Test
    void validLifecycleTransitionsWork() throws Exception {
        EngineeringChange draft = persistChange(ChangeStatus.DRAFT, RiskLevel.LOW, List.of(criterion("One", false)));
        transition(draft.getId(), ChangeStatus.READY).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        EngineeringChange ready = persistChange(ChangeStatus.READY, RiskLevel.LOW, List.of(criterion("One", false)));
        transition(ready.getId(), ChangeStatus.DRAFT).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));

        EngineeringChange readyTwo = persistChange(ChangeStatus.READY, RiskLevel.LOW, List.of(criterion("One", false)));
        transition(readyTwo.getId(), ChangeStatus.IN_PROGRESS).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        EngineeringChange inProgress = persistChange(ChangeStatus.IN_PROGRESS, RiskLevel.LOW, List.of(criterion("One", true)));
        transition(inProgress.getId(), ChangeStatus.READY).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        EngineeringChange inProgressVerified = persistChange(ChangeStatus.IN_PROGRESS, RiskLevel.LOW, List.of(criterion("One", true)));
        transition(inProgressVerified.getId(), ChangeStatus.VERIFIED).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VERIFIED"));

        EngineeringChange verified = persistChange(ChangeStatus.VERIFIED, RiskLevel.LOW, List.of(criterion("One", true)));
        transition(verified.getId(), ChangeStatus.IN_PROGRESS).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        EngineeringChange verifiedDone = persistChange(ChangeStatus.VERIFIED, RiskLevel.LOW, List.of(criterion("One", true)));
        transition(verifiedDone.getId(), ChangeStatus.DONE).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void verifiedLocksCriteriaButAllowsMetadataEdit() throws Exception {
        EngineeringChange verified = persistChange(ChangeStatus.VERIFIED, RiskLevel.MEDIUM, List.of(criterion("Locked", true)));
        UUID criterionId = verified.getAcceptanceCriteria().getFirst().getId();

        mockMvc.perform(put("/api/engineering-changes/{id}", verified.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated metadata",
                                  "description": "Updated description",
                                  "risk": "HIGH",
                                  "affectedComponents": ["api"],
                                  "criteria": [
                                    {"id": "%s", "text": "Locked"}
                                  ]
                                }
                                """.formatted(criterionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated metadata"))
                .andExpect(jsonPath("$.criteria[0].text").value("Locked"));

        mockMvc.perform(patch("/api/engineering-changes/{id}/criteria/{criterionId}", verified.getId(), criterionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"completed": false}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_CONFLICT"));

        mockMvc.perform(put("/api/engineering-changes/{id}", verified.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated metadata",
                                  "description": "Updated description",
                                  "risk": "HIGH",
                                  "affectedComponents": ["api"],
                                  "criteria": [
                                    {"id": "%s", "text": "Changed text"}
                                  ]
                                }
                                """.formatted(criterionId)))
                .andExpect(status().isConflict());
    }

    @Test
    void doneIsReadOnlyAndDeleteIsDraftOnly() throws Exception {
        EngineeringChange done = persistChange(ChangeStatus.DONE, RiskLevel.MEDIUM, List.of(criterion("Locked", true)));
        UUID doneCriterionId = done.getAcceptanceCriteria().getFirst().getId();

        mockMvc.perform(put("/api/engineering-changes/{id}", done.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Nope",
                                  "description": "Nope",
                                  "risk": "LOW",
                                  "affectedComponents": [],
                                  "criteria": [
                                    {"id": "%s", "text": "Locked"}
                                  ]
                                }
                                """.formatted(doneCriterionId)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/engineering-changes/{id}", done.getId()))
                .andExpect(status().isConflict());

        EngineeringChange draft = persistChange(ChangeStatus.DRAFT, RiskLevel.LOW, List.of(criterion("Delete", false)));
        mockMvc.perform(delete("/api/engineering-changes/{id}", draft.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingResourceAndMalformedEnumUseConsistentErrorShape() throws Exception {
        mockMvc.perform(get("/api/engineering-changes/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.path").value(containsString("/api/engineering-changes/")));

        mockMvc.perform(post("/api/engineering-changes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Title",
                                  "description": "Description",
                                  "risk": "EXTREME",
                                  "affectedComponents": [],
                                  "criteria": [{"text": "One"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/engineering-changes"));
    }

    @Test
    void unmappedRouteReturnsConsistentNotFoundError() throws Exception {
        mockMvc.perform(get("/api/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Resource not found"))
                .andExpect(jsonPath("$.path").value("/api/missing"));
    }

    @Test
    void openApiDocumentsCreateAndDeleteStatusCodes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/engineering-changes'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/engineering-changes'].post.responses['200']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/engineering-changes/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/engineering-changes/{id}'].delete.responses['200']").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions transition(UUID id, ChangeStatus target) throws Exception {
        return mockMvc.perform(patch("/api/engineering-changes/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{" + "\"targetStatus\":\"" + target + "\"}"));
    }

    private EngineeringChange persistChange(ChangeStatus status, RiskLevel risk, List<AcceptanceCriterion> criteria) {
        EngineeringChange change = new EngineeringChange(UUID.randomUUID(), status + " change", "Description", risk, status,
                Instant.parse("2026-07-17T00:00:00Z"), Instant.parse("2026-07-17T00:00:00Z"));
        change.replaceAffectedComponents(List.of("api"));
        change.replaceAcceptanceCriteria(criteria);
        return repository.save(change);
    }

    private AcceptanceCriterion criterion(String text, boolean completed) {
        return new AcceptanceCriterion(UUID.randomUUID(), text, completed);
    }
}
