package com.utkarsh.in.DeploymentPlatform.repository;

import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    List<Deployment> findByProjectIdOrderByDeployedAtDesc(Long projectId);

    Optional<Deployment> findTopByProjectIdOrderByDeployedAtDesc(Long projectId);

    boolean existsByProjectIdAndStatusIn(Long projectId, List<DeploymentStatus> statuses);

    List<Deployment> findByStatus(DeploymentStatus status);

    @Query("SELECT d FROM Deployment d WHERE d.status IN :statuses AND d.updatedAt < :cutoff")
    List<Deployment> findOldDeploymentsByStatuses(
            @Param("statuses") List<DeploymentStatus> statuses,
            @Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT d FROM Deployment d WHERE d.status = 'RUNNING'")
    List<Deployment> findAllRunningDeployments();

    List<Deployment> findByProjectId(Long projectId);
}