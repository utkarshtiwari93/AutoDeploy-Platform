package com.utkarsh.in.DeploymentPlatform.entity;


import com.utkarsh.in.DeploymentPlatform.enums.DeploymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "deployments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeploymentStatus status;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "image_tag")
    private String imageTag;

    @Column(name = "image_id")
    private String imageId;

    @Column(name = "public_url")
    private String publicUrl;

    @CreationTimestamp
    @Column(name = "deployed_at", updatable = false)
    private LocalDateTime deployedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "deployment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DeploymentLog> logs;
}