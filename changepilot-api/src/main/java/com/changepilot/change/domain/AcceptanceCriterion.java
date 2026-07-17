package com.changepilot.change.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "acceptance_criterion")
public class AcceptanceCriterion {

    @Id
    private UUID id;

    @Column(name = "criterion_text", nullable = false, length = DomainConstraints.CRITERION_TEXT_MAX_LENGTH)
    private String text;

    @Column(nullable = false)
    private boolean completed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "engineering_change_id", nullable = false)
    private EngineeringChange engineeringChange;

    protected AcceptanceCriterion() {
    }

    public AcceptanceCriterion(UUID id, String text, boolean completed) {
        this.id = id;
        this.text = text;
        this.completed = completed;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public EngineeringChange getEngineeringChange() {
        return engineeringChange;
    }

    public void setEngineeringChange(EngineeringChange engineeringChange) {
        this.engineeringChange = engineeringChange;
    }
}
