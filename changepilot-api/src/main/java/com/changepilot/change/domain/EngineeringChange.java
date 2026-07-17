package com.changepilot.change.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "engineering_change")
public class EngineeringChange {

    @Id
    private UUID id;

    @Column(nullable = false, length = DomainConstraints.TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = DomainConstraints.DESCRIPTION_MAX_LENGTH)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RiskLevel risk;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeStatus status;

    @ElementCollection
    @CollectionTable(name = "engineering_change_component", joinColumns = @JoinColumn(name = "engineering_change_id"))
    @OrderColumn(name = "component_order")
    @Column(name = "component_name", nullable = false, length = DomainConstraints.COMPONENT_MAX_LENGTH)
    private List<String> affectedComponents = new ArrayList<>();

    @OneToMany(mappedBy = "engineeringChange", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "criterion_order")
    private List<AcceptanceCriterion> acceptanceCriteria = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected EngineeringChange() {
    }

    public EngineeringChange(UUID id, String title, String description, RiskLevel risk, ChangeStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.risk = risk;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public RiskLevel getRisk() {
        return risk;
    }

    public void setRisk(RiskLevel risk) {
        this.risk = risk;
    }

    public ChangeStatus getStatus() {
        return status;
    }

    public void setStatus(ChangeStatus status) {
        this.status = status;
    }

    public List<String> getAffectedComponents() {
        return affectedComponents;
    }

    public void replaceAffectedComponents(List<String> affectedComponents) {
        this.affectedComponents.clear();
        this.affectedComponents.addAll(affectedComponents);
    }

    public List<AcceptanceCriterion> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void replaceAcceptanceCriteria(List<AcceptanceCriterion> acceptanceCriteria) {
        this.acceptanceCriteria.clear();
        for (AcceptanceCriterion criterion : acceptanceCriteria) {
            criterion.setEngineeringChange(this);
            this.acceptanceCriteria.add(criterion);
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
