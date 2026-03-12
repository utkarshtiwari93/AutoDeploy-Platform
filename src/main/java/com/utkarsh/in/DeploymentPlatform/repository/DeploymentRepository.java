package com.utkarsh.in.DeploymentPlatform.repository;


import com.utkarsh.in.DeploymentPlatform.entity.Deployment;
import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByProjectIdOrderByDeployedAtDesc(Long projectId);
    Optional<Deployment> findTopByProjectIdOrderByDeployedAtDesc(Long projectId);
    boolean existsByProjectIdAndStatusIn(Long projectId, List<DeploymentStatus> statuses);
}