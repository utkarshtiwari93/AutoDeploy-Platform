package com.utkarsh.in.DeploymentPlatform.repository;


import com.utkarsh.in.DeploymentPlatform.entity.DeploymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
    List<DeploymentLog> findByDeploymentIdOrderByCreatedAtAsc(Long deploymentId);
    List<DeploymentLog> findByDeploymentIdAndIdGreaterThanOrderByCreatedAtAsc(Long deploymentId, Long lastId);
}