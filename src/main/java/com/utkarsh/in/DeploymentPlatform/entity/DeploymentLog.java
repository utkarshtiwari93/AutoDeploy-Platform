package com.utkarsh.in.DeploymentPlatform.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "deployment_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "log_level", nullable = false)
    private String logLevel;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}