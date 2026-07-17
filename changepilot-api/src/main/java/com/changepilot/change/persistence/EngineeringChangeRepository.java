package com.changepilot.change.persistence;

import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EngineeringChangeRepository extends JpaRepository<EngineeringChange, UUID> {

    List<EngineeringChange> findAllByStatus(ChangeStatus status, org.springframework.data.domain.Sort sort);
}
